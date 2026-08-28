package com.ando.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.Telephony
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ando.launcher.model.AppEntry
import com.ando.launcher.model.RecentItem
import com.ando.launcher.model.SourceKind
import com.ando.launcher.notifications.CapturedNotification
import com.ando.launcher.notifications.NotificationStore
import com.ando.launcher.util.toImageBitmap
import java.util.concurrent.TimeUnit

/** Sentinel stored in [AppEntry.missingPermission] for the notification-access special permission
 *  (it isn't a normal runtime permission, so it can't be requested the usual way). */
const val NOTIFICATION_ACCESS = "notification_access"

/** Every runtime permission any card might need — requested together on first launch. */
val ALL_RUNTIME_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.READ_CALENDAR)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_IMAGES) else add(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * Turns real, on-device signals (installed apps' notifications, MediaStore, the call log,
 * SMS, the calendar, battery/storage/network) into the [AppEntry] list the launcher renders.
 * Nothing gathered here is written anywhere but this device.
 */
class RealContentRepository(private val context: Context) {

    private val packageManager = context.packageManager

    fun isNotificationAccessEnabled(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun buildApps(): List<AppEntry> {
        val notifications = NotificationStore.byPackage.value
        return AppCatalog.all.mapNotNull { entry -> buildEntry(entry, notifications) }
    }

    private fun buildEntry(entry: CatalogEntry, notifications: Map<String, List<CapturedNotification>>): AppEntry? =
        when (entry.source) {
            SourceKind.NOTIFICATIONS -> buildNotificationEntry(entry, notifications)
            SourceKind.MEDIA_PHOTOS -> buildMediaEntry(entry, cameraOnly = false)
            SourceKind.MEDIA_CAMERA -> buildMediaEntry(entry, cameraOnly = true)
            SourceKind.CALENDAR -> buildCalendarEntry(entry)
            SourceKind.CALL_LOG -> buildCallLogEntry(entry)
            SourceKind.SMS -> buildSmsEntry(entry)
            SourceKind.DEVICE_STATUS -> buildDeviceStatusEntry(entry)
        }

    // ---- notification-sourced apps (Telegram, WhatsApp, Chrome, Spotify, Gmail, Maps, ChatGPT, Reddit, X) ----

    private fun buildNotificationEntry(entry: CatalogEntry, notifications: Map<String, List<CapturedNotification>>): AppEntry? {
        val installedPackage = entry.packageCandidates.firstOrNull { isInstalled(it) } ?: return null
        val (name, iconBitmap) = realAppIdentity(installedPackage, entry.fallbackName)

        if (!isNotificationAccessEnabled()) {
            return AppEntry(
                id = entry.id, name = name, icon = entry.fallbackIcon, iconBitmap = iconBitmap,
                accent = entry.accent, recentLabel = "Bildirim erişimi kapalı",
                recent = emptyList(), source = entry.source, missingPermission = NOTIFICATION_ACCESS,
            )
        }

        val captured = notifications[installedPackage].orEmpty().sortedByDescending { it.whenMillis }
        val items = captured.map {
            RecentItem(
                title = it.title.ifBlank { name },
                subtitle = it.text,
                meta = relativeTime(it.whenMillis),
            )
        }
        return AppEntry(
            id = entry.id, name = name, icon = entry.fallbackIcon, iconBitmap = iconBitmap,
            accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Henüz bildirim yok" else "Son bildirimler",
            recent = items, source = entry.source,
        )
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun realAppIdentity(packageName: String, fallbackName: String): Pair<String, ImageBitmap?> = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val label = packageManager.getApplicationLabel(appInfo).toString()
        val icon = packageManager.getApplicationIcon(appInfo).toImageBitmap()
        label to icon
    }.getOrDefault(fallbackName to null)

    // ---- MediaStore: Photos & Camera ----

    private val mediaPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun buildMediaEntry(entry: CatalogEntry, cameraOnly: Boolean): AppEntry {
        if (!hasPermission(mediaPermission)) {
            return permissionNeededEntry(entry, mediaPermission)
        }
        val items = mutableListOf<RecentItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val (selection, args) = if (cameraOnly) {
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?" to arrayOf("Camera")
        } else {
            null to null
        }
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext() && items.size < 8) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                    items += RecentItem(
                        title = cursor.getString(nameCol) ?: "Fotoğraf",
                        subtitle = cursor.getString(bucketCol) ?: "",
                        meta = relativeTime(cursor.getLong(dateCol) * 1000),
                        thumbBitmap = loadImageThumbnail(uri),
                    )
                }
            }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon,
            accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Fotoğraf bulunamadı" else if (cameraOnly) "Son çekimler" else "Son eklenenler",
            recent = items, source = entry.source, isGallery = true,
        )
    }

    private fun loadImageThumbnail(uri: Uri): ImageBitmap? = runCatching {
        val targetSize = 160
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(uri, android.util.Size(targetSize, targetSize), null).asImageBitmap()
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / targetSize)
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }
        }
    }.getOrNull()

    // ---- Calendar ----

    private fun buildCalendarEntry(entry: CatalogEntry): AppEntry {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return permissionNeededEntry(entry, Manifest.permission.READ_CALENDAR)
        }
        val now = System.currentTimeMillis()
        val weekAhead = now + TimeUnit.DAYS.toMillis(7)
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath(weekAhead.toString()).build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val items = mutableListOf<RecentItem>()
        runCatching {
            context.contentResolver.query(instancesUri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val locationCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                    while (cursor.moveToNext() && items.size < 6) {
                        val begin = cursor.getLong(beginCol)
                        items += RecentItem(
                            title = cursor.getString(titleCol) ?: "(başlıksız)",
                            subtitle = cursor.getString(locationCol) ?: "",
                            meta = relativeTime(begin, future = true),
                        )
                    }
                }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Yaklaşan etkinlik yok" else "Yaklaşan",
            recent = items, source = entry.source,
        )
    }

    // ---- Call log ----

    private fun buildCallLogEntry(entry: CatalogEntry): AppEntry {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            return permissionNeededEntry(entry, Manifest.permission.READ_CALL_LOG)
        }
        val items = mutableListOf<RecentItem>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numberCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (cursor.moveToNext() && items.size < 6) {
                    val name = cursor.getString(nameCol)
                    val number = cursor.getString(numberCol)
                    items += RecentItem(
                        title = name?.takeIf { it.isNotBlank() } ?: number ?: "Bilinmeyen",
                        subtitle = callTypeLabel(cursor.getInt(typeCol)),
                        meta = relativeTime(cursor.getLong(dateCol)),
                    )
                }
            }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Arama geçmişi yok" else "Son aramalar",
            recent = items, source = entry.source,
        )
    }

    private fun callTypeLabel(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "Gelen arama"
        CallLog.Calls.OUTGOING_TYPE -> "Giden arama"
        CallLog.Calls.MISSED_TYPE -> "Cevapsız arama"
        CallLog.Calls.REJECTED_TYPE -> "Reddedildi"
        CallLog.Calls.BLOCKED_TYPE -> "Engellendi"
        else -> "Arama"
    }

    // ---- SMS ----

    private fun buildSmsEntry(entry: CatalogEntry): AppEntry {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            return permissionNeededEntry(entry, Manifest.permission.READ_SMS)
        }
        val items = mutableListOf<RecentItem>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext() && items.size < 6) {
                    items += RecentItem(
                        title = cursor.getString(addressCol) ?: "Bilinmeyen",
                        subtitle = cursor.getString(bodyCol) ?: "",
                        meta = relativeTime(cursor.getLong(dateCol)),
                    )
                }
            }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Mesaj yok" else "Son mesajlar",
            recent = items, source = entry.source,
        )
    }

    // ---- Device status (battery / storage / network) — no special permission needed ----

    private fun buildDeviceStatusEntry(entry: CatalogEntry): AppEntry {
        val items = mutableListOf<RecentItem>()

        runCatching {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val plugged = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            if (level >= 0 && scale > 0) {
                val percent = (level * 100 / scale)
                items += RecentItem(
                    title = "Batarya",
                    subtitle = if (plugged) "Şarj oluyor" else "Şarjda değil",
                    meta = "%$percent",
                )
            }
        }

        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
            val totalGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
            items += RecentItem(
                title = "Depolama",
                subtitle = "%.1f GB boş / %.1f GB".format(freeGb, totalGb),
                meta = "${(100 * stat.availableBytes / stat.totalBytes)}%",
            )
        }

        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val network = when {
                caps == null -> "Bağlı değil"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil veri"
                else -> "Bağlı"
            }
            items += RecentItem(title = "Ağ", subtitle = network, meta = "canlı")
        }

        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = "Cihaz durumu", recent = items, source = entry.source,
        )
    }

    // ---- shared helpers ----

    private fun permissionNeededEntry(entry: CatalogEntry, permission: String) = AppEntry(
        id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
        recentLabel = "İzin gerekli", recent = emptyList(), source = entry.source,
        missingPermission = permission, isGallery = entry.source == SourceKind.MEDIA_PHOTOS || entry.source == SourceKind.MEDIA_CAMERA,
    )

    private fun relativeTime(millis: Long, future: Boolean = false): String {
        if (millis <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = if (future) millis - now else now - millis
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val prefix = if (future) "içinde " else ""
        val suffix = if (future) "" else " önce"
        return when {
            diff < 0 -> "şimdi"
            minutes < 1 -> "az önce"
            minutes < 60 -> "$prefix$minutes dk$suffix"
            hours < 24 -> "$prefix$hours sa$suffix"
            days < 2 -> if (future) "yarın" else "dün"
            days < 7 -> "$prefix$days gün$suffix"
            else -> "$prefix${days / 7} hf$suffix"
        }
    }
}
