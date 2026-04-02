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

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import org.json.JSONObject

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

    inner class LocalBinder : Binder() {
        fun getService(): WatchdogService = this@WatchdogService
    }

    private val binder = LocalBinder()

    private var alarmManager: AlarmManager? = null
    private var alarmPendingIntent: PendingIntent? = null
    private var serviceStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "WatchdogService created")
        alarmManager = getSystemService()
        serviceStartTime = SystemClock.elapsedRealtime()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WatchdogService onStartCommand")

        // RESTART-02/03: Track restart count and source
        val source = intent?.getStringExtra(EXTRA_RESTART_SOURCE) ?: "sticky"
        incrementRestartCount(source)

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

        // HEART-01: WakeLock prevents CPU sleep during heartbeat check
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BreezyWeather:WatchdogHeartbeat"
        )
        wakeLock.acquire(30_000L) // 30-second timeout
        try {
            performHeartbeat()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }

        // Arm next alarm for self-healing
        scheduleNextAlarm()

        // PROC-01/02: Launch invisible anchor Activity on Xiaomi to elevate OOM adj
        launchAnchorIfNeeded()

        // START_STICKY: OS restarts service with null intent if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "WatchdogService destroyed")
        cancelAlarm()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Builds the keepalive notification showing relative time since last weather update.
     */
    private fun buildNotification(): Notification {
        val settingsManager = SettingsManager.getInstance(this)
        val showNotification = settingsManager.watchdogNotificationVisible

        // When user hides notification, use absolute minimum content
        if (!showNotification) {
            return notificationBuilder(Notifications.CHANNEL_WATCHDOG) {
                setSmallIcon(R.drawable.ic_running_in_background)
                setOngoing(true)
                setShowWhen(false)
                priority = NotificationCompat.PRIORITY_MIN
            }.build()
        }

        val lastUpdate = settingsManager.weatherUpdateLastTimestamp
        val intervalMin = settingsManager.watchdogHeartbeatInterval

        // NOTIF-04: Show both "Last updated" and "Next refresh" timing
        val contentText = buildString {
            if (lastUpdate > 0) {
                append(getString(
                    R.string.location_last_updated_x,
                    DateUtils.getRelativeTimeSpanString(
                        lastUpdate,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                ))
                append(" · ")
                append(getString(R.string.watchdog_next_refresh, intervalMin))
            } else {
                append(getString(R.string.notification_running_in_background))
            }
        }

        // NOTIF-05: Elevate notification priority on Xiaomi/Redmi/POCO to reduce kill chance
        val notifPriority = if (isXiaomiDevice()) {
            NotificationCompat.PRIORITY_LOW
        } else {
            NotificationCompat.PRIORITY_MIN
        }

        return notificationBuilder(Notifications.CHANNEL_WATCHDOG) {
            setSmallIcon(R.drawable.ic_running_in_background)
            setContentTitle(getString(R.string.notification_channel_watchdog))
            setContentText(contentText)
            setOngoing(true)
            setShowWhen(false)
            priority = notifPriority
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

        val jobStatus: String
        if (healthyWorks.isEmpty()) {
            Log.d(TAG, "Re-enqueued WeatherUpdateJob — was not found in RUNNING/ENQUEUED state")
            WeatherUpdateJob.setupTask(this)
            jobStatus = "re-enqueued"
        } else {
            Log.d(TAG, "WeatherUpdateJob is healthy (RUNNING or ENQUEUED)")
            jobStatus = "healthy"
        }

        // HEART-03: Write diagnostic entry
        writeDiagnostic(jobStatus)

        updateNotification()

        // PROC-01: Re-launch anchor if system killed it between heartbeats
        launchAnchorIfNeeded()
    }

    /**
     * HEART-03: Writes timestamped diagnostic entry to SharedPreferences.
     * Data is read by the health dashboard (Phase 14).
     */
    private fun writeDiagnostic(jobStatus: String) {
        val prefs = getSharedPreferences("watchdog_diagnostics", Context.MODE_PRIVATE)
        val heartbeatCount = prefs.getInt("heartbeat_count", 0) + 1
        val uptimeMs = SystemClock.elapsedRealtime() - serviceStartTime

        val diagnostic = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("heartbeat_count", heartbeatCount)
            put("uptime_ms", uptimeMs)
            put("job_status", jobStatus)
            // PROC-03: Include process importance and anchor status on Xiaomi devices
            if (isXiaomiDevice()) {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val importance = am?.runningAppProcesses
                    ?.firstOrNull { it.pid == android.os.Process.myPid() }
                    ?.importance ?: -1
                put("process_importance", importance)
                put("anchor_active", WatchdogAnchorActivity.isActive)
            }
        }

        prefs.edit()
            .putString("last_diagnostic", diagnostic.toString())
            .putInt("heartbeat_count", heartbeatCount)
            .putLong("last_heartbeat_timestamp", System.currentTimeMillis())
            .apply()

        Log.d(TAG, "Diagnostic #$heartbeatCount: $diagnostic")
    }

    /**
     * RESTART-02: Increments cumulative restart count and records source.
     * Data persists across process death in SharedPreferences.
     */
    private fun incrementRestartCount(source: String) {
        val prefs = getSharedPreferences("watchdog_diagnostics", Context.MODE_PRIVATE)
        val count = prefs.getInt("restart_count", 0) + 1
        prefs.edit()
            .putInt("restart_count", count)
            .putString("last_restart_source", source)
            .putLong("last_restart_timestamp", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Restart #$count (source: $source)")
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

        val intervalMs = SettingsManager.getInstance(this).watchdogHeartbeatInterval.toLong() * 60 * 1000L
        val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMs

        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmPendingIntent!!
            )
            Log.d(TAG, "Scheduled exact alarm in ${intervalMs / 1000 / 60} minutes")
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

    /**
     * PROC-02: Launches invisible WatchdogAnchorActivity on Xiaomi/Redmi/POCO devices
     * to bind with BIND_IMPORTANT and elevate OOM adj. No-ops on other manufacturers.
     */
    private fun launchAnchorIfNeeded() {
        if (!isXiaomiDevice()) return
        if (WatchdogAnchorActivity.isActive) return
        try {
            val intent = Intent(this, WatchdogAnchorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "Launched WatchdogAnchorActivity for OOM adj elevation")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WatchdogAnchorActivity: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WatchdogService"

        private const val REQUEST_CODE_WATCHDOG_ALARM = 1001
        internal const val EXTRA_RESTART_SOURCE = "restart_source"

        @Volatile
        var isRunning = false
            private set

        /**
         * PROC-02: Checks if the device manufacturer is Xiaomi, Redmi, or POCO.
         */
        internal fun isXiaomiDevice(): Boolean {
            return Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco")
        }

        /**
         * Starts the WatchdogService and enqueues the WorkManager backup restart job.
         */
        fun start(context: Context, source: String = "manual") {
            val intent = Intent(context, WatchdogService::class.java).apply {
                putExtra(EXTRA_RESTART_SOURCE, source)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            WatchdogRestartWorker.enqueue(context)
        }

        /**
         * Stops the WatchdogService, cancels the WorkManager backup, and cancels any pending alarm.
         */
        fun stop(context: Context) {
            // PROC-01: Dismiss anchor Activity before stopping service
            WatchdogAnchorActivity.finish(context)
            context.stopService(Intent(context, WatchdogService::class.java))
            WatchdogRestartWorker.cancel(context)
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
