package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.entity.DoseRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val medId = intent.getLongExtra("MED_ID", -1L)
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val dosage = intent.getStringExtra("MED_DOSAGE") ?: ""
        val instructions = intent.getStringExtra("MED_INSTRUCTIONS") ?: ""
        val familyMemberId = intent.getLongExtra("FAMILY_MEMBER_ID", -1L)
        val soundUri = intent.getStringExtra("SOUND_URI") ?: ""

        Log.d(TAG, "onReceive: action=$action, medId=$medId, medName=$medName")

        if (action == ACTION_TAKE || action == ACTION_SKIP || action == ACTION_SNOOZE) {
            val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 30)
            handleNotificationAction(context, action, medId, medName, dosage, snoozeMinutes)
            return
        }

        // Standard alarm trigger - spawn the high-priority alarm foreground service and schedule the NEXT alarm for this medication
        if (medId != -1L) {
            val reminder = ActiveReminder(medId, medName, dosage, instructions, familyMemberId)
            ActiveAlarmManager.addAlarm(reminder)

            try {
                val serviceIntent = Intent(context, MedicationAlarmService::class.java).apply {
                    putExtra("MED_ID", medId)
                    putExtra("MED_NAME", medName)
                    putExtra("MED_DOSAGE", dosage)
                    putExtra("MED_INSTRUCTIONS", instructions)
                    putExtra("FAMILY_MEMBER_ID", familyMemberId)
                    putExtra("SOUND_URI", soundUri)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Started MedicationAlarmService from Receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MedicationAlarmService foreground service: ${e.message}", e)
            }

            try {
                // Persistent Popup: Launch the AlarmActivity directly!
                val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("MED_ID", medId)
                    putExtra("MED_NAME", medName)
                    putExtra("MED_DOSAGE", dosage)
                    putExtra("MED_INSTRUCTIONS", instructions)
                    putExtra("FAMILY_MEMBER_ID", familyMemberId)
                }
                context.startActivity(activityIntent)
                Log.d(TAG, "Successfully started AlarmActivity directly from Receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AlarmActivity directly: ${e.message}", e)
                // Fallback: show static notification
                showNotification(context, medId, medName, dosage, instructions, familyMemberId)
            }

            rescheduleNextAlarm(context, medId)
        }
    }

    private fun showNotification(
        context: Context,
        medId: Long,
        medName: String,
        dosage: String,
        instructions: String,
        familyMemberId: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medrem_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_reminders_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to launch MainActivity when clicking notification itself
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            medId.toInt() * 10,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Take" action button
        val takeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            setAction(ACTION_TAKE)
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", dosage)
        }
        val takePendingIntent = PendingIntent.getBroadcast(
            context,
            medId.toInt() * 10 + 1,
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for "Snooze" action button
        val snoozeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            setAction(ACTION_SNOOZE)
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", dosage)
            putExtra("SNOOZE_MINUTES", 30)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            medId.toInt() * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for closing/dismissing notification (deleteIntent)
        val dismissIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            setAction(ACTION_SNOOZE)
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", dosage)
            putExtra("SNOOZE_MINUTES", 5)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            medId.toInt() * 10 + 3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun buildAndPost(profileName: String, profileColorHex: String) {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$profileName: $medName")
                .setContentText(dosage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(mainPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(android.R.drawable.checkbox_on_background, context.getString(R.string.notification_action_taken), takePendingIntent)
                .addAction(android.R.drawable.ic_lock_idle_alarm, context.getString(R.string.notification_action_snooze), snoozePendingIntent)
                .setDeleteIntent(dismissPendingIntent)

            try {
                builder.setColor(android.graphics.Color.parseColor(profileColorHex))
            } catch (e: Exception) {
                // Ignore parse errors
            }

            notificationManager.notify(medId.toInt(), builder.build())
        }

        // Post placeholder immediately
        buildAndPost(context.getString(R.string.notification_default_profile_name), "#B00020")

        // Look up profile details off-thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val member = db.dao().getFamilyMemberById(familyMemberId)
                val profileName = member?.name ?: context.getString(R.string.notification_default_profile_name)
                val profileColorHex = member?.colorHex ?: "#B00020"
                buildAndPost(profileName, profileColorHex)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching profile info in BroadcastReceiver: ${e.message}", e)
            }
        }
    }

    private fun handleNotificationAction(
        context: Context,
        action: String,
        medId: Long,
        medName: String,
        dosage: String,
        snoozeMinutes: Int = 30
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medId.toInt())

        if (action == ACTION_SNOOZE) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.dao()
                    val medication = dao.getMedicationById(medId)
                    if (medication != null) {
                        val snoozeTime = System.currentTimeMillis() + snoozeMinutes * 60 * 1000L
                        val updatedMed = medication.copy(snoozedUntil = snoozeTime)
                        dao.insertMedication(updatedMed)
                        ReminderScheduler.scheduleSnoozeAlarm(context, updatedMed, snoozeTime)
                        Log.d(TAG, "Snoozed from receiver action: medName=$medName for $snoozeMinutes mins until $snoozeTime")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error snoozing in NotificationAction: ${e.message}", e)
                }
            }
            return
        }

        val status = if (action == ACTION_TAKE) "TAKEN" else "SKIPPED"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.dao()

                // Find family member name for logging
                val medication = dao.getMedicationById(medId)
                val familyMemberName = if (medication != null) {
                    dao.getFamilyMemberById(medication.familyMemberId)?.name ?: "Me"
                } else {
                    "Me"
                }

                val now = System.currentTimeMillis()
                val record = DoseRecord(
                    medicationId = medId,
                    medicationName = medName,
                    familyMemberName = familyMemberName,
                    dosage = dosage,
                    scheduledTime = now, // Approximate scheduled time
                    actualTime = now,
                    status = status
                )
                dao.insertDoseRecord(record)
                Log.d(TAG, "Logged dose record: medName=$medName, status=$status")

                if (medication != null) {
                    val updatedMed = medication.copy(lastLoggedTime = now, snoozedUntil = 0L)
                    dao.insertMedication(updatedMed)
                    if (updatedMed.isActive) {
                        ReminderScheduler.scheduleAlarm(context, updatedMed)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error logging dose record in BroadcastReceiver: ${e.message}", e)
            }
        }
    }

    private fun rescheduleNextAlarm(context: Context, medId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val medication = db.dao().getMedicationById(medId)
                if (medication != null && medication.isActive) {
                    ReminderScheduler.scheduleAlarm(context, medication)
                    Log.d(TAG, "Rescheduled next alarm for medication ID $medId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling next alarm in BroadcastReceiver: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "MedicationAlarmReceiver"
        const val ACTION_TAKE = "com.example.reminder.ACTION_TAKE"
        const val ACTION_SKIP = "com.example.reminder.ACTION_SKIP"
        const val ACTION_SNOOZE = "com.example.reminder.ACTION_SNOOZE"
    }
}
