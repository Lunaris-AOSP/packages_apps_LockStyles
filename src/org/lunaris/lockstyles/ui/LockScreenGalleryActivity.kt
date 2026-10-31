@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.lunaris.lockstyles.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryApplier
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.GalleryWallpaper.Kind
import org.lunaris.lockstyles.LockScreen
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.Shortcuts
import org.lunaris.lockstyles.StockClock
import org.lunaris.lockstyles.settings.ui.LockStylesTheme

/**
 * iOS-style lock screens: a switcher of saved lock screens, a gallery to add new ones and a
 * customise screen. Opened by long-pressing the lock screen.
 */
class LockScreenGalleryActivity : ComponentActivity() {

    private val shortcuts by lazy { Shortcuts(this) }
    private val keyguardManager by lazy {
        checkNotNull(getSystemService(KeyguardManager::class.java))
    }
    private var launchGeneration = 0
    private var launchRequest by mutableStateOf(LaunchRequest(false, false, false, 0))
    private var deviceLocked by mutableStateOf(false)
    private var initializing by mutableStateOf(true)

    private data class LaunchRequest(
        val fromLockScreen: Boolean,
        val startInGallery: Boolean,
        val startInClock: Boolean,
        val generation: Int,
    )

    private sealed interface Screen {
        /** [zoomIn]: arrive as a full lock screen shrinking into its card. */
        data class Switcher(val zoomIn: Boolean) : Screen
        data object Gallery : Screen
        data class Customise(
            val lockScreen: LockScreen,
            val isNew: Boolean,
            val openClock: Boolean = false,
        ) : Screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The switcher animates itself out of the lock screen, so no window animation.
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in,
            android.R.anim.fade_out)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launchRequest = requestFor(intent, 0)
        updateWindowForEntry()
        val store = GalleryStore.get(this)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { adoptCurrentWallpaper(store) }
            } finally {
                initializing = false
            }
        }
        setContent {
            if (initializing) {
                LockStylesTheme { GalleryProgress(R.string.gallery_loading) }
                return@setContent
            }
            val request = launchRequest
            // A new singleTask intent must discard the previous editor immediately. In
            // particular, do not animate an old photo-backed editor into a locked entry.
            key(request.generation) {
                LockStylesTheme {
                    var screen by remember {
                        val current = store.lockScreens.firstOrNull { it.id == store.currentId }
                        mutableStateOf<Screen>(
                            when {
                                request.startInClock && current != null ->
                                    Screen.Customise(current, isNew = false, openClock = true)
                                request.startInGallery -> Screen.Gallery
                                else -> Screen.Switcher(zoomIn = true)
                            })
                    }
                var version by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()
                val allowPhotoPicking = !deviceLocked
                var importing by remember { mutableStateOf(false) }

                DisposableEffect(screen) {
                    val draft = (screen as? Screen.Customise)?.takeIf { it.isNew }
                    onDispose {
                        if (draft != null) {
                            lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                                store.discardImportedPhotos(draft.lockScreen.wallpaper.photos)
                            }
                        }
                    }
                }

                fun openWallpaper(wallpaper: GalleryWallpaper) {
                    if (importing || applying) return
                    val hasExternalPhoto = wallpaper.photos.any { it.startsWith("content:") }
                    if (!hasExternalPhoto) {
                        screen = Screen.Customise(newLockScreen(store, wallpaper), isNew = true)
                        return
                    }
                    if (!allowPhotoPicking || !canUsePrivatePhotos()) return
                    importing = true
                    scope.launch {
                        val imported = mutableListOf<String>()
                        var opened = false
                        try {
                            val photos = withContext(Dispatchers.IO) {
                                wallpaper.photos.map { path ->
                                    coroutineContext.ensureActive()
                                    if (path.startsWith("content:")) {
                                        val copy = store.importPhoto(Uri.parse(path))
                                            ?: return@withContext null
                                        imported += copy
                                        copy
                                    } else path
                                }
                            }
                            if (!canUsePrivatePhotos()) return@launch
                            if (photos == null) {
                                Toast.makeText(this@LockScreenGalleryActivity,
                                    R.string.gallery_import_failed, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            screen = Screen.Customise(newLockScreen(store,
                                wallpaper.copy(photos = photos)), isNew = true)
                            opened = true
                        } finally {
                            if (!opened) {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    store.discardImportedPhotos(imported)
                                }
                            }
                            importing = false
                        }
                    }
                }

                val pickPhoto = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (!allowPhotoPicking || !canUsePrivatePhotos() || uri == null) {
                        return@rememberLauncherForActivityResult
                    }
                    openWallpaper(GalleryWallpaper(Kind.PHOTO, photos = listOf(uri.toString())))
                }
                val pickKaleidoscope = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (!allowPhotoPicking || !canUsePrivatePhotos() || uri == null) {
                        return@rememberLauncherForActivityResult
                    }
                    openWallpaper(GalleryWallpaper(Kind.KALEIDOSCOPE,
                        photos = listOf(uri.toString())))
                }
                val pickShuffle = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(MAX_SHUFFLE)) { uris ->
                    if (!allowPhotoPicking || !canUsePrivatePhotos() || uris.isEmpty())
                        return@rememberLauncherForActivityResult
                    if (uris.size > MAX_SHUFFLE) {
                        Toast.makeText(this@LockScreenGalleryActivity,
                            getString(R.string.gallery_shuffle_limit, MAX_SHUFFLE),
                            Toast.LENGTH_LONG).show()
                    } else {
                        openWallpaper(GalleryWallpaper(Kind.SHUFFLE,
                            photos = uris.distinct().map { it.toString() }))
                    }
                }
                val requestPhotos = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()) { version++ }

                BackHandler {
                    if (importing || applying) return@BackHandler
                    when (screen) {
                        is Screen.Switcher -> finish()
                        Screen.Gallery ->
                            if (request.startInGallery || request.fromLockScreen) finish()
                            else screen = Screen.Switcher(false)
                        is Screen.Customise -> screen = Screen.Switcher(zoomIn = true)
                    }
                }

                val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val from = initialState
                        val to = targetState
                        when {
                            // The switcher zooms its card to and from full screen itself.
                            from is Screen.Switcher && to is Screen.Customise ||
                                from is Screen.Customise && to is Screen.Switcher ->
                                EnterTransition.None togetherWith ExitTransition.None
                            to == Screen.Gallery ->
                                (slideInVertically(spatial) { it / 3 } +
                                    fadeIn(effects)) togetherWith fadeOut(effects)
                            from == Screen.Gallery && to is Screen.Switcher ->
                                fadeIn(effects) togetherWith
                                    (slideOutVertically(spatial) { it / 3 } +
                                        fadeOut(effects))
                            else -> (fadeIn(effects) + scaleIn(scale,
                                initialScale = 0.94f)) togetherWith fadeOut(effects)
                        }
                    },
                    label = "screen",
                ) { current ->
                    when (current) {
                        is Screen.Switcher -> SwitcherScreen(
                            store = store,
                            version = version,
                            zoomIn = current.zoomIn,
                            onApply = { lockScreen ->
                                apply(lockScreen, closeWhenApplied = request.fromLockScreen,
                                    onFinished = { version++ })
                            },
                            onCustomise = { screen = Screen.Customise(it, isNew = false) },
                            onAdd = { screen = Screen.Gallery },
                            onDelete = {
                                if (!applying) {
                                    store.delete(it.id)
                                    version++
                                    scope.launch(Dispatchers.IO) { store.pruneImportedPhotos() }
                                }
                            },
                            onClock = { lockScreen ->
                                screen = Screen.Customise(lockScreen, isNew = false, openClock = true)
                            },
                        )
                        Screen.Gallery -> {
                            val featured by produceState<List<Pair<String, Long>>>(emptyList(),
                                version, allowPhotoPicking) {
                                value = withContext(Dispatchers.IO) {
                                    featuredPhotos(allowPhotoPicking)
                                }
                            }
                            GalleryScreen(
                                featured = if (allowPhotoPicking) featured else emptyList(),
                                hasPhotoAccess = allowPhotoPicking && hasPhotoAccess(),
                                allowPhotoPicking = allowPhotoPicking,
                                onRequestPhotoAccess = {
                                    if (allowPhotoPicking && canUsePrivatePhotos()) {
                                        requestPhotos.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                    }
                                },
                                onCancel = {
                                    if (request.startInGallery || request.fromLockScreen) finish()
                                    else screen = Screen.Switcher(false)
                                },
                                onPick = ::openWallpaper,
                                onPickPhoto = {
                                    if (allowPhotoPicking && canUsePrivatePhotos()) {
                                        pickPhoto.launch(PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                                onPickShuffle = {
                                    if (allowPhotoPicking && canUsePrivatePhotos()) {
                                        pickShuffle.launch(PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                                onPickKaleidoscope = {
                                    if (allowPhotoPicking && canUsePrivatePhotos()) {
                                        pickKaleidoscope.launch(PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                            )
                        }
                        is Screen.Customise -> CustomiseScreen(
                            initial = current.lockScreen,
                            isNew = current.isNew,
                            isCurrent = !current.isNew && current.lockScreen.id == store.currentId,
                            openClock = current.openClock,
                            allowPhotoPicking = allowPhotoPicking,
                            shortcuts = shortcuts,
                            onCancel = {
                                screen = if (current.isNew) Screen.Gallery
                                else Screen.Switcher(zoomIn = true)
                            },
                            onDone = { lockScreen, tuning ->
                                val updateWallpaper = current.isNew ||
                                    lockScreen.id != store.currentId ||
                                    lockScreen.wallpaper != current.lockScreen.wallpaper ||
                                    lockScreen.home != current.lockScreen.home
                                apply(lockScreen, tuning, updateWallpaper,
                                    closeWhenApplied = request.fromLockScreen) {
                                    version++
                                    screen = Screen.Switcher(zoomIn = true)
                                }
                            },
                        )
                    }
                }
                if (importing || applying) {
                    GalleryProgress(if (importing) R.string.gallery_loading
                        else R.string.gallery_saving)
                }
            }
        }
    }
    }

    override fun onStart() {
        super.onStart()
        // Lifecycle/focus updates are sufficient here and avoid requiring the privileged
        // SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE permission.
        refreshDeviceLocked()
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceLocked()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        refreshDeviceLocked()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = requestFor(intent, ++launchGeneration)
        updateWindowForEntry()
    }

    private fun requestFor(intent: Intent, generation: Int) = LaunchRequest(
        fromLockScreen = intent.action == ACTION_LOCK_SCREENS,
        startInGallery = intent.getBooleanExtra(EXTRA_GALLERY, false),
        startInClock = intent.action == ACTION_LOCK_SCREEN_CLOCK,
        generation = generation,
    )

    private fun updateWindowForEntry() {
        // Do not set this in the manifest: launcher/settings entries must remain behind keyguard.
        setShowWhenLocked(launchRequest.fromLockScreen)
        refreshDeviceLocked()
    }

    private fun refreshDeviceLocked() {
        deviceLocked = keyguardManager.isDeviceLocked
    }

    private fun canUsePrivatePhotos() = !keyguardManager.isDeviceLocked

    private fun newLockScreen(store: GalleryStore, wallpaper: GalleryWallpaper) =
        LockScreen(store.newId(), wallpaper, clock = ClockStyle(preset = 5))

    private fun hasPhotoAccess() =
        checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED

    /** Recent portrait camera photos, as content URIs, newest first. */
    private fun featuredPhotos(allowPhotoPicking: Boolean): List<Pair<String, Long>> {
        // READ_MEDIA_IMAGES may be pre-granted to this privileged app; that is not enough to
        // make a locked-screen query safe. Never touch MediaStore until after authentication.
        if (!allowPhotoPicking || !canUsePrivatePhotos() || !hasPhotoAccess()) return emptyList()
        val photos = mutableListOf<Pair<String, Long>>()
        runCatching {
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.ORIENTATION),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("DCIM/%"),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
                while (c.moveToNext() && photos.size < FEATURED) {
                    val rotated = c.getInt(4) % 180 != 0
                    val w = if (rotated) c.getInt(3) else c.getInt(2)
                    val h = if (rotated) c.getInt(2) else c.getInt(3)
                    if (h <= w) continue
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                    photos += uri.toString() to c.getLong(1)
                }
            }
        }
        return if (canUsePrivatePhotos()) photos else emptyList()
    }

    /**
     * Opened from the lock screen, applying goes back to it, as on iOS. Opened from the
     * launcher, closing would look like a crash, so the switcher stays and says so instead.
     */
    private var applying by mutableStateOf(false)

    private fun apply(lockScreen: LockScreen, tuning: CustomClocks.Tuning? = null,
            updateWallpaper: Boolean = true, closeWhenApplied: Boolean = launchRequest.fromLockScreen,
            onFinished: () -> Unit = {},
            onApplied: () -> Unit = {}) {
        if (applying) return
        applying = true
        val generation = launchRequest.generation
        val store = GalleryStore.get(this)
        store.retainImportedPhotos(lockScreen.wallpaper.photos)
        lifecycleScope.launch {
            try {
                if (updateWallpaper && lockScreen.wallpaper.depth) {
                    Toast.makeText(this@LockScreenGalleryActivity,
                        R.string.depthwall_extracting_toast, Toast.LENGTH_LONG).show()
                }
                val result = withContext(NonCancellable) {
                    applyMutex.withLock {
                        val applied = if (updateWallpaper) {
                            withContext(Dispatchers.IO) {
                                GalleryApplier.apply(this@LockScreenGalleryActivity, lockScreen)
                            }
                        } else GalleryApplier.Result.APPLIED
                        if (applied != GalleryApplier.Result.FAILED) {
                            applyClock(lockScreen.clock, tuning ?: lockScreen.clock.tuning)
                            withContext(Dispatchers.IO) {
                                store.save(lockScreen, makeCurrent = true)
                                store.pruneImportedPhotos()
                            }
                        }
                        applied
                    }
                }
                coroutineContext.ensureActive()
                if (result == GalleryApplier.Result.FAILED) {
                    Toast.makeText(this@LockScreenGalleryActivity, R.string.gallery_apply_failed,
                        Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (generation != launchRequest.generation) return@launch
                if (result == GalleryApplier.Result.DEPTH_FAILED) {
                    Toast.makeText(this@LockScreenGalleryActivity, R.string.depthwall_apply_failed,
                        Toast.LENGTH_LONG).show()
                }
                if (closeWhenApplied) {
                    finish()
                } else {
                    if (result == GalleryApplier.Result.APPLIED) {
                        Toast.makeText(this@LockScreenGalleryActivity, R.string.gallery_applied,
                            Toast.LENGTH_SHORT).show()
                    }
                    onApplied()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LockScreenGallery", "Could not save the lock screen", e)
                Toast.makeText(this@LockScreenGalleryActivity, R.string.gallery_apply_failed,
                    Toast.LENGTH_LONG).show()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    store.discardImportedPhotos(lockScreen.wallpaper.photos)
                }
                applying = false
                if (generation == launchRequest.generation) onFinished()
            }
        }
    }

    private suspend fun applyClock(style: ClockStyle, tuning: CustomClocks.Tuning?) {
        if (style.face == 0) applyStockClock(style)
        withContext(Dispatchers.IO) {
            runCatching { CustomClocks.apply(this@LockScreenGalleryActivity, style.face,
                style.color, tuning, style.design) }
        }
    }

    private suspend fun applyStockClock(style: ClockStyle) {
        val axes = style.preset?.let {
            val (weight, width) = CLOCK_PRESETS[it.coerceIn(0, CLOCK_PRESETS.size - 1)]
            mapOf("wght" to weight, "wdth" to width,
                "ROND" to if (style.rounded) 100f else 0f, "slnt" to 0f)
        }
        withContext(Dispatchers.IO) {
            runCatching {
                StockClock.apply(this@LockScreenGalleryActivity, style.small, style.color, axes)
            }
        }
    }

    /**
     * The first time, the wallpaper already on the lock screen becomes the first saved lock
     * screen, so the switcher never starts empty.
     */
    private fun adoptCurrentWallpaper(store: GalleryStore) {
        if (store.lockScreens.isNotEmpty()) return
        val wm = WallpaperManager.getInstance(this)
        val drawable = runCatching { wm.getDrawable(WallpaperManager.FLAG_LOCK) }.getOrNull()
            ?: runCatching { wm.builtInDrawable }.getOrNull() ?: return
        val (w, h) = GalleryApplier.screenSize(this)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bitmap))
            val path = store.importBitmap(bitmap) ?: return
            try {
                store.saveInitial(LockScreen(store.newId(),
                    GalleryWallpaper(Kind.PHOTO, photos = listOf(path))))
            } finally {
                store.discardImportedPhotos(listOf(path))
            }
        } catch (e: Exception) {
            Log.w("LockScreenGallery", "Could not import the current wallpaper", e)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val EXTRA_GALLERY = "gallery"
        private const val ACTION_LOCK_SCREENS = "com.android.wallpaper.action.LOCK_SCREENS"
        private const val ACTION_LOCK_SCREEN_CLOCK =
            "org.lunaris.lockstyles.action.LOCK_SCREEN_CLOCK"
        private const val MAX_SHUFFLE = 30
        private const val FEATURED = 6
        private val applyMutex = Mutex()
    }
}

@Composable
private fun GalleryProgress(message: Int) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ExpressiveLoadingIndicator()
                Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
