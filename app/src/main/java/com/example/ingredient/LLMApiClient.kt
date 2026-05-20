package com.example.ingredient

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LLMApiClient(private val apiKey: String) {

    private val apiUrl = "https://api.groq.com/openai/v1/chat/completions"
    private val TAG = "LLMApiClient"

    /**
     * Send text to LLM and get structured menu response.
     * If text has === CATEGORIA: ... === section markers, processes section-by-section
     * with per-section validation + retry for maximum recall.
     */
    suspend fun processMenuText(
        menuText: String,
        temperature: Double = 0.1,
        maxTokens: Int = 4096,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val cleanedText = menuText
                .replace("\n\n+".toRegex(), "\n")
                .replace("[ ]{2,}".toRegex(), " ")
                .trim()

            Log.d(TAG, "Processing full menu text (${cleanedText.length} chars)")

            // If DOM segmentation produced section markers, use section-by-section extraction
            if (cleanedText.contains("=== CATEGORIA:") || cleanedText.contains("=== SEZIONE:")) {
                Log.d(TAG, "Detected section markers — using section-by-section extraction")
                return@withContext processBySections(cleanedText, temperature, maxTokens, onProgress)
            }

            // Check if text is too large for a single call.
            // Groq llama-3.1-8b-instant: 6000 TPM limit. System prompt adds ~2500 tokens,
            // leaving ~3500 tokens for content (~3000 chars at ~1.2 chars/token).
            if (cleanedText.length > 3000) {
                Log.w(TAG, "Text too large for single call (${cleanedText.length} chars), splitting into chunks")
                return@withContext processInChunks(cleanedText, temperature, maxTokens, onProgress)
            }

            // Single API call with all text
            val prompt = """
                Questo è il contenuto del Menu di un Ristorante/Pizzeria: 
                $cleanedText

                DEVI restituire il risultato SOLO in formato JSON valido.
                NON aggiungere testo, spiegazioni, commenti o markdown (niente ```json).
                L'output deve essere SOLO un array contenente TUTTI gli elementi del menu. Non fermarti a 3 piatti per categoria.

                🔥 REGOLA SUL NOME ("Piatti"): usa SEMPRE il nome ESATTO scritto nel testo. NON usare "Piatto 1", "Piatto 2", "Item 3" o simili. Se il nome non è leggibile → salta quel piatto.

                🔥 REGOLE IMPORTANTISSIME PER LE CATEGORIE:
                • NON usare testo tagliato come categoria (es: "Risto", "Rist", "Res", "Piz", "Pi", "Rol", "Cap", ecc.).
                • NON inventare categorie nuove che non sono chiaramente sezioni del menu.
                • Se la categoria non è chiaramente riconoscibile → usa la categoria: "Menu"
                • Se la categoria è troppo breve (< 3 caratteri) → usa "Menu"
                • Se il testo della categoria è ambiguo, incompleto o proviene da un ritaglio → usa "Menu"

                🔥 Categorie valide sono SOLO se esplicitamente presenti nel menu, es.:
                "Antipasti", "Primi", "Secondi", "Dolci", "Pizze", "Pinsa", "Carne", "Contorni", ecc.
                Descrizione o Ingredienti ci devono sempre essere.
                Devi riorganizzare il contenuto in JSON usando questa struttura ESATTA per ogni piatto:

                [ // INIZIA CON ARRAY
                {
                  "Categoria": "string",
                  "Piatti": "string",
                  "Descrizione/Ingredienti/Extra": "string",
                  "Allergeni": "string",
                  "Prezzo": "string",
                  "Calorie": number,
                  "Paese": "string",
                  "Regione": "string"
                },
                // ... altri piatti
                ] // FINISCE CON ARRAY

                🔥 LINGUA: mantieni la lingua originale del menu. Se è in inglese, lascia i nomi e le descrizioni in inglese. NON tradurre.

                Se mancano informazioni → lascia una stringa vuota "".

                "Descrizione/Ingredienti/Extra": se gli ingredienti sono nel testo usali, altrimenti INFERISCILI dal nome del piatto (es. "Diavola" → "mozzarella, salame piccante, pomodoro"; "Bresaola" → "bresaola, rucola, grana, olio"). Non lasciare mai vuoto questo campo.
                "Prezzo" SEMPRE nel formato "0.00"
                Gli allergeni vengono dai numeri tra asterischi.
                "Calorie" è una stima delle kcal per porzione basata su ingredienti e tipo di piatto (numero intero, es. 450). Se non è stimabile → usa 0.
                "Paese" è il paese di origine del piatto inferito dal nome/ingredienti (es: "Italia", "Giappone", "Messico"). Se non determinabile → "".
                "Regione" è la regione/città di origine (es: "Campania", "Sicilia", "Osaka"). Se non determinabile → "".

                RITORNA ORA SOLO IL JSON. Nient'altro.
            """.trimIndent()

            val response = invokeAPI(prompt, temperature, maxTokens)

            if (response.contains("\"error\"")) {
                Log.e(TAG, "Error in API response: $response")
                // 413 = token limit exceeded → fallback to chunked processing
                if (response.contains("413") || response.contains("rate_limit_exceeded") || response.contains("too large")) {
                    Log.w(TAG, "Single-call token limit hit, retrying with chunk processing")
                    return@withContext processInChunks(cleanedText, temperature, maxTokens, onProgress)
                }
                return@withContext response
            }

            response

        } catch (e: Exception) {
            Log.e(TAG, "Error processing menu text", e)
            """{"error": "Failed to process menu: ${e.message}"}"""
        }
    }

    /**
     * Section-by-section extraction: each === CATEGORIA: X === section is sent independently.
     * Validates each section and retries with a stronger prompt if 0 dishes returned.
     */
    private suspend fun processBySections(
        menuText: String,
        temperature: Double,
        maxTokens: Int,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String {
        // Match both === CATEGORIA: X === (high-confidence) and === SEZIONE: X === (generic)
        val sectionRegex = Regex("=== (?:CATEGORIA|SEZIONE): (.+?) ===")
        val parts = sectionRegex.split(menuText)
        val headers = sectionRegex.findAll(menuText).map { it.groupValues[1].trim() }.toList()

        // pairs: (categoryName, sectionContent)
        val rawSections = headers.zip(parts.drop(1)).filter { (_, content) -> content.trim().isNotEmpty() }

        // ── Pre-filter: drop sections that are clearly NOT menu content ────────
        // Zero API cost. LLM also returns [] for these, but this avoids wasted calls.
        val nonMenuKeywords = listOf(
            "orari", "opening hour", "hours", "schedule", "horaires",
            "eventi", "events", "novità", "news", "notizie",
            "about", "chi siamo", "su di noi", "informazioni", "info",
            "contatti", "contacts", "dove siamo", "location", "mappa",
            "prenota", "reservation", "book", "prenotazione",
            "social", "seguici", "condividi", "share", "follow us",
            "gallery", "galleria", "foto", "photos",
            "copyright", "privacy", "cookie", "terms",
            "login", "register", "accedi", "registrati"
        )
        val sections = rawSections.filter { (name, _) ->
            val lower = name.lowercase()
            nonMenuKeywords.none { lower.contains(it) }
        }

        Log.d(TAG, "Sections total=${rawSections.size} after pre-filter=${sections.size} (dropped=${rawSections.size - sections.size})")

        if (sections.isEmpty()) {
            Log.w(TAG, "No sections found after split, falling back to chunk processing")
            return processInChunks(menuText, temperature, maxTokens, onProgress)
        }

        Log.d(TAG, "Processing ${sections.size} sections individually")

        // If only 1 section was detected, the markers didn't help divide the menu.
        // Fall back to chunk-based so we don't truncate a large menu to 2000 chars.
        if (sections.size == 1) {
            val (catName, content) = sections[0]
            if (content.trim().length > 1800) {
                Log.w(TAG, "Only 1 section detected and content is large — using chunk processing")
                return processInChunks(menuText, temperature, maxTokens, onProgress)
            }
        }
        val responses = mutableListOf<String>()

        for ((idx, pair) in sections.withIndex()) {
            val (categoryName, sectionContent) = pair
            withContext(Dispatchers.Main) { onProgress?.invoke(idx + 1, sections.size) }

            val sectionResult = extractSection(categoryName, sectionContent.trim(), temperature, maxTokens)

            // Validation: if section returned empty array but had meaningful content → retry once
            val isEmpty = sectionResult.trim().let { it == "[]" || it.isBlank() || it == "{}" }
            val hasContent = sectionContent.trim().length > 30
            if (isEmpty && hasContent) {
                Log.w(TAG, "Section '$categoryName' returned empty — retrying with aggressive prompt")
                val retryResult = extractSectionAggressive(categoryName, sectionContent.trim(), temperature, maxTokens)
                if (!retryResult.trim().let { it == "[]" || it.isBlank() }) {
                    responses.add(retryResult)
                    Log.d(TAG, "Retry for '$categoryName' succeeded")
                } else {
                    Log.w(TAG, "Section '$categoryName' still empty after retry — skipping")
                }
            } else if (!isEmpty) {
                responses.add(sectionResult)
            }
        }

        return if (responses.size == 1) {
            responses[0]
        } else {
            responses.mapIndexed { idx, text -> "=== PARTE ${idx + 1} ===\n$text" }.joinToString("\n\n")
        }
    }

    private fun extractSection(categoryName: String, content: String, temperature: Double, maxTokens: Int): String {
        // Truncate to safe size
        val safeContent = if (content.length > 2000) content.substring(0, 2000) else content
        val prompt = """
Stai analizzando la sezione "$categoryName" di un menu ristorante.

Testo della sezione:
$safeContent

ESTRAI TUTTI i piatti presenti. NON omettere nessun piatto. NON fermarti prima di aver letto tutto.
Restituisci SOLO un JSON array. Niente markdown, niente spiegazioni.

Struttura per ogni piatto:
[{"Categoria":"$categoryName","Piatti":"nome esatto del piatto","Descrizione/Ingredienti/Extra":"ingredienti reali o inferiti dal nome","Allergeni":"","Prezzo":"0.00","Calorie":0,"Paese":"","Regione":""}]

REGOLE:
- "Piatti": usa il nome ESATTO dal testo. MAI "Piatto 1" o simili.
- LINGUA: mantieni la lingua originale. Se il menu è in inglese, lascia tutto in inglese. NON tradurre.
- "Descrizione/Ingredienti/Extra": OBBLIGATORIO. Usa ingredienti dal testo, o inferiscili dal nome piatto.
- "Prezzo": formato "0.00". Se non presente → "0.00"
- "Calorie": stima kcal (intero). Se non stimabile → 0
- Se non ci sono piatti → restituisci []

RITORNA SOLO IL JSON ARRAY.
        """.trimIndent()
        return invokeAPI(prompt, temperature, maxTokens)
    }

    private fun extractSectionAggressive(categoryName: String, content: String, temperature: Double, maxTokens: Int): String {
        val safeContent = if (content.length > 1500) content.substring(0, 1500) else content
        val prompt = """
Sezione menu: "$categoryName"

$safeContent

Elenca OGNI elemento alimentare che trovi in questo testo come piatto del menu.
Sii aggressivo: qualsiasi cosa sembri un cibo o una bevanda va inclusa.
Mantieni la lingua originale del testo (se inglese → lascia in inglese, NON tradurre).
Restituisci SOLO JSON array:
[{"Categoria":"$categoryName","Piatti":"nome","Descrizione/Ingredienti/Extra":"ingredienti inferiti","Allergeni":"","Prezzo":"0.00","Calorie":0,"Paese":"","Regione":""}]
Se davvero non c'è nulla di alimentare → []
SOLO JSON.
        """.trimIndent()
        return invokeAPI(prompt, temperature, maxTokens)
    }

    /**
     * Fallback: process in chunks only if text is extremely large
     */
    private suspend fun processInChunks(
        menuText: String,
        temperature: Double,
        maxTokens: Int,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String {
        val chunks = splitBySection(menuText, maxChunkSize = 2500)
        Log.d(TAG, "Split into ${chunks.size} section-aware chunks")

        val responses = mutableListOf<String>()

        for ((index, chunk) in chunks.withIndex()) {
            Log.d(TAG, "Processing chunk ${index + 1}/${chunks.size}")
            withContext(Dispatchers.Main) { onProgress?.invoke(index + 1, chunks.size) }

            val prompt = """
                Questo è la parte ${index + 1} di ${chunks.size} del Menu di un Ristorante/Pizzeria:
                $chunk

                DEVI restituire il risultato SOLO in formato JSON valido.
                NON aggiungere testo, spiegazioni, commenti o markdown (niente ```json).
                L'output deve essere SOLO un array contenente gli elementi del menu trovati in questa parte.

                🔥 REGOLA NOME: usa il nome ESATTO scritto nel testo. NON scrivere mai "Piatto 1", "Piatto 2", "Item 3" o simili. Se non riesci a leggere il nome di un piatto → salta quell'elemento.

                🔥 REGOLE IMPORTANTISSIME PER LE CATEGORIE:
                • NON usare testo tagliato come categoria (es: "Risto", "Rist", "Res", "Piz", "Pi", "Rol", "Cap", ecc.).
                • NON inventare categorie nuove che non sono chiaramente sezioni del menu.
                • Se la categoria non è chiaramente riconoscibile → usa la categoria: "Menu"
                • Se la categoria è troppo breve (< 3 caratteri) → usa "Menu"

                Devi riorganizzare il contenuto in JSON usando questa struttura ESATTA:

                [
                {
                  "Categoria": "string",
                  "Piatti": "string",
                  "Descrizione/Ingredienti/Extra": "string",
                  "Allergeni": "string",
                  "Prezzo": "string",
                  "Calorie": number,
                  "Paese": "string",
                  "Regione": "string"
                }
                ]

                🔥 LINGUA: mantieni la lingua originale del menu. Se è in inglese, lascia i nomi e le descrizioni in inglese. NON tradurre.

                Se mancano informazioni → lascia una stringa vuota "".
                "Descrizione/Ingredienti/Extra": se gli ingredienti sono nel testo usali, altrimenti INFERISCILI dal nome del piatto. Non lasciare mai vuoto questo campo.
                "Prezzo" SEMPRE nel formato "0.00"
                "Calorie" è una stima kcal per porzione (numero intero, es. 450). Se non stimabile → 0.
                Se in questa parte non ci sono piatti del menu → restituisci un array vuoto: []

                RITORNA ORA SOLO IL JSON. Nient'altro.
            """.trimIndent()

            val response = invokeAPI(prompt, temperature, maxTokens)

            if (response.contains("\"error\"")) {
                Log.e(TAG, "Error in chunk ${index + 1}: $response")
                // 413 on a chunk means the chunk is still too large — skip it rather than failing the whole batch
                if (response.contains("413") || response.contains("rate_limit_exceeded") || response.contains("too large")) {
                    Log.w(TAG, "Chunk ${index + 1} too large even after splitting — skipping")
                    continue
                }
                responses.add(response)
                break
            }

            responses.add(response)
        }

        return if (responses.size == 1) {
            responses[0]
        } else {
            responses.mapIndexed { index, text ->
                "=== PARTE ${index + 1} ===\n$text"
            }.joinToString("\n\n")
        }
    }

    /**
     * Make API call to Siemens LLM with increased timeout
     */
    private fun invokeAPI(
        message: String,
        temperature: Double = 0.1,
        maxTokens: Int = 4096,
        retryCount: Int = 0
    ): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"

            // Set headers
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")

            connection.doOutput = true
            // INCREASED TIMEOUTS - Key fix!
            connection.connectTimeout = 90000  // 90 seconds (was 30)
            connection.readTimeout = 90000     // 90 seconds (was 30)

            // Build request payload
            val payload = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("temperature", temperature)
                put("max_tokens", maxTokens)
            }

            Log.d(TAG, "Sending request to: $apiUrl")
            Log.d(TAG, "Message length: ${message.length} chars")
            Log.d(TAG, "Max tokens: $maxTokens")

            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            // Read response
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }

                Log.d(TAG, "Response received: ${response.take(200)}...")

                // Parse response
                val jsonResponse = JSONObject(response)
                val content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                Log.d(TAG, "Extracted content length: ${content.length} chars")
                return content

            } else {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "No error details available"
                }
                Log.e(TAG, "API Error ($responseCode): $errorResponse")

                // Auto-retry on rate limit (429) — parse retry-after from error message
                if (responseCode == 429 && retryCount < 3) {
                    val waitMs = runCatching {
                        val match = Regex("try again in ([0-9.]+)s").find(errorResponse)
                        ((match?.groupValues?.get(1)?.toDouble() ?: 35.0) * 1000).toLong() + 1000L
                    }.getOrDefault(35_000L)
                    Log.w(TAG, "Rate limited. Waiting ${waitMs}ms before retry ${retryCount + 1}/3...")
                    Thread.sleep(waitMs)
                    return invokeAPI(message, temperature, maxTokens, retryCount + 1)
                }

                return """{"error": "API returned error code $responseCode: $errorResponse"}"""
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout during API call", e)
            return """{"error": "Request timed out. The menu might be too large. Try processing a smaller section."}"""
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call", e)
            e.printStackTrace()
            return """{"error": "API call failed: ${e.message}"}"""
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Section-aware splitting: breaks at menu category headers (ALL CAPS lines, short lines before content).
     * Falls back to char-based splitting if no sections detected.
     */
    private fun splitBySection(text: String, maxChunkSize: Int = 2500): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val lines = text.split("\n")
        val chunks = mutableListOf<String>()
        var current = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            val isSectionHeader = trimmed.isNotEmpty() &&
                trimmed.length <= 40 &&
                (trimmed == trimmed.uppercase() ||
                 trimmed.endsWith(":") ||
                 Regex("^[A-ZÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖÙÚÛÜÝ][a-zàáâãäåæçèéêëìíîïðñòóôõöùúûüý ]+$").matches(trimmed))

            // Start new chunk at section boundary when current is large enough
            if (isSectionHeader && current.length > maxChunkSize / 2) {
                val built = current.toString().trim()
                if (built.isNotEmpty()) chunks.add(built)
                current = StringBuilder()
            }

            current.append(line).append("\n")

            // Hard split if chunk exceeds max regardless of section
            if (current.length >= maxChunkSize) {
                val built = current.toString().trim()
                if (built.isNotEmpty()) chunks.add(built)
                current = StringBuilder()
            }
        }

        val last = current.toString().trim()
        if (last.isNotEmpty()) chunks.add(last)

        return if (chunks.size <= 1) splitTextNoOverlap(text, maxChunkSize) else chunks
    }

    private fun splitTextNoOverlap(text: String, maxChunkSize: Int = 2500): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        val lines = text.split("\n")
        var currentChunk = StringBuilder()

        for (line in lines) {
            // Hard-split lines that are themselves too long
            if (line.length > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }
                var pos = 0
                while (pos < line.length) {
                    chunks.add(line.substring(pos, minOf(pos + maxChunkSize, line.length)))
                    pos += maxChunkSize
                }
                continue
            }

            if (currentChunk.length + line.length + 1 > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }
            }
            currentChunk.append(line).append("\n")
        }

        if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString())
        return chunks
    }

    /**
     * Enrichment pass: for every dish that is missing ingredients, calories, country, or region,
     * sends batches to LLM to infer them from the dish name + category.
     * This ensures these 4 fields are ALWAYS filled regardless of source menu quality.
     */
    suspend fun enrichDishes(
        categories: List<MenuCategory>,
        onProgress: ((String) -> Unit)? = null
    ): List<MenuCategory> = withContext(Dispatchers.IO) {
        // Collect dishes that need enrichment (missing key fields)
        data class DishRef(val catIdx: Int, val dishIdx: Int, val name: String, val category: String, val existing: MenuItem)
        val needsEnrichment = mutableListOf<DishRef>()

        categories.forEachIndexed { catIdx, cat ->
            cat.dishes.forEachIndexed { dishIdx, dish ->
                val missingIngredients = dish.description.isBlank()
                val missingCalories = dish.calories == 0
                val missingCountry = dish.country.isBlank()
                if (missingIngredients || missingCalories || missingCountry) {
                    needsEnrichment.add(DishRef(catIdx, dishIdx, dish.name, cat.categoryName, dish))
                }
            }
        }

        if (needsEnrichment.isEmpty()) {
            Log.d(TAG, "Enrichment: all dishes already complete")
            return@withContext categories
        }

        Log.d(TAG, "Enrichment: ${needsEnrichment.size} dishes need filling")

        // Small delay to avoid 429 immediately after main extraction (TPM window resets)
        Thread.sleep(3000L)

        // Work on mutable copies
        val result: List<MutableList<MenuItem>> = categories.map { it.dishes.toMutableList() }

        // Process in batches of 15 (keeps prompt small, under TPM limit)
        val batchSize = 15
        needsEnrichment.chunked(batchSize).forEachIndexed { batchIdx, batch ->
            onProgress?.invoke("Arricchimento dati: batch ${batchIdx + 1}/${(needsEnrichment.size + batchSize - 1) / batchSize}…")

            // Build compact JSON input for LLM
            val inputJson = batch.mapIndexed { i, ref ->
                """{"id":$i,"nome":"${ref.name.replace("\"","")}","categoria":"${ref.category.replace("\"","")}"}"""
            }.joinToString(",\n", "[", "]")

            val prompt = """
You are a culinary knowledge engine. For each dish in the JSON array below, fill in the missing details.

Input:
$inputJson

For EACH dish, return:
- "id": same as input
- "ingredienti": list of main ingredients (infer from dish name if not known). Always non-empty.
- "calorie": estimated kcal per serving (integer). Use culinary knowledge. Never 0.
- "paese": country of origin of the dish (e.g. "Italia", "Giappone", "Messico", "USA"). Never empty.
- "regione": region or city of origin (e.g. "Campania", "Sicilia", "Toscana"). Empty string "" if truly unknown.

Rules:
- Respond ONLY with a JSON array. No markdown, no explanations.
- Keep dish names and ingredients in their ORIGINAL LANGUAGE (do not translate).
- Every dish MUST have non-empty "ingredienti" and "paese".
- Use culinary common knowledge to infer. Even pizza margherita → paese: "Italia", regione: "Campania".

Output format:
[{"id":0,"ingredienti":"...","calorie":500,"paese":"Italia","regione":"Campania"},...]

RETURN ONLY THE JSON ARRAY.
            """.trimIndent()

            val response = invokeAPI(prompt, temperature = 0.1, maxTokens = 2048)

            if (response.contains("\"error\"")) {
                Log.e(TAG, "Enrichment batch $batchIdx failed: $response")
                return@forEachIndexed
            }

            // Parse enrichment response and merge back
            try {
                val startIdx = response.indexOf('[')
                val endIdx = response.lastIndexOf(']')
                if (startIdx == -1 || endIdx == -1) return@forEachIndexed

                val arr = org.json.JSONArray(response.substring(startIdx, endIdx + 1))
                for (j in 0 until arr.length()) {
                    val obj = arr.getJSONObject(j)
                    val id = obj.optInt("id", -1)
                    if (id < 0 || id >= batch.size) continue

                    val ref = batch[id]
                    val original = ref.existing
                    result[ref.catIdx][ref.dishIdx] = original.copy(
                        description = if (original.description.isBlank()) obj.optString("ingredienti", original.description) else original.description,
                        calories = if (original.calories == 0) obj.optInt("calorie", 0) else original.calories,
                        country = if (original.country.isBlank()) obj.optString("paese", original.country) else original.country,
                        region = if (original.region.isBlank()) obj.optString("regione", original.region) else original.region
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse enrichment response", e)
            }
        }

        // Rebuild final list
        categories.mapIndexed { catIdx, cat ->
            cat.copy(dishes = result[catIdx].toList())
        }
    }
}