package com.example.ingredient

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Composable for manually selecting column divisions in an image with draggable lines
 */
@Composable
fun ManualColumnSelector(
    bitmap: Bitmap,
    onColumnsConfirmed: (List<Pair<Int, Int>>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var columnLines by remember { mutableStateOf<List<Float>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggedLineIndex by remember { mutableStateOf<Int?>(null) }

    // Calculate scale factor between displayed image and actual bitmap
    val scaleFactor = remember(canvasSize, bitmap) {
        if (canvasSize.width > 0) {
            bitmap.width.toFloat() / canvasSize.width.toFloat()
        } else 1f
    }

    // Touch threshold for detecting line grabs (in pixels)
    val touchThreshold = 30f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Manual Column Selection",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "• Tap to add dividers\n• Drag to reposition\n• Tap line to remove",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Image with tap and drag detection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    canvasSize = coordinates.size
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Check if tap is near existing line
                            val nearLineIndex = columnLines.indexOfFirst { line ->
                                abs(line - offset.x) < touchThreshold
                            }

                            if (nearLineIndex >= 0) {
                                // Remove line if tapped
                                columnLines = columnLines.filterIndexed { index, _ ->
                                    index != nearLineIndex
                                }
                            } else {
                                // Add new line at tap position
                                columnLines = (columnLines + offset.x).sorted()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // Find which line is being dragged
                                val nearLineIndex = columnLines.indexOfFirst { line ->
                                    abs(line - offset.x) < touchThreshold
                                }
                                draggedLineIndex = if (nearLineIndex >= 0) nearLineIndex else null
                            },
                            onDrag = { change, dragAmount ->
                                draggedLineIndex?.let { index ->
                                    val currentLines = columnLines.toMutableList()
                                    val newX = (currentLines[index] + dragAmount.x)
                                        .coerceIn(0f, size.width.toFloat())

                                    currentLines[index] = newX
                                    columnLines = currentLines.sorted()

                                    // Update dragged index after sorting
                                    draggedLineIndex = columnLines.indexOf(newX)

                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                draggedLineIndex = null
                            },
                            onDragCancel = {
                                draggedLineIndex = null
                            }
                        )
                    }
            ) {
                // Draw the image
                val imageBitmap = bitmap.asImageBitmap()
                val scale = size.width / bitmap.width
                val scaledHeight = bitmap.height * scale

                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(
                        size.width.toInt(),
                        scaledHeight.toInt()
                    )
                )

                // Draw column divider lines
                columnLines.forEachIndexed { index, x ->
                    val isBeingDragged = index == draggedLineIndex
                    val lineColor = if (isBeingDragged) Color.Yellow else Color.Red
                    val lineWidth = if (isBeingDragged) 5f else 3f

                    // Draw vertical line
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, scaledHeight),
                        strokeWidth = lineWidth
                    )

                    // Draw grab handles (larger circles at top and bottom)
                    val handleRadius = if (isBeingDragged) 12f else 10f
                    val handleColor = if (isBeingDragged) Color.Yellow else Color.Red

                    // Top handle
                    drawCircle(
                        color = handleColor,
                        radius = handleRadius,
                        center = Offset(x, 20f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius - 3f,
                        center = Offset(x, 20f)
                    )

                    // Bottom handle
                    drawCircle(
                        color = handleColor,
                        radius = handleRadius,
                        center = Offset(x, scaledHeight - 20f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius - 3f,
                        center = Offset(x, scaledHeight - 20f)
                    )

                    // Draw column number label
                    drawCircle(
                        color = lineColor,
                        radius = 15f,
                        center = Offset(x, scaledHeight / 2)
                    )
                }

                // Draw semi-transparent overlay for column regions
                if (columnLines.isNotEmpty()) {
                    val regions = getColumnRegions(columnLines, size.width)
                    regions.forEachIndexed { index, region ->
                        val overlayColor = if (index % 2 == 0) {
                            Color(0x20FF0000) // Light red
                        } else {
                            Color(0x200000FF) // Light blue
                        }

                        drawRect(
                            color = overlayColor,
                            topLeft = Offset(region.first, 0f),
                            size = androidx.compose.ui.geometry.Size(
                                region.second - region.first,
                                scaledHeight
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info text
        val columnCount = columnLines.size + 1
        Text(
            text = if (columnLines.isEmpty()) {
                "No dividers - will process as single column"
            } else {
                "$columnCount columns (${columnLines.size} divider${if (columnLines.size > 1) "s" else ""})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onCancel) {
                Text("Cancel")
            }

            Button(
                onClick = { columnLines = emptyList() },
                enabled = columnLines.isNotEmpty()
            ) {
                Text("Clear All")
            }

            Button(
                onClick = {
                    // Convert screen coordinates to bitmap coordinates
                    val bitmapColumns = convertToColumnRegions(
                        columnLines,
                        scaleFactor,
                        bitmap.width
                    )
                    onColumnsConfirmed(bitmapColumns)
                }
            ) {
                Text("Confirm ($columnCount columns)")
            }
        }
    }
}

/**
 * Get column regions for visualization
 */
private fun getColumnRegions(lines: List<Float>, width: Float): List<Pair<Float, Float>> {
    if (lines.isEmpty()) {
        return listOf(Pair(0f, width))
    }

    val regions = mutableListOf<Pair<Float, Float>>()

    // First column
    regions.add(Pair(0f, lines[0]))

    // Middle columns
    for (i in 0 until lines.size - 1) {
        regions.add(Pair(lines[i], lines[i + 1]))
    }

    // Last column
    regions.add(Pair(lines.last(), width))

    return regions
}

/**
 * Convert tap positions to column regions in bitmap coordinates
 */
private fun convertToColumnRegions(
    lines: List<Float>,
    scaleFactor: Float,
    bitmapWidth: Int
): List<Pair<Int, Int>> {
    if (lines.isEmpty()) {
        return listOf(Pair(0, bitmapWidth))
    }

    // Convert screen x positions to bitmap x positions
    val bitmapLines = lines.map { (it * scaleFactor).roundToInt() }.sorted()

    val regions = mutableListOf<Pair<Int, Int>>()

    // First column: from 0 to first line
    regions.add(Pair(0, bitmapLines[0]))

    // Middle columns: between lines
    for (i in 0 until bitmapLines.size - 1) {
        regions.add(Pair(bitmapLines[i], bitmapLines[i + 1]))
    }

    // Last column: from last line to end
    regions.add(Pair(bitmapLines.last(), bitmapWidth))

    return regions
}

/**
 * Process image with manual column regions
 */
fun processImageWithManualColumns(
    tesseract: EnhancedTesseractManager,
    bitmap: Bitmap,
    columnRegions: List<Pair<Int, Int>>,
    lang: String = "spa"
): String {
    val results = mutableListOf<String>()

    for ((index, region) in columnRegions.withIndex()) {
        val startX = region.first
        val endX = region.second
        val width = endX - startX

        if (width > 0) {
            try {
                // Extract column bitmap
                val columnBitmap = Bitmap.createBitmap(
                    bitmap,
                    startX,
                    0,
                    width,
                    bitmap.height
                )

                // Process with Tesseract (no column splitting)
                val text = tesseract.processImage(
                    image = columnBitmap,
                    lang = lang,
                    useColumnSplit = false,
                    addColumnMarkers = false
                )

                if (text.isNotEmpty()) {
                    results.add(text)
                }

            } catch (e: Exception) {
                android.util.Log.e("ManualColumnSelector", "Error processing column $index", e)
            }
        }
    }

    return results.joinToString("\n\n")
}