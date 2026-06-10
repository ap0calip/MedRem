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

        if (action == ACTION_TAKE || action == ACTION_SKIP || action == ACTION_DISMISS) {
            handleServiceAction(action, medId, medName, dosage)
            return START_NOT_STICKY
        }

        // Persistent Alarm: ensure sound & vibration are running if already alive
        if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
            startAlarmSound()
        }
        startVibration()

        if (medId != -1L) {
            showForegroundNotification(medId, medName, dosage, instructions, familyMemberId)
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

    private fun showForegroundNotification(
        medId: Long,
        medName: String,
        dosage: String,
        instructions: String,
        familyMemberId: Long
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medred_urgent_alarms"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Urgent Medication Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarms with sound and visual overlays."
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Full-screen Intent setup to trigger AlarmActivity directly
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", dosage)
            putExtra("MED_INSTRUCTIONS", instructions)
            putExtra("FAMILY_MEMBER_ID", familyMemberId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            medId.toInt() * 100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Action intents (Trigger service directly)
        val takeIntent = Intent(this, MedicationAlarmService::class.java).apply {
            action = ACTION_TAKE
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", dosage)
        }
        val takePendingIntent = PendingIntent.getService(
            this,
            medId.toInt() * 100 + 1,
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(this, MedicationAlarmService::class.java).apply {
            action = ACTION_SKIP
            putExtra("MED_ID", medId)
            putExtra("MED_NAME", medName)
            putExtra("MED_DOSAGE", dosage)
        }
        val skipPendingIntent = PendingIntent.getService(
            this,
            medId.toInt() * 100 + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detailText = listOfNotNull(
            if (dosage.isNotEmpty()) "Dosage: $dosage" else null,
            if (instructions.isNotEmpty()) "Note: $instructions" else null
        ).joinToString(" | ")

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Urgent: Medication Reminder")
            .setContentText("Time to take your $medName (${dosage})")
            .setSubText(if (detailText.isNotEmpty()) detailText else null)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.checkbox_on_background, "Take It", takePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Skip", skipPendingIntent)

        val notification = builder.build()

        val notificationId = if (medId <= 0) 99999 + Math.abs(medId.toInt()) else medId.toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun handleServiceAction(action: String, medId: Long, medName: String, dosage: String) {
        stopAlarmSound()
        stopForeground(true)
        stopSelf()

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.dao()

                val medication = dao.getMedicationById(medId)
                if (medication != null) {
                    if (action == ACTION_DISMISS) {
                        val snoozeTime = System.currentTimeMillis() + 30 * 60 * 1000L
                        val updatedMed = medication.copy(snoozedUntil = snoozeTime)
                        dao.insertMedication(updatedMed)
                        ReminderScheduler.scheduleSnoozeAlarm(applicationContext, updatedMed, snoozeTime)
                        Log.d(TAG, "Snoozed medication ${medication.name} until $snoozeTime")
                        return@launch
                    } else {
                        // Clear snooze condition
                        if (medication.snoozedUntil != 0L) {
                            val updatedMed = medication.copy(snoozedUntil = 0L)
                            dao.insertMedication(updatedMed)
                        }
                    }
                }

                if (action != ACTION_DISMISS) {
                    val familyMemberName = if (medication != null) {
                        dao.getFamilyMemberById(medication.familyMemberId)?.name ?: "Me"
                    } else {
                        "Me"
                    }

                    val status = if (action == ACTION_TAKE) "TAKEN" else "SKIPPED"
                    val now = System.currentTimeMillis()
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
    }
}
