package com.nexus.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.nexus.data.model.DocumentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveRepository @Inject constructor(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        // ── Carpeta NEXUS compartida — fija para todos los que instalen la app ──
        const val NEXUS_FOLDER_ID = "13rb_n3nWQf3Yty26S0wt9yEehddCAagp"

        // Carpeta local donde se descargan los archivos de Drive
        const val DRIVE_CACHE_DIR = "/storage/emulated/0/NEXUS/DriveCache"
        const val TAG = "NEXUS_DRIVE"

        // Permiso lectura Y escritura — todos pueden subir archivos a la carpeta
        val DRIVE_SCOPE = Scope(DriveScopes.DRIVE_FILE)
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive? = try {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .also { it.selectedAccount = account.account }
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("NEXUS").build()
    } catch (e: Exception) {
        Log.e(TAG, "Error creando Drive service: ${e.message}")
        null
    }

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)
    }

    fun getSignedInAccountEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    fun getSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_SCOPE)
            .build()

    // ── Listar archivos en la carpeta NEXUS ───────────────────────────────────
    suspend fun listFilesInFolder(): List<DriveFileInfo> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext emptyList()
        val drive = getDriveService(account) ?: return@withContext emptyList()
        try {
            drive.files().list()
                .setQ("'$NEXUS_FOLDER_ID' in parents and trashed = false")
                .setFields("files(id, name, mimeType, size, modifiedTime)")
                .setPageSize(100)
                .execute()
                .files.map {
                    DriveFileInfo(
                        id = it.id,
                        name = it.name,
                        mimeType = it.mimeType ?: "",
                        sizeBytes = it.getSize() ?: 0L,
                        modifiedTime = it.modifiedTime?.value ?: 0L
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "listFiles: ${e.message}")
            emptyList()
        }
    }

    // ── Descargar archivo al cache local ──────────────────────────────────────
    suspend fun downloadFile(fileInfo: DriveFileInfo): File? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        val drive = getDriveService(account) ?: return@withContext null
        try {
            val cacheDir = File(DRIVE_CACHE_DIR).also { it.mkdirs() }
            val localFile = File(cacheDir, fileInfo.name)
            // No re-descarga si ya está y no cambió
            if (localFile.exists() && localFile.lastModified() >= fileInfo.modifiedTime) {
                return@withContext localFile
            }
            drive.files().get(fileInfo.id)
                .executeMediaAndDownloadTo(localFile.outputStream())
            Log.d(TAG, "Descargado: ${fileInfo.name}")
            localFile
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile ${fileInfo.name}: ${e.message}")
            null
        }
    }

    // ── Subir archivo a la carpeta NEXUS ──────────────────────────────────────
    suspend fun uploadFile(localFile: File): Boolean = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
        val drive = getDriveService(account) ?: return@withContext false
        try {
            val metadata = com.google.api.services.drive.model.File().apply {
                name = localFile.name
                parents = listOf(NEXUS_FOLDER_ID)
            }
            val mediaContent = com.google.api.client.http.FileContent(
                getMimeType(localFile.extension), localFile
            )
            drive.files().create(metadata, mediaContent)
                .setFields("id, name")
                .execute()
            Log.d(TAG, "Subido: ${localFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "uploadFile: ${e.message}")
            false
        }
    }

    // ── Sincronizar Drive → cache local ───────────────────────────────────────
    suspend fun syncDriveFolder(
        folderId: String = NEXUS_FOLDER_ID,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<File> = withContext(Dispatchers.IO) {
        if (!isSignedIn()) return@withContext emptyList()
        val files = listFilesInFolder()
        if (files.isEmpty()) return@withContext emptyList()
        val downloaded = mutableListOf<File>()
        files.forEachIndexed { i, fileInfo ->
            onProgress(i.toFloat() / files.size, fileInfo.name)
            downloadFile(fileInfo)?.let { downloaded.add(it) }
        }
        downloaded
    }

    // ── Buscar archivos en Drive por nombre ───────────────────────────────────
    suspend fun searchDriveFiles(query: String, folderId: String = NEXUS_FOLDER_ID): List<DocumentResult> =
        withContext(Dispatchers.IO) {
            if (!isSignedIn()) return@withContext emptyList()
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: return@withContext emptyList()
                val drive = getDriveService(account) ?: return@withContext emptyList()
                drive.files().list()
                    .setQ("'$folderId' in parents and name contains '$query' and trashed = false")
                    .setFields("files(id, name, mimeType, size)")
                    .setPageSize(20)
                    .execute()
                    .files.map {
                        DocumentResult(
                            name = it.name.substringBeforeLast("."),
                            path = "drive://${it.id}",
                            extension = it.name.substringAfterLast(".", ""),
                            snippet = "☁️ Google Drive — carpeta NEXUS compartida",
                            score = 0.9f,
                            source = "drive"
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "searchDriveFiles: ${e.message}")
                emptyList()
            }
        }

    private fun getMimeType(ext: String) = when (ext.lowercase()) {
        "pdf"  -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt"  -> "text/plain"
        "csv"  -> "text/csv"
        else   -> "application/octet-stream"
    }
}

data class DriveFileInfo(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: Long
)
