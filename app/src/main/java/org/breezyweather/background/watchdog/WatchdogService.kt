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
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import org.breezyweather.R
import org.breezyweather.background.weather.WeatherUpdateJob
import org.breezyweather.common.extensions.notificationBuilder
import org.breezyweather.common.extensions.workManager
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.remoteviews.Notifications

/**
 * Persistent foreground service that monitors WeatherUpdateJob health.
 *
 * On HyperOS/MIUI, WorkManager periodic jobs get silently killed. This service:
 * 1. Runs as a foreground service with a persistent notification (ROM is less likely to kill it)
 * 2. Checks WeatherUpdateJob state every 15 minutes via AlarmManager heartbeat
 * 3. Re-enqueues WeatherUpdateJob if it's not RUNNING or ENQUEUED
 * 4. Self-heals via AlarmManager if the ROM kills the service process
 *
 * Phase 7 wires the ON/OFF toggle in Settings.
 * Phase 8 wires BootReceiver to restart this service on device reboot.
 */
class WatchdogService : Service() {

    private var alarmManager: AlarmManager? = null
    private var alarmPendingIntent: PendingIntent? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WatchdogService created")
        alarmManager = getSystemService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WatchdogService onStartCommand")

        // Promote to foreground immediately — Android requires this within 5 seconds
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_WATCHDOG_KEEPALIVE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifications.ID_WATCHDOG_KEEPALIVE, notification)
        }

        // Check job health and re-enqueue if dead
        performHeartbeat()

        // Arm next alarm for self-healing
        scheduleNextAlarm()

        // START_STICKY: OS restarts service with null intent if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WatchdogService destroyed")
        cancelAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Builds the keepalive notification showing relative time since last weather update.
     */
    private fun buildNotification(): Notification {
        val lastUpdate = SettingsManager.getInstance(this).weatherUpdateLastTimestamp
        val contentText = if (lastUpdate > 0) {
            getString(
                R.string.location_last_updated_x,
                DateUtils.getRelativeTimeSpanString(
                    lastUpdate,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            )
        } else {
            getString(R.string.notification_running_in_background)
        }

        return notificationBuilder(Notifications.CHANNEL_WATCHDOG) {
            setSmallIcon(R.drawable.ic_running_in_background)
            setContentTitle(getString(R.string.notification_channel_watchdog))
            setContentText(contentText)
            setOngoing(true)
            setShowWhen(false)
            priority = NotificationCompat.PRIORITY_MIN
        }.build()
    }

    /**
     * Refreshes the foreground notification with the latest "last updated" time.
     * Called after each heartbeat or when WeatherUpdateJob signals completion.
     */
    fun updateNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_WATCHDOG_KEEPALIVE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifications.ID_WATCHDOG_KEEPALIVE, notification)
        }
    }

    /**
     * Checks WeatherUpdateJob health; re-enqueues if not RUNNING or ENQUEUED.
     */
    private fun performHeartbeat() {
        Log.d(TAG, "Performing heartbeat check")

        val workQuery = WorkQuery.Builder
            .fromUniqueWorkNames(listOf(WeatherUpdateJob.WORK_NAME_AUTO))
            .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
            .build()

        val healthyWorks = workManager.getWorkInfos(workQuery).get()

        if (healthyWorks.isEmpty()) {
            Log.d(TAG, "Re-enqueued WeatherUpdateJob — was not found in RUNNING/ENQUEUED state")
            WeatherUpdateJob.setupTask(this)
        } else {
            Log.d(TAG, "WeatherUpdateJob is healthy (RUNNING or ENQUEUED)")
        }

        updateNotification()
    }

    /**
     * Arms the next AlarmManager alarm for self-healing after process kill.
     * Tries exact alarm first; falls back to inexact if restricted by ROM.
     */
    private fun scheduleNextAlarm() {
        val am = alarmManager ?: return

        val intent = Intent(this, WatchdogAlarmReceiver::class.java).apply {
            action = WatchdogAlarmReceiver.ACTION_WATCHDOG_ALARM
        }
        alarmPendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_WATCHDOG_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS

        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmPendingIntent!!
            )
            Log.d(TAG, "Scheduled exact alarm in ${HEARTBEAT_INTERVAL_MS / 1000 / 60} minutes")
        } catch (e: SecurityException) {
            Log.d(TAG, "setExactAndAllowWhileIdle restricted, falling back to setAndAllowWhileIdle: ${e.message}")
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmPendingIntent!!
            )
        }
    }

    private fun cancelAlarm() {
        alarmPendingIntent?.let { pendingIntent ->
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Watchdog alarm cancelled")
        }
        alarmPendingIntent = null
    }

    companion object {
        private const val TAG = "WatchdogService"

        private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L
        private const val REQUEST_CODE_WATCHDOG_ALARM = 1001

        /**
         * Starts the WatchdogService.
         * Called by Phase 7 Settings toggle or Phase 8 BootReceiver.
         */
        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the WatchdogService and cancels any pending alarm.
         * Defensively cancels the alarm PendingIntent even if the service
         * process was already killed (onDestroy may not have fired).
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
            // Cancel alarm independently — covers case where service process was already killed
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_WATCHDOG_ALARM,
                Intent(context, WatchdogAlarmReceiver::class.java).apply {
                    action = WatchdogAlarmReceiver.ACTION_WATCHDOG_ALARM
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am?.cancel(pi)
            pi.cancel()
        }
    }
}
