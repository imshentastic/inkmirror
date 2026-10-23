package io.github.jqssun.airplay.inkmirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * InkMirror: drive the Bluetooth mouse from adb, for testing and calibration.
 * Protected by android.permission.DUMP (only adb/shell can send it), like the
 * app's SettingsReceiver.
 *   am broadcast -n io.github.imshentastic.inkmirror/io.github.jqssun.airplay.inkmirror.InkMirrorDebugReceiver \
 *     -a io.github.jqssun.airplay.inkmirror.HID --es cmd "home" | "move 100 0" | "click" | "tap 0.5 0.5"
 */
class InkMirrorDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = (intent.getStringExtra("cmd") ?: return).trim().split(Regex("\\s+"))
        HidPointer.start(context)
        when (parts[0]) {
            "home" -> HidPointer.debugHome()
            "move" -> HidPointer.debugMove(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.toIntOrNull() ?: 0)
            "click" -> HidPointer.debugClick()
            "tap" -> HidPointer.tap(parts.getOrNull(1)?.toFloatOrNull() ?: 0.5f, parts.getOrNull(2)?.toFloatOrNull() ?: 0.5f)
            "offset" -> { HidPointer.offsetX = parts.getOrNull(1)?.toIntOrNull() ?: 0; HidPointer.offsetY = parts.getOrNull(2)?.toIntOrNull() ?: 0 }
            "step" -> parts.getOrNull(1)?.toIntOrNull()?.let { HidPointer.stepCounts = it.coerceIn(1, 127) }
        }
        resultData = "ok ${parts.joinToString(" ")} connected=${HidPointer.connected} step=${HidPointer.stepCounts}"
    }
}
