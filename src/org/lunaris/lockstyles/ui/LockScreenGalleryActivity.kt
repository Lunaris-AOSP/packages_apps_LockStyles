package org.lunaris.lockstyles.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
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
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        lifecycleScope.launch(Dispatchers.IO) { adoptCurrentWallpaper(store) }
        setContent {
            val request = launchRequest
            // A new singleTask intent must discard the previous editor immediately. In
            // particular, do not animate an old photo-backed editor into a locked entry.
            key(request.generation) {
                // Over wallpapers, so always the dark side of the expressive theme.
                LockStylesTheme(dark = true) {
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

                val pickPhoto = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (!allowPhotoPicking || !canUsePrivatePhotos() || uri == null) {
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { store.importPhoto(uri) }
                            ?: return@launch
                        screen = Screen.Customise(newLockScreen(store, GalleryWallpaper(
                            Kind.PHOTO, photos = listOf(path))), isNew = true)
                    }
                }
                val pickKaleidoscope = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (!allowPhotoPicking || !canUsePrivatePhotos() || uri == null) {
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { store.importPhoto(uri) }
                            ?: return@launch
                        screen = Screen.Customise(newLockScreen(store, GalleryWallpaper(
                            Kind.KALEIDOSCOPE, photos = listOf(path))), isNew = true)
                    }
                }
                val pickShuffle = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(MAX_SHUFFLE)) { uris ->
                    if (!allowPhotoPicking || !canUsePrivatePhotos() || uris.isEmpty())
                        return@rememberLauncherForActivityResult
                    scope.launch {
                        val paths = withContext(Dispatchers.IO) {
                            uris.mapNotNull { store.importPhoto(it) }
                        }
                        if (paths.isNotEmpty()) {
                            screen = Screen.Customise(newLockScreen(store, GalleryWallpaper(
                                Kind.SHUFFLE, photos = paths)), isNew = true)
                        }
                    }
                }
                val requestPhotos = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()) { version++ }

                BackHandler {
                    when (screen) {
                        is Screen.Switcher -> finish()
                        Screen.Gallery ->
                            if (request.startInGallery || request.fromLockScreen) finish()
                            else screen = Screen.Switcher(false)
                        is Screen.Customise -> screen = Screen.Switcher(zoomIn = true)
                    }
                }

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
                                (slideInVertically(tween(420)) { it / 3 } +
                                    fadeIn(tween(300))) togetherWith fadeOut(tween(250))
                            from == Screen.Gallery && to is Screen.Switcher ->
                                fadeIn(tween(250)) togetherWith
                                    (slideOutVertically(tween(380)) { it / 3 } +
                                        fadeOut(tween(250)))
                            else -> (fadeIn(tween(300)) + scaleIn(tween(350),
                                initialScale = 0.94f)) togetherWith fadeOut(tween(200))
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
                                apply(lockScreen, closeWhenApplied = request.fromLockScreen)
                            },
                            onCustomise = { screen = Screen.Customise(it, isNew = false) },
                            onAdd = { screen = Screen.Gallery },
                            onDelete = {
                                store.delete(it.id)
                                version++
                            },
                            onClock = { lockScreen ->
                                screen = Screen.Customise(lockScreen, isNew = false, openClock = true)
                            },
                        )
                        Screen.Gallery -> GalleryScreen(
                            featured = remember(version, allowPhotoPicking) {
                                featuredPhotos(allowPhotoPicking)
                            },
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
                            onPick = { wallpaper ->
                                val hasExternalPhoto = wallpaper.photos.any {
                                    it.startsWith("content:")
                                }
                                if (!hasExternalPhoto ||
                                    (allowPhotoPicking && canUsePrivatePhotos())) {
                                    scope.launch {
                                        // Featured photos come from MediaStore; keep our own copy.
                                        val imported = withContext(Dispatchers.IO) {
                                            wallpaper.copy(photos = wallpaper.photos.map {
                                                if (it.startsWith("content:"))
                                                    store.importPhoto(android.net.Uri.parse(it)) ?: it
                                                else it
                                            })
                                        }
                                        screen = Screen.Customise(newLockScreen(store, imported),
                                            isNew = true)
                                    }
                                }
                            },
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
                                store.save(lockScreen)
                                version++
                                apply(lockScreen, tuning, updateWallpaper,
                                    closeWhenApplied = request.fromLockScreen) {
                                    screen = Screen.Switcher(zoomIn = true)
                                }
                            },
                        )
                    }
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
    private var applying = false

    private fun apply(lockScreen: LockScreen, tuning: CustomClocks.Tuning? = null,
            updateWallpaper: Boolean = true, closeWhenApplied: Boolean = launchRequest.fromLockScreen,
            onApplied: () -> Unit = {}) {
        if (applying) return
        applying = true
        lifecycleScope.launch {
            try {
                if (updateWallpaper && lockScreen.wallpaper.depth) {
                    Toast.makeText(this@LockScreenGalleryActivity,
                        R.string.depthwall_extracting_toast, Toast.LENGTH_LONG).show()
                }
                val result = if (updateWallpaper) {
                    withContext(Dispatchers.IO) {
                        GalleryApplier.apply(this@LockScreenGalleryActivity, lockScreen)
                    }
                } else GalleryApplier.Result.APPLIED
                if (result == GalleryApplier.Result.FAILED) {
                    Toast.makeText(this@LockScreenGalleryActivity, R.string.gallery_apply_failed,
                        Toast.LENGTH_LONG).show()
                    return@launch
                }
                applyClock(lockScreen.clock, tuning ?: lockScreen.clock.tuning)
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
            } finally {
                applying = false
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
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        val file = File(File(filesDir, "gallery").apply { mkdirs() }, "current.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val lockScreen = LockScreen(store.newId(),
            GalleryWallpaper(Kind.PHOTO, photos = listOf(file.absolutePath)))
        store.save(lockScreen)
        store.setCurrent(lockScreen.id)
    }

    companion object {
        const val EXTRA_GALLERY = "gallery"
        private const val ACTION_LOCK_SCREENS = "com.android.wallpaper.action.LOCK_SCREENS"
        private const val ACTION_LOCK_SCREEN_CLOCK =
            "org.lunaris.lockstyles.action.LOCK_SCREEN_CLOCK"
        private const val MAX_SHUFFLE = 30
        private const val FEATURED = 6
    }
}
