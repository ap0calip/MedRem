# Technical Specification: MedRed (Medication Reminder Application)

Welcome to the **MedRed Technical Specification**. This document outlines the system philosophy, database schema, background scheduling model, exact alarm protocols, and user interface traits of the MedRed application.

---

## 1. System Philosophy & Objectives
The key objective of MedRed is to provide an **offline-first, zero-latency, high-precision medication scheduling machine** that caters to both individuals and multi-member households. 
*   **Privacy & Local Preservation**: All profiles, medications, schedules, and compliance intake logs are saved locally using an embedded SQLite engine via Room. No profile data ever leaves the device.
*   **Battery-Efficient Dispatch**: Utilizes precise wake alarms (`AlarmManager`) mapped directly to targeted system intent broadcasts to wake up the application only when a target dose window is reached.
*   **Intent-Driven Filtering & Search**: An ergonomic, responsive single-screen dashboard layout that allows the user to search all active medication schedules and historical compliance statistics by profile tags and search keywords simultaneously.

---

## 2. Technical Stack & Dependencies
*   **Language**: Kotlin (`v1.9+` recommended) with Coroutines and Flows for asynchronous state management.
*   **UI Framework**: Jetpack Compose designed with Material Design 3 (M3).
*   **Database**: Room Persistence Library with KSP (Kotlin Symbol Processing).
*   **Background Actions**: Android `AlarmManager` for precise time trigger dispatches and a background `BroadcastReceiver` for boot-reload survival.
*   **Unit & Screenshot Testing**: Robolectric and Roborazzi for local JVM screenshot visual validation.

---

## 3. Database Schema (SQLite / Room)
The application defines three main database tables mapped directly to Room Entity wrappers under `com.example.data.entity`. The schema version is `3`.

### A. `family_members`
Holds individual user profiles. Each profile represents the patient details.
```kotlin
@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String, // String hexadecimal representation of profile color tag
    val isMe: Boolean = false // Flag indicating default self profile
)
```

### B. `medications`
Configures active scheduled medication alerts. A foreign key links each record directly to a `FamilyMember` with `CASCADE` delete behaviors.
```kotlin
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
    val scheduleType: String, // "WEEKLY" (specific days) or "INTERVAL" (every N hours)
    val daysOfWeekCommaSeparated: String, // e.g., "Mon,Wed,Fri" or empty for interval
    val intervalHours: Int, // e.g., 4, 6, 8, 12, 24
    val startTime: String, // format "HH:mm" (24h format, e.g., "08:30")
    val startDate: Long, // Epoch timestamp (epoch millis) representing start date
    val familyMemberId: Long,
    val isActive: Boolean = true,
    val snoozedUntil: Long = 0L,
    val lastLoggedTime: Long = 0L
)
```

### C. `dose_records`
Logs historical status logs of intake events. Captures intake events as they occur.
```kotlin
@Entity(tableName = "dose_records")
data class DoseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val medicationName: String,
    val familyMemberName: String,
    val dosage: String,
    val scheduledTime: Long, // Target scheduled trigger time (epoch millis)
    val actualTime: Long, // Time of action logging (epoch millis)
    val status: String // "TAKEN", "SKIPPED", or "MISSED"
)
```

---

## 4. Architectural Layers
The codebase adheres directly to the Android MVVM pattern, separating the application logic into layers. To see a detailed design and system visualization of these layers, please refer to `/LAYER_SPECIFICATION.md`.

---

## 5. Alarm Scheduling Calculations & Precision
To optimize battery life while maintaining reliable trigger precision, MedRed implements exact scheduling offsets:

### A. Weekly Schedule Method
Target hours and minutes are calculated based on user input. The application calculates the closest upcoming calendar day matching the selected active days list (e.g. `Mon`, `Wed`, `Fri`) starting from the baseline time.

### B. Interval Schedule Method
For periodic medications (e.g., *"Take every 8 hours"*):
1. Compute the delta between current system epoch time and the baseline start epoch.
2. Modulate the difference by the interval duration sequence.
3. Set the target notification trigger index at the exact next upcoming modulo threshold.

### C. Adaptive Trigger Adjustments
When a user logs a dose as **TAKEN** or **SKIPPED**:
* If they execute the log event when there are **less than 30 minutes remaining** until the medication is scheduled to be due, the system intelligently calculates a dynamic shift forwards. This prevents redundant, back-to-back alarm triggers and optimizes the user's dosage compliance spacing.

### D. System-Level Interruption Protocol & Snooze
* **Doze-Mode Resist**: Leverages `AlarmManager.setExactAndAllowWhileIdle()` to guarantee execution across standby or Doze state transitions.
* **Foreground Service Continuity & Wake Activities**: Broadcasts activate high-volume `MediaPlayer` sound loop structures with vibration fallbacks alongside lockscreen-bypassing patient windows (`AlarmActivity`).
* **Snooze Engine**: Users can select the **Snooze** action to silence alarms for exactly **30 minutes**, scheduling an independent, precise snooze wakeup window.

---

## 6. UI & UX Layout Polish (Material Design 3)
The user interface utilizes custom Material 3 components aligned on an asymmetric layout designed with elegant spacing:
*   **Adaptive Header Workspace**: Features a stylized status brand logo incorporating clinic cross geometries, coupled with a distinct "Add Profile" action key.
*   **Profile Tag Ribbon**: High-contrast horizontal chips allowing quick profile traversal.
*   **Unified Active/Inactive Toggles**: Clean, minimalist switch configurations without redundant visual labels (such as "Active" or "Inactive" text).
*   **Shared Keyword Search & History Isolation**: Allows users to filter scheduled medications and historical intake ledger records under composite queries, dynamically combining profile selection with the central text search bar queries.

---

## 7. Accessibility & Validation Tags
All interactive components are fully configured:
*   **Touch Targets**: Minimum interactive layout size is constrained to `48.dp x 48.dp` utilizing surrounding padding configurations.
*   **Testing Tags**:
    *   Search Bar: `Modifier.testTag("medicine_search")`
    *   Floating Action Button: `Modifier.testTag("add_medication_fab")`
    *   Save Reminders Option: `Modifier.testTag("save_medication_button")`
    *   Save Profiles Option: `Modifier.testTag("save_profile_button")`

---

## 8. Development Verification Tasks
To verify complete compilation and execute Robolectric unit/screenshot regressions locally, engineers can utilize Gradle commands:

```bash
# Compile and build the application
gradle assembleDebug

# Run Robolectric unit and UI tests
gradle :app:testDebugUnitTest
```
