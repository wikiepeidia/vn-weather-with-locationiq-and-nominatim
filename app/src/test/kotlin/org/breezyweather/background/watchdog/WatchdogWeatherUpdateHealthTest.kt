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

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class WatchdogWeatherUpdateHealthTest {

    @Test
    fun `zero timestamp with scheduled work forces immediate refresh`() {
        // Weather never fetched: watchdog must not wait indefinitely (AWAITING_FIRST_RUN was the blind spot).
        WatchdogWeatherUpdateHealth.decideRecoveryPlan(
            hasScheduledPeriodicWork = true,
            lastUpdateTimestamp = 0L,
            now = 46.minutes.inWholeMilliseconds,
            scheduledIntervalMillis = 30.minutes.inWholeMilliseconds,
            watchdogIntervalMillis = 15.minutes.inWholeMilliseconds,
        ) shouldBe WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
    }

    @Test
    fun `stale timestamp with scheduled work forces immediate refresh`() {
        WatchdogWeatherUpdateHealth.decideRecoveryPlan(
            hasScheduledPeriodicWork = true,
            lastUpdateTimestamp = 1L,
            now = 46.minutes.inWholeMilliseconds,
            scheduledIntervalMillis = 30.minutes.inWholeMilliseconds,
            watchdogIntervalMillis = 15.minutes.inWholeMilliseconds,
        ) shouldBe WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
    }

    @Test
    fun `missing and stale scheduled work repairs both schedule and refresh`() {
        WatchdogWeatherUpdateHealth.decideRecoveryPlan(
            hasScheduledPeriodicWork = false,
            lastUpdateTimestamp = 1L,
            now = 46.minutes.inWholeMilliseconds,
            scheduledIntervalMillis = 30.minutes.inWholeMilliseconds,
            watchdogIntervalMillis = 15.minutes.inWholeMilliseconds,
        ) shouldBe WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
    }

    @Test
    fun `fresh scheduled work stays healthy`() {
        WatchdogWeatherUpdateHealth.decideRecoveryPlan(
            hasScheduledPeriodicWork = true,
            lastUpdateTimestamp = 20.minutes.inWholeMilliseconds,
            now = 40.minutes.inWholeMilliseconds,
            scheduledIntervalMillis = 30.minutes.inWholeMilliseconds,
            watchdogIntervalMillis = 15.minutes.inWholeMilliseconds,
        ) shouldBe WeatherUpdateRecoveryPlan.HEALTHY
    }
}