package us.fr4x.mlbbbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/** Foreground service: menjaga proses tetap hidup selama sesi HID, dan menyediakan tombol STOP di notifikasi. */
class HidService : Service() {

    companion object {
        const val ACTION_STOP = "us.fr4x.mlbbbot.STOP"
        private const val CHANNEL = "hid"
        private const val NOTIF_ID = 1
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val s = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if (s == BluetoothAdapter.STATE_OFF || s == BluetoothAdapter.STATE_TURNING_OFF) {
                HidController.log("Bluetooth dimatikan - sesi dihentikan")
                HidController.shutdown()
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sesi HID", NotificationManager.IMPORTANCE_LOW)
        )
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(btReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            HidController.emergencyStop()
            if (!HidController.isActive()) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, HidService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_delete), "STOP", stopPi
        ).build()
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("MLBB HID Test aktif")
            .setContentText("Mouse Bluetooth HID berjalan. Tekan STOP untuk berhenti.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(btReceiver)
        } catch (e: IllegalArgumentException) {
            // receiver sudah dilepas
        }
        HidController.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
