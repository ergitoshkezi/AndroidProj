package com.example.ingredient.model

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ingredient_session", Context.MODE_PRIVATE)

    fun saveSession(userId: String, userType: String) {
        prefs.edit()
            .putString("session_user_id", userId)
            .putString("session_user_type", userType)
            .apply()
    }

    fun getUserId(): String = prefs.getString("session_user_id", "") ?: ""

    fun getUserType(): String = prefs.getString("session_user_type", "") ?: ""

    fun isLoggedIn(): Boolean = getUserId().isNotEmpty()

    fun logout() {
        prefs.edit()
            .remove("session_user_id")
            .remove("session_user_type")
            .apply()
    }

    fun isDisclaimerAccepted(): Boolean = prefs.getBoolean("disclaimer_accepted", false)

    fun setDisclaimerAccepted() {
        prefs.edit().putBoolean("disclaimer_accepted", true).apply()
    }
}
