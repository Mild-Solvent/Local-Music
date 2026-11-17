package com.lyra.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.mutableStateOf
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.File

/**
 * Per-song cover art.
 *
 * By default a song shows the picture embedded in its own file (not the album's picture, which
 * Android shares between every song of an album). The user can override it per song with a
 * picture from the gallery, or with the fish placeholder.
 */
object CoverArt {
    const val SCHEME = "muzza-art"
    const val FISH = "fish"

    /** song id -> FISH or absolute path of a custom picture. Compose state, so covers update live. */
    private val overrides = mutableStateOf<Map<Long, String>>(emptyMap())

    fun init() {
        overrides.value = LibraryStore.data.value.covers
    }

    fun override(songId: Long): String? = overrides.value[songId]

    /** What the app's image views load for this song. */
    fun uriFor(song: Song): Uri = when (val o = overrides.value[song.id]) {
        null -> Uri.parse("$SCHEME://song/${song.id}?album=${song.albumId}")
        FISH -> Uri.parse("$SCHEME://fish")
        else -> Uri.fromFile(File(o))
    }

    /** What the media notification / lock screen loads (it cannot use our own scheme). */
    fun notificationUriFor(song: Song): Uri? = when (val o = overrides.value[song.id]) {
        null -> albumArtUri(song.albumId)
        FISH -> null
        else -> Uri.fromFile(File(o))
    }

    fun useFish(songId: Long) = set(songId, FISH)
    fun restore(songId: Long) = set(songId, null)

    /** Copies the picked picture into the app (square, max 1024 px) and uses it for the song. */
    suspend fun setCustom(context: Context, songId: Long, picked: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(picked)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
            val full = context.contentResolver.openInputStream(picked)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val side = minOf(full.width, full.height)
            var square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
            if (side > 1024) square = Bitmap.createScaledBitmap(square, 1024, 1024, true)
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val out = File(dir, "$songId-${System.currentTimeMillis()}.jpg")
            out.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            withContext(Dispatchers.Main) { set(songId, out.absolutePath) }
            true
        }.getOrDefault(false)
    }

    private fun set(songId: Long, value: String?) {
        val old = overrides.value[songId]
        if (old != null && old != FISH && old != value) File(old).delete()
        val next = if (value == null) overrides.value - songId else overrides.value + (songId to value)
        overrides.value = next
        LibraryStore.setCovers(next)
    }

    /** Loads `muzza-art://song/<id>?album=<albumId>`: the song file's own picture, then the album's. */
    class SongArtFetcher(private val uri: Uri, private val options: Options) : Fetcher {
        override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
            val ctx = options.context
            if (uri.host == FISH) error("fish cover")
            val id = uri.lastPathSegment?.toLongOrNull() ?: error("bad art uri")
            val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { ctx.contentResolver.loadThumbnail(songUri, Size(512, 512), null) }.getOrNull()?.let {
                    return@withContext DrawableResult(BitmapDrawable(ctx.resources, it), isSampled = true, dataSource = DataSource.DISK)
                }
            }
            embedded(ctx, songUri)?.let {
                return@withContext SourceResult(ImageSource(Buffer().write(it), ctx), mimeType = null, dataSource = DataSource.DISK)
            }
            val albumId = uri.getQueryParameter("album")?.toLongOrNull() ?: error("no art")
            val stream = ctx.contentResolver.openInputStream(albumArtUri(albumId)) ?: error("no art")
            val bytes = stream.use { it.readBytes() }
            SourceResult(ImageSource(Buffer().write(bytes), ctx), mimeType = null, dataSource = DataSource.DISK)
        }

        private fun embedded(ctx: Context, songUri: Uri): ByteArray? {
            val r = MediaMetadataRetriever()
            return try {
                r.setDataSource(ctx, songUri)
                r.embeddedPicture
            } catch (e: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }

        class Factory : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
                if (data.scheme == SCHEME) SongArtFetcher(data, options) else null
        }
    }
}
