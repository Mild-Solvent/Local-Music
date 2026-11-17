package com.muzza.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.muzza.desktop.data.MusicLibrary
import com.muzza.desktop.data.Store
import com.muzza.desktop.playback.Player
import com.muzza.desktop.ui.App
import com.muzza.desktop.ui.handleKey
import com.muzza.desktop.update.Updater
import java.awt.Dimension
import javax.swing.UIManager

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--selftest") { Store.init(); kotlin.system.exitProcess(SelfTest.run(args.drop(1))) }
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    Store.init()
    MusicLibrary.init()
    Player.init()
    Updater.check()
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { Player.shutdown() } })

    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("muzza-icon.png")?.use { BitmapPainter(loadImageBitmap(it)) }
    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        Window(
            onCloseRequest = { Player.shutdown(); exitApplication() },
            state = state,
            title = "Muzza",
            icon = icon,
            onKeyEvent = { handleKey(it) },
        ) {
            window.minimumSize = Dimension(980, 640)
            App()
        }
    }
}
