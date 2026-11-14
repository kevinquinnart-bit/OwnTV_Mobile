package tv.own.owntv.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Kevin's copy: the free UK and Irish players, as on the TV. Their programmes only play inside their
 * own apps, so each row opens the official app when it is on this phone, and otherwise opens the app
 * store on a search for it. An app is recognised by its name or its package. The list is in
 * [PlayerForChannel]'s order.
 */
internal object FreePlayers {
    class Player(val nameRes: Int, val labels: List<String>, val exactLabels: List<String>, val packages: List<String>)

    val all = listOf(
        Player(R.string.kevin_player_bbc, listOf("iplayer"), emptyList(), listOf("iplayer")),
        Player(R.string.kevin_player_itv, listOf("itvx", "itv hub"), emptyList(), listOf("com.itv.", "air.itv")),
        Player(R.string.kevin_player_c4, listOf("channel 4", "all 4"), emptyList(), listOf("channel4")),
        Player(R.string.kevin_player_c5, listOf("my5"), listOf("5"), listOf("channel5", "my5", "demand5")),
        Player(R.string.kevin_player_rte, listOf("rté player", "rte player"), emptyList(), listOf("ie.rte")),
        Player(R.string.kevin_player_virgin, listOf("virgin media"), emptyList(), listOf("virginmedia")),
    )

    class Installed(val packageName: String, val icon: Bitmap?)

    /** Which of [all] are on this phone, by position. */
    fun find(context: Context): List<Installed?> {
        val pm = context.packageManager
        val apps = LinkedHashMap<String, String>()
        val found = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) }.getOrDefault(emptyList())
        for (info in found) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || pkg in apps) continue
            apps[pkg] = runCatching { info.loadLabel(pm).toString() }.getOrDefault("").trim().lowercase()
        }
        return all.map { p ->
            val hit = apps.entries.firstOrNull { (pkg, label) ->
                val lowPkg = pkg.lowercase()
                p.labels.any { label.contains(it) } || p.exactLabels.any { label == it } || p.packages.any { lowPkg.contains(it) }
            } ?: return@map null
            Installed(hit.key, runCatching { pm.getApplicationIcon(hit.key).toBitmap(96, 96) }.getOrNull())
        }
    }

    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** The app store, on a search for [name]. */
    fun openStore(context: Context, name: String): Boolean {
        for (prefix in listOf("market://search?q=", "https://play.google.com/store/search?c=apps&q=")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(prefix + Uri.encode(name))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}

@Composable
fun PlayersPage() {
    val context = LocalContext.current
    val palette = LocalStagePalette.current
    var refresh by remember { mutableIntStateOf(0) }
    // Coming back from the app store, a newly installed player shows as installed.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val installed by produceState<List<FreePlayers.Installed?>>(List(FreePlayers.all.size) { null }, refresh) {
        value = withContext(Dispatchers.IO) { FreePlayers.find(context.applicationContext) }
    }
    val noStore = stringResource(R.string.kevin_players_no_store)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.kevin_players_note), style = stageText(12.5f, 500), color = palette.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
        FreePlayers.all.forEachIndexed { i, p ->
            val name = stringResource(p.nameRes)
            val app = installed.getOrNull(i)
            val icon = app?.icon
            StageMenuItem(
                label = name,
                onClick = {
                    val ok = if (app != null) FreePlayers.open(context, app.packageName) else FreePlayers.openStore(context, name)
                    if (!ok) Toast.makeText(context, noStore, Toast.LENGTH_LONG).show()
                },
                icon = if (icon == null) MobileIcons.PlayCircle else null,
                leading = if (icon != null) {
                    { Image(bitmap = icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))) }
                } else null,
                count = stringResource(if (app != null) R.string.kevin_players_installed else R.string.kevin_players_missing),
                height = 52.dp,
                chevron = true,
            )
        }
    }
}
