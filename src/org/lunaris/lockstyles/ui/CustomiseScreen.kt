package org.lunaris.lockstyles.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.AccountCircle
import org.lunaris.lockstyles.settings.ui.DepthPage
import org.lunaris.lockstyles.settings.ui.WidgetsPage
import org.lunaris.lockstyles.settings.ui.SettingsPage
import org.lunaris.lockstyles.settings.ui.AodAvatarSettings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.lunaris.lockstyles.settings.ui.components.squishable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.GalleryWallpaper.Kind
import org.lunaris.lockstyles.GalleryWallpaper.Shuffle
import org.lunaris.lockstyles.GalleryWeather
import org.lunaris.lockstyles.HomeStyle
import org.lunaris.lockstyles.LockScreen
import org.lunaris.lockstyles.Shortcuts
import org.lunaris.lockstyles.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CLOCK_COLOURS = listOf(null, 0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(),
    0xFFFF9F0A.toInt(), 0xFFFF6482.toInt(), 0xFFBF5AF2.toInt(), 0xFF64D2FF.toInt(),
    0xFF30D158.toInt(), 0xFF8E8E93.toInt(), 0xFF1C1C1E.toInt())
private val ACCENT: Color
    @Composable get() = MaterialTheme.colorScheme.primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomiseScreen(
    initial: LockScreen,
    isNew: Boolean,
    openClock: Boolean = false,
    shortcuts: Shortcuts,
    onCancel: () -> Unit,
    onDone: (LockScreen, CustomClocks.Tuning) -> Unit,
) {
    val context = LocalContext.current
    // Unlock effects are no longer configurable. Clear the legacy value when an
    // existing lock screen is opened so it cannot be carried forward on save.
    var wallpaper by remember { mutableStateOf(initial.wallpaper.copy(effect = 0)) }
    var clock by remember { mutableStateOf(initial.clock) }
    var tuning by remember { mutableStateOf(initial.clock.tuning ?: CustomClocks.Tuning.load(context)) }
    var sheet by remember { mutableStateOf<String?>(if (openClock) "clock" else null) }
    var askHome by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<GalleryWeather.Snapshot?>(null) }
    LaunchedEffect(Unit) {
        weather = withContext(Dispatchers.IO) { GalleryWeather.query(context) }
    }
    // The preview is already on screen from the switcher; only the controls fade in.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320, delayMillis = 60)) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LiveWallpaper(wallpaper, Modifier.fillMaxSize(), weather)
        val onLight = rememberTopLight(wallpaper)
        if (clock.face > 0) {
            CustomClockLayer(clock, tuning = tuning)
            // The clock area stays tappable, as iOS lets you tap the clock to edit it.
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.3f).clickable { sheet = "clock" })
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same proportions as the switcher's cards, so zooming in lines up.
            val screenHeight = maxHeight
            val dateSize = (maxWidth.value * 0.055f).sp
            Column(
                Modifier.fillMaxWidth().padding(top = maxHeight * 0.1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dateString(), color = if (onLight) Color(0xFF1C1C1E) else Color.White,
                    fontSize = dateSize, fontWeight = FontWeight.Medium)
                // Framed, as iOS marks what can be edited.
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x66FFFFFF).copy(alpha = 0.4f * appear.value),
                            RoundedCornerShape(16.dp))
                        .background(Color(0x14FFFFFF).copy(alpha = 0.08f * appear.value))
                        .clickable { sheet = "clock" }
                        .padding(horizontal = 16.dp),
                ) {
                    ClockText(clock, screenHeight * if (clock.small) 0.06f else 0.24f,
                        fixedTime = false, onLight = onLight)
                }
            }
        }

        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp,
                vertical = 12.dp).alpha(appear.value),
        ) {
            Pill(stringResource(android.R.string.cancel), primary = false, onCancel)
            Spacer(Modifier.weight(1f))
            Pill(stringResource(if (isNew) R.string.gallery_add else R.string.gallery_done),
                primary = true) {
                askHome = true
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().alpha(appear.value)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
                .navigationBarsPadding().padding(bottom = 16.dp, top = 20.dp),
        ) {
            KindControls(wallpaper) { wallpaper = it }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                RoundAction(Icons.Rounded.Schedule, stringResource(R.string.gallery_clock)) {
                    sheet = "clock"
                }
                RoundAction(Icons.Rounded.Bolt, stringResource(R.string.gallery_shortcuts)) {
                    sheet = "shortcuts"
                }
                RoundAction(Icons.Rounded.Layers, stringResource(R.string.gallery_depth)) {
                    sheet = "depth"
                }
                RoundAction(Icons.Rounded.Widgets, stringResource(R.string.gallery_widgets)) {
                    sheet = "widgets"
                }
                RoundAction(Icons.Rounded.AccountCircle, stringResource(R.string.aod_avatar_title)) {
                    sheet = "avatar"
                }
            }
        }
    }

    if (sheet == "clock") {
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            ClockSheet(clock, tuning, wallpaper, { clock = it }, { tuning = it })
        }
    }
    if (sheet == "shortcuts") {
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            ShortcutsSheet(shortcuts)
        }
    }
    if (sheet == "depth") {
        ModalBottomSheet(onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            DepthPage(back = { sheet = null }, inSheet = true)
        }
    }
    if (sheet == "widgets" || sheet == "avatar") {
        ModalBottomSheet(onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            if (sheet == "widgets") {
                WidgetsPage(back = { sheet = null }, inSheet = true)
            } else {
                SettingsPage(stringResource(R.string.aod_avatar_title), { sheet = null }, inSheet = true) {
                    item { AodAvatarSettings() }
                }
            }
        }
    }
    if (askHome) {
        HomeDialog(wallpaper, onDismiss = { askHome = false }, onSkip = {
            askHome = false
            onDone(initial.copy(wallpaper = wallpaper, clock = clock.copy(tuning = tuning),
                home = HomeStyle.KEEP), tuning)
        }) { home ->
            askHome = false
            onDone(initial.copy(wallpaper = wallpaper, clock = clock.copy(tuning = tuning),
                home = home), tuning)
        }
    }
}

@Composable
private fun Pill(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (primary) colors.primary else colors.surfaceContainerHigh,
        modifier = Modifier.squishable(),
    ) {
        Text(text, color = if (primary) colors.onPrimary else colors.onSurface,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(56.dp).squishable().clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier.squishable().clip(RoundedCornerShape(50))
                    .background(if (on) colors.primary else colors.surfaceContainerHighest)
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(label, color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Swatches(colors: List<Int?>, selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(if (on) 5.dp else 0.dp).clip(CircleShape)
                    .background(if (c == null) Brush.sweepGradient(listOf(Color.Red,
                        Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta,
                        Color.Red)) else SolidColor(Color(c)))
                    .clickable { onSelect(c) },
            )
        }
    }
}

@Composable
private fun KindControls(wallpaper: GalleryWallpaper, onChange: (GalleryWallpaper) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (wallpaper.kind) {
            Kind.PHOTO -> {
                Chips(listOf(stringResource(R.string.gallery_filter_natural),
                    stringResource(R.string.gallery_filter_mono),
                    stringResource(R.string.gallery_filter_duotone),
                    stringResource(R.string.gallery_filter_wash)), wallpaper.variant) {
                    onChange(wallpaper.copy(variant = it))
                }
                Surface(shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.gallery_depth),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.gallery_depth_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThumbSwitch(wallpaper.depth) { onChange(wallpaper.copy(depth = it)) }
                    }
                }
            }
            Kind.SHUFFLE -> Chips(listOf(stringResource(R.string.gallery_shuffle_tap),
                stringResource(R.string.gallery_shuffle_lock),
                stringResource(R.string.gallery_shuffle_hourly),
                stringResource(R.string.gallery_shuffle_daily)), wallpaper.shuffle.ordinal) {
                onChange(wallpaper.copy(shuffle = Shuffle.entries[it]))
            }
            Kind.COLOUR -> {
                Swatches(GalleryRenderer.COLOURS.toList(), wallpaper.colors.firstOrNull()) {
                    onChange(wallpaper.copy(colors = listOfNotNull(it)))
                }
                Chips(listOf(stringResource(R.string.gallery_colour_vibrant),
                    stringResource(R.string.gallery_colour_tone),
                    stringResource(R.string.gallery_colour_deep),
                    stringResource(R.string.gallery_colour_solid)), wallpaper.variant) {
                    onChange(wallpaper.copy(variant = it))
                }
            }
            Kind.EMOJI -> {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = wallpaper.emojis,
                        onValueChange = { text ->
                            val emojis = GalleryRenderer.splitEmoji(text).take(6)
                            onChange(wallpaper.copy(emojis = emojis.joinToString("")))
                        },
                        textStyle = TextStyle(fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Chips(listOf(stringResource(R.string.gallery_emoji_small),
                    stringResource(R.string.gallery_emoji_medium),
                    stringResource(R.string.gallery_emoji_large),
                    stringResource(R.string.gallery_emoji_rings),
                    stringResource(R.string.gallery_emoji_spiral)), wallpaper.variant) {
                    onChange(wallpaper.copy(variant = it))
                }
                Swatches(listOf(0xFFBFE3F2.toInt(), 0xFFF6D7E0.toInt(), 0xFFF3D36B.toInt(),
                    0xFFC8E6C9.toInt(), 0xFFE1D5F5.toInt(), 0xFF1D3B5A.toInt(),
                    0xFF2B2B2B.toInt(), 0xFFFFFFFF.toInt()), wallpaper.colors.firstOrNull()) {
                    onChange(wallpaper.copy(colors = listOfNotNull(it)))
                }
            }
            Kind.KALEIDOSCOPE -> Chips(listOf("6", "8", "12"), wallpaper.variant) {
                onChange(wallpaper.copy(variant = it))
            }
            Kind.ASTRONOMY -> Chips(listOf(stringResource(R.string.gallery_astro_earth),
                stringResource(R.string.gallery_astro_earth_detail),
                stringResource(R.string.gallery_astro_moon),
                stringResource(R.string.gallery_astro_moon_detail),
                stringResource(R.string.gallery_astro_solar),
                stringResource(R.string.gallery_astro_mars)), wallpaper.variant) {
                onChange(wallpaper.copy(variant = it))
            }
            Kind.BUBBLES, Kind.STRIPES, Kind.WAVES, Kind.PETALS -> {
                val count = when (wallpaper.kind) {
                    Kind.BUBBLES -> GalleryRenderer.BUBBLE_VARIANTS
                    Kind.STRIPES -> GalleryRenderer.STRIPE_VARIANTS
                    Kind.WAVES -> GalleryRenderer.WAVE_VARIANTS
                    else -> GalleryRenderer.PETAL_VARIANTS
                }
                VariantStrip(wallpaper, (0 until count).toList(), onChange)
            }
            Kind.WEATHER -> {}
            Kind.PAPER -> {
                // Choose among the wallpaper's own set: WallWidgy's or the Lunaris walls.
                val collection = GalleryRenderer.isCollectionPaper(wallpaper.variant)
                val variants =
                    if (collection) 0 until GalleryRenderer.PAPER_COLLECTIONS
                    else GalleryRenderer.PAPER_COLLECTIONS until GalleryRenderer.PAPERS.size
                VariantStrip(wallpaper, variants.toList(), onChange)
            }
        }
    }
}

/**
 * The wallpaper's variants as a strip that scrolls and snaps to the middle. Like a picker
 * wheel, whichever is in the middle is the chosen one, so the preview follows the scroll;
 * tapping one brings it to the middle. The edges fade to show there's more.
 */
@Composable
private fun VariantStrip(
    wallpaper: GalleryWallpaper,
    variants: List<Int>,
    onChange: (GalleryWallpaper) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = variants.indexOf(wallpaper.variant).coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val current by rememberUpdatedState(wallpaper)
    // Set when the scroll picked the variant, so the strip doesn't then scroll to it again.
    val pickedByScroll = remember { booleanArrayOf(false) }
    LaunchedEffect(state, variants) {
        snapshotFlow {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index
        }.distinctUntilChanged().collect { index ->
            val variant = index?.let(variants::getOrNull) ?: return@collect
            if (state.isScrollInProgress && variant != current.variant) {
                pickedByScroll[0] = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(current.copy(variant = variant))
            }
        }
    }
    LaunchedEffect(wallpaper.variant) {
        if (pickedByScroll[0]) {
            pickedByScroll[0] = false
            return@LaunchedEffect
        }
        val index = variants.indexOf(wallpaper.variant)
        if (index < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            state.animateScrollToItem(index)
        } else {
            // Centre it: move by how far its middle is from the viewport's.
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            state.animateScrollBy((item.offset + item.size / 2 - middle).toFloat(),
                spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - VARIANT_WIDTH) / 2
        LazyRow(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().height(VARIANT_WIDTH * 2 + 16.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(
                        0f to Color.Transparent, 0.12f to Color.Black,
                        0.88f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(variants, key = { it }) { i ->
                val on = i == wallpaper.variant
                val scale by animateFloatAsState(if (on) 1.12f else 0.94f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow), label = "variant")
                Box(
                    Modifier.width(VARIANT_WIDTH).aspectRatio(0.5f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(12.dp))
                        .border(if (on) 2.dp else 1.dp,
                            if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .alpha(if (on) 1f else 0.75f)
                        .clickable {
                            if (!on) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChange(wallpaper.copy(variant = i))
                            }
                        },
                ) {
                    WallpaperImage(wallpaper.copy(variant = i), Modifier.fillMaxSize())
                }
            }
        }
    }
}

private val VARIANT_WIDTH = 52.dp

@Composable
private fun ClockSheet(
    clock: ClockStyle,
    tuning: CustomClocks.Tuning,
    wallpaper: GalleryWallpaper,
    onChange: (ClockStyle) -> Unit,
    onTuning: (CustomClocks.Tuning) -> Unit,
) {
    val context = LocalContext.current
    val styles by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) { CustomClocks.names(context) }
    }
    // Kept to half the screen and scrolled inside, so the clock preview above stays in view.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)
        .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (styles.size > 1) {
            Text(stringResource(R.string.gallery_clock_style), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(styles) { face, name ->
                    val on = clock.face == face
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(104.dp)
                            .clickable { onChange(clock.copy(face = face)) }) {
                        Box(
                            Modifier.fillMaxWidth().height(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(if (on) 2.dp else 0.dp,
                                    if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(14.dp))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LockClock(clock.copy(face = face, small = face == 0, tuning = tuning),
                                if (face == 0) 30.dp else 84.dp, 92.dp, fixedTime = face == 0)
                        }
                        Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (clock.face == 0) {
            Text(stringResource(R.string.gallery_clock_font), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CLOCK_PRESETS.indices.forEach { i ->
                    val on = (clock.preset ?: 3) == i
                    Box(
                        Modifier.size(width = 64.dp, height = 72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(if (on) 2.dp else 0.dp,
                                if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(14.dp))
                            .clickable { onChange(clock.copy(preset = i)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        ClockText(ClockStyle(preset = i, rounded = clock.rounded, small = true),
                            30.dp, fixedTime = true)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gallery_clock_rounded), color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(clock.rounded, { onChange(clock.copy(rounded = it)) },
                    colors = switchColors())
            }
        }
        // Some custom styles keep their own colours on the lock screen.
        if (clock.face == 0 || CustomClocks.isColourable(clock.face)) {
            Text(stringResource(R.string.gallery_clock_colour), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            if (clock.face > 0) {
                val mode = when {
                    tuning.gradient -> 2
                    tuning.accent -> 1
                    else -> 0
                }
                Chips(listOf(stringResource(R.string.gallery_clock_colour_custom),
                    stringResource(R.string.gallery_clock_colour_accent),
                    stringResource(R.string.gallery_clock_colour_gradient)), mode) {
                    onTuning(tuning.copy(accent = it == 1, gradient = it == 2))
                }
            }
            if (clock.face == 0 || (!tuning.accent && !tuning.gradient)) {
                ClockColourPicker(CLOCK_COLOURS, clock.color, wallpaper) { onChange(clock.copy(color = it)) }
            }
            if (clock.face > 0 && tuning.gradient) {
                SheetLabel(stringResource(R.string.gallery_clock_gradient_start))
                ClockColourPicker(GRADIENT_COLOURS, tuning.gradientStart, wallpaper) {
                    onTuning(tuning.copy(gradientStart = it ?: tuning.gradientStart))
                }
                SheetLabel(stringResource(R.string.gallery_clock_gradient_end))
                ClockColourPicker(GRADIENT_COLOURS, tuning.gradientEnd, wallpaper) {
                    onTuning(tuning.copy(gradientEnd = it ?: tuning.gradientEnd))
                }
                TuningSlider(stringResource(R.string.gallery_clock_gradient_position),
                    tuning.gradientAnchorY, 0..100, "%", CustomClocks.Tuning().gradientAnchorY) {
                    onTuning(tuning.copy(gradientAnchorY = it))
                }
                TuningSlider(stringResource(R.string.gallery_clock_gradient_spread),
                    tuning.gradientRadius, 25..200, "%", CustomClocks.Tuning().gradientRadius) {
                    onTuning(tuning.copy(gradientRadius = it))
                }
            }
        }
        if (clock.face > 0) {
            Text(stringResource(R.string.gallery_clock_adjust), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            TuningSlider(stringResource(R.string.gallery_clock_size), tuning.scale, 50..150, "%",
                CustomClocks.Tuning().scale) {
                onTuning(tuning.copy(scale = it))
            }
            TuningSlider(stringResource(R.string.gallery_clock_opacity), tuning.opacity, 10..100,
                "%", CustomClocks.Tuning().opacity) { onTuning(tuning.copy(opacity = it)) }
            TuningSlider(stringResource(R.string.gallery_clock_margin_top), tuning.marginTop,
                0..200, " dp", CustomClocks.Tuning().marginTop) { onTuning(tuning.copy(marginTop = it)) }
            if (CustomClocks.supportsHorizontalMargin(clock.face)) {
                TuningSlider(stringResource(R.string.gallery_clock_margin_start), tuning.marginStart,
                    -200..200, " dp", CustomClocks.Tuning().marginStart) {
                    onTuning(tuning.copy(marginStart = it))
                }
            }
            TuningSlider(stringResource(R.string.custom_clock_rotation_title), tuning.rotation,
                -180..180, "°", CustomClocks.Tuning().rotation) { onTuning(tuning.copy(rotation = it)) }
            TuningSlider(stringResource(R.string.clock_tilt_x_title), tuning.tiltX,
                -75..75, "°", CustomClocks.Tuning().tiltX) { onTuning(tuning.copy(tiltX = it)) }
            TuningSlider(stringResource(R.string.clock_tilt_y_title), tuning.tiltY,
                -75..75, "°", CustomClocks.Tuning().tiltY) { onTuning(tuning.copy(tiltY = it)) }
            if (CustomClocks.supportsHyperBlur(clock.face) && !tuning.gradient) {
                TuningSwitch(stringResource(R.string.lock_screen_custom_clock_hyper_blur_title),
                    tuning.hyperBlur) { onTuning(tuning.copy(hyperBlur = it)) }
            }
            TuningSwitch(stringResource(R.string.gallery_clock_album_art),
                tuning.albumArtColour) { onTuning(tuning.copy(albumArtColour = it)) }
            TuningSwitch(stringResource(R.string.gallery_clock_aod_animation),
                tuning.aodAnimation) { onTuning(tuning.copy(aodAnimation = it)) }
            TuningSwitch(stringResource(R.string.gallery_clock_wobble), tuning.wobbleOnCharge) {
                onTuning(tuning.copy(wobbleOnCharge = it))
            }
            TuningSwitch(stringResource(R.string.gallery_clock_weather), tuning.weather) {
                onTuning(tuning.copy(weather = it))
            }
        }
        if (clock.face == 0) {
            Chips(listOf(stringResource(R.string.gallery_clock_large),
                stringResource(R.string.gallery_clock_small)), if (clock.small) 1 else 0) {
                onChange(clock.copy(small = it == 1))
            }
        }
    }
}

private val GRADIENT_COLOURS = listOf(0xFF00E5FF.toInt(), 0xFFFF2DAA.toInt(),
    0xFFFFD60A.toInt(), 0xFFFF9F0A.toInt(), 0xFF30D158.toInt(), 0xFFBF5AF2.toInt(),
    0xFF0A84FF.toInt(), 0xFFFFFFFF.toInt())

@Composable
private fun ClockColourPicker(colors: List<Int?>, value: Int?, wallpaper: GalleryWallpaper,
        onChange: (Int?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    Swatches(colors, value, onChange)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        TextButton(onClick = { showPicker = true }) {
            Text(stringResource(R.string.custom_color))
        }
        TextButton(onClick = { showWallpaper = true }) {
            Text(stringResource(R.string.color_picker_wallpaper))
        }
    }
    if (showWallpaper) {
        WallpaperColorPickerDialog(wallpaper, onDismiss = { showWallpaper = false }) {
            onChange(it)
            showWallpaper = false
        }
    }
    if (showPicker) {
        ColorPickerDialog(value ?: 0xFFFFFFFF.toInt(), onDismiss = { showPicker = false }) {
            onChange(it)
            showPicker = false
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors()

/** A switch with a check or a cross in its thumb, as the settings use. */
@Composable
private fun ThumbSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked, onChange, thumbContent = {
        Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, null,
            modifier = Modifier.size(16.dp))
    })
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun TuningSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    defaultValue: Int,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("$value$unit", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            TextButton(enabled = value != defaultValue, onClick = { onChange(defaultValue) }) {
                Text(stringResource(R.string.gallery_clock_reset))
            }
        }
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@Composable
private fun TuningSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        ThumbSwitch(checked, onChange)
    }
}

@Composable
private fun ShortcutsSheet(shortcuts: Shortcuts) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    val state by produceState(Triple(emptyList<Shortcuts.Slot>(),
            emptyList<Shortcuts.Affordance>(), emptyList<Shortcuts.Selection>()), version) {
        value = withContext(Dispatchers.IO) {
            Triple(shortcuts.slots(), shortcuts.affordances(), shortcuts.selections())
        }
    }
    val (slots, affordances, selections) = state
    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gallery_shortcuts), color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        slots.forEachIndexed { index, slot ->
            Text(stringResource(if (index == 0) R.string.gallery_shortcut_left
                else R.string.gallery_shortcut_right),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            val chosen = selections.firstOrNull { it.slotId == slot.id }?.affordanceId
            val options = listOf(null to stringResource(R.string.gallery_shortcut_none)) +
                affordances.filter { it.isEnabled }.map { it.id to it.name }
            Chips(options.map { it.second }, options.indexOfFirst { it.first == chosen }
                .coerceAtLeast(0)) { i ->
                val id = options[i].first
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (id == null) shortcuts.unselectAll(slot.id)
                        else shortcuts.select(slot.id, id)
                    }
                    version++
                }
            }
        }
    }
}

@Composable
private fun HomeDialog(
    wallpaper: GalleryWallpaper,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onPick: (HomeStyle) -> Unit,
) {
    val context = LocalContext.current
    var dominant by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(wallpaper) {
        dominant = withContext(Dispatchers.Default) {
            GalleryRenderer.dominantColor(context, wallpaper)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.gallery_home_title), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.gallery_home_summary), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(HomeStyle.PAIR, HomeStyle.BLUR, HomeStyle.COLOUR, HomeStyle.GRADIENT)
                    .forEach { style ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(62.dp).clickable { onPick(style) }) {
                        val shape = RoundedCornerShape(12.dp)
                        val base = Color(dominant ?: 0xFF444444.toInt())
                        Box(Modifier.fillMaxWidth().aspectRatio(0.46f).clip(shape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
                            when (style) {
                                HomeStyle.PAIR -> WallpaperImage(wallpaper, Modifier.fillMaxSize())
                                HomeStyle.BLUR -> WallpaperImage(wallpaper,
                                    Modifier.fillMaxSize().blur(8.dp))
                                HomeStyle.COLOUR -> Box(Modifier.fillMaxSize().background(base))
                                HomeStyle.GRADIENT -> Box(Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(
                                        Color(GalleryRenderer.blend(base.toArgb(),
                                            0xFFFFFFFF.toInt(), 0.3f)),
                                        Color(GalleryRenderer.blend(base.toArgb(),
                                            0xFF000000.toInt(), 0.45f))))))
                            }
                        }
                        Text(stringResource(when (style) {
                            HomeStyle.PAIR -> R.string.gallery_home_pair
                            HomeStyle.BLUR -> R.string.gallery_home_blur
                            HomeStyle.COLOUR -> R.string.gallery_colour
                            HomeStyle.GRADIENT -> R.string.gallery_home_gradient
                        }), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.gallery_home_skip))
            }
        }
    }
}
