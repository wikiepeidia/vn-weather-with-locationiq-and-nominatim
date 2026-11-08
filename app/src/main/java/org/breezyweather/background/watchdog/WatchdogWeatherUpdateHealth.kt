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

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import org.breezyweather.background.weather.WeatherUpdateJob
import org.breezyweather.common.extensions.isOnline
import org.breezyweather.common.extensions.powerManager
import org.breezyweather.common.extensions.workManager
import org.breezyweather.domain.settings.SettingsManager
import kotlin.time.Duration.Companion.minutes

internal enum class WeatherUpdateRecoveryPlan {
    UPDATES_DISABLED,
    HEALTHY,
    SCHEDULE_PERIODIC,
    START_IMMEDIATE_REFRESH,
    SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH,
}

internal object WatchdogWeatherUpdateHealth {

    const val PREFS_NAME = "watchdog_diagnostics"
    private const val KEY_LAST_FORCED_REFRESH = "last_forced_refresh_timestamp"
    private const val KEY_LAST_CHECK = "last_heartbeat_timestamp"
    private const val KEY_CHECK_COUNT = "heartbeat_count"

    private const val CHAIN_OVERDUE_GRACE_MS = 5 * 60 * 1000L
    // Passive checks wait for Doze's maintenance windows, so being hours late is normal
    private const val PASSIVE_OVERDUE_GRACE_MS = 6 * 60 * 60 * 1000L

    fun ensureHealthy(context: Context): String {
        val settings = SettingsManager.getInstance(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val workQuery = WorkQuery.Builder
            .fromUniqueWorkNames(listOf(WeatherUpdateJob.WORK_NAME_AUTO))
            .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
            .build()

        val hasScheduledPeriodicWork = context.workManager.getWorkInfos(workQuery).get().isNotEmpty()
        val watchdogIntervalMinutes = if (WatchdogService.isXiaomiDevice()) {
            settings.watchdogHeartbeatInterval.toLong()
        } else {
            WatchdogRestartWorker.periodMinutes()
        }
        val plan = decideRecoveryPlan(
            hasScheduledPeriodicWork = hasScheduledPeriodicWork,
            lastUpdateTimestamp = settings.weatherUpdateLastTimestamp,
            now = now,
            scheduledIntervalMillis = settings.updateInterval.interval?.inWholeMilliseconds,
            watchdogIntervalMillis = watchdogIntervalMinutes.minutes.inWholeMilliseconds,
            lastForcedRefreshTimestamp = prefs.getLong(KEY_LAST_FORCED_REFRESH, 0L),
            previousCheckTimestamp = prefs.getLong(KEY_LAST_CHECK, 0L),
            canForceRefresh = context.isOnline() &&
                !context.powerManager.isPowerSaveMode &&
                !(settings.ignoreUpdatesWhenBatteryLow && context.isBatteryLow()),
        )

        if (plan == WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC ||
            plan == WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
        ) {
            WeatherUpdateJob.setupTask(context)
        }

        val startedImmediateRefresh = if (
            plan == WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH ||
            plan == WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
        ) {
            WeatherUpdateJob.startNow(context)
        } else {
            false
        }

        prefs.edit().apply {
            putInt(KEY_CHECK_COUNT, prefs.getInt(KEY_CHECK_COUNT, 0) + 1)
            putLong(KEY_LAST_CHECK, now)
            if (startedImmediateRefresh) putLong(KEY_LAST_FORCED_REFRESH, now)
        }.apply()

        return when (plan) {
            WeatherUpdateRecoveryPlan.UPDATES_DISABLED -> "updates-disabled"
            WeatherUpdateRecoveryPlan.HEALTHY -> "healthy"
            WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC -> "re-enqueued"
            WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH -> {
                if (startedImmediateRefresh) "stale-refresh-started" else "stale-refresh-already-running"
            }
            WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH -> {
                if (startedImmediateRefresh) {
                    "re-enqueued-and-started"
                } else {
                    "re-enqueued-awaiting-running-refresh"
                }
            }
        }
    }

    fun decideRecoveryPlan(
        hasScheduledPeriodicWork: Boolean,
        lastUpdateTimestamp: Long,
        now: Long,
        scheduledIntervalMillis: Long?,
        watchdogIntervalMillis: Long,
        lastForcedRefreshTimestamp: Long = 0L,
        previousCheckTimestamp: Long = 0L,
        canForceRefresh: Boolean = true,
    ): WeatherUpdateRecoveryPlan {
        if (scheduledIntervalMillis == null) {
            return WeatherUpdateRecoveryPlan.UPDATES_DISABLED
        }

        val needsScheduling = !hasScheduledPeriodicWork
        val staleAfter = scheduledIntervalMillis + watchdogIntervalMillis
        // Weather never successfully fetched counts as stale: the periodic job may be stuck.
        // So does a timestamp from the future, left by a clock that was later corrected.
        val neverUpdated = lastUpdateTimestamp <= 0L || lastUpdateTimestamp > now
        val isStale = neverUpdated || now - lastUpdateTimestamp > staleAfter
        // After a long idle the periodic job runs in this same window; forcing now would fetch twice
        val wasAlreadyStale = neverUpdated ||
            previousCheckTimestamp <= 0L ||
            previousCheckTimestamp - lastUpdateTimestamp > staleAfter
        // A clock moved backwards must not block recovery
        val cooledDown = now < lastForcedRefreshTimestamp ||
            now - lastForcedRefreshTimestamp >= scheduledIntervalMillis

        return when {
            isStale && wasAlreadyStale && canForceRefresh && cooledDown -> {
                if (needsScheduling) {
                    WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
                } else {
                    WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
                }
            }
            needsScheduling -> WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC
            else -> WeatherUpdateRecoveryPlan.HEALTHY
        }
    }

    fun usesAlarmChain(
        isXiaomi: Boolean,
        sdkInt: Int,
        ignoringBatteryOptimizations: Boolean,
        updatesEnabled: Boolean,
    ): Boolean {
        return isXiaomi && updatesEnabled && (sdkInt < Build.VERSION_CODES.S || ignoringBatteryOptimizations)
    }

    fun isCheckOverdue(now: Long, nextCheck: Long, usesAlarmChain: Boolean): Boolean {
        return now > nextCheck + if (usesAlarmChain) CHAIN_OVERDUE_GRACE_MS else PASSIVE_OVERDUE_GRACE_MS
    }

    // shortcut: approximates JobScheduler's battery-not-low threshold, read the system level if it drifts
    private fun Context.isBatteryLow(): Boolean {
        val batteryManager = getSystemService<BatteryManager>() ?: return false
        return !batteryManager.isCharging &&
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) in 1..15
    }
}
