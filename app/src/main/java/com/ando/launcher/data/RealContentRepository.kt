package com.ando.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
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
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.ando.launcher.model.AppEntry
import com.ando.launcher.model.CardAction
import com.ando.launcher.model.ContactAvatar
import com.ando.launcher.model.RecentItem
import com.ando.launcher.model.SourceKind
import com.ando.launcher.notifications.CapturedNotification
import com.ando.launcher.notifications.MediaHistoryStore
import com.ando.launcher.notifications.NotificationStore
import com.ando.launcher.util.toImageBitmap
import java.io.File
import java.util.concurrent.TimeUnit

/** Sentinel stored in [AppEntry.missingPermission] for the notification-access special permission
 *  (it isn't a normal runtime permission, so it can't be requested the usual way). */
const val NOTIFICATION_ACCESS = "notification_access"

/** Sentinel for the "All files access" special permission the Files card needs on Android 11+. */
const val MANAGE_STORAGE_ACCESS = "manage_storage_access"

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
 * Nothing gathered here is written anywhere but this device. Every card and every row also
 * carries a real click action — opening the app, the exact chat, the photo, the event, or the
 * closest system screen for it.
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
            SourceKind.MEDIA_PHOTOS -> buildPhotosEntry(entry)
            SourceKind.MEDIA_CAMERA -> buildCameraEntry(entry)
            SourceKind.CALENDAR -> buildCalendarEntry(entry)
            SourceKind.CALL_LOG -> buildCallLogEntry(entry)
            SourceKind.SMS -> buildSmsEntry(entry)
            SourceKind.FILES -> buildFilesEntry(entry)
            SourceKind.DEVICE_STATUS -> buildDeviceStatusEntry(entry)
        }

    // ---- notification-sourced apps (Telegram, WhatsApp, Chrome, Spotify, Gmail, Maps, ChatGPT, Reddit, X) ----

    private fun buildNotificationEntry(entry: CatalogEntry, notifications: Map<String, List<CapturedNotification>>): AppEntry? {
        val installedPackage = entry.packageCandidates.firstOrNull { isInstalled(it) } ?: return null
        val (name, iconBitmap) = realAppIdentity(installedPackage, entry.fallbackName)
        val openApp = { launchPackage(installedPackage) }
        val primaryAction = notificationPrimaryAction(entry.id, installedPackage, openApp)

        if (!isNotificationAccessEnabled()) {
            return AppEntry(
                id = entry.id, name = name, icon = entry.fallbackIcon, iconBitmap = iconBitmap,
                accent = entry.accent, recentLabel = "Bildirim erişimi kapalı",
                recent = emptyList(), source = entry.source, missingPermission = NOTIFICATION_ACCESS,
                onOpen = openApp, primaryAction = primaryAction,
            )
        }

        // Spotify has no public "recently played" API — a real play history is instead built
        // from MediaSession metadata changes (see AndoNotificationListenerService), not from notifications.
        if (entry.id == "spotify") {
            return buildSpotifyEntry(entry, name, iconBitmap, installedPackage, openApp, primaryAction)
        }

        val captured = notifications[installedPackage].orEmpty().sortedByDescending { it.whenMillis }
        val displayLimit = if (entry.id == "telegram" || entry.id == "whatsapp") 3 else 6
        val items = captured.take(displayLimit).map { notification ->
            RecentItem(
                title = notification.title.ifBlank { name },
                subtitle = notification.text,
                meta = relativeTime(notification.whenMillis),
                onClick = {
                    val opened = notification.contentIntent?.let { runCatching { it.send() }.isSuccess } ?: false
                    if (!opened) openApp()
                },
            )
        }
        val contactsStrip = if (entry.id == "whatsapp") topContacts(captured, name, openApp) else emptyList()

        return AppEntry(
            id = entry.id, name = name, icon = entry.fallbackIcon, iconBitmap = iconBitmap,
            accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Henüz bildirim yok" else "Son sohbetler",
            recent = items, source = entry.source,
            onOpen = openApp, primaryAction = primaryAction, contactsStrip = contactsStrip,
        )
    }

    /** "En sık yazışılanlar" — built from how often each sender has shown up in captured
     *  notifications, since WhatsApp exposes no contact-frequency API of its own. */
    private fun topContacts(captured: List<CapturedNotification>, fallbackName: String, openApp: () -> Unit): List<ContactAvatar> =
        captured.groupBy { it.title.ifBlank { fallbackName } }
            .map { (contactName, notificationsForContact) ->
                val latest = notificationsForContact.maxByOrNull { it.whenMillis }
                ContactAvatar(
                    name = contactName,
                    avatar = notificationsForContact.firstNotNullOfOrNull { it.avatar },
                    count = notificationsForContact.size,
                    onClick = {
                        val opened = latest?.contentIntent?.let { runCatching { it.send() }.isSuccess } ?: false
                        if (!opened) openApp()
                    },
                )
            }
            .sortedByDescending { it.count }
            .take(8)

    /** Spotify's card: a real play history from MediaSession metadata, not notifications. */
    private fun buildSpotifyEntry(
        entry: CatalogEntry,
        name: String,
        iconBitmap: ImageBitmap?,
        installedPackage: String,
        openApp: () -> Unit,
        primaryAction: CardAction,
    ): AppEntry {
        val history = MediaHistoryStore.byPackage.value[installedPackage].orEmpty()
        val items = history.take(6).map { track ->
            RecentItem(
                title = track.title,
                subtitle = track.artist,
                meta = relativeTime(track.whenMillis),
                onClick = openApp,
            )
        }
        return AppEntry(
            id = entry.id, name = name, icon = entry.fallbackIcon, iconBitmap = iconBitmap,
            accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Henüz çalma geçmişi yok" else "Son çalınanlar",
            recent = items, source = entry.source,
            onOpen = openApp, primaryAction = primaryAction,
        )
    }

    private fun notificationPrimaryAction(id: String, installedPackage: String, openApp: () -> Unit): CardAction = when (id) {
        "mail" -> CardAction("Yeni e-posta", Icons.Filled.Edit) {
            safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
        }
        "maps" -> CardAction("Ara", Icons.Filled.Search) {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply { setPackage(installedPackage) })
        }
        "chrome" -> CardAction("Yeni sekme", Icons.Filled.Add) {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply { setPackage(installedPackage) })
        }
        "telegram", "whatsapp" -> CardAction("Sohbetler", Icons.Filled.ChatBubble, openApp)
        "spotify" -> CardAction("Çal", Icons.Filled.PlayArrow, openApp)
        "chatgpt" -> CardAction("Yeni sohbet", Icons.Filled.Add, openApp)
        "reddit" -> CardAction("Gündem", Icons.Filled.TrendingUp, openApp)
        "x" -> CardAction("Gönderiler", Icons.Filled.Home, openApp)
        else -> CardAction("Aç", Icons.Filled.OpenInNew, openApp)
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

    private fun launchPackage(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { safeStart(it) }
    }

    private fun safeStart(intent: Intent) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // ---- MediaStore: Photos & Camera ----

    private val mediaPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun buildPhotosEntry(entry: CatalogEntry): AppEntry {
        val captureAction = CardAction("Çek", Icons.Filled.CameraAlt) { openCameraCapture(video = false) }
        if (!hasPermission(mediaPermission)) {
            return permissionNeededEntry(entry, mediaPermission, primaryAction = captureAction)
        }
        val items = mutableListOf<RecentItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
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
                        onClick = {
                            safeStart(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                            )
                        },
                    )
                }
            }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon,
            accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Fotoğraf bulunamadı" else "Son eklenenler",
            recent = items, source = entry.source, isGallery = true,
            onOpen = { safeStart(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) },
            primaryAction = captureAction,
        )
    }

    /** Camera is just the shutter — a photo/video button pair, no thumbnails to keep it small. */
    private fun buildCameraEntry(entry: CatalogEntry): AppEntry {
        val photoAction = CardAction("Fotoğraf", Icons.Filled.CameraAlt) { openCameraCapture(video = false) }
        val videoAction = CardAction("Video", Icons.Filled.Videocam) { openCameraCapture(video = true) }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = "Hızlı çekim", recent = emptyList(), source = entry.source,
            onOpen = photoAction.onClick, primaryAction = photoAction,
            actionButtons = listOf(photoAction, videoAction),
        )
    }

    /** Opens the camera app pointed at a fresh MediaStore entry, so the shot is actually saved. */
    private fun openCameraCapture(video: Boolean) {
        runCatching {
            if (video) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "Ando_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                safeStart(
                    Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                        if (uri != null) {
                            putExtra(MediaStore.EXTRA_OUTPUT, uri)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                    },
                )
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Ando_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                safeStart(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        if (uri != null) {
                            putExtra(MediaStore.EXTRA_OUTPUT, uri)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                    },
                )
            }
        }
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
        val addEventAction = CardAction("Ekle", Icons.Filled.Add) {
            safeStart(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI))
        }
        val openCalendar = {
            safeStart(
                Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI.buildUpon()
                        .appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
                },
            )
        }
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return permissionNeededEntry(entry, Manifest.permission.READ_CALENDAR, onOpen = openCalendar, primaryAction = addEventAction)
        }
        val now = System.currentTimeMillis()
        val weekAhead = now + TimeUnit.DAYS.toMillis(7)
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath(weekAhead.toString()).build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val items = mutableListOf<RecentItem>()
        runCatching {
            context.contentResolver.query(instancesUri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    val eventIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val locationCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                    while (cursor.moveToNext() && items.size < 6) {
                        val begin = cursor.getLong(beginCol)
                        val eventId = cursor.getLong(eventIdCol)
                        items += RecentItem(
                            title = cursor.getString(titleCol) ?: "(başlıksız)",
                            subtitle = cursor.getString(locationCol) ?: "",
                            meta = relativeTime(begin, future = true),
                            onClick = {
                                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                                safeStart(Intent(Intent.ACTION_VIEW, eventUri))
                            },
                        )
                    }
                }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Yaklaşan etkinlik yok" else "Yaklaşan",
            recent = items, source = entry.source,
            onOpen = openCalendar, primaryAction = addEventAction,
        )
    }

    // ---- Call log ----

    private fun buildCallLogEntry(entry: CatalogEntry): AppEntry {
        val dialAction = CardAction("Ara", Icons.Filled.Call) { safeStart(Intent(Intent.ACTION_DIAL)) }
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            return permissionNeededEntry(entry, Manifest.permission.READ_CALL_LOG, onOpen = dialAction.onClick, primaryAction = dialAction)
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
                        onClick = {
                            if (!number.isNullOrBlank()) {
                                safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                            }
                        },
                    )
                }
            }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Arama geçmişi yok" else "Son aramalar",
            recent = items, source = entry.source,
            onOpen = dialAction.onClick, primaryAction = dialAction,
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
        val openMessaging = {
            safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
        }
        val newMessageAction = CardAction("Yeni", Icons.Filled.Edit) {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("smsto:")))
        }
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            return permissionNeededEntry(entry, Manifest.permission.READ_SMS, onOpen = openMessaging, primaryAction = newMessageAction)
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
                    val address = cursor.getString(addressCol)
                    items += RecentItem(
                        title = address ?: "Bilinmeyen",
                        subtitle = cursor.getString(bodyCol) ?: "",
                        meta = relativeTime(cursor.getLong(dateCol)),
                        onClick = {
                            if (!address.isNullOrBlank()) {
                                safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("smsto:" + Uri.encode(address))))
                            }
                        },
                    )
                }
            }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Mesaj yok" else "Son mesajlar",
            recent = items, source = entry.source,
            onOpen = openMessaging, primaryAction = newMessageAction,
        )
    }

    // ---- Device status (battery / storage / network) — no special permission needed ----

    private fun buildDeviceStatusEntry(entry: CatalogEntry): AppEntry {
        val items = mutableListOf<RecentItem>()

        runCatching {
            val info = packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            items += RecentItem(
                title = "Ando",
                subtitle = "Sürüm ${info.versionName}",
                meta = "build $versionCode",
                onClick = {
                    safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                },
            )
        }

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
                    onClick = { safeStart(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
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
                onClick = { safeStart(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
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
            items += RecentItem(
                title = "Ağ", subtitle = network, meta = "canlı",
                onClick = { safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            )
        }

        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = "Cihaz durumu", recent = items, source = entry.source,
            onOpen = { safeStart(Intent(Settings.ACTION_SETTINGS)) },
            primaryAction = CardAction("Ayarlar", Icons.Filled.SettingsIcon) { safeStart(Intent(Settings.ACTION_SETTINGS)) },
            compactStats = true,
        )
    }

    // ---- Files (Downloads & Documents) ----

    private fun buildFilesEntry(entry: CatalogEntry): AppEntry {
        val openSettings = {
            if (Build.VERSION.SDK_INT >= 30) {
                safeStart(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)),
                )
            }
        }
        val browseAction = CardAction("Gözat", Icons.Filled.FolderOpen, openSettings)

        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            return permissionNeededEntry(entry, MANAGE_STORAGE_ACCESS, onOpen = openSettings, primaryAction = browseAction)
        }

        val items = mutableListOf<RecentItem>()
        runCatching {
            val roots = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            )
            roots.filter { it.isDirectory }
                .flatMap { dir -> dir.listFiles()?.filter { it.isFile } ?: emptyList() }
                .sortedByDescending { it.lastModified() }
                .take(6)
                .forEach { file ->
                    items += RecentItem(
                        title = file.name,
                        subtitle = humanFileSize(file.length()),
                        meta = relativeTime(file.lastModified()),
                        onClick = { openFile(file) },
                    )
                }
        }
        return AppEntry(
            id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
            recentLabel = if (items.isEmpty()) "Dosya bulunamadı" else "Son dosyalar",
            recent = items, source = entry.source,
            onOpen = openSettings, primaryAction = browseAction,
        )
    }

    private fun openFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = MimeTypeMap.getFileExtensionFromUrl(file.name)
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                ?: "*/*"
            safeStart(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }
    }

    private fun humanFileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    // ---- shared helpers ----

    private fun permissionNeededEntry(
        entry: CatalogEntry,
        permission: String,
        onOpen: (() -> Unit)? = null,
        primaryAction: CardAction? = null,
    ) = AppEntry(
        id = entry.id, name = entry.fallbackName, icon = entry.fallbackIcon, accent = entry.accent,
        recentLabel = "İzin gerekli", recent = emptyList(), source = entry.source,
        missingPermission = permission, isGallery = entry.source == SourceKind.MEDIA_PHOTOS || entry.source == SourceKind.MEDIA_CAMERA,
        onOpen = onOpen, primaryAction = primaryAction,
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
