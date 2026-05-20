package com.example.ingredient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object PdfMenuExtractor {

    private const val TAG = "PdfMenuExtractor"
    private const val MAX_PAGES = 15
    private const val PAGE_WIDTH_PX = 1200

    /** Returns true if the URL likely points to a PDF (by URL pattern or HEAD content-type). */
    suspend fun isPdfUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.contains(".pdf", ignoreCase = true)) return@withContext true
        // Check content-type via HEAD request (catches redirected or obfuscated PDF links)
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.connect()
            val ct = conn.contentType ?: ""
            conn.disconnect()
            ct.contains("pdf", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    /**
     * Downloads a PDF from [url], renders each page with PdfRenderer,
     * runs Tesseract OCR on each page, and returns the combined text.
     */
    suspend fun extractText(
        context: Context,
        url: String,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        onProgress("Download PDF in corso…")
        val pdfFile = downloadPdf(context, url)
            ?: return@withContext ""

        try {
            onProgress("Apertura PDF…")
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, MAX_PAGES)
            Log.d(TAG, "PDF has ${renderer.pageCount} pages, processing $pageCount")

            val tesseract = EnhancedTesseractManager(context)
            val ocrLang = detectOcrLang(url)
            Log.d(TAG, "Using OCR lang: $ocrLang for URL: $url")
            val allText = StringBuilder()

            for (i in 0 until pageCount) {
                onProgress("OCR pagina ${i + 1}/$pageCount…")
                val page = renderer.openPage(i)

                // Render to bitmap
                val ratio = page.height.toFloat() / page.width.toFloat()
                val bmpWidth = PAGE_WIDTH_PX
                val bmpHeight = (bmpWidth * ratio).toInt()
                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)

                // Fill white background (PdfRenderer leaves alpha channel)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageText = tesseract.processImage(
                    image = bitmap,
                    lang = ocrLang,
                    useColumnSplit = true
                )
                bitmap.recycle()

                if (pageText.isNotBlank()) {
                    allText.append("\n\n=== PAGINA ${i + 1} ===\n")
                    allText.append(pageText)
                    Log.d(TAG, "Page ${i + 1}: ${pageText.length} chars")
                }
            }

            renderer.close()
            pfd.close()
            tesseract.recycle()

            Log.d(TAG, "Total PDF text: ${allText.length} chars")
            allText.toString()
        } finally {
            pdfFile.delete()
        }
    }

    /**
     * Detects OCR language from URL.
     * Returns "spa" (which is the Italian tessdata in this project) for Italian sites,
     * "eng" for English/international sites.
     */
    private fun detectOcrLang(url: String): String {
        val lower = url.lowercase()
        val italianSignals = listOf(
            ".it/", ".it?", ".it#", // Italian TLD
            "ristorante", "pizzeria", "trattoria", "osteria",
            "cucina", "piatti", "/it/", "menu-ita", "italiano",
            "gelato", "pasta", "antipasti", "secondi", "dolci"
        )
        return if (italianSignals.any { lower.contains(it) }) "spa" else "eng"
    }

    private fun downloadPdf(context: Context, url: String): File? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0.0.0"
            )
            conn.connect()

            // Follow redirects manually if needed
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Log.e(TAG, "HTTP $responseCode downloading PDF")
                return null
            }

            val tmpFile = File(context.cacheDir, "menu_tmp_${System.currentTimeMillis()}.pdf")
            FileOutputStream(tmpFile).use { out ->
                conn.inputStream.copyTo(out)
            }
            conn.disconnect()
            Log.d(TAG, "PDF downloaded: ${tmpFile.length() / 1024} KB")
            tmpFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download PDF", e)
            null
        }
    }
}
