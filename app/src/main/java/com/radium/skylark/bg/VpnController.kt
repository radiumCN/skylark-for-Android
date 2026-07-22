package com.radium.skylark.bg

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 连接控制器：连接状态的唯一来源，并向 [SkylarkVpnService] 下发启动/停止指令。
 */
@Singleton
class VpnController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun start(configJson: String) {
        val intent = Intent(context, SkylarkVpnService::class.java).apply {
            action = SkylarkVpnService.ACTION_START
            putExtra(SkylarkVpnService.EXTRA_CONFIG, configJson)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop() {
        val intent = Intent(context, SkylarkVpnService::class.java).apply {
            action = SkylarkVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /** 由服务回调更新状态。 */
    fun updateState(state: ConnectionState) {
        _state.value = state
    }
}
