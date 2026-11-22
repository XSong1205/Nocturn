package dev.nocturn.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import coil3.EventListener
import coil3.ImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Size
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.ui.components.rememberPlayerArtworkSeed
import dev.nocturn.app.ui.preview.PreviewData
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises real Coil requests with an interceptor that never accesses the network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerMonetBackdropTest {
    @get:Rule
    val compose = createComposeRule()

    private val loaders = mutableListOf<ImageLoader>()
    private val artworkEvents = CopyOnWriteArrayList<String>()

    @After
    fun shutdownImageLoaders() {
        loaders.forEach(ImageLoader::shutdown)
    }

    @Test
    fun artworkRequestsUseReadableBitmapsAndFollowCoverChanges() {
        val requests = CopyOnWriteArrayList<ImageRequest>()
        val sizes = CopyOnWriteArrayList<Size>()
        val loader = imageLoader { chain ->
            requests += chain.request
            sizes += chain.size
            success(chain.request, if (chain.request.data.toString().contains("red")) Red else Blue)
        }
        val song = mutableStateOf<Song?>(track(1, "red"))
        val observed = AtomicReference(Color.Unspecified)
        compose.setContent {
            val seed = rememberPlayerArtworkSeed(song.value, Fallback, loader).value
            SideEffect { observed.set(seed) }
        }

        awaitCondition { isRed(observed.get()) }
        compose.runOnIdle { song.value = track(2, "blue") }
        awaitCondition { isBlue(observed.get()) }

        assertEquals(2, requests.size)
        requests.forEach {
            assertFalse(it.allowHardware)
            assertEquals(Bitmap.Config.ARGB_8888, it.bitmapConfig)
        }
        assertEquals(listOf(Size(128, 128), Size(128, 128)), sizes.toList())
    }

    @Test
    fun missingArtworkUsesTheCurrentSongFallbackAndRespondsToThemeChanges() {
        val requests = AtomicInteger()
        val loader = imageLoader { chain ->
            requests.incrementAndGet()
            success(chain.request, Red)
        }
        val song = mutableStateOf<Song?>(track(1, "red"))
        val fallback = mutableStateOf(Fallback)
        val observed = AtomicReference(Color.Unspecified)
        compose.setContent {
            val seed = rememberPlayerArtworkSeed(song.value, fallback.value, loader).value
            SideEffect { observed.set(seed) }
        }

        awaitCondition { isRed(observed.get()) }
        compose.runOnIdle {
            song.value = track(2, null)
            fallback.value = Color.Green
        }
        awaitCondition { observed.get() == Color.Green }
        compose.runOnIdle {
            song.value = track(3, null)
            fallback.value = Color.Blue
        }
        awaitCondition { observed.get() == Color.Blue }
        compose.runOnIdle { fallback.value = Color.Magenta }
        awaitCondition { observed.get() == Color.Magenta }

        assertEquals(1, requests.get())
    }

    @Test
    fun failedArtworkDoesNotKeepThePreviousSongColorOrUseTheErrorImage() {
        val requests = AtomicInteger()
        val loader = imageLoader { chain ->
            requests.incrementAndGet()
            if (chain.request.data.toString().contains("missing")) {
                ErrorResult(
                    image = bitmap(Blue).asImage(),
                    request = chain.request,
                    throwable = IOException("Deterministic artwork failure"),
                )
            } else {
                success(chain.request, Red)
            }
        }
        val song = mutableStateOf<Song?>(track(1, "red"))
        val fallback = mutableStateOf(Fallback)
        val observed = AtomicReference(Color.Unspecified)
        compose.setContent {
            val seed = rememberPlayerArtworkSeed(song.value, fallback.value, loader).value
            SideEffect { observed.set(seed) }
        }

        awaitCondition { isRed(observed.get()) }
        compose.runOnIdle {
            song.value = track(2, "missing")
            fallback.value = Color.Green
        }
        awaitCondition { requests.get() == 2 && observed.get() == Color.Green }
        compose.waitForIdle()
        assertEquals(Color.Green, observed.get())
    }

    @Test
    fun aNewSongWithTheSameCoverRestartsItsArtworkRequest() {
        val requests = AtomicInteger()
        val loader = imageLoader { chain ->
            success(chain.request, if (requests.incrementAndGet() == 1) Red else Blue)
        }
        val song = mutableStateOf<Song?>(track(1, "shared"))
        val observed = AtomicReference(Color.Unspecified)
        compose.setContent {
            val seed = rememberPlayerArtworkSeed(song.value, Fallback, loader).value
            SideEffect { observed.set(seed) }
        }

        awaitCondition { isRed(observed.get()) }
        compose.runOnIdle { song.value = track(2, "shared") }
        awaitCondition { requests.get() == 2 && isBlue(observed.get()) }
    }

    @Test
    fun aSlowPreviousArtworkCannotOverwriteTheNewSongColor() {
        val releasePrevious = CompletableDeferred<Unit>()
        val previousStarted = AtomicBoolean()
        val previousFinished = AtomicBoolean()
        val previousCancelled = AtomicBoolean()
        val listener = object : EventListener() {
            override fun onCancel(request: ImageRequest) {
                if (request.data.toString().contains("slow")) previousCancelled.set(true)
            }
        }
        val loader = imageLoader(listener) { chain ->
            if (chain.request.data.toString().contains("slow")) {
                previousStarted.set(true)
                // Simulate a decoder that finishes after cancellation instead of stopping promptly.
                withContext(NonCancellable) {
                    releasePrevious.await()
                    previousFinished.set(true)
                    success(chain.request, Red)
                }
            } else {
                success(chain.request, Blue)
            }
        }
        val song = mutableStateOf<Song?>(track(1, "slow"))
        val observed = AtomicReference(Color.Unspecified)
        compose.setContent {
            val seed = rememberPlayerArtworkSeed(song.value, Fallback, loader).value
            SideEffect { observed.set(seed) }
        }

        try {
            awaitCondition { previousStarted.get() }
            compose.runOnIdle { song.value = track(2, "fast") }
            awaitCondition { isBlue(observed.get()) }
            releasePrevious.complete(Unit)
            awaitCondition { previousFinished.get() && previousCancelled.get() }
            compose.waitForIdle()
            assertTrue(isBlue(observed.get()))
        } finally {
            releasePrevious.complete(Unit)
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        try {
            compose.waitUntil {
                // Robolectric pauses the Android main looper. Advancing only Compose's clock
                // does not run Coil's main-dispatcher work or commits from background extraction.
                compose.waitForIdle()
                condition()
            }
        } catch (failure: Throwable) {
            throw AssertionError("Artwork condition failed; Coil events: $artworkEvents", failure)
        }
    }

    private fun imageLoader(listener: EventListener? = null, interceptor: Interceptor): ImageLoader =
        ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .memoryCache(null)
            .diskCache(null)
            .components { add(interceptor) }
            .eventListener(object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    artworkEvents += "start ${request.data}"
                    listener?.onStart(request)
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    artworkEvents += "success ${request.data}"
                    listener?.onSuccess(request, result)
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    artworkEvents += "error ${request.data}: ${result.throwable.stackTraceToString()}"
                    listener?.onError(request, result)
                }

                override fun onCancel(request: ImageRequest) {
                    artworkEvents += "cancel ${request.data}"
                    listener?.onCancel(request)
                }
            })
            .build()
            .also(loaders::add)

    private fun track(id: Long, cover: String?): Song = PreviewData.songs.first().copy(
        id = id,
        album = null,
        coverUrl = cover?.let { "https://artwork.test/$it" },
    )

    private fun success(request: ImageRequest, color: Int): SuccessResult =
        SuccessResult(image = bitmap(color).asImage(), request = request)

    private fun bitmap(color: Int): Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }

    private fun isRed(color: Color): Boolean = color != Color.Unspecified &&
        color.red > color.green * 1.5f && color.red > color.blue * 1.5f

    private fun isBlue(color: Color): Boolean = color != Color.Unspecified &&
        color.blue > color.red * 1.5f && color.blue > color.green * 1.5f

    private companion object {
        const val Red = 0xFFE23434.toInt()
        const val Blue = 0xFF274CDE.toInt()
        val Fallback = Color(0xFF557766)
    }
}
