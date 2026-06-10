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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {

    private var currentMedId = mutableStateOf(-1L)
    private var currentMedName = mutableStateOf("Medication")
    private var currentDosage = mutableStateOf("")
    private var currentInstructions = mutableStateOf("")
    private var currentFamilyMemberId = mutableStateOf(-1L)

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
                    medId = currentMedId.value,
                    medName = currentMedName.value,
                    dosage = currentDosage.value,
                    instructions = currentInstructions.value,
                    familyMemberId = currentFamilyMemberId.value,
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
        currentMedId.value = intent.getLongExtra("MED_ID", -1L)
        currentMedName.value = intent.getStringExtra("MED_NAME") ?: "Medication"
        currentDosage.value = intent.getStringExtra("MED_DOSAGE") ?: ""
        currentInstructions.value = intent.getStringExtra("MED_INSTRUCTIONS") ?: ""
        currentFamilyMemberId.value = intent.getLongExtra("FAMILY_MEMBER_ID", -1L)
        Log.d(TAG, "updateFields: updated medId=${currentMedId.value}, name=${currentMedName.value}")
    }

    companion object {
        private const val TAG = "AlarmActivity"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    medId: Long,
    medName: String,
    dosage: String,
    instructions: String,
    familyMemberId: Long,
    onActionTaken: () -> Unit
) {
    val context = LocalContext.current

    androidx.activity.compose.BackHandler {
        // Map back button to the Mute/Snooze action to stop the service sound and close activity!
        val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
            action = MedicationAlarmService.ACTION_DISMISS
            putExtra("MED_ID", medId)
        }
        context.startService(svcIntent)
        onActionTaken()
    }

    var familyMemberName by remember { mutableStateOf("Me") }
    var familyMemberColorHex by remember { mutableStateOf("#B00020") } // default deep red

    // Fetch family member detail from Database
    LaunchedEffect(familyMemberId) {
        if (familyMemberId != -1L) {
            withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val member = db.dao().getFamilyMemberById(familyMemberId)
                    if (member != null) {
                        familyMemberName = member.name
                        familyMemberColorHex = member.colorHex
                    }
                } catch (e: Exception) {
                    Log.e("AlarmActivity", "Failed to fetch family member detail: ${e.message}")
                }
            }
        }
    }

    // Color parsing helper
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val memberColor = remember(familyMemberColorHex, defaultPrimary) {
        try {
            Color(android.graphics.Color.parseColor(familyMemberColorHex))
        } catch (e: Exception) {
            defaultPrimary
        }
    }

    // Alarm Icon scale pulse animation
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
            // High-Contrast Alert Card displaying only username, medication, and dose using the user's color scheme
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 24.dp),
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
                        contentDescription = "Alarm Active Icon",
                        tint = memberColor,
                        modifier = Modifier
                            .size(80.dp)
                            .scale(scalePulse)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Username Text in user's color
                    Text(
                        text = familyMemberName.uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = memberColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("alarm_username")
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Medication Name in user's color
                    Text(
                        text = medName,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            lineHeight = 44.sp
                        ),
                        color = memberColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("alarm_med_name")
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Dose amount in user's color
                    Text(
                        text = dosage,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = memberColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("alarm_dosage")
                    )
                }
            }

            // High-Contrast Interactive Actions Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Action 1: Take Log (Primary Action Button)
                Button(
                    onClick = {
                        val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                            action = MedicationAlarmService.ACTION_TAKE
                            putExtra("MED_ID", medId)
                            putExtra("MED_NAME", medName)
                            putExtra("MED_DOSAGE", dosage)
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
                            contentDescription = "Check Icon",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Record Intake (Taken)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Action 2: Skip Log (Secondary Action Button)
                    OutlinedButton(
                        onClick = {
                            val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                                action = MedicationAlarmService.ACTION_SKIP
                                putExtra("MED_ID", medId)
                                putExtra("MED_NAME", medName)
                                putExtra("MED_DOSAGE", dosage)
                            }
                            context.startService(svcIntent)
                            onActionTaken()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(),
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
                                contentDescription = "Block Icon"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Skip Dose",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Action 3: Dismiss/Snooze Alert (Bypass/Mute sound only)
                    FilledTonalButton(
                        onClick = {
                            val svcIntent = Intent(context, MedicationAlarmService::class.java).apply {
                                action = MedicationAlarmService.ACTION_DISMISS
                                putExtra("MED_ID", medId)
                            }
                            context.startService(svcIntent)
                            onActionTaken()
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
                                contentDescription = "Alarm Dismiss Icon"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mute / Snooze",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
