package com.nocturn.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Text
import java.util.concurrent.TimeUnit

object ImageLoader {
    private val imageCache = object : LruCache<String, Bitmap>(50 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val imageClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getCached(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return imageCache.get(url)
    }

    suspend fun loadBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        val cached = imageCache.get(url)
        if (cached != null) return@withContext cached

        try {
            val fetchUrl = if (url.contains("music.126.net") && !url.contains("?param=")) {
                "$url?param=300y300"
            } else {
                url
            }
            val req = Request.Builder()
                .url(fetchUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val loaded = imageClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    res.body.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else null
            }
            if (loaded != null) {
                imageCache.put(url, loaded)
            }
            loaded
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun AsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Color(0xFF2C2C2E)
) {
    var bitmap by remember(url) {
        mutableStateOf(ImageLoader.getCached(url))
    }
    var isLoading by remember(url) {
        mutableStateOf(!url.isNullOrBlank() && bitmap == null)
    }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            isLoading = false
            return@LaunchedEffect
        }

        val cached = ImageLoader.getCached(url)
        if (cached != null) {
            bitmap = cached
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        val loaded = ImageLoader.loadBitmap(url)
        if (loaded != null) {
            bitmap = loaded
        }
        isLoading = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = bitmap,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "image-fade"
        ) { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(placeholderColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    } else {
                        Text(
                            text = "♪",
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
