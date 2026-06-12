package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import com.example.data.repository.MedicationRepository
import com.example.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MedicationRepository

    init {
        val dao = AppDatabase.getDatabase(application).dao()
        repository = MedicationRepository(dao)
    }

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

    fun addOrUpdateFamilyMember(id: Long = 0L, name: String, colorHex: String, isMe: Boolean = false) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val member = FamilyMember(id = id, name = name.trim(), colorHex = colorHex, isMe = isMe)
                if (id == 0L) {
                    repository.insertFamilyMember(member)
                } else {
                    repository.updateFamilyMember(member)
                }
            }
        }
    }

    fun deleteFamilyMember(familyMember: FamilyMember) {
        viewModelScope.launch {
            val meds = allMedications.value.filter { it.familyMemberId == familyMember.id }
            meds.forEach { med ->
                ReminderScheduler.cancelAlarm(getApplication(), med)
            }
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
        snoozedUntil: Long = 0L
    ) {
        viewModelScope.launch {
            var lastLogged = 0L
            if (id != 0L) {
                repository.getMedicationById(id)?.let {
                    lastLogged = it.lastLoggedTime
                }
            }

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
                lastLoggedTime = lastLogged
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

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            ReminderScheduler.cancelAlarm(getApplication(), medication)
            repository.deleteMedication(medication)
        }
    }

    fun logMedicationDose(medication: Medication, status: String) {
        viewModelScope.launch {
            // Retrieve family member name
            val member = repository.getFamilyMemberById(medication.familyMemberId)
            val memberName = member?.name ?: "Me"

            val now = System.currentTimeMillis()
            val record = DoseRecord(
                medicationId = medication.id,
                medicationName = medication.name,
                familyMemberName = memberName,
                dosage = medication.dosage,
                scheduledTime = now, // manual logging schedules for now
                actualTime = now,
                status = status
            )

            repository.insertDoseRecord(record)

            // Auto reschedule to next interval or day
            val updatedMed = if (medication.isActive) {
                medication.copy(snoozedUntil = 0L, lastLoggedTime = now)
            } else {
                medication.copy(lastLoggedTime = now)
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
}
