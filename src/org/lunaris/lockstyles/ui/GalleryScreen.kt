package org.lunaris.lockstyles.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.GalleryWallpaper.Kind
import org.lunaris.lockstyles.GalleryWeather
import org.lunaris.lockstyles.R

private val TILE_CLOCK = ClockStyle(preset = 5)

private data class Tile(val wallpaper: GalleryWallpaper?, val label: String? = null,
        val onClick: (() -> Unit)? = null, val icon: ImageVector? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    featured: List<Pair<String, Long>>,
    hasPhotoAccess: Boolean,
    allowPhotoPicking: Boolean,
    onRequestPhotoAccess: () -> Unit,
    onCancel: () -> Unit,
    onPick: (GalleryWallpaper) -> Unit,
    onPickPhoto: () -> Unit,
    onPickShuffle: () -> Unit,
    onPickKaleidoscope: () -> Unit,
) {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<GalleryWeather.Snapshot?>(null) }
    LaunchedEffect(Unit) {
        weather = withContext(Dispatchers.IO) { GalleryWeather.query(context) }
    }
    val featuredPhotos = featured.map { GalleryWallpaper(Kind.PHOTO, photos = listOf(it.first)) }
    val featuredShuffle = featured.takeIf { it.size >= 2 }?.let {
        GalleryWallpaper(Kind.SHUFFLE, photos = it.map { p -> p.first })
    }
    fun month(ms: Long) = DateFormat.format("MMMM yyyy", ms).toString()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_add_wallpaper)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.gallery_browse_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.gallery_browse_summary),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowPhotoPicking) {
                        item {
                            SourceButton(stringResource(R.string.gallery_photos),
                                featuredPhotos.firstOrNull(),
                                if (featured.isEmpty()) Icons.Rounded.PhotoLibrary else null,
                                onPickPhoto)
                        }
                        item {
                            SourceButton(stringResource(R.string.gallery_photo_shuffle), null,
                                Icons.Rounded.Shuffle,
                                onPickShuffle)
                        }
                    }
                    item {
                        SourceButton(stringResource(R.string.gallery_emoji),
                            GalleryWallpaper(Kind.COLOUR, 1, listOf(0xFFF3D36B.toInt())),
                            Icons.Rounded.EmojiEmotions) {
                            onPick(EMOJI_PRESETS[0])
                        }
                    }
                    item {
                        SourceButton(stringResource(R.string.gallery_colour),
                            GalleryWallpaper(Kind.COLOUR, 0, listOf(GalleryRenderer.COLOURS[1])),
                            Icons.Rounded.Palette) {
                            onPick(GalleryWallpaper(Kind.COLOUR, 0,
                                listOf(GalleryRenderer.COLOURS[1])))
                        }
                    }
                    if (allowPhotoPicking) {
                        item {
                            SourceButton(stringResource(R.string.gallery_kaleidoscope), null,
                                Icons.Rounded.FilterVintage, onPickKaleidoscope)
                        }
                    }
                    item {
                        SourceButton(stringResource(R.string.gallery_live), null, Icons.Rounded.AutoAwesome) {
                            runCatching {
                                context.startActivity(android.content.Intent(
                                    android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                            }
                        }
                    }
                }
            }

            item {
                val tiles = mutableListOf(Tile(GalleryWallpaper(Kind.PAPER),
                    stringResource(R.string.gallery_wallwidgy)))
                featured.forEachIndexed { i, (_, taken) ->
                    tiles += Tile(featuredPhotos[i], month(taken))
                }
                featuredShuffle?.let {
                    tiles.add(1, Tile(it, stringResource(R.string.gallery_featured),
                        icon = Icons.Rounded.Shuffle))
                }
                if (allowPhotoPicking && !hasPhotoAccess) {
                    tiles += Tile(null, stringResource(R.string.gallery_allow_photos),
                        onRequestPhotoAccess, Icons.Rounded.Lock)
                }
                Section(stringResource(R.string.gallery_featured), null, tiles,
                    weather, onPick)
            }

            if (allowPhotoPicking) item {
                val tiles = mutableListOf(Tile(null, stringResource(R.string.gallery_choose_photos),
                    onPickShuffle, Icons.Rounded.Add))
                featuredShuffle?.let { tiles += Tile(it, stringResource(R.string.gallery_featured)) }
                Section(stringResource(R.string.gallery_photo_shuffle),
                    stringResource(R.string.gallery_photo_shuffle_summary), tiles, weather,
                    onPick)
            }

            item {
                val tiles = (0 until GalleryRenderer.PAPER_COLLECTIONS).map {
                    Tile(GalleryWallpaper(Kind.PAPER, it))
                }
                Section(stringResource(R.string.gallery_wallwidgy),
                    stringResource(R.string.gallery_wallwidgy_credit), tiles, weather,
                    onPick)
            }

            if (GalleryRenderer.PAPERS.size > GalleryRenderer.PAPER_COLLECTIONS) item {
                val tiles = (GalleryRenderer.PAPER_COLLECTIONS until GalleryRenderer.PAPERS.size)
                    .map { Tile(GalleryWallpaper(Kind.PAPER, it)) }
                Section(stringResource(R.string.gallery_lunaris), null, tiles, weather,
                    onPick)
            }

            item {
                val tiles = listOf(
                    GalleryWallpaper(Kind.COLOUR, GalleryWallpaper.COLOUR_TONE,
                        listOf(GalleryRenderer.COLOURS[8])),
                    GalleryWallpaper(Kind.COLOUR, GalleryWallpaper.COLOUR_VIBRANT,
                        listOf(GalleryRenderer.COLOURS[0])),
                    GalleryWallpaper(Kind.COLOUR, GalleryWallpaper.COLOUR_DEEP,
                        listOf(GalleryRenderer.COLOURS[1])),
                    GalleryWallpaper(Kind.COLOUR, GalleryWallpaper.COLOUR_VIBRANT,
                        listOf(GalleryRenderer.COLOURS[2])),
                    GalleryWallpaper(Kind.COLOUR, GalleryWallpaper.COLOUR_TONE,
                        listOf(GalleryRenderer.COLOURS[4])),
                    GalleryWallpaper(Kind.COLOUR, GalleryWallpaper.COLOUR_DEEP,
                        listOf(GalleryRenderer.COLOURS[6])),
                ).map { Tile(it) }
                Section(stringResource(R.string.gallery_colour), null, tiles, weather,
                    onPick)
            }

            item {
                Section(stringResource(R.string.gallery_petals), null,
                    (0 until GalleryRenderer.PETAL_VARIANTS).map {
                        Tile(GalleryWallpaper(Kind.PETALS, it))
                    }, weather, onPick)
            }

            item {
                Section(stringResource(R.string.gallery_emoji),
                    stringResource(R.string.gallery_emoji_summary),
                    EMOJI_PRESETS.map { Tile(it) }, weather, onPick)
            }
            item {
                val tiles = mutableListOf<Tile>()
                if (allowPhotoPicking) {
                    tiles += Tile(null, stringResource(R.string.gallery_choose_photo),
                        onPickKaleidoscope, Icons.Rounded.Add)
                    featured.take(4).forEachIndexed { i, (uri, _) ->
                        tiles += Tile(GalleryWallpaper(Kind.KALEIDOSCOPE, i % 3,
                            photos = listOf(uri)))
                    }
                }
                if (tiles.isNotEmpty()) {
                    Section(stringResource(R.string.gallery_kaleidoscope),
                        stringResource(R.string.gallery_kaleidoscope_summary), tiles,
                        weather, onPick)
                }
            }
        }

    }
}

val EMOJI_PRESETS = listOf(
    GalleryWallpaper(Kind.EMOJI, GalleryWallpaper.EMOJI_SMALL, listOf(0xFFBFE3F2.toInt()),
        emojis = "🐝☁️"),
    GalleryWallpaper(Kind.EMOJI, GalleryWallpaper.EMOJI_MEDIUM, listOf(0xFFF6D7E0.toInt()),
        emojis = "🌸🌷"),
    GalleryWallpaper(Kind.EMOJI, GalleryWallpaper.EMOJI_RINGS, listOf(0xFF1D3B5A.toInt()),
        emojis = "🐠🐙🫧"),
    GalleryWallpaper(Kind.EMOJI, GalleryWallpaper.EMOJI_LARGE, listOf(0xFFF3D36B.toInt()),
        emojis = "😀🥳😎"),
    GalleryWallpaper(Kind.EMOJI, GalleryWallpaper.EMOJI_SPIRAL, listOf(0xFF2B2B2B.toInt()),
        emojis = "🍕🍩🍟"),
)

@Composable
private fun Section(
    title: String,
    summary: String?,
    tiles: List<Tile>,
    weather: GalleryWeather.Snapshot?,
    onPick: (GalleryWallpaper) -> Unit,
) {
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall)
            if (summary != null) {
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(tiles) { index, tile ->
                val label = tile.label ?: stringResource(R.string.gallery_wallpaper_option, title, index + 1)
                Card(
                    onClick = { tile.onClick?.invoke() ?: tile.wallpaper?.let(onPick) },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.width(156.dp).semantics(mergeDescendants = true) {
                        contentDescription = label
                    },
                ) {
                    if (tile.wallpaper != null) {
                        LockScreenCard(
                            wallpaper = tile.wallpaper,
                            clock = TILE_CLOCK,
                            weather = weather,
                            corner = 28.dp,
                            modifier = Modifier.padding(6.dp).fillMaxWidth().aspectRatio(0.5f),
                        )
                    } else {
                        Box(
                            Modifier.padding(6.dp).fillMaxWidth().aspectRatio(0.5f)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(tile.icon ?: Icons.Rounded.Add, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(40.dp))
                        }
                    }
                    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tile.wallpaper != null && tile.icon != null) {
                            Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                        Text(tile.label ?: title, color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
