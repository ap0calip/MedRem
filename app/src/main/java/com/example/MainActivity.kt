package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MedicationViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    MedRemApp(innerPadding)
                }
            }
        }
    }
}

@Composable
fun MedRemApp(
    innerPadding: PaddingValues,
    viewModel: MedicationViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // State lists from Room Database (Flow-connected)
    val familyMembers by viewModel.familyMembers.collectAsState()
    val filteredMedications by viewModel.filteredMedications.collectAsState()
    val doseRecords by viewModel.doseRecords.collectAsState()
    
    // Filter variables
    val selectedProfileId by viewModel.selectedFamilyMemberId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // UI control states
    var showAddMedicationDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var medicationToEdit by remember { mutableStateOf<Medication?>(null) }
    
    // Active navigation tab (0 = Medications, 1 = Dose History)
    var activeTab by remember { mutableStateOf(0) }

    // Request permissions for Notifications automatically on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    context,
                    "Notifications disabled. Alarms will fire but alerts won't pop up.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Styled clinic cross icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = "Clinic Logo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MedRem",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("app_title")
                    )
                }
                
                // Button to create a custom Family Profile
                TextButton(
                    onClick = { showAddProfileDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("add_profile_button")
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Profile")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Profile", fontSize = 14.sp)
                }
            }

            // --- PROFILES FILTER TAG REGION (Unified View) ---
            Text(
                text = "Profiles Filter",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "All Profiles" tag
                item {
                    val isSelected = selectedProfileId == 0L
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFamilyMember(0L) },
                        label = { Text("All Profiles") },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("profile_pill_all")
                    )
                }
                
                items(familyMembers, key = { it.id }) { member ->
                    val isSelected = selectedProfileId == member.id
                    val color = remember(member.colorHex) {
                        try { Color(android.graphics.Color.parseColor(member.colorHex)) }
                        catch (e: Exception) { Color.Gray }
                    }
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFamilyMember(member.id) },
                        label = {
                            Text(
                                text = member.name + (if (member.isMe) " (Me)" else ""),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else color
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(16.dp)) }
                        } else {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        },
                        modifier = Modifier.testTag("profile_pill_${member.name.lowercase()}")
                    )
                }
            }

            // --- SEARCH BAR ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search medicine name or notes...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("medicine_search"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // --- NAVIGATION TABS ---
            TabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Schedules", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = { Icon(Icons.Default.Vaccines, contentDescription = "Schedules List") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Taken History", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = { Icon(Icons.Default.History, contentDescription = "Logs History") }
                )
            }

            // --- MAIN LIST REGION ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab == 0) {
                    // Schedules list
                    if (filteredMedications.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.HealthAndSafety,
                            title = "No active reminders",
                            description = if (searchQuery.isNotEmpty()) "No results match your search." else "Tap the '+' floating button to set up your first weekly or interval medication reminder."
                        )
                    } else {
                        val now = System.currentTimeMillis()
                        val snoozedMeds = filteredMedications.filter { it.isActive && it.snoozedUntil > now }
                        val nextMeds = filteredMedications.filter { !it.isActive || it.snoozedUntil <= now }
                        val sortedNextMeds = nextMeds.sortedBy { med ->
                            if (med.isActive) {
                                com.example.reminder.ReminderScheduler.getNextTriggerTime(med, now)
                            } else {
                                Long.MAX_VALUE
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (snoozedMeds.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Snoozed Medications",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(snoozedMeds, key = { "snoozed_${it.id}" }) { medication ->
                                    val member = familyMembers.find { it.id == medication.familyMemberId }
                                    MedicationReminderCard(
                                        medication = medication,
                                        member = member,
                                        onTakeDose = { viewModel.logMedicationDose(medication, "TAKEN") },
                                        onSkipDose = { viewModel.logMedicationDose(medication, "SKIPPED") },
                                        onToggleActive = { active ->
                                            viewModel.addOrUpdateMedication(
                                                id = medication.id,
                                                name = medication.name,
                                                dosage = medication.dosage,
                                                instructions = medication.instructions,
                                                scheduleType = medication.scheduleType,
                                                daysOfWeekCommaSeparated = medication.daysOfWeekCommaSeparated,
                                                intervalHours = medication.intervalHours,
                                                startTime = medication.startTime,
                                                startDate = medication.startDate,
                                                familyMemberId = medication.familyMemberId,
                                                isActive = active,
                                                snoozedUntil = medication.snoozedUntil
                                            )
                                        },
                                        onEdit = {
                                            medicationToEdit = medication
                                            showAddMedicationDialog = true
                                        },
                                        onDelete = { viewModel.deleteMedication(medication) }
                                    )
                                }
                            }

                            if (sortedNextMeds.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Next Medications",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(sortedNextMeds, key = { "next_${it.id}" }) { medication ->
                                    val member = familyMembers.find { it.id == medication.familyMemberId }
                                    MedicationReminderCard(
                                        medication = medication,
                                        member = member,
                                        onTakeDose = { viewModel.logMedicationDose(medication, "TAKEN") },
                                        onSkipDose = { viewModel.logMedicationDose(medication, "SKIPPED") },
                                        onToggleActive = { active ->
                                            viewModel.addOrUpdateMedication(
                                                id = medication.id,
                                                name = medication.name,
                                                dosage = medication.dosage,
                                                instructions = medication.instructions,
                                                scheduleType = medication.scheduleType,
                                                daysOfWeekCommaSeparated = medication.daysOfWeekCommaSeparated,
                                                intervalHours = medication.intervalHours,
                                                startTime = medication.startTime,
                                                startDate = medication.startDate,
                                                familyMemberId = medication.familyMemberId,
                                                isActive = active,
                                                snoozedUntil = medication.snoozedUntil
                                            )
                                        },
                                        onEdit = {
                                            medicationToEdit = medication
                                            showAddMedicationDialog = true
                                        },
                                        onDelete = { viewModel.deleteMedication(medication) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Log History List
                    val filteredRecords = remember(doseRecords, selectedProfileId, searchQuery) {
                        val base = if (selectedProfileId == 0L) {
                            doseRecords
                        } else {
                            val targetMemberName = familyMembers.find { it.id == selectedProfileId }?.name
                            doseRecords.filter { it.familyMemberName == targetMemberName }
                        }
                        if (searchQuery.isEmpty()) {
                            base
                        } else {
                            base.filter {
                                it.medicationName.contains(searchQuery, ignoreCase = true) ||
                                it.dosage.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredRecords.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Timeline,
                            title = "No history recorded",
                            description = "No intakes logged or skipped yet for the selected profile."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredRecords, key = { it.id }) { record ->
                                DoseHistoryCard(
                                    record = record,
                                    onDeleteHistory = { viewModel.deleteDoseRecord(record.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- FLOATING ACTION BUTTON ---
        LargeFloatingActionButton(
            onClick = {
                medicationToEdit = null
                showAddMedicationDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_medication_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add medicine schedule",
                modifier = Modifier.size(32.dp)
            )
        }
    }

    // --- DIALOGS ---

    // 1. Add/Edit Family Member Dialog
    if (showAddProfileDialog) {
        AddFamilyProfileDialog(
            profiles = familyMembers,
            onDismiss = { showAddProfileDialog = false },
            onSave = { id, name, colorHex, isMe ->
                viewModel.addOrUpdateFamilyMember(id ?: 0L, name, colorHex, isMe)
                showAddProfileDialog = false
            },
            onDelete = { member ->
                viewModel.deleteFamilyMember(member)
            }
        )
    }

    // 2. Add/Edit Medication Intake Schedule
    if (showAddMedicationDialog) {
        val editingMed = medicationToEdit
        AddEditMedicationScheduleDialog(
            medication = editingMed,
            profiles = familyMembers,
            initialSelectedProfileId = selectedProfileId,
            onDismiss = { showAddMedicationDialog = false },
            onSave = { name, dosage, notes, schedType, days, hours, time, profileId, active ->
                viewModel.addOrUpdateMedication(
                    id = editingMed?.id ?: 0,
                    name = name,
                    dosage = dosage,
                    instructions = notes,
                    scheduleType = schedType,
                    daysOfWeekCommaSeparated = days,
                    intervalHours = hours,
                    startTime = time,
                    startDate = editingMed?.startDate ?: System.currentTimeMillis(),
                    familyMemberId = profileId,
                    isActive = active
                )
                showAddMedicationDialog = false
            }
        )
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty list illustration",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

fun formatNextTriggerTime(triggerTime: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
    val sdfDate = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
            "Today at ${sdfTime.format(cal.time)}"
        }
        cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> {
            "Tomorrow at ${sdfTime.format(cal.time)}"
        }
        else -> {
            "${sdfDate.format(cal.time)} at ${sdfTime.format(cal.time)}"
        }
    }
}

@Composable
fun MedicationReminderCard(
    medication: Medication,
    member: FamilyMember?,
    onTakeDose: () -> Unit,
    onSkipDose: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val memberColor = remember(member?.colorHex) {
        try { Color(android.graphics.Color.parseColor(member?.colorHex ?: "#757575")) }
        catch (e: Exception) { Color.Gray }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (medication.isActive) memberColor.copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .testTag("med_card_${medication.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (medication.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // First row - medication name and user align left, active align to right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = medication.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (medication.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Family member tag badge
                    Box(
                        modifier = Modifier
                            .background(memberColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, memberColor.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = member?.name ?: "Unknown",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = memberColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = medication.isActive,
                        onCheckedChange = { onToggleActive(it) },
                        modifier = Modifier
                            .scale(0.8f)
                            .testTag("med_switch_${medication.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second row - Dosage align left, Edit and Delete align to right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dosage details
                Text(
                    text = "Dosage: " + medication.dosage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = "Edit Schedule",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete medicine",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Note/Instructions
            if (medication.instructions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Instructions: ${medication.instructions}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Display next scheduled medication time / snoozed status
            if (medication.isActive) {
                Spacer(modifier = Modifier.height(10.dp))
                val now = System.currentTimeMillis()
                val isCurrentlySnoozed = medication.snoozedUntil > now
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrentlySnoozed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCurrentlySnoozed) Icons.Default.Alarm else Icons.Default.Schedule,
                        contentDescription = "Next Dose Icon",
                        tint = if (isCurrentlySnoozed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentlySnoozed) {
                            "Snoozed until: " + formatNextTriggerTime(medication.snoozedUntil)
                        } else {
                            "Next dose: " + formatNextTriggerTime(com.example.reminder.ReminderScheduler.getNextTriggerTime(medication, now))
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentlySnoozed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Quick log actions
            if (medication.isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onTakeDose()
                            Toast.makeText(context, "${medication.name} logged as TAKEN!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("action_take_${medication.name.lowercase()}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Taken", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onSkipDose()
                            Toast.makeText(context, "${medication.name} logged as SKIPPED.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("action_skip_${medication.name.lowercase()}"),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy()//Default outline border
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Skipped", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Reminder?") },
            text = { Text("Are you sure you want to stop tracking and delete the reminder schedule for ${medication.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DoseHistoryCard(
    record: DoseRecord,
    onDeleteHistory: () -> Unit
) {
    val dateString = remember(record.actualTime) {
        try {
            val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            sdf.format(Date(record.actualTime))
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    val isTaken = record.status == "TAKEN"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_record_${record.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status icon circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isTaken) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTaken) Icons.Default.DoneOutline else Icons.Default.Block,
                        contentDescription = "Status icon",
                        tint = if (isTaken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.medicationName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${record.familyMemberName})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Status: ${record.status} | $dateString",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            IconButton(onClick = onDeleteHistory) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete dose log",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Dialog: Add/Manage family profile
@Composable
fun AddFamilyProfileDialog(
    profiles: List<FamilyMember>,
    onDismiss: () -> Unit,
    onSave: (id: Long?, name: String, colorHex: String, isMe: Boolean) -> Unit,
    onDelete: (FamilyMember) -> Unit
) {
    var editingProfileId by remember { mutableStateOf<Long?>(null) }
    var editingProfileIsMe by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var inlineErrorMsg by remember { mutableStateOf<String?>(null) }

    // Hex codes for selecting colored tags
    val colors = listOf(
        "#EF5350", // Coral Pink-Red
        "#4CAF50", // Leaf Green
        "#2196F3", // Blue
        "#FF9800", // Amber Orange
        "#9C27B0", // Deep Purple
        "#FF3D00", // Crimson Orange
        "#00BCD4"  // Water Cyan
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_profile_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (editingProfileId != null) "Edit Profile" else "Manage & Add Profiles",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                if (editingProfileId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Editing Mode",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(
                            onClick = {
                                editingProfileId = null
                                editingProfileIsMe = false
                                name = ""
                                selectedColorIndex = 0
                            }
                        ) {
                            Text("Switch to Create Profile")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.trim().isNotEmpty()) inlineErrorMsg = null
                    },
                    label = { Text("Profile Name (e.g. Grandma, Dad)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    singleLine = true,
                    isError = inlineErrorMsg != null
                )

                if (inlineErrorMsg != null) {
                    Text(
                        text = inlineErrorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Choose Tag Color",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colors.forEachIndexed { index, hex ->
                        val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColorIndex == index) 3.dp else 0.dp,
                                    color = if (selectedColorIndex == index) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                inlineErrorMsg = "Name cannot be empty!"
                            } else {
                                onSave(editingProfileId, name, colors[selectedColorIndex], editingProfileIsMe)
                            }
                        },
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        Text(if (editingProfileId != null) "Update Profile" else "Save Profile")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Existing Profiles",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(8.dp))

                profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val profileColor = remember(profile.colorHex) {
                            try { Color(android.graphics.Color.parseColor(profile.colorHex)) }
                            catch (e: Exception) { Color.Gray }
                        }
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(profileColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = profile.name + (if (profile.isMe) " (Me)" else ""),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = {
                                editingProfileId = profile.id
                                editingProfileIsMe = profile.isMe
                                name = profile.name
                                val idx = colors.indexOf(profile.colorHex)
                                if (idx >= 0) {
                                    selectedColorIndex = idx
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("edit_profile_${profile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (!profile.isMe) {
                            IconButton(
                                onClick = { onDelete(profile) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("delete_profile_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Profile",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Required",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add or Edit Medication Reminders
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationScheduleDialog(
    medication: Medication?,
    profiles: List<FamilyMember>,
    initialSelectedProfileId: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        dosage: String,
        notes: String,
        scheduleType: String,
        daysOfWeekCommaSeparated: String,
        intervalHours: Int,
        startTime: String,
        profileId: Long,
        isActive: Boolean
    ) -> Unit
) {
    // Basic Form Fields
    var name by remember { mutableStateOf(medication?.name ?: "") }
    var dosage by remember { mutableStateOf(medication?.dosage ?: "") }
    var notes by remember { mutableStateOf(medication?.instructions ?: "") }
    var scheduleType by remember { mutableStateOf(medication?.scheduleType ?: "WEEKLY") } // "WEEKLY" or "INTERVAL"
    
    // Schedule Configuration: Weekly custom days
    val daysList = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val checkedDays = remember {
        val initialMap = mutableStateMapOf<String, Boolean>()
        daysList.forEach { day ->
            initialMap[day] = medication?.daysOfWeekCommaSeparated?.contains(day) ?: false
        }
        initialMap
    }
    
    // Schedule Configuration: Interval hours
    var intervalHours by remember { mutableStateOf(medication?.intervalHours ?: 8) }
    
    // Time & Starting Configuration (AM/PM option with split hours & minutes)
    var is12Hour by remember { mutableStateOf(true) }
    
    var selectedHour by remember { mutableStateOf("") }
    var selectedMinute by remember { mutableStateOf("") }
    var isAm by remember { mutableStateOf(true) }
    
    // Selected Profile FK
    var selectedProfileId by remember {
        val defaultId = if (initialSelectedProfileId != 0L) {
            initialSelectedProfileId
        } else {
            profiles.firstOrNull { it.isMe }?.id ?: profiles.firstOrNull()?.id ?: 0L
        }
        mutableStateOf(medication?.familyMemberId ?: defaultId)
    }

    // Input Validation Error Messages
    var nameError by remember { mutableStateOf<String?>(null) }
    var dosageError by remember { mutableStateOf<String?>(null) }
    var timeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(medication) {
        name = medication?.name ?: ""
        dosage = medication?.dosage ?: ""
        notes = medication?.instructions ?: ""
        scheduleType = medication?.scheduleType ?: "WEEKLY"
        intervalHours = medication?.intervalHours ?: 8
        
        daysList.forEach { day ->
            checkedDays[day] = medication?.daysOfWeekCommaSeparated?.contains(day) ?: false
        }
        
        val defaultId = if (initialSelectedProfileId != 0L) {
            initialSelectedProfileId
        } else {
            profiles.firstOrNull { it.isMe }?.id ?: profiles.firstOrNull()?.id ?: 0L
        }
        selectedProfileId = medication?.familyMemberId ?: defaultId
        
        nameError = null
        dosageError = null
        timeError = null

        val parts = (medication?.startTime ?: "08:30").split(":")
        var hourInt = 8
        var minuteInt = 30
        if (parts.size == 2) {
            hourInt = parts[0].toIntOrNull() ?: 8
            minuteInt = parts[1].toIntOrNull() ?: 30
        }
        
        isAm = hourInt < 12
        selectedHour = if (is12Hour) {
            val h12 = if (hourInt % 12 == 0) 12 else hourInt % 12
            h12.toString()
        } else {
            hourInt.toString().padStart(2, '0')
        }
        selectedMinute = minuteInt.toString().padStart(2, '0')
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp)
                .testTag("add_medication_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = if (medication == null) "New Reminder Schedule" else "Edit Medication Schedule",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Field 4: Profile selector dropdown list (Moved to top)
                item {
                    Text(
                        text = "Assign to Profile",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Simple Row of Profile choices to avoid heavy spinner dropdowns
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profiles.forEach { profile ->
                            val color = remember(profile.colorHex) {
                                try { Color(android.graphics.Color.parseColor(profile.colorHex)) }
                                catch (e: Exception) { Color.Gray }
                            }
                            val isSelected = selectedProfileId == profile.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedProfileId = profile.id },
                                label = { Text(profile.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                },
                                modifier = Modifier.testTag("profile_option_${profile.name.lowercase()}")
                            )
                        }
                    }
                }

                // Field 1: Name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) nameError = null
                        },
                        label = { Text("Medication Name (e.g. Paracetamol)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("medication_name_input"),
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }

                // Field 2: Dosage
                item {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = {
                            dosage = it
                            if (it.isNotBlank()) dosageError = null
                        },
                        label = { Text("Dosage (e.g. 1 Tablet, 10ml)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("medication_dosage_input"),
                        singleLine = true,
                        isError = dosageError != null,
                        supportingText = dosageError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }

                // Field 5: Schedule Type
                item {
                    Text(
                        text = "Reminders Type Selection",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { scheduleType = "WEEKLY" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("type_weekly"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "WEEKLY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                contentColor = if (scheduleType == "WEEKLY") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        ) {
                            Text("Weekly Days")
                        }
                        
                        Button(
                            onClick = { scheduleType = "INTERVAL" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("type_interval"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "INTERVAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                contentColor = if (scheduleType == "INTERVAL") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        ) {
                            Text("Interval Hours")
                        }
                    }
                }

                // Field 6: Conditional Parameters Configuration
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (scheduleType == "WEEKLY") {
                            Text(
                                text = "Weekly Custom Days selection",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                daysList.forEach { day ->
                                    val isChecked = checkedDays[day] == true
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = { checkedDays[day] = !isChecked },
                                        label = { Text(day, fontSize = 12.sp) },
                                        modifier = Modifier.testTag("day_chip_$day")
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "How often? (Interval in Hours)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val intervals = listOf(4, 6, 8, 12, 24)
                                intervals.forEach { hrs ->
                                    val isSelected = intervalHours == hrs
                                    ElevatedFilterChip(
                                        selected = isSelected,
                                        onClick = { intervalHours = hrs },
                                        label = { Text("${hrs}h") },
                                        modifier = Modifier.testTag("interval_chip_$hrs")
                                    )
                                }
                            }
                        }
                    }
                }

                // Field 7: Alert Trigger Starting Time (AM/PM option with split hours and minutes)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Alert Starting Time",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Toggle between 12-Hour format and 24-Hour format
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!is12Hour) {
                                        val hr = selectedHour.toIntOrNull() ?: 8
                                        isAm = hr < 12
                                        val h12 = if (hr % 12 == 0) 12 else hr % 12
                                        selectedHour = h12.toString()
                                        is12Hour = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (is12Hour) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    contentColor = if (is12Hour) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("12-Hour (AM/PM)", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (is12Hour) {
                                        var hr = selectedHour.toIntOrNull() ?: 8
                                        if (hr < 1 || hr > 12) hr = 12
                                        val h24 = if (isAm) {
                                            if (hr == 12) 0 else hr
                                        } else {
                                            if (hr == 12) 12 else hr + 12
                                        }
                                        selectedHour = h24.toString().padStart(2, '0')
                                        is12Hour = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!is12Hour) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    contentColor = if (!is12Hour) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("24-Hour", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Split Hour and Minute Inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedHour,
                                onValueChange = {
                                    val cleaned = it.filter { char -> char.isDigit() }
                                    if (cleaned.length <= 2) {
                                        selectedHour = cleaned
                                        timeError = null
                                    }
                                },
                                label = { Text("Hour") },
                                placeholder = { Text("--") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("time_hour_input"),
                                singleLine = true,
                                isError = timeError != null
                            )

                            Text(
                                text = ":",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            OutlinedTextField(
                                value = selectedMinute,
                                onValueChange = {
                                    val cleaned = it.filter { char -> char.isDigit() }
                                    if (cleaned.length <= 2) {
                                        selectedMinute = cleaned
                                        timeError = null
                                    }
                                },
                                label = { Text("Minute") },
                                placeholder = { Text("--") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("time_minute_input"),
                                singleLine = true,
                                isError = timeError != null
                            )

                            if (is12Hour) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Button(
                                        onClick = { isAm = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                            contentColor = if (isAm) Color.White else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.height(34.dp).width(50.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("AM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { isAm = false },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isAm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                            contentColor = if (!isAm) Color.White else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.height(34.dp).width(50.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("PM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (timeError != null) {
                            Text(
                                text = timeError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Save or Cancel Buttons
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                var hasError = false
                                if (name.trim().isEmpty()) {
                                    nameError = "Medication name is required!"
                                    hasError = true
                                }
                                if (dosage.trim().isEmpty()) {
                                    dosageError = "Dosage amount is required!"
                                    hasError = true
                                }

                                val hrVal = selectedHour.toIntOrNull()
                                val minVal = selectedMinute.toIntOrNull()
                                
                                if (hrVal == null || minVal == null) {
                                    timeError = "Hour and minute are required!"
                                    hasError = true
                                } else {
                                    if (is12Hour) {
                                        if (hrVal < 1 || hrVal > 12) {
                                            timeError = "Hour must be 1 to 12"
                                            hasError = true
                                        }
                                    } else {
                                        if (hrVal < 0 || hrVal > 23) {
                                            timeError = "Hour must be 0 to 23"
                                            hasError = true
                                        }
                                    }
                                    if (minVal < 0 || minVal > 59) {
                                        timeError = "Minute must be 0 to 59"
                                        hasError = true
                                    }
                                }

                                if (!hasError && hrVal != null && minVal != null) {
                                    val daysCommaStr = if (scheduleType == "WEEKLY") {
                                        checkedDays.filter { it.value }.keys.joinToString(",")
                                    } else {
                                        ""
                                    }

                                    val finalHr24 = if (is12Hour) {
                                        if (isAm) {
                                            if (hrVal == 12) 0 else hrVal
                                        } else {
                                            if (hrVal == 12) 12 else hrVal + 12
                                        }
                                    } else {
                                        hrVal
                                    }
                                    val formattedStartTime = "${finalHr24.toString().padStart(2, '0')}:${minVal.toString().padStart(2, '0')}"

                                    onSave(
                                        name.trim(),
                                        dosage.trim(),
                                        notes.trim(),
                                        scheduleType,
                                        daysCommaStr,
                                        intervalHours,
                                        formattedStartTime,
                                        selectedProfileId,
                                        medication?.isActive ?: true
                                    )
                                }
                            },
                            modifier = Modifier.testTag("save_medication_button")
                        ) {
                            Text(if (medication == null) "Create Schedule" else "Save Changes")
                        }
                    }
                }
            }
        }
    }
}


