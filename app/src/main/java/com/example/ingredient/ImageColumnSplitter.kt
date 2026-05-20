package com.example.ingredient

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min

class ImageColumnSplitter {
    private val TAG = "ImageColumnSplitter"

    data class ColumnRegion(
        val startX: Int,
        val endX: Int,
        val bitmap: Bitmap
    )

    /**
     * Automatically detect and split image into columns
     * @param bitmap The original menu image
     * @param minColumnWidth Minimum width for a column (default 100px)
     * @param sensitivity How sensitive to gaps (0.0-1.0, default 0.3)
     * @return List of column regions with their bitmaps
     */
    fun splitIntoColumns(
        bitmap: Bitmap,
        minColumnWidth: Int = 100,
        sensitivity: Float = 0.3f
    ): List<ColumnRegion> {
        Log.d(TAG, "Starting column detection for image ${bitmap.width}x${bitmap.height}")

        // Calculate vertical projection (text density per column)
        val projection = calculateVerticalProjection(bitmap)

        // Find column boundaries based on gaps in text
        val columnBoundaries = detectColumnBoundaries(
            projection,
            minColumnWidth,
            sensitivity
        )

        // If no columns detected, return the whole image
        if (columnBoundaries.isEmpty()) {
            Log.d(TAG, "No columns detected, returning full image")
            return listOf(ColumnRegion(0, bitmap.width, bitmap))
        }

        Log.d(TAG, "Detected ${columnBoundaries.size} columns: $columnBoundaries")

        // Extract bitmap for each column
        val columns = mutableListOf<ColumnRegion>()
        for (i in columnBoundaries.indices) {
            val startX = columnBoundaries[i].first
            val endX = columnBoundaries[i].second
            val width = endX - startX

            if (width > 0) {
                try {
                    val columnBitmap = Bitmap.createBitmap(
                        bitmap,
                        startX,
                        0,
                        width,
                        bitmap.height
                    )
                    columns.add(ColumnRegion(startX, endX, columnBitmap))
                    Log.d(TAG, "Extracted column ${i+1}: x=$startX to $endX, width=$width")
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting column $i", e)
                }
            }
        }

        return columns
    }

    /**
     * Calculate vertical projection - how much text/content is in each column
     */
    private fun calculateVerticalProjection(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val projection = IntArray(width)

        // Convert to grayscale and count dark pixels per column
        for (x in 0 until width) {
            var darkPixelCount = 0
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3

                // Consider pixel as "text" if it's darker than threshold
                if (gray < 200) {
                    darkPixelCount++
                }
            }
            projection[x] = darkPixelCount
        }

        // Apply smoothing to reduce noise
        return smoothProjection(projection, windowSize = 5)
    }

    /**
     * Smooth the projection using moving average
     */
    private fun smoothProjection(projection: IntArray, windowSize: Int): IntArray {
        val smoothed = IntArray(projection.size)
        val halfWindow = windowSize / 2

        for (i in projection.indices) {
            val start = max(0, i - halfWindow)
            val end = min(projection.size - 1, i + halfWindow)
            var sum = 0
            var count = 0

            for (j in start..end) {
                sum += projection[j]
                count++
            }

            smoothed[i] = sum / count
        }

        return smoothed
    }

    /**
     * Detect column boundaries based on gaps in the projection
     */
    private fun detectColumnBoundaries(
        projection: IntArray,
        minColumnWidth: Int,
        sensitivity: Float
    ): List<Pair<Int, Int>> {
        val width = projection.size

        // Calculate threshold for "empty" space
        val maxDensity = projection.maxOrNull() ?: 0
        val threshold = (maxDensity * sensitivity).toInt()

        Log.d(TAG, "Max density: $maxDensity, Threshold: $threshold")

        val columns = mutableListOf<Pair<Int, Int>>()
        var inColumn = false
        var columnStart = 0
        var emptyStreak = 0
        val minEmptyGap = 20 // Minimum pixels of empty space to consider a gap

        for (x in projection.indices) {
            val isEmpty = projection[x] < threshold

            if (!inColumn && !isEmpty) {
                // Start of a new column
                columnStart = x
                inColumn = true
                emptyStreak = 0
            } else if (inColumn && isEmpty) {
                // Potentially in a gap between columns
                emptyStreak++

                // If we've seen enough empty space, end the column
                if (emptyStreak >= minEmptyGap) {
                    val columnEnd = x - emptyStreak
                    val columnWidth = columnEnd - columnStart

                    if (columnWidth >= minColumnWidth) {
                        columns.add(Pair(columnStart, columnEnd))
                        Log.d(TAG, "Column found: $columnStart to $columnEnd (width: $columnWidth)")
                    }

                    inColumn = false
                    emptyStreak = 0
                }
            } else if (inColumn && !isEmpty) {
                // Still in column, reset empty streak
                emptyStreak = 0
            }
        }

        // Add the last column if we're still in one
        if (inColumn) {
            val columnWidth = width - columnStart
            if (columnWidth >= minColumnWidth) {
                columns.add(Pair(columnStart, width))
                Log.d(TAG, "Final column: $columnStart to $width (width: $columnWidth)")
            }
        }

        // If no columns detected, return full width as single column
        if (columns.isEmpty()) {
            return listOf(Pair(0, width))
        }

        return columns
    }

    /**
     * Visualize the column detection (optional, for debugging)
     * Returns a bitmap with vertical lines showing detected columns
     */
    fun visualizeColumns(bitmap: Bitmap, columns: List<ColumnRegion>): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)

        for (column in columns) {
            // Draw vertical lines at column boundaries
            for (y in 0 until result.height) {
                // Red line at start
                if (column.startX < result.width) {
                    result.setPixel(column.startX, y, Color.RED)
                }
                // Blue line at end
                if (column.endX - 1 < result.width) {
                    result.setPixel(column.endX - 1, y, Color.BLUE)
                }
            }
        }

        return result
    }
}