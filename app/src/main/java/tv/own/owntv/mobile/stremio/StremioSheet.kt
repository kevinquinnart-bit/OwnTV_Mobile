package tv.own.owntv.mobile.stremio

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

private enum class Mode { STREAMS, ADD, REMOVE }

/**
 * Kevin's copy: "Add-on streams" for a film or an episode, as on the TV. [type] is "movie" or "series";
 * [id] the IMDb id, with ":season:episode" for an episode, or null when the title's details have not
 * been fetched. Adding (by pasting the address) and removing add-ons happen in the same sheet.
 */
@Composable
internal fun StremioSheet(
    type: String,
    id: String?,
    onPlay: (StremioAddons.Stream) -> Unit,
    onDismiss: () -> Unit,
) {
    val addons: StremioAddons = koinInject()
    val list by addons.addons.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(Mode.STREAMS) }
    val palette = LocalStagePalette.current
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.kevin_stremio_streams), stage = true) {
        when (mode) {
            Mode.STREAMS -> {
                StageSheetHeader(title = stringResource(R.string.kevin_stremio_streams))
                var found by remember(list, id) { mutableStateOf<StremioAddons.Found?>(null) }
                LaunchedEffect(list, id) {
                    found = null
                    if (id != null && list.isNotEmpty()) {
                        found = runCatching { addons.streams(type, id) }.getOrDefault(StremioAddons.Found(emptyList(), 0, list.size))
                    }
                }
                val note: @Composable (String) -> Unit = { text ->
                    Text(text, style = stageText(13f, 500, lineHeight = 1.4f), color = palette.dim, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                }
                val f = found
                when {
                    list.isEmpty() -> note(stringResource(R.string.kevin_stremio_none_added))
                    id == null -> note(stringResource(R.string.kevin_stremio_needs_imdb_phone))
                    f == null -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 3.dp)
                    }
                    else -> {
                        if (f.playable.isEmpty()) note(stringResource(R.string.kevin_stremio_no_streams))
                        f.playable.forEach { s ->
                            StageSettingRow(title = s.title, subtitle = s.detail, icon = MobileIcons.PlayArrow, onClick = { onPlay(s) })
                        }
                        if (f.torrents > 0) note(pluralStringResource(R.plurals.kevin_stremio_torrents, f.torrents, f.torrents))
                        if (f.failedAddons > 0) note(pluralStringResource(R.plurals.kevin_stremio_failed, f.failedAddons, f.failedAddons))
                    }
                }
                StageMenuItem(stringResource(R.string.kevin_stremio_add), onClick = { mode = Mode.ADD }, icon = MobileIcons.Add, chevron = true)
                if (list.isNotEmpty()) {
                    StageMenuItem(stringResource(R.string.kevin_stremio_remove), onClick = { mode = Mode.REMOVE }, icon = MobileIcons.Delete, chevron = true)
                }
            }
            Mode.ADD -> AddAddon(addons, onDone = { mode = Mode.STREAMS })
            Mode.REMOVE -> {
                StageSheetHeader(title = stringResource(R.string.kevin_stremio_remove_title))
                list.forEach { a ->
                    StageMenuItem(
                        a.name,
                        onClick = { addons.remove(a); mode = Mode.STREAMS },
                        icon = MobileIcons.Delete,
                        danger = true,
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
    }
}

@Composable
private fun AddAddon(addons: StremioAddons, onDone: () -> Unit) {
    val palette = LocalStagePalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<StremioAddons.AddResult?>(null) }
    var working by remember { mutableStateOf(false) }
    StageSheetHeader(title = stringResource(R.string.kevin_stremio_add_title), subtitle = stringResource(R.string.kevin_stremio_add_steps_phone))
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MobileTextField(
            value = address,
            onValueChange = { address = it; result = null },
            label = stringResource(R.string.kevin_stremio_address_label),
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.fillMaxWidth(),
        )
        StageButton(
            onClick = {
                val clip = runCatching {
                    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                }.getOrNull()
                if (!clip.isNullOrBlank()) { address = clip.trim(); result = null }
            },
            text = stringResource(R.string.kevin_stremio_paste),
            icon = MobileIcons.Save,
            modifier = Modifier.fillMaxWidth(),
        )
        StageButton(
            onClick = {
                if (!working) scope.launch {
                    working = true
                    result = addons.add(address)
                    working = false
                    if (result is StremioAddons.AddResult.Added) onDone()
                }
            },
            text = stringResource(R.string.kevin_stremio_add_title),
            icon = MobileIcons.Add,
            style = StageButtonStyle.PRIMARY,
            enabled = address.isNotBlank() && !working,
            modifier = Modifier.fillMaxWidth(),
        )
        val message = when (result) {
            StremioAddons.AddResult.NotAManifest -> stringResource(R.string.kevin_stremio_not_manifest)
            StremioAddons.AddResult.NoStreams -> stringResource(R.string.kevin_stremio_no_stream_resource)
            StremioAddons.AddResult.Unreachable -> stringResource(R.string.kevin_stremio_unreachable)
            else -> null
        }
        message?.let { Text(it, style = stageText(13f, 600), color = palette.text) }
    }
}
