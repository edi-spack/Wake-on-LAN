package org.edi.spack.wakeonlan

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import org.edi.spack.wakeonlan.ui.theme.WakeOnLANTheme

const val PARTIAL_IP_REGEX =
    "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.){0,3}([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])?\$"
const val FULL_IP_REGEX =
    "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\$"
const val PARTIAL_MAC_REGEX = "^([0-9A-Fa-f]{1,2}:){0,5}[0-9A-Fa-f]{0,2}\$"
const val FULL_MAC_REGEX = "^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})\$"
const val PORT_REGEX =
    "^[1-9]|[1-5]?[0-9]{2,4}|6[1-4][0-9]{3}|65[1-4][0-9]{2}|655[1-2][0-9]|6553[1-5]\$"

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        }

        val sharedPrefs = getSharedPreferences(
            PREFERENCES_FILE, Context.MODE_PRIVATE
        )

        enableEdgeToEdge()
        setContent {
            val isServiceRunning by WakeOnLanService.isServiceRunning.observeAsState(initial = false)
            var ipTextFieldValue by remember {
                mutableStateOf(
                    sharedPrefs.getString(
                        IP_PREF, ""
                    )!!
                )
            }
            var macTextFieldValue by remember {
                mutableStateOf(
                    sharedPrefs.getString(
                        MAC_PREF, ""
                    )!!
                )
            }
            var portTextFieldValue by remember {
                mutableStateOf(
                    sharedPrefs.getInt(PORT_PREF, 9).toString()
                )
            }
            var secondsTextFieldValue by remember {
                mutableStateOf(
                    sharedPrefs.getLong(
                        SECONDS_PREF, 60L
                    ).toString()
                )
            }

            fun shouldStartButtonBeEnabled(): Boolean {
                val ipIsValid = ipTextFieldValue.matches(Regex(FULL_IP_REGEX))
                val macIsValid = macTextFieldValue.matches(Regex(FULL_MAC_REGEX))
                val portIsValid = portTextFieldValue.matches(Regex(PORT_REGEX))
                val secondsIsValid = try {
                    secondsTextFieldValue.toLong() >= 1
                } catch (e: NumberFormatException) {
                    false
                }

                return ipIsValid && macIsValid && portIsValid && secondsIsValid
            }

            var isStartButtonEnabled by remember { mutableStateOf(shouldStartButtonBeEnabled()) }

            WakeOnLANTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(), topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Wake On LAN ${
                                        if (isServiceRunning) {
                                            "(running)"
                                        } else {
                                            "(stopped)"
                                        }
                                    }"
                                )
                            })
                    }) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(
                            16.dp, Alignment.CenterVertically
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextField(
                            label = { Text("IP Address") },
                            value = ipTextFieldValue,
                            onValueChange = { ip: String ->
                                if (ip.matches(Regex(PARTIAL_IP_REGEX))) {
                                    ipTextFieldValue = ip
                                    isStartButtonEnabled = shouldStartButtonBeEnabled()
                                }
                            },
                            placeholder = { Text("Ex. 192.168.0.100") })
                        TextField(
                            label = { Text("MAC Address") },
                            value = macTextFieldValue,
                            onValueChange = { mac: String ->
                                if (mac.matches(Regex(PARTIAL_MAC_REGEX))) {
                                    macTextFieldValue = mac.uppercase()
                                    isStartButtonEnabled = shouldStartButtonBeEnabled()
                                }
                            },
                            placeholder = { Text("Ex. 12:34:56:78:9A:BC") })
                        TextField(
                            label = { Text("Port") },
                            value = portTextFieldValue,
                            onValueChange = { port: String ->
                                if (port.isEmpty() || port.matches(Regex(PORT_REGEX))) {
                                    portTextFieldValue = port
                                    isStartButtonEnabled = shouldStartButtonBeEnabled()
                                }
                            },
                            placeholder = { Text("Default port is 9") })
                        TextField(
                            label = { Text("Interval (seconds)") },
                            value = secondsTextFieldValue,
                            onValueChange = { seconds: String ->
                                if (seconds.isEmpty() || try {
                                        seconds.toInt() >= 1
                                    } catch (e: NumberFormatException) {
                                        false
                                    }
                                ) {
                                    secondsTextFieldValue = seconds
                                    isStartButtonEnabled = shouldStartButtonBeEnabled()
                                }
                            },
                            placeholder = { Text("At least 1 second") })
                        Button(
                            onClick = {
                                val serviceIntent = Intent(
                                    this@MainActivity, WakeOnLanService::class.java
                                )

                                if (isServiceRunning) {
                                    this@MainActivity.stopService(serviceIntent)
                                } else {
                                    sharedPrefs.edit {
                                        putString(IP_PREF, ipTextFieldValue)
                                        putString(MAC_PREF, macTextFieldValue)
                                        putInt(PORT_PREF, portTextFieldValue.toInt())
                                        putLong(SECONDS_PREF, secondsTextFieldValue.toLong())
                                    }

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        this@MainActivity.startForegroundService(
                                            serviceIntent
                                        )
                                    } else {
                                        this@MainActivity.startService(serviceIntent)
                                    }
                                }
                            }, enabled = isStartButtonEnabled
                        ) {
                            Text(
                                text = if (isServiceRunning) {
                                    "STOP"
                                } else {
                                    "START"
                                }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            var startOnBoot by remember {
                                mutableStateOf(
                                    sharedPrefs.getBoolean(
                                        START_ON_BOOT_PREF, false
                                    )
                                )
                            }

                            Checkbox(
                                checked = startOnBoot, onCheckedChange = {
                                    sharedPrefs.edit { putBoolean(START_ON_BOOT_PREF, it) }
                                    startOnBoot = it
                                })
                            Text(
                                "Start on boot"
                            )
                        }
                    }
                }
            }
        }
    }
}
