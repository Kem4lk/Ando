package com.ando.launcher.notifications

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class PlayedTrack(
    val title: String,
    val artist: String,
    val whenMillis: Long,
)

/**
 * A real "recently played" history, built by watching MediaSession metadata changes for a
 * handful of media apps (see [com.ando.launcher.data.AppCatalog.trackedMediaPackages]) — the
 * closest thing to Spotify's play history a third-party app can legitimately observe, since
 * Spotify exposes no public API for it.
 */
object MediaHistoryStore {
    private const val PREFS = "ando_media_history"
    private const val MAX_PER_PACKAGE = 20

    private val _byPackage = MutableStateFlow<Map<String, List<PlayedTrack>>>(emptyMap())
    val byPackage: StateFlow<Map<String, List<PlayedTrack>>> = _byPackage

    fun loadFromDisk(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = mutableMapOf<String, List<PlayedTrack>>()
        for ((key, value) in prefs.all) {
            if (value !is String) continue
            result[key] = decode(value)
        }
        _byPackage.value = result
    }

    fun record(context: Context, packageName: String, title: String, artist: String, whenMillis: Long) {
        if (title.isBlank()) return
        val existing = _byPackage.value[packageName].orEmpty()
        // MediaSession fires onMetadataChanged repeatedly for the same track (seek, buffering, ...);
        // only log it once per play.
        if (existing.firstOrNull()?.title == title && existing.firstOrNull()?.artist == artist) return
        val updated = (listOf(PlayedTrack(title, artist, whenMillis)) + existing).take(MAX_PER_PACKAGE)
        _byPackage.update { it + (packageName to updated) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(packageName, encode(updated))
        }
    }

    private fun encode(items: List<PlayedTrack>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("artist", item.artist)
                    put("when", item.whenMillis)
                },
            )
        }
        return array.toString()
    }

    private fun decode(json: String): List<PlayedTrack> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            PlayedTrack(
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                whenMillis = obj.optLong("when"),
            )
        }
    }.getOrDefault(emptyList())
}
