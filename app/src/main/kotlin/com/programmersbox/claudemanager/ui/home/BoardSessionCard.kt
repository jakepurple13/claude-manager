package com.programmersbox.claudemanager.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.programmersbox.claudemanager.data.SessionSummary
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.programmersbox.claudemanager.ui.components.ConfirmDialog
import com.programmersbox.claudemanager.ui.window.ScreenRect
import com.programmersbox.claudemanager.ui.window.SessionWindows
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.koinInject
import dev.nucleusframework.application.LocalNucleusWindow

private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")
private val openInWindowShortcut = if (isMac) "⌘↩" else "Ctrl+Enter"

/**
 * A board card with a right-click menu. ⌘-click (Ctrl-click off macOS), middle-click and ⌘↩ open the
 * session in its own window; a plain click opens it in the main window as before.
 */
@Composable
internal fun BoardSessionCard(
    session: SessionSummary,
    now: Long,
    showCost: Boolean,
    inWindow: Boolean,
    onOpen: () -> Unit,
    /** Gets the card's place on screen, so the new window can grow out of it. */
    onOpenInWindow: (ScreenRect?) -> Unit,
    onOpenTimeline: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onStop: () -> Boolean,
    modifier: Modifier = Modifier,
    sessionWindows: SessionWindows = koinInject(),
) {
    val nucleusWindow = LocalNucleusWindow.current
    val density = LocalDensity.current
    // The card's bounds inside the main window, in px; turned into screen dp when a window opens.
    val cardBounds = remember { arrayOfNulls<Rect>(1) }
    fun cardOnScreen(): ScreenRect? {
        val window = nucleusWindow.boundsOnScreen() ?: return null
        val card = cardBounds[0] ?: return null
        return with(density) {
            ScreenRect(window.x + card.left.toDp().value, window.y + card.top.toDp().value, card.width.toDp().value, card.height.toDp().value)
        }
    }
    // Lets a closing session window find this card and shrink back into it.
    DisposableEffect(session.live.sessionId) {
        val id = session.live.sessionId
        val locate: () -> ScreenRect? = { cardOnScreen() }
        sessionWindows.registerCard(id, locate)
        onDispose { sessionWindows.unregisterCard(id, locate) }
    }
    val openInWindowFromCard by rememberUpdatedState({ onOpenInWindow(cardOnScreen()) })
    val openInWindow: () -> Unit = { openInWindowFromCard() }
    // Where the menu was asked for, in the card's own coordinates; null while it is closed.
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    // Whether the press that leads to the next click held ⌘ (Ctrl off macOS).
    val commandPress = remember { booleanArrayOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var stopFailed by remember { mutableStateOf(false) }

    Box(
        modifier
            .onSizeChanged { cardSize = it }
            .onGloballyPositioned { cardBounds[0] = it.boundsInWindow() }
            .onPreviewKeyEvent { event ->
                val command = if (isMac) event.isMetaPressed else event.isCtrlPressed
                if (command && event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                    openInWindow()
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        val position = event.changes.firstOrNull()?.position ?: continue
                        val mods = event.keyboardModifiers
                        when {
                            // macOS treats Ctrl-click as a right-click.
                            event.buttons.isSecondaryPressed || (isMac && mods.isCtrlPressed && event.buttons.isPrimaryPressed) -> {
                                menuAt = position
                                event.changes.forEach { it.consume() }
                            }
                            event.buttons.isTertiaryPressed -> {
                                openInWindow()
                                event.changes.forEach { it.consume() }
                            }
                            else -> commandPress[0] = if (isMac) mods.isMetaPressed else mods.isCtrlPressed
                        }
                    }
                }
            },
    ) {
        SessionCard(
            session = session,
            now = now,
            showCost = showCost,
            inWindow = inWindow,
            onClick = {
                when {
                    menuAt != null -> Unit
                    commandPress[0] -> openInWindow()
                    else -> onOpen()
                }
            },
        )

        val at = menuAt
        DropdownMenu(
            expanded = at != null,
            onDismissRequest = { menuAt = null },
            // The menu hangs below its anchor; shift it up so it opens at the pointer.
            offset = with(density) {
                if (at == null) DpOffset.Zero else DpOffset(at.x.toDp(), (at.y - cardSize.height).toDp())
            },
        ) {
            MenuItem("Open", "↩") { menuAt = null; onOpen() }
            MenuItem(if (inWindow) "Show Window" else "Open in New Window", openInWindowShortcut) { menuAt = null; openInWindow() }
            HorizontalDivider()
            MenuItem("Open Timeline") { menuAt = null; onOpenTimeline() }
            MenuItem(if (session.pinned) "Unpin" else "Pin") { menuAt = null; onSetPinned(!session.pinned) }
            MenuItem("Rename…") { menuAt = null; renaming = true }
            HorizontalDivider()
            MenuItem("Stop Session…", danger = true) { menuAt = null; confirmStop = true }
        }
    }

    if (renaming) {
        RenameDialog(
            current = session.displayName,
            onRename = { renaming = false; onRename(it) },
            onDismiss = { renaming = false },
        )
    }
    if (confirmStop) {
        ConfirmDialog(
            title = "Stop ${session.displayName}?",
            body = "Claude Code (pid ${session.live.pid}) will be asked to exit. Work in progress in that session stops where it is.",
            confirmLabel = "Stop session",
            onConfirm = { confirmStop = false; stopFailed = !onStop() },
            onDismiss = { confirmStop = false },
        )
    }
    if (stopFailed) {
        AlertDialog(
            onDismissRequest = { stopFailed = false },
            title = { Text("Could not stop ${session.displayName}") },
            text = { Text("The process may already have exited.") },
            confirmButton = { TextButton(onClick = { stopFailed = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun MenuItem(label: String, shortcut: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
        trailingIcon = shortcut?.let { { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name != current) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
