package com.example.data.repository

import com.example.data.dao.AppDao
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

class MedicationRepository(private val dao: AppDao) {

    // Seeding logic runs on flow starting, or we can expose a dedicated seed method.
    val familyMembersFlow: Flow<List<FamilyMember>> = dao.getAllFamilyMembersFlow()
        .onStart {
            checkAndSeedDefaults()
        }

    val medicationsFlow: Flow<List<Medication>> = dao.getAllMedicationsFlow()

    val doseRecordsFlow: Flow<List<DoseRecord>> = dao.getAllDoseRecordsFlow()

    private suspend fun checkAndSeedDefaults() {
        if (dao.getFamilyMembersCount() == 0) {
            // Seed default "Me" profile
            dao.insertFamilyMember(
                FamilyMember(
                    name = "Me",
                    colorHex = "#2196F3", // Calm Blue
                    isMe = true
                )
            )
        }
    }

    suspend fun getFamilyMemberById(id: Long): FamilyMember? {
        return dao.getFamilyMemberById(id)
    }

    suspend fun insertFamilyMember(familyMember: FamilyMember): Long {
        return dao.insertFamilyMember(familyMember)
    }

    suspend fun updateFamilyMember(familyMember: FamilyMember) {
        dao.updateFamilyMember(familyMember)
    }

    suspend fun deleteFamilyMember(familyMember: FamilyMember) {
        dao.deleteFamilyMember(familyMember)
    }

    suspend fun getMedicationById(id: Long): Medication? {
        return dao.getMedicationById(id)
    }

    suspend fun getAllActiveMedications(): List<Medication> {
        return dao.getAllActiveMedications()
    }

    suspend fun insertMedication(medication: Medication): Long {
        return dao.insertMedication(medication)
    }

    suspend fun deleteMedication(medication: Medication) {
        dao.deleteMedication(medication)
    }

    suspend fun insertDoseRecord(record: DoseRecord): Long {
        return dao.insertDoseRecord(record)
    }

    suspend fun deleteDoseRecord(id: Long) {
        dao.deleteDoseRecord(id)
    }

    suspend fun deleteDoseRecordsByMedicationId(medicationId: Long) {
        dao.deleteDoseRecordsByMedicationId(medicationId)
    }
}
