package com.hdnteam.cloudlinevpn.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String = "",
    val alias: String = "",
    val username: String = "",
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = 0,
    val expireTimestamp: Long = 0,  // Unix timestamp seconds
    val lastUpdated: Long = 0,
    val serverCount: Int = 0,
    val isActive: Boolean = true
)
