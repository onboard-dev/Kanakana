package me.zssu.ime.ime

import android.content.Context
import me.zssu.ime.mozc.SecureStore
import org.json.JSONArray
import java.io.File

class SnippetStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "snippets.enc")
    private val nameFile = File(context.applicationContext.filesDir, "snippet_name.enc")
    private val prefs = context.applicationContext.getSharedPreferences(me.zssu.ime.settings.ImeSettings.NAME, Context.MODE_PRIVATE)
    fun dateFormat(): String = prefs.getString("snippet_date_format", null)
        ?.takeIf(SnippetTemplate::validDateFormat) ?: "yyyy-MM-dd"
    fun saveDateFormat(pattern: String) { prefs.edit().putString("snippet_date_format", pattern).apply() }
    fun name(): String = runCatching { SecureStore.read(nameFile).orEmpty() }.getOrDefault("")
    fun saveName(value: String): Boolean = SecureStore.writeBytes(nameFile, value.trim().take(100).toByteArray())
    fun entries(): List<String> = runCatching {
        val array = JSONArray(SecureStore.read(file) ?: "[]")
        (0 until array.length()).map { array.getString(it) }.take(100)
    }.getOrDefault(emptyList())
    private val recentFile = File(context.applicationContext.filesDir, "snippet_recent.enc")

    /** Most recently inserted snippets, newest first, limited to ones that still exist. */
    fun recent(limit: Int = 5): List<String> = runCatching {
        val array = JSONArray(SecureStore.read(recentFile) ?: "[]")
        val current = entries().toHashSet()
        (0 until array.length()).map { array.getString(it) }.filter { it in current }.take(limit)
    }.getOrDefault(emptyList())

    fun markUsed(value: String) {
        val updated = (listOf(value) + runCatching {
            val array = JSONArray(SecureStore.read(recentFile) ?: "[]")
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())).distinct().take(20)
        SecureStore.writeBytes(recentFile, JSONArray(updated).toString().toByteArray())
    }

    fun save(values: List<String>): Boolean = SecureStore.writeBytes(
        file, JSONArray(values.map { it.trim().take(2000) }.filter { it.isNotEmpty() }.distinct().take(100)).toString().toByteArray(),
    )
}
