package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import com.example.data.repository.MedicationRepository
import com.example.reminder.ActiveAlarmManager
import com.example.reminder.MedicationAlarmService
import com.example.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ImportResult(
    val success: Boolean,
    val importedProfilesCount: Int,
    val importedMedicationsCount: Int,
    val errorMessage: String? = null
)

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MedicationRepository

    init {
        val dao = AppDatabase.getDatabase(application).dao()
        repository = MedicationRepository(dao)

        // Reschedule active alarms on startup in case they were lost or restored from Auto Backup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.sanitizeRecurringDeleteAfterCompletion()
                val activeMeds = dao.getAllActiveMedications()
                for (med in activeMeds) {
                    ReminderScheduler.scheduleAlarm(application, med)
                }
                Log.d("MedicationViewModel", "Alarms rescheduled on ViewModel init for ${activeMeds.size} active medications.")
            } catch (e: Exception) {
                Log.e("MedicationViewModel", "Failed to reschedule alarms on ViewModel init: ${e.message}", e)
            }
        }
    }

    // --- Dynamic Labels ---
    private val prefs = application.getSharedPreferences("MedRemPrefs", android.content.Context.MODE_PRIVATE)

    val labelMode = MutableStateFlow(prefs.getString("label_mode", "ITEM") ?: "ITEM")
    
    val customProfileSing = MutableStateFlow(prefs.getString("custom_profile_sing", "Profile") ?: "Profile")
    val customProfilePlur = MutableStateFlow(prefs.getString("custom_profile_plur", "Profiles") ?: "Profiles")
    
    val customMedicationSing = MutableStateFlow(prefs.getString("custom_med_sing", "Medication") ?: "Medication")
    val customMedicationPlur = MutableStateFlow(prefs.getString("custom_med_plur", "Medications") ?: "Medications")
    
    val customDosageSing = MutableStateFlow(prefs.getString("custom_dosage_sing", "Dosage") ?: "Dosage")
    val customDosagePlur = MutableStateFlow(prefs.getString("custom_dosage_plur", "Dosages") ?: "Dosages")

    val customTakenLabel = MutableStateFlow(prefs.getString("custom_taken_label", "Taken") ?: "Taken")

    fun setLabelMode(mode: String) {
        labelMode.value = mode
        prefs.edit().putString("label_mode", mode).commit()
    }

    fun setCustomLabels(
        profileSing: String,
        profilePlur: String,
        medicationSing: String,
        medicationPlur: String,
        dosageSing: String,
        dosagePlur: String,
        takenLabel: String
    ) {
        customProfileSing.value = profileSing
        customProfilePlur.value = profilePlur
        customMedicationSing.value = medicationSing
        customMedicationPlur.value = medicationPlur
        customDosageSing.value = dosageSing
        customDosagePlur.value = dosagePlur
        customTakenLabel.value = takenLabel
        
        prefs.edit()
            .putString("custom_profile_sing", profileSing)
            .putString("custom_profile_plur", profilePlur)
            .putString("custom_med_sing", medicationSing)
            .putString("custom_med_plur", medicationPlur)
            .putString("custom_dosage_sing", dosageSing)
            .putString("custom_dosage_plur", dosagePlur)
            .putString("custom_taken_label", takenLabel)
            .putString("label_mode", "CUSTOM")
            .commit()
    }

    private fun calcProfileSing(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Profile"
        "ITEM" -> "Category"
        "CUSTOM" -> custom
        else -> "Category"
    }

    private fun calcProfilePlur(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Profiles"
        "ITEM" -> "Categories"
        "CUSTOM" -> custom
        else -> "Categories"
    }

    private fun calcMedicationSing(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Medication"
        "ITEM" -> "Item"
        "CUSTOM" -> custom
        else -> "Item"
    }

    private fun calcMedicationPlur(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Medications"
        "ITEM" -> "Items"
        "CUSTOM" -> custom
        else -> "Items"
    }

    private fun calcDosageSing(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Dosage"
        "ITEM" -> "Description"
        "CUSTOM" -> custom
        else -> "Description"
    }

    private fun calcDosagePlur(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Dosages"
        "ITEM" -> "Descriptions"
        "CUSTOM" -> custom
        else -> "Descriptions"
    }

    private fun calcTakenLabel(mode: String, custom: String): String = when (mode) {
        "MEDICATION" -> "Taken"
        "ITEM" -> "Completed"
        "CUSTOM" -> custom
        else -> "Completed"
    }

    val currentProfileSing: StateFlow<String> = combine(labelMode, customProfileSing) { mode, custom ->
        calcProfileSing(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcProfileSing(labelMode.value, customProfileSing.value))

    val currentProfilePlur: StateFlow<String> = combine(labelMode, customProfilePlur) { mode, custom ->
        calcProfilePlur(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcProfilePlur(labelMode.value, customProfilePlur.value))

    val currentMedicationSing: StateFlow<String> = combine(labelMode, customMedicationSing) { mode, custom ->
        calcMedicationSing(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcMedicationSing(labelMode.value, customMedicationSing.value))

    val currentMedicationPlur: StateFlow<String> = combine(labelMode, customMedicationPlur) { mode, custom ->
        calcMedicationPlur(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcMedicationPlur(labelMode.value, customMedicationPlur.value))

    val currentDosageSing: StateFlow<String> = combine(labelMode, customDosageSing) { mode, custom ->
        calcDosageSing(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcDosageSing(labelMode.value, customDosageSing.value))

    val currentDosagePlur: StateFlow<String> = combine(labelMode, customDosagePlur) { mode, custom ->
        calcDosagePlur(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcDosagePlur(labelMode.value, customDosagePlur.value))

    val currentTakenLabel: StateFlow<String> = combine(labelMode, customTakenLabel) { mode, custom ->
        calcTakenLabel(mode, custom)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, calcTakenLabel(labelMode.value, customTakenLabel.value))

    // --- State Sources ---
    val familyMembers: StateFlow<List<FamilyMember>> = repository.familyMembersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allMedications: StateFlow<List<Medication>> = repository.medicationsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val doseRecords: StateFlow<List<DoseRecord>> = repository.doseRecordsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Filtering states ---
    // 0L represents "All Profiles"
    val selectedFamilyMemberId = MutableStateFlow(0L)
    val searchQuery = MutableStateFlow("")

    val filteredMedications: StateFlow<List<Medication>> = combine(
        allMedications,
        selectedFamilyMemberId,
        searchQuery
    ) { medications, selectedId, query ->
        medications.filter { med ->
            val matchesProfile = (selectedId == 0L || med.familyMemberId == selectedId)
            val matchesSearch = query.isEmpty() || med.name.contains(query, ignoreCase = true) ||
                    med.dosage.contains(query, ignoreCase = true) ||
                    med.instructions.contains(query, ignoreCase = true)
            matchesProfile && matchesSearch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Actions ---
    fun selectFamilyMember(id: Long) {
        selectedFamilyMemberId.value = id
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addOrUpdateFamilyMember(id: Long = 0L, name: String, colorHex: String, soundUri: String, isMe: Boolean = false) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val member = FamilyMember(id = id, name = name.trim(), colorHex = colorHex, soundUri = soundUri, isMe = isMe)
                val memberId = if (id == 0L) {
                    repository.insertFamilyMember(member)
                } else {
                    repository.updateFamilyMember(member)
                    id
                }

                // Reschedule active medications for this profile so scheduled alarms reflect updated sound
                val activeMeds = repository.getAllActiveMedications().filter { it.familyMemberId == memberId }
                activeMeds.forEach { med ->
                    ReminderScheduler.scheduleAlarm(getApplication(), med)
                }

                // If alarm service is actively running with alarms, notify it to update the playing sound live
                if (ActiveAlarmManager.activeAlarms.value.isNotEmpty()) {
                    val updateIntent = Intent(getApplication(), MedicationAlarmService::class.java).apply {
                        action = MedicationAlarmService.ACTION_UPDATE_SOUND
                        putExtra("FAMILY_MEMBER_ID", memberId)
                        putExtra("SOUND_URI", soundUri)
                    }
                    try {
                        getApplication<Application>().startService(updateIntent)
                    } catch (e: Exception) {
                        Log.e("MedicationViewModel", "Failed to send ACTION_UPDATE_SOUND to service: ${e.message}")
                    }
                }
            }
        }
    }

    fun deleteFamilyMember(familyMember: FamilyMember) {
        viewModelScope.launch {
            if (familyMembers.value.size <= 1) return@launch
            val meds = allMedications.value.filter { it.familyMemberId == familyMember.id }
            meds.forEach { med ->
                ReminderScheduler.cancelAlarm(getApplication(), med)
                repository.deleteDoseRecordsByMedicationId(med.id)
            }
            repository.deleteDoseRecordsByFamilyMemberName(familyMember.name)
            repository.deleteFamilyMember(familyMember)
            if (selectedFamilyMemberId.value == familyMember.id) {
                selectedFamilyMemberId.value = 0L
            }
        }
    }

    fun addOrUpdateMedication(
        id: Long = 0,
        name: String,
        dosage: String,
        instructions: String,
        scheduleType: String,
        daysOfWeekCommaSeparated: String,
        intervalHours: Int,
        startTime: String,
        startDate: Long,
        familyMemberId: Long,
        isActive: Boolean = true,
        snoozedUntil: Long = 0L,
        autoReset: Boolean = false,
        soundUri: String,
        deleteAfterCompletion: Boolean = false,
        recordInHistory: Boolean = true
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            if (id == 0L) {
                val duplicate = allMedications.value.find { it.name.trim().equals(trimmedName, ignoreCase = true) }
                if (duplicate != null) {
                    android.util.Log.w("MedicationViewModel", "Cannot add duplicate medication name: $name")
                    return@launch
                }
            } else {
                val duplicate = allMedications.value.find { it.id != id && it.name.trim().equals(trimmedName, ignoreCase = true) }
                if (duplicate != null) {
                    android.util.Log.w("MedicationViewModel", "Cannot update medication with duplicate name: $name")
                    return@launch
                }
            }

            var lastLogged = 0L
            if (id != 0L) {
                repository.getMedicationById(id)?.let {
                    // Only preserve lastLogged if schedule parameters haven't changed.
                    // If user updated time, days, schedule type, or start date, clear lastLogged so the newly scheduled alarm is not skipped.
                    if (it.startTime == startTime &&
                        it.scheduleType == scheduleType &&
                        it.daysOfWeekCommaSeparated == daysOfWeekCommaSeparated &&
                        it.intervalHours == intervalHours &&
                        it.startDate == startDate) {
                        lastLogged = it.lastLoggedTime
                    } else {
                        lastLogged = 0L
                    }
                }
            }

            val effectiveDeleteAfterCompletion = if (scheduleType == "ONE_TIME") deleteAfterCompletion else false

            val med = Medication(
                id = id,
                name = name.trim(),
                dosage = dosage.trim(),
                instructions = instructions.trim(),
                scheduleType = scheduleType,
                daysOfWeekCommaSeparated = daysOfWeekCommaSeparated,
                intervalHours = intervalHours,
                startTime = startTime,
                startDate = startDate,
                familyMemberId = familyMemberId,
                isActive = isActive,
                snoozedUntil = snoozedUntil,
                lastLoggedTime = lastLogged,
                autoReset = autoReset,
                soundUri = soundUri,
                deleteAfterCompletion = effectiveDeleteAfterCompletion,
                recordInHistory = recordInHistory
            )

            val newId = repository.insertMedication(med)
            val finalMed = med.copy(id = if (id == 0L) newId else id)

            if (isActive) {
                ReminderScheduler.scheduleAlarm(getApplication(), finalMed)
            } else {
                ReminderScheduler.cancelAlarm(getApplication(), finalMed)
            }
            Log.d("MedicationViewModel", "Saved medication ${finalMed.name} and synced alarms.")
        }
    }

    fun deleteMedication(medication: Medication, deleteHistory: Boolean = false) {
        viewModelScope.launch {
            ReminderScheduler.cancelAlarm(getApplication(), medication)
            repository.deleteMedication(medication)
            if (deleteHistory) {
                repository.deleteDoseRecordsByMedicationId(medication.id)
            }
        }
    }

    fun logMedicationDose(medication: Medication, status: String) {
        viewModelScope.launch {
            // Retrieve family member name
            val member = repository.getFamilyMemberById(medication.familyMemberId)
            val memberName = member?.name ?: "Me"

            val now = System.currentTimeMillis()
            val nextReminderTime = if (medication.snoozedUntil > now) {
                medication.snoozedUntil
            } else {
                ReminderScheduler.getNextTriggerTime(medication, now)
            }
            val scheduledDoseTime = if (nextReminderTime > 0L) nextReminderTime else now

            if (medication.recordInHistory) {
                val record = DoseRecord(
                    medicationId = medication.id,
                    medicationName = medication.name,
                    familyMemberName = memberName,
                    dosage = medication.dosage,
                    scheduledTime = scheduledDoseTime,
                    actualTime = now,
                    status = status
                )
                repository.insertDoseRecord(record)
            }

            if (medication.scheduleType == "ONE_TIME") {
                ReminderScheduler.cancelAlarm(getApplication(), medication)
                if (medication.deleteAfterCompletion) {
                    repository.deleteMedication(medication)
                } else {
                    val disabledMed = medication.copy(isActive = false, snoozedUntil = 0L, lastLoggedTime = now)
                    repository.insertMedication(disabledMed)
                }
                return@launch
            }

            // Auto reschedule to next interval or day
            var updatedMed = if (medication.isActive) {
                medication.copy(snoozedUntil = 0L, lastLoggedTime = now)
            } else {
                medication.copy(lastLoggedTime = now)
            }

            if (medication.autoReset && medication.scheduleType == "CUSTOM" && medication.daysOfWeekCommaSeparated != "hours") {
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = now
                
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                updatedMed = updatedMed.copy(
                    startDate = calendar.timeInMillis
                )
            }
            
            repository.insertMedication(updatedMed)
            if (updatedMed.isActive) {
                ReminderScheduler.scheduleAlarm(getApplication(), updatedMed)
            }
        }
    }

    fun deleteDoseRecord(recordId: Long) {
        viewModelScope.launch {
            repository.deleteDoseRecord(recordId)
        }
    }

    fun clearAllDoseRecords(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = doseRecords.value.size
            repository.deleteAllDoseRecords()
            onComplete?.invoke(count)
        }
    }

    fun clearDoseRecordsByMedication(medicationId: Long, medicationName: String, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = doseRecords.value.count { it.medicationId == medicationId || it.medicationName.equals(medicationName, ignoreCase = true) }
            repository.deleteDoseRecordsByMedicationId(medicationId)
            repository.deleteDoseRecordsByMedicationName(medicationName)
            onComplete?.invoke(count)
        }
    }

    fun clearDoseRecordsByFamilyMember(memberName: String, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = doseRecords.value.count { it.familyMemberName.equals(memberName, ignoreCase = true) }
            repository.deleteDoseRecordsByFamilyMemberName(memberName)
            onComplete?.invoke(count)
        }
    }

    fun clearDoseRecordsOlderThan(days: Int, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
            val count = doseRecords.value.count { it.actualTime < cutoff }
            repository.deleteDoseRecordsOlderThan(cutoff)
            onComplete?.invoke(count)
        }
    }

    fun clearDoseRecordsByStatus(status: String, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = doseRecords.value.count { it.status.equals(status, ignoreCase = true) }
            repository.deleteDoseRecordsByStatus(status)
            onComplete?.invoke(count)
        }
    }

    fun exportSchedulesJson(profileId: Long?): String {
        try {
            val rootObj = JSONObject()
            val profilesArr = JSONArray()
            val medsArr = JSONArray()

            val allMeds = allMedications.value
            val allMembers = familyMembers.value

            val filteredMembers = if (profileId == null || profileId == 0L) {
                allMembers
            } else {
                allMembers.filter { it.id == profileId }
            }

            val filteredMeds = if (profileId == null || profileId == 0L) {
                allMeds
            } else {
                allMeds.filter { it.familyMemberId == profileId }
            }

            filteredMembers.forEach { member ->
                val mObj = JSONObject().apply {
                    put("id", member.id)
                    put("name", member.name)
                    put("colorHex", member.colorHex)
                    put("soundUri", member.soundUri)
                    put("isMe", member.isMe)
                }
                profilesArr.put(mObj)
            }

            filteredMeds.forEach { med ->
                val medObj = JSONObject().apply {
                    put("id", med.id)
                    put("name", med.name)
                    put("dosage", med.dosage)
                    put("instructions", med.instructions)
                    put("scheduleType", med.scheduleType)
                    put("daysOfWeekCommaSeparated", med.daysOfWeekCommaSeparated)
                    put("intervalHours", med.intervalHours)
                    put("startTime", med.startTime)
                    put("startDate", med.startDate)
                    put("familyMemberId", med.familyMemberId)
                    put("isActive", med.isActive)
                    put("snoozedUntil", med.snoozedUntil)
                    put("lastLoggedTime", med.lastLoggedTime)
                    put("soundUri", med.soundUri)
                    put("deleteAfterCompletion", med.deleteAfterCompletion)
                    put("recordInHistory", med.recordInHistory)
                }
                medsArr.put(medObj)
            }

            rootObj.put("profiles", profilesArr)
            rootObj.put("medications", medsArr)
            rootObj.put("label_mode", labelMode.value)
            val customLabelsObj = JSONObject().apply {
                put("profile_sing", customProfileSing.value)
                put("profile_plur", customProfilePlur.value)
                put("med_sing", customMedicationSing.value)
                put("med_plur", customMedicationPlur.value)
                put("dosage_sing", customDosageSing.value)
                put("dosage_plur", customDosagePlur.value)
                put("taken_label", customTakenLabel.value)
            }
            rootObj.put("custom_labels", customLabelsObj)
            return rootObj.toString(2)
        } catch (e: Exception) {
            Log.e("MedicationViewModel", "Failed to export JSON: ${e.message}", e)
            return "{\"error\": \"Export failed: ${e.message}\"}"
        }
    }

    fun importSchedulesJson(jsonStr: String, onComplete: (ImportResult) -> Unit) {
        viewModelScope.launch {
            try {
                if (jsonStr.isBlank()) {
                    onComplete(ImportResult(false, 0, 0, "Input is empty"))
                    return@launch
                }

                val rootObj = JSONObject(jsonStr)
                if (rootObj.has("label_mode")) {
                    val mode = rootObj.getString("label_mode")
                    val customObj = rootObj.optJSONObject("custom_labels")
                    if (customObj != null) {
                        setCustomLabels(
                            customObj.optString("profile_sing", "Profile"),
                            customObj.optString("profile_plur", "Profiles"),
                            customObj.optString("med_sing", "Medication"),
                            customObj.optString("med_plur", "Medications"),
                            customObj.optString("dosage_sing", "Dosage"),
                            customObj.optString("dosage_plur", "Dosages"),
                            customObj.optString("taken_label", "Taken")
                        )
                    }
                    setLabelMode(mode)
                }

                val profilesArr = rootObj.optJSONArray("profiles") ?: JSONArray()
                val medsArr = rootObj.optJSONArray("medications") ?: JSONArray()

                var importedProfiles = 0
                var importedMeds = 0

                val profileIdMap = mutableMapOf<Long, Long>()

                // 1. Process profiles / family members
                for (i in 0 until profilesArr.length()) {
                    val mObj = profilesArr.getJSONObject(i)
                    val origId = mObj.getLong("id")
                    val name = mObj.getString("name").trim()
                    val colorHex = mObj.optString("colorHex", "#2196F3")
                    val soundUri = mObj.optString("soundUri", "")
                    val isMe = mObj.optBoolean("isMe", false)

                    // Check if already exists by name
                    val existing = familyMembers.value.find { it.name.equals(name, ignoreCase = true) }
                    if (existing != null) {
                        profileIdMap[origId] = existing.id
                    } else {
                        val newMember = FamilyMember(name = name, colorHex = colorHex, soundUri = soundUri, isMe = isMe)
                        val newId = repository.insertFamilyMember(newMember)
                        profileIdMap[origId] = newId
                        importedProfiles++
                    }
                }

                // Default family member fallback if map fails
                val defaultFamilyId = familyMembers.value.firstOrNull()?.id ?: 1L

                // 2. Process medications
                for (i in 0 until medsArr.length()) {
                    val medObj = medsArr.getJSONObject(i)
                    val name = medObj.getString("name").trim()
                    val dosage = medObj.getString("dosage").trim()
                    val instructions = medObj.optString("instructions", "").trim()
                    val scheduleType = medObj.getString("scheduleType")
                    val daysOfWeekCommaSeparated = medObj.optString("daysOfWeekCommaSeparated", "")
                    val intervalHours = medObj.optInt("intervalHours", 0)
                    val startTime = medObj.getString("startTime")
                    val startDate = medObj.optLong("startDate", System.currentTimeMillis())
                    val origFamilyMemberId = medObj.getLong("familyMemberId")
                    val isActive = medObj.optBoolean("isActive", true)
                    val snoozedUntil = medObj.optLong("snoozedUntil", 0L)
                    val lastLoggedTime = medObj.optLong("lastLoggedTime", 0L)
                    val soundUri = medObj.optString("soundUri", "")
                    val deleteAfterCompletion = medObj.optBoolean("deleteAfterCompletion", false)
                    val recordInHistory = medObj.optBoolean("recordInHistory", true)

                    val mappedFamilyMemberId = profileIdMap[origFamilyMemberId] ?: defaultFamilyId

                    // Check if medication with same name already exists to avoid repeat item/medication name
                    val duplicate = allMedications.value.find {
                        it.name.trim().equals(name, ignoreCase = true)
                    }

                    if (duplicate == null) {
                        val medInstance = Medication(
                            name = name,
                            dosage = dosage,
                            instructions = instructions,
                            scheduleType = scheduleType,
                            daysOfWeekCommaSeparated = daysOfWeekCommaSeparated,
                            intervalHours = intervalHours,
                            startTime = startTime,
                            startDate = startDate,
                            familyMemberId = mappedFamilyMemberId,
                            isActive = isActive,
                            snoozedUntil = snoozedUntil,
                            lastLoggedTime = lastLoggedTime,
                            soundUri = soundUri,
                            deleteAfterCompletion = if (scheduleType == "ONE_TIME") deleteAfterCompletion else false,
                            recordInHistory = recordInHistory
                        )
                        val newMedId = repository.insertMedication(medInstance)
                        val insertedMed = medInstance.copy(id = newMedId)

                        if (insertedMed.isActive) {
                            ReminderScheduler.scheduleAlarm(getApplication(), insertedMed)
                        }
                        importedMeds++
                    }
                }

                onComplete(ImportResult(true, importedProfiles, importedMeds))
            } catch (e: Exception) {
                Log.e("MedicationViewModel", "Failed to import JSON: ${e.message}", e)
                onComplete(ImportResult(false, 0, 0, "Failed to parse backup metadata: ${e.localizedMessage}"))
            }
        }
    }
}
