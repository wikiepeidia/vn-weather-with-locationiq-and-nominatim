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

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import org.breezyweather.R
import org.breezyweather.common.extensions.notificationBuilder
import org.breezyweather.common.extensions.powerManager
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.remoteviews.Notifications
import kotlin.time.Duration.Companion.minutes

/**
 * Short-lived foreground service for the alarm-driven heartbeat on Xiaomi/Redmi/POCO, where
 * WorkManager periodic jobs can get silently killed. It checks WeatherUpdateJob health once and exits.
 *
 * Every other device relies on [WatchdogRestartWorker] alone: alarms and foreground services can't
 * beat Samsung's sleep tiers or Doze, so they would only cost wakeups.
 */
class WatchdogService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WatchdogService onStartCommand")

        // RESTART-02/03: Track restart count and source
        val source = intent?.getStringExtra(EXTRA_RESTART_SOURCE) ?: "sticky"
        incrementRestartCount(source)

        // Promote to foreground immediately — Android requires this within 5 seconds.
        // Never piggyback on the weather notification ID here: OEMs such as MIUI/HyperOS can
        // later treat that notification as service-owned and clear the user's persistent weather
        // notification when the short-lived watchdog service exits.
        val notification = getForegroundNotification()
        if (!startWatchdogForeground(notification, source)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // HEART-01: Acquire WakeLock to prevent CPU sleep during heartbeat.
        // Run all blocking I/O on a background thread — workManager.getWorkInfos().get() can
        // block for several seconds on cold WorkManager init (Room DB on HyperOS cold-start),
        // which would ANR the main thread if left here.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BreezyWeather:WatchdogHeartbeat"
        )
        wakeLock.acquire(30_000L) // 30-second timeout
        Thread {
            try {
                val jobStatus = WatchdogWeatherUpdateHealth.ensureHealthy(this)
                Log.d(TAG, "Watchdog weather health result: $jobStatus")
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog heartbeat failed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                stopSelf()
            }
        }.start()

        return START_NOT_STICKY
    }

    // shortService gets about 3 minutes; not stopping after the timeout is an ANR
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Explicitly tear down foreground state so the temporary watchdog notification follows
        // the short-service lifecycle instead of lingering until the system cleans it up.
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "WatchdogService destroyed")
        // The next alarm is armed in start() and must survive this: it is the heartbeat loop.
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Gets the notification for startForeground().
     *
     * Always uses a dedicated watchdog notification so the persistent weather
     * notification never becomes bound to the watchdog foreground-service lifecycle.
     */
    private fun getForegroundNotification(): Notification {
        return notificationBuilder(Notifications.CHANNEL_WATCHDOG) {
            setSmallIcon(R.drawable.ic_running_in_background)
            setContentTitle(getString(R.string.watchdog_notification_title))
            setContentText(getString(R.string.notification_running_in_background))
            setOngoing(true)
            setShowWhen(false)
            priority = if (isXiaomiDevice()) {
                NotificationCompat.PRIORITY_LOW
            } else {
                NotificationCompat.PRIORITY_MIN
            }
        }.build()
    }

    private fun startWatchdogForeground(
        notification: Notification,
        source: String,
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Notifications.ID_WATCHDOG_KEEPALIVE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                )
            } else {
                startForeground(Notifications.ID_WATCHDOG_KEEPALIVE, notification)
            }
            true
        } catch (e: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (API 31+)
            Log.w(TAG, "Watchdog foreground promotion failed (source=$source): ${e.message}")
            false
        }
    }

    /**
     * RESTART-02: Increments cumulative restart count and records source.
     * Data persists across process death in SharedPreferences.
     */
    private fun incrementRestartCount(source: String) {
        val prefs = getSharedPreferences(WatchdogWeatherUpdateHealth.PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("restart_count", 0) + 1
        prefs.edit()
            .putInt("restart_count", count)
            .putString("last_restart_source", source)
            .putLong("last_restart_timestamp", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Restart #$count (source: $source)")
    }

    companion object {
        private const val TAG = "WatchdogService"

        private const val REQUEST_CODE_WATCHDOG_ALARM = 1001
        internal const val EXTRA_RESTART_SOURCE = "restart_source"

        /**
         * PROC-02: Checks if the device manufacturer is Xiaomi, Redmi, or POCO.
         */
        internal fun isXiaomiDevice(): Boolean {
            return Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco")
        }

        internal fun usesAlarmChain(context: Context): Boolean {
            return WatchdogWeatherUpdateHealth.usesAlarmChain(
                isXiaomi = isXiaomiDevice(),
                sdkInt = Build.VERSION.SDK_INT,
                ignoringBatteryOptimizations = context.powerManager.isIgnoringBatteryOptimizations(context.packageName),
                updatesEnabled = SettingsManager.getInstance(context).updateInterval.interval != null,
            )
        }

        /**
         * Arms the next heartbeat. No-op unless the alarm chain applies: without the exact alarm's
         * foreground-service exemption the alarm could not start the service anyway.
         */
        internal fun scheduleNextAlarm(context: Context) {
            if (!usesAlarmChain(context)) return
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intervalMs = SettingsManager.getInstance(context).watchdogHeartbeatInterval.minutes.inWholeMilliseconds

            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + intervalMs,
                    alarmPendingIntent(context)
                )
            } catch (e: SecurityException) {
                Log.d(TAG, "Exact alarm restricted, leaving the heartbeat to the worker: ${e.message}")
                return
            }

            // Persist wall-clock next alarm time for the Health Dashboard (HEALTH-01)
            context.getSharedPreferences(WatchdogWeatherUpdateHealth.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong("next_alarm_timestamp", System.currentTimeMillis() + intervalMs)
                .putBoolean("alarm_armed", true)
                .apply()
        }

        private fun alarmPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_WATCHDOG_ALARM,
                Intent(context, WatchdogAlarmReceiver::class.java).apply {
                    action = WatchdogAlarmReceiver.ACTION_WATCHDOG_ALARM
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Enqueues the WorkManager check and, on Xiaomi family devices only, runs a heartbeat.
         * With background updates off there is nothing to watch, so the check is cancelled instead.
         */
        fun start(context: Context, source: String = "manual") {
            if (SettingsManager.getInstance(context).updateInterval.interval == null) {
                WatchdogRestartWorker.cancel(context)
                return
            }
            WatchdogRestartWorker.enqueue(context)
            if (!isXiaomiDevice()) return

            // Armed first so a blocked foreground start or a dead process can't end the chain
            scheduleNextAlarm(context)
            val intent = Intent(context, WatchdogService::class.java).apply {
                putExtra(EXTRA_RESTART_SOURCE, source)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException (extends IllegalStateException, API 31+)
                // or SecurityException — HyperOS midnight-kill can leave the process in a
                // restricted background state where startForegroundService() is blocked.
                // The worker enqueued above still checks WeatherUpdateJob health.
                Log.w(TAG, "startForegroundService blocked (source=$source): ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        /**
         * Stops the WatchdogService, cancels the WorkManager backup, and cancels any pending alarm.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
            WatchdogRestartWorker.cancel(context)
            // Cancel alarm independently — covers case where service process was already killed
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val pi = alarmPendingIntent(context)
            am?.cancel(pi)
            pi.cancel()
            context.getSharedPreferences(WatchdogWeatherUpdateHealth.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove("next_alarm_timestamp")
                .remove("alarm_armed")
                .apply()
        }
    }
}
