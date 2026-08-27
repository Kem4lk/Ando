package com.ando.launcher.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/** A single recent-content row shown inside an app's card. */
data class RecentItem(
    val title: String,
    val subtitle: String,
    val meta: String,
    val badge: String? = null,
    val thumbTint: Color? = null,
)

/** One app tile on the launcher, with its dummy "recent activity" feed. */
data class AppEntry(
    val id: String,
    val name: String,
    @DrawableRes val icon: Int,
    val accent: Color,
    val recentLabel: String,
    val recent: List<RecentItem>,
    /** Image-forward apps (Photos, Camera) render their feed as a horizontally scrolling gallery. */
    val isGallery: Boolean = false,
)
