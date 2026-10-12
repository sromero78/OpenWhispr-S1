package com.edib.openwhispr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, non-secret personal spelling dictionary. */
object VocabularyStore {
    private const val KEY = "vocabulary_v1"
    const val MAX_ENTRIES = 500
    const val MAX_LENGTH = 100

    data class Entry(val spelling: String, val heardAs: String = "")

    fun validate(entries: List<Entry>): List<Entry> {
        require(entries.size <= MAX_ENTRIES) { "Too many vocabulary entries" }
        val seen = HashSet<String>()
        return entries.map { entry ->
            val word = entry.spelling.trim()
            val hint = entry.heardAs.trim()
            require(word.isNotEmpty() && word.length <= MAX_LENGTH && hint.length <= MAX_LENGTH) { "Invalid entry" }
            require(seen.add(word.lowercase())) { "Duplicate entry: $word" }
            Entry(word, hint)
        }
    }

    fun encode(entries: List<Entry>): String {
        val array = JSONArray()
        validate(entries).forEach { array.put(JSONObject().put("spelling", it.spelling).put("heardAs", it.heardAs)) }
        return JSONObject().put("format", "openwispr-vocabulary").put("version", 1).put("entries", array).toString(2)
    }

    fun decode(json: String): List<Entry> {
        require(json.length <= 150000) { "Backup too large" }
        val root = JSONObject(json)
        require(root.getString("format") == "openwispr-vocabulary" && root.getInt("version") == 1) { "Unsupported backup" }
        val array = root.getJSONArray("entries")
        require(array.length() <= MAX_ENTRIES) { "Too many entries" }
        return validate((0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Entry(item.getString("spelling"), item.optString("heardAs", ""))
        })
    }

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try { decode(raw) } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, entries: List<Entry>) {
        val encoded = encode(entries)
        check(context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE).edit().putString(KEY, encoded).commit()) { "Unable to save vocabulary" }
    }

    fun prompt(context: Context): String = load(context).joinToString("\n") {
        if (it.heardAs.isBlank()) "- ${it.spelling}" else "- ${it.spelling} (may be heard as ${it.heardAs})"
    }
}
