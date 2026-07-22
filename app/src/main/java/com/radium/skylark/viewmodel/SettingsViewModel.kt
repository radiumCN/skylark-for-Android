package com.radium.skylark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radium.skylark.BuildConfig
import com.radium.skylark.data.datastore.SettingsStore
import com.radium.skylark.update.UpdateChannel
import com.radium.skylark.update.UpdateChecker
import com.radium.skylark.update.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    val channel: StateFlow<UpdateChannel> =
        settingsStore.updateChannel
            .map { it ?: UpdateChannel.default(versionName) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UpdateChannel.default(versionName),
            )

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _result = MutableStateFlow<UpdateResult?>(null)
    val result: StateFlow<UpdateResult?> = _result.asStateFlow()

    fun setChannel(channel: UpdateChannel) = viewModelScope.launch {
        settingsStore.setUpdateChannel(channel)
        _result.value = null
    }

    fun checkForUpdate() = viewModelScope.launch {
        if (_checking.value) return@launch
        _checking.value = true
        _result.value = null
        _result.value = updateChecker.check(channel.value, versionName)
        _checking.value = false
    }

    fun clearResult() {
        _result.value = null
    }
}
