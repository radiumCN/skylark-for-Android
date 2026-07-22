package com.radium.skylark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radium.skylark.bg.ConnectionState
import com.radium.skylark.bg.VpnController
import com.radium.skylark.converter.ConfigBuilder
import com.radium.skylark.data.datastore.SelectionStore
import com.radium.skylark.data.db.ProfileEntity
import com.radium.skylark.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val controller: VpnController,
    selectionStore: SelectionStore,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = controller.state

    /** 当前生效的订阅：显式选中优先，否则回退到第一个。 */
    val selectedProfile: StateFlow<ProfileEntity?> =
        combine(
            repository.observeProfiles(),
            selectionStore.selectedProfileId,
        ) { profiles, selectedId ->
            profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun connect() {
        val profile = selectedProfile.value
        if (profile == null) {
            _message.value = "请先在「订阅」页添加并选择一个订阅"
            return
        }
        viewModelScope.launch {
            val configJson = runCatching {
                withContext(Dispatchers.Default) {
                    val parsed = repository.parse(profile)
                    if (parsed.nodes.isEmpty()) error("该订阅未解析出任何节点")
                    ConfigBuilder.buildJsonString(parsed)
                }
            }.getOrElse {
                _message.value = "配置生成失败：${it.message}"
                return@launch
            }
            controller.start(configJson)
        }
    }

    fun disconnect() {
        controller.stop()
    }

    fun clearMessage() {
        _message.value = null
    }
}
