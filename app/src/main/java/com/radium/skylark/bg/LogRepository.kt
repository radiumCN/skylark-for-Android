package com.radium.skylark.bg

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogLine(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * 应用内日志仓库：环形缓冲，供日志页展示。
 *
 * 当前由服务/内核（桩）的生命周期事件写入；接入真实 `libbox` 后，
 * 内核日志回调调用 [append] 即可复用同一展示层。
 */
@Singleton
class LogRepository @Inject constructor() {

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val idGen = AtomicLong(0)

    @Synchronized
    fun append(level: LogLevel, tag: String, message: String) {
        val line = LogLine(
            id = idGen.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        val current = _lines.value
        val next = if (current.size >= MAX_LINES) {
            current.subList(current.size - MAX_LINES + 1, current.size) + line
        } else {
            current + line
        }
        _lines.value = next
    }

    fun d(tag: String, message: String) = append(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = append(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = append(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = append(LogLevel.ERROR, tag, message)

    fun clear() {
        _lines.value = emptyList()
    }

    private companion object {
        const val MAX_LINES = 800
    }
}
