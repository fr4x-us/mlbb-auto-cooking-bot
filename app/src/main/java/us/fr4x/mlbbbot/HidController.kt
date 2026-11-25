package us.fr4x.mlbbbot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Mouse Bluetooth HID memakai API resmi BluetoothHidDevice.
 * Semua gerakan berjalan di satu thread worker; emergencyStop() membatalkan semuanya secara atomik.
 */
@SuppressLint("MissingPermission")
object HidController {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val history = ArrayDeque<String>()

    @Volatile private var epoch = 0
    @Volatile private var buttons = 0
    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var registered = false
    private var pending: BluetoothDevice? = null
    private var app: Context? = null

    private var onLog: ((String) -> Unit)? = null
    private var onState: (() -> Unit)? = null

    // ---------- UI binding (thread utama) ----------

    fun attach(log: (String) -> Unit, state: () -> Unit): List<String> {
        onLog = log
        onState = state
        return history.toList()
    }

    fun detach() {
        onLog = null
        onState = null
    }

    fun log(msg: String) {
        main.post {
            val line = "${fmt.format(System.currentTimeMillis())} $msg"
            history.addLast(line)
            while (history.size > 300) history.removeFirst()
            onLog?.invoke(line)
        }
    }

    private fun notifyState() {
        main.post { onState?.invoke() }
    }

    fun status(): String = when {
        hid == null -> "Mati"
        host != null -> "Terhubung: ${host?.name ?: host?.address}"
        registered -> "Terdaftar - menunggu iPhone"
        else -> "Mendaftar..."
    }

    fun isActive() = hid != null
    fun isConnected() = host != null

    fun bondedDevices(ctx: Context): List<BluetoothDevice> =
        adapter(ctx)?.bondedDevices?.toList().orEmpty()

    private fun adapter(ctx: Context): BluetoothAdapter? =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter

    // ---------- Siklus hidup ----------

    fun start(ctx: Context, target: BluetoothDevice?) {
        val c = ctx.applicationContext
        app = c
        pending = target
        val existing = hid
        if (existing != null) {
            log("HID sudah aktif")
            if (registered && target != null) connectTo(target)
            return
        }
        val ad = adapter(c)
        if (ad == null || !ad.isEnabled) {
            log("Bluetooth mati atau tidak tersedia")
            return
        }
        log("Meminta profil HID_DEVICE...")
        if (!ad.getProfileProxy(c, proxyListener, BluetoothProfile.HID_DEVICE)) {
            log("getProfileProxy gagal")
        }
    }

    fun connectTo(d: BluetoothDevice) {
        val h = hid ?: return
        if (host == d) return
        pending = null
        log("connect(${d.address}) -> ${h.connect(d)}")
    }

    fun shutdown() {
        emergencyStop(silent = true)
        val h = hid
        if (h != null) {
            try {
                host?.let { h.disconnect(it) }
                h.unregisterApp()
                app?.let { adapter(it)?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h) }
            } catch (e: Exception) {
                log("shutdown error: ${e.message}")
            }
            log("HID dimatikan")
        }
        hid = null
        host = null
        registered = false
        pending = null
        notifyState()
    }

    /** Batalkan semua gerakan yang berjalan/antre dan lepaskan semua tombol. */
    fun emergencyStop(silent: Boolean = false) {
        synchronized(lock) {
            epoch++
            buttons = 0
            val h = hid
            val d = host
            if (h != null && d != null) h.sendReport(d, 0, MouseReport.build(0, 0, 0))
        }
        if (!silent) log("EMERGENCY STOP: gerakan dibatalkan, tombol dilepas")
    }

    private fun resetLocal() {
        synchronized(lock) {
            epoch++
            buttons = 0
        }
    }

    // ---------- Callback sistem ----------

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "MLBB Mouse", "Bluetooth mouse", "MLBB",
                BluetoothHidDevice.SUBCLASS1_MOUSE, MouseReport.DESCRIPTOR
            )
            val ok = h.registerApp(sdp, null, null, app!!.mainExecutor, callback)
            log("registerApp -> $ok")
            notifyState()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("Proxy HID terputus oleh sistem")
            resetLocal()
            hid = null
            host = null
            registered = false
            notifyState()
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidController.registered = registered
            log("App terdaftar=$registered perangkat=${pluggedDevice?.address}")
            if (registered) pending?.let { connectTo(it) }
            notifyState()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            log("Koneksi ${device?.address}: ${stateName(state)}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> host = device
                BluetoothProfile.STATE_DISCONNECTED -> if (device == null || device == host) {
                    resetLocal()
                    host = null
                }
            }
            notifyState()
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (device != null) hid?.replyReport(device, type, id, ByteArray(4))
        }
    }

    private fun stateName(s: Int) = when (s) {
        BluetoothProfile.STATE_CONNECTED -> "TERHUBUNG"
        BluetoothProfile.STATE_CONNECTING -> "MENGHUBUNGKAN"
        BluetoothProfile.STATE_DISCONNECTING -> "MEMUTUSKAN"
        BluetoothProfile.STATE_DISCONNECTED -> "TERPUTUS"
        else -> "state=$s"
    }

    // ---------- Gerakan ----------

    private fun report(my: Int, dx: Int = 0, dy: Int = 0, btn: Int = buttons): Boolean {
        synchronized(lock) {
            if (epoch != my) return false
            val h = hid
            val d = host
            if (h == null || d == null) {
                log("Tidak terhubung ke iPhone")
                return false
            }
            buttons = btn
            return h.sendReport(d, 0, MouseReport.build(btn, dx, dy))
        }
    }

    private fun enqueue(name: String, block: (my: Int) -> Unit) {
        val my = epoch
        worker.execute {
            if (epoch != my) return@execute
            log("$name: mulai")
            try {
                block(my)
            } catch (e: Exception) {
                log("$name error: ${e.message}")
            }
            log(if (epoch == my) "$name: selesai" else "$name: dibatalkan")
        }
    }

    private fun moveSteps(my: Int, dx: Int, dy: Int, maxStep: Int = 127, delayMs: Long = 8): Boolean {
        for ((sx, sy) in MouseReport.splitMove(dx, dy, maxStep)) {
            if (!report(my, sx, sy)) return false
            Thread.sleep(delayMs)
        }
        return true
    }

    fun moveBy(dx: Int, dy: Int) = enqueue("Gerak ($dx,$dy)") { my -> moveSteps(my, dx, dy) }

    fun testMove() = enqueue("Test gerak (kotak 150px)") { my ->
        moveSteps(my, 150, 0) && moveSteps(my, 0, 150) && moveSteps(my, -150, 0) && moveSteps(my, 0, -150)
    }

    fun press() = enqueue("Tekan") { my -> report(my, btn = MouseReport.BTN_LEFT) }

    fun release() = enqueue("Lepas") { my -> report(my, btn = 0) }

    fun click() = enqueue("Klik") { my ->
        if (report(my, btn = MouseReport.BTN_LEFT)) {
            Thread.sleep(70)
            report(my, btn = 0)
        }
    }

    fun testDrag() = enqueue("Test drag") { my ->
        try {
            if (report(my, btn = MouseReport.BTN_LEFT)) {
                Thread.sleep(200)
                moveSteps(my, 250, 0, maxStep = 4, delayMs = 10)
                Thread.sleep(200)
                moveSteps(my, 0, 120, maxStep = 4, delayMs = 10)
                Thread.sleep(200)
            }
        } finally {
            report(my, btn = 0)
        }
    }
}
