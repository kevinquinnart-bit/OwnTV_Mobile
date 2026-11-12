package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.mobile.R

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/**
 * KueTV (Kevin's copy, v2): the KayKue K mark, the same whatever icon colour or accent is set. [icon] and
 * [followAccent] stay so the callers need no change.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    Image(painterResource(R.drawable.kuetv_mark), contentDescription = null, modifier = modifier.size(size))
}

/**
 * KueTV (Kevin's copy, v2): "Kue" in cream on a dark page (the text colour on a light one), "TV" in the
 * accent, Poppins SemiBold as in the KayKue logo. As tall as the old 988 × 182 wordmark at [width]; the box
 * is as wide as the word ([KUETV_WORD_ASPECT] × its height).
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier) {
    val palette = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current
    val own = if (palette.isLight) palette.text else WordmarkCream
    val tv = tv.own.owntv.mobile.ui.stage.stageAccent.accent
    androidx.compose.foundation.layout.Box(modifier.size(width * 182f / 988f * KUETV_WORD_ASPECT, width * 182f / 988f)) {
        Image(painterResource(R.drawable.owntv_wordmark_own), null, Modifier.matchParentSize(), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(own))
        Image(painterResource(R.drawable.owntv_wordmark_tv), null, Modifier.matchParentSize(), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tv))
    }
}

private val WordmarkCream = Color(0xFFFFF6EE)

/** Width ÷ cap height of "KueTV" in the two wordmark drawings. */
const val KUETV_WORD_ASPECT = 4.486f
