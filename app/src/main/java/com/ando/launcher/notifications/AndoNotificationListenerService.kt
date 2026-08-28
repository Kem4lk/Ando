package com.ando.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ando.launcher.data.AppCatalog

/**
 * Reads only the title/text of notifications from the handful of apps this launcher
 * shows cards for (see [AppCatalog.trackedNotificationPackages]); everything else is
 * ignored. Requires the user to grant "Notification access" in system settings, so it
 * only ever sees notifications posted after that permission was turned on.
 */
class AndoNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.loadFromDisk(applicationContext)
        activeNotifications?.forEach { handle(it) }
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

        NotificationStore.record(applicationContext, packageName, title, text, sbn.postTime, sbn.notification.contentIntent)
    }
}
