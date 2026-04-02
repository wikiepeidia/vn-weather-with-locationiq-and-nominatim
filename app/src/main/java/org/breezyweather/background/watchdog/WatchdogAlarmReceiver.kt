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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.breezyweather.domain.settings.SettingsManager

/**
 * Receives AlarmManager alarm to restart WatchdogService after process kill.
 * When WatchdogService is killed by the ROM, the pending AlarmManager alarm fires
 * this receiver, which calls startForegroundService to restart the service.
 */
class WatchdogAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Guard: don't restart if user disabled Watchdog (DEGRADE-02)
        if (!SettingsManager.getInstance(context).watchdogEnabled) {
            Log.d(TAG, "Alarm received but watchdog disabled — ignoring")
            return
        }

        Log.d(TAG, "Alarm received — restarting WatchdogService")

        val serviceIntent = Intent(context, WatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "WatchdogAlarmReceiver"
        const val ACTION_WATCHDOG_ALARM = "org.breezyweather.action.WATCHDOG_ALARM"
    }
}
