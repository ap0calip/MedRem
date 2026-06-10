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

    // --- Dose Records ---
    @Query("SELECT * FROM dose_records ORDER BY actualTime DESC")
    fun getAllDoseRecordsFlow(): Flow<List<DoseRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseRecord(doseRecord: DoseRecord): Long

    @Query("DELETE FROM dose_records WHERE id = :id")
    suspend fun deleteDoseRecord(id: Long)
}
