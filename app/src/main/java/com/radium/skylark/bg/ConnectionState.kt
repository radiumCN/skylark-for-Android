package com.radium.skylark.bg

/**
 * 代理连接状态。
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState

    /** @param since 连接建立时间戳（毫秒） */
    data class Connected(val since: Long) : ConnectionState

    data object Stopping : ConnectionState
    data class Error(val message: String) : ConnectionState

    val isActive: Boolean
        get() = this is Connecting || this is Connected
}
