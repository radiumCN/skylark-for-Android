package com.radium.skylark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radium.skylark.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    data class NodeItem(
        val tag: String,
        val protocol: String,
        val server: String,
        val serverPort: Int,
        val profileName: String,
    )

    val nodes: StateFlow<List<NodeItem>> =
        repository.observeProfiles()
            .map { profiles ->
                profiles.flatMap { profile ->
                    repository.parse(profile).nodes.map { node ->
                        NodeItem(
                            tag = node.tag,
                            protocol = node::class.simpleName ?: "?",
                            server = node.server,
                            serverPort = node.serverPort,
                            profileName = profile.name,
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
