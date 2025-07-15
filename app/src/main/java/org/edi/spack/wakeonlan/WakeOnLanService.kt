package org.edi.spack.wakeonlan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Timer
import java.util.TimerTask

const val PREFERENCES_FILE = "org.edi.spack.wakeonlan.PREFERENCES"
const val START_ON_BOOT_PREF = "start_on_boot"
const val IP_PREF = "ip"
const val MAC_PREF = "mac"
const val PORT_PREF = "port"
const val SECONDS_PREF = "seconds"

const val ACTION_SERVICE_STATE_CHANGED = "org.edi.spack.wakeonlan.SERVICE_STATE_CHANGED"
const val EXTRA_IS_RUNNING = "is_running"

class WakeOnLanService : Service() {

    companion object {
        private val _isServiceRunning = MutableLiveData<Boolean>()
        val isServiceRunning: LiveData<Boolean> = _isServiceRunning
    }

    private var timer: Timer? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTimer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startTimer() {
        val sharedPrefs = this.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        val ip = sharedPrefs.getString(IP_PREF, null)
        val mac = sharedPrefs.getString(MAC_PREF, null)
        val port = sharedPrefs.getInt(PORT_PREF, 9)
        val seconds = sharedPrefs.getLong(SECONDS_PREF, 60)

        if (
            ip.isNullOrEmpty() ||
            mac.isNullOrEmpty() ||
            !sharedPrefs.contains(PORT_PREF) ||
            !sharedPrefs.contains(SECONDS_PREF)
        ) {
            Toast.makeText(this, "All fields must be set", Toast.LENGTH_SHORT).show()

            return
        }

        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                sendWakeOnLanPacket(ip, mac, port)
            }
        }, 0, seconds * 1000)

        _isServiceRunning.postValue(true)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null

        _isServiceRunning.postValue(false)
    }

    private fun sendWakeOnLanPacket(ip: String, mac: String, port: Int) {
        try {
            val macBytes = getMacBytes(mac)
            val bytes = ByteArray(6 + 16 * macBytes.size)
            for (i in 0..5) {
                bytes[i] = 0xff.toByte()
            }
            var i = 6
            while (i < bytes.size) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                i += macBytes.size
            }

            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(bytes, bytes.size, address, port)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun getMacBytes(macStr: String): ByteArray {
        val bytes = ByteArray(6)
        val hex = macStr.split("([:-])".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (hex.size != 6) {
            throw IllegalArgumentException("Invalid MAC address.")
        }
        try {
            for (i in 0..5) {
                bytes[i] = hex[i].toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid hex digit in MAC address.")
        }
        return bytes
    }

    private fun startForegroundService() {
        val channelId = "wake_on_lan_channel"
        val channelName = "Wake On LAN Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Wake on LAN Service Running")
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(1, notification)
    }
}
