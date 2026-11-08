/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.background.watchdog

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import org.breezyweather.R
import org.breezyweather.common.extensions.notificationBuilder
import org.breezyweather.common.extensions.setForegroundSafely
import org.breezyweather.common.extensions.workManager
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.remoteviews.Notifications
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * The only watchdog mechanism on most devices, and the backup for the alarm chain on Xiaomi family
 * devices, where it also re-arms a dead chain. Checks WeatherUpdateJob health on each run.
 */
class WatchdogRestartWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!SettingsManager.getInstance(applicationContext).watchdogEnabled) {
            Log.d(TAG, "Watchdog disabled — skipping restart check")
            return Result.success()
        }

        val context = applicationContext
        val prefs = context.getSharedPreferences(WatchdogWeatherUpdateHealth.PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // Promote to foreground for the brief execution window so HyperOS doesn't kill us mid-check
        if (WatchdogService.isXiaomiDevice()) setForegroundSafely()

        if (WatchdogService.usesAlarmChain(context)) {
            // A dead chain otherwise stays dead until reboot. The flag covers a chain that only
            // just became usable, such as after the battery exemption is granted.
            if (!prefs.getBoolean("alarm_armed", false) ||
                WatchdogWeatherUpdateHealth.isCheckOverdue(now, prefs.getLong("next_alarm_timestamp", 0L), true)
            ) {
                WatchdogService.scheduleNextAlarm(context)
            }
        } else {
            prefs.edit()
                .putLong("next_alarm_timestamp", now + periodMinutes().minutes.inWholeMilliseconds)
                .putBoolean("alarm_armed", false)
                .apply()
        }

        val jobStatus = WatchdogWeatherUpdateHealth.ensureHealthy(context)
        Log.d(TAG, "Watchdog weather health result: $jobStatus")

        return Result.success()
    }

    /**
     * ForegroundInfo for the brief execution window.
     * Uses ID_WATCHDOG_KEEPALIVE (not ID_WIDGET) so WorkManager's stop-foreground
     * call cancels this temporary notification without touching the weather notification.
     * CHANNEL_WATCHDOG is IMPORTANCE_MIN/LOW — not user-visible on most devices.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_WATCHDOG) {
            setSmallIcon(R.drawable.ic_running_in_background)
            setContentTitle(applicationContext.getString(R.string.notification_running_in_background))
            setOngoing(true)
            setShowWhen(false)
            priority = NotificationCompat.PRIORITY_MIN
        }.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                Notifications.ID_WATCHDOG_KEEPALIVE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        } else {
            ForegroundInfo(Notifications.ID_WATCHDOG_KEEPALIVE, notification)
        }
    }

    companion object {
        private const val TAG = "WatchdogRestart"
        const val WORK_NAME = "WatchdogRestart"

        internal fun periodMinutes(): Long = if (WatchdogService.isXiaomiDevice()) 30L else 60L

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogRestartWorker>(
                periodMinutes(), TimeUnit.MINUTES
            ).build()
            context.workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE moves upgraded installs off the old 30-minute period
                if (WatchdogService.isXiaomiDevice()) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Enqueued periodic restart check (${periodMinutes()}min)")
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic restart check")
        }
    }
}
