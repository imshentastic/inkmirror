package io.github.jqssun.airplay.s4

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * S4: drive the Bluetooth mouse from adb, for testing and calibration.
 * Protected by android.permission.DUMP (only adb/shell can send it), like the
 * app's SettingsReceiver.
 *   am broadcast -n io.github.jqssun.airplay.s4/io.github.jqssun.airplay.s4.S4DebugReceiver \
 *     -a io.github.jqssun.airplay.s4.HID --es cmd "home" | "move 100 0" | "click" | "tap 0.5 0.5"
 */
class S4DebugReceiver : BroadcastReceiver() {
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
