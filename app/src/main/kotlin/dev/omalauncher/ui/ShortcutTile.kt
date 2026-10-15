package dev.omalauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.omalauncher.spokenLabel
import dev.omalauncher.widgets.Shortcut

/**
 * A pinned app: its icon (a silhouette in the accent color, or full color), framed
 * like a widget, or bare on the wallpaper like a stock launcher (LauncherStyle.bareAppIcons).
 */
@Composable
fun ShortcutTile(
    shortcut: Shortcut,
    /** Its name now, as the drawer shows it (Runtime.shortcutLabel). */
    label: String,
    /** From the app catalogs, as the drawer shows it: a silhouette when [LauncherStyle.monochromeIcons]; null for initials. */
    icon: ImageBitmap?,
    focused: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    /** In a paused work profile: drawn dimmed, like stock launchers. */
    paused: Boolean = false,
    /** In a clone profile: read out as a clone, not a work app. */
    clone: Boolean = false,
    /** Has a notification: a dot on the icon. */
    dot: Boolean = false,
) {
    val mono = LauncherStyle.monochromeIcons
    val bare = LauncherStyle.bareAppIcons
    val spoken = spokenLabel(label, shortcut.profile, clone)
    BoxWithConstraints(
        modifier
            .alpha(if (paused) PausedAlpha else 1f)
            .tileFrame(focused)
            .pointerInput(shortcut.key) { detectTapGestures(onTap = { onTap() }) }
            .padding(4.dp),
    ) {
        val showLabel = showsLabel()
        val iconSize = tileIconSize(showLabel)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            val bmp = icon
            when {
                // Bare silhouettes and initials sit on a badge, as in the drawer, so they read on any wallpaper.
                bare && (mono || bmp == null) ->
                    Box(
                        Modifier.size(iconSize).notificationDot(dot).clip(LauncherStyle.shape(iconSize / 2)).background(Palette.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) {
                            Image(bmp, spoken, Modifier.size(iconSize * 0.75f), colorFilter = ColorFilter.tint(Palette.accent))
                        } else {
                            BasicText(initials(label), style = Mono.copy(color = Palette.accent, fontSize = (iconSize.value * 0.35f).sp))
                        }
                    }
                bmp != null ->
                    Image(bmp, spoken, Modifier.size(iconSize).notificationDot(dot), colorFilter = if (mono) ColorFilter.tint(Palette.accent) else null)
                else ->
                    // Apps without an icon get their initials.
                    Box(Modifier.size(iconSize).notificationDot(dot), contentAlignment = Alignment.Center) {
                        BasicText(initials(label), style = Mono.copy(color = Palette.accent, fontSize = (iconSize.value * 0.45f).sp))
                    }
            }
            if (showLabel) TileLabel(label)
        }
    }
}

/**
 * An accent dot in the icon's top corner, for an app with a notification, as stock launchers draw it: round or square
 * as the corners are, ringed in the background color so it stands apart from an accent silhouette.
 */
internal fun Modifier.notificationDot(dot: Boolean) =
    if (!dot) {
        this
    } else {
        drawWithContent {
            drawContent()
            val d = size.minDimension * 0.24f
            val ring = 1.5.dp.toPx()
            val corner = d / 2 * LauncherStyle.cornerScale.coerceAtMost(1f)
            drawRoundRect(Palette.background, Offset(size.width - d, 0f), Size(d, d), CornerRadius(corner))
            drawRoundRect(Palette.accent, Offset(size.width - d + ring, ring), Size(d - 2 * ring, d - 2 * ring), CornerRadius((corner - ring).coerceAtLeast(0f)))
        }
    }

/** Whether a tile is tall enough to fit its label under the icon. */
internal fun BoxWithConstraintsScope.showsLabel() = maxHeight > 64.dp

/** An app tile's icon edge, leaving room for the label when [label]. Bare icons have no frame to fill, so they take nearly the whole cell. */
internal fun BoxWithConstraintsScope.tileIconSize(label: Boolean) = min(maxWidth, maxHeight - if (label) 18.dp else 0.dp) * if (LauncherStyle.bareAppIcons) 0.95f else 0.85f

/** How opaque an app from a paused work profile is drawn. */
internal const val PausedAlpha = 0.4f

/** An app tile's frame, or none for bare icons unless [focused]. */
internal fun Modifier.tileFrame(focused: Boolean) =
    when {
        !LauncherStyle.bareAppIcons -> tile(focused)
        // Bare icons still show which tile is focused or being edited.
        focused -> frame(LauncherStyle.focusWidth, LauncherStyle.focusBorder)
        else -> this
    }

internal fun initials(name: String): String {
    val words = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0][0]}${words[1][0]}".uppercase()
        words.isNotEmpty() -> words[0].take(2).replaceFirstChar(Char::uppercase)
        else -> "?"
    }
}
