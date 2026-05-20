package com.example.ingredient

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class MenuItem(
    val name: String = "",
    val description: String = "",
    val allergens: List<String> = emptyList(),
    val price: Double = 0.0,
    val originalPrice: Double? = null,
    val isOffer: Boolean = false,
    val country: String = "",
    val region: String = "",
    val cucina: String = "",
    val calories: Int = 0
)

data class MenuCategory(
    val categoryName: String = "",
    val dishes: List<MenuItem> = emptyList()
)

class MenuParser {
    private val TAG = "MenuParser"

    /**
     * Parse the LLM response text file and convert to structured menu data
     */
    fun parseMenuText(menuText: String): List<MenuCategory> {
        val categories = mutableMapOf<String, MutableList<MenuItem>>()

        try {
            // Split by parts (=== PARTE N ===)
            val parts = menuText.split("=== PARTE")
                .filter { it.isNotBlank() }

            for (part in parts) {
                // Extract JSON from each part
                val jsonContent = extractJsonFromPart(part)

                if (jsonContent != null) {
                    parseJsonContent(jsonContent, categories)
                }
            }

            Log.d(TAG, "Parsed ${categories.size} categories")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing menu text", e)
        }

        // Convert map to list of MenuCategory
        return categories
            .map { (categoryName, dishes) ->
                MenuCategory(categoryName, dishes)
            }.sortedBy { it.categoryName }
    }

    /**
     * Extract JSON content from a part of the text, now strictly looking for a JSON array [ ]
     */
    private fun extractJsonFromPart(part: String): String? {
        // Remove any markdown blocks or descriptive text before looking for JSON
        val cleanedPart = part.substringAfter("```json", part).substringBeforeLast("```", part).trim()

        return try {
            // Find JSON between [ and ] (we expect an array now)
            val jsonArrayStart = cleanedPart.indexOf('[')

            if (jsonArrayStart == -1) {
                return null
            }

            val jsonArrayEnd = cleanedPart.lastIndexOf(']')

            if (jsonArrayStart != -1 && jsonArrayEnd != -1 && jsonArrayEnd > jsonArrayStart) {
                // Include surrounding [ and ]
                cleanedPart.substring(jsonArrayStart, jsonArrayEnd + 1)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting JSON", e)
            null
        }
    }

    /**
     * Parse JSON content, now strictly expecting an Array.
     * Falls back to truncation-repair if the LLM hit the token limit.
     */
    private fun parseJsonContent(
        jsonContent: String,
        categories: MutableMap<String, MutableList<MenuItem>>
    ) {
        try {
            val jsonArray = JSONArray(jsonContent)
            parseJsonArray(jsonArray, categories)
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed (possibly truncated), attempting repair…")
            val repaired = repairTruncatedJsonArray(jsonContent)
            if (repaired != null) {
                try {
                    parseJsonArray(JSONArray(repaired), categories)
                    Log.d(TAG, "JSON repair succeeded")
                } catch (e2: Exception) {
                    Log.e(TAG, "JSON repair also failed", e2)
                }
            } else {
                Log.e(TAG, "Could not repair JSON", e)
            }
        }
    }

    /**
     * Repairs a JSON array that was truncated mid-object by the LLM token limit.
     * Finds the last complete top-level object (depth-1 '}') and closes the array there.
     */
    private fun repairTruncatedJsonArray(json: String): String? {
        var depth = 0
        var inString = false
        var escape = false
        var lastCompleteObjectEnd = -1

        for (i in json.indices) {
            val c = json[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{', '[' -> depth++
                '}' -> {
                    depth--
                    if (depth == 1) lastCompleteObjectEnd = i   // directly inside outer array
                }
                ']' -> depth--
            }
        }

        if (lastCompleteObjectEnd == -1) return null
        return json.substring(0, lastCompleteObjectEnd + 1) + "]"
    }

    /**
     * Parse JSON array format (standardized LLM output)
     */
    private fun parseJsonArray(
        jsonArray: JSONArray,
        categories: MutableMap<String, MutableList<MenuItem>>
    ) {
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)

            val category = item.optString("Categoria", "Other").trim()
            val name = item.optString("Piatti", "").trim()
            val description = item.optString("Descrizione/Ingredienti/Extra", "").trim()
            val allergens = item.optString("Allergeni", "").trim()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val price = cleanPrice(item.optString("Prezzo", "0"))
            val calories = item.optInt("Calorie", 0)
            val country = item.optString("Paese", "").trim()
            val region = item.optString("Regione", "").trim()

            // Only add if there is a name (price can be 0 when not listed on the website)
            if (name.isNotEmpty()) {
                val menuItem = MenuItem(
                    name = name,
                    description = description,
                    allergens = allergens,
                    price = price,
                    calories = calories,
                    country = country,
                    region = region
                )
                categories.getOrPut(category) { mutableListOf() }.add(menuItem)
            }
        }
    }

    // The parseJsonObject function has been removed.

    /**
     * Clean and normalize price strings
     */
    private fun cleanPrice(price: String): Double {
        return try {
            val cleaned = price
                .replace("€", "")
                .replace(",", ".")
                .trim()
            cleaned.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }
}