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

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent ring-buffer log of weather update events.
 *
 * Written at key decision points inside WeatherUpdateJob and read by WatchdogService
 * diagnostics. Survives process death; capped at MAX_ENTRIES so SharedPreferences
 * size stays bounded.
 *
 * Event types:
 *   JOB_START        — worker entered doWork()
 *   JOB_RETRY        — worker exited early with Result.retry()
 *   JOB_ERROR        — worker caught an unexpected exception
 *   WEATHER_UPDATED  — at least one location refreshed; timestamp written
 *   WEATHER_NO_UPDATE — doWork() completed but no location refreshed; timestamp NOT written
 */
internal object WeatherUpdateEventLog {

    private const val PREFS_NAME = "weather_update_event_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 30

    fun append(context: Context, type: String, details: String = "") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        arr.put(
            JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("type", type)
                if (details.isNotEmpty()) put("d", details)
            }
        )

        val trimmed = if (arr.length() > MAX_ENTRIES) {
            val drop = arr.length() - MAX_ENTRIES
            JSONArray().also { t -> for (i in drop until arr.length()) t.put(arr.get(i)) }
        } else {
            arr
        }

        prefs.edit().putString(KEY_ENTRIES, trimmed.toString()).apply()
    }

    /**
     * Returns the last [count] entries as compact strings: "timestamp|TYPE|details".
     * Callers embed these in watchdog diagnostics for post-mortem analysis.
     */
    fun getLastEntries(context: Context, count: Int = 5): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { return emptyList() }
        val start = maxOf(0, arr.length() - count)
        return (start until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val details = obj.optString("d", "")
            "${obj.optLong("ts")}|${obj.optString("type")}${if (details.isNotEmpty()) "|$details" else ""}"
        }
    }
}
