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
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import org.breezyweather.common.extensions.workManager
import org.breezyweather.domain.settings.SettingsManager
import java.util.concurrent.TimeUnit

/**
 * RESTART-01: Redundant restart vector via WorkManager.
 * Runs every 30 minutes independently of AlarmManager.
 * If WatchdogService is not running (e.g., AlarmManager failed to fire),
 * this worker restarts it.
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

        if (WatchdogService.isRunning) {
            Log.d(TAG, "WatchdogService already running — no action needed")
            return Result.success()
        }

        Log.d(TAG, "WatchdogService not running — restarting via WorkManager")
        WatchdogService.start(applicationContext, "workmanager")
        return Result.success()
    }

    companion object {
        private const val TAG = "WatchdogRestart"
        const val WORK_NAME = "WatchdogRestart"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogRestartWorker>(
                30, TimeUnit.MINUTES
            ).build()
            context.workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Enqueued periodic restart check (30min)")
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic restart check")
        }
    }
}
