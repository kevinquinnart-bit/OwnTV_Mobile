package tv.own.owntv.mobile.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.StageMenuItem
import tv.own.owntv.mobile.ui.stage.StageSheetHeader

/** What Clear favourites can remove, in the television's order: Live TV, Movies, Series, then all. */
private val FAVOURITE_SCOPES: List<MediaType?> = listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES, null)

/**
 * Kevin's copy — Clear favourites, the same two steps as the television: pick Live TV, Movies, Series or
 * All favourites (each with how many it holds; an empty one cannot be picked), then confirm with
 * Cancel / OK. Reached from More › Favourites' red bin.
 */
@Composable
fun ClearFavouritesFlow(counts: TypeCounts, onClear: (MediaType?) -> Unit, onDismiss: () -> Unit) {
    // An index, not the scope itself: null is a real scope here — it means "all favourites".
    var confirming by remember { mutableIntStateOf(-1) }

    if (confirming < 0) {
        MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.more_fav_clear), stage = true) {
            StageSheetHeader(title = stringResource(R.string.more_fav_clear), subtitle = stringResource(R.string.more_fav_clear_choose))
            FAVOURITE_SCOPES.forEachIndexed { index, scope ->
                val n = scope.countIn(counts)
                StageMenuItem(
                    stringResource(scope.labelRes()),
                    onClick = { confirming = index },
                    icon = scope.icon(),
                    count = n.toString(),
                    danger = scope == null,
                    enabled = n > 0,
                    chevron = true,
                )
            }
        }
        return
    }

    val scope = FAVOURITE_SCOPES[confirming]
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(scope.labelRes()), stage = true) {
        StageSheetHeader(
            title = stringResource(scope.confirmRes()),
            subtitle = stringResource(R.string.settings_cannot_undo),
        )
        StageMenuItem(stringResource(R.string.common_cancel), onClick = onDismiss, icon = MobileIcons.Close)
        StageMenuItem(
            stringResource(R.string.common_ok),
            onClick = { onClear(scope); onDismiss() },
            icon = MobileIcons.Delete,
            danger = true,
        )
    }
}

private fun MediaType?.countIn(c: TypeCounts) = when (this) {
    MediaType.LIVE -> c.live
    MediaType.MOVIE -> c.movies
    MediaType.SERIES -> c.series
    else -> c.total
}

private fun MediaType?.labelRes() = when (this) {
    MediaType.LIVE -> R.string.common_nav_live_tv
    MediaType.MOVIE -> R.string.common_nav_movies
    MediaType.SERIES -> R.string.common_nav_series
    else -> R.string.more_fav_clear_all_option
}

private fun MediaType?.confirmRes() = when (this) {
    MediaType.LIVE -> R.string.more_fav_clear_confirm_live
    MediaType.MOVIE -> R.string.more_fav_clear_confirm_movies
    MediaType.SERIES -> R.string.more_fav_clear_confirm_series
    else -> R.string.more_fav_clear_confirm_all
}

private fun MediaType?.icon() = when (this) {
    MediaType.LIVE -> MobileIcons.LiveTv
    MediaType.MOVIE -> MobileIcons.Movie
    MediaType.SERIES -> MobileIcons.Series
    else -> MobileIcons.Delete
}
