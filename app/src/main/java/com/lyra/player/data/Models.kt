package com.lyra.player.data

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val path: String,
    val folder: String,
    val dateAdded: Long,
    val track: Int,
    val year: Int,
    val size: Long,
) {
    val artUri: Uri get() = albumArtUri(albumId)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}

data class Album(val id: Long, val title: String, val artist: String, val year: Int, val songs: List<Song>) {
    val artUri: Uri get() = albumArtUri(id)
}

data class Artist(val name: String, val songs: List<Song>, val albums: List<Album>)

data class Folder(val path: String, val name: String, val songs: List<Song>)

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun totalDuration(songs: List<Song>): String {
    val mins = songs.sumOf { it.duration } / 60000
    return if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
}
