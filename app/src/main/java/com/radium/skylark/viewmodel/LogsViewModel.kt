package com.radium.skylark.viewmodel

import androidx.lifecycle.ViewModel
import com.radium.skylark.bg.LogLevel
import com.radium.skylark.bg.LogLine
import com.radium.skylark.bg.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: LogRepository,
) : ViewModel() {

    val lines: StateFlow<List<LogLine>> = repository.lines

    /** 最低显示等级；null 表示全部。 */
    private val _minLevel = MutableStateFlow<LogLevel?>(null)
    val minLevel: StateFlow<LogLevel?> = _minLevel.asStateFlow()

    fun setMinLevel(level: LogLevel?) {
        _minLevel.value = level
    }

    fun clear() = repository.clear()
}
