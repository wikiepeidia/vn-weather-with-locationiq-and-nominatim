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

package org.breezyweather.ui.settings.compose

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import org.breezyweather.R
import org.breezyweather.background.watchdog.WatchdogService
import org.breezyweather.background.weather.WeatherUpdateJob
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatTime
import org.breezyweather.common.extensions.getFormattedDate
import org.breezyweather.common.extensions.plus
import org.breezyweather.common.extensions.powerManager
import org.breezyweather.common.options.UpdateInterval
import org.breezyweather.common.utils.helpers.SnackbarHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.defaultCardListItemElevation
import org.breezyweather.ui.common.widgets.generateCollapsedScrollBehavior
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.settings.activities.WorkerInfoActivity
import org.breezyweather.ui.settings.preference.bottomInsetItem
import org.breezyweather.ui.settings.preference.clickablePreferenceItem
import org.breezyweather.ui.settings.preference.composables.ListPreferenceViewWithCard
import org.breezyweather.ui.settings.preference.composables.PreferenceScreen
import org.breezyweather.ui.settings.preference.composables.PreferenceViewWithCard
import org.breezyweather.ui.settings.preference.composables.SwitchPreferenceView
import org.breezyweather.ui.settings.preference.largeSeparatorItem
import org.breezyweather.ui.settings.preference.listPreferenceItem
import org.breezyweather.ui.settings.preference.sectionFooterItem
import org.breezyweather.ui.settings.preference.sectionHeaderItem
import org.breezyweather.ui.settings.preference.smallSeparatorItem
import org.breezyweather.ui.settings.preference.switchPreferenceItem
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.formatting.format
import java.util.Date
import kotlin.time.DurationUnit

@Composable
fun BackgroundSettingsScreen(
    context: Context,
    updateInterval: UpdateInterval,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = generateCollapsedScrollBehavior()

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_background_updates),
                onBackPressed = onNavigateBack,
                actions = { AboutActivityIconButton(context) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddings ->
        PreferenceScreen(
            paddingValues = paddings.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
        ) {
            sectionHeaderItem(R.string.settings_background_updates_section_general)
            listPreferenceItem(R.string.settings_background_updates_refresh_title) { id ->
                val valueArray = stringArrayResource(R.array.automatic_refresh_rate_values)
                val nameArray = stringArrayResource(R.array.automatic_refresh_rates).mapIndexed { index, value ->
                    UpdateInterval.entries.firstOrNull { it.id == valueArray[index] }?.let { updateInterval ->
                        updateInterval.interval?.formatTime(
                            context = context,
                            smallestUnit = DurationUnit.MINUTES,
                            unitWidth = UnitWidth.LONG
                        )
                    } ?: value
                }.toTypedArray()
                val dialogNeverRefreshOpenState = remember { mutableStateOf(false) }
                ListPreferenceViewWithCard(
                    title = stringResource(id),
                    summary = { _, value ->
                        valueArray.indexOfFirst { it == value }.let { if (it == -1) nameArray[0] else nameArray[it] }
                    },
                    selectedKey = updateInterval.id,
                    valueArray = valueArray,
                    nameArray = nameArray,
                    withState = false,
                    isFirst = true,
                    onValueChanged = {
                        val newValue = UpdateInterval.getInstance(it)
                        if (newValue == UpdateInterval.INTERVAL_NEVER) {
                            dialogNeverRefreshOpenState.value = true
                        } else {
                            SettingsManager
                                .getInstance(context)
                                .updateInterval = UpdateInterval.getInstance(it)
                            WeatherUpdateJob.setupTask(context)
                        }
                    }
                )
                if (dialogNeverRefreshOpenState.value) {
                    var timeLeft by remember { mutableIntStateOf(10) }
                    LaunchedEffect(key1 = timeLeft) {
                        while (timeLeft > 0) {
                            delay(1000L)
                            --timeLeft
                        }
                    }
                    AlertDialog(
                        onDismissRequest = { dialogNeverRefreshOpenState.value = false },
                        text = {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_background_updates_refresh_never_warning1),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
                                Text(
                                    text = stringResource(
                                        R.string.settings_background_updates_refresh_never_warning2,
                                        5
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
                                Text(
                                    text = stringResource(R.string.settings_background_updates_refresh_never_warning3),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dialogNeverRefreshOpenState.value = false
                                    SettingsManager
                                        .getInstance(context)
                                        .updateInterval = UpdateInterval.INTERVAL_NEVER
                                    WeatherUpdateJob.setupTask(context)
                                },
                                enabled = timeLeft == 0
                            ) {
                                Text(
                                    text = if (timeLeft > 0) {
                                        stringResource(
                                            R.string.parenthesis,
                                            stringResource(R.string.action_continue),
                                            timeLeft.format(decimals = 0, locale = context.currentLocale)
                                        )
                                    } else {
                                        stringResource(R.string.action_continue)
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    dialogNeverRefreshOpenState.value = false
                                }
                            ) {
                                Text(
                                    text = stringResource(android.R.string.cancel),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    )
                }
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_background_updates_refresh_skip_when_battery_low) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).ignoreUpdatesWhenBatteryLow,
                    enabled = updateInterval != UpdateInterval.INTERVAL_NEVER,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).ignoreUpdatesWhenBatteryLow = it
                        WeatherUpdateJob.setupTask(context)
                    }
                )
            }
            sectionFooterItem(R.string.settings_background_updates_section_general)

            largeSeparatorItem()

            // Watchdog Mode section — persistent foreground service for HyperOS/MIUI keepalive
            sectionHeaderItem(R.string.settings_background_updates_section_watchdog)

            val isXiaomiDevice = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)

            switchPreferenceItem(R.string.settings_background_updates_watchdog_switch) { id ->
                val dialogBatteryOptState = remember { mutableStateOf(false) }

                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).watchdogEnabled,
                    isFirst = true,
                    isLast = false,
                    onValueChanged = { enabled ->
                        SettingsManager.getInstance(context).watchdogEnabled = enabled
                        if (enabled) {
                            WatchdogService.start(context)
                            if (!context.powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                dialogBatteryOptState.value = true
                            }
                        } else {
                            WatchdogService.stop(context)
                        }
                    }
                )

                if (dialogBatteryOptState.value) {
                    AlertDialog(
                        onDismissRequest = { dialogBatteryOptState.value = false },
                        title = {
                            Text(
                                text = stringResource(R.string.settings_background_updates_watchdog_battery_opt_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.settings_background_updates_watchdog_battery_opt_message),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dialogBatteryOptState.value = false
                                    try {
                                        @SuppressLint("BatteryLife")
                                        val intent = Intent().apply {
                                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                            data = "package:${context.packageName}".toUri()
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        SnackbarHelper.showSnackbar(
                                            context.getString(R.string.settings_background_updates_battery_optimization_activity_not_found)
                                        )
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_background_updates_battery_optimization),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { dialogBatteryOptState.value = false }) {
                                Text(
                                    text = stringResource(android.R.string.cancel),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    )
                }
            }

            smallSeparatorItem()
            item {
                val settingsManager = SettingsManager.getInstance(context)
                val watchdogOn = settingsManager.watchdogEnabled
                var sliderValue by remember {
                    mutableFloatStateOf(settingsManager.watchdogHeartbeatInterval.toFloat())
                }

                Material3ExpressiveCardListItem(
                    elevation = if (watchdogOn) defaultCardListItemElevation else 0.dp,
                    isFirst = false,
                    isLast = !isXiaomiDevice
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (watchdogOn) 1f else 0.5f)
                            .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.settings_background_updates_watchdog_heartbeat_interval),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_background_updates_watchdog_heartbeat_interval_summary,
                                    sliderValue.toInt()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                val newInterval = sliderValue.toInt()
                                settingsManager.watchdogHeartbeatInterval = newInterval
                                if (watchdogOn) {
                                    WatchdogService.start(context)
                                }
                            },
                            valueRange = 10f..30f,
                            steps = 3,
                            enabled = watchdogOn
                        )
                    }
                }
            }

            if (isXiaomiDevice) {
                smallSeparatorItem()
                clickablePreferenceItem(R.string.settings_background_updates_watchdog_hyperios_autostart) { id ->
                    PreferenceViewWithCard(
                        titleId = id,
                        summaryId = R.string.settings_background_updates_watchdog_hyperios_autostart_summary,
                        isFirst = false,
                        isLast = true
                    ) {
                        try {
                            val intent = Intent().apply {
                                setClassName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            try {
                                val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                                    putExtra("extra_pkgname", context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e2: ActivityNotFoundException) {
                                SnackbarHelper.showSnackbar(
                                    context.getString(R.string.settings_background_updates_watchdog_hyperios_autostart_not_found)
                                )
                            }
                        }
                    }
                }
            }

            sectionFooterItem(R.string.settings_background_updates_section_watchdog)

            // HEALTH-01/02/03: Watchdog health dashboard (visible only when watchdog is enabled)
            if (SettingsManager.getInstance(context).watchdogEnabled) {
                largeSeparatorItem()
                sectionHeaderItem(R.string.settings_background_updates_section_watchdog_health)

                item {
                    val prefs = context.getSharedPreferences("watchdog_diagnostics", Context.MODE_PRIVATE)
                    var lastHeartbeat by remember { mutableLongStateOf(prefs.getLong("last_heartbeat_timestamp", 0L)) }
                    var heartbeatCount by remember { mutableIntStateOf(prefs.getInt("heartbeat_count", 0)) }
                    var restartCount by remember { mutableIntStateOf(prefs.getInt("restart_count", 0)) }
                    var lastRestart by remember { mutableLongStateOf(prefs.getLong("last_restart_timestamp", 0L)) }
                    var nextAlarmTimestamp by remember { mutableLongStateOf(prefs.getLong("next_alarm_timestamp", 0L)) }

                    // HEALTH-02: Periodic poll every 5s for live refresh
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(5000)
                            lastHeartbeat = prefs.getLong("last_heartbeat_timestamp", 0L)
                            heartbeatCount = prefs.getInt("heartbeat_count", 0)
                            restartCount = prefs.getInt("restart_count", 0)
                            lastRestart = prefs.getLong("last_restart_timestamp", 0L)
                            nextAlarmTimestamp = prefs.getLong("next_alarm_timestamp", 0L)
                        }
                    }

                    // Status derived from next alarm timestamp — isRunning is unreliable in ephemeral model
                    // (WatchdogService exits after each heartbeat, so isRunning is almost always false)
                    val now = System.currentTimeMillis()
                    val gracePeriodMs = 5 * 60 * 1000L // 5 min grace for ROM scheduling delays
                    val statusText = when {
                        nextAlarmTimestamp == 0L -> stringResource(R.string.watchdog_health_status_starting)
                        now <= nextAlarmTimestamp + gracePeriodMs -> stringResource(R.string.watchdog_health_status_scheduled)
                        else -> stringResource(R.string.watchdog_health_status_overdue)
                    }

                    val heartbeatText = if (lastHeartbeat > 0L) {
                        android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastHeartbeat,
                            now,
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                        ).toString()
                    } else {
                        stringResource(R.string.watchdog_health_never)
                    }

                    val nextRefreshText = if (nextAlarmTimestamp > 0L) {
                        android.text.format.DateUtils.getRelativeTimeSpanString(
                            nextAlarmTimestamp,
                            now,
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                        ).toString()
                    } else {
                        stringResource(R.string.watchdog_health_never)
                    }

                    val restartText = if (lastRestart > 0L) {
                        "$restartCount (${android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastRestart,
                            now,
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                        )})"
                    } else {
                        restartCount.toString()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensionResource(R.dimen.normal_margin))
                    ) {
                        PreferenceViewWithCard(
                            title = stringResource(R.string.watchdog_health_status),
                            summary = statusText,
                            isFirst = true,
                        ) { /* no-op */ }
                        Spacer(modifier = Modifier.height(1.dp))
                        PreferenceViewWithCard(
                            title = stringResource(R.string.watchdog_health_last_heartbeat),
                            summary = heartbeatText,
                        ) { /* no-op */ }
                        Spacer(modifier = Modifier.height(1.dp))
                        PreferenceViewWithCard(
                            title = stringResource(R.string.watchdog_health_next_refresh),
                            summary = nextRefreshText,
                        ) { /* no-op */ }
                        Spacer(modifier = Modifier.height(1.dp))
                        PreferenceViewWithCard(
                            title = stringResource(R.string.watchdog_health_heartbeat_count),
                            summary = heartbeatCount.toString(),
                        ) { /* no-op */ }
                        Spacer(modifier = Modifier.height(1.dp))
                        PreferenceViewWithCard(
                            title = stringResource(R.string.watchdog_health_restart_count),
                            summary = restartText,
                            isLast = true,
                        ) { /* no-op */ }
                    }
                }

                // HEALTH-03: Reset stats action
                clickablePreferenceItem(R.string.watchdog_health_reset_stats) { id ->
                    PreferenceViewWithCard(
                        titleId = id,
                        summaryId = R.string.watchdog_health_reset_stats_summary,
                        isLast = true
                    ) {
                        context.getSharedPreferences("watchdog_diagnostics", Context.MODE_PRIVATE)
                            .edit()
                            .putInt("heartbeat_count", 0)
                            .putInt("restart_count", 0)
                            .putLong("last_restart_timestamp", 0L)
                            .putLong("last_heartbeat_timestamp", 0L)
                            .remove("last_diagnostic")
                            .remove("last_restart_source")
                            .apply()
                        SnackbarHelper.showSnackbar("Stats reset")
                    }
                }

                sectionFooterItem(R.string.settings_background_updates_section_watchdog_health)
            }

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_background_updates_section_troubleshoot)
            clickablePreferenceItem(R.string.settings_background_updates_battery_optimization) { id ->
                PreferenceViewWithCard(
                    titleId = id,
                    summaryId = R.string.settings_background_updates_battery_optimization_summary,
                    isFirst = true
                ) {
                    val packageName: String = context.packageName
                    if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            @SuppressLint("BatteryLife")
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            SnackbarHelper.showSnackbar(
                                context.getString(
                                    R.string.settings_background_updates_battery_optimization_activity_not_found
                                )
                            )
                        }
                    } else {
                        SnackbarHelper.showSnackbar(
                            context.getString(R.string.settings_background_updates_battery_optimization_disabled)
                        )
                    }
                }
            }
            smallSeparatorItem()
            clickablePreferenceItem(R.string.settings_background_updates_dont_kill_my_app_title) { id ->
                PreferenceViewWithCard(
                    titleId = id,
                    summaryId = R.string.settings_background_updates_dont_kill_my_app_summary
                ) {
                    uriHandler.openUri("https://dontkillmyapp.com/")
                }
            }
            smallSeparatorItem()
            clickablePreferenceItem(R.string.settings_background_updates_worker_info_title) { id ->
                PreferenceViewWithCard(
                    title = context.getString(id),
                    summary = if (SettingsManager.getInstance(context).weatherUpdateLastTimestamp > 0) {
                        context.getString(
                            R.string.settings_background_updates_worker_info_summary,
                            Date(SettingsManager.getInstance(context).weatherUpdateLastTimestamp)
                                .getFormattedDate("yyyy-MM-dd HH:mm")
                        )
                    } else {
                        null
                    },
                    isLast = true
                ) {
                    context.startActivity(Intent(context, WorkerInfoActivity::class.java))
                }
            }
            sectionFooterItem(R.string.settings_background_updates_section_troubleshoot)

            bottomInsetItem()
        }
    }
}
