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
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import org.breezyweather.background.weather.WeatherUpdateJob
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

    fun ensureHealthy(context: Context): String {
        val settings = SettingsManager.getInstance(context)
        val workQuery = WorkQuery.Builder
            .fromUniqueWorkNames(listOf(WeatherUpdateJob.WORK_NAME_AUTO))
            .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
            .build()

        val hasScheduledPeriodicWork = context.workManager.getWorkInfos(workQuery).get().isNotEmpty()
        val plan = decideRecoveryPlan(
            hasScheduledPeriodicWork = hasScheduledPeriodicWork,
            lastUpdateTimestamp = settings.weatherUpdateLastTimestamp,
            now = System.currentTimeMillis(),
            scheduledIntervalMillis = settings.updateInterval.interval?.inWholeMilliseconds,
            watchdogIntervalMillis = settings.watchdogHeartbeatInterval.toLong().minutes.inWholeMilliseconds,
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
    ): WeatherUpdateRecoveryPlan {
        if (scheduledIntervalMillis == null) {
            return WeatherUpdateRecoveryPlan.UPDATES_DISABLED
        }

        val needsScheduling = !hasScheduledPeriodicWork
        if (lastUpdateTimestamp <= 0L) {
            // Weather never successfully fetched. Trigger an immediate refresh now —
            // don't wait on the periodic job alone, which may be stuck or repeatedly failing.
            return if (needsScheduling) {
                WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
            } else {
                WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
            }
        }

        val staleThresholdMillis = scheduledIntervalMillis + watchdogIntervalMillis
        val isStale = now - lastUpdateTimestamp > staleThresholdMillis
        if (!isStale) {
            return if (needsScheduling) {
                WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC
            } else {
                WeatherUpdateRecoveryPlan.HEALTHY
            }
        }

        return if (needsScheduling) {
            WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
        } else {
            WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
        }
    }
}