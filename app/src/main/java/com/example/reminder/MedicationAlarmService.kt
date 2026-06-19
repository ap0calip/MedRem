package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.database.AppDatabase
import com.example.data.entity.DoseRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ActiveReminder(
    val medId: Long,
    val medName: String,
    val dosage: String,
    val instructions: String,
    val familyMemberId: Long
)

object ActiveAlarmManager {
    val activeAlarms = kotlinx.coroutines.flow.MutableStateFlow<List<ActiveReminder>>(emptyList())

    fun addAlarm(reminder: ActiveReminder) {
        val current = activeAlarms.value.toMutableList()
        if (current.none { it.medId == reminder.medId }) {
            current.add(reminder)
            activeAlarms.value = current
            Log.d("ActiveAlarmManager", "Added alarm: ${reminder.medName}, total: ${activeAlarms.value.size}")
        }
    }

    fun removeAlarm(medId: Long) {
        val current = activeAlarms.value.toMutableList()
        val removed = current.removeAll { it.medId == medId }
        if (removed) {
            activeAlarms.value = current
            Log.d("ActiveAlarmManager", "Removed alarm ID: $medId, total: ${activeAlarms.value.size}")
        }
    }
}

class MedicationAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: android.os.Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: MedicationAlarmService started")
        startAlarmSound()
        startVibration()

        serviceScope.launch {
            ActiveAlarmManager.activeAlarms.collect { list ->
                if (list.isEmpty()) {
                    Log.d(TAG, "Active alarm list is empty, stopping service")
                    stopAlarmSound()
                    stopForeground(true)
                    stopSelf()
                } else {
                    showForegroundNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        val medId = intent.getLongExtra("MED_ID", -1L)
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val dosage = intent.getStringExtra("MED_DOSAGE") ?: ""
        val instructions = intent.getStringExtra("MED_INSTRUCTIONS") ?: ""
        val familyMemberId = intent.getLongExtra("FAMILY_MEMBER_ID", -1L)

        Log.d(TAG, "onStartCommand: action=$action, medId=$medId, medName=$medName")

        if (action == ACTION_TAKE || action == ACTION_SKIP || action == ACTION_DISMISS ||
            action == ACTION_TAKE_ALL || action == ACTION_DISMISS_ALL) {
            handleServiceAction(action, medId, medName, dosage)
            return START_NOT_STICKY
        }

        // Standard alarm trigger
        // Persistent Alarm: ensure sound & vibration are running if already alive
        if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
            startAlarmSound()
        }
        startVibration()

        if (medId != -1L) {
            val reminder = ActiveReminder(medId, medName, dosage, instructions, familyMemberId)
            ActiveAlarmManager.addAlarm(reminder)
        }

        return START_STICKY
    }

    private fun startAlarmSound() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing old MediaPlayer: ${e.message}")
        }
        mediaPlayer = null

        val uris = listOf(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )

        for (uri in uris) {
            if (uri == null) continue
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = mp
                Log.d(TAG, "MediaPlayer successfully started looping alarm with: $uri")
                return // Started successfully!
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing sound with URI $uri: ${e.message}. Trying next...")
            }
        }
        Log.e(TAG, "All fallback URIs failed to play alarm sound!")
    }

    private fun startVibration() {
        if (vibrator != null) return // Already vibrating
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    val pattern = longArrayOf(0, 1000, 1000) // Vibrate 1s, silent 1s
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(pattern, 0)
                    }
                    Log.d(TAG, "Looping vibration sequence initiated")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
            vibrator = null
            Log.d(TAG, "Vibration sequence ended")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel vibration: ${e.message}")
        }
    }

    private fun stopAlarmSound() {
        stopVibration()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "MediaPlayer stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Media Player: ${e.message}", e)
        }
    }

    private suspend fun showForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medrem_urgent_alarms"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medication Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarms with sound and visual overlays."
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val list = ActiveAlarmManager.activeAlarms.value
        if (list.isEmpty()) {
            return
        }

        // Full-screen Intent setup to trigger AlarmActivity directly
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            8888,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        val db = AppDatabase.getDatabase(applicationContext)

        if (list.size == 1) {
            val rem = list.first()
            val member = db.dao().getFamilyMemberById(rem.familyMemberId)
            val profileName = member?.name ?: "Me"
            val profileColorHex = member?.colorHex ?: "#B00020"

            builder.setContentTitle("$profileName: ${rem.medName}")
            builder.setContentText(rem.dosage)
            try {
                builder.setColor(android.graphics.Color.parseColor(profileColorHex))
            } catch (e: Exception) {}

            val takeIntent = Intent(this, MedicationAlarmService::class.java).apply {
                action = ACTION_TAKE
                putExtra("MED_ID", rem.medId)
                putExtra("MED_NAME", rem.medName)
                putExtra("MED_DOSAGE", rem.dosage)
            }
            val takePendingIntent = PendingIntent.getService(
                this,
                rem.medId.toInt() * 100 + 1,
                takeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(this, MedicationAlarmService::class.java).apply {
                action = ACTION_DISMISS
                putExtra("MED_ID", rem.medId)
            }
            val snoozePendingIntent = PendingIntent.getService(
                this,
                rem.medId.toInt() * 100 + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, "Taken", takePendingIntent)
            builder.addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze", snoozePendingIntent)
        } else {
            // Find all unique profile names for the active reminders
            val profileNames = list.map { rem ->
                db.dao().getFamilyMemberById(rem.familyMemberId)?.name ?: "Me"
            }.distinct()

            val namesLabel = if (profileNames.isEmpty()) {
                "Me"
            } else if (profileNames.size == 1) {
                profileNames.first()
            } else if (profileNames.size == 2) {
                "${profileNames[0]} & ${profileNames[1]}"
            } else {
                profileNames.joinToString(", ")
            }

            builder.setContentTitle("$namesLabel: Multiple Medications Due")
            val names = list.joinToString(", ") { it.medName }
            builder.setContentText("${list.size} medications can be taken: $names")

            val takeAllIntent = Intent(this, MedicationAlarmService::class.java).apply {
                action = ACTION_TAKE_ALL
            }
            val takeAllPendingIntent = PendingIntent.getService(
                this,
                8889,
                takeAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeAllIntent = Intent(this, MedicationAlarmService::class.java).apply {
                action = ACTION_DISMISS_ALL
            }
            val snoozeAllPendingIntent = PendingIntent.getService(
                this,
                8890,
                snoozeAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, "Taken All", takeAllPendingIntent)
            builder.addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze All", snoozeAllPendingIntent)
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                8888,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(8888, notification)
        }

        notificationManager.notify(8888, notification)
    }

    private fun handleServiceAction(action: String, medId: Long, medName: String, dosage: String) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.dao()
                val now = System.currentTimeMillis()

                if (action == ACTION_TAKE_ALL) {
                    val alarmsToHandle = ActiveAlarmManager.activeAlarms.value.toList()
                    ActiveAlarmManager.activeAlarms.value = emptyList() // clear immediately

                    alarmsToHandle.forEach { rem ->
                        val medication = dao.getMedicationById(rem.medId)
                        val familyMemberName = if (medication != null) {
                            dao.getFamilyMemberById(medication.familyMemberId)?.name ?: "Me"
                        } else {
                            "Me"
                        }
                        val record = DoseRecord(
                            medicationId = rem.medId,
                            medicationName = rem.medName,
                            familyMemberName = familyMemberName,
                            dosage = rem.dosage,
                            scheduledTime = now,
                            actualTime = now,
                            status = "TAKEN"
                        )
                        dao.insertDoseRecord(record)
                        if (medication != null) {
                            val updatedMed = medication.copy(lastLoggedTime = now, snoozedUntil = 0L)
                            dao.insertMedication(updatedMed)
                            if (updatedMed.isActive) {
                                ReminderScheduler.scheduleAlarm(applicationContext, updatedMed)
                            }
                        }
                    }
                } else if (action == ACTION_DISMISS_ALL) {
                    val alarmsToHandle = ActiveAlarmManager.activeAlarms.value.toList()
                    ActiveAlarmManager.activeAlarms.value = emptyList() // clear immediately

                    alarmsToHandle.forEach { rem ->
                        val medication = dao.getMedicationById(rem.medId)
                        if (medication != null) {
                            val snoozeTime = now + 30 * 60 * 1000L
                            val updatedMed = medication.copy(snoozedUntil = snoozeTime)
                            dao.insertMedication(updatedMed)
                            ReminderScheduler.scheduleSnoozeAlarm(applicationContext, updatedMed, snoozeTime)
                        }
                    }
                } else {
                    // Single item logic
                    val medication = dao.getMedicationById(medId)
                    ActiveAlarmManager.removeAlarm(medId)

                    if (action == ACTION_DISMISS) {
                        if (medication != null) {
                            val snoozeTime = now + 30 * 60 * 1000L
                            val updatedMed = medication.copy(snoozedUntil = snoozeTime)
                            dao.insertMedication(updatedMed)
                            ReminderScheduler.scheduleSnoozeAlarm(applicationContext, updatedMed, snoozeTime)
                            Log.d(TAG, "Snoozed medication ${medication.name} until $snoozeTime")
                        }
                    } else {
                        // Clear snooze condition if we took it or skipped it
                        if (medication != null && medication.snoozedUntil != 0L) {
                            val updatedMed = medication.copy(snoozedUntil = 0L)
                            dao.insertMedication(updatedMed)
                        }

                        val familyMemberName = if (medication != null) {
                            dao.getFamilyMemberById(medication.familyMemberId)?.name ?: "Me"
                        } else {
                            "Me"
                        }

                        val status = if (action == ACTION_TAKE) "TAKEN" else "SKIPPED"
                        val record = DoseRecord(
                            medicationId = medId,
                            medicationName = medName,
                            familyMemberName = familyMemberName,
                            dosage = dosage,
                            scheduledTime = now,
                            actualTime = now,
                            status = status
                        )
                        dao.insertDoseRecord(record)
                        Log.d(TAG, "Logged dose record from Alarm Service: medName=$medName, status=$status")

                        if (medication != null) {
                            val updatedMed = medication.copy(lastLoggedTime = now, snoozedUntil = 0L)
                            dao.insertMedication(updatedMed)
                            if (updatedMed.isActive) {
                                ReminderScheduler.scheduleAlarm(applicationContext, updatedMed)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling action $action in Service: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        stopAlarmSound()
        super.onDestroy()
        Log.d(TAG, "onDestroy: MedicationAlarmService destroyed")
    }

    companion object {
        private const val TAG = "MedicationAlarmService"
        const val ACTION_TAKE = "com.example.reminder.service.ACTION_TAKE"
        const val ACTION_SKIP = "com.example.reminder.service.ACTION_SKIP"
        const val ACTION_DISMISS = "com.example.reminder.service.ACTION_DISMISS"
        const val ACTION_TAKE_ALL = "com.example.reminder.service.ACTION_TAKE_ALL"
        const val ACTION_DISMISS_ALL = "com.example.reminder.service.ACTION_DISMISS_ALL"
    }
}
