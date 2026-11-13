/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.extrainfowidgets.ExtraInfoCatalog
import org.lunaris.extrainfowidgets.ExtraInfoFreeformLayout
import org.lunaris.extrainfowidgets.ExtraInfoLayout
import org.lunaris.extrainfowidgets.InfoData
import org.lunaris.extrainfowidgets.InfoWidget
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.GalleryApplier
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.LockScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import org.lunaris.lockstyles.ui.ExpressiveIconButton
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.ui.LockScreenCard
import org.lunaris.lockstyles.ui.holdPreview
import org.lunaris.lockstyles.ui.rememberClockGlass

@Composable
internal fun IndependentInfoPreview(
    layout: ExtraInfoFreeformLayout,
    wallpaper: GalleryWallpaper,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelected: ((String) -> Unit)? = null,
    onEdit: ((ExtraInfoFreeformLayout) -> Unit)? = null,
    onPinchStart: (() -> Unit)? = null,
    onPinch: ((Float) -> Unit)? = null,
    onPinchEnd: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val screenWidth =
        remember(context, LocalConfiguration.current) {
            GalleryApplier.screenSize(context).first.coerceAtLeast(1)
        }
    val appearance = extraInfoAppearance()
    val glass = rememberClockGlass(wallpaper, appearance.liquidGlass, listOf(appearance.glassStyle.blurRadius))
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bitmap by
            produceState<Bitmap?>(null, wallpaper, width, height, appearance.axBlur) {
                value =
                    if (appearance.axBlur && width > 0 && height > 0) {
                        withContext(Dispatchers.Default) {
                            runCatching {
                                    GalleryRenderer.toBitmap(context, wallpaper, width, height)
                                }
                                .getOrNull()
                        }
                    } else null
            }
        val hold = holdPreview(appearance.liquidGlass || appearance.axBlur,
            if (appearance.liquidGlass) glass != null else bitmap != null, wallpaper)
        AndroidView(
            factory = { ExtraInfoFreeformPreviewHost(it) },
            modifier = Modifier.fillMaxSize().alpha(if (hold) 0f else 1f),
            update = {
                it.onPinchStart = onPinchStart
                it.onPinch = onPinch
                it.onPinchEnd = onPinchEnd
                it.wallpaper = bitmap
                it.widgets.selection = layout
                it.widgets.data = InfoData.SAMPLE
                it.widgets.appearance = appearance
                it.setGlassMaterial(glass)
                it.setGlassEnabled(appearance.liquidGlass)
                it.widgets.previewScale = width / screenWidth.toFloat()
                it.widgets.editable = onEdit != null
                it.widgets.selectedId = selected
                it.widgets.onSelected = onSelected
                it.widgets.onEdit = onEdit
            },
        )
    }
}

@Composable
internal fun IndependentInfoPanel(preview: LockScreen? = null) {
    val context = LocalContext.current
    val catalog = remember(context) { ExtraInfoCatalog.load(context) }
    val rows by Keys.EXTRA_INFO_LAYOUT.state()
    var raw by Keys.EXTRA_INFO_FREEFORM.state()
    val layout =
        remember(raw, rows, catalog) {
            ExtraInfoFreeformLayout.parse(raw, catalog, ExtraInfoLayout.parse(rows, catalog))
        }
    val current =
        preview
            ?: remember(context) {
                val store = GalleryStore.get(context)
                store.lockScreens.firstOrNull { it.id == store.currentId }
            }
    val wallpaper = current?.wallpaper ?: GalleryWallpaper(GalleryWallpaper.Kind.COLOUR)
    val clock = current?.clock ?: ClockStyle()
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.extra_info_independent_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            PositionPreview(layout, wallpaper, clock)
        }
        Text(
            stringResource(
                R.string.extra_info_widget_count,
                layout.widgets.size,
                ExtraInfoFreeformLayout.MAX_WIDGETS,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.extra_info_edit_positions))
        }
    }
    if (editing) {
        IndependentInfoEditor(
            layout,
            catalog,
            wallpaper,
            clock,
            onDismiss = { editing = false },
            onSave = {
                raw = it.encode()
                editing = false
            },
        )
    }
}

@Composable
private fun PositionPreview(
    layout: ExtraInfoFreeformLayout,
    wallpaper: GalleryWallpaper,
    clock: ClockStyle,
    selected: String? = null,
    onSelected: ((String) -> Unit)? = null,
    onEdit: ((ExtraInfoFreeformLayout) -> Unit)? = null,
    onPinchStart: (() -> Unit)? = null,
    onPinch: ((Float) -> Unit)? = null,
    onPinchEnd: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val screen =
        remember(context, LocalConfiguration.current) { GalleryApplier.screenSize(context) }
    val aspect = screen.first.toFloat() / screen.second.coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * aspect)
        Box(Modifier.size(width, width / aspect).clip(RoundedCornerShape(20.dp))) {
            LockScreenCard(wallpaper, clock, Modifier.fillMaxSize(), showExtraInfo = false)
            IndependentInfoPreview(
                layout,
                wallpaper,
                Modifier.fillMaxSize(),
                selected,
                onSelected,
                onEdit,
                onPinchStart,
                onPinch,
                onPinchEnd,
            )
        }
    }
}

@Composable
private fun IndependentInfoEditor(
    initial: ExtraInfoFreeformLayout,
    catalog: List<InfoWidget>,
    wallpaper: GalleryWallpaper,
    clock: ClockStyle,
    onDismiss: () -> Unit,
    onSave: (ExtraInfoFreeformLayout) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial.encode()) }
    val layout = remember(draft, catalog) { ExtraInfoFreeformLayout.parse(draft, catalog) }
    var selected by rememberSaveable { mutableStateOf(initial.widgets.lastOrNull()?.id) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var pinchBase by remember { mutableStateOf<Float?>(null) }
    var pinchBefore by remember { mutableStateOf<String?>(null) }
    val latestLayout by rememberUpdatedState(layout)
    var undo by remember { mutableStateOf(listOf<String>()) }
    var redo by remember { mutableStateOf(listOf<String>()) }
    var lastPush by remember { mutableStateOf(0L) }
    val edit: (ExtraInfoFreeformLayout) -> Unit = { changed ->
        val next = changed.encode()
        if (next != draft) {
            val now = android.os.SystemClock.uptimeMillis()
            if (undo.isEmpty() || now - lastPush > 500L) undo = (undo + draft).takeLast(50)
            lastPush = now
            redo = emptyList()
            draft = next
        }
    }
    Dialog(
        onDismissRequest = { if (fullScreen) fullScreen = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars(hidden = false)
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .statusBarsPadding().navigationBarsPadding().padding(if (fullScreen) 0.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!fullScreen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Text(
                        stringResource(R.string.extra_info_editor_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    androidx.compose.material3.FilledTonalIconButton(onClick = { fullScreen = true }) {
                        Icon(Icons.Rounded.Fullscreen, stringResource(R.string.clock_maker_editor_fullscreen))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(layout) }) {
                        Text(stringResource(R.string.extra_info_save))
                    }
                }
                val pinchEnabled = fullScreen && selected != null
                val pinchStart: (() -> Unit)? = if (!pinchEnabled) null else {
                    {
                        pinchBase = latestLayout.widgets.firstOrNull { it.id == selected }?.scale
                        pinchBefore = draft
                    }
                }
                val pinch: ((Float) -> Unit)? = if (!pinchEnabled) null else { { factor ->
                    val base = pinchBase
                    val id = selected
                    if (base != null && id != null) {
                        draft = latestLayout.resize(id, (base * factor).coerceIn(
                            ExtraInfoFreeformLayout.MIN_SCALE, ExtraInfoFreeformLayout.MAX_SCALE)).encode()
                    }
                } }
                val pinchEnd: (() -> Unit)? = if (!pinchEnabled) null else { {
                    pinchBefore?.takeIf { it != draft }?.let {
                        undo = (undo + it).takeLast(50)
                        redo = emptyList()
                        lastPush = 0L
                    }
                    pinchBase = null
                    pinchBefore = null
                } }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val controlsWidth = maxWidth * 0.5f
                    val controlsMaxHeight = maxHeight * 0.5f
                    val controls: @Composable () -> Unit = {
                        PositionControls(
                            layout,
                            catalog,
                            selected,
                            { selected = it },
                            edit,
                            onAdd = { choosing = true },
                        )
                    }
                    if (maxWidth > maxHeight) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                PositionPreview(
                                    layout,
                                    wallpaper,
                                    clock,
                                    selected,
                                    { selected = it },
                                    edit,
                                    pinchStart,
                                    pinch,
                                    pinchEnd,
                                )
                            }
                            if (!fullScreen) Column(
                                Modifier.width(controlsWidth).verticalScroll(rememberScrollState())
                            ) {
                                controls()
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                PositionPreview(
                                    layout,
                                    wallpaper,
                                    clock,
                                    selected,
                                    { selected = it },
                                    edit,
                                    pinchStart,
                                    pinch,
                                    pinchEnd,
                                )
                            }
                            if (!fullScreen) Column(
                                Modifier.heightIn(max = controlsMaxHeight)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                controls()
                            }
                        }
                    }
                }
            }
            if (fullScreen) {
                Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveIconButton(Icons.AutoMirrored.Rounded.Undo,
                        stringResource(R.string.clock_maker_undo), enabled = undo.isNotEmpty(), onClick = {
                            redo = redo + draft
                            draft = undo.last()
                            undo = undo.dropLast(1)
                            lastPush = 0L
                        })
                    ExpressiveIconButton(Icons.AutoMirrored.Rounded.Redo,
                        stringResource(R.string.clock_maker_redo), enabled = redo.isNotEmpty(), onClick = {
                            undo = undo + draft
                            draft = redo.last()
                            redo = redo.dropLast(1)
                            lastPush = 0L
                        })
                    ExpressiveIconButton(Icons.Rounded.FullscreenExit,
                        stringResource(R.string.clock_maker_editor_split),
                        onClick = { fullScreen = false })
                }
            }
            }
        }
    }
    if (choosing) {
        IndependentWidgetPicker(catalog, layout, onDismiss = { choosing = false }) { widget ->
            layout.add(widget)?.let {
                edit(it)
                selected = widget.id
                choosing = false
            }
        }
    }
}

@Composable
private fun DialogSystemBars(hidden: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view, hidden) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        window?.let {
            it.statusBarColor = android.graphics.Color.TRANSPARENT
            it.navigationBarColor = android.graphics.Color.TRANSPARENT
            it.attributes = it.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        if (hidden) {
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (hidden) controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun PositionControls(
    layout: ExtraInfoFreeformLayout,
    catalog: List<InfoWidget>,
    selected: String?,
    onSelected: (String?) -> Unit,
    onEdit: (ExtraInfoFreeformLayout) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.extra_info_widget_count,
                    layout.widgets.size,
                    ExtraInfoFreeformLayout.MAX_WIDGETS,
                ),
                Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onAdd,
                enabled = layout.widgets.size < ExtraInfoFreeformLayout.MAX_WIDGETS,
            ) {
                Text(
                    stringResource(
                        if (layout.widgets.size < ExtraInfoFreeformLayout.MAX_WIDGETS)
                            R.string.extra_info_add_widget
                        else R.string.extra_info_limit
                    )
                )
            }
        }
        Text(
            stringResource(
                if (layout.widgets.isEmpty()) R.string.extra_info_select_hint
                else R.string.extra_info_editor_hint
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            layout.widgets.forEach { position ->
                FilterChip(
                    selected = selected == position.id,
                    onClick = { onSelected(position.id) },
                    label = { Text(catalog.first { it.id == position.id }.name) },
                )
            }
        }
        layout.widgets
            .firstOrNull { it.id == selected }
            ?.let { position ->
                WidgetSlider(
                    stringResource(R.string.extra_info_size),
                    position.scale,
                    valueRange =
                        ExtraInfoFreeformLayout.MIN_SCALE..ExtraInfoFreeformLayout.MAX_SCALE,
                    steps = 29,
                ) {
                    onEdit(layout.resize(position.id, it))
                }
                WidgetSlider(stringResource(R.string.extra_info_horizontal), position.x) {
                    onEdit(layout.move(position.id, it, position.y))
                }
                WidgetSlider(stringResource(R.string.extra_info_vertical), position.y) {
                    onEdit(layout.move(position.id, position.x, it))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { onEdit(layout.bringToFront(position.id)) },
                        enabled = layout.widgets.lastOrNull()?.id != position.id,
                    ) {
                        Text(stringResource(R.string.extra_info_front))
                    }
                    TextButton(
                        onClick = {
                            val next = layout.remove(position.id)
                            onEdit(next)
                            onSelected(next.widgets.lastOrNull()?.id)
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.extra_info_remove,
                                catalog.first { it.id == position.id }.name,
                            )
                        )
                    }
                }
            }
    }
}

@Composable
private fun WidgetSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(88.dp), style = MaterialTheme.typography.labelMedium)
        Slider(
            value,
            onChange,
            Modifier.weight(1f).semantics { contentDescription = label },
            valueRange = valueRange,
            steps = steps,
        )
        Text(
            "${(value * 100).roundToInt()}%",
            Modifier.width(40.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun IndependentWidgetPicker(
    catalog: List<InfoWidget>,
    layout: ExtraInfoFreeformLayout,
    onDismiss: () -> Unit,
    onAdd: (InfoWidget) -> Unit,
) {
    val appearance = extraInfoAppearance()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.extra_info_choose),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                Text(
                    stringResource(R.string.extra_info_picker_independent_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyVerticalGrid(
                    GridCells.Fixed(2),
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(catalog, key = { it.id }) { widget ->
                        Card(onClick = { onAdd(widget) }, enabled = layout.add(widget) != null) {
                            Column(Modifier.padding(8.dp)) {
                                DesignPreview(
                                    widget,
                                    appearance,
                                    Modifier.fillMaxWidth().height(80.dp),
                                )
                                Text(widget.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
