package com.radium.skylark.bg

/**
 * 代理内核抽象。
 *
 * 当前由 [StubProxyCore] 提供桩实现；接入 sing-box 后由基于 `libbox` 的
 * 实现替换（在 [SkylarkVpnService.openTun] 中把 TUN fd 交给内核）。
 */
interface ProxyCore {
    val isRunning: Boolean

    /**
     * 启动内核。
     * @param configJson sing-box 配置（由 ConfigBuilder 生成）
     * @param tunFd VpnService 建立的 TUN 文件描述符；桩实现可忽略
     */
    suspend fun start(configJson: String, tunFd: Int?)

    suspend fun stop()
}
