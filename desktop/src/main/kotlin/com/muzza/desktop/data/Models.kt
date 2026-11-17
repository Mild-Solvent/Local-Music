package com.muzza.desktop.data

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val track: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val size: Long = 0,
    val modified: Long = 0,
) {
    val folder: String get() = path.substringBeforeLast('/', "").substringBeforeLast('\\')
    val albumKey: String get() = "${album.lowercase()}|${artist.lowercase()}"
}

data class Album(val key: String, val title: String, val artist: String, val year: Int, val songs: List<Song>)
data class Artist(val name: String, val songs: List<Song>, val albums: List<Album>)
data class Folder(val path: String, val name: String, val songs: List<Song>)

data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
) {
    val byId: Map<Long, Song> = songs.associateBy { it.id }
    val albumByKey: Map<String, Album> = albums.associateBy { it.key }

    companion object {
        fun of(songs: List<Song>): Library {
            val sorted = songs.sortedBy { it.title.lowercase() }
            val albums = sorted.groupBy { it.albumKey }.map { (k, s) ->
                val ordered = s.sortedWith(compareBy<Song> { it.track }.thenBy { it.title.lowercase() })
                Album(k, ordered.first().album, ordered.first().artist, ordered.maxOf { it.year }, ordered)
            }.sortedBy { it.title.lowercase() }
            val artists = sorted.groupBy { it.artist }.map { (name, s) ->
                Artist(name, s, albums.filter { a -> a.songs.any { it.artist == name } })
            }.sortedBy { it.name.lowercase() }
            val folders = sorted.groupBy { it.folder }.map { (p, s) ->
                Folder(p, p.substringAfterLast('/').substringAfterLast('\\').ifBlank { p }, s)
            }.sortedBy { it.name.lowercase() }
            return Library(sorted, albums, artists, folders)
        }
    }
}

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

/** Stable id for a file: the same path always gives the same id, so playlists survive restarts. */
fun songId(path: String): Long {
    var h = -0x340d631b7bdddcdbL // FNV-1a 64
    for (c in path.replace('\\', '/').lowercase()) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
    return h and Long.MAX_VALUE
}
