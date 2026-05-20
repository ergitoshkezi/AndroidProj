package com.example.ingredient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

object ImageMenuExtractor {

    private const val TAG = "ImageMenuExtractor"
    private const val MIN_OCR_WIDTH = 1400
    private val PRICE_REGEX = Regex("""€?\s*(\d{1,3}[.,]\d{2})\b""")
    private var openCvReady = false

    private fun ensureOpenCv(): Boolean {
        if (!openCvReady) openCvReady = OpenCVLoader.initLocal()
        return openCvReady
    }

    // ─── Public entry point ────────────────────────────────────────────────────

    suspend fun extractText(
        context: Context,
        uris: List<Uri>,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val mlKit = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()

        uris.forEachIndexed { idx, uri ->
            onProgress("Preprocessing foto ${idx + 1}/${uris.size}…")

            val raw = try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { Log.e(TAG, "Decode failed $uri", e); null }
                ?: return@forEachIndexed

            val text = try {
                val processed = preprocessWithOpenCv(raw)
                onProgress("OCR foto ${idx + 1}/${uris.size}…")
                val mlResult = recognizeMlKit(InputImage.fromBitmap(processed, 0), mlKit)
                if (processed !== raw) processed.recycle()
                buildStructuredText(mlResult, raw.width)
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline failed photo ${idx + 1}", e)
                ""
            } finally {
                raw.recycle()
            }

            if (text.isNotBlank()) {
                if (uris.size > 1) sb.append("\n\n=== FOTO ${idx + 1} ===\n")
                sb.append(text)
            }
            Log.d(TAG, "Photo ${idx + 1}: ${text.length} chars")
        }

        mlKit.close()
        Log.d(TAG, "Total: ${sb.length} chars from ${uris.size} photos")
        sb.toString()
    }

    // ─── Step 1: OpenCV Preprocessing ─────────────────────────────────────────

    private fun preprocessWithOpenCv(src: Bitmap): Bitmap {
        if (!ensureOpenCv()) {
            Log.w(TAG, "OpenCV unavailable, skipping preprocessing")
            return src
        }
        return try {
            val scaled = if (src.width < MIN_OCR_WIDTH) {
                val s = MIN_OCR_WIDTH.toFloat() / src.width
                Bitmap.createScaledBitmap(src, MIN_OCR_WIDTH, (src.height * s).toInt(), true)
            } else src

            val mat = Mat()
            Utils.bitmapToMat(scaled, mat)
            if (scaled !== src) scaled.recycle()

            // Grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            mat.release()

            // Deskew
            val deskewed = deskew(gray)
            gray.release()

            // Adaptive threshold (handles uneven lighting)
            val thresh = Mat()
            Imgproc.adaptiveThreshold(
                deskewed, thresh, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                15, 8.0
            )
            deskewed.release()

            // Sharpen via unsharp mask
            val blurred = Mat()
            Imgproc.GaussianBlur(thresh, blurred, Size(0.0, 0.0), 2.0)
            val sharpened = Mat()
            Core.addWeighted(thresh, 1.5, blurred, -0.5, 0.0, sharpened)
            thresh.release(); blurred.release()

            // Back to ARGB Bitmap for ML Kit
            val result = Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888)
            val rgba = Mat()
            Imgproc.cvtColor(sharpened, rgba, Imgproc.COLOR_GRAY2BGRA)
            sharpened.release()
            Utils.matToBitmap(rgba, result)
            rgba.release()
            result
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV preprocessing failed, using raw", e)
            src
        }
    }

    private fun deskew(gray: Mat): Mat {
        return try {
            val edges = Mat()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            val lines = Mat()
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 80, 50.0, 10.0)
            edges.release()

            if (lines.rows() == 0) return gray.clone().also { lines.release() }

            val angles = mutableListOf<Double>()
            for (i in 0 until lines.rows()) {
                val l = lines.get(i, 0)
                val angle = Math.toDegrees(atan2(l[3] - l[1], l[2] - l[0]))
                if (abs(angle) < 15) angles.add(angle)
            }
            lines.release()

            if (angles.isEmpty() || abs(angles.sorted()[angles.size / 2]) < 0.5) return gray.clone()

            val median = angles.sorted()[angles.size / 2]
            val center = Point(gray.cols() / 2.0, gray.rows() / 2.0)
            val rotMat = Imgproc.getRotationMatrix2D(center, median, 1.0)
            val rotated = Mat()
            Imgproc.warpAffine(gray, rotated, rotMat, gray.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            rotMat.release()
            Log.d(TAG, "Deskewed ${median.roundToInt()}°")
            rotated
        } catch (e: Exception) {
            Log.w(TAG, "Deskew failed: ${e.message}")
            gray.clone()
        }
    }

    // ─── Step 2: ML Kit OCR ────────────────────────────────────────────────────

    private suspend fun recognizeMlKit(
        image: InputImage,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): com.google.mlkit.vision.text.Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    // ─── Steps 3–10: Layout graph → structured output ─────────────────────────

    private data class OcrLine(
        val text: String,
        val left: Int, val right: Int,
        val top: Int, val bottom: Int,
        val cy: Float = (top + bottom) / 2f,
        val cx: Float = (left + right) / 2f,
        val height: Int = bottom - top
    )

    private fun buildStructuredText(result: com.google.mlkit.vision.text.Text, imageWidth: Int): String {
        // Step 3: normalize
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val text = normalizeText(line.text) ?: return@mapNotNull null
            OcrLine(text, box.left, box.right, box.top, box.bottom)
        }
        if (lines.isEmpty()) return result.text.trim()

        val avgLineH = lines.map { it.height }.average().toFloat()

        // Step 4: layout graph
        val graph = buildLayoutGraph(lines, avgLineH)

        // Step 5: column detection
        val splitX = findColumnSplit(lines, imageWidth)

        // Step 6: reading order
        val ordered = if (splitX == null) {
            lines.sortedBy { it.cy }
        } else {
            val left = lines.filter { it.cx <= splitX }.sortedBy { it.cy }
            val right = lines.filter { it.cx > splitX }.sortedBy { it.cy }
            Log.d(TAG, "2 cols split at x=${splitX.toInt()}: left=${left.size} right=${right.size}")
            left + right
        }

        // Steps 7–10
        return buildSectionedOutput(ordered, graph, avgLineH)
    }

    // Step 3
    private fun normalizeText(raw: String): String? {
        val t = raw.trim().replace(Regex("[|}{\\[\\]\\\\~`]"), "").replace(Regex("\\s{2,}"), " ").trim()
        return if (t.length < 2) null else t
    }

    // Step 4: layout graph — each line → lines directly below in same horizontal zone
    private fun buildLayoutGraph(lines: List<OcrLine>, avgLineH: Float): Map<OcrLine, List<OcrLine>> {
        val maxGap = avgLineH * 1.8f
        return lines.associateWith { line ->
            lines.filter { other ->
                other !== line &&
                other.cy > line.cy &&
                other.cy - line.bottom < maxGap &&
                other.left >= line.left - 30 &&
                other.left <= line.right + 30
            }.sortedBy { it.cy }
        }
    }

    // Step 5
    private fun findColumnSplit(lines: List<OcrLine>, imageWidth: Int): Float? {
        val centers = lines.map { it.cx }.sorted()
        if (centers.size < 6) return null
        var bestGap = 0f; var bestSplit: Float? = null
        for (i in 0 until centers.size - 1) {
            val gap = centers[i + 1] - centers[i]
            if (gap > bestGap && gap > imageWidth * 0.12f) { bestGap = gap; bestSplit = (centers[i] + centers[i + 1]) / 2f }
        }
        return bestSplit
    }

    // Steps 7–10
    private fun buildSectionedOutput(ordered: List<OcrLine>, graph: Map<OcrLine, List<OcrLine>>, avgLineH: Float): String {
        data class Item(val name: String, val price: String?, val description: String?, val confidence: Float)

        val sections = mutableListOf<Pair<String, MutableList<Item>>>()
        var currentSection = "Menu"
        var currentItems = mutableListOf<Item>()
        val consumed = mutableSetOf<OcrLine>()
        var prevCy = ordered.firstOrNull()?.cy ?: 0f

        for (line in ordered) {
            if (line in consumed) continue
            val gap = abs(line.cy - prevCy); prevCy = line.cy

            // Step 7: section header
            if (isSectionHeader(line, gap, avgLineH)) {
                if (currentItems.isNotEmpty()) { sections.add(Pair(currentSection, currentItems)); currentItems = mutableListOf() }
                currentSection = toTitleCase(line.text)
                Log.d(TAG, "Section: '$currentSection'")
                continue
            }

            // Step 8: item + price pairing
            val priceInLine = PRICE_REGEX.find(line.text)
            val itemName: String; val linePrice: String?
            if (priceInLine != null) {
                linePrice = priceInLine.groupValues[1].replace(",", ".")
                itemName = line.text.replace(priceInLine.value, "").trim().trimEnd('.')
            } else {
                val rightNeighbor = ordered.firstOrNull { other ->
                    other !== line && other !in consumed &&
                    abs(other.cy - line.cy) < avgLineH * 0.6f &&
                    other.left > line.right &&
                    PRICE_REGEX.containsMatchIn(other.text)
                }
                linePrice = rightNeighbor?.let { PRICE_REGEX.find(it.text)!!.groupValues[1].replace(",", ".").also { _ -> consumed.add(rightNeighbor) } }
                itemName = line.text
            }
            if (itemName.length < 2) continue

            // Step 9: description attach
            val descLines = graph[line]?.filter { it !in consumed && !isSectionHeader(it, 0f, avgLineH) && !PRICE_REGEX.containsMatchIn(it.text) && it.height <= line.height * 1.1f }?.take(2) ?: emptyList()
            val description = descLines.joinToString(" ") { it.text }.takeIf { it.isNotBlank() }
            descLines.forEach { consumed.add(it) }

            // Step 10: confidence scoring
            val confidence = run {
                var s = 0f
                if (itemName.length in 3..50) s += 0.35f
                if (linePrice != null) s += 0.35f
                if (description != null) s += 0.15f
                val pv = linePrice?.toDoubleOrNull()
                if (pv != null && pv in 0.5..120.0) s += 0.15f
                s
            }

            if (confidence >= 0.35f) {
                currentItems.add(Item(itemName, linePrice, description, confidence))
                Log.d(TAG, "  Item(${" %.2f".format(confidence)}): '$itemName' | ${linePrice ?: "-"}")
            }
        }
        if (currentItems.isNotEmpty()) sections.add(Pair(currentSection, currentItems))

        val valid = sections.filter { it.second.isNotEmpty() }
        return if (valid.size <= 1) {
            valid.flatMap { it.second }.joinToString("\n") { i ->
                buildString { append(i.name); if (i.price != null) append(" | €${i.price}"); if (i.description != null) append("\n  ${i.description}") }
            }
        } else {
            valid.joinToString("\n\n") { (h, items) ->
                "=== CATEGORIA: $h ===\n" + items.joinToString("\n") { i ->
                    buildString { append(i.name); if (i.price != null) append(" | €${i.price}"); if (i.description != null) append("\n  ${i.description}") }
                }
            }
        }
    }

    private fun isSectionHeader(line: OcrLine, gap: Float, avgLineH: Float): Boolean {
        val t = line.text; if (t.length < 3 || t.length > 40) return false
        val upperRatio = t.count { it.isUpperCase() }.toFloat() / t.count { it.isLetter() }.coerceAtLeast(1)
        return upperRatio > 0.7f && !PRICE_REGEX.containsMatchIn(t) && t.count { it.isDigit() } < t.length / 3 && (gap > avgLineH * 2f || t.length < 25)
    }

    private fun toTitleCase(text: String) =
        text.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
}
