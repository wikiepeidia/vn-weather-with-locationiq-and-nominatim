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
import android.app.ForegroundServiceStartNotAllowedException
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
import androidx.core.content.getSystemService
import org.breezyweather.R
import org.breezyweather.background.weather.WeatherUpdateEventLog
import org.breezyweather.common.extensions.notificationBuilder
import org.breezyweather.common.extensions.workManager
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.remoteviews.Notifications
import org.json.JSONArray
import org.json.JSONObject

/**
 * Short-lived foreground service that monitors WeatherUpdateJob health.
 *
 * On HyperOS/MIUI, WorkManager periodic jobs can get silently killed. This service:
 * 1. Runs a brief foreground heartbeat instead of a long-lived keepalive on Android 14+
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

        // Promote to foreground immediately — Android requires this within 5 seconds.
        // Never piggyback on the weather notification ID here: OEMs such as MIUI/HyperOS can
        // later treat that notification as service-owned and clear the user's persistent weather
        // notification when the short-lived watchdog service exits.
        val notification = getForegroundNotification()
        if (!startWatchdogForeground(notification, source)) {
            WatchdogRestartWorker.enqueue(this)
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
                performHeartbeat()
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
            // Arm next alarm for self-healing BEFORE stopping
            scheduleNextAlarm()
            // Ephemeral: process exits after heartbeat; AlarmManager fires next wakeup
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        // Explicitly tear down foreground state so the temporary watchdog notification follows
        // the short-service lifecycle instead of lingering until the system cleans it up.
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "WatchdogService destroyed")
        // NOTE: cancelAlarm() is intentionally NOT called here.
        // For ephemeral runs (stopSelf after heartbeat) the scheduled alarm MUST survive —
        // it is the self-healing heartbeat loop. Cancelling it here was silently breaking
        // the loop on every normal run.
        // For intentional shutdown, WatchdogService.stop() companion cancels the alarm
        // independently before stopService() is even called.
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

    /**
     * Refreshes the dedicated watchdog foreground notification.
     */
    fun updateNotification() {
        val notification = getForegroundNotification()
        startWatchdogForeground(notification, "update")
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
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Watchdog foreground promotion blocked (source=$source): ${e.message}")
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Watchdog foreground promotion failed (source=$source): ${e.message}")
            false
        }
    }

    /**
     * Checks WeatherUpdateJob health and repairs stale or missing background updates.
     */
    private fun performHeartbeat() {
        Log.d(TAG, "Performing heartbeat check")

        val jobStatus = WatchdogWeatherUpdateHealth.ensureHealthy(this)
        Log.d(TAG, "Watchdog weather health result: $jobStatus")

        // HEART-03: Write diagnostic entry
        writeDiagnostic(jobStatus)

        updateNotification()

    }

    /**
     * HEART-03: Writes timestamped diagnostic entry to SharedPreferences.
     * Data is read by the health dashboard (Phase 14).
     */
    private fun writeDiagnostic(jobStatus: String) {
        val prefs = getSharedPreferences("watchdog_diagnostics", Context.MODE_PRIVATE)
        val heartbeatCount = prefs.getInt("heartbeat_count", 0) + 1
        val uptimeMs = SystemClock.elapsedRealtime() - serviceStartTime

        val lastWeatherEvents = WeatherUpdateEventLog.getLastEntries(this, 3)
        val diagnostic = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("heartbeat_count", heartbeatCount)
            put("uptime_ms", uptimeMs)
            put("job_status", jobStatus)
            put("weather_events", JSONArray(lastWeatherEvents))
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

        val nextAlarmWallClock = System.currentTimeMillis() + intervalMs

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

        // Persist wall-clock next alarm time for the Health Dashboard (HEALTH-01)
        getSharedPreferences("watchdog_diagnostics", Context.MODE_PRIVATE).edit()
            .putLong("next_alarm_timestamp", nextAlarmWallClock)
            .apply()
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
                Log.w(TAG, "startForegroundService blocked (source=$source): ${e.javaClass.simpleName}: ${e.message}")
                // Still enqueue WorkManager backup so WeatherUpdateJob health is checked.
            }
            WatchdogRestartWorker.enqueue(context)
        }

        /**
         * Stops the WatchdogService, cancels the WorkManager backup, and cancels any pending alarm.
         */
        fun stop(context: Context) {
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
