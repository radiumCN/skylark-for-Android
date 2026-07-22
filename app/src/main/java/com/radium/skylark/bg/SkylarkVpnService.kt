package com.radium.skylark.bg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.radium.skylark.MainActivity
import com.radium.skylark.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * VPN 前台服务。
 *
 * M1：打通「启动前台服务 → 启动内核（桩）→ 更新连接状态」流程。
 * 接入 sing-box 后，在 [openTun] 中用 `VpnService.Builder` 建立 TUN 并把 fd 交给内核。
 */
@AndroidEntryPoint
class SkylarkVpnService : VpnService() {

    @Inject lateinit var controller: VpnController
    @Inject lateinit var core: ProxyCore
    @Inject lateinit var log: LogRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn(intent.getStringExtra(EXTRA_CONFIG).orEmpty())
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(configJson: String) {
        log.i(TAG, "收到启动指令，准备连接")
        controller.updateState(ConnectionState.Connecting)
        startAsForeground()
        scope.launch {
            runCatching {
                // TODO(libbox)：在此调用 openTun() 建立 TUN，并把 fd 传给真实内核。
                core.start(configJson, tunFd = null)
            }.onSuccess {
                controller.updateState(ConnectionState.Connected(System.currentTimeMillis()))
                log.i(TAG, "已连接")
            }.onFailure {
                log.e(TAG, "启动失败：${it.message}")
                controller.updateState(ConnectionState.Error(it.message ?: "启动失败"))
                stopSelf()
            }
        }
    }

    private fun stopVpn() {
        log.i(TAG, "收到停止指令")
        controller.updateState(ConnectionState.Stopping)
        scope.launch {
            runCatching { core.stop() }
            controller.updateState(ConnectionState.Disconnected)
            stopForegroundCompat()
            stopSelf()
            log.i(TAG, "已断开")
        }
    }

    /**
     * 建立 TUN 接口（接入内核后启用）。当前占位返回 -1。
     */
    @Suppress("unused")
    private fun openTun(): Int {
        // val builder = Builder()
        //     .setSession(getString(R.string.app_name))
        //     .addAddress("172.19.0.1", 30)
        //     .addRoute("0.0.0.0", 0)
        //     .addDnsServer("1.1.1.1")
        //     .setMtu(9000)
        // return builder.establish()?.detachFd() ?: -1
        return -1
    }

    private fun startAsForeground() {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("代理运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "代理服务",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.radium.skylark.action.START"
        const val ACTION_STOP = "com.radium.skylark.action.STOP"
        const val EXTRA_CONFIG = "config"
        private const val TAG = "VpnService"
        private const val CHANNEL_ID = "skylark_vpn"
        private const val NOTIFICATION_ID = 1001
    }
}
