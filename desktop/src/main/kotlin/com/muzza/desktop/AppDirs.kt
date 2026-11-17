package com.muzza.desktop

import java.io.File

/** Where Muzza keeps its files on this computer. */
object AppDirs {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    /** Settings, playlists, the song index and the downloader engine. */
    val data: File by lazy {
        val base = if (isWindows) System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Roaming")
        else System.getenv("XDG_DATA_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
        File(base, if (isWindows) "Muzza" else "muzza").apply { mkdirs() }
    }

    val bin: File get() = File(data, "bin").apply { mkdirs() }
    val covers: File get() = File(data, "covers").apply { mkdirs() }

    val home: File get() = File(System.getProperty("user.home"))
    val music: File get() = File(home, "Music")
    /** The downloader saves here, like Music/Muzza on the phone. */
    val downloads: File get() = File(music, "Muzza")

    /** Folder the app was started from (holds the bundled ffmpeg in a release build). */
    val appHome: File? by lazy {
        System.getProperty("muzza.home")?.let(::File) ?: runCatching {
            File(AppDirs::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        }.getOrNull()
    }

    private fun exe(name: String) = if (isWindows) "$name.exe" else name

    /** The bundled ffmpeg next to the app, or the one on PATH while developing. */
    val ffmpeg: String by lazy {
        val name = exe("ffmpeg")
        val roots = listOfNotNull(appHome, appHome?.parentFile)
        roots.flatMap { listOf(File(it, "ffmpeg/$name"), File(it, name)) }.firstOrNull { it.isFile }?.absolutePath ?: name
    }

    val ytDlp: File get() = File(bin, exe("yt-dlp"))
}
