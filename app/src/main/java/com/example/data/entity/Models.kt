package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String, // Hex string represent color of tag
    val soundUri: String = "",
    val isMe: Boolean = false
)

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = FamilyMember::class,
            parentColumns = ["id"],
            childColumns = ["familyMemberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["familyMemberId"])]
)
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val instructions: String,
    val scheduleType: String, // "WEEKLY" or "INTERVAL"
    val daysOfWeekCommaSeparated: String, // e.g., "Mon,Wed,Fri" or empty for interval
    val intervalHours: Int, // e.g., 8, 12, 24
    val startTime: String, // Time of day or initial start time, format "HH:mm"
    val startDate: Long, // Start epoch timestamp
    val familyMemberId: Long,
    val isActive: Boolean = true,
    val snoozedUntil: Long = 0L,
    val lastLoggedTime: Long = 0L,
    val autoReset: Boolean = false,
    val soundUri: String = "",
    val deleteAfterCompletion: Boolean = false,
    val recordInHistory: Boolean = true
)

@Entity(tableName = "dose_records")
data class DoseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val medicationName: String,
    val familyMemberName: String,
    val dosage: String,
    val scheduledTime: Long, // epoch millis
    val actualTime: Long, // epoch millis
    val status: String // "TAKEN", "SKIPPED", "MISSED"
)
