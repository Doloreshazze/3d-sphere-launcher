package com.playeverywhere.spherelauncher.audio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VoicePrintStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_prints_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "VoicePrintStore"
        private const val KEY_PRINTS = "enrolled_voice_prints"
    }

    fun savePrints(prints: List<VoiceMatcher.VoicePrint>) {
        try {
            val json = Json.encodeToString(prints)
            prefs.edit().putString(KEY_PRINTS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist voice prints", e)
        }
    }

    fun loadPrints(): List<VoiceMatcher.VoicePrint> {
        val json = prefs.getString(KEY_PRINTS, null)
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            Log.e(TAG, "Stored voice prints are invalid; clearing corrupted data", e)
            prefs.edit().remove(KEY_PRINTS).apply()
            emptyList()
        }
    }
}
