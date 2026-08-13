package com.example.reminder

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.ui.theme.MyApplicationTheme
import com.example.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: AlarmActivity started")

        // Lockscreen bypass setup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        updateFields(intent)

        setContent {
            MyApplicationTheme {
                AlarmScreen(
                    onActionTaken = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFields(intent)
    }

    private fun updateFields(intent: Intent) {
        val medId = intent.getLongExtra("MED_ID", -1L)
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val dosage = intent.getStringExtra("MED_DOSAGE") ?: ""
        val instructions = intent.getStringExtra("MED_INSTRUCTIONS") ?: ""
        val familyMemberId = intent.getLongExtra("FAMILY_MEMBER_ID", -1L)

        if (medId != -1L) {
            val reminder = ActiveReminder(medId, medName, dosage, instructions, familyMemberId)
            ActiveAlarmManager.addAlarm(reminder)
        }
        Log.d(TAG, "updateFields: processed medId=$medId, name=$medName")
    }

    companion object {
        private const val TAG = "AlarmActivity"
    }
}

@Composable
fun AlarmScreen(
    onActionTaken: () -> Unit
) {
    val context = LocalContext.current
    val activeAlarms by ActiveAlarmManager.activeAlarms.collectAsState()

    var showSnoozeDurationDialog by remember { mutableStateOf(false) }
    var snoozeTargetAlarmId by remember { mutableStateOf<Long?>(null) } // null means Snooze All

    val prefs = remember(context) { context.getSharedPreferences("MedRemPrefs", Context.MODE_PRIVATE) }
    val labelMode = remember(prefs) { prefs.getString("label_mode", "ITEM") ?: "ITEM" }

    val medicationSing = remember(prefs, labelMode) {
        when (labelMode) {
            "MEDICATION" -> "Medication"
            "ITEM" -> "Item"
            "CUSTOM" -> prefs.getString("custom_med_sing", "Medication") ?: "Medication"
            else -> "Medication"
        }
    }

    val medicationPlur = remember(prefs, labelMode) {
        when (labelMode) {
            "MEDICATION" -> "Medications"
            "ITEM" -> "Items"
            "CUSTOM" -> prefs.getString("custom_med_plur", "Medications") ?: "Medications"
            else -> "Medications"
        }
    }

    val takenLabel = remember(prefs, labelMode) {
        when (labelMode) {
            "MEDICATION" -> "Taken"
            "ITEM" -> "Completed"
            "CUSTOM" -> prefs.getString("custom_taken_label", "Taken") ?: "Taken"
            else -> "Taken"
        }
    }

    androidx.activity.compose.BackHandler {
        // Map back button to the Snooze All action to stop sound and dismiss activity
        val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
            action = MedicationAlarmService.ACTION_DISMISS_ALL
        }
        context.startService(svcIntent)
        onActionTaken()
    }

    LaunchedEffect(activeAlarms) {
        if (activeAlarms.isEmpty()) {
            onActionTaken()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Title Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = stringResource(R.string.company_logo_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$medicationSing Reminder",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (activeAlarms.size == 1) {
                // High-Contrast Alert Card displaying only 1 active alarm
                val rem = activeAlarms.first()
                SingleAlarmLayout(
                    reminder = rem,
                    medicationSing = medicationSing,
                    takenLabel = takenLabel,
                    onActionTaken = onActionTaken,
                    onSnoozeClick = { medId ->
                        snoozeTargetAlarmId = medId
                        showSnoozeDurationDialog = true
                    }
                )
            } else if (activeAlarms.isNotEmpty()) {
                // Multi-Alarm Layout: Beautiful list of cards with individual actions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "You have ${activeAlarms.size} ${if (activeAlarms.size == 1) medicationSing.lowercase() else medicationPlur.lowercase()} due now:",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(activeAlarms, key = { it.medId }) { rem ->
                            MultiAlarmCard(
                                reminder = rem,
                                takenLabel = takenLabel,
                                onAction = { action ->
                                    val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                                        this.action = action
                                        putExtra("MED_ID", rem.medId)
                                        putExtra("MED_NAME", rem.medName)
                                        putExtra("MED_DOSAGE", rem.dosage)
                                    }
                                    context.startService(svcIntent)
                                },
                                onSnoozeClick = { medId ->
                                    snoozeTargetAlarmId = medId
                                    showSnoozeDurationDialog = true
                                }
                            )
                        }
                    }
                }

                // Global Actions Area for Multi-Alarm Screens
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            snoozeTargetAlarmId = null
                            showSnoozeDurationDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("alarm_action_dismiss_all"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.btn_snooze_all_label), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        onClick = {
                            val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                                action = MedicationAlarmService.ACTION_TAKE_ALL
                            }
                            context.startService(svcIntent)
                            onActionTaken()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("alarm_action_take_all"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("$takenLabel All", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            } else {
                // Empty state fallback / safe layout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    var targetMedication by remember(snoozeTargetAlarmId) { mutableStateOf<com.example.data.entity.Medication?>(null) }
    LaunchedEffect(snoozeTargetAlarmId) {
        val tid = snoozeTargetAlarmId
        if (tid != null) {
            withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    targetMedication = db.dao().getMedicationById(tid)
                } catch (e: Exception) {
                    Log.e("AlarmActivity", "Failed to fetch target medication: ${e.message}")
                }
            }
        } else {
            targetMedication = null
        }
    }

    if (showSnoozeDurationDialog) {
        com.example.ui.SnoozeDurationDialog(
            medication = targetMedication,
            onDismiss = { showSnoozeDurationDialog = false },
            onConfirm = { minutes ->
                val targetId = snoozeTargetAlarmId
                val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                    if (targetId == null) {
                        action = MedicationAlarmService.ACTION_DISMISS_ALL
                    } else {
                        action = MedicationAlarmService.ACTION_DISMISS
                        putExtra("MED_ID", targetId)
                    }
                    putExtra("SNOOZE_MINUTES", minutes)
                }
                context.startService(svcIntent)
                showSnoozeDurationDialog = false
                onActionTaken()
            }
        )
    }
}

@Composable
fun SingleAlarmLayout(
    reminder: ActiveReminder,
    medicationSing: String,
    takenLabel: String,
    onActionTaken: () -> Unit,
    onSnoozeClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val defaultProfileName = stringResource(R.string.notification_default_profile_name)
    var familyMemberName by remember(defaultProfileName) { mutableStateOf(defaultProfileName) }
    var familyMemberColorHex by remember { mutableStateOf("#B00020") } // default deep red
    var soundName by remember { mutableStateOf("") }

    LaunchedEffect(reminder.medId, reminder.familyMemberId) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val member = if (reminder.familyMemberId != -1L) db.dao().getFamilyMemberById(reminder.familyMemberId) else null
                val med = if (reminder.medId != -1L) db.dao().getMedicationById(reminder.medId) else null
                if (member != null) {
                    familyMemberName = member.name
                    familyMemberColorHex = member.colorHex
                }
                soundName = com.example.SoundUtils.getSoundName(context, med?.soundUri, member?.soundUri)
            } catch (e: Exception) {
                Log.e("AlarmActivity", "Failed to fetch family member detail: ${e.message}")
            }
        }
    }

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val memberColor = remember(familyMemberColorHex, defaultPrimary) {
        try {
            Color(android.graphics.Color.parseColor(familyMemberColorHex))
        } catch (e: Exception) {
            defaultPrimary
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-Contrast Alert Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = memberColor.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, memberColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = stringResource(R.string.company_logo_description),
                    tint = memberColor,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scalePulse)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // First row (big and bold text) - [profile name]: [medication name]
                Text(
                    text = "$familyMemberName: ${reminder.medName}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        lineHeight = 38.sp
                    ),
                    color = memberColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("alarm_med_name")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Second row (little and normal text) - [dosage]
                Text(
                    text = reminder.dosage,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 18.sp
                    ),
                    color = memberColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("alarm_dosage")
                )

                if (soundName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sound: $soundName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = memberColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Action Buttons Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action 2: Skip
                OutlinedButton(
                    onClick = {
                        val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                            action = MedicationAlarmService.ACTION_SKIP
                            putExtra("MED_ID", reminder.medId)
                            putExtra("MED_NAME", reminder.medName)
                            putExtra("MED_DOSAGE", reminder.dosage)
                        }
                        context.startService(svcIntent)
                        onActionTaken()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("alarm_action_skip"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = stringResource(R.string.btn_skip)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_skip),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Action 3: Snooze
                FilledTonalButton(
                    onClick = {
                        onSnoozeClick(reminder.medId)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("alarm_action_dismiss"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = stringResource(R.string.btn_snooze)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_snooze),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Action 1: Taken
            Button(
                onClick = {
                    val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                        action = MedicationAlarmService.ACTION_TAKE
                        putExtra("MED_ID", reminder.medId)
                        putExtra("MED_NAME", reminder.medName)
                        putExtra("MED_DOSAGE", reminder.dosage)
                    }
                    context.startService(svcIntent)
                    onActionTaken()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = memberColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("alarm_action_take"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = takenLabel,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = takenLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun MultiAlarmCard(
    reminder: ActiveReminder,
    takenLabel: String,
    onAction: (String) -> Unit,
    onSnoozeClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val defaultProfileName = stringResource(R.string.notification_default_profile_name)
    var familyMemberName by remember(defaultProfileName) { mutableStateOf(defaultProfileName) }
    var familyMemberColorHex by remember { mutableStateOf("#B00020") }
    var soundName by remember { mutableStateOf("") }

    LaunchedEffect(reminder.medId, reminder.familyMemberId) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val member = if (reminder.familyMemberId != -1L) db.dao().getFamilyMemberById(reminder.familyMemberId) else null
                val med = if (reminder.medId != -1L) db.dao().getMedicationById(reminder.medId) else null
                if (member != null) {
                    familyMemberName = member.name
                    familyMemberColorHex = member.colorHex
                }
                soundName = com.example.SoundUtils.getSoundName(context, med?.soundUri, member?.soundUri)
            } catch (e: Exception) {}
        }
    }

    val memberColor = remember(familyMemberColorHex) {
        try {
            Color(android.graphics.Color.parseColor(familyMemberColorHex))
        } catch (e: Exception) {
            Color.Red
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = memberColor.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, memberColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$familyMemberName: ${reminder.medName}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = memberColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reminder.dosage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = memberColor.copy(alpha = 0.8f)
                    )
                    if (soundName.isNotEmpty()) {
                        Text(
                            text = "Sound: $soundName",
                            style = MaterialTheme.typography.bodySmall,
                            color = memberColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Individual Actions Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAction(MedicationAlarmService.ACTION_SKIP) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("alarm_action_skip_${reminder.medId}"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_skip), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }

                OutlinedButton(
                    onClick = { onSnoozeClick(reminder.medId) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = memberColor
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, memberColor),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("alarm_action_dismiss_${reminder.medId}"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_snooze), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }

                Button(
                    onClick = { onAction(MedicationAlarmService.ACTION_TAKE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = memberColor
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("alarm_action_take_${reminder.medId}"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(takenLabel, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}
