package com.example.ingredient

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IngredientApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("IngredientApplication", "========== APPLICATION STARTING ==========")

        // Initialize model in background thread to avoid ANR
        applicationScope.launch {
            LocalAiParser.init(this@IngredientApplication)
        }

        Log.d("IngredientApplication", "========== APPLICATION STARTED ==========")
    }
}