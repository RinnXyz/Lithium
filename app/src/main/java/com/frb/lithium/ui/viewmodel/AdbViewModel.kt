package com.rinn.lithium.ui.viewmodel

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rinn.engine.adb.AdbClient
import com.rinn.engine.adb.AdbKey
import com.rinn.engine.adb.AdbMdns
import com.rinn.engine.adb.AdbPairingService
import com.rinn.engine.adb.PreferenceAdbKeyStore
import com.rinn.engine.client.Lithium
import com.rinn.engine.core.LithiumSettings
import com.rinn.engine.core.Engine
import com.rinn.engine.data.LithiumInfo
import com.rinn.engine.utils.Starter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

const val TAG = "AdbViewModel"

class AdbViewModel : ViewModel() {

    var lithiumInfo by mutableStateOf(LithiumInfo())
        private set

    var isNotificationEnabled by mutableStateOf(false)
        private set

    var launchDevSettings by mutableStateOf(false)

    var tryActivate by mutableStateOf(false)
        private set

    var isUpdating by mutableStateOf(false)

    init {
        checkLithiumService()
    }

    fun checkLithiumService() {
        viewModelScope.launch {
            if (Lithium.pingBinder()) {
                tryActivate = false
                lithiumInfo = Lithium.getInfo()
            } else {
                if (isUpdating) return@launch
                lithiumInfo = LithiumInfo()
            }
        }
    }

    /**
     * Update state apakah notifikasi aktif atau tidak
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun updateNotificationState(context: Context) {
        viewModelScope.launch {
            isNotificationEnabled = checkNotificationEnabled(context)
        }
    }

    var adbMdns: AdbMdns? = null

    @RequiresApi(Build.VERSION_CODES.R)
    fun startAdb(context: Context, tryConnect: Boolean = false): Boolean =
        runBlocking(Dispatchers.IO) {
            if (tryActivate) return@runBlocking true
            tryActivate = true

            val cr = Engine.application.contentResolver
            if (Engine.application.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
                LithiumSettings.getLastLaunchMode() == LithiumSettings.LaunchMethod.ADB
            ) {
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            }

            val adbWifiEnabled = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1

            if (!adbWifiEnabled && !tryConnect) {
                tryActivate = false
                launchDevSettings = true
                startPairingService(context)
                return@runBlocking false
            }

            AdbMdns(
                context,
                AdbMdns.TLS_CONNECT
            ) { data ->
                Log.d(TAG, "AdbMdns ${data.host} ${data.port}")
                AdbClient(
                    data.host,
                    data.port,
                    AdbKey(PreferenceAdbKeyStore(LithiumSettings.getPreferences()), "lithium")
                ).runCatching {
                    Log.d(TAG, "AdbClient running")
                    connect()
                    shellCommand(Starter.internalCommand, null)
                    close()
                }.onSuccess {
                    Log.d(TAG, "AdbClient success")
                    LithiumSettings.setLastLaunchMode(LithiumSettings.LaunchMethod.ADB)
                }.onFailure {
                    Log.e(TAG, "AdbClient failed", it)
                    it.printStackTrace()
                    if (!tryConnect) {
                        launchDevSettings = true
                        startPairingService(context)
                        adbMdns?.run {
                            this.stop()
                        }
                    }
                }

                tryActivate = false
            }.runCatching {
                Log.d(TAG, "AdbMdns running")
                adbMdns = this
                adbMdns?.run {
                    this.start()
                }
            }.onFailure {
                Log.e(TAG, "AdbMdns failed", it)
                it.printStackTrace()
                if (!tryConnect) {
                    launchDevSettings = true
                    startPairingService(context)
                    adbMdns?.run {
                        this.stop()
                    }
                }
            }
            tryActivate = false
            return@runBlocking false
        }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startPairingService(context: Context) {
        viewModelScope.launch {
            if (!isNotificationEnabled) return@launch
            val intent = AdbPairingService.startIntent(context)
            try {
                context.startForegroundService(intent)
            } catch (e: Throwable) {
                Log.e("lithium", "startForegroundService", e)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && e is ForegroundServiceStartNotAllowedException
                ) {
                    val mode = context.getSystemService(AppOpsManager::class.java)
                        .noteOpNoThrow(
                            "android:start_foreground",
                            android.os.Process.myUid(),
                            context.packageName,
                            null,
                            null
                        )
                    if (mode == AppOpsManager.MODE_ERRORED) {
                        Toast.makeText(
                            context,
                            "OP_START_FOREGROUND is denied. What are you doing?",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    context.startService(intent)
                }
            }
        }
    }


    /**
     * Cek notifikasi aktif atau tidak
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkNotificationEnabled(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.notificationChannel)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }
}