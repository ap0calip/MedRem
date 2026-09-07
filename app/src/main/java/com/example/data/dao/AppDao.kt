package com.example.data.dao

import androidx.room.*
import com.example.data.entity.DoseRecord
import com.example.data.entity.FamilyMember
import com.example.data.entity.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- Family Members ---
    @Query("SELECT * FROM family_members ORDER BY isMe DESC, name ASC")
    fun getAllFamilyMembersFlow(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE id = :id")
    suspend fun getFamilyMemberById(id: Long): FamilyMember?

    @Query("SELECT COUNT(*) FROM family_members")
    suspend fun getFamilyMembersCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMember(familyMember: FamilyMember): Long

    @Update
    suspend fun updateFamilyMember(familyMember: FamilyMember)

    @Delete
    suspend fun deleteFamilyMember(familyMember: FamilyMember)

    // --- Medications ---
    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedicationsFlow(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isActive = 1")
    suspend fun getAllActiveMedications(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("UPDATE medications SET deleteAfterCompletion = 0 WHERE scheduleType != 'ONE_TIME'")
    suspend fun sanitizeRecurringDeleteAfterCompletion()

    // --- Dose Records ---
    @Query("SELECT * FROM dose_records ORDER BY actualTime DESC")
    fun getAllDoseRecordsFlow(): Flow<List<DoseRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseRecord(doseRecord: DoseRecord): Long

    @Query("DELETE FROM dose_records WHERE id = :id")
    suspend fun deleteDoseRecord(id: Long)

    @Query("DELETE FROM dose_records WHERE medicationId = :medicationId")
    suspend fun deleteDoseRecordsByMedicationId(medicationId: Long)

    @Query("DELETE FROM dose_records WHERE familyMemberName = :familyMemberName")
    suspend fun deleteDoseRecordsByFamilyMemberName(familyMemberName: String)

    @Query("DELETE FROM dose_records")
    suspend fun deleteAllDoseRecords()

    @Query("DELETE FROM dose_records WHERE medicationName = :medicationName")
    suspend fun deleteDoseRecordsByMedicationName(medicationName: String)

    @Query("DELETE FROM dose_records WHERE actualTime < :timestamp")
    suspend fun deleteDoseRecordsOlderThan(timestamp: Long)

    @Query("DELETE FROM dose_records WHERE status = :status")
    suspend fun deleteDoseRecordsByStatus(status: String)
}
