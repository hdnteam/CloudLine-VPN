package com.hdnteam.cloudlinevpn.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.hdnteam.cloudlinevpn.worker.AppUpdateWorker
import com.hdnteam.cloudlinevpn.worker.SubscriptionUpdateWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val workManager = WorkManager.getInstance(context)
            SubscriptionUpdateWorker.schedule(workManager)
            AppUpdateWorker.schedule(workManager)
        }
    }
}
