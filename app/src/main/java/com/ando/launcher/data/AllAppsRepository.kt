package com.ando.launcher.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import com.ando.launcher.util.toImageBitmap

data class LauncherApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
)

/** Every app on the device that can be launched from a home screen — backs the all-apps drawer. */
class AllAppsRepository(private val context: Context) {

    fun loadAll(): List<LauncherApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                runCatching {
                    LauncherApp(
                        label = resolveInfo.loadLabel(pm).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        icon = resolveInfo.loadIcon(pm).toImageBitmap(),
                    )
                }.getOrNull()
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(app: LauncherApp) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
