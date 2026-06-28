package com.hdnteam.cloudlinevpn.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdnteam.cloudlinevpn.data.model.Subscription
import com.hdnteam.cloudlinevpn.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    val subscriptions: StateFlow<List<Subscription>> = subscriptionRepository.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primarySubscription: StateFlow<Subscription?> = subscriptions
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Returns days remaining, or -1 if no expiry set */
    fun getDaysRemaining(sub: Subscription): Long {
        if (sub.expireTimestamp <= 0L) return -1L
        val nowSec = System.currentTimeMillis() / 1000L
        val diffSec = sub.expireTimestamp - nowSec
        return max(0L, TimeUnit.SECONDS.toDays(diffSec))
    }

    fun getUsedBytes(sub: Subscription): Long = sub.uploadBytes + sub.downloadBytes

    fun getUsagePercent(sub: Subscription): Float {
        if (sub.totalBytes <= 0L) return 0f
        return (getUsedBytes(sub).toFloat() / sub.totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0L             -> "0 B"
        bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
        else                    -> "$bytes B"
    }

    fun refresh() {
        viewModelScope.launch {
            subscriptions.value.forEach { sub ->
                subscriptionRepository.fetchAndUpdate(sub)
            }
        }
    }

    fun addSubscription(url: String, alias: String = "") {
        viewModelScope.launch {
            _message.value = "در حال دریافت سرورها…"
            val result = subscriptionRepository.addSubscription(url.trim(), alias.trim())
            _message.value = if (result.isSuccess) "اشتراک با موفقیت اضافه شد ✓"
            else "خطا: ${result.exceptionOrNull()?.message ?: "دوباره تلاش کنید"}"
        }
    }

    fun deleteSubscription(sub: Subscription) {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(sub)
            _message.value = "اشتراک حذف شد"
        }
    }

    fun clearMessage() { _message.value = "" }

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message
}
