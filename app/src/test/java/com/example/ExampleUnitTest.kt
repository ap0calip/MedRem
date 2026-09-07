package com.example

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testNextAlarmInfoCalculation_Today() {
    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 20)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val now = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 18)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val info = calculateNextAlarmInfo(cal.timeInMillis, is12Hour = true, nowMillis = now.timeInMillis)
    assertEquals("Today", info.dayText)
    assertTrue(info.relativeText.contains("2 hours") || info.relativeText.contains("In 2 hour"))
    assertFalse(info.isPastToday)
  }

  @Test
  fun testNextAlarmInfoCalculation_Tomorrow() {
    val now = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 21)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val tomorrow = Calendar.getInstance().apply {
      timeInMillis = now.timeInMillis
      add(Calendar.DAY_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, 8)
      set(Calendar.MINUTE, 30)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val info = calculateNextAlarmInfo(tomorrow.timeInMillis, is12Hour = true, nowMillis = now.timeInMillis)
    assertEquals("Tomorrow", info.dayText)
    assertTrue(info.isPastToday)
  }

  @Test
  fun testReminderScheduler_OneTimePastTimeMovesToNextDay() {
    val now = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 15)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    // Alarm set for 09:00 AM on today
    val med = com.example.data.entity.Medication(
      id = 1L,
      name = "Morning Vitamin",
      dosage = "1 tablet",
      instructions = "",
      scheduleType = "ONE_TIME",
      daysOfWeekCommaSeparated = "",
      intervalHours = 1,
      startTime = "09:00",
      startDate = now.timeInMillis,
      familyMemberId = 1L
    )
    val trigger = com.example.reminder.ReminderScheduler.calculateRawNextTrigger(med, now.timeInMillis)
    val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }
    
    val expectedCal = Calendar.getInstance().apply {
      timeInMillis = now.timeInMillis
      add(Calendar.DAY_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, 9)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    assertEquals(expectedCal.get(Calendar.DAY_OF_YEAR), triggerCal.get(Calendar.DAY_OF_YEAR))
    assertEquals(9, triggerCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, triggerCal.get(Calendar.MINUTE))
  }

  @Test
  fun testCustomRepeat_1Day_ResetDay_WhenTakenResetsToNextDay() {
    val today = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 13)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val startDay = Calendar.getInstance().apply {
      timeInMillis = today.timeInMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val med = com.example.data.entity.Medication(
      id = 2L,
      name = "Daily Med",
      dosage = "1 pill",
      instructions = "",
      scheduleType = "CUSTOM",
      daysOfWeekCommaSeparated = "days",
      intervalHours = 1,
      startTime = "14:00",
      startDate = startDay.timeInMillis,
      familyMemberId = 1L,
      autoReset = true,
      lastLoggedTime = today.timeInMillis
    )
    val trigger = com.example.reminder.ReminderScheduler.getNextTriggerTime(med, today.timeInMillis)
    val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }

    val tomorrowCal = Calendar.getInstance().apply {
      timeInMillis = today.timeInMillis
      add(Calendar.DAY_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, 14)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    assertEquals(tomorrowCal.get(Calendar.DAY_OF_YEAR), triggerCal.get(Calendar.DAY_OF_YEAR))
    assertEquals(14, triggerCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, triggerCal.get(Calendar.MINUTE))
  }

  @Test
  fun testCustomRepeat_1Week_ResetDay_WhenTakenResetsToNextWeek() {
    val today = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 13)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val startDay = Calendar.getInstance().apply {
      timeInMillis = today.timeInMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val med = com.example.data.entity.Medication(
      id = 3L,
      name = "Weekly Med",
      dosage = "1 pill",
      instructions = "",
      scheduleType = "CUSTOM",
      daysOfWeekCommaSeparated = "weeks",
      intervalHours = 1,
      startTime = "14:00",
      startDate = startDay.timeInMillis,
      familyMemberId = 1L,
      autoReset = true,
      lastLoggedTime = today.timeInMillis
    )
    val trigger = com.example.reminder.ReminderScheduler.getNextTriggerTime(med, today.timeInMillis)
    val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }

    val nextWeekCal = Calendar.getInstance().apply {
      timeInMillis = today.timeInMillis
      add(Calendar.WEEK_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, 14)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    assertEquals(nextWeekCal.get(Calendar.DAY_OF_YEAR), triggerCal.get(Calendar.DAY_OF_YEAR))
    assertEquals(14, triggerCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, triggerCal.get(Calendar.MINUTE))
  }
}

