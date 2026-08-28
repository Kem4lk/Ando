package com.ando.launcher.notifications

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ando.launcher.data.AppCatalog
import com.ando.launcher.util.toImageBitmap

/**
 * Reads only the title/text of notifications from the handful of apps this launcher
 * shows cards for (see [AppCatalog.trackedNotificationPackages]); everything else is
 * ignored. Requires the user to grant "Notification access" in system settings, so it
 * only ever sees notifications posted after that permission was turned on.
 *
 * Being a bound notification listener also lets it read active [MediaController]s, which
 * is how Spotify's card gets a real "recently played" history — Spotify has no public API
 * for that, but its media-session metadata changes are a legitimate, observable proxy.
 */
class AndoNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val trackedControllers = mutableMapOf<String, MediaController>()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> updateControllers(controllers.orEmpty()) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.loadFromDisk(applicationContext)
        MediaHistoryStore.loadFromDisk(applicationContext)
        activeNotifications?.forEach { handle(it) }

        runCatching {
            val manager = getSystemService(MediaSessionManager::class.java)
            mediaSessionManager = manager
            val component = ComponentName(this, AndoNotificationListenerService::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            updateControllers(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        cleanupControllers()
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName !in AppCatalog.trackedNotificationPackages) return
        if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val avatar = runCatching {
            sbn.notification.getLargeIcon()?.loadDrawable(applicationContext)?.toImageBitmap(96)
        }.getOrNull()

        NotificationStore.record(applicationContext, packageName, title, text, sbn.postTime, sbn.notification.contentIntent, avatar)
    }

    // ---- MediaSession-based real play history (Spotify) ----

    private fun updateControllers(controllers: List<MediaController>) {
        val stillActive = controllers.map { it.packageName }.toSet()
        trackedControllers.entries.removeAll { (packageName, controller) ->
            if (packageName !in stillActive) {
                controllerCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
                true
            } else {
                false
            }
        }

        controllers.filter { it.packageName in AppCatalog.trackedMediaPackages }.forEach { controller ->
            if (trackedControllers[controller.packageName]?.sessionToken == controller.sessionToken) return@forEach
            trackedControllers[controller.packageName]?.let { old ->
                controllerCallbacks.remove(old)?.let { old.unregisterCallback(it) }
            }
            trackedControllers[controller.packageName] = controller

            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    recordTrack(controller.packageName, metadata)
                }
            }
            controller.registerCallback(callback)
            controllerCallbacks[controller] = callback
            recordTrack(controller.packageName, controller.metadata)
        }
    }

    private fun recordTrack(packageName: String, metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        MediaHistoryStore.record(applicationContext, packageName, title, artist, System.currentTimeMillis())
    }

    private fun cleanupControllers() {
        controllerCallbacks.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        controllerCallbacks.clear()
        trackedControllers.clear()
    }
}
