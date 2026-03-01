package com.nexus.data.repository

import android.content.Context
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.lang.StringBuilder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentExtractor @Inject constructor(private val context: Context) {

    fun extractText(file: File): String = try {
        when (file.extension.lowercase()) {
            "pdf"        -> extractPdf(file)
            "docx"       -> extractDocx(file)
            "doc"        -> extractDoc(file)
            "xlsx", "xls", "csv" -> extractExcel(file)
            "pptx"       -> extractPptx(file)
            "txt", "md", "log"   -> file.readText(Charsets.UTF_8)
            "json"       -> file.readText(Charsets.UTF_8)
            else         -> ""
        }
    } catch (e: Exception) {
        ""
    }

    private fun extractPdf(file: File): String {
        val text = StringBuilder()
        try {
            val reader = PdfReader(file)
            val pdfDoc = PdfDocument(reader)
            for (i in 1..pdfDoc.numberOfPages) {
                text.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)))
            }
            pdfDoc.close()
        } catch (e: Exception) { }
        return text.toString()
    }

    private fun extractDocx(file: File): String {
        return try {
            XWPFDocument(file.inputStream()).use { doc ->
                doc.paragraphs.joinToString("\n") { it.text }
            }
        } catch (e: Exception) { "" }
    }

    private fun extractDoc(file: File): String {
        return try {
            // Esto usa poi-scratchpad que ya agregamos al gradle
            val hwpf = org.apache.poi.hwpf.HWPFDocument(file.inputStream())
            hwpf.range.text()
        } catch (e: Exception) { "" }
    }

    private fun extractExcel(file: File): String {
        if (file.extension.lowercase() == "csv") return file.readText()
        return try {
            WorkbookFactory.create(file.inputStream()).use { wb ->
                val sb = StringBuilder()
                for (i in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(i)
                    sb.appendLine("SHEET: ${sheet.sheetName}")
                    sheet.forEach { row ->
                        val line = row.joinToString("\t") { cell ->
                            cell.toString()
                        }
                        sb.appendLine(line)
                    }
                }
                sb.toString()
            }
        } catch (e: Exception) { "" }
    }

    private fun extractPptx(file: File): String {
        return try {
            XMLSlideShow(file.inputStream()).use { ppt ->
                ppt.slides.joinToString("\n---\n") { slide ->
                    slide.shapes.joinToString("\n") { shape ->
                        if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape)
                            shape.text else ""
                    }
                }
            }
        } catch (e: Exception) { "" }
    }

    val supportedExtensions = setOf(
        "pdf", "docx", "doc", "xlsx", "xls", "csv",
        "pptx", "txt", "md", "log", "json"
    )
}
