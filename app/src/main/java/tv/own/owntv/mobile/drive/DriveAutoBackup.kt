package tv.own.owntv.mobile.drive

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import tv.own.owntv.core.backup.BackupManager
import java.io.File

/**
 * Kevin's copy: backs the app up to Google Drive › KueTV › Backups by itself.
 *
 * [onAppOpened] is called each time the app comes to the front. Whether that makes a backup depends on
 * [How]: every open (the default, but never twice within [OPEN_GAP_MS]), once a day, once a week, or
 * never. Nothing happens until Google Drive is signed in. The file has its own name, and only the
 * newest [KEEP] of those are kept, so the folder does not fill up; backups made by hand are untouched.
 * Passwords and PINs are left out, as in a backup made by hand without a passphrase.
 */
class DriveAutoBackup(
    context: Context,
    private val drive: GoogleDrive,
    private val backup: BackupManager,
    private val scope: CoroutineScope,
) {
    enum class How(val gapMs: Long) { OPEN(OPEN_GAP_MS), DAILY(24 * HOUR_MS), WEEKLY(7 * 24 * HOUR_MS), OFF(Long.MAX_VALUE) }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val running = Mutex()

    private val _how = MutableStateFlow(runCatching { How.valueOf(prefs.getString(KEY_HOW, null) ?: How.OPEN.name) }.getOrDefault(How.OPEN))
    val how: StateFlow<How> = _how.asStateFlow()

    fun setHow(value: How) {
        prefs.edit().putString(KEY_HOW, value.name).apply()
        _how.value = value
    }

    /** The app came to the front. */
    fun onAppOpened() {
        val how = _how.value
        if (how == How.OFF || !drive.configured || !drive.signedIn) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_MS, 0L) < how.gapMs) return
        if (!running.tryLock()) return
        scope.launch {
            try {
                run()
            } catch (e: Exception) {
                Log.w(TAG, "automatic backup failed: ${e.message}")
            } finally {
                running.unlock()
            }
        }
    }

    private suspend fun run() {
        val dir = File(appContext.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
        try {
            val made = File(backup.export(dir).getOrThrow())
            drive.upload(made, GoogleDrive.Folder.BACKUPS, FILE_NAME, MIME)
            prefs.edit().putLong(KEY_LAST_MS, System.currentTimeMillis()).apply()
            // list() is oldest first: everything but the newest KEEP automatic backups goes.
            val mine = drive.list(GoogleDrive.Folder.BACKUPS).filter { it.name == FILE_NAME }
            mine.dropLast(KEEP).forEach { old -> runCatching { drive.delete(old.id) } }
        } finally {
            dir.deleteRecursively()
        }
    }

    companion object {
        private const val TAG = "KevinDrive"
        private const val PREFS = "kevin_drive_auto_backup"
        private const val KEY_HOW = "how"
        private const val KEY_LAST_MS = "last_ms"
        private const val DIR = "backup-drive-auto"
        private const val FILE_NAME = "kuetv-auto-backup.own"
        private const val MIME = "application/octet-stream"
        private const val KEEP = 3
        private const val HOUR_MS = 3_600_000L
        const val OPEN_GAP_MS = 600_000L
    }
}
