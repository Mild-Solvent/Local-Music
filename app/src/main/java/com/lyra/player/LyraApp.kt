package com.lyra.player

import android.app.Application
import com.lyra.player.data.LibraryStore
import com.lyra.player.data.MusicRepository
import com.lyra.player.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LyraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        LibraryStore.init(this)
        MusicRepository.init(this)
    }

    companion object {
        lateinit var instance: LyraApp
            private set
        /** Lives as long as the process; used for work that must outlive a screen. */
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
