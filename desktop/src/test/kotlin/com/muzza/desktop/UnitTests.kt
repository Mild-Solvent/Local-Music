package com.muzza.desktop

import com.muzza.desktop.data.Settings
import com.muzza.desktop.data.songId
import com.muzza.desktop.download.Downloader
import com.muzza.desktop.playback.Effects
import com.muzza.desktop.update.Updater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitTests {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test fun updaterPicksNewestDesktopReleaseAndIgnoresPhoneAndDrafts() {
        val r = Updater.latestDesktop(res("releases.json"))!!
        assertEquals("1.1.0", r.version)
        assertTrue(r.page.endsWith("desktop-v1.1.0"))
        assertTrue(r.pkg!!.url.endsWith(r.packageName), r.pkg!!.url)
    }

    @Test fun updaterHandlesNoReleases() = assertNull(Updater.latestDesktop("[]"))

    @Test fun versionCompare() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("1.10.0", "1.9.9"))
        assertTrue(!Updater.isNewer("1.0.0", "1.0.0"))
    }

    @Test fun songIdIsStableAndIgnoresSlashStyleAndCase() {
        assertEquals(songId("C:\\Music\\A.mp3"), songId("c:/music/a.mp3"))
        assertTrue(songId("/a/b.mp3") >= 0)
    }

    @Test fun effectsChain() {
        assertEquals("", Effects.filters(Settings()))
        val f = Effects.filters(Settings(eqEnabled = true, eqBands = listOf(6f, 0f, 0f, 0f, -2f), speed = 1.25f))
        assertTrue(f.startsWith("equalizer=f=60:"), f)
        assertTrue("equalizer=f=14000" in f && "alimiter" in f && f.endsWith("atempo=1.25"), f)
    }

    @Test fun extractUrl() {
        assertEquals("https://youtu.be/abc", Downloader.extractUrl("listen https://youtu.be/abc."))
        assertNull(Downloader.extractUrl("just words"))
    }
}
