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
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
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
        synchronized(this) {
            val current = activeAlarms.value.toMutableList()
            if (current.none { it.medId == reminder.medId }) {
                current.add(reminder)
                activeAlarms.value = current
                Log.d("ActiveAlarmManager", "Added alarm: ${reminder.medName}, total: ${current.size}")
            }
        }
    }

    fun removeAlarm(medId: Long) {
        synchronized(this) {
            val current = activeAlarms.value.toMutableList()
            val removed = current.removeAll { it.medId == medId }
            if (removed) {
                activeAlarms.value = current
                Log.d("ActiveAlarmManager", "Removed alarm ID: $medId, total: ${current.size}")
            }
        }
    }

    fun clearAlarms() {
        synchronized(this) {
            activeAlarms.value = emptyList()
            Log.d("ActiveAlarmManager", "Cleared all alarms")
        }
    }
}

class MedicationAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var currentlyPlayingUri: String? = null
    private var vibrator: android.os.Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: MedicationAlarmService started")

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
        val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 30)
        val soundUriStr = intent.getStringExtra("SOUND_URI")

        Log.d(TAG, "onStartCommand: action=$action, medId=$medId, medName=$medName, snoozeMinutes=$snoozeMinutes, soundUri=$soundUriStr")

        if (action == ACTION_TAKE || action == ACTION_SKIP || action == ACTION_DISMISS ||
            action == ACTION_TAKE_ALL || action == ACTION_DISMISS_ALL) {
            handleServiceAction(action, medId, medName, dosage, snoozeMinutes)
            return START_NOT_STICKY
        }

        if (action == ACTION_UPDATE_SOUND) {
            val updateMemberId = intent.getLongExtra("FAMILY_MEMBER_ID", -1L)
            val updatedSoundUri = intent.getStringExtra("SOUND_URI")
            updateSoundPlayback(explicitFamilyMemberId = updateMemberId, fallbackSoundUri = updatedSoundUri)
            return START_STICKY
        }

        // Standard alarm trigger
        startVibration()

        if (medId != -1L) {
            val reminder = ActiveReminder(medId, medName, dosage, instructions, familyMemberId)
            ActiveAlarmManager.addAlarm(reminder)
        }

        updateSoundPlayback(explicitMedId = medId, explicitFamilyMemberId = familyMemberId, fallbackSoundUri = soundUriStr)

        return START_STICKY
    }

    private fun updateSoundPlayback(
        explicitMedId: Long = -1L,
        explicitFamilyMemberId: Long = -1L,
        fallbackSoundUri: String? = null
    ) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.dao()

                val activeList = ActiveAlarmManager.activeAlarms.value
                val targetMedId = if (explicitMedId != -1L) explicitMedId else (activeList.firstOrNull()?.medId ?: -1L)
                val targetMemberId = if (explicitFamilyMemberId != -1L) explicitFamilyMemberId else (activeList.firstOrNull()?.familyMemberId ?: -1L)

                val med = if (targetMedId != -1L) dao.getMedicationById(targetMedId) else null
                val finalMemberId = if (targetMemberId != -1L) targetMemberId else (med?.familyMemberId ?: -1L)
                val member = if (finalMemberId != -1L) dao.getFamilyMemberById(finalMemberId) else null

                val effectiveSoundUri = med?.soundUri?.ifEmpty { null }
                    ?: member?.soundUri?.ifEmpty { null }
                    ?: fallbackSoundUri?.ifEmpty { null }

                startAlarmSound(effectiveSoundUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving sound playback from database: ${e.message}", e)
                startAlarmSound(fallbackSoundUri)
            }
        }
    }

    private fun getCandidateSoundUris(context: Context, customUriString: String?): List<Uri> {
        val list = mutableListOf<Uri>()

        if (!customUriString.isNullOrEmpty()) {
            val parsed = Uri.parse(customUriString)
            val isDefaultSymbolic = RingtoneManager.isDefault(parsed) ||
                customUriString == "content://settings/system/alarm_alert" ||
                customUriString == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()

            if (isDefaultSymbolic) {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)?.let { list.add(it) }
            }
            list.add(parsed)
        }

        // Actual default alarm sound (resolves the concrete media URI that MediaPlayer can open)
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)?.let {
            if (!list.contains(it)) list.add(it)
        }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let {
            if (!list.contains(it)) list.add(it)
        }

        // Fallback: Ringtone
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)?.let {
            if (!list.contains(it)) list.add(it)
        }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let {
            if (!list.contains(it)) list.add(it)
        }

        // Fallback: Notification
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)?.let {
            if (!list.contains(it)) list.add(it)
        }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let {
            if (!list.contains(it)) list.add(it)
        }

        return list
    }

    private fun startAlarmSound(customUriString: String? = null) {
        val effectiveUriStr = customUriString?.ifEmpty { null }

        // If sound is already actively playing and target sound hasn't changed, continue playing
        val isCurrentlyPlaying = (mediaPlayer?.isPlaying == true) || (ringtone?.isPlaying == true)
        if (isCurrentlyPlaying && currentlyPlayingUri == effectiveUriStr) {
            Log.d(TAG, "Alarm sound is already playing: $effectiveUriStr")
            return
        }

        stopAlarmSoundOnly()

        val candidateUris = getCandidateSoundUris(applicationContext, effectiveUriStr)
        Log.d(TAG, "Attempting to play alarm sound with candidates: $candidateUris")

        // 1. Try MediaPlayer
        for (uri in candidateUris) {
            try {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = mp
                currentlyPlayingUri = effectiveUriStr
                Log.d(TAG, "MediaPlayer successfully started playing: $uri")
                return
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer failed for URI $uri: ${e.message}")
            }
        }

        // 2. Try Ringtone fallback (handles Settings content URIs natively)
        for (uri in candidateUris) {
            try {
                val rt = RingtoneManager.getRingtone(applicationContext, uri)
                if (rt != null) {
                    rt.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        rt.isLooping = true
                    }
                    rt.play()
                    ringtone = rt
                    currentlyPlayingUri = effectiveUriStr
                    Log.d(TAG, "Ringtone successfully started playing: $uri")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ringtone fallback failed for URI $uri: ${e.message}")
            }
        }

        Log.e(TAG, "All alarm sound playback candidates failed!")
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

    private fun stopAlarmSoundOnly() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer: ${e.message}")
        }
        mediaPlayer = null

        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Ringtone: ${e.message}")
        }
        ringtone = null
        currentlyPlayingUri = null
    }

    private fun stopAlarmSound() {
        stopVibration()
        stopAlarmSoundOnly()
    }

    private suspend fun showForegroundNotification() {
        val prefs = getSharedPreferences("MedRemPrefs", Context.MODE_PRIVATE)
        val labelMode = prefs.getString("label_mode", "ITEM") ?: "ITEM"

        val medicationSing = when (labelMode) {
            "MEDICATION" -> "Medication"
            "ITEM" -> "Item"
            "CUSTOM" -> prefs.getString("custom_med_sing", "Medication") ?: "Medication"
            else -> "Medication"
        }

        val medicationPlur = when (labelMode) {
            "MEDICATION" -> "Medications"
            "ITEM" -> "Items"
            "CUSTOM" -> prefs.getString("custom_med_plur", "Medications") ?: "Medications"
            else -> "Medications"
        }

        val takenLabel = when (labelMode) {
            "MEDICATION" -> "Taken"
            "ITEM" -> "Completed"
            "CUSTOM" -> prefs.getString("custom_taken_label", "Taken") ?: "Taken"
            else -> "Taken"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medrem_urgent_alarms_v2"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "$medicationSing Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority $medicationPlur alarms with sound and visual overlays."
                enableVibration(false)
                setSound(null, null)
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
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        val db = AppDatabase.getDatabase(applicationContext)

        if (list.size == 1) {
            val rem = list.first()
            val member = db.dao().getFamilyMemberById(rem.familyMemberId)
            val profileName = member?.name ?: getString(R.string.notification_default_profile_name)
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
                putExtra("SNOOZE_MINUTES", 30)
            }
            val snoozePendingIntent = PendingIntent.getService(
                this,
                rem.medId.toInt() * 100 + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(this, MedicationAlarmService::class.java).apply {
                action = ACTION_DISMISS
                putExtra("MED_ID", rem.medId)
                putExtra("SNOOZE_MINUTES", 5)
            }
            val dismissPendingIntent = PendingIntent.getService(
                this,
                rem.medId.toInt() * 100 + 3,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, takenLabel, takePendingIntent)
            builder.addAction(android.R.drawable.ic_lock_idle_alarm, getString(R.string.notification_action_snooze), snoozePendingIntent)
            builder.setDeleteIntent(dismissPendingIntent)
        } else {
            // Find all unique profile names for the active reminders
            val profileNames = list.map { rem ->
                db.dao().getFamilyMemberById(rem.familyMemberId)?.name ?: getString(R.string.notification_default_profile_name)
            }.distinct()

            val namesLabel = if (profileNames.isEmpty()) {
                getString(R.string.notification_default_profile_name)
            } else if (profileNames.size == 1) {
                profileNames.first()
            } else if (profileNames.size == 2) {
                "${profileNames[0]} & ${profileNames[1]}"
            } else {
                profileNames.joinToString(", ")
            }

            builder.setContentTitle("$namesLabel: Multiple $medicationPlur Due")
            val names = list.joinToString(", ") { it.medName }
            builder.setContentText("${list.size} $medicationPlur can be $takenLabel: $names")

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
                putExtra("SNOOZE_MINUTES", 30)
            }
            val snoozeAllPendingIntent = PendingIntent.getService(
                this,
                8890,
                snoozeAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissAllIntent = Intent(this, MedicationAlarmService::class.java).apply {
                action = ACTION_DISMISS_ALL
                putExtra("SNOOZE_MINUTES", 5)
            }
            val dismissAllPendingIntent = PendingIntent.getService(
                this,
                8891,
                dismissAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, "$takenLabel All", takeAllPendingIntent)
            builder.addAction(android.R.drawable.ic_lock_idle_alarm, getString(R.string.notification_action_snooze_all), snoozeAllPendingIntent)
            builder.setDeleteIntent(dismissAllPendingIntent)
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

    private fun handleServiceAction(action: String, medId: Long, medName: String, dosage: String, snoozeMinutes: Int = 30) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.dao()
                val now = System.currentTimeMillis()

                if (action == ACTION_TAKE_ALL) {
                    val alarmsToHandle = ActiveAlarmManager.activeAlarms.value.toList()
                    ActiveAlarmManager.clearAlarms() // clear immediately

                    alarmsToHandle.forEach { rem ->
                        val medication = dao.getMedicationById(rem.medId)
                        val familyMemberName = if (medication != null) {
                            dao.getFamilyMemberById(medication.familyMemberId)?.name ?: "Me"
                        } else {
                            "Me"
                        }

                        if (medication != null) {
                            if (medication.recordInHistory) {
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
                            }

                            if (medication.scheduleType == "ONE_TIME" && medication.deleteAfterCompletion) {
                                ReminderScheduler.cancelAlarm(applicationContext, medication)
                                dao.deleteMedication(medication)
                            } else {
                                var updatedMed = medication.copy(lastLoggedTime = now, snoozedUntil = 0L)
                                if (medication.autoReset && medication.scheduleType == "CUSTOM" && medication.daysOfWeekCommaSeparated != "hours") {
                                    val calendar = java.util.Calendar.getInstance()
                                    calendar.timeInMillis = now
                                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    calendar.set(java.util.Calendar.MINUTE, 0)
                                    calendar.set(java.util.Calendar.SECOND, 0)
                                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                                    updatedMed = updatedMed.copy(startDate = calendar.timeInMillis)
                                }
                                dao.insertMedication(updatedMed)
                                if (updatedMed.isActive) {
                                    ReminderScheduler.scheduleAlarm(applicationContext, updatedMed)
                                }
                            }
                        } else {
                            // Medication already deleted or not found, but we still log if we have info?
                            // Actually if it's null we can't check recordInHistory
                        }
                    }
                } else if (action == ACTION_DISMISS_ALL) {
                    val alarmsToHandle = ActiveAlarmManager.activeAlarms.value.toList()
                    ActiveAlarmManager.clearAlarms() // clear immediately

                    alarmsToHandle.forEach { rem ->
                        val medication = dao.getMedicationById(rem.medId)
                        if (medication != null) {
                            val snoozeTime = now + snoozeMinutes * 60 * 1000L
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
                            val snoozeTime = now + snoozeMinutes * 60 * 1000L
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

                        if (medication != null) {
                            if (medication.recordInHistory) {
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
                            }

                            if (medication.scheduleType == "ONE_TIME") {
                                ReminderScheduler.cancelAlarm(applicationContext, medication)
                                if (medication.deleteAfterCompletion) {
                                    dao.deleteMedication(medication)
                                } else {
                                    val disabledMed = medication.copy(isActive = false, lastLoggedTime = now, snoozedUntil = 0L)
                                    dao.insertMedication(disabledMed)
                                }
                            } else {
                                var updatedMed = medication.copy(lastLoggedTime = now, snoozedUntil = 0L)
                                if (medication.autoReset && medication.scheduleType == "CUSTOM" && medication.daysOfWeekCommaSeparated != "hours") {
                                    val calendar = java.util.Calendar.getInstance()
                                    calendar.timeInMillis = now
                                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    calendar.set(java.util.Calendar.MINUTE, 0)
                                    calendar.set(java.util.Calendar.SECOND, 0)
                                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                                    updatedMed = updatedMed.copy(startDate = calendar.timeInMillis)
                                }
                                dao.insertMedication(updatedMed)
                                if (updatedMed.isActive) {
                                    ReminderScheduler.scheduleAlarm(applicationContext, updatedMed)
                                }
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
        const val ACTION_UPDATE_SOUND = "com.example.reminder.service.ACTION_UPDATE_SOUND"
    }
}
