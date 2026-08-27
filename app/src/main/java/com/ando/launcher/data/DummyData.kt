package com.ando.launcher.data

import androidx.compose.ui.graphics.Color
import com.ando.launcher.R
import com.ando.launcher.model.AppEntry
import com.ando.launcher.model.RecentItem

/**
 * Purely local, fabricated "recent activity" per app — there is no
 * network access or account linkage, this only exists to make the
 * launcher feel alive.
 */
object DummyData {

    val apps: List<AppEntry> = listOf(
        AppEntry(
            id = "telegram",
            name = "Telegram",
            icon = R.drawable.icon_telegram,
            accent = Color(0xFF29A9EA),
            recentLabel = "Recent chats",
            recent = listOf(
                RecentItem("Mira Aydın", "sent a voice message · 0:42", "2m", "3"),
                RecentItem("Design Crew", "Efe: pushed the new icon set 🎨", "18m", "12"),
                RecentItem("Kerem", "see you at 7?", "1h"),
            ),
        ),
        AppEntry(
            id = "whatsapp",
            name = "WhatsApp",
            icon = R.drawable.icon_whatsapp,
            accent = Color(0xFF25D366),
            recentLabel = "Recent chats",
            recent = listOf(
                RecentItem("Mom ❤️", "Akşam yemeğe gelir misin?", "6m", "1"),
                RecentItem("Team Ando", "Selin: build is green ✅", "40m"),
                RecentItem("Barış", "📍 Location shared", "3h"),
            ),
        ),
        AppEntry(
            id = "photos",
            name = "Google Photos",
            icon = R.drawable.icon_photos,
            accent = Color(0xFF4285F4),
            recentLabel = "Recently added",
            recent = listOf(
                RecentItem("42 new photos", "Kadıköy waterfront walk", "Today", thumbTint = Color(0xFFEA4335)),
                RecentItem("Screen recording", "0:38 · saved from Chrome", "Yesterday", thumbTint = Color(0xFF4285F4)),
                RecentItem("Shared album", "\"Yaz 2026\" · 6 people", "2d ago", thumbTint = Color(0xFFFBBC05)),
                RecentItem("Beach sunset", "Bodrum", "3d ago", thumbTint = Color(0xFFFF8A65)),
                RecentItem("Coffee & code", "Home office", "5d ago", thumbTint = Color(0xFF9575CD)),
            ),
            isGallery = true,
        ),
        AppEntry(
            id = "chrome",
            name = "Chrome",
            icon = R.drawable.icon_chrome,
            accent = Color(0xFF4285F4),
            recentLabel = "Open tabs & history",
            recent = listOf(
                RecentItem("android developers – Jetpack Compose", "developer.android.com", "3 tabs open"),
                RecentItem("Ando launcher inspiration", "dribbble.com/search", "12m ago"),
                RecentItem("kotlinlang.org – Coroutines guide", "kotlinlang.org", "1h ago"),
            ),
        ),
        AppEntry(
            id = "spotify",
            name = "Spotify",
            icon = R.drawable.icon_spotify,
            accent = Color(0xFF1ED760),
            recentLabel = "Recently played",
            recent = listOf(
                RecentItem("Gecenin Sonu", "Model 79", "▶ playing"),
                RecentItem("Chill Focus Mix", "Playlist · 48 songs", "queued next"),
                RecentItem("Blinding Lights", "The Weeknd", "played 1h ago"),
            ),
        ),
        AppEntry(
            id = "mail",
            name = "Mail",
            icon = R.drawable.icon_mail,
            accent = Color(0xFF1E88E5),
            recentLabel = "Inbox",
            recent = listOf(
                RecentItem("GitHub", "[Ando] New workflow run finished", "9m", "1"),
                RecentItem("Figma", "Selin commented on \"Home v3\"", "35m"),
                RecentItem("Spotify", "Your 2026 Rewind is ready", "4h"),
            ),
        ),
        AppEntry(
            id = "maps",
            name = "Maps",
            icon = R.drawable.icon_maps,
            accent = Color(0xFF34A853),
            recentLabel = "Recent destinations",
            recent = listOf(
                RecentItem("Moda Sahili", "12 min drive · 4.3 km", "yesterday"),
                RecentItem("Home", "Saved place", "used often"),
                RecentItem("Airport – SAW", "38 min · traffic light", "last week"),
            ),
        ),
        AppEntry(
            id = "chatgpt",
            name = "ChatGPT",
            icon = R.drawable.icon_chatgpt,
            accent = Color(0xFF10A37F),
            recentLabel = "Recent conversations",
            recent = listOf(
                RecentItem("Refactor plan for Ando", "let's split the repo module...", "5m"),
                RecentItem("Trip to Izmir", "3-day itinerary draft", "yesterday"),
                RecentItem("Regex helper", "match Turkish phone numbers", "3d ago"),
            ),
        ),
        AppEntry(
            id = "orange",
            name = "Flux",
            icon = R.drawable.icon_orange,
            accent = Color(0xFFFF7A1A),
            recentLabel = "Recent notes",
            recent = listOf(
                RecentItem("Grocery list", "süt, ekmek, yumurta, kahve", "10m"),
                RecentItem("Launcher ideas", "widget row for recents, dark theme", "1h"),
                RecentItem("Reading list", "3 articles saved", "yesterday"),
            ),
        ),
        AppEntry(
            id = "settings",
            name = "Settings",
            icon = R.drawable.icon_settings,
            accent = Color(0xFF5F6368),
            recentLabel = "Recently changed",
            recent = listOf(
                RecentItem("Wi-Fi", "connected to \"Ando_5G\"", "2m"),
                RecentItem("Battery", "saver turned off", "1h"),
                RecentItem("Display", "brightness set to auto", "3h"),
            ),
        ),
        AppEntry(
            id = "reddit",
            name = "Reddit",
            icon = R.drawable.icon_reddit,
            accent = Color(0xFFFF4500),
            recentLabel = "Recent posts",
            recent = listOf(
                RecentItem("r/androiddev", "Jetpack Compose 1.7 is out", "▲ 2.1k · 14m"),
                RecentItem("r/Turkey", "En iyi kahve nerede?", "▲ 340 · 1h"),
                RecentItem("r/kotlin", "Coroutines vs Flow, when to use what", "▲ 812 · 3h"),
            ),
        ),
        AppEntry(
            id = "messages",
            name = "Messages",
            icon = R.drawable.icon_messages,
            accent = Color(0xFF1A73E8),
            recentLabel = "Recent texts",
            recent = listOf(
                RecentItem("Ela", "yoldayım, 10 dk", "3m", "1"),
                RecentItem("+90 532 *** 12 34", "Kargonuz teslim edilmiştir.", "1h"),
                RecentItem("Deniz", "tamamdır 👍", "yesterday"),
            ),
        ),
        AppEntry(
            id = "phone",
            name = "Phone",
            icon = R.drawable.icon_phone,
            accent = Color(0xFF34A853),
            recentLabel = "Recent calls",
            recent = listOf(
                RecentItem("Mom ❤️", "Incoming · 4:12", "20m"),
                RecentItem("Unknown", "Missed call", "2h"),
                RecentItem("Kerem", "Outgoing · 1:03", "yesterday"),
            ),
        ),
        AppEntry(
            id = "x",
            name = "X",
            icon = R.drawable.icon_x,
            accent = Color(0xFF0F1419),
            recentLabel = "Recent posts",
            recent = listOf(
                RecentItem("@androiddev", "Compose Multiplatform 1.7 released 🚀", "8m"),
                RecentItem("@kotlin", "New coroutines debugging tools", "2h"),
                RecentItem("@github", "Copilot workspace updates", "5h"),
            ),
        ),
        AppEntry(
            id = "camera",
            name = "Camera",
            icon = R.drawable.icon_camera,
            accent = Color(0xFF202124),
            recentLabel = "Last shots",
            recent = listOf(
                RecentItem("IMG_2618.jpg", "4032×3024 · Portrait", "12m", thumbTint = Color(0xFF6C63FF)),
                RecentItem("VID_0091.mp4", "0:24 · 1080p", "1h", thumbTint = Color(0xFF00BFA5)),
                RecentItem("IMG_2601.jpg", "Panorama", "yesterday", thumbTint = Color(0xFFFF8A65)),
                RecentItem("IMG_2588.jpg", "Night mode", "2d ago", thumbTint = Color(0xFF4E5D6C)),
                RecentItem("IMG_2570.jpg", "Macro · flower", "3d ago", thumbTint = Color(0xFF66BB6A)),
            ),
            isGallery = true,
        ),
        AppEntry(
            id = "calendar",
            name = "Calendar",
            icon = R.drawable.icon_calendar,
            accent = Color(0xFFEA4335),
            recentLabel = "Upcoming",
            recent = listOf(
                RecentItem("Standup", "09:30 · Google Meet", "in 2h"),
                RecentItem("Selin – design review", "14:00 · Room 3", "today"),
                RecentItem("Dentist", "10:00", "tomorrow"),
            ),
        ),
    )
}
