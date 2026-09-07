package com.example

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TestBug {
  @Test
  fun simulateUserAction() {
    val now = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 8)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    
    // User creates alarm for every 1 day at 10:00, starting today.
    var med = com.example.data.entity.Medication(
      id = 1L,
      name = "Test",
      dosage = "1",
      instructions = "",
      scheduleType = "CUSTOM",
      daysOfWeekCommaSeparated = "days",
      intervalHours = 1,
      startTime = "10:00",
      startDate = now.timeInMillis, // they selected today
      familyMemberId = 1L,
      autoReset = true,
      lastLoggedTime = 0L
    )
    
    // User clicks Taken at 9:00 today.
    val takenTime = Calendar.getInstance().apply {
      timeInMillis = now.timeInMillis
      set(Calendar.HOUR_OF_DAY, 9)
    }.timeInMillis
    
    // MedicationViewModel logic:
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = takenTime
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    
    med = med.copy(
      startDate = calendar.timeInMillis,
      lastLoggedTime = takenTime
    )
    
    val nextTrigger = com.example.reminder.ReminderScheduler.getNextTriggerTime(med, takenTime)
    
    val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
    println("Next trigger is: ${triggerCal.time}")
  }
}
