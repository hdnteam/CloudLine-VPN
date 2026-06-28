package com.hdnteam.cloudlinevpn.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.hdnteam.cloudlinevpn.R
import com.hdnteam.cloudlinevpn.ui.MainActivity
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Notification worker — sends 3 reminders per day (morning, noon, night) Iran time (UTC+3:30).
 * Schedule: ~9:00, ~14:00, ~21:00 Iran time.
 */
class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val WORK_MORNING = "notification_morning"
        private const val WORK_NOON    = "notification_noon"
        private const val WORK_NIGHT   = "notification_night"
        private const val CHANNEL_ID   = "cloudline_reminder"

        // Iran timezone: Asia/Tehran (UTC+3:30)
        private val IRAN_TZ = TimeZone.getTimeZone("Asia/Tehran")

        fun schedule(workManager: WorkManager) {
            // Schedule 3 one-time workers that repeat daily
            scheduleAt(workManager, WORK_MORNING, 9, 0)   // صبح ۹
            scheduleAt(workManager, WORK_NOON, 14, 0)     // ظهر ۱۴
            scheduleAt(workManager, WORK_NIGHT, 21, 0)    // شب ۲۱
        }

        private fun scheduleAt(workManager: WorkManager, tag: String, hourIran: Int, minuteIran: Int) {
            val delay = calculateDelay(hourIran, minuteIran)

            val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(tag)
                .build()

            workManager.enqueueUniquePeriodicWork(
                tag,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Calculate delay from now to the next occurrence of the given Iran time.
         */
        private fun calculateDelay(hourIran: Int, minuteIran: Int): Long {
            val now = Calendar.getInstance(IRAN_TZ)
            val target = Calendar.getInstance(IRAN_TZ).apply {
                set(Calendar.HOUR_OF_DAY, hourIran)
                set(Calendar.MINUTE, minuteIran)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If target time already passed today, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }
    }

    override fun doWork(): Result {
        createChannel()
        showNotification()
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "یادآوری CloudLine",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "یادآوری روزانه" }
            )
        }
    }

    private fun showNotification() {
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CloudLine VPN 💙")
            .setContentText("از خدمات ما راضی هستید؟ لطفاً ما را به دوستانتان پیشنهاد دهید\u200F🙏💙 رضایت شما افتخار ماست")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("از خدمات ما راضی هستید؟ لطفاً ما را به دوستانتان پیشنهاد دهید\u200F🙏💙 رضایت شما افتخار ماست"))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        // Use unique notification ID based on current hour to avoid overwriting
        val notifId = 2000 + Calendar.getInstance(IRAN_TZ).get(Calendar.HOUR_OF_DAY)
        nm.notify(notifId, notification)
    }
}
