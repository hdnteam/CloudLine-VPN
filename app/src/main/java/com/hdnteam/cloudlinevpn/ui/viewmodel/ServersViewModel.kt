package com.hdnteam.cloudlinevpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.data.repository.SubscriptionRepository
import com.hdnteam.cloudlinevpn.vpn.LatencyTester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    val servers: StateFlow<List<ServerConfig>> = subscriptionRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions = subscriptionRepository.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Servers grouped by subscriptionId
    val groupedServers: StateFlow<Map<Long, List<ServerConfig>>> = subscriptionRepository.servers
        .map { list -> list.groupBy { it.subscriptionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _testingIds = MutableStateFlow<Set<Long>>(emptySet())
    val testingIds: StateFlow<Set<Long>> = _testingIds

    private val _isTestingAll = MutableStateFlow(false)
    val isTestingAll: StateFlow<Boolean> = _isTestingAll

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    fun testLatency(server: ServerConfig) {
        viewModelScope.launch {
            _testingIds.value = _testingIds.value + server.id
            val latency = LatencyTester.testServer(server.address, server.port)
            subscriptionRepository.updateLatency(server.id, latency)
            _testingIds.value = _testingIds.value - server.id
        }
    }

    fun testAllLatency() {
        viewModelScope.launch {
            _isTestingAll.value = true
            val list = servers.value
            list.map { server ->
                async {
                    _testingIds.value = _testingIds.value + server.id
                    val latency = LatencyTester.testServer(server.address, server.port)
                    subscriptionRepository.updateLatency(server.id, latency)
                    _testingIds.value = _testingIds.value - server.id
                }
            }.awaitAll()
            _isTestingAll.value = false
        }
    }

    fun refreshServers() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _statusMessage.value = "در حال بروزرسانی…"
            try {
                val subs = subscriptionRepository.subscriptions.first()
                var failed = 0
                subs.forEach { sub ->
                    val result = subscriptionRepository.fetchAndUpdate(sub)
                    if (result.isFailure) failed++
                }
                _statusMessage.value = if (failed == 0)
                    "سرورها بروزرسانی شدند (${servers.value.size} سرور)"
                else
                    "$failed اشتراک بروزرسانی نشد"
            } catch (e: Exception) {
                _statusMessage.value = "خطا: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectServer(server: ServerConfig) {
        viewModelScope.launch {
            subscriptionRepository.selectServer(server.id)
        }
    }

    fun updateServer(server: ServerConfig) {
        viewModelScope.launch {
            subscriptionRepository.updateServer(server)
        }
    }

    fun clearStatusMessage() { _statusMessage.value = "" }
}
