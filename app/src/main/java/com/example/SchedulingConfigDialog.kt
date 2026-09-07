package com.example

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun SchedulingConfigDialog(
    sharedPref: SharedPreferences,
    dosageSing: String,
    takenLabel: String,
    onDismiss: () -> Unit
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    ThemedDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.62f else 0.94f)
                .widthIn(max = 480.dp)
                .heightIn(max = (config.screenHeightDp * if (isLandscape) 0.92f else 0.90f).dp)
                .padding(vertical = 12.dp)
                .imePadding(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // General
                    ConfigSectionTitle("General")
                    ConfigCheckbox(
                        label = "Use the 12-hour format (AM/PM)",
                        explanation = "Toggles between 12-hour (AM/PM) and 24-hour time formats for all time pickers in the application.",
                        prefKey = "pref_12h",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )

                    HorizontalDivider()
                    // One Time
                    ConfigSectionTitle("One Time")
                    ConfigCheckbox(
                        label = "Show the $dosageSing field",
                        explanation = "Displays the optional $dosageSing input field when creating or editing a One Time schedule.",
                        prefKey = "pref_onetime_desc",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Show the alarm sound selection",
                        explanation = "Displays the option to pick a custom alarm ringtone for One Time schedules.",
                        prefKey = "pref_onetime_sound",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Save the entry to the history",
                        explanation = "Sets the default behavior to save a historical record whenever an alarm for this schedule is acknowledged.",
                        prefKey = "pref_onetime_history",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Delete after completion",
                        explanation = "Automatically removes the schedule from your active list once the alarm has triggered and been acknowledged.",
                        prefKey = "pref_onetime_delete",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )

                    HorizontalDivider()
                    // Interval Hours
                    ConfigSectionTitle("Interval Hours")
                    ConfigCheckbox(
                        label = "Show the $dosageSing field",
                        explanation = "Displays the optional $dosageSing input field when creating or editing an Interval Hours schedule.",
                        prefKey = "pref_interval_desc",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Show the alarm sound selection",
                        explanation = "Displays the option to pick a custom alarm ringtone for Interval Hours schedules.",
                        prefKey = "pref_interval_sound",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Save the entry to the history",
                        explanation = "Sets the default behavior to save a historical record whenever an alarm for this schedule is acknowledged.",
                        prefKey = "pref_interval_history",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )

                    HorizontalDivider()
                    // Weekly Days
                    ConfigSectionTitle("Weekly Days")
                    ConfigCheckbox(
                        label = "Show the $dosageSing field",
                        explanation = "Displays the optional $dosageSing input field when creating or editing a Weekly Days schedule.",
                        prefKey = "pref_weekly_desc",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Show the alarm sound selection",
                        explanation = "Displays the option to pick a custom alarm ringtone for Weekly Days schedules.",
                        prefKey = "pref_weekly_sound",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Save the entry to the history",
                        explanation = "Sets the default behavior to save a historical record whenever an alarm for this schedule is acknowledged.",
                        prefKey = "pref_weekly_history",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )

                    HorizontalDivider()
                    // Custom Repeat
                    ConfigSectionTitle("Custom Repeat")
                    ConfigCheckbox(
                        label = "Show the $dosageSing field",
                        explanation = "Displays the optional $dosageSing input field when creating or editing a Custom Repeat schedule.",
                        prefKey = "pref_custom_desc",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Show the alarm sound selection",
                        explanation = "Displays the option to pick a custom alarm ringtone for Custom Repeat schedules.",
                        prefKey = "pref_custom_sound",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Save the entry to the history",
                        explanation = "Sets the default behavior to save a historical record whenever an alarm for this schedule is acknowledged.",
                        prefKey = "pref_custom_history",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                    ConfigCheckbox(
                        label = "Reset day count",
                        explanation = "If enabled, skips the current scheduled sequence if an alarm is $takenLabel or Skipped, resetting the countdown interval starting from the current action time.",
                        prefKey = "pref_custom_reset",
                        sharedPref = sharedPref,
                        defaultVal = true
                    )
                }
                
                // Bottom Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ConfigCheckbox(
    label: String,
    explanation: String,
    prefKey: String,
    sharedPref: SharedPreferences,
    defaultVal: Boolean
) {
    var checked by remember { mutableStateOf(sharedPref.getBoolean(prefKey, defaultVal)) }
    var showExplanation by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
                sharedPref.edit().putBoolean(prefKey, it).apply()
            }
        )
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { showExplanation = true }) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "More Information",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showExplanation) {
        ThemedAlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(label) },
            text = { Text(explanation) },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
