package com.ando.launcher.notifications

import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class CapturedNotification(
    val title: String,
    val text: String,
    val whenMillis: Long,
    /** Tapping through to the exact chat/screen the notification pointed at, and the sender's
     *  avatar if the notification carried one — only ever set for notifications captured live
     *  in this process, never restored from disk. */
    val contentIntent: PendingIntent? = null,
    val avatar: ImageBitmap? = null,
)

/**
 * On-disk log of the last notifications per tracked package, written by
 * [AndoNotificationListenerService] and read by the launcher UI. Nothing here ever
 * leaves the device. Kept generous enough (well beyond what a card displays) that
 * "most talked to" frequency stats (see WhatsApp's contact strip) mean something.
 */
object NotificationStore {
    private const val PREFS = "ando_notifications"
    private const val MAX_PER_PACKAGE = 40

    /** In-memory mirror so the UI can react immediately without re-reading disk. */
    private val _byPackage = MutableStateFlow<Map<String, List<CapturedNotification>>>(emptyMap())
    val byPackage: StateFlow<Map<String, List<CapturedNotification>>> = _byPackage

    fun loadFromDisk(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = mutableMapOf<String, List<CapturedNotification>>()
        for ((key, value) in prefs.all) {
            if (value !is String) continue
            result[key] = decode(value)
        }
        _byPackage.value = result
    }

    fun record(
        context: Context,
        packageName: String,
        title: String,
        text: String,
        whenMillis: Long,
        contentIntent: PendingIntent? = null,
        avatar: ImageBitmap? = null,
    ) {
        if (title.isBlank() && text.isBlank()) return
        val updated = (
            _byPackage.value[packageName].orEmpty() +
                CapturedNotification(title, text, whenMillis, contentIntent, avatar)
            )
            .sortedByDescending { it.whenMillis }
            .take(MAX_PER_PACKAGE)
        _byPackage.update { it + (packageName to updated) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(packageName, encode(updated))
        }
    }

    private fun encode(items: List<CapturedNotification>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("text", item.text)
                    put("when", item.whenMillis)
                },
            )
        }
        return array.toString()
    }

    private fun decode(json: String): List<CapturedNotification> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CapturedNotification(
                title = obj.optString("title"),
                text = obj.optString("text"),
                whenMillis = obj.optLong("when"),
            )
        }
    }.getOrDefault(emptyList())
}
