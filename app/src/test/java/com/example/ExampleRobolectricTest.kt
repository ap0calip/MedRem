package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import com.example.viewmodel.MedicationViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MedRem", appName)
  }

  @Test
  fun `recurring reminders never have deleteAfterCompletion and are not deleted when taken`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val dao = db.dao()
    val memberId = dao.insertFamilyMember(FamilyMember(name = "Test User", colorHex = "#2196F3", isMe = true))
    val vm = MedicationViewModel(app)

    // Attempt to add an INTERVAL medication with deleteAfterCompletion = true
    val intervalMed = Medication(
      name = "Vitamin D",
      dosage = "1000 IU",
      instructions = "Daily",
      scheduleType = "INTERVAL",
      daysOfWeekCommaSeparated = "",
      intervalHours = 8,
      startTime = "08:00",
      startDate = System.currentTimeMillis(),
      familyMemberId = memberId,
      isActive = true,
      soundUri = "",
      deleteAfterCompletion = false
    )
    val medId = dao.insertMedication(intervalMed)
    val med = dao.getMedicationById(medId)!!

    assertFalse("Interval reminder must not have deleteAfterCompletion=true", med.deleteAfterCompletion)

    // Now test logging TAKEN does not delete it
    vm.logMedicationDose(med, "TAKEN")
    kotlinx.coroutines.delay(200)

    val medAfterTaken = dao.getMedicationById(med.id)
    assertNotNull("Interval reminder must not be deleted after being taken", medAfterTaken)
  }

  @Test
  fun `one time reminder with deleteAfterCompletion true is deleted when taken`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val dao = db.dao()
    val memberId = dao.insertFamilyMember(FamilyMember(name = "Test User 2", colorHex = "#2196F3", isMe = true))
    val vm = MedicationViewModel(app)

    val oneTimeMed = Medication(
      name = "Aspirin Once",
      dosage = "500 mg",
      instructions = "After lunch",
      scheduleType = "ONE_TIME",
      daysOfWeekCommaSeparated = "",
      intervalHours = 0,
      startTime = "12:00",
      startDate = System.currentTimeMillis(),
      familyMemberId = memberId,
      isActive = true,
      soundUri = "",
      deleteAfterCompletion = true
    )
    val medId = dao.insertMedication(oneTimeMed)
    val med = dao.getMedicationById(medId)!!

    assertTrue("One-time reminder should keep deleteAfterCompletion=true", med.deleteAfterCompletion)

    vm.logMedicationDose(med, "TAKEN")
    var medAfterTaken: Medication? = med
    for (i in 1..20) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
      kotlinx.coroutines.delay(100)
      medAfterTaken = dao.getMedicationById(med.id)
      if (medAfterTaken == null) break
    }

    assertNull("One-time reminder must be deleted after being taken", medAfterTaken)
  }

  @Test
  fun `database sanitization cleans up any legacy recurring reminders with deleteAfterCompletion true`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val dao = db.dao()
    val memberId = dao.insertFamilyMember(FamilyMember(name = "Test User 3", colorHex = "#2196F3", isMe = true))

    // Insert directly into DB with deleteAfterCompletion = true for WEEKLY
    val weeklyMed = Medication(
      name = "Blood Pressure Med",
      dosage = "10 mg",
      instructions = "",
      scheduleType = "WEEKLY",
      daysOfWeekCommaSeparated = "Mon,Wed,Fri",
      intervalHours = 0,
      startTime = "09:00",
      startDate = System.currentTimeMillis(),
      familyMemberId = memberId,
      isActive = true,
      deleteAfterCompletion = true
    )
    val id = dao.insertMedication(weeklyMed)

    dao.sanitizeRecurringDeleteAfterCompletion()

    val sanitized = dao.getMedicationById(id)
    assertNotNull(sanitized)
    assertFalse("Sanitization must reset deleteAfterCompletion to false for non-ONE_TIME", sanitized!!.deleteAfterCompletion)
  }
}
