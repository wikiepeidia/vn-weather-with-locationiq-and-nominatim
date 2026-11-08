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

    private val interval = 90.minutes.inWholeMilliseconds
    private val check = 60.minutes.inWholeMilliseconds
    private val lastUpdate = 1000.minutes.inWholeMilliseconds

    private fun plan(
        previousCheckAfterUpdate: Long = 160,
        nowAfterUpdate: Long = 220,
        hasWork: Boolean = true,
        lastUpdateTimestamp: Long = lastUpdate,
        lastForced: Long = 0L,
        canForce: Boolean = true,
    ) = WatchdogWeatherUpdateHealth.decideRecoveryPlan(
        hasScheduledPeriodicWork = hasWork,
        lastUpdateTimestamp = lastUpdateTimestamp,
        now = lastUpdate + nowAfterUpdate.minutes.inWholeMilliseconds,
        scheduledIntervalMillis = interval,
        watchdogIntervalMillis = check,
        lastForcedRefreshTimestamp = lastForced,
        previousCheckTimestamp = lastUpdate + previousCheckAfterUpdate.minutes.inWholeMilliseconds,
        canForceRefresh = canForce,
    )

    @Test
    fun `first stale check leaves the refresh to the periodic job`() {
        plan(previousCheckAfterUpdate = 100, nowAfterUpdate = 200) shouldBe WeatherUpdateRecoveryPlan.HEALTHY
        plan(previousCheckAfterUpdate = 100, nowAfterUpdate = 200, hasWork = false) shouldBe
            WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC
    }

    @Test
    fun `second stale check forces a refresh`() {
        plan() shouldBe WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
        plan(hasWork = false) shouldBe WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC_AND_START_IMMEDIATE_REFRESH
    }

    @Test
    fun `forced refresh waits one update interval`() {
        val now = lastUpdate + 220.minutes.inWholeMilliseconds
        plan(lastForced = now - 89.minutes.inWholeMilliseconds) shouldBe WeatherUpdateRecoveryPlan.HEALTHY
        plan(lastForced = now - 90.minutes.inWholeMilliseconds) shouldBe WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
    }

    @Test
    fun `clock moved backwards does not block recovery`() {
        val now = lastUpdate + 220.minutes.inWholeMilliseconds
        plan(lastForced = now + 1L) shouldBe WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
    }

    @Test
    fun `last update from the future counts as stale`() {
        plan(lastUpdateTimestamp = lastUpdate + 100_000.minutes.inWholeMilliseconds) shouldBe
            WeatherUpdateRecoveryPlan.START_IMMEDIATE_REFRESH
    }

    @Test
    fun `no forced refresh when it is not allowed, but scheduling is still repaired`() {
        plan(canForce = false) shouldBe WeatherUpdateRecoveryPlan.HEALTHY
        plan(canForce = false, lastUpdateTimestamp = 0L, hasWork = false) shouldBe
            WeatherUpdateRecoveryPlan.SCHEDULE_PERIODIC
    }

    @Test
    fun `disabled updates stay disabled even with work missing`() {
        WatchdogWeatherUpdateHealth.decideRecoveryPlan(
            hasScheduledPeriodicWork = false,
            lastUpdateTimestamp = 0L,
            now = lastUpdate,
            scheduledIntervalMillis = null,
            watchdogIntervalMillis = check,
        ) shouldBe WeatherUpdateRecoveryPlan.UPDATES_DISABLED
    }

    @Test
    fun `alarm chain only applies to Xiaomi family devices that can use it`() {
        fun chain(isXiaomi: Boolean, sdk: Int, exempt: Boolean, updates: Boolean = true) =
            WatchdogWeatherUpdateHealth.usesAlarmChain(isXiaomi, sdk, exempt, updates)

        chain(isXiaomi = false, sdk = 35, exempt = true) shouldBe false
        chain(isXiaomi = true, sdk = 35, exempt = true) shouldBe true
        chain(isXiaomi = true, sdk = 30, exempt = false) shouldBe true
        chain(isXiaomi = true, sdk = 31, exempt = false) shouldBe false
        chain(isXiaomi = true, sdk = 35, exempt = true, updates = false) shouldBe false
    }

    @Test
    fun `overdue only counts after the grace period`() {
        val next = lastUpdate
        val chainGrace = 5.minutes.inWholeMilliseconds
        val passiveGrace = 360.minutes.inWholeMilliseconds

        WatchdogWeatherUpdateHealth.isCheckOverdue(next + chainGrace, next, true) shouldBe false
        WatchdogWeatherUpdateHealth.isCheckOverdue(next + chainGrace + 1, next, true) shouldBe true
        WatchdogWeatherUpdateHealth.isCheckOverdue(next + passiveGrace, next, false) shouldBe false
        WatchdogWeatherUpdateHealth.isCheckOverdue(next + passiveGrace + 1, next, false) shouldBe true
    }
}
