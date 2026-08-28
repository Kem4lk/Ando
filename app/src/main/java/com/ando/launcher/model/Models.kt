package com.ando.launcher.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** Where a card's recent-content list actually comes from. */
enum class SourceKind {
    /** Captured live via NotificationListenerService — only sees notifications posted after access was granted. */
    NOTIFICATIONS,
    MEDIA_PHOTOS,
    MEDIA_CAMERA,
    CALENDAR,
    CALL_LOG,
    SMS,
    /** Battery / storage / network — read directly, no special permission. */
    DEVICE_STATUS,
}

/** A single recent-content row shown inside an app's card. */
data class RecentItem(
    val title: String,
    val subtitle: String,
    val meta: String,
    val badge: String? = null,
    val thumbTint: Color? = null,
    /** A real decoded photo/camera thumbnail, when available. */
    val thumbBitmap: ImageBitmap? = null,
)

/** One app tile on the launcher, sourced from a real, on-device signal. */
data class AppEntry(
    val id: String,
    val name: String,
    /** Bundled fallback icon, used when there's no installed-app icon to read. */
    @DrawableRes val icon: Int? = null,
    /** The real installed app's own icon, when this card maps to one. */
    val iconBitmap: ImageBitmap? = null,
    val accent: Color,
    val recentLabel: String,
    val recent: List<RecentItem>,
    val source: SourceKind,
    /** Android permission this card's data needs, if any is still missing. */
    val missingPermission: String? = null,
    val isGallery: Boolean = false,
)
