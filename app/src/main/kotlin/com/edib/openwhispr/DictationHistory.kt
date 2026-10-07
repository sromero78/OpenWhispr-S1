package com.edib.openwhispr

import android.content.SharedPreferences
import org.json.JSONArray

/** Local-only rolling history of final dictation text. No audio is stored. */
object DictationHistory {
    private const val KEY = "dictation_history_v1"
    const val MAX_ITEMS = 20

    fun load(prefs: SharedPreferences): List<String> {
        return try {
            val raw = prefs.getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val text = arr.optString(i).trim()
                    if (text.isNotEmpty()) add(text)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(prefs: SharedPreferences, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val items = load(prefs).toMutableList()
        items.remove(clean)
        items.add(0, clean)
        val arr = JSONArray()
        items.take(MAX_ITEMS).forEach(arr::put)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY).apply()
    }
}
