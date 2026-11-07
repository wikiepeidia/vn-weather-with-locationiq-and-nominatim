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

package org.breezyweather.background.weather

import io.kotest.matchers.shouldBe
import org.breezyweather.background.weather.WeatherUpdateJob.ImmediateRefreshEnqueuePlan
import org.junit.jupiter.api.Test

class WeatherUpdateJobTest {

    @Test
    fun `running work does not enqueue another immediate refresh`() {
        WeatherUpdateJob.decideImmediateRefreshEnqueuePlan(
            hasRunningWork = true,
            hasPendingManualWork = false,
        ) shouldBe ImmediateRefreshEnqueuePlan.ALREADY_RUNNING
    }

    @Test
    fun `pending manual refresh is replaced so watchdog can retry`() {
        WeatherUpdateJob.decideImmediateRefreshEnqueuePlan(
            hasRunningWork = false,
            hasPendingManualWork = true,
        ) shouldBe ImmediateRefreshEnqueuePlan.REPLACE_PENDING
    }

    @Test
    fun `fresh immediate refresh request is enqueued normally`() {
        WeatherUpdateJob.decideImmediateRefreshEnqueuePlan(
            hasRunningWork = false,
            hasPendingManualWork = false,
        ) shouldBe ImmediateRefreshEnqueuePlan.ENQUEUE_NEW
    }
}