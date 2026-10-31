package org.lunaris.lockstyles.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.GalleryWallpaper.Kind
import org.lunaris.lockstyles.LockScreen
import org.lunaris.lockstyles.R

fun wallpaperLabel(wallpaper: GalleryWallpaper): Int = when (wallpaper.kind) {
    Kind.COLOUR -> R.string.gallery_colour
    Kind.BUBBLES, Kind.STRIPES, Kind.WAVES -> R.string.gallery_collections
    Kind.PETALS -> R.string.gallery_petals
    Kind.PAPER -> if (GalleryRenderer.isCollectionPaper(wallpaper.variant)) {
        R.string.gallery_wallwidgy
    } else {
        R.string.gallery_lunaris
    }
    Kind.ASTRONOMY -> R.string.gallery_astronomy
    Kind.WEATHER -> R.string.gallery_weather
    Kind.EMOJI -> R.string.gallery_emoji
    Kind.KALEIDOSCOPE -> R.string.gallery_kaleidoscope
    Kind.PHOTO -> R.string.gallery_photo
    Kind.SHUFFLE -> R.string.gallery_photo_shuffle
}

/**
 * Long-press on the lock screen lands here: swipe through lock screens, tap one to use it. As on
 * iOS, the lock screen shrinks into its card on the way in and grows back out of it on the way
 * to applying or customising.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitcherScreen(
    store: GalleryStore,
    version: Int,
    zoomIn: Boolean,
    onApply: (LockScreen) -> Unit,
    onCustomise: (LockScreen) -> Unit,
    onAdd: () -> Unit,
    onDelete: (LockScreen) -> Unit,
    onClock: (LockScreen) -> Unit,
) {
    var changes by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener: () -> Unit = { changes++ }
        store.addListener(listener)
        onDispose { store.removeListener(listener) }
    }
    val lockScreens = remember(version, changes) { store.lockScreens }
    val current = remember(version, changes) {
        lockScreens.indexOfFirst { it.id == store.currentId }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = current) { lockScreens.size }
    val scope = rememberCoroutineScope()
    // 1 is the lock screen filling the display, 0 is it sitting in its card.
    val zoom = remember { Animatable(if (zoomIn) 1f else 0f) }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    var zooming by remember { mutableStateOf<LockScreen?>(null) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val interactive = zoom.value == 0f && zooming == null && !pager.isScrollInProgress

    LaunchedEffect(lockScreens.isEmpty()) {
        if (lockScreens.isEmpty()) zoom.snapTo(0f)
    }

    LaunchedEffect(cardBounds != null) {
        if (cardBounds != null && zoom.value > 0f && zooming == null) zoom.animateTo(0f, spring)
    }

    LaunchedEffect(version) {
        if (zooming != null) {
            zoom.animateTo(0f, spring)
            zooming = null
        }
    }

    fun zoomOut(lockScreen: LockScreen, then: (LockScreen) -> Unit) {
        if (!interactive) return
        zooming = lockScreen
        scope.launch {
            zoom.animateTo(1f, spring)
            then(lockScreen)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val fullWidth = constraints.maxWidth.toFloat()
        val fullHeight = constraints.maxHeight.toFloat()
        val chrome = (1f - zoom.value).coerceIn(0f, 1f)
        val stackedActions = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        val scrollContent = maxHeight < 600.dp || stackedActions
        val previewHeight = (maxHeight * 0.6f).coerceAtLeast(180.dp)
        Column(
            Modifier.fillMaxSize().systemBarsPadding()
                .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState(), enabled = interactive) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val previewModifier = if (scrollContent) Modifier.height(previewHeight) else Modifier.weight(1f)
            val shown = lockScreens.getOrNull(pager.currentPage)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).alpha(chrome),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.gallery_title), style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.gallery_saved_summary), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (shown != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AnimatedContent(
                                targetState = stringResource(wallpaperLabel(shown.wallpaper)),
                                transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                                label = "kind",
                            ) { label ->
                                Text(label, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            if (shown.id == store.currentId) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.gallery_active),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        IconButton(onClick = { onDelete(shown) }, enabled = interactive) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.gallery_delete),
                                tint = if (interactive) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        }
                    }
                }
            }
            if (lockScreens.isEmpty()) {
                val emptyModifier = if (scrollContent) Modifier.heightIn(min = previewHeight) else previewModifier
                Column(emptyModifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Wallpaper, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.gallery_empty_title), Modifier.padding(top = 24.dp),
                        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.gallery_empty_summary), Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                HorizontalPager(
                    state = pager,
                    key = { lockScreens[it].id },
                    contentPadding = PaddingValues(horizontal = 40.dp),
                    pageSpacing = 20.dp,
                    userScrollEnabled = zoom.value == 0f && zooming == null,
                    modifier = previewModifier,
                ) { page ->
                    val lockScreen = lockScreens[page]
                    val isCurrent = page == pager.currentPage
                    var drag by remember(lockScreen.id) { mutableFloatStateOf(0f) }
                    LaunchedEffect(isCurrent) { if (!isCurrent) drag = 0f }
                    val lift by animateFloatAsState(drag, animationSpec = spring, label = "lift")
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val previewWidth = minOf(maxWidth, maxHeight * 0.46f)
                        val revealDistance = with(LocalDensity.current) { 112.dp.toPx() }
                        val revealThreshold = with(LocalDensity.current) { 40.dp.toPx() }
                        val label = stringResource(wallpaperLabel(lockScreen.wallpaper))
                        val deleteLabel = stringResource(R.string.gallery_delete)
                        if (drag < 0) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).size(56.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .clickable(enabled = interactive) { onDelete(lockScreen) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Delete, deleteLabel, tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(26.dp))
                            }
                        }
                        val pageOffset =
                            abs(pager.currentPage - page + pager.currentPageOffsetFraction)
                        LockScreenCard(
                            wallpaper = lockScreen.wallpaper,
                            clock = lockScreen.clock,
                            live = isCurrent,
                            fixedTime = false,
                            corner = 36.dp,
                            modifier = Modifier
                                .width(previewWidth)
                                .aspectRatio(0.46f)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = label
                                    customActions = if (interactive) listOf(CustomAccessibilityAction(deleteLabel) {
                                        onDelete(lockScreen)
                                        true
                                    }) else emptyList()
                                }
                                .onGloballyPositioned {
                                    if (isCurrent && drag == 0f) cardBounds = it.boundsInRoot()
                                }
                                .graphicsLayer {
                                    // Shrinks toward its top edge so the pager doesn't clip it.
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                    val scale =
                                        (1f - 0.08f * min(1f, pageOffset)) * (1f + lift / (revealDistance * 6f))
                                    scaleX = scale
                                    scaleY = scale
                                    // The zooming copy stands in for the current card.
                                    alpha = if (isCurrent && zoom.value > 0f) 0f
                                    else if (isCurrent) 1f else chrome
                                }
                                .pointerInput(lockScreen.id, interactive, isCurrent) {
                                    if (!interactive || !isCurrent) return@pointerInput
                                    // Swipe a lock screen up to reveal delete, as on iOS.
                                    detectVerticalDragGestures(
                                        onDragEnd = { drag = if (drag < -revealThreshold) -revealDistance else 0f },
                                        onDragCancel = { drag = 0f },
                                    ) { _, dy -> drag = (drag + dy).coerceIn(-revealDistance * 1.15f, 0f) }
                                },
                            onClick = if (interactive) {{
                                when {
                                    !isCurrent -> scope.launch { pager.animateScrollToPage(page) }
                                    drag < 0 -> drag = 0f
                                    else -> zoomOut(lockScreen, onApply)
                                }
                            }} else null,
                        )
                    }
                }
            }
            if (lockScreens.isNotEmpty()) {
                Row(Modifier.padding(vertical = 16.dp).alpha(chrome),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val first = (pager.currentPage - 3).coerceIn(0, (lockScreens.size - 7).coerceAtLeast(0))
                    repeat(minOf(lockScreens.size, 7)) { index ->
                        val selected = first + index == pager.currentPage
                        val width by animateFloatAsState(if (selected) 24f else 6f,
                            animationSpec = spring, label = "page_indicator")
                        Box(Modifier.width(width.dp).height(6.dp).clip(CircleShape).background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    }
                    Text(stringResource(R.string.gallery_page_position, pager.currentPage + 1, lockScreens.size),
                        Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).alpha(chrome)) {
                if (shown == null) {
                    ExpressiveButton(stringResource(R.string.gallery_add_wallpaper), onClick = onAdd,
                        enabled = interactive, modifier = Modifier.padding(8.dp).fillMaxWidth(), icon = Icons.Rounded.Add)
                } else if (stackedActions) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveButton(stringResource(R.string.gallery_customise),
                            onClick = { zoomOut(shown, onCustomise) },
                            enabled = interactive, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Edit)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ExpressiveIconButton(Icons.Rounded.Schedule, stringResource(R.string.gallery_clock),
                                enabled = interactive, onClick = { onClock(shown) })
                            ExpressiveIconButton(Icons.Rounded.Add, stringResource(R.string.gallery_add_wallpaper),
                                enabled = interactive, onClick = onAdd)
                        }
                    }
                } else {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveIconButton(Icons.Rounded.Schedule, stringResource(R.string.gallery_clock),
                            enabled = interactive, onClick = { onClock(shown) })
                        ExpressiveButton(stringResource(R.string.gallery_customise),
                            onClick = { zoomOut(shown, onCustomise) },
                            enabled = interactive, modifier = Modifier.weight(1f), icon = Icons.Rounded.Edit)
                        ExpressiveIconButton(Icons.Rounded.Add, stringResource(R.string.gallery_add_wallpaper),
                            enabled = interactive, onClick = onAdd)
                    }
                }
            }
        }

        // The lock screen on its way between the display and its card.
        // Until the card has been laid out, the lock screen simply fills the display.
        val card = cardBounds ?: Rect(0f, 0f, fullWidth, fullHeight)
        val moving = zooming ?: lockScreens.getOrNull(pager.currentPage)
        if (moving != null && zoom.value > 0f) {
            val t = zoom.value.coerceIn(0f, 1f)
            val left = card.left * (1 - t)
            val top = card.top * (1 - t)
            val width = card.width + (fullWidth - card.width) * t
            val height = card.height + (fullHeight - card.height) * t
            val density = LocalDensity.current
            LockScreenCard(
                wallpaper = moving.wallpaper,
                clock = moving.clock,
                live = true,
                fixedTime = false,
                corner = 36.dp * (1 - t).coerceIn(0f, 1f),
                modifier = Modifier
                    .offset { IntOffset(left.toInt(), top.toInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
            )
        }
    }
}
