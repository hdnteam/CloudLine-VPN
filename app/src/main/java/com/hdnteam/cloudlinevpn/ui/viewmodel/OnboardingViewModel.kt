package com.hdnteam.cloudlinevpn.ui.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdnteam.cloudlinevpn.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val subscriptionRepository: SubscriptionRepository
) : AndroidViewModel(application) {

    companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    private val dataStore = application.dataStore

    /**
     * null  → still loading from DataStore
     * false → first launch, show onboarding
     * true  → onboarding complete, show main app
     */
    private val _onboardingDone = MutableStateFlow<Boolean?>(null)
    val onboardingDone: StateFlow<Boolean?> = _onboardingDone

    init {
        viewModelScope.launch {
            dataStore.data
                .map { it[KEY_ONBOARDING_DONE] ?: false }
                .collect { _onboardingDone.value = it }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error

    fun submitSubscription(url: String, alias: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = ""
            try {
                val result = subscriptionRepository.addSubscription(url.trim(), alias.trim())
                _isLoading.value = false
                if (result.isSuccess) {
                    completeOnboarding()
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "خطا"
                    _error.value = "خطا: $errMsg\nاتصال اینترنت را بررسی کنید."
                }
            } catch (e: Throwable) {
                _isLoading.value = false
                _error.value = "خطای غیرمنتظره: ${e.message}"
            }
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch { completeOnboarding() }
    }

    private suspend fun completeOnboarding() {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }
}
