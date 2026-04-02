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

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log

/**
 * Invisible zero-pixel Activity that binds to WatchdogService (PROC-01).
 *
 * The binding with BIND_IMPORTANT elevates the service process OOM adj
 * from "service" (~800) to "visible" (~100), making HyperOS/MIUI far less
 * likely to kill it. The Activity uses a transparent theme and is excluded
 * from Recents so the user never sees it.
 *
 * Lifecycle: Launched by WatchdogService on Xiaomi/Redmi/POCO devices (PROC-02).
 * Stays alive in background via moveTaskToBack(). If the system kills it,
 * the next heartbeat re-launches it.
 */
class WatchdogAnchorActivity : Activity() {

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
            Log.d(TAG, "Bound to WatchdogService — OOM adj elevated")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            Log.d(TAG, "Disconnected from WatchdogService")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, WatchdogService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        moveTaskToBack(true)
        isActive = true
        Log.d(TAG, "WatchdogAnchorActivity created and moved to back")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_FINISH, false) == true) {
            Log.d(TAG, "Received finish signal")
            finish()
        }
    }

    override fun onDestroy() {
        isActive = false
        if (bound) {
            unbindService(connection)
            bound = false
        }
        Log.d(TAG, "WatchdogAnchorActivity destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchdogAnchor"

        @Volatile
        var isActive = false
            private set

        fun finish(context: Context) {
            if (isActive) {
                val intent = Intent(context, WatchdogAnchorActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_FINISH, true)
                }
                context.startActivity(intent)
            }
        }

        internal const val EXTRA_FINISH = "finish_anchor"
    }
}
