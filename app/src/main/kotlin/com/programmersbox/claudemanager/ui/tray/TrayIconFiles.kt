package com.programmersbox.claudemanager.ui.tray

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The tray icon as fixed files on disk, copied once from the bundled `tray/` resources.
 *
 * ComposeNativeTray's ImageVector/Painter `Tray` overloads re-render the icon to a fresh temp file on
 * every menu update. On macOS they also hand both paths to `tray_set_icons_for_appearance`, whose
 * JNI wrapper frees the C strings while the Swift side still reads them on the main queue later.
 * That use-after-free crashed the app (SIGTRAP in `NSImage initWithContentsOfFile:`) after a while
 * of session churn. Passing a stable path means the library sees an unchanged icon and never calls
 * back into native icon code. The native side needs a real file, so the resources are copied out.
 */
data class TrayIconFiles(val png: Path, val ico: Path)

/** Copies the bundled tray icons into [dir] (only when missing or changed) and returns their paths. */
fun trayIconFiles(dir: Path): TrayIconFiles {
    Files.createDirectories(dir)
    val files = TrayIconFiles(png = dir.resolve("tray.png"), ico = dir.resolve("tray.ico"))
    writeIfChanged(files.png, resource("tray/tray.png"))
    writeIfChanged(files.ico, resource("tray/tray.ico"))
    return files
}

private fun resource(name: String): ByteArray =
    checkNotNull(TrayIconFiles::class.java.classLoader.getResourceAsStream(name)) { "Missing resource $name" }
        .use { it.readBytes() }

private fun writeIfChanged(target: Path, bytes: ByteArray) {
    if (Files.exists(target) && Files.readAllBytes(target).contentEquals(bytes)) return
    val tmp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    Files.write(tmp, bytes)
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
