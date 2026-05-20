package com.example.ingredient

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extracts clean menu text from raw page content (Next.js __NEXT_DATA__, API JSON blobs, etc.)
 * before sending to the LLM — removes noise, reduces tokens, improves accuracy.
 */
object MenuContentPreprocessor {

    private const val TAG = "MenuPreprocessor"

    fun preprocess(content: String): String {
        // Next.js / Nuxt embedded data
        if (content.startsWith("DATI JSON PAGINA")) {
            val json = content.substringAfter(":\n").trim()
            val extracted = extractFromNextData(json)
            if (extracted.length > 200) {
                Log.d(TAG, "Extracted ${extracted.length} chars from Next.js data")
                return extracted
            }
            // Preprocessor couldn't find menu structure → signal to use visible text fallback
            Log.w(TAG, "Next.js data found but no menu structure detected — falling back to visible text")
            return ""
        }

        // API JSON blob
        if (content.startsWith("DATI API JSON DEL SITO:")) {
            val apiPart = content.substringAfter("DATI API JSON DEL SITO:\n")
                .substringBefore("\n\n---\n")
            val extracted = extractFromApiJson(apiPart)
            if (extracted.length > 200) {
                Log.d(TAG, "Extracted ${extracted.length} chars from API JSON")
                return extracted
            }
            // Fall through to raw content
            val visibleText = content.substringAfter("---\nTESTO VISIBILE PAGINA:\n", "")
            if (visibleText.length > 100) return visibleText
        }

        return content
    }

    // ── Next.js __NEXT_DATA__ ──────────────────────────────────────────────────

    private fun extractFromNextData(raw: String): String {
        return try {
            val root = JSONObject(raw)
            val sb = StringBuilder()

            // Try common paths for JustEat IT and similar Next.js food platforms
            val candidates = listOf(
                "props.pageProps.menu",
                "props.pageProps.initialData.menuData",
                "props.pageProps.restaurantDetails.menu",
                "props.pageProps.restaurant.menu",
                "props.pageProps.data.menu",
                "props.pageProps.menuData",
                "props.pageProps.initialProps.menu"
            )

            for (path in candidates) {
                val node = getPath(root, path)
                if (node != null) {
                    flattenMenuNode(node, sb)
                    if (sb.isNotBlank()) {
                        Log.d(TAG, "Found menu at path: $path")
                        return sb.toString()
                    }
                }
            }

            // Fallback: recursive search for arrays that look like menu categories
            searchMenuArrays(root, sb, depth = 0)
            sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Next.js data: ${e.message}")
            ""
        }
    }

    private fun getPath(obj: JSONObject, path: String): Any? {
        return try {
            var current: Any = obj
            for (key in path.split(".")) {
                current = when (current) {
                    is JSONObject -> current.opt(key) ?: return null
                    else -> return null
                }
            }
            current
        } catch (e: Exception) { null }
    }

    private fun flattenMenuNode(node: Any, sb: StringBuilder) {
        when (node) {
            is JSONObject -> {
                // Looks like a menu object with categories
                val catArray = node.optJSONArray("categories")
                    ?: node.optJSONArray("menuCategories")
                    ?: node.optJSONArray("sections")
                if (catArray != null) {
                    flattenMenuNode(catArray, sb)
                    return
                }
                // Looks like a single item
                val name = node.optString("name", "").ifBlank { node.optString("title", "") }
                val desc = node.optString("description", "")
                    .ifBlank { node.optString("ingredients", "") }
                val price = node.optDouble("price", 0.0)
                    .takeIf { it > 0 } ?: node.optJSONObject("price")?.optDouble("value", 0.0)
                if (name.isNotBlank()) {
                    sb.append(name)
                    if (desc.isNotBlank()) sb.append(": $desc")
                    if (price != null && price > 0) sb.append(" (€${"%.2f".format(price)})")
                    sb.append("\n")
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val item = node.opt(i) ?: continue
                    when (item) {
                        is JSONObject -> {
                            // Category object?
                            val catName = item.optString("name", "")
                                .ifBlank { item.optString("categoryName", "") }
                                .ifBlank { item.optString("title", "") }
                            val items = item.optJSONArray("items")
                                ?: item.optJSONArray("dishes")
                                ?: item.optJSONArray("products")
                                ?: item.optJSONArray("menuItems")
                            if (items != null && catName.isNotBlank()) {
                                sb.append("\n## $catName\n")
                                flattenMenuNode(items, sb)
                            } else {
                                flattenMenuNode(item, sb)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun searchMenuArrays(obj: JSONObject, sb: StringBuilder, depth: Int) {
        if (depth > 8) return
        for (key in obj.keys()) {
            val value = obj.opt(key) ?: continue
            when (value) {
                is JSONArray -> {
                    if (looksLikeMenuArray(value)) {
                        Log.d(TAG, "Found menu-like array at key: $key (depth=$depth)")
                        flattenMenuNode(value, sb)
                        if (sb.length > 500) return
                    } else {
                        // Recurse into array elements
                        for (i in 0 until minOf(value.length(), 5)) {
                            val item = value.opt(i)
                            if (item is JSONObject) searchMenuArrays(item, sb, depth + 1)
                        }
                    }
                }
                is JSONObject -> searchMenuArrays(value, sb, depth + 1)
            }
        }
    }

    private fun looksLikeMenuArray(arr: JSONArray): Boolean {
        if (arr.length() < 2) return false
        var hits = 0
        for (i in 0 until minOf(arr.length(), 5)) {
            val item = arr.optJSONObject(i) ?: continue
            val keys = item.keys().asSequence().map { it.lowercase() }.toSet()
            val menuKeys = setOf("name", "title", "description", "price", "category",
                "ingredients", "items", "dishes", "products", "allergen")
            if (keys.intersect(menuKeys).size >= 2) hits++
        }
        return hits >= 2
    }

    // ── Generic API JSON ───────────────────────────────────────────────────────

    private fun extractFromApiJson(raw: String): String {
        return try {
            val sb = StringBuilder()
            when {
                raw.trimStart().startsWith("[") -> flattenMenuNode(JSONArray(raw), sb)
                raw.trimStart().startsWith("{") -> flattenMenuNode(JSONObject(raw), sb)
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
