package com.hdnteam.cloudlinevpn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import com.hdnteam.cloudlinevpn.data.model.Subscription

@Database(
    entities = [ServerConfig::class, Subscription::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val DATABASE_NAME = "cloudline_vpn.db"
    }
}
