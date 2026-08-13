package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ThemedDialog
import com.example.data.entity.Medication
import java.util.Locale

fun getFrequencyInMinutes(medication: Medication?): Long? {
    if (medication == null) return null
    return when (medication.scheduleType) {
        "INTERVAL" -> {
            val hrs = if (medication.intervalHours <= 0) 24 else medication.intervalHours
            hrs * 60L
        }
        "CUSTOM" -> {
            val num = if (medication.intervalHours <= 0) 1 else medication.intervalHours
            when (medication.daysOfWeekCommaSeparated.lowercase(Locale.getDefault())) {
                "hours", "hour" -> num * 60L
                "days", "day" -> num * 1440L
                "weeks", "week" -> num * 7L * 1440L
                "months", "month" -> num * 30L * 1440L
                "years", "year" -> num * 365L * 1440L
                else -> num * 1440L
            }
        }
        "WEEKLY" -> {
            val days = medication.daysOfWeekCommaSeparated
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (days.isEmpty()) 1440L
            else if (days.size == 1) 7 * 1440L
            else (7L * 1440L) / days.size
        }
        else -> 1440L
    }
}

fun formatDurationMinutes(totalMinutes: Long): String {
    if (totalMinutes < 60) {
        return "$totalMinutes minutes"
    }
    val hours = totalMinutes / 60.0
    if (hours < 24) {
        val hInt = totalMinutes / 60
        val mRem = totalMinutes % 60
        return if (mRem == 0L) {
            if (hInt == 1L) "1 hour" else "$hInt hours"
        } else {
            "${hInt}h ${mRem}m"
        }
    }
    val days = totalMinutes / 1440.0
    if (days < 7) {
        val dInt = totalMinutes / 1440
        val hRem = (totalMinutes % 1440) / 60
        return if (hRem == 0L) {
            if (dInt == 1L) "1 day" else "$dInt days"
        } else {
            "${dInt}d ${hRem}h"
        }
    }
    val weeks = days / 7.0
    return if (weeks % 1.0 == 0.0) {
        val wInt = weeks.toLong()
        if (wInt == 1L) "1 week" else "$wInt weeks"
    } else {
        val formatted = String.format(Locale.US, "%.2f", weeks).trimEnd('0').trimEnd('.')
        "$formatted weeks"
    }
}

private data class PresetOption(val label: String, val valStr: String, val unitStr: String, val totalMinutes: Long)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeDurationDialog(
    medication: Medication? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val freqMinutes = remember(medication) { getFrequencyInMinutes(medication) }
    val maxSafeSnoozeMinutes = remember(freqMinutes) {
        freqMinutes?.let { (it * 0.25).toLong().coerceAtLeast(1L) }
    }

    val unitMinutesMap = remember {
        mapOf(
            "minutes" to 1L,
            "hours" to 60L,
            "days" to 1440L,
            "weeks" to 10080L,
            "months" to 43200L,
            "years" to 525600L
        )
    }

    // Units allowed in dropdown based on maxSafeSnoozeMinutes
    val availableUnits = remember(maxSafeSnoozeMinutes) {
        val allUnits = listOf("minutes", "hours", "days", "weeks", "months", "years")
        if (maxSafeSnoozeMinutes == null) {
            listOf("minutes", "hours", "days", "weeks")
        } else {
            val filtered = allUnits.filter { u ->
                val minVal = unitMinutesMap[u] ?: 1L
                minVal <= maxSafeSnoozeMinutes
            }
            if (filtered.isEmpty()) listOf("minutes") else filtered
        }
    }

    // Default values
    val defaultTextVal = remember(maxSafeSnoozeMinutes) {
        if (maxSafeSnoozeMinutes != null && 30L > maxSafeSnoozeMinutes) {
            maxSafeSnoozeMinutes.toString()
        } else {
            "30"
        }
    }

    var textValue by remember(defaultTextVal) { mutableStateOf(defaultTextVal) }
    var selectedUnit by remember(availableUnits) { mutableStateOf(availableUnits.firstOrNull() ?: "minutes") }
    var expandedDropdown by remember { mutableStateOf(false) }
    var userIsSure by remember { mutableStateOf(false) }

    // Candidates for quick preset chips
    val allPresets = remember {
        listOf(
            PresetOption("5m", "5", "minutes", 5L),
            PresetOption("10m", "10", "minutes", 10L),
            PresetOption("15m", "15", "minutes", 15L),
            PresetOption("30m", "30", "minutes", 30L),
            PresetOption("1h", "1", "hours", 60L),
            PresetOption("2h", "2", "hours", 120L),
            PresetOption("1d", "1", "days", 1440L),
            PresetOption("1w", "1", "weeks", 10080L)
        )
    }

    // Filter presets that exceed maxSafeSnoozeMinutes
    val visiblePresets = remember(maxSafeSnoozeMinutes) {
        if (maxSafeSnoozeMinutes == null) {
            allPresets
        } else {
            val filtered = allPresets.filter { it.totalMinutes <= maxSafeSnoozeMinutes }
            if (filtered.isEmpty()) listOf(PresetOption("${maxSafeSnoozeMinutes}m", maxSafeSnoozeMinutes.toString(), "minutes", maxSafeSnoozeMinutes)) else filtered
        }
    }

    // Calculate currently requested snooze time
    val numInput = textValue.toLongOrNull() ?: 30L
    val currentUnitMult = unitMinutesMap[selectedUnit.lowercase(Locale.getDefault())] ?: 1L
    val selectedSnoozeMinutes = numInput * currentUnitMult

    // Check states
    val isConflict = freqMinutes != null && selectedSnoozeMinutes >= freqMinutes
    val isLongWarning = freqMinutes != null && maxSafeSnoozeMinutes != null &&
            !isConflict && selectedSnoozeMinutes > maxSafeSnoozeMinutes

    val configSnooze = androidx.compose.ui.platform.LocalConfiguration.current
    val isSnoozeLandscape = configSnooze.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    ThemedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(if (isSnoozeLandscape) 0.55f else 0.92f)
                .widthIn(max = 420.dp)
                .padding(vertical = 12.dp)
                .testTag("snooze_duration_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Snooze Duration",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Top description
                Text(
                    text = "Choose a quick snooze preset or select a custom duration below (Default is 30 minutes).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Quick Presets
                if (visiblePresets.isNotEmpty()) {
                    Text(
                        text = "Quick Presets",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        visiblePresets.forEach { preset ->
                            val isSelected = textValue == preset.valStr && selectedUnit == preset.unitStr
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = {
                                    textValue = preset.valStr
                                    selectedUnit = preset.unitStr
                                    userIsSure = false
                                },
                                label = { Text(preset.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.testTag("snooze_preset_chip_${preset.label}")
                            )
                        }
                    }
                }

                // Custom Repeat Every Style Input
                Text(
                    text = "Repeat Every / Snooze Duration",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val current = textValue.toIntOrNull() ?: 30
                            val newVal = if (current > 1) current - 1 else 1
                            textValue = newVal.toString()
                            userIsSure = false
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("snooze_decrement_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Decrease",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            textValue = filtered
                            userIsSure = false
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("snooze_input_field")
                    )

                    IconButton(
                        onClick = {
                            val current = textValue.toIntOrNull() ?: 30
                            val newVal = current + 1
                            textValue = newVal.toString()
                            userIsSure = false
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("snooze_increment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Increase",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("snooze_unit_box")
                    ) {
                        OutlinedTextField(
                            value = selectedUnit.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                    Icon(
                                        imageVector = if (expandedDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "Select unit"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedDropdown = !expandedDropdown },
                            singleLine = true
                        )

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            availableUnits.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }) },
                                    onClick = {
                                        selectedUnit = unit
                                        expandedDropdown = false
                                        userIsSure = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Error / Conflict Card
                if (isConflict && freqMinutes != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Snoozing for ${formatDurationMinutes(selectedSnoozeMinutes)} is not possible because it conflicts with or exceeds the next scheduled frequency (${formatDurationMinutes(freqMinutes)}).",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Long Snooze Warning Card
                if (isLongWarning && freqMinutes != null && maxSafeSnoozeMinutes != null) {
                    val isDark = isSystemInDarkTheme()
                    val warningContainer = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0)
                    val warningContent = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = warningContainer,
                            contentColor = warningContent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = warningContent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Long Snooze Warning",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = warningContent
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${formatDurationMinutes(selectedSnoozeMinutes)} is a long snooze duration for a ${formatDurationMinutes(freqMinutes)} schedule frequency (exceeds recommended 25% max / ${formatDurationMinutes(maxSafeSnoozeMinutes)}).",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 19.sp,
                                color = warningContent
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { userIsSure = !userIsSure }
                            ) {
                                Checkbox(
                                    checked = userIsSure,
                                    onCheckedChange = { userIsSure = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = warningContent,
                                        uncheckedColor = warningContent,
                                        checkmarkColor = if (isDark) Color.Black else Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "I am sure I want to snooze for this long",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = warningContent
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("snooze_dialog_cancel")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val canConfirm = !isConflict && (!isLongWarning || userIsSure)

                    Button(
                        enabled = canConfirm,
                        onClick = {
                            val totalMinutes = selectedSnoozeMinutes.coerceAtLeast(1L).toInt()
                            onConfirm(totalMinutes)
                        },
                        modifier = Modifier.testTag("snooze_dialog_confirm")
                    ) {
                        Text("Snooze")
                    }
                }
            }
        }
    }
}
