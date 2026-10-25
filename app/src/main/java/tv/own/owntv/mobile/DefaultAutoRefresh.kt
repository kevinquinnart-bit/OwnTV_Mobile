package tv.own.owntv.mobile

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Kevin's copy: every playlist (and TV Guide source) refreshes itself every 12 hours unless someone has picked a setting for
 * it. Upstream leaves a playlist on Off until it is set by hand, so the channel list went stale until
 * "Resync now" was pressed. A playlist with any saved choice, Off included, is left exactly as chosen;
 * only playlists with nothing saved get the default. Runs just before the Auto refresh check, so the
 * new default is acted on in the same check.
 */
internal suspend fun applyDefaultPlaylistAutoRefresh(settings: SettingsRepository, sourceDao: SourceDao) {
    runCatching {
        val chosen = settings.playlistAutoRefresh.first().keys
        sourceDao.allSourceIds().filter { it !in chosen }.forEach { id ->
            settings.setPlaylistAutoRefresh(id, PlaylistRefresh(PlaylistAutoRefresh.HOURS_12))
        }
    }
    // The TV Guide's sources get the same treatment: refreshed every 12 hours unless a choice is saved.
    runCatching {
        val epgSources = org.koin.core.context.GlobalContext.get().get<EpgSourceStore>()
        val chosen = settings.epgAutoRefresh.first().keys
        epgSources.getAll().map { it.id }.filter { it !in chosen }.forEach { id ->
            settings.setEpgAutoRefresh(id, EpgRefresh(EpgAutoRefresh.HOURS_12))
        }
    }
}

/**
 * Kevin's copy: any playlist with no channels, films or series, and any TV Guide source with no
 * programmes, starts downloading now. Used at start-up and straight after a Wi-Fi sync, which copies
 * a playlist or guide address but never its contents. An empty list or guide is never left waiting:
 * unlike the timed refresh, this does not hold back while something is playing.
 */
internal suspend fun downloadEmptySources() {
    val koin = org.koin.core.context.GlobalContext.get()
    runCatching {
        val channels = koin.get<tv.own.owntv.core.database.dao.ChannelDao>()
        val movies = koin.get<tv.own.owntv.core.database.dao.MovieDao>()
        val series = koin.get<tv.own.owntv.core.database.dao.SeriesDao>()
        val catalog = koin.get<tv.own.owntv.core.sync.work.CatalogSyncScheduler>()
        koin.get<SourceDao>().allSourceIds().forEach { id ->
            val held = channels.countForSourceOnce(id) + movies.countForSourceOnce(id) + series.countForSourceOnce(id)
            if (held == 0) catalog.enqueueSync(id, reason = EMPTY_FILL, policy = androidx.work.ExistingWorkPolicy.KEEP)
        }
    }
    runCatching {
        val epgDao = koin.get<tv.own.owntv.core.database.dao.EpgDao>()
        val epgSync = koin.get<tv.own.owntv.core.sync.work.EpgSyncScheduler>()
        koin.get<EpgSourceStore>().getAll().forEach { src ->
            if (epgDao.countForSources(listOf(src.id)) == 0) {
                epgSync.enqueueSync(src.id, EMPTY_FILL, 0, androidx.work.ExistingWorkPolicy.KEEP)
            }
        }
    }
}

private const val EMPTY_FILL = "empty_fill"

/**
 * Kevin's copy: every time the app is opened, each playlist and TV Guide refreshes, unless it was
 * refreshed in the last [ON_OPEN_MIN_GAP_MS] (so opening it a few times in a row does not download
 * everything again each time). A playlist or guide set to Off is left alone. The same as the television.
 *
 * REPLACE, not KEEP: core's own timed refresh queues its job with KEEP and then holds it until playback
 * stops. Kept, that waiting job would swallow this one and nothing would download while something plays.
 */
internal suspend fun refreshOnOpen() {
    val koin = org.koin.core.context.GlobalContext.get()
    val settings = koin.get<SettingsRepository>()
    val now = System.currentTimeMillis()
    runCatching {
        val modes = settings.epgAutoRefresh.first()
        val epgDao = koin.get<tv.own.owntv.core.database.dao.EpgDao>()
        val epgSync = koin.get<tv.own.owntv.core.sync.work.EpgSyncScheduler>()
        koin.get<EpgSourceStore>().getAll().forEach { src ->
            if (modes[src.id]?.mode == EpgAutoRefresh.OFF) return@forEach
            if (now - (src.lastSyncAt ?: 0L) >= ON_OPEN_MIN_GAP_MS) {
                epgSync.enqueueSync(src.id, ON_OPEN, epgDao.countForSources(listOf(src.id)), androidx.work.ExistingWorkPolicy.REPLACE)
            }
        }
    }
    runCatching {
        val modes = settings.playlistAutoRefresh.first()
        val catalog = koin.get<tv.own.owntv.core.sync.work.CatalogSyncScheduler>()
        koin.get<SourceDao>().getAllOnce().forEach { source ->
            if (modes[source.id]?.mode == PlaylistAutoRefresh.OFF) return@forEach
            if (now - (source.lastSyncAt ?: 0L) >= ON_OPEN_MIN_GAP_MS) {
                catalog.enqueueSync(
                    source.id,
                    reason = ON_OPEN,
                    contentTypes = tv.own.owntv.core.sync.SyncContentTypes.enabledOf(source),
                    policy = androidx.work.ExistingWorkPolicy.REPLACE,
                )
            }
        }
    }
}

private const val ON_OPEN = "on_open_refresh"
private const val ON_OPEN_MIN_GAP_MS = 10 * 60_000L
