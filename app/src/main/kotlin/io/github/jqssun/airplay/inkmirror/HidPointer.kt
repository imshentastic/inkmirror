package io.github.jqssun.airplay.inkmirror

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * InkMirror: this app as a Bluetooth mouse for the phone it mirrors.
 *
 * Android's HID device profile (API 28+) lets the app present itself as a
 * standard relative mouse (3 buttons, X, Y, wheel). iOS shows a pointer for it
 * when AssistiveTouch is on. iOS mice are relative, so a tap is turned into:
 * push the pointer into the bottom-left corner (a known spot), then move by the
 * calibrated number of counts in small, evenly paced steps (keeps iOS pointer
 * acceleration out of it), then click.
 *
 * Only one app can be the HID device at a time: close other Bluetooth mouse
 * apps (e.g. Bluetooth Remote) first.
 */
@SuppressLint("MissingPermission")
object HidPointer {
    // also a keyboard (report id 2): the tablet page buttons send arrow keys
    private const val TAG = "InkMirrorHid"
    private const val REPORT_ID = 1
    private const val KEYBOARD_ID = 2
    // USB HID key codes
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_SPACE = 0x2C

    /** True while the mirror is on screen: the page buttons then go to the phone. */
    @Volatile var mirrorShowing = false

    // relative mouse: buttons(3 bits + 5 pad), X, Y, wheel (signed bytes)
    private val DESCRIPTOR = byteArrayOf(
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), REPORT_ID.toByte(),
        0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95.toByte(), 0x03,
        0x75, 0x01, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81.toByte(), 0x25, 0x7F,
        0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte(),
        // keyboard, report id 2: modifiers, reserved, 6 key codes
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), KEYBOARD_ID.toByte(),
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
        0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
        0xC0.toByte()
    )

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile var host: BluetoothDevice? = null
        private set
    @Volatile private var started = false

    // counts that move the pointer across the whole mirrored width / height
    @Volatile var countsPerWidth = 402
    @Volatile var countsPerHeight = 874
    // counts from the corner to the phone's (0, 0) edge, from 2-point calibration
    @Volatile var offsetX = 0
    @Volatile var offsetY = 0
    private fun cx(u: Float) = (offsetX + u * countsPerWidth).toInt().coerceAtLeast(0)
    // counts up from the bottom edge (home is the bottom-left corner)
    private fun cy(v: Float) = (offsetY + (1f - v) * countsPerHeight).toInt().coerceAtLeast(0)
    @Volatile var stepCounts = 12
    @Volatile var stepDelayMs = 8L

    val connected get() = hid != null && host != null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "app registered=$registered plugged=${pluggedDevice?.address}")
            if (registered) connectHost(pluggedDevice)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.i(TAG, "connection ${device.address} state=$state")
            if (state == BluetoothProfile.STATE_CONNECTED) host = device
            else if (state == BluetoothProfile.STATE_DISCONNECTED && host == device) host = null
        }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "InkMirror", "Touch control for the mirrored phone", "InkMirror",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESCRIPTOR
                )
                val ok = h.registerApp(sdp, null, null, worker, callback)
                Log.i(TAG, "registerApp=$ok")
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                host = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    // the phone that pairs as host: the one already plugged, else a bonded phone
    private fun connectHost(plugged: BluetoothDevice?) {
        val h = hid ?: return
        val target = plugged ?: BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.firstOrNull {
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE
        }
        if (target != null) {
            Log.i(TAG, "connecting to ${target.address}")
            h.connect(target)
        }
    }

    private var sent = 0
    private fun send(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) {
        val h = hid ?: run { Log.w(TAG, "send: no HID proxy"); return }
        val d = host ?: run { Log.w(TAG, "send: no host connected"); return }
        val ok = h.sendReport(d, REPORT_ID, byteArrayOf(buttons.toByte(), dx.toByte(), dy.toByte(), wheel.toByte()))
        if (!ok || buttons != 0 || sent++ % 50 == 0) Log.i(TAG, "sendReport ok=$ok b=$buttons dx=$dx dy=$dy w=$wheel (#$sent)")
    }

    private fun pause(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    // where the pointer is, in counts from the corner; -1 = unknown (go home first)
    private var curX = -1
    private var curY = -1
    private var movesSinceHome = 0
    private var lastMoveAt = 0L
    private const val REHOME_EVERY = 10
    private const val REHOME_IDLE_MS = 120_000L

    private fun home() {
        // bottom-left corner: the phone's top status bar is never needed
        repeat(20) { send(0, -127, 127); pause(stepDelayMs) }
        pause(40)
        curX = 0
        curY = 0
        movesSinceHome = 0
    }

    // to (tx, ty) counts: a short hop from the last position when it is trusted,
    // otherwise home first (every few moves, after idle, or when unknown)
    private fun goTo(tx: Int, ty: Int) {
        val now = System.currentTimeMillis()
        if (curX < 0 || movesSinceHome >= REHOME_EVERY || now - lastMoveAt > REHOME_IDLE_MS) home()
        moveBy(tx - curX, -(ty - curY))
        curX = tx
        curY = ty
        movesSinceHome++
        lastMoveAt = now
    }

    // move in even steps so iOS acceleration stays at its lowest
    private fun moveBy(dx: Int, dy: Int) {
        if (dx != 0 && dy != 0) { moveBy(dx, 0); moveBy(0, dy); return }
        var rx = dx
        var ry = dy
        while (rx != 0 || ry != 0) {
            val sx = rx.coerceIn(-stepCounts, stepCounts)
            val sy = ry.coerceIn(-stepCounts, stepCounts)
            send(0, sx, sy)
            rx -= sx
            ry -= sy
            pause(stepDelayMs)
        }
    }

    /** Pointer to (u, v), fractions of the mirrored screen. */
    fun moveTo(u: Float, v: Float) = worker.execute { goTo(cx(u), cy(v)) }

    fun tap(u: Float, v: Float) = worker.execute {
        val tx = cx(u)
        val ty = cy(v)
        Log.i(TAG, "tap u=%.3f v=%.3f -> counts %d,%d (per width %d, height %d)".format(u, v, tx, ty, countsPerWidth, countsPerHeight))
        goTo(tx, ty)
        pause(120)
        send(1, 0, 0)
        pause(90)
        send(0, 0, 0)
        Log.i(TAG, "click sent")
    }

    fun press(u: Float, v: Float, holdMs: Long = 800) = worker.execute {
        goTo(cx(u), cy(v))
        pause(40)
        send(1, 0, 0)
        pause(holdMs)
        send(0, 0, 0)
    }

    /** Wheel scroll; positive scrolls content down (like dragging up). */
    fun scroll(notches: Int) = worker.execute {
        var n = notches
        while (n != 0) {
            val s = n.coerceIn(-1, 1)
            send(0, 0, 0, s)
            n -= s
            pause(30)
        }
    }

    /** One key press and release on the phone (e.g. arrow keys turn pages in reading apps). */
    fun key(code: Int) = worker.execute {
        val h = hid ?: return@execute
        val d = host ?: return@execute
        h.sendReport(d, KEYBOARD_ID, byteArrayOf(0, 0, code.toByte(), 0, 0, 0, 0, 0))
        pause(30)
        h.sendReport(d, KEYBOARD_ID, ByteArray(8))
        Log.i(TAG, "key 0x%02x".format(code))
    }

    fun debugHome() = worker.execute { home() }
    fun debugMove(dx: Int, dy: Int) = worker.execute { moveBy(dx, dy); curX = -1 }
    fun debugClick() = worker.execute { send(1, 0, 0); pause(90); send(0, 0, 0) }

    /** Two-point calibration: from the bottom-left corner, right and up by these counts; the user taps where it is. */
    val CAL_POINTS = arrayOf(intArrayOf(100, 250), intArrayOf(300, 750))
    fun calibrationMove(step: Int) = worker.execute {
        home()
        moveBy(CAL_POINTS[step][0], -CAL_POINTS[step][1])
        curX = -1   // the calibration may change the mapping: home on the next tap
        Log.i(TAG, "calibration point $step at ${CAL_POINTS[step][0]},${CAL_POINTS[step][1]} counts")
    }
}
