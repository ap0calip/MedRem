package com.example

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import com.example.viewmodel.MedicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearHistoryDialog(
    viewModel: MedicationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    val profileSing by viewModel.currentProfileSing.collectAsState()
    val profilePlur by viewModel.currentProfilePlur.collectAsState()
    val medicationSing by viewModel.currentMedicationSing.collectAsState()
    val medicationPlur by viewModel.currentMedicationPlur.collectAsState()
    val takenLabel by viewModel.currentTakenLabel.collectAsState()

    val doseRecords by viewModel.doseRecords.collectAsState()
    val allMedications by viewModel.allMedications.collectAsState()
    val familyMembers by viewModel.familyMembers.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: By Item/Medication, 1: By Date / Status, 2: Clear All
    var searchQuery by remember { mutableStateOf("") }

    // Confirmation dialog states
    var showConfirmClearAll by remember { mutableStateOf(false) }
    var itemToClear by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var profileToClear by remember { mutableStateOf<String?>(null) }
    var daysCutoffToClear by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var statusToClear by remember { mutableStateOf<String?>(null) }

    ThemedDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.65f else 0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = (config.screenHeightDp * if (isLandscape) 0.92f else 0.90f).dp)
                .padding(vertical = 12.dp)
                .imePadding(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Clear History",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs within dialog
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = Color.Transparent,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "By $medicationSing",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "More Options",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "Clear All",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Option 1: Clear History of the Item / Medication
                            ClearByItemSection(
                                doseRecords = doseRecords,
                                allMedications = allMedications,
                                familyMembers = familyMembers,
                                medicationSing = medicationSing,
                                medicationPlur = medicationPlur,
                                profileSing = profileSing,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onRequestClearItem = { medId, medName ->
                                    itemToClear = Pair(medId, medName)
                                }
                            )
                        }
                        1 -> {
                            // Option 2: More Options (By Profile, Older Than, By Status)
                            MoreOptionsSection(
                                doseRecords = doseRecords,
                                familyMembers = familyMembers,
                                profileSing = profileSing,
                                profilePlur = profilePlur,
                                takenLabel = takenLabel,
                                onRequestClearProfile = { profileName ->
                                    profileToClear = profileName
                                },
                                onRequestClearOlderThan = { days, label ->
                                    daysCutoffToClear = Pair(days, label)
                                },
                                onRequestClearStatus = { status ->
                                    statusToClear = status
                                }
                            )
                        }
                        2 -> {
                            // Option 3: Clear All History
                            ClearAllSection(
                                totalRecords = doseRecords.size,
                                medicationPlur = medicationPlur,
                                profilePlur = profilePlur,
                                onRequestClearAll = {
                                    showConfirmClearAll = true
                                }
                            )
                        }
                    }
                }

                // Footer Close Button
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // --- CONFIRMATION DIALOGS ---

    // 1. Confirm Clear All History
    if (showConfirmClearAll) {
        ThemedAlertDialog(
            onDismissRequest = { showConfirmClearAll = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Clear All History?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text("Are you sure you want to permanently delete ALL ${doseRecords.size} history records across all $medicationPlur and $profilePlur? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = doseRecords.size
                        viewModel.clearAllDoseRecords {
                            Toast.makeText(context, "Cleared all $count history records", Toast.LENGTH_SHORT).show()
                        }
                        showConfirmClearAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Confirm Clear History of specific Item/Medication
    itemToClear?.let { (medId, medName) ->
        val recordCount = doseRecords.count { it.medicationId == medId || it.medicationName.equals(medName, ignoreCase = true) }
        val med = allMedications.find { it.id == medId || it.name.equals(medName, ignoreCase = true) }
        val member = familyMembers.find {
            (med != null && it.id == med.familyMemberId) || it.name.equals(doseRecords.find { d -> d.medicationId == medId || d.medicationName.equals(medName, ignoreCase = true) }?.familyMemberName, ignoreCase = true)
        }
        val memberColor = remember(member?.colorHex) {
            try { Color(android.graphics.Color.parseColor(member?.colorHex ?: "#757575")) } catch (e: Exception) { Color.Gray }
        }
        ThemedAlertDialog(
            onDismissRequest = { itemToClear = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(memberColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear History for $medName?",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text("Are you sure you want to delete all $recordCount history record${if (recordCount == 1) "" else "s"} for \"$medName\"?\n\nActive schedules and future reminders will remain intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDoseRecordsByMedication(medId, medName) { deleted ->
                            Toast.makeText(context, "Cleared $deleted record(s) for $medName", Toast.LENGTH_SHORT).show()
                        }
                        itemToClear = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear History", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToClear = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Confirm Clear by Profile
    profileToClear?.let { memberName ->
        val recordCount = doseRecords.count { it.familyMemberName.equals(memberName, ignoreCase = true) }
        val member = familyMembers.find { it.name.equals(memberName, ignoreCase = true) }
        val memberColor = remember(member?.colorHex) {
            try { Color(android.graphics.Color.parseColor(member?.colorHex ?: "#757575")) } catch (e: Exception) { Color.Gray }
        }
        ThemedAlertDialog(
            onDismissRequest = { profileToClear = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(memberColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear $memberName History?",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text("Are you sure you want to delete all $recordCount history record${if (recordCount == 1) "" else "s"} for $profileSing \"$memberName\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDoseRecordsByFamilyMember(memberName) { deleted ->
                            Toast.makeText(context, "Cleared $deleted record(s) for $memberName", Toast.LENGTH_SHORT).show()
                        }
                        profileToClear = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear History", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToClear = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Confirm Clear Older Than
    daysCutoffToClear?.let { (days, label) ->
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        val recordCount = doseRecords.count { it.actualTime < cutoff }
        ThemedAlertDialog(
            onDismissRequest = { daysCutoffToClear = null },
            title = {
                Text(
                    text = "Clear $label?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to delete $recordCount history record${if (recordCount == 1) "" else "s"} older than $days days?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDoseRecordsOlderThan(days) { deleted ->
                            Toast.makeText(context, "Cleared $deleted record(s) older than $days days", Toast.LENGTH_SHORT).show()
                        }
                        daysCutoffToClear = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { daysCutoffToClear = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Confirm Clear by Status
    statusToClear?.let { status ->
        val displayStatus = if (status == "TAKEN") takenLabel else if (status == "SKIPPED") "Skipped" else status
        val recordCount = doseRecords.count { it.status.equals(status, ignoreCase = true) }
        ThemedAlertDialog(
            onDismissRequest = { statusToClear = null },
            title = {
                Text(
                    text = "Clear All $displayStatus Records?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to delete all $recordCount history record${if (recordCount == 1) "" else "s"} with status \"$displayStatus\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDoseRecordsByStatus(status) { deleted ->
                            Toast.makeText(context, "Cleared $deleted \"$displayStatus\" record(s)", Toast.LENGTH_SHORT).show()
                        }
                        statusToClear = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { statusToClear = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// SUB-SECTIONS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearByItemSection(
    doseRecords: List<DoseRecord>,
    allMedications: List<Medication>,
    familyMembers: List<FamilyMember>,
    medicationSing: String,
    medicationPlur: String,
    profileSing: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRequestClearItem: (Long, String) -> Unit
) {
    var selectedProfileFilterId by remember { mutableStateOf<Long?>(null) }

    // Collect all distinct item names present in history OR in active medications
    val itemStatsList = remember(doseRecords, allMedications, familyMembers) {
        val statsMap = mutableMapOf<String, ItemHistoryStat>()

        // 1. First add stats from dose records
        for (record in doseRecords) {
            val key = record.medicationName.trim()
            val current = statsMap[key]
            if (current != null) {
                statsMap[key] = current.copy(
                    count = current.count + 1,
                    lastLogged = maxOf(current.lastLogged, record.actualTime)
                )
            } else {
                val med = allMedications.find { it.id == record.medicationId || it.name.equals(key, ignoreCase = true) }
                val member = familyMembers.find {
                    (med != null && it.id == med.familyMemberId) || it.name.equals(record.familyMemberName, ignoreCase = true)
                }
                val profileName = record.familyMemberName.ifBlank {
                    member?.name ?: "Me"
                }
                val profileColorHex = member?.colorHex ?: "#757575"
                val profileId = member?.id ?: med?.familyMemberId ?: 0L

                statsMap[key] = ItemHistoryStat(
                    medicationId = record.medicationId,
                    medicationName = key,
                    dosage = record.dosage.ifBlank { med?.dosage ?: "" },
                    profileName = profileName,
                    profileColorHex = profileColorHex,
                    profileId = profileId,
                    count = 1,
                    lastLogged = record.actualTime
                )
            }
        }

        // 2. Also add active medications that may have 0 records logged
        for (med in allMedications) {
            val key = med.name.trim()
            if (!statsMap.containsKey(key)) {
                val member = familyMembers.find { it.id == med.familyMemberId }
                val profile = member?.name ?: "Me"
                val profileColorHex = member?.colorHex ?: "#757575"
                statsMap[key] = ItemHistoryStat(
                    medicationId = med.id,
                    medicationName = key,
                    dosage = med.dosage,
                    profileName = profile,
                    profileColorHex = profileColorHex,
                    profileId = med.familyMemberId,
                    count = 0,
                    lastLogged = 0L
                )
            }
        }

        statsMap.values.sortedWith(
            compareByDescending<ItemHistoryStat> { it.count }
                .thenByDescending { it.lastLogged }
                .thenBy { it.medicationName }
        )
    }

    val filteredList = remember(itemStatsList, searchQuery, selectedProfileFilterId) {
        itemStatsList.filter { item ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                item.medicationName.contains(searchQuery, ignoreCase = true) ||
                item.profileName.contains(searchQuery, ignoreCase = true) ||
                item.dosage.contains(searchQuery, ignoreCase = true)
            }
            val matchesProfile = if (selectedProfileFilterId == null) true else {
                item.profileId == selectedProfileFilterId ||
                familyMembers.find { it.id == selectedProfileFilterId }?.name.equals(item.profileName, ignoreCase = true)
            }
            matchesSearch && matchesProfile
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Description
        Text(
            text = "Select a specific $medicationSing to clear its recorded history while keeping active schedules intact.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Profile filter chips if multiple profiles exist
        if (familyMembers.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isAllSelected = selectedProfileFilterId == null
                FilterChip(
                    selected = isAllSelected,
                    onClick = { selectedProfileFilterId = null },
                    label = { Text("All", fontSize = 12.sp, fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                familyMembers.forEach { member ->
                    val isSelected = selectedProfileFilterId == member.id
                    val color = remember(member.colorHex) {
                        try { Color(android.graphics.Color.parseColor(member.colorHex)) }
                        catch (e: Exception) { Color.Gray }
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedProfileFilterId = if (isSelected) null else member.id
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = member.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            selectedBorderColor = color,
                            borderWidth = if (isSelected) 1.5.dp else 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // Search Bar if more than 3 items
        if (itemStatsList.size > 3) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search $medicationPlur...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No $medicationPlur found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredList.forEach { item ->
                    val profileColor = remember(item.profileColorHex) {
                        try { Color(android.graphics.Color.parseColor(item.profileColorHex)) }
                        catch (e: Exception) { Color.Gray }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, profileColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .testTag("clear_history_item_${item.medicationName.lowercase().replace(" ", "_")}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(profileColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.medicationName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Profile badge using selected color
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = profileColor.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, profileColor.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = item.profileName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = profileColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (item.dosage.isNotBlank()) {
                                    Text(
                                        text = item.dosage,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (item.count > 0) "${item.count} history log${if (item.count == 1) "" else "s"}" else "No history recorded",
                                    fontSize = 12.sp,
                                    fontWeight = if (item.count > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (item.count > 0) profileColor else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledTonalButton(
                                onClick = { onRequestClearItem(item.medicationId, item.medicationName) },
                                enabled = item.count > 0,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreOptionsSection(
    doseRecords: List<DoseRecord>,
    familyMembers: List<FamilyMember>,
    profileSing: String,
    profilePlur: String,
    takenLabel: String,
    onRequestClearProfile: (String) -> Unit,
    onRequestClearOlderThan: (Int, String) -> Unit,
    onRequestClearStatus: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Clear by Date Range (Older Than) ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear Older Records",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep recent logs while purging older historical data:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val now = System.currentTimeMillis()
                    val days7Cutoff = now - (7L * 24 * 60 * 60 * 1000L)
                    val days30Cutoff = now - (30L * 24 * 60 * 60 * 1000L)
                    val days90Cutoff = now - (90L * 24 * 60 * 60 * 1000L)

                    val count7 = doseRecords.count { it.actualTime < days7Cutoff }
                    val count30 = doseRecords.count { it.actualTime < days30Cutoff }
                    val count90 = doseRecords.count { it.actualTime < days90Cutoff }

                    DatePresetButton(
                        label = "> 7 Days",
                        subtext = "$count7 logs",
                        enabled = count7 > 0,
                        onClick = { onRequestClearOlderThan(7, "Records older than 7 days") },
                        modifier = Modifier.weight(1f)
                    )
                    DatePresetButton(
                        label = "> 30 Days",
                        subtext = "$count30 logs",
                        enabled = count30 > 0,
                        onClick = { onRequestClearOlderThan(30, "Records older than 30 days") },
                        modifier = Modifier.weight(1f)
                    )
                    DatePresetButton(
                        label = "> 90 Days",
                        subtext = "$count90 logs",
                        enabled = count90 > 0,
                        onClick = { onRequestClearOlderThan(90, "Records older than 90 days") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- 2. Clear by Profile / Category ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear by $profileSing",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Clear history entries associated with a specific $profileSing:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    familyMembers.forEach { member ->
                        val count = doseRecords.count { it.familyMemberName.equals(member.name, ignoreCase = true) }
                        val color = remember(member.colorHex) {
                            try { Color(android.graphics.Color.parseColor(member.colorHex)) } catch (e: Exception) { Color.Gray }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = member.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = color
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "($count records)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { onRequestClearProfile(member.name) },
                                    enabled = count > 0,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Clear by Status ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear by Status",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Purge only specific entry outcomes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                val skippedCount = doseRecords.count { it.status.equals("SKIPPED", ignoreCase = true) }
                val takenCount = doseRecords.count { it.status.equals("TAKEN", ignoreCase = true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRequestClearStatus("SKIPPED") },
                        enabled = skippedCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clear Skipped", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("$skippedCount records", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedButton(
                        onClick = { onRequestClearStatus("TAKEN") },
                        enabled = takenCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clear $takenLabel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("$takenCount records", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePresetButton(
    label: String,
    subtext: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtext,
                fontSize = 12.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ClearAllSection(
    totalRecords: Int,
    medicationPlur: String,
    profilePlur: String,
    onRequestClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Clear Entire History",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This will permanently wipe all $totalRecords recorded logs across all $medicationPlur and $profilePlur from the local database.\n\nAll reminder schedules and configuration settings will be preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRequestClearAll,
                    enabled = totalRecords > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_history_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (totalRecords > 0) "Clear All ($totalRecords Records)" else "History is Empty",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class ItemHistoryStat(
    val medicationId: Long,
    val medicationName: String,
    val dosage: String,
    val profileName: String,
    val profileColorHex: String = "#757575",
    val profileId: Long = 0L,
    val count: Int,
    val lastLogged: Long
)
