package us.fr4x.mlbbbot

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var spinner: Spinner
    private var devices: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val missing = requiredPerms().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onStart() {
        super.onStart()
        val old = HidController.attach(
            log = { line -> appendLog(line) },
            state = { refreshStatus() }
        )
        logView.text = ""
        old.forEach { appendLog(it) }
        refreshStatus()
        refreshDevices()
    }

    override fun onStop() {
        HidController.detach()
        super.onStop()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        refreshDevices()
        HidController.log(if (btGranted()) "Izin Bluetooth diberikan" else "Izin Bluetooth DITOLAK - Connect tidak akan jalan")
    }

    private fun requiredPerms(): List<String> {
        val l = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            l += Manifest.permission.BLUETOOTH_CONNECT
            l += Manifest.permission.BLUETOOTH_ADVERTISE
        }
        if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.POST_NOTIFICATIONS
        return l
    }

    private fun btGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshDevices() {
        devices = if (btGranted()) HidController.bondedDevices(this) else emptyList()
        val names = mutableListOf("(tanpa target - tunggu iPhone menyambung)")
        devices.forEach { names += "${it.name ?: "?"} (${it.address})" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
    }

    private fun refreshStatus() {
        statusView.text = "Status: ${HidController.status()}"
    }

    private fun appendLog(line: String) {
        logView.append(line + "\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onConnect() {
        if (!btGranted()) {
            requestPermissions(requiredPerms().toTypedArray(), 1)
            return
        }
        val target = devices.getOrNull(spinner.selectedItemPosition - 1)
        startForegroundService(Intent(this, HidService::class.java))
        HidController.start(this, target)
        startActivity(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        )
    }

    private fun onDisconnect() {
        HidController.shutdown()
        stopService(Intent(this, HidService::class.java))
    }

    private fun buildUi() {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        statusView = TextView(this).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD) }
        spinner = Spinner(this)
        root.addView(statusView)
        root.addView(spinner)

        fun button(text: String, color: Int? = null, action: () -> Unit) {
            val b = Button(this).apply {
                this.text = text
                setOnClickListener { action() }
                if (color != null) {
                    setBackgroundColor(color)
                    setTextColor(Color.WHITE)
                }
            }
            root.addView(b)
        }

        button("Connect") { onConnect() }
        button("Move") { HidController.testMove() }
        button("Test Click") { HidController.click() }
        button("Test Drag") { HidController.testDrag() }
        button("Press (tahan)") { HidController.press() }
        button("Release") { HidController.release() }
        button("Disconnect") { onDisconnect() }
        button("EMERGENCY STOP", Color.RED) { HidController.emergencyStop() }

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
        }
        logScroll = ScrollView(this).apply { addView(logView) }
        root.addView(logScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
