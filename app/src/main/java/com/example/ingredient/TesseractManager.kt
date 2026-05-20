package com.example.ingredient

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.InputStream

class EnhancedTesseractManager(val context: Context) {

    private val tess = TessBaseAPI()
    private val columnSplitter = ImageColumnSplitter()
    private val folderTessDataName: String = "tessdata"
    private val pathDir = context.getExternalFilesDir(null).toString()
    private var isInitialized = false
    private val TAG = "EnhancedTesseract"

    init {
        try {
            val folder = File(pathDir, folderTessDataName)

            if (!folder.exists()) {
                folder.mkdir()
                Log.d(TAG, "Created tessdata folder at: ${folder.absolutePath}")
            }

            if (folder.exists()) {
                addFileFromAssets("eng.traineddata", "models/eng.traineddata")
                addFileFromAssets("spa.traineddata", "models/spa.traineddata")
                isInitialized = true
                Log.d(TAG, "Tesseract initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Tesseract", e)
            isInitialized = false
        }
    }

    private fun addFileFromAssets(name: String, assetPath: String) {
        val file = File("$pathDir/$folderTessDataName/$name")
        if (!file.exists()) {
            try {
                Log.d(TAG, "Copying $name from assets to ${file.absolutePath}")
                val inputStream: InputStream = context.assets.open(assetPath)
                file.writeBytes(inputStream.readBytes())
                inputStream.close()
                Log.d(TAG, "$name copied successfully. Size: ${file.length() / 1024} KB")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying $name from assets", e)
            }
        } else {
            Log.d(TAG, "$name already exists. Size: ${file.length() / 1024} KB")
        }
    }

    /**
     * Process image with automatic column detection
     * @param image The menu image to process
     * @param lang Language code (e.g., "spa", "eng")
     * @param useColumnSplit Enable automatic column splitting
     * @param minColumnWidth Minimum column width in pixels
     * @param sensitivity Detection sensitivity (0.0-1.0)
     * @param addColumnMarkers Add "COLUMN 1", "COLUMN 2" markers in output
     * @return Recognized text with columns processed separately
     */
    fun processImage(
        image: Bitmap,
        lang: String,
        useColumnSplit: Boolean = true,
        minColumnWidth: Int = 100,
        sensitivity: Float = 0.3f,
        addColumnMarkers: Boolean = false
    ): String {
        return try {
            if (!useColumnSplit) {
                // Standard processing without column split
                return processSimple(image, lang)
            }

            Log.d(TAG, "Processing image with automatic column detection")

            // Detect and split columns
            val columns = columnSplitter.splitIntoColumns(
                image,
                minColumnWidth,
                sensitivity
            )

            Log.d(TAG, "Detected ${columns.size} columns")

            // Process each column separately
            val results = mutableListOf<String>()

            for ((index, column) in columns.withIndex()) {
                Log.d(TAG, "Processing column ${index + 1}/${columns.size}")

                tess.init(pathDir, lang)
                tess.setImage(column.bitmap)
                val columnText = tess.utF8Text.trim()

                if (columnText.isNotEmpty()) {
                    results.add(columnText)
                }

                Log.d(TAG, "Column ${index + 1} text length: ${columnText.length}")
            }

            // Combine results with separator
            val finalText = if (results.size == 1) {
                results[0]
            } else {
                if (addColumnMarkers) {
                    // Add column markers for debugging/visibility
                    results.mapIndexed { index, text ->
                        "--- COLUMN ${index + 1} ---\n\n$text"
                    }.joinToString("\n\n")
                } else {
                    // Clean join - just separate columns with double newline
                    results.joinToString("\n\n")
                }
            }

            Log.d(TAG, "Total recognized text length: ${finalText.length}")
            finalText

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image with column split", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Simple processing without column detection (original method)
     */
    private fun processSimple(image: Bitmap, lang: String): String {
        return try {
            tess.init(pathDir, lang)
            tess.setImage(image)
            val result = tess.utF8Text
            Log.d(TAG, "Text recognition completed. Length: ${result.length}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get visualization of detected columns (for debugging)
     */
    fun getColumnVisualization(image: Bitmap): Bitmap? {
        return try {
            val columns = columnSplitter.splitIntoColumns(image)
            columnSplitter.visualizeColumns(image, columns)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating visualization", e)
            null
        }
    }

    fun isReady(): Boolean = isInitialized

    fun recycle() {
        try {
            tess.recycle()
            Log.d(TAG, "Tesseract recycled")
        } catch (e: Exception) {
            Log.e(TAG, "Error recycling Tesseract", e)
        }
    }
}