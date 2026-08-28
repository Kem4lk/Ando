package com.ando.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ando.launcher.data.ALL_RUNTIME_PERMISSIONS
import com.ando.launcher.data.AllAppsRepository
import com.ando.launcher.data.LauncherApp
import com.ando.launcher.data.NOTIFICATION_ACCESS
import com.ando.launcher.data.RealContentRepository
import com.ando.launcher.model.AppEntry
import com.ando.launcher.model.RecentItem
import com.ando.launcher.notifications.NotificationStore
import com.ando.launcher.ui.theme.AndoOnSurfaceMuted
import com.ando.launcher.ui.theme.AndoSurface
import com.ando.launcher.ui.theme.AndoSurfaceVariant
import com.ando.launcher.ui.theme.AndoTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: RealContentRepository
    private lateinit var allAppsRepository: AllAppsRepository
    private val refreshTick = mutableStateOf(0)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshTick.value++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = RealContentRepository(applicationContext)
        allAppsRepository = AllAppsRepository(applicationContext)
        NotificationStore.loadFromDisk(applicationContext)
        permissionLauncher.launch(ALL_RUNTIME_PERMISSIONS.toTypedArray())

        setContent {
            AndoTheme {
                val tick by refreshTick
                val notificationSnapshot by NotificationStore.byPackage.collectAsState()
                val apps = remember(tick, notificationSnapshot) { repository.buildApps() }
                val notificationAccessEnabled = remember(tick, notificationSnapshot) {
                    repository.isNotificationAccessEnabled()
                }
                val allApps = remember(tick) { allAppsRepository.loadAll() }
                var drawerOpen by remember { mutableStateOf(false) }

                BackHandler(enabled = drawerOpen) { drawerOpen = false }

                Box(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(
                        apps = apps,
                        showNotificationBanner = !notificationAccessEnabled,
                        onGrantPermission = { permission -> requestPermission(permission) },
                    )

                    // Edge-swipe zone: drag right from the left edge to reveal the all-apps drawer.
                    if (!drawerOpen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(24.dp)
                                .pointerInput(Unit) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDrag = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            totalDrag += dragAmount
                                            if (totalDrag > 80f) drawerOpen = true
                                            change.consume()
                                        },
                                    )
                                },
                        )
                    }

                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = slideInHorizontally(initialOffsetX = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it }),
                    ) {
                        AllAppsDrawer(
                            apps = allApps,
                            onLaunch = { app ->
                                allAppsRepository.launch(app)
                                drawerOpen = false
                            },
                            onClose = { drawerOpen = false },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches permissions or notification access toggled from Settings while we were away.
        refreshTick.value++
    }

    private fun requestPermission(permission: String) {
        if (permission == NOTIFICATION_ACCESS) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            permissionLauncher.launch(arrayOf(permission))
        }
    }
}

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    showNotificationBanner: Boolean,
    onGrantPermission: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showNotificationBanner) {
                item { NotificationAccessBanner(onEnable = { onGrantPermission(NOTIFICATION_ACCESS) }) }
            }
            items(apps, key = { it.id }) { app -> AppCard(app, onGrantPermission) }
            item { Box(Modifier.size(1.dp)) } // bottom breathing room
        }
    }
}

@Composable
private fun NotificationAccessBanner(onEnable: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnable() },
        colors = CardDefaults.cardColors(containerColor = AndoSurfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bildirim erişimi kapalı",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "WhatsApp, Telegram, Spotify gibi uygulamalardan gelen bildirimleri göstermek için dokun ve aç.",
                style = MaterialTheme.typography.bodyMedium,
                color = AndoOnSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AppCard(app: AppEntry, onGrantPermission: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AndoSurfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppCardHeader(app)
            when {
                app.missingPermission != null -> GrantPermissionRow(
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = { onGrantPermission(app.missingPermission) },
                )
                app.recent.isEmpty() -> Text(
                    text = app.recentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AndoOnSurfaceMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
                app.isGallery -> LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(app.recent, key = { it.title + it.meta }) { item -> GalleryItemCard(item) }
                }
                else -> Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    app.recent.forEach { item -> RecentItemCard(item) }
                }
            }
        }
    }
}

@Composable
private fun GrantPermissionRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AndoSurface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "İzin ver",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppCardHeader(app: AppEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(app.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                app.iconBitmap != null -> Image(
                    bitmap = app.iconBitmap,
                    contentDescription = app.name,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                )
                app.icon != null -> Image(
                    painter = painterResource(id = app.icon),
                    contentDescription = app.name,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = app.recentLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AndoOnSurfaceMuted,
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(app.accent),
        )
    }
}

/** A single piece of recent content, rendered as its own small card. */
@Composable
private fun RecentItemCard(item: RecentItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AndoSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.thumbTint != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.thumbTint),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AndoSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.title.take(1).uppercase(),
                    color = AndoOnSurfaceMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AndoOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(text = item.meta, style = MaterialTheme.typography.labelSmall, color = AndoOnSurfaceMuted)
            if (item.badge != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = item.badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Full-screen drawer listing every launchable app on the device. Swipe left, or use back, to close it. */
@Composable
private fun AllAppsDrawer(apps: List<LauncherApp>, onLaunch: (LauncherApp) -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        if (totalDrag < -80f) onClose()
                        change.consume()
                    },
                )
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text(
                    text = "Tüm uygulamalar",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Kapat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onClose() }.padding(8.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gridItems(apps, key = { it.packageName }) { app -> AppDrawerItem(app, onLaunch) }
            }
        }
    }
}

@Composable
private fun AppDrawerItem(app: LauncherApp, onLaunch: (LauncherApp) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onLaunch(app) }
            .padding(vertical = 8.dp),
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A gallery thumbnail card for image-forward apps (Photos, Camera) — sits in a horizontally scrolling row. */
@Composable
private fun GalleryItemCard(item: RecentItem) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AndoSurface)
            .padding(8.dp),
    ) {
        val bitmap: ImageBitmap? = item.thumbBitmap
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(item.thumbTint ?: AndoSurfaceVariant),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                )
            }
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.meta,
                style = MaterialTheme.typography.labelSmall,
                color = AndoOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
