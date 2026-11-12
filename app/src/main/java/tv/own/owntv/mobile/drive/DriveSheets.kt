package tv.own.owntv.mobile.drive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.settings.BackupViewModel
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * Kevin's copy: Google's sign-in, as on the TV — a short code entered at google.com/device. On the phone
 * that page opens right here, with the code copied, so it is paste and confirm.
 */
@Composable
internal fun DriveSignInSheet(state: BackupViewModel.DriveSignIn, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalStagePalette.current
    val context = LocalContext.current
    val code = state.code
    val ended = state.denied || state.expired || state.failed != null
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.kevin_drive_sign_in_title), stage = true) {
        StageSheetHeader(title = stringResource(R.string.kevin_drive_sign_in_title))
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (code == null && state.failed == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 3.dp)
                }
            }
            if (code != null) {
                Text(stringResource(R.string.kevin_drive_sign_in_steps_phone), style = stageText(13.5f, 500, lineHeight = 1.4f), color = palette.body)
                Text(code.userCode, style = stageText(34f, 800), color = palette.text)
                if (!ended) {
                    StageButton(
                        onClick = {
                            copy(context, code.userCode)
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        text = stringResource(R.string.kevin_drive_open_google),
                        icon = MobileIcons.OpenInNew,
                        style = StageButtonStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val line = when {
                state.denied -> stringResource(R.string.kevin_drive_sign_in_denied)
                state.expired -> stringResource(R.string.kevin_drive_sign_in_expired)
                state.failed != null -> stringResource(R.string.kevin_drive_sign_in_failed, state.failed)
                code != null -> stringResource(R.string.kevin_drive_sign_in_waiting)
                else -> null
            }
            line?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!ended) CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                    Text(it, style = stageText(13f, 500), color = if (ended) palette.text else palette.muted)
                }
            }
            if (ended) {
                StageButton(onClick = onRetry, text = stringResource(R.string.kevin_drive_try_again), icon = MobileIcons.Refresh, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    runCatching {
        val clip = context.getSystemService(ClipboardManager::class.java)
        clip?.setPrimaryClip(ClipData.newPlainText(text, text))
    }
}

/** Kevin's copy: the backups on Drive (newest first), the recordings switch, and Sign out. */
@Composable
internal fun DriveSheet(
    account: String,
    recordingsOn: Boolean,
    recordingLast: DriveRecordings.Last,
    onToggleRecordings: () -> Unit,
    load: suspend () -> List<GoogleDrive.DriveFile>?,
    onPick: (GoogleDrive.DriveFile) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val files by produceState<List<GoogleDrive.DriveFile>?>(null) { value = load() ?: emptyList() }
    val palette = LocalStagePalette.current
    val context = LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.kevin_drive), stage = true) {
        StageSheetHeader(
            title = stringResource(R.string.kevin_drive_restore_title),
            subtitle = if (account.isNotBlank()) stringResource(R.string.kevin_drive_signed_in_as, account) else stringResource(R.string.kevin_drive_signed_in),
        )
        val list = files
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 3.dp)
            }
            list.isEmpty() -> Text(
                stringResource(R.string.kevin_drive_no_backups),
                style = stageText(13.5f, 500),
                color = palette.dim,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            else -> list.forEach { f ->
                tv.own.owntv.mobile.ui.stage.StageSettingRow(
                    title = f.name,
                    subtitle = listOf(
                        android.text.format.DateUtils.formatDateTime(
                            context, f.createdMs,
                            android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME,
                        ),
                        android.text.format.Formatter.formatShortFileSize(context, f.size),
                    ).joinToString(sep),
                    icon = MobileIcons.Restore,
                    onClick = { onPick(f) },
                )
            }
        }
        val recLine = when (val l = recordingLast) {
            DriveRecordings.Last.None -> stringResource(R.string.kevin_drive_recordings_line)
            is DriveRecordings.Last.Copying -> stringResource(R.string.kevin_drive_rec_copying, l.title, l.percent)
            is DriveRecordings.Last.Copied ->
                if (l.removedOld > 0) pluralStringResource(R.plurals.kevin_drive_rec_copied_removed, l.removedOld, l.title, l.removedOld)
                else stringResource(R.string.kevin_drive_rec_copied, l.title)
            is DriveRecordings.Last.Failed -> when {
                l.signedOut -> stringResource(R.string.kevin_drive_error_signed_out)
                l.full -> stringResource(R.string.kevin_drive_rec_full, l.title)
                else -> stringResource(R.string.kevin_drive_rec_failed, l.title)
            }
        }
        tv.own.owntv.mobile.ui.stage.StageSettingRow(
            title = stringResource(R.string.kevin_drive_recordings),
            subtitle = recLine,
            icon = MobileIcons.Record,
            onClick = onToggleRecordings,
        ) { tv.own.owntv.mobile.ui.stage.StageSwitch(recordingsOn) }
        StageMenuItem(stringResource(R.string.kevin_drive_sign_out), onClick = onSignOut, icon = MobileIcons.Close, danger = true)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
    }
}
