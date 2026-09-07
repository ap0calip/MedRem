package com.example

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MedicationViewModel

@Composable
fun EditLabelsDialog(
    viewModel: MedicationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentLabelMode by viewModel.labelMode.collectAsState()

    var selectedMode by remember { mutableStateOf(currentLabelMode) }

    val customProfSing by viewModel.customProfileSing.collectAsState()
    val customProfPlur by viewModel.customProfilePlur.collectAsState()
    val customMedSing by viewModel.customMedicationSing.collectAsState()
    val customMedPlur by viewModel.customMedicationPlur.collectAsState()
    val customDoseSing by viewModel.customDosageSing.collectAsState()
    val customDosePlur by viewModel.customDosagePlur.collectAsState()
    val customTakeLabel by viewModel.customTakenLabel.collectAsState()

    var profSing by remember { mutableStateOf(customProfSing) }
    var profPlur by remember { mutableStateOf(customProfPlur) }
    var medSing by remember { mutableStateOf(customMedSing) }
    var medPlur by remember { mutableStateOf(customMedPlur) }
    var doseSing by remember { mutableStateOf(customDoseSing) }
    var dosePlur by remember { mutableStateOf(customDosePlur) }
    var takeLabelVal by remember { mutableStateOf(customTakeLabel) }

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
                .imePadding()
                .testTag("edit_labels_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Edit Labels",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose how items, categories, and actions are labeled across the app, or customize your own terms.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mode 1: Medication Preset
                LabelModeSelectionCard(
                    title = "Medication Labels",
                    subtitle = "Profile • Medication • Dosage • Taken",
                    icon = Icons.Default.MedicalServices,
                    isSelected = selectedMode == "MEDICATION",
                    onClick = { selectedMode = "MEDICATION" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mode 2: Item Preset
                LabelModeSelectionCard(
                    title = "Item Labels",
                    subtitle = "Category • Item • Description • Completed",
                    icon = Icons.Default.Category,
                    isSelected = selectedMode == "ITEM",
                    onClick = { selectedMode = "ITEM" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mode 3: Custom Labels
                LabelModeSelectionCard(
                    title = "Custom Labels",
                    subtitle = "Personalized singular & plural terms",
                    icon = Icons.Default.Edit,
                    isSelected = selectedMode == "CUSTOM",
                    onClick = { selectedMode = "CUSTOM" }
                )

                // Editable Fields shown if Custom Mode is chosen
                if (selectedMode == "CUSTOM") {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Customize Terms",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Categories Group
                    Text(
                        text = "Categories / Profiles Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = profSing,
                            onValueChange = { profSing = it },
                            label = { Text("Singular") },
                            placeholder = { Text("e.g. Category") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = profPlur,
                            onValueChange = { profPlur = it },
                            label = { Text("Plural") },
                            placeholder = { Text("e.g. Categories") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Items Group
                    Text(
                        text = "Items / Medications Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = medSing,
                            onValueChange = { medSing = it },
                            label = { Text("Singular") },
                            placeholder = { Text("e.g. Item") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = medPlur,
                            onValueChange = { medPlur = it },
                            label = { Text("Plural") },
                            placeholder = { Text("e.g. Items") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Descriptions Group
                    Text(
                        text = "Descriptions / Dosages Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = doseSing,
                            onValueChange = { doseSing = it },
                            label = { Text("Singular") },
                            placeholder = { Text("e.g. Description") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dosePlur,
                            onValueChange = { dosePlur = it },
                            label = { Text("Plural") },
                            placeholder = { Text("e.g. Descriptions") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Label
                    Text(
                        text = "Action Label",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = takeLabelVal,
                        onValueChange = { takeLabelVal = it },
                        label = { Text("Action Label") },
                        placeholder = { Text("e.g. Completed") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("edit_labels_cancel_button")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (selectedMode == "CUSTOM") {
                                if (profSing.isNotBlank() && profPlur.isNotBlank() &&
                                    medSing.isNotBlank() && medPlur.isNotBlank() &&
                                    doseSing.isNotBlank() && dosePlur.isNotBlank() &&
                                    takeLabelVal.isNotBlank()
                                ) {
                                    viewModel.setCustomLabels(
                                        profSing.trim(), profPlur.trim(),
                                        medSing.trim(), medPlur.trim(),
                                        doseSing.trim(), dosePlur.trim(),
                                        takeLabelVal.trim()
                                    )
                                    viewModel.setLabelMode("CUSTOM")
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "All fields are required for Custom Labels!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.setLabelMode(selectedMode)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("edit_labels_apply_button")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelModeSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
