package tv.own.owntv.mobile.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionController
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import java.io.File

/**
 * Writing a backup out to a file the user picked, and reading one back in.
 *
 * The backup itself is core's, shared with the television — what is this app's own is the document
 * picker at each end. Core reads and writes real files, so a picked document is copied into the
 * cache before it is opened, and an export is written to the cache and then poured into the URI the
 * picker handed back. That is the price of never asking a phone for all-files access.
 */
class BackupViewModel(
    private val backup: BackupManager,
    private val profileManager: ProfileManager,
    private val context: Context,
    profileDao: ProfileDao,
    settings: SettingsRepository,
    private val companion: CompanionController,
    private val drive: tv.own.owntv.mobile.drive.GoogleDrive,
    private val driveRecordings: tv.own.owntv.mobile.drive.DriveRecordings,
) : ViewModel() {

    // ---- Kevin's copy: Google Drive, as on the TV ----

    data class DriveUi(val configured: Boolean, val signedIn: Boolean, val account: String)

    var driveState by mutableStateOf(driveUi())
        private set
    private fun driveUi() = DriveUi(drive.configured, drive.signedIn, drive.account)

    /** Google's sign-in in progress: the code to enter, and how it is going. */
    data class DriveSignIn(
        val code: tv.own.owntv.mobile.drive.GoogleDrive.DeviceCode? = null,
        val failed: String? = null,
        val denied: Boolean = false,
        val expired: Boolean = false,
    )

    var driveSignIn by mutableStateOf<DriveSignIn?>(null)
        private set
    private var signInJob: kotlinx.coroutines.Job? = null

    fun startDriveSignIn() {
        signInJob?.cancel()
        driveSignIn = DriveSignIn()
        signInJob = viewModelScope.launch {
            val code = runCatching { drive.startSignIn() }.getOrElse {
                driveSignIn = DriveSignIn(failed = it.message ?: it.javaClass.simpleName)
                return@launch
            }
            driveSignIn = DriveSignIn(code)
            var interval = code.intervalMs
            while (true) {
                kotlinx.coroutines.delay(interval)
                when (val r = drive.pollSignIn(code)) {
                    tv.own.owntv.mobile.drive.GoogleDrive.Poll.Waiting -> Unit
                    tv.own.owntv.mobile.drive.GoogleDrive.Poll.SlowDown -> interval += 5_000
                    tv.own.owntv.mobile.drive.GoogleDrive.Poll.Done -> { driveState = driveUi(); driveSignIn = null; return@launch }
                    tv.own.owntv.mobile.drive.GoogleDrive.Poll.Denied -> { driveSignIn = DriveSignIn(code, denied = true); return@launch }
                    tv.own.owntv.mobile.drive.GoogleDrive.Poll.Expired -> { driveSignIn = DriveSignIn(code, expired = true); return@launch }
                    is tv.own.owntv.mobile.drive.GoogleDrive.Poll.Failed -> { driveSignIn = DriveSignIn(code, failed = r.message); return@launch }
                }
            }
        }
    }

    fun cancelDriveSignIn() {
        signInJob?.cancel()
        signInJob = null
        driveSignIn = null
    }

    fun signOutOfDrive() {
        drive.signOut()
        driveState = driveUi()
    }

    val recordingsToDrive: StateFlow<Boolean> get() = driveRecordings.enabled
    val driveRecordingLast: StateFlow<tv.own.owntv.mobile.drive.DriveRecordings.Last> get() = driveRecordings.last
    fun setRecordingsToDrive(on: Boolean) = driveRecordings.setEnabled(on)

    /** The message for a Drive failure. An unrecognised one also carries Google's own reason. */
    private fun driveFailure(e: Throwable): Outcome.Failed {
        driveState = driveUi()
        return when ((e as? tv.own.owntv.mobile.drive.GoogleDrive.DriveException)?.kind) {
            tv.own.owntv.mobile.drive.GoogleDrive.DriveException.Kind.SIGNED_OUT -> Outcome.Failed(R.string.kevin_drive_error_signed_out)
            tv.own.owntv.mobile.drive.GoogleDrive.DriveException.Kind.FULL -> Outcome.Failed(R.string.kevin_drive_error_full)
            null -> Outcome.Failed(R.string.kevin_drive_error, detail = (e.message ?: e.javaClass.simpleName).take(400))
        }
    }

    /** Back up into the cache, send it to Drive › OwnTV › Backups, then remove the local copy. */
    fun exportToDrive(sections: Set<BackupManager.Section>, profileIds: Set<Long>, password: String?) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val dir = File(context.cacheDir, DRIVE_DIR).apply { deleteRecursively(); mkdirs() }
            val path = backup.export(dir, sections, password, profileIds).getOrElse {
                outcome = Outcome.Failed(R.string.settings_backup_export_error)
                busy = false
                return@launch
            }
            val file = File(path)
            outcome = runCatching {
                drive.upload(file, tv.own.owntv.mobile.drive.GoogleDrive.Folder.BACKUPS, file.name, BACKUP_FILE_MIME)
            }.fold(
                onSuccess = { Outcome.Exported(context.getString(R.string.kevin_drive_backup_place, file.name), passwordsOmitted = password.isNullOrBlank()) },
                onFailure = { driveFailure(it) },
            )
            dir.deleteRecursively()
            busy = false
        }
    }

    /** The backups on Drive, newest first; null when Drive could not be read (the reason is shown). */
    suspend fun driveBackups(): List<tv.own.owntv.mobile.drive.GoogleDrive.DriveFile>? =
        runCatching { drive.list(tv.own.owntv.mobile.drive.GoogleDrive.Folder.BACKUPS).sortedByDescending { it.createdMs } }
            .getOrElse { outcome = driveFailure(it); null }

    /** Fetch a backup from Drive, then the normal restore steps. */
    fun restoreFromDrive(file: tv.own.owntv.mobile.drive.GoogleDrive.DriveFile) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val local = File(File(context.cacheDir, DRIVE_DIR).apply { mkdirs() }, file.name.ifBlank { "backup.own" })
            val ok = runCatching { drive.download(file.id, local) }.onFailure { outcome = driveFailure(it) }.isSuccess
            busy = false
            if (ok) beginRestore(local)
        }
    }

    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val activeProfileId: StateFlow<Long> = settings.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), -1L)

    /** A backup of a large catalog takes a while; nothing else may be started on top of it. */
    var busy by mutableStateOf(false)
        private set

    /** What the finished action has to tell the user, shown until they dismiss it. */
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    /** The file being restored, as far as it has been understood so far. */
    var pending by mutableStateOf<PendingRestore?>(null)
        private set

    sealed interface Outcome {
        data class Exported(val name: String, val passwordsOmitted: Boolean) : Outcome
        data class Restored(val summary: BackupManager.ImportSummary) : Outcome
        data class Failed(@param:StringRes val messageRes: Int, val detail: String? = null) : Outcome
    }

    /**
     * [inspection] is null while a sealed file is still locked — a sealed container says nothing at
     * all about itself, not even which sections it holds, until the password opens it.
     */
    data class PendingRestore(
        val file: File,
        val inspection: BackupManager.Inspection? = null,
        val password: String? = null,
        val sealed: Boolean = false,
        val wrongPassword: Boolean = false,
    )

    // ---- Remote (M8-04): the television's companion server, core's. Another device on the same Wi-Fi
    // opens the address, enters the PIN, and downloads this phone's backup or sends one to restore. ----

    /** Idle / Starting / Listening with the PIN and QR / Failed / Locked, for the Remote sheets. */
    val remoteState: StateFlow<CompanionServerState> get() = companion.state

    /** Backups sent from another device; each one goes into [beginRestore]. */
    val remoteBackups: Flow<File> get() = companion.backups

    /** The backup is being written before the Remote server opens to serve it. */
    var preparingRemote by mutableStateOf(false)
        private set

    fun startRemoteRestore() = companion.startForBackupRestore(CompanionLink.DEFAULT_PORT)

    fun stopRemote() = companion.stop()

    /** Writes the backup into the companion's folder, then serves it for another device to download. */
    fun exportRemote(sections: Set<BackupManager.Section>, profileIds: Set<Long>, password: String?) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            preparingRemote = true
            backup.export(companion.backupExportDir, sections, password, profileIds).fold(
                onSuccess = { path -> companion.startForBackupDownload(CompanionLink.DEFAULT_PORT, File(path)) },
                onFailure = { outcome = Outcome.Failed(R.string.settings_backup_export_error) },
            )
            preparingRemote = false
            busy = false
        }
    }

    /** Writes the backup into the cache for the system share sheet; [onReady] gets the file to share. */
    fun exportForShare(sections: Set<BackupManager.Section>, profileIds: Set<Long>, password: String?, onReady: (File) -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            // One shared backup at a time: the last one is gone by the time the next is written.
            val dir = File(context.cacheDir, SHARE_DIR).apply { deleteRecursively(); mkdirs() }
            backup.export(dir, sections, password, profileIds).fold(
                onSuccess = { path -> onReady(File(path)) },
                onFailure = { outcome = Outcome.Failed(R.string.settings_backup_export_error) },
            )
            busy = false
        }
    }

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = profileManager.verifyPin(profile, pin)

    /** The picked document could not even be copied out of its provider — nothing to inspect. */
    fun reportReadFailure() {
        outcome = Outcome.Failed(R.string.settings_backup_read_error)
    }

    fun dismissOutcome() {
        outcome = null
    }

    fun cancelRestore() {
        pending?.file?.delete()
        pending = null
    }

    /** Writes the backup into the cache, then into [target]; the cached copy never survives the call. */
    fun export(
        target: Uri,
        name: String,
        sections: Set<BackupManager.Section>,
        profileIds: Set<Long>,
        password: String?,
    ) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val staged = File(context.cacheDir, EXPORT_DIR)
            val result = backup.export(staged, sections, password, profileIds)
                .mapCatching { path -> copyOut(File(path), target) }
            staged.deleteRecursively()
            outcome = result.fold(
                onSuccess = { Outcome.Exported(name, passwordsOmitted = password.isNullOrBlank()) },
                onFailure = { Outcome.Failed(R.string.settings_backup_export_error) },
            )
            busy = false
        }
    }

    /** Step one of a restore: what is in this file, and does it need a password before anyone can look. */
    fun beginRestore(file: File) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val sealed = backup.isSealed(file)
            pending = if (sealed) {
                PendingRestore(file = file, sealed = true)
            } else {
                backup.sectionsIn(file).fold(
                    onSuccess = { PendingRestore(file = file, inspection = it) },
                    onFailure = {
                        outcome = Outcome.Failed(R.string.settings_backup_read_error)
                        file.delete()
                        null
                    },
                )
            }
            busy = false
        }
    }

    /** The password for a sealed file, which is the only thing that can reveal what is inside it. */
    fun unseal(password: String) {
        val current = pending ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            backup.sectionsIn(current.file, password).fold(
                onSuccess = { pending = current.copy(inspection = it, password = password, wrongPassword = false) },
                onFailure = { pending = current.copy(wrongPassword = true) },
            )
            busy = false
        }
    }

    /**
     * Restore [sections]. [password] may be null even for an encrypted file: everything but the
     * saved passwords comes back, which is a fair trade for a forgotten passphrase.
     */
    fun restore(sections: Set<BackupManager.Section>, password: String?, deviceSettings: Boolean = false) {
        val current = pending ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            backup.import(current.file, sections, password ?: current.password, deviceSettings = deviceSettings).fold(
                onSuccess = {
                    outcome = Outcome.Restored(it)
                    current.file.delete()
                    pending = null
                },
                onFailure = { failure ->
                    if (failure is BackupManager.WrongPasswordException) {
                        pending = current.copy(wrongPassword = true)
                    } else {
                        outcome = Outcome.Failed(R.string.settings_backup_import_error)
                        current.file.delete()
                        pending = null
                    }
                },
            )
            busy = false
        }
    }

    private suspend fun copyOut(source: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("backup target unavailable")
    }

    private companion object {
        /** Long enough that rotating the phone does not restart the profile query. */
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPORT_DIR = "backup-export"

        /** Under the cache; the only folder `res/xml/share_paths.xml` lets the share sheet read. */
        const val SHARE_DIR = "backup-share"
        const val DRIVE_DIR = "backup-drive"
        const val BACKUP_FILE_MIME = "application/octet-stream"
    }
}
