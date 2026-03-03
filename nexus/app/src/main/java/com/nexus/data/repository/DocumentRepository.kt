package com.nexus.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexus.data.local.DocumentDao
import com.nexus.data.local.LocalAiService
import com.nexus.data.local.ChatMessage
import com.nexus.data.local.ChatRequest
import com.nexus.data.local.EmbeddingRequest
import com.nexus.data.model.DocumentEntity
import com.nexus.data.model.DocumentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class DocumentRepository @Inject constructor(
    private val dao: DocumentDao,
    private val extractor: DocumentExtractor,
    private val aiService: LocalAiService,
    private val settingsRepository: SettingsRepository,
    private val context: Context
) {
    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()
    private val gson = Gson()

    // ── Indexado ──────────────────────────────────────────────────────────────
    suspend fun indexFolder(
        folderPath: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        if (!folder.exists()) return@withContext

        val settings = settingsRepository.getSettings()
        val extensions = if (settings.includeImages)
            extractor.allSupportedExtensions
        else
            extractor.supportedExtensions

        val files = folder.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .toList()

        val existingPaths = mutableListOf<String>()

        files.forEachIndexed { i, file ->
            onProgress(i.toFloat() / files.size, file.name)
            existingPaths.add(file.absolutePath)

            val existing = dao.getByPath(file.absolutePath)
            if (existing != null && existing.lastModified == file.lastModified()) return@forEachIndexed

            val content = extractor.extractTextSuspend(file)
            if (content.isBlank()) return@forEachIndexed

            val embeddingJson = fetchEmbeddingJson(content.take(2000))

            dao.upsert(
                DocumentEntity(
                    path = file.absolutePath,
                    name = file.nameWithoutExtension,
                    extension = file.extension,
                    content = content.take(50_000),
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    indexedAt = System.currentTimeMillis(),
                    embedding = embeddingJson
                )
            )
        }

        if (existingPaths.isNotEmpty()) dao.deleteOrphans(existingPaths)
    }

    // ── Búsqueda semántica (cosine similarity) ────────────────────────────────
    suspend fun semanticSearch(query: String, topK: Int = 15): List<DocumentResult> =
        withContext(Dispatchers.IO) {
            val queryEmbedding = fetchEmbedding(query)

            if (queryEmbedding.isEmpty()) return@withContext keywordSearch(query)

            val allDocs = dao.getAllDocumentsSync()
            val scored = allDocs.mapNotNull { doc ->
                val docEmbedding = parseEmbedding(doc.embedding)
                if (docEmbedding.isEmpty()) null
                else Pair(doc, cosineSimilarity(queryEmbedding, docEmbedding))
            }

            val results = scored
                .sortedByDescending { it.second }
                .take(topK)
                .map { (doc, score) ->
                    DocumentResult(
                        name = doc.name,
                        path = doc.path,
                        extension = doc.extension,
                        snippet = extractSnippet(doc.content, query),
                        score = score
                    )
                }

            if (results.isEmpty() || results.first().score < 0.3f) {
                return@withContext (results + keywordSearch(query)).distinctBy { it.path }.take(20)
            }

            results
        }

    // ── Búsqueda por keywords (fallback) ──────────────────────────────────────
    suspend fun keywordSearch(query: String): List<DocumentResult> =
        withContext(Dispatchers.IO) {
            (dao.searchByContent(query) + dao.searchByName(query))
                .distinctBy { it.path }
                .map { doc ->
                    DocumentResult(
                        name = doc.name,
                        path = doc.path,
                        extension = doc.extension,
                        snippet = extractSnippet(doc.content, query)
                    )
                }.take(30)
        }

    suspend fun search(query: String): List<DocumentResult> = semanticSearch(query)

    // ── IA: pregunta en lenguaje natural sobre documentos ────────────────────
    suspend fun queryWithAi(userQuery: String, contextDocs: List<DocumentResult>): String {
        val settings = settingsRepository.getSettings()
        val contextText = contextDocs.take(5).joinToString("\n---\n") {
            "ARCHIVO: ${it.name}.${it.extension}\n${it.snippet}"
        }
        val request = ChatRequest(
            model = settings.modelName,
            messages = listOf(
                ChatMessage(
                    "system",
                    "Eres NEXUS, un sistema de inteligencia documental personal. " +
                    "Responde preguntas basadas en los documentos del usuario. " +
                    "Sé conciso, preciso y responde en el mismo idioma que la pregunta."
                ),
                ChatMessage(
                    "user",
                    "Documentos disponibles:\n$contextText\n\nPregunta: $userQuery"
                )
            )
        )
        return try {
            aiService.chat(request).choices.firstOrNull()?.message?.content
                ?: "Sin respuesta del modelo"
        } catch (e: Exception) {
            "IA offline — mostrando solo resultados por palabras clave"
        }
    }

    // Búsqueda semántica + respuesta IA en un paso
    suspend fun smartQuery(question: String): String {
        val docs = semanticSearch(question, topK = 8)
        if (docs.isEmpty()) return "No encontré documentos relevantes para: \"$question\""
        return queryWithAi(question, docs)
    }

    // ── Abrir documento con la app del sistema ────────────────────────────────
    fun openDocument(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file.extension))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ── Stats ──────────────────────────────────────────────────────────────────
    suspend fun totalCount() = dao.count()
    suspend fun totalSize() = dao.totalSize() ?: 0L
    suspend fun countByExtension() = dao.countByExtension()
    suspend fun recentlyIndexed() = dao.recentlyIndexed()

    // ── Embedding helpers ──────────────────────────────────────────────────────
    private suspend fun fetchEmbedding(text: String): FloatArray = try {
        val settings = settingsRepository.getSettings()
        val response = aiService.embeddings(EmbeddingRequest(settings.modelName, text))
        response.data.firstOrNull()?.embedding?.toFloatArray() ?: floatArrayOf()
    } catch (e: Exception) { floatArrayOf() }

    private suspend fun fetchEmbeddingJson(text: String): String {
        val vec = fetchEmbedding(text)
        return if (vec.isEmpty()) "" else gson.toJson(vec.toList())
    }

    private fun parseEmbedding(json: String): FloatArray = try {
        if (json.isBlank()) return floatArrayOf()
        val type = object : TypeToken<List<Float>>() {}.type
        val list: List<Float> = gson.fromJson(json, type)
        list.toFloatArray()
    } catch (e: Exception) { floatArrayOf() }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
    return if (denom == 0f) 0f else dot / denom
    }

    private fun extractSnippet(content: String, query: String): String {
        val idx = content.indexOf(query, ignoreCase = true)
        return if (idx >= 0) {
            val start = maxOf(0, idx - 80)
            val end = minOf(content.length, idx + query.length + 160)
            "...${content.substring(start, end)}..."
        } else content.take(220) + "..."
    }

    private fun getMimeType(ext: String) = when (ext.lowercase()) {
        "pdf"  -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc"  -> "application/msword"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls"  -> "application/vnd.ms-excel"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt"  -> "text/plain"
        "csv"  -> "text/csv"
        "jpg", "jpeg", "png", "webp" -> "image/*"
        else   -> "*/*"
    }
}
