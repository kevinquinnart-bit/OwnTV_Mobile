package tv.own.owntv.mobile.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageLabel
import tv.own.owntv.mobile.ui.stage.StagePoster
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.screens.home.formatRating
import tv.own.owntv.mobile.ui.screens.home.formatYear

/**
 * Kevin's copy: "More like this" under a film or a show — library titles sharing the most genres, then
 * the best rated of the same category. Nothing shows until there is something to show.
 */
@Composable
internal fun MoreLikeThisRow(key: Any, load: suspend () -> List<DetailViewModel.LinkItem>, onOpen: (DetailViewModel.LinkItem) -> Unit) {
    val items by produceState<List<DetailViewModel.LinkItem>>(emptyList(), key) { value = runCatching { load() }.getOrDefault(emptyList()) }
    if (items.isEmpty()) return
    StageLabel(stringResource(R.string.kevin_more_like_this), Modifier.padding(top = 22.dp, bottom = 10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { "${it.tab}_${it.id}" }) { item ->
            StagePoster(
                imageUrl = item.posterUrl,
                title = item.name,
                rating = item.rating?.takeIf { it > 0 }?.let(::formatRating),
                onClick = { onOpen(item) },
                modifier = Modifier.width(POSTER_WIDTH),
            )
        }
    }
}

/** Kevin's copy: an actor's other films and shows in the library, from a tap on their picture. */
@Composable
internal fun WithPersonSheet(
    person: String,
    load: suspend () -> List<DetailViewModel.LinkItem>,
    onOpen: (DetailViewModel.LinkItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val items by produceState<List<DetailViewModel.LinkItem>?>(null, person) { value = runCatching { load() }.getOrDefault(emptyList()) }
    val palette = LocalStagePalette.current
    MobileBottomSheet(onDismissRequest = onDismiss, stage = true) {
        tv.own.owntv.mobile.ui.stage.StageSheetHeader(stringResource(R.string.kevin_with_person, person))
        val list = items
        Column(Modifier.padding(bottom = 12.dp)) {
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = stageAccent.accent, strokeWidth = 3.dp)
                }
                list.isEmpty() -> Text(
                    stringResource(R.string.kevin_with_person_none, person),
                    style = stageText(13.5f, 500),
                    color = palette.dim,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                else -> list.forEach { item ->
                    tv.own.owntv.mobile.ui.stage.StageSettingRow(
                        title = item.name,
                        subtitle = listOfNotNull(
                            stringResource(if (item.tab == LibraryTab.MOVIES) R.string.common_nav_movies else R.string.common_nav_series),
                            item.year?.takeIf { it > 0 }?.let(::formatYear),
                        ).joinToString(stringResource(R.string.content_epg_bits_separator)),
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

private val POSTER_WIDTH = 104.dp
