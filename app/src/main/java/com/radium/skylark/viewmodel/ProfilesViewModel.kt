package com.radium.skylark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radium.skylark.data.datastore.SelectionStore
import com.radium.skylark.data.db.ProfileEntity
import com.radium.skylark.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> =
        repository.observeProfiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedProfileId: StateFlow<Long?> =
        selectionStore.selectedProfileId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(profile: ProfileEntity) = viewModelScope.launch {
        selectionStore.selectProfile(profile.id)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val busy: Boolean = false,
        val message: String? = null,
    )

    fun addFromUrl(name: String, url: String) = launchGuarded {
        repository.addFromUrl(name, url)
        setMessage("已添加订阅")
    }

    fun addFromText(name: String, text: String) = launchGuarded {
        repository.addFromText(name, text)
        setMessage("已导入配置")
    }

    fun refresh(profile: ProfileEntity) = launchGuarded {
        repository.refresh(profile.id)
        setMessage("已刷新：${profile.name}")
    }

    fun delete(profile: ProfileEntity) = launchGuarded {
        repository.delete(profile)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun setMessage(msg: String) {
        _uiState.value = _uiState.value.copy(message = msg)
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, message = null)
            runCatching { block() }
                .onFailure { _uiState.value = _uiState.value.copy(message = "失败：${it.message}") }
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }
}
