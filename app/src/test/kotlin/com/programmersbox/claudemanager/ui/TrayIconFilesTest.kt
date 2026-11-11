package com.programmersbox.claudemanager.ui

import com.programmersbox.claudemanager.ui.tray.trayIconFiles
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TrayIconFilesTest {
    @Test
    fun copiesBundledIconsToStablePaths() {
        val dir = Files.createTempDirectory("tray-icons")
        val first = trayIconFiles(dir)
        val modified = Files.getLastModifiedTime(first.png)
        val second = trayIconFiles(dir)

        // Same path on every call is what keeps the tray library from touching native icon code.
        assertEquals(first, second)
        assertEquals(modified, Files.getLastModifiedTime(second.png))
        assertEquals(2, Files.list(dir).use { it.count() })
    }

    @Test
    fun iconsAreValid() {
        val files = trayIconFiles(Files.createTempDirectory("tray-icons"))
        assertEquals(64, ImageIO.read(files.png.toFile()).width)
        // ICO header: reserved 0, type 1 (icon).
        assertContentEquals(byteArrayOf(0, 0, 1, 0), Files.readAllBytes(files.ico).copyOfRange(0, 4))
    }
}
