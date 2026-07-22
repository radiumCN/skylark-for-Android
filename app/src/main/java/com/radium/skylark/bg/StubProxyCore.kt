package com.radium.skylark.bg

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * 桩内核：在接入 `libbox.aar` 前用于打通连接流程与 UI。
 * 不建立真实代理，仅模拟启动/停止与运行状态。
 */
@Singleton
class StubProxyCore @Inject constructor(
    private val log: LogRepository,
) : ProxyCore {

    @Volatile
    override var isRunning: Boolean = false
        private set

    override suspend fun start(configJson: String, tunFd: Int?) {
        log.i(TAG, "启动内核（桩实现）：配置长度=${configJson.length}，tunFd=$tunFd")
        log.w(TAG, "当前为桩内核，未建立真实代理；接入 libbox.aar 后生效")
        // 模拟内核启动耗时
        delay(400)
        isRunning = true
        log.i(TAG, "内核已就绪")
    }

    override suspend fun stop() {
        log.i(TAG, "停止内核")
        isRunning = false
    }

    private companion object {
        const val TAG = "Core"
    }
}
