package com.ando.launcher.data

import androidx.compose.ui.graphics.Color
import com.ando.launcher.R
import com.ando.launcher.model.SourceKind

/** Static description of one card; real content is resolved at runtime by the repository. */
data class CatalogEntry(
    val id: String,
    val fallbackName: String,
    val fallbackIcon: Int,
    val accent: Color,
    val source: SourceKind,
    /** Real Android package name(s) to check, in preference order — first installed one wins. */
    val packageCandidates: List<String> = emptyList(),
)

object AppCatalog {

    /** Apps we only ever see through their own notifications — there is no other public API for "recent chats". */
    val notificationApps: List<CatalogEntry> = listOf(
        CatalogEntry("telegram", "Telegram", R.drawable.icon_telegram, Color(0xFF29A9EA), SourceKind.NOTIFICATIONS, listOf("org.telegram.messenger")),
        CatalogEntry("whatsapp", "WhatsApp", R.drawable.icon_whatsapp, Color(0xFF25D366), SourceKind.NOTIFICATIONS, listOf("com.whatsapp")),
        CatalogEntry("chrome", "Chrome", R.drawable.icon_chrome, Color(0xFF4285F4), SourceKind.NOTIFICATIONS, listOf("com.android.chrome")),
        CatalogEntry("spotify", "Spotify", R.drawable.icon_spotify, Color(0xFF1ED760), SourceKind.NOTIFICATIONS, listOf("com.spotify.music")),
        CatalogEntry("mail", "Gmail", R.drawable.icon_mail, Color(0xFF1E88E5), SourceKind.NOTIFICATIONS, listOf("com.google.android.gm")),
        CatalogEntry("maps", "Maps", R.drawable.icon_maps, Color(0xFF34A853), SourceKind.NOTIFICATIONS, listOf("com.google.android.apps.maps")),
        CatalogEntry("chatgpt", "ChatGPT", R.drawable.icon_chatgpt, Color(0xFF10A37F), SourceKind.NOTIFICATIONS, listOf("com.openai.chatgpt")),
        CatalogEntry("reddit", "Reddit", R.drawable.icon_reddit, Color(0xFFFF4500), SourceKind.NOTIFICATIONS, listOf("com.reddit.frontpage")),
        CatalogEntry("x", "X", R.drawable.icon_x, Color(0xFF0F1419), SourceKind.NOTIFICATIONS, listOf("com.twitter.android", "com.x.android")),
    )

    /** Every package name above, flattened — what the notification listener should pay attention to. */
    val trackedNotificationPackages: Set<String> =
        notificationApps.flatMap { it.packageCandidates }.toSet()

    /** Packages whose MediaSession playback we track for a real "recently played" history. */
    val trackedMediaPackages: Set<String> = setOf("com.spotify.music")

    private val byId = notificationApps.associateBy { it.id }

    val photos = CatalogEntry("photos", "Google Photos", R.drawable.icon_photos, Color(0xFF4285F4), SourceKind.MEDIA_PHOTOS)
    val camera = CatalogEntry("camera", "Camera", R.drawable.icon_camera, Color(0xFF202124), SourceKind.MEDIA_CAMERA, listOf("com.google.android.GoogleCamera"))
    val calendar = CatalogEntry("calendar", "Calendar", R.drawable.icon_calendar, Color(0xFFEA4335), SourceKind.CALENDAR, listOf("com.google.android.calendar"))
    val phone = CatalogEntry("phone", "Phone", R.drawable.icon_phone, Color(0xFF34A853), SourceKind.CALL_LOG)
    val messages = CatalogEntry("messages", "Messages", R.drawable.icon_messages, Color(0xFF1A73E8), SourceKind.SMS, listOf("com.google.android.apps.messaging"))
    val files = CatalogEntry("files", "Dosyalar", R.drawable.icon_orange, Color(0xFFFF7A1A), SourceKind.FILES)
    val deviceStatus = CatalogEntry("device", "Device status", R.drawable.icon_settings, Color(0xFF5F6368), SourceKind.DEVICE_STATUS)

    /** Full card order, top to bottom. */
    val all: List<CatalogEntry> = listOf(
        byId.getValue("telegram"), byId.getValue("whatsapp"),
        photos, camera,
        byId.getValue("chrome"), byId.getValue("spotify"), byId.getValue("mail"),
        byId.getValue("maps"), byId.getValue("chatgpt"),
        files,
        deviceStatus,
        byId.getValue("reddit"), messages, phone, byId.getValue("x"),
        calendar,
    )
}
