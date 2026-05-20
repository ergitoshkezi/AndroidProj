package com.example.ingredient

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

object LocalAiParser {

    private const val TAG = "LocalAiParser"
    private var llmInference: LlmInference? = null
    private var isInitializing = false
    private var initializationFailed = false

    @Synchronized
    fun init(context: Context) {
        if (llmInference != null) {
            Log.d(TAG, "Model already initialized")
            return
        }

        if (isInitializing) {
            Log.d(TAG, "Model initialization already in progress")
            return
        }

        isInitializing = true

        try {
            Log.d(TAG, "Starting model initialization...")

            // Copy model from assets to cache directory
            val modelFile = File(context.cacheDir, "menu_parser.bin")
            Log.d(TAG, "Model path: ${modelFile.absolutePath}")

            if (!modelFile.exists()) {
                Log.d(TAG, "Copying model from assets...")
                val startTime = System.currentTimeMillis()

                context.assets.open("models/menu_parser.gguf").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Model copied in ${duration}ms. Size: ${modelFile.length() / (1024 * 1024)}MB")
            } else {
                Log.d(TAG, "Model exists. Size: ${modelFile.length() / (1024 * 1024)}MB")
            }

            Log.d(TAG, "Loading model...")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setTemperature(0.2f)
                .setTopK(40)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "Model initialized successfully!")
            initializationFailed = false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model", e)
            e.printStackTrace()
            initializationFailed = true
        } finally {
            isInitializing = false
        }
    }

    fun isReady(): Boolean = llmInference != null

    fun isInitializing(): Boolean = isInitializing

    fun parseMenu(menuText: String): String {
        if (isInitializing) {
            return "{\"error\": \"Model is still initializing. Please wait...\"}"
        }

        if (initializationFailed) {
            return "{\"error\": \"Model initialization failed. Please check your model file.\"}"
        }

        val currentModel = llmInference
        if (currentModel == null) {
            Log.e(TAG, "Model is null!")
            return "{\"error\": \"Model is not initialized. Please restart the app.\"}"
        }

        val prompt = """
            Extract dishes and their ingredients from the following restaurant menu text.
            The output must be ONLY a valid JSON array of objects.
            Each object should have a "dish" key and an "ingredients" key, which is an array of strings.
            Example:
            [
              { "dish": "Spaghetti Carbonara", "ingredients": ["pasta", "eggs", "pecorino cheese", "guanciale", "black pepper"] }
            ]

            Do not include any comments, explanations, or markdown.

            MENU TEXT:
            $menuText
        """.trimIndent()

        return try {
            Log.d(TAG, "Generating response...")
            val result = currentModel.generateResponse(prompt)
            Log.d(TAG, "Response generated successfully")
            result.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            e.printStackTrace()
            "{\"error\": \"Failed to parse menu: ${e.message}\"}"
        }
    }

    fun release() {
        llmInference?.close()
        llmInference = null
        Log.d(TAG, "Model released")
    }
}