package com.lyra.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lyra.player.data.CoverArt
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LyraApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        LibraryStore.init(this)
        CoverArt.init()
        MusicRepository.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(CoverArt.SongArtFetcher.Factory()) }
        .build()

    companion object {
        lateinit var instance: LyraApp
            private set
        /** Lives as long as the process; used for work that must outlive a screen. */
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
