package org.edi.spack.wakeonlan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences(
            PREFERENCES_FILE,
            Context.MODE_PRIVATE
        )
        val shouldStartOnBoot = sharedPrefs.getBoolean(START_ON_BOOT_PREF, false)

//        Toast.makeText(context, "WoL should start on boot: $shouldStartOnBoot", Toast.LENGTH_LONG).show()

        if (shouldStartOnBoot) {
            if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
                val serviceIntent = Intent(context, WakeOnLanService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
