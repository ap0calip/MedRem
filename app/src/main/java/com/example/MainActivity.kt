package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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
    var tutorialStepIndex by remember { mutableStateOf(-1) }
    
    // State lists from Room Database (Flow-connected)
    val familyMembers by viewModel.familyMembers.collectAsState()
    val filteredMedications by viewModel.filteredMedications.collectAsState()
    val doseRecords by viewModel.doseRecords.collectAsState()
    
    // Dynamic labels
    val activeLabelMode by viewModel.labelMode.collectAsState()
    val profileSing by viewModel.currentProfileSing.collectAsState()
    val profilePlur by viewModel.currentProfilePlur.collectAsState()
    val medicationSing by viewModel.currentMedicationSing.collectAsState()
    val medicationPlur by viewModel.currentMedicationPlur.collectAsState()
    val dosageSing by viewModel.currentDosageSing.collectAsState()
    val dosagePlur by viewModel.currentDosagePlur.collectAsState()
    val takenLabel by viewModel.currentTakenLabel.collectAsState()
    
    var showCustomLabelsDialog by remember { mutableStateOf(false) }
    
    // Filter variables
    val selectedProfileId by viewModel.selectedFamilyMemberId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // UI control states
    var showAddMedicationDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showDataTransferDialog by remember { mutableStateOf(false) }
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showMenu by remember { mutableStateOf(false) }
                
                // Menu icon on the left
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .testTag("nav_menu_button")
                            .tutorialHighlight(1, tutorialStepIndex, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Navigation Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add $profileSing") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                            onClick = {
                                showAddProfileDialog = true
                                showMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Labels", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                            onClick = { /* Just a header */ },
                            enabled = false
                        )
                        DropdownMenuItem(
                            text = { Text("Medication (Profile, Med, Dosage)") },
                            leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                            modifier = Modifier.padding(start = 16.dp),
                            onClick = {
                                viewModel.setLabelMode("MEDICATION")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Item (Category, Item, Description)") },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                            modifier = Modifier.padding(start = 16.dp),
                            onClick = {
                                viewModel.setLabelMode("ITEM")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom Labels") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            modifier = Modifier.padding(start = 16.dp),
                            onClick = {
                                showCustomLabelsDialog = true
                                showMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Backup & Restore") },
                            leadingIcon = { Icon(Icons.Default.ImportExport, contentDescription = null) },
                            onClick = {
                                showDataTransferDialog = true
                                showMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Tutorial") },
                            leadingIcon = { Icon(Icons.Default.Help, contentDescription = null) },
                            onClick = {
                                tutorialStepIndex = 0
                                showMenu = false
                            }
                        )
                    }
                }

                // Title centered
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (activeLabelMode == "MEDICATION") Icons.Default.MedicalServices else Icons.Default.Category,
                        contentDescription = "Active Label Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp).padding(end = 8.dp)
                    )
                    Text(
                        text = "MedRem",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("app_title")
                    )
                }
                
                // Empty box to balance the menu icon on the left
                Box(modifier = Modifier.size(48.dp))
            }

            // --- ACTIVE ALARMS RUNNING BANNER ---
            ActiveAlarmsBanner(viewModel = viewModel)

            // --- PROFILES FILTER TAG REGION (Unified View) ---
            Text(
                text = "$profilePlur Filter",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .tutorialHighlight(2, tutorialStepIndex, RoundedCornerShape(12.dp)),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "All Profiles" tag
                item {
                    val isSelected = selectedProfileId == 0L
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFamilyMember(0L) },
                        label = { Text("All $profilePlur") },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, stringResource(R.string.selected_desc), modifier = Modifier.size(16.dp)) }
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
                                text = member.name + (if (member.isMe) stringResource(R.string.profile_me_suffix) else ""),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else color
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, stringResource(R.string.selected_desc), modifier = Modifier.size(16.dp)) }
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
                placeholder = { Text("Search ${medicationSing.lowercase()} or ${dosageSing.lowercase()}...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_icon_desc)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_desc))
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("medicine_search")
                    .tutorialHighlight(3, tutorialStepIndex, RoundedCornerShape(12.dp)),
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
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .tutorialHighlight(4, tutorialStepIndex, RoundedCornerShape(8.dp)),
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("$medicationPlur Schedules", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = { Icon(Icons.Default.Vaccines, contentDescription = stringResource(R.string.schedules_list_desc)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text(stringResource(R.string.tab_history), fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.logs_history_desc)) }
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateView(
                                    icon = Icons.Default.HealthAndSafety,
                                    title = "No active reminders",
                                    description = if (searchQuery.isNotEmpty()) "No results match your search." else "Tap the '+' floating button to set up your first weekly or interval ${medicationSing.lowercase()} reminder."
                                )
                            }
                            DeveloperInfoCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 16.dp)
                            )
                        }
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
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (snoozedMeds.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Snoozed $medicationPlur",
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
                                        dosageSing = dosageSing,
                                        medicationSing = medicationSing,
                                        takenLabel = takenLabel,
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
                                        onDelete = { deleteHistory -> viewModel.deleteMedication(medication, deleteHistory) }
                                    )
                                }
                            }

                            if (sortedNextMeds.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Next $medicationPlur",
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
                                        dosageSing = dosageSing,
                                        medicationSing = medicationSing,
                                        takenLabel = takenLabel,
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
                                        onDelete = { deleteHistory -> viewModel.deleteMedication(medication, deleteHistory) }
                                    )
                                }
                            }

                            // Moving DeveloperInfoCard here below Next Medication card
                            item {
                                DeveloperInfoCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 16.dp)
                                )
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateView(
                                    icon = Icons.Default.Timeline,
                                    title = "No history recorded",
                                    description = "No ${takenLabel.lowercase()} or skipped yet for the selected ${profileSing.lowercase()}."
                                )
                            }
                            DeveloperInfoCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredRecords, key = { it.id }) { record ->
                                DoseHistoryCard(
                                    record = record,
                                    takenLabel = takenLabel,
                                    onDeleteHistory = { viewModel.deleteDoseRecord(record.id) }
                                )
                            }

                            // Moving DeveloperInfoCard here below Dose History logs
                            item {
                                DeveloperInfoCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 16.dp)
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
                .testTag("add_medication_fab")
                .tutorialHighlight(5, tutorialStepIndex, RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add schedule",
                modifier = Modifier.size(32.dp)
            )
        }

        val tutorialSteps = remember(profileSing, profilePlur, medicationSing, medicationPlur, dosageSing, dosagePlur) {
            listOf(
                TutorialStep(
                    title = "Welcome to the MedRem!",
                    content = "This interactive guide will walk you through the key features of the application to help you keep track of your ${profilePlur.lowercase()}, ${medicationPlur.lowercase()}, and ${dosagePlur.lowercase()} effectively. Tap 'Next' to begin."
                ),
                TutorialStep(
                    title = "Top-Left Navigation Menu",
                    content = "Tap this menu icon to manage ${profilePlur.lowercase()}, change label presets, or back up your schedules."
                ),
                TutorialStep(
                    title = "Category Filters",
                    content = "Filter by ${profileSing.lowercase()} to quickly find your schedules and logs."
                ),
                TutorialStep(
                    title = "Smart Item Search",
                    content = "Quickly find any ${medicationSing.lowercase()} schedule, or ${dosageSing.lowercase()} by typing its name or details here."
                ),
                TutorialStep(
                    title = "Schedules vs. History",
                    content = "Easily toggle between schedules and history to view logged entries."
                ),
                TutorialStep(
                    title = "Create a New Schedule",
                    content = "Tap this '+' floating button to set up a new schedule, define its ${dosageSing.lowercase()}, and configure active timers."
                ),
                TutorialStep(
                    title = "You're Ready to Go!",
                    content = "You're all set! Enjoy organizing your reminder schedules."
                )
            )
        }

        if (tutorialStepIndex >= 0 && tutorialStepIndex < tutorialSteps.size) {
            TutorialGuideBanner(
                currentStep = tutorialStepIndex,
                steps = tutorialSteps,
                onNext = {
                    if (tutorialStepIndex < tutorialSteps.size - 1) {
                        tutorialStepIndex++
                    } else {
                        tutorialStepIndex = -1
                    }
                },
                onBack = {
                    if (tutorialStepIndex > 0) {
                        tutorialStepIndex--
                    }
                },
                onDismiss = {
                    tutorialStepIndex = -1
                }
            )
        }
    }

    // --- DIALOGS ---

    // 1. Add/Edit Family Member Dialog
    if (showAddProfileDialog) {
        AddFamilyProfileDialog(
            profiles = familyMembers,
            profileSing = profileSing,
            profilePlur = profilePlur,
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
            profileSing = profileSing,
            profilePlur = profilePlur,
            medicationSing = medicationSing,
            medicationPlur = medicationPlur,
            dosageSing = dosageSing,
            dosagePlur = dosagePlur,
            takenLabel = takenLabel,
            onDismiss = { showAddMedicationDialog = false },
            onSave = { name, dosage, notes, schedType, days, hours, time, startDate, profileId, active, autoReset ->
                viewModel.addOrUpdateMedication(
                    id = editingMed?.id ?: 0,
                    name = name,
                    dosage = dosage,
                    instructions = notes,
                    scheduleType = schedType,
                    daysOfWeekCommaSeparated = days,
                    intervalHours = hours,
                    startTime = time,
                    startDate = startDate,
                    familyMemberId = profileId,
                    isActive = active,
                    autoReset = autoReset
                )
                showAddMedicationDialog = false
            }
        )
    }

    if (showCustomLabelsDialog) {
        var profSing by remember { mutableStateOf(profileSing) }
        var profPlur by remember { mutableStateOf(profilePlur) }
        var medSing by remember { mutableStateOf(medicationSing) }
        var medPlur by remember { mutableStateOf(medicationPlur) }
        var doseSing by remember { mutableStateOf(dosageSing) }
        var dosePlur by remember { mutableStateOf(dosagePlur) }
        var takeLabelVal by remember { mutableStateOf(takenLabel) }

        Dialog(onDismissRequest = { showCustomLabelsDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("custom_labels_dialog"),
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
                        text = "Customize Labels",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Provide your own custom singular and plural terms for Items, Categories, Descriptions, and Completed actions.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Categories Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
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

                    Text(
                        text = "Items Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
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

                    Text(
                        text = "Descriptions Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
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

                    Text(
                        text = "Action Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = takeLabelVal,
                        onValueChange = { takeLabelVal = it },
                        label = { Text("Action Label") },
                        placeholder = { Text("e.g. Completed") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomLabelsDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
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
                                    showCustomLabelsDialog = false
                                } else {
                                    Toast.makeText(context, "All fields are required for Custom Labels!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    if (showDataTransferDialog) {
        DataTransferDialog(
            viewModel = viewModel,
            onDismiss = { showDataTransferDialog = false }
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

fun formatDateMmDdYyyy(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
fun MedicationReminderCard(
    medication: Medication,
    member: FamilyMember?,
    dosageSing: String,
    medicationSing: String,
    takenLabel: String,
    onTakeDose: () -> Unit,
    onSkipDose: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (Boolean) -> Unit
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
                    text = "$dosageSing: " + medication.dosage,
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
                        contentDescription = "Next Icon",
                        tint = if (isCurrentlySnoozed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentlySnoozed) {
                            "Snoozed until: " + formatNextTriggerTime(medication.snoozedUntil)
                        } else {
                            "Next: " + formatNextTriggerTime(com.example.reminder.ReminderScheduler.getNextTriggerTime(medication, now))
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
                            Toast.makeText(context, "${medication.name} logged as ${takenLabel.uppercase()}!", Toast.LENGTH_SHORT).show()
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
                        Text("$takenLabel", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                        Text("Skipped", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        var deleteHistory by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $medicationSing Reminder?") },
            text = {
                Column {
                    Text("Are you sure you want to stop tracking and delete the reminder schedule for ${medication.name}?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteHistory = !deleteHistory }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = deleteHistory,
                            onCheckedChange = { deleteHistory = it },
                            modifier = Modifier.testTag("delete_history_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Also delete all history logs for this ${medicationSing.lowercase()}.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleteHistory)
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
    takenLabel: String,
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
                    val displayStatus = if (record.status == "TAKEN") takenLabel else if (record.status == "SKIPPED") "Skipped" else record.status
                    Text(
                        text = "Status: $displayStatus | $dateString",
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
    profileSing: String,
    profilePlur: String,
    onDismiss: () -> Unit,
    onSave: (id: Long?, name: String, colorHex: String, isMe: Boolean) -> Unit,
    onDelete: (FamilyMember) -> Unit
) {
    val context = LocalContext.current
    var editingProfileId by remember { mutableStateOf<Long?>(null) }
    var editingProfileIsMe by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var inlineErrorMsg by remember { mutableStateOf<String?>(null) }
    var profileToDelete by remember { mutableStateOf<FamilyMember?>(null) }

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
                    text = if (editingProfileId != null) "Edit $profileSing" else "Manage & Add $profilePlur",
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
                            text = stringResource(R.string.editing_mode),
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
                            Text("Switch to Create $profileSing")
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
                    label = { Text("$profileSing Name") },
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
                    text = stringResource(R.string.choose_tag_color),
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
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                inlineErrorMsg = context.getString(R.string.error_empty_name)
                            } else {
                                onSave(editingProfileId, name, colors[selectedColorIndex], editingProfileIsMe)
                            }
                        },
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        Text(if (editingProfileId != null) "Update $profileSing" else "Save $profileSing")
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
                    text = "Existing $profilePlur",
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
                            text = profile.name + (if (profile.isMe) stringResource(R.string.profile_me_suffix) else ""),
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
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (!profile.isMe) {
                            IconButton(
                                onClick = { profileToDelete = profile },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("delete_profile_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.required),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (profileToDelete != null) {
        val member = profileToDelete
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete $profileSing?") },
            text = { Text("Are you sure you want to delete the $profileSing for \"${member?.name ?: ""}\"? All history will remain but the $profileSing itself will be removed. Action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        member?.let { onDelete(it) }
                        profileToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_profile_button")
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { profileToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_profile_button")
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

// Dialog: Add or Edit Medication Reminders
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScheduleDialog(
    medication: Medication?,
    profiles: List<FamilyMember>,
    initialSelectedProfileId: Long = 0L,
    profileSing: String,
    profilePlur: String,
    medicationSing: String,
    medicationPlur: String,
    dosageSing: String,
    dosagePlur: String,
    takenLabel: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        dosage: String,
        notes: String,
        scheduleType: String,
        daysOfWeekCommaSeparated: String,
        intervalHours: Int,
        startTime: String,
        startDate: Long,
        profileId: Long,
        isActive: Boolean,
        autoReset: Boolean
    ) -> Unit
) {
    // Basic Form Fields
    val context = LocalContext.current
    var name by remember { mutableStateOf(medication?.name ?: "") }
    var dosage by remember { mutableStateOf(medication?.dosage ?: "") }
    var notes by remember { mutableStateOf(medication?.instructions ?: "") }
    var scheduleType by remember { mutableStateOf(medication?.scheduleType ?: "WEEKLY") } // "WEEKLY", "INTERVAL" or "CUSTOM"
    
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

    // Custom Configuration: Repeat value, unit, and start date
    var customRepeatValue by remember { mutableStateOf("1") }
    var customRepeatUnit by remember { mutableStateOf("days") }
    var isAutoReset by remember { mutableStateOf(medication?.autoReset ?: false) }
    var customStartDate by remember { mutableStateOf(System.currentTimeMillis()) }
    
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
        
        customRepeatValue = if (medication?.scheduleType == "CUSTOM") medication.intervalHours.toString() else "1"
        customRepeatUnit = if (medication?.scheduleType == "CUSTOM") medication.daysOfWeekCommaSeparated else "days"
        customStartDate = if (medication?.scheduleType == "CUSTOM") medication.startDate else System.currentTimeMillis()
        
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
                        text = if (medication == null) "New $medicationSing Schedule" else "Edit $medicationSing Schedule",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Field 4: Profile selector dropdown list (Moved to top)
                item {
                    Text(
                        text = "Assign to $profileSing",
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
                        label = { Text("$medicationSing Name") },
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
                        label = { Text(dosageSing) },
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
                        text = stringResource(R.string.schedule_type_selection_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Button(
                            onClick = { scheduleType = "WEEKLY" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("type_weekly"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "WEEKLY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                contentColor = if (scheduleType == "WEEKLY") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            val parts = stringResource(R.string.schedule_weekly_days).split(" ")
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(parts.getOrNull(0) ?: "Weekly", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (parts.size > 1) {
                                    Text(parts.getOrNull(1) ?: "Days", fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Spacer(modifier = Modifier.height(9.dp))
                                }
                            }
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
                            shape = RoundedCornerShape(0.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            val parts = stringResource(R.string.schedule_interval_hours).split(" ")
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(parts.getOrNull(0) ?: "Interval", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (parts.size > 1) {
                                    Text(parts.getOrNull(1) ?: "Hours", fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Spacer(modifier = Modifier.height(9.dp))
                                }
                            }
                        }

                        Button(
                            onClick = { scheduleType = "CUSTOM" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("type_custom"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scheduleType == "CUSTOM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                contentColor = if (scheduleType == "CUSTOM") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Custom", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Repeat", fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // Field 6: Conditional Parameters Configuration
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (scheduleType == "WEEKLY") {
                            Text(
                                text = stringResource(R.string.weekly_custom_days_title),
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
                        } else if (scheduleType == "INTERVAL") {
                            Text(
                                text = stringResource(R.string.how_often_interval_title),
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
                        } else if (scheduleType == "CUSTOM") {
                            // Part 1: Label "Repeat Every", textbox to input number, drop-down to select from: hours, days, weeks, months, years
                            Text(
                                text = "Repeat Every",
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
                                OutlinedTextField(
                                    value = customRepeatValue,
                                    onValueChange = { newValue ->
                                        val filtered = newValue.filter { it.isDigit() }
                                        customRepeatValue = filtered
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("custom_repeat_value_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    placeholder = { Text("1") }
                                )

                                var expandedDropdown by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("custom_repeat_unit_box")
                                ) {
                                    OutlinedTextField(
                                        value = customRepeatUnit.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
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
                                        val units = listOf("hours", "days", "weeks", "months", "years")
                                        units.forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }) },
                                                onClick = {
                                                    customRepeatUnit = unit
                                                    expandedDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAutoReset = !isAutoReset }
                            ) {
                                Checkbox(
                                    checked = isAutoReset,
                                    onCheckedChange = { isAutoReset = it }
                                )
                                Text("Auto Reset")
                            }
                            if (isAutoReset) {
                                Text(
                                    text = "The Alarm reset to the time of $takenLabel and Skipped.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Part 2: Calendar to select the first day mm/dd/yyyy.
                            Text(
                                text = "First Day",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            var showDatePicker by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = formatDateMmDdYyyy(customStartDate),
                                    onValueChange = {},
                                    readOnly = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Select calendar day"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("custom_start_date_input"),
                                    singleLine = true
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Transparent)
                                        .clickable { showDatePicker = true }
                                )
                            }

                            if (showDatePicker) {
                                val datePickerState = rememberDatePickerState(
                                    initialSelectedDateMillis = customStartDate
                                )
                                DatePickerDialog(
                                    onDismissRequest = { showDatePicker = false },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                datePickerState.selectedDateMillis?.let {
                                                    customStartDate = it
                                                }
                                                showDatePicker = false
                                            }
                                        ) {
                                            Text("OK")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePicker = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                        }
                    }
                }

                // Field 7: Alert Trigger Starting Time (AM/PM option with split hours and minutes)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.alert_starting_time_title),
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
                                Text(stringResource(R.string.twelve_hour_format), fontSize = 12.sp)
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
                                Text(stringResource(R.string.twenty_four_hour_format), fontSize = 12.sp)
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
                                label = { Text(stringResource(R.string.hour_label)) },
                                placeholder = { Text(stringResource(R.string.placeholder_dash)) },
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
                                label = { Text(stringResource(R.string.minute_label)) },
                                placeholder = { Text(stringResource(R.string.placeholder_dash)) },
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
                                        Text(stringResource(R.string.am_text), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        Text(stringResource(R.string.pm_text), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.btn_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                var hasError = false
                                if (name.trim().isEmpty()) {
                                    nameError = "$medicationSing name is required"
                                    hasError = true
                                }
                                if (dosage.trim().isEmpty()) {
                                    dosageError = "$dosageSing is required"
                                    hasError = true
                                }

                                val hrVal = selectedHour.toIntOrNull()
                                val minVal = selectedMinute.toIntOrNull()
                                
                                if (hrVal == null || minVal == null) {
                                    timeError = context.getString(R.string.error_hour_minute_required)
                                    hasError = true
                                } else {
                                    if (is12Hour) {
                                        if (hrVal < 1 || hrVal > 12) {
                                            timeError = context.getString(R.string.error_hour_1_to_12)
                                            hasError = true
                                        }
                                    } else {
                                        if (hrVal < 0 || hrVal > 23) {
                                            timeError = context.getString(R.string.error_hour_0_to_23)
                                            hasError = true
                                        }
                                    }
                                    if (minVal < 0 || minVal > 59) {
                                        timeError = context.getString(R.string.error_minute_0_to_59)
                                        hasError = true
                                    }
                                }

                                if (!hasError && hrVal != null && minVal != null) {
                                    val daysCommaStr = when (scheduleType) {
                                        "WEEKLY" -> checkedDays.filter { it.value }.keys.joinToString(",")
                                        "CUSTOM" -> customRepeatUnit
                                        else -> ""
                                    }

                                    val finalIntervalHours = when (scheduleType) {
                                        "CUSTOM" -> customRepeatValue.toIntOrNull() ?: 1
                                        else -> intervalHours
                                    }

                                    val finalStartDate = if (scheduleType == "CUSTOM") {
                                        customStartDate
                                    } else {
                                        medication?.startDate ?: System.currentTimeMillis()
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
                                        finalIntervalHours,
                                        formattedStartTime,
                                        finalStartDate,
                                        selectedProfileId,
                                        medication?.isActive ?: true,
                                        isAutoReset
                                    )
                                }
                            },
                            modifier = Modifier.testTag("save_medication_button")
                        ) {
                            Text(if (medication == null) "Create $medicationSing Schedule" else "Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveAlarmsBanner(
    modifier: Modifier = Modifier,
    viewModel: MedicationViewModel
) {
    val context = LocalContext.current
    val activeAlarms by com.example.reminder.ActiveAlarmManager.activeAlarms.collectAsState()
    val familyMembers by viewModel.familyMembers.collectAsState()
    val takenLabel by viewModel.currentTakenLabel.collectAsState()

    if (activeAlarms.isEmpty()) return

    val defaultErrorColor = MaterialTheme.colorScheme.error

    // Elegant animated pulsator for active banner
    val infiniteTransition = rememberInfiniteTransition(label = "banner_p")
    val alphaColorMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "banner_p"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("active_alarms_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = defaultErrorColor.copy(alpha = alphaColorMultiplier)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, defaultErrorColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Active alarms screaming",
                    tint = defaultErrorColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACTIVE MEDICAL ALARMS:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = defaultErrorColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Alarm items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeAlarms.forEach { alarm ->
                    val member = familyMembers.find { it.id == alarm.familyMemberId }
                    val memberName = member?.name ?: "Me"
                    val memberColorHex = member?.colorHex ?: "#B00020"
                    val parsedColor = remember(memberColorHex) {
                        try { Color(android.graphics.Color.parseColor(memberColorHex)) }
                        catch (e: Exception) { defaultErrorColor }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, parsedColor.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "$memberName: ${alarm.medName}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = parsedColor
                            )
                            if (alarm.dosage.isNotEmpty()) {
                                Text(
                                    text = alarm.dosage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Individual action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Taken Button
                                Button(
                                    onClick = {
                                        val svcIntent = Intent(context, com.example.reminder.MedicationAlarmService::class.java).apply {
                                            action = com.example.reminder.MedicationAlarmService.ACTION_TAKE
                                            putExtra("MED_ID", alarm.medId)
                                            putExtra("MED_NAME", alarm.medName)
                                            putExtra("MED_DOSAGE", alarm.dosage)
                                        }
                                        context.startService(svcIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = parsedColor),
                                    modifier = Modifier.weight(1f).height(38.dp).testTag("banner_action_take_${alarm.medId}"),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Check, takenLabel, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(takenLabel, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                // Snooze Button
                                OutlinedButton(
                                    onClick = {
                                        val svcIntent = Intent(context, com.example.reminder.MedicationAlarmService::class.java).apply {
                                            action = com.example.reminder.MedicationAlarmService.ACTION_DISMISS
                                            putExtra("MED_ID", alarm.medId)
                                        }
                                        context.startService(svcIntent)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.weight(1.0f).height(38.dp).testTag("banner_action_snooze_${alarm.medId}"),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Alarm, "Snooze", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Snooze", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Skip Button
                                OutlinedButton(
                                    onClick = {
                                        val svcIntent = Intent(context, com.example.reminder.MedicationAlarmService::class.java).apply {
                                            action = com.example.reminder.MedicationAlarmService.ACTION_SKIP
                                            putExtra("MED_ID", alarm.medId)
                                            putExtra("MED_NAME", alarm.medName)
                                            putExtra("MED_DOSAGE", alarm.dosage)
                                        }
                                        context.startService(svcIntent)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = defaultErrorColor),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, defaultErrorColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(38.dp).testTag("banner_action_skip_${alarm.medId}"),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Block, "Skip", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Skip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (activeAlarms.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val svcIntent = Intent(context, com.example.reminder.MedicationAlarmService::class.java).apply {
                                action = com.example.reminder.MedicationAlarmService.ACTION_TAKE_ALL
                            }
                            context.startService(svcIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = defaultErrorColor),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("banner_action_take_all"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Take All Due", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            val svcIntent = Intent(context, com.example.reminder.MedicationAlarmService::class.java).apply {
                                action = com.example.reminder.MedicationAlarmService.ACTION_DISMISS_ALL
                            }
                            context.startService(svcIntent)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = defaultErrorColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, defaultErrorColor),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("banner_action_snooze_all"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Snooze All", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DataTransferDialog(
    viewModel: MedicationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profileSing by viewModel.currentProfileSing.collectAsState()
    val profilePlur by viewModel.currentProfilePlur.collectAsState()
    val medicationSing by viewModel.currentMedicationSing.collectAsState()
    val medicationPlur by viewModel.currentMedicationPlur.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Export, 1 = Import

    // --- Export states ---
    var selectedExportProfileId by remember { mutableStateOf(0L) } // 0 = All Profiles
    val familyMembers by viewModel.familyMembers.collectAsState()

    // --- Import states ---
    var importText by remember { mutableStateOf("") }
    var importPreviewProfilesCount by remember { mutableStateOf(0) }
    var importPreviewMedsCount by remember { mutableStateOf(0) }
    var importValidationErrorMsg by remember { mutableStateOf<String?>(null) }
    var hasParsedSuccessfully by remember { mutableStateOf(false) }

    // Logic to validate live input in real-time
    LaunchedEffect(importText) {
        if (importText.isBlank()) {
            importValidationErrorMsg = null
            importPreviewProfilesCount = 0
            importPreviewMedsCount = 0
            hasParsedSuccessfully = false
            return@LaunchedEffect
        }
        try {
            val rootObj = org.json.JSONObject(importText)
            val pArr = rootObj.optJSONArray("profiles") ?: org.json.JSONArray()
            val mArr = rootObj.optJSONArray("medications") ?: org.json.JSONArray()
            importPreviewProfilesCount = pArr.length()
            importPreviewMedsCount = mArr.length()
            importValidationErrorMsg = null
            hasParsedSuccessfully = true
        } catch (e: Exception) {
            importValidationErrorMsg = context.getString(R.string.error_invalid_json, e.localizedMessage ?: "")
            importPreviewProfilesCount = 0
            importPreviewMedsCount = 0
            hasParsedSuccessfully = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("data_transfer_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ImportExport,
                        contentDescription = stringResource(R.string.data_transfer_icon_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.backup_restore_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Tab Row
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text(stringResource(R.string.export_schedules_tab), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text(stringResource(R.string.import_backup_tab), fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (activeTab == 0) {
                    // EXPORT TAB UI
                    Text(
                        text = stringResource(R.string.export_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Target profile option
                    Text(
                        text = "Target $profileSing Scope:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            val isSelected = selectedExportProfileId == 0L
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedExportProfileId = 0L },
                                label = { Text("All $profilePlur") },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, stringResource(R.string.selected_desc), modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                        items(familyMembers, key = { "exp_${it.id}" }) { member ->
                            val isSelected = selectedExportProfileId == member.id
                            val color = remember(member.colorHex) {
                                try { Color(android.graphics.Color.parseColor(member.colorHex)) }
                                catch (e: Exception) { Color.Gray }
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedExportProfileId = member.id },
                                label = {
                                    Text(
                                        text = member.name,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else color
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            )
                        }
                    }

                    // Export triggers
                    Button(
                        onClick = {
                            val profileFilter = if (selectedExportProfileId == 0L) null else selectedExportProfileId
                            val jsonString = viewModel.exportSchedulesJson(profileFilter)

                            // Copy to clipboard
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            if (clipboardManager != null) {
                                val clipData = android.content.ClipData.newPlainText(context.getString(R.string.app_name) + " Export", jsonString)
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("copy_export_json_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_icon_desc))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_copy_export_json), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val profileFilter = if (selectedExportProfileId == 0L) null else selectedExportProfileId
                            val jsonString = viewModel.exportSchedulesJson(profileFilter)

                            // Launch Android share sheet
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name) + " Schedules Backup")
                                putExtra(Intent.EXTRA_TEXT, jsonString)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.btn_share_backup)))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("share_export_json_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, stringResource(R.string.share_icon_desc))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_share_backup), fontWeight = FontWeight.Bold)
                    }

                } else {
                    // IMPORT TAB UI
                    Text(
                        text = stringResource(R.string.import_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Clipboard Quick paste action
                    Button(
                        onClick = {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val itemText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!itemText.isNullOrBlank()) {
                                importText = itemText
                                Toast.makeText(context, context.getString(R.string.toast_clipboard_pasted), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_clipboard_empty), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, stringResource(R.string.paste_icon_desc))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_quick_paste), fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text(stringResource(R.string.paste_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("import_text_field"),
                        maxLines = 15,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Realtime verification indicator block
                    if (importValidationErrorMsg != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = importValidationErrorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else if (hasParsedSuccessfully) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.backup_verified),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "- Found ${profilePlur.lowercase()}: $importPreviewProfilesCount\n- Found ${medicationPlur.lowercase()}: $importPreviewMedsCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Action proceed trigger
                    Button(
                        onClick = {
                            viewModel.importSchedulesJson(importText) { result ->
                                if (result.success) {
                                    val profWord = if (result.importedProfilesCount == 1) profileSing.lowercase() else profilePlur.lowercase()
                                    val medWord = if (result.importedMedicationsCount == 1) medicationSing.lowercase() else medicationPlur.lowercase()
                                    Toast.makeText(
                                        context,
                                        "Import successful! Added ${result.importedProfilesCount} $profWord and ${result.importedMedicationsCount} $medWord.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(
                                        context,
                                        result.errorMessage ?: context.getString(R.string.import_failed_default),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = hasParsedSuccessfully,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("execute_import_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.import_icon_desc))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_proceed_import), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dismiss button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).testTag("close_transfer_dialog_button")
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    }
}

@Composable
fun DeveloperInfoCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val developerLink = stringResource(id = R.string.developer_link)
    val developerTag = stringResource(id = R.string.developer_info_card_tag)
    val logoDesc = stringResource(id = R.string.company_logo_description)
    val prefixText = stringResource(id = R.string.developer_info_prefix)
    val nameText = stringResource(id = R.string.developer_name)
    val suffixText = stringResource(id = R.string.developer_info_suffix)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                uriHandler.openUri(developerLink)
            }
            .testTag(developerTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.m4),
                    contentDescription = logoDesc,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val annotatedText = buildAnnotatedString {
                val prefix = prefixText.trim()
                val name = nameText.trim()
                val suffix = suffixText.trim()
                append("$prefix ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(name)
                }
                append(suffix)
            }
            Text(
                text = annotatedText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

data class TutorialStep(val title: String, val content: String)

@Composable
fun TutorialGuideBanner(
    currentStep: Int,
    steps: List<TutorialStep>,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    if (currentStep < 0 || currentStep >= steps.size) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (currentStep == 5) PaddingValues(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp) else PaddingValues(16.dp)),
        contentAlignment = if (currentStep == 5) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .shadow(12.dp, shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header with step indicator and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step ${currentStep + 1} of ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tutorial",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title of step
                Text(
                    text = steps[currentStep].title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description content
                Text(
                    text = steps[currentStep].content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Skip")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Back")
                            }
                        }

                        Button(
                            onClick = onNext,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (currentStep == steps.size - 1) "Finish" else "Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.tutorialHighlight(
    stepIndex: Int,
    currentStep: Int,
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier {
    if (currentStep != stepIndex) return this

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    return this
        .graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }
        .border(
            width = 3.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
            shape = shape
        )
}


