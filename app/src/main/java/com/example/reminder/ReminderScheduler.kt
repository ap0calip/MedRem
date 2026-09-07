package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.entity.Medication
import java.util.Calendar

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    fun calculateRawNextTrigger(medication: Medication, startAfterMillis: Long = System.currentTimeMillis()): Long {
        val parts = medication.startTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        if (medication.scheduleType == "WEEKLY") {
            val days = medication.daysOfWeekCommaSeparated.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (days.isEmpty()) {
                // Return tomorrow at the same time if no custom day is chosen
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = startAfterMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (targetCal.timeInMillis <= startAfterMillis) {
                    targetCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return targetCal.timeInMillis
            }

            val dayMap = mapOf(
                "Sun" to Calendar.SUNDAY,
                "Mon" to Calendar.MONDAY,
                "Tue" to Calendar.TUESDAY,
                "Wed" to Calendar.WEDNESDAY,
                "Thu" to Calendar.THURSDAY,
                "Fri" to Calendar.FRIDAY,
                "Sat" to Calendar.SATURDAY
            )

            val targetDays = days.mapNotNull { dayMap[it] }
            var minNextTime = Long.MAX_VALUE

            // Search next 7 days (including today)
            for (i in 0..7) {
                val testCal = Calendar.getInstance().apply {
                    timeInMillis = startAfterMillis
                    add(Calendar.DAY_OF_YEAR, i)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayOfWeek = testCal.get(Calendar.DAY_OF_WEEK)
                if (targetDays.contains(dayOfWeek)) {
                    if (testCal.timeInMillis > startAfterMillis) {
                        minNextTime = minOf(minNextTime, testCal.timeInMillis)
                    }
                }
            }
            return if (minNextTime == Long.MAX_VALUE) {
                startAfterMillis + 24 * 60 * 60 * 1000L
            } else {
                minNextTime
            }

        } else if (medication.scheduleType == "CUSTOM") {
            val repeatNum = if (medication.intervalHours <= 0) 1 else medication.intervalHours
            val repeatUnit = medication.daysOfWeekCommaSeparated

            val field = when (repeatUnit) {
                "hours" -> Calendar.HOUR_OF_DAY
                "days" -> Calendar.DAY_OF_YEAR
                "weeks" -> Calendar.WEEK_OF_YEAR
                "months" -> Calendar.MONTH
                "years" -> Calendar.YEAR
                else -> Calendar.DAY_OF_YEAR
            }

            val startCal = Calendar.getInstance().apply {
                timeInMillis = medication.startDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If autoReset is enabled and a dose was already logged on or after the startDate day:
            if (medication.autoReset && repeatUnit != "hours" && medication.lastLoggedTime != 0L) {
                val logCal = Calendar.getInstance().apply { timeInMillis = medication.lastLoggedTime }
                val startDayCal = Calendar.getInstance().apply { timeInMillis = medication.startDate }
                val isLoggedOnOrAfterStartDay = logCal.get(Calendar.ERA) == startDayCal.get(Calendar.ERA) &&
                        (logCal.get(Calendar.YEAR) > startDayCal.get(Calendar.YEAR) ||
                                (logCal.get(Calendar.YEAR) == startDayCal.get(Calendar.YEAR) &&
                                        logCal.get(Calendar.DAY_OF_YEAR) >= startDayCal.get(Calendar.DAY_OF_YEAR)))
                
                if (isLoggedOnOrAfterStartDay) {
                    // Advance startCal by at least 1 interval from the start day
                    startCal.add(field, repeatNum)
                }
            }

            if (startCal.timeInMillis > startAfterMillis) {
                return startCal.timeInMillis
            }

            if (field == Calendar.HOUR_OF_DAY) {
                val hourMillis = repeatNum * 60 * 60 * 1000L
                val elapsed = startAfterMillis - startCal.timeInMillis
                val count = (elapsed / hourMillis) + 1
                return startCal.timeInMillis + (count * hourMillis)
            } else {
                var safetyCount = 0
                while (startCal.timeInMillis <= startAfterMillis && safetyCount < 1000) {
                    startCal.add(field, repeatNum)
                    safetyCount++
                }
                return startCal.timeInMillis
            }
        } else if (medication.scheduleType == "ONE_TIME") {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = medication.startDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (targetCal.timeInMillis <= startAfterMillis) {
                val todayCal = Calendar.getInstance().apply {
                    timeInMillis = startAfterMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (todayCal.timeInMillis > startAfterMillis) {
                    return todayCal.timeInMillis
                } else {
                    todayCal.add(Calendar.DAY_OF_YEAR, 1)
                    return todayCal.timeInMillis
                }
            }
            return targetCal.timeInMillis
        } else {
            // INTERVAL BASED
            val startCal = Calendar.getInstance().apply {
                timeInMillis = medication.startDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val startMillis = startCal.timeInMillis
            if (startMillis > startAfterMillis) {
                return startMillis
            }

            val intervalMillis = medication.intervalHours * 60 * 60 * 1000L
            if (intervalMillis <= 0) {
                return startAfterMillis + 24 * 60 * 60 * 1000L // Safeguard
            }
            val elapsed = startAfterMillis - startMillis
            val count = (elapsed / intervalMillis) + 1
            return startMillis + (count * intervalMillis)
        }
    }

    fun getNextTriggerTime(medication: Medication, currentMillis: Long = System.currentTimeMillis()): Long {
        var nextTrigger = calculateRawNextTrigger(medication, currentMillis)

        // Adjust Next Trigger if a dose was logged within 30 minutes of this scheduled time
        if (medication.lastLoggedTime != 0L && (nextTrigger - medication.lastLoggedTime) in 0L until (30 * 60 * 1000L)) {
            nextTrigger = calculateRawNextTrigger(medication, nextTrigger + 60_000L)
        }

        return nextTrigger
    }

    fun scheduleAlarm(context: Context, medication: Medication) {
        if (!medication.isActive) {
            cancelAlarm(context, medication)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextTrigger = if (medication.snoozedUntil > System.currentTimeMillis()) {
            medication.snoozedUntil
        } else {
            getNextTriggerTime(medication)
        }

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra("MED_ID", medication.id)
            putExtra("MED_NAME", medication.name)
            putExtra("MED_DOSAGE", medication.dosage)
            putExtra("MED_INSTRUCTIONS", medication.instructions)
            putExtra("FAMILY_MEMBER_ID", medication.familyMemberId)
            putExtra("SOUND_URI", medication.soundUri)
        }

        // We use the medication ID as requestCode to keep distinct alarms
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Create a user-visible alarm representation (required for AlarmClockInfo)
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                medication.id.toInt() * 10 + 3,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTrigger, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled ALARM_CLOCK (highly exact/immune to Doze) for ${medication.name} (id: ${medication.id}) at $nextTrigger")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm clock: ${e.message}", e)
            // Fallback for exceptional failures
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled fallback exact alarm for ${medication.name} at $nextTrigger")
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled fallback direct exact alarm for ${medication.name} at $nextTrigger")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Final fallback alarm registration failed: ${ex.message}", ex)
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
                } catch (lastEx: Exception) {
                    Log.e(TAG, "Last resort inexact set failed: ${lastEx.message}", lastEx)
                }
            }
        }
    }

    fun cancelAlarm(context: Context, medication: Medication) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MedicationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for ${medication.name} (id: ${medication.id})")
        }
    }

    fun scheduleSnoozeAlarm(context: Context, medication: Medication, snoozeUntil: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra("MED_ID", medication.id)
            putExtra("MED_NAME", medication.name)
            putExtra("MED_DOSAGE", medication.dosage)
            putExtra("MED_INSTRUCTIONS", medication.instructions)
            putExtra("FAMILY_MEMBER_ID", medication.familyMemberId)
            putExtra("SOUND_URI", medication.soundUri)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                medication.id.toInt() * 10 + 3,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(snoozeUntil, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled SNOOZE ALARM for ${medication.name} (id: ${medication.id}) at $snoozeUntil")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeUntil, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeUntil, pendingIntent)
                }
            } catch (ex: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeUntil, pendingIntent)
            }
        }
    }
}
