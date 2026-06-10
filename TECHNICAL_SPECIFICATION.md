# Technical Specification: MedRed (Medication Reminder Application)

Welcome to the **MedRed Technical Specification**. This document outlines the technical design, system architecture, database schema, background scheduling model, and user interface layouts of the MedRed application. MedRed is a production-ready Android-based personal and family medication reminder application built with Jetpack Compose, Room DB, and Android’s native AlarmManager system.

---

## 1. System Philosophy & Objectives
The key objective of MedRed is to provide an **offline-first, zero-latency, high-precision medication scheduling machine** that caters to both individuals and multi-member households. 
*   **Privacy & Local Preservation**: All profiles, medications, schedules, and compliance intake logs are saved locally using an embedded SQLite engine via Room. No profile data ever leaves the device.
*   **Battery-Efficient Dispatch**: Utilizes precise wake alarms (`AlarmManager`) mapped directly to targeted system intent broadcasts to wake up the application only when a target dose window is reached.
*   **Intent-Driven Filtering**: An ergonomic, responsive single-screen dashboard layout that allows the user to filter all medicine schedules and compliance statistics by profile tags at a single click.

---

## 2. Technical Stack & Dependencies
*   **Language**: Kotlin (`v1.9+` recommended) with Coroutines and Flows for asynchronous state management.
*   **UI Framework**: Jetpack Compose designed with Material Design 3 (M3).
*   **Database**: Room Persistence Library with KSP (Kotlin Symbol Processing).
*   **Background Actions**: Android `AlarmManager` for precise time trigger dispatches and a background `BroadcastReceiver` for boot-reload survival.
*   **Unit & Screenshot Testing**: Robolectric and Roborazzi for local JVM screenshot visual validation.

---

## 3. Database Schema (SQLite / Room)
The application defines three main database tables mapped directly to Room Entity wrappers under `com.example.data.entity`:

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
    val isActive: Boolean = true
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

## 4. Architectural Layers (MVVM Model)

The application adheres directly to standard Android MVVM architecture guidelines:

```
┌────────────────────────────────────────────────────────┐
│                      Compose UI                        │
│   Dashboard (MainActivity) ◄──► Dialog Modules / Cards │
└───────────▲────────────────────────────────▲───────────┘
            │                                │ Observe
            │ Events / Commands              │ State Flows
┌───────────▼────────────────────────────────┴───────────┐
│                    ViewModel                           │
│   Exposes: filteredMedications, familyMembers, etc.     │
└───────────▲────────────────────────────────────────────┘
            │ Handles logic and routes updates
┌───────────▼────────────────────────────────────────────┐
│                    Repository                          │
│   Coordinates Local DB access and Alarm Scheduler API  │
└───────────▲────────────────────────────────────────────┘
            │ Core Queries / Room Mutations
┌───────────▼────────────────────────────────────────────┐
│                     Local DB                           │
│   Room Database Engine ◄──► SQLite File Store          │
└────────────────────────────────────────────────────────┘
```

### Layers Description:
1.  **Data Layer (`com.example.data`)**:
    *   **Dao (`AppDao`)**: Declares Room DAO methods. Returns standard asynchronous `Flow<List<T>>` for list responses, granting the UI automatic live-updates upon database mutation.
    *   **Database (`AppDatabase`)**: Extends `RoomDatabase`. Pre-populates a default profile "Me" (with dynamic styling) on first creation if dry-launched.
    *   **Repository (`MedicationRepository`)**: Aggregates Dao instructions. Houses logic to register and unregister exact alarm system configurations when inserting or updating medications.
2.  **Scheduling/Reminder Layer (`com.example.reminder`)**:
    *   **`ReminderScheduler`**: Formulates calculations to map current time, weekly/interval constraints, and scheduled start offsets to target millisecond epochs. Communicates directly with the Android SDK’s `AlarmManager`.
    *   **`MedicationAlarmReceiver`**: Receives target alarm dispatches. Coordinates the event pipeline by routing intent deliverables directly to the `MedicationAlarmService` (Foreground Service) for continuous execution.
    *   **`MedicationAlarmService`**: Android Foreground Service which instantiates a persistent background loop. Leverages standard Android background constraints by executing a foreground worker containing a `MediaPlayer` managing an active alarm ringtone stream. It utilizes a `NotificationChannel` configured with `IMPORTANCE_HIGH` that encapsulates a `fullScreenIntent` pointing directly to the wake-screen component.
    *   **`AlarmActivity`**: Full-screen overlay activity configured with window bypass parameters like `setShowWhenLocked(true)` and `setTurnScreenOn(true)`. Leveraging the `USE_FULL_SCREEN_INTENT` permission, this activity directly bypasses keyguards on standby states to present a prominent high-contrast interactive patient HUD.
    *   **`BootReceiver`**: Automatically invoked when of Android's `ACTION_BOOT_COMPLETED` triggers. Re-initiates database-driven background registrations to preserve alarm survival over phone shutdowns.
3.  **UI & State Layer (`com.example.viewmodel` / `com.example.ui`)**:
    *   **`MedicationViewModel`**: Binds queries together. Exposes safe state flows leveraging reactive transformations. Listens directly to query searches and profile navigation selections.

---

## 5. Alarm Scheduling Calculations & Precision

To optimize battery life while maintaining reliable trigger precision, MedRed implements exact scheduling offsets:

### A. Weekly Schedule Method
Target hours and minutes are calculated based on user input. The application calculates the closest upcoming calendar day matching the selected active days list (e.g. `Mon`, `Wed`, `Fri`) starting from the baseline time:
1. Parse the customized HH:mm string (e.g. `"08:30"` -> Hour `8`, Minute `30`).
2. Construct a calendar baseline. If the scheduled time on a target day has passed for today, the scheduler advances to the next week.
3. Compute matching calendar offsets and schedule individual intent callbacks using `AlarmManager.setExactAndAllowWhileIdle`.

### B. Interval Schedule Method
For periodic medications (e.g., *"Take every 8 hours"*):
1. Compute the delta between current system epoch time and the baseline start epoch.
2. Modulate the difference by the interval duration sequence.
3. Set the target notification trigger index at the exact next upcoming modulo threshold.

### C. OS-Level Intent Payload Architecture
Each alarm is assigned a unique Request Code mapped directly to its database primary key. The `Intent` broadcast stores the target ID, medication name, instructions, and family member tags to reconstruct warning labels on the system heads-up notification prompt:

```kotlin
val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
    putExtra("medKey", medication.id)
    putExtra("medName", medication.name)
    putExtra("medDosage", medication.dosage)
    putExtra("instructions", medication.instructions)
    putExtra("memberName", memberName)
}
val pendingIntent = PendingIntent.getBroadcast(
    context,
    medication.id.toInt(),
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

### D. System-Level Interruption Protocol & Bypassing Standby
To achieve fail-safe alarms even when the device is asleep or locked:
1.  **Exact Timers (Doze-Mode Resist)**: Leverages `AlarmManager.setExactAndAllowWhileIdle()` to guarantee execution across Doze mode transitions.
2.  **Foreground Service Continuity**: The broadcast delegate starts `MedicationAlarmService` in the foreground. By utilizing `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions, the service executes persistent, high-volume feedback streams.
3.  **Tiered Cascading Audio Fallbacks**: The `MediaPlayer` handles failure-resilient playback by testing a prioritized roster of system-level sound URIs: `TYPE_ALARM` is prioritized, falling back automatically to `TYPE_RINGTONE` (incoming call ringing), and scaling down to `TYPE_NOTIFICATION` should system paths throw IOExceptions. It handles file decoding loop boundaries using `isLooping = true` coupled with an asynchronous auto-replay `OnCompletionListener` trigger.
4.  **Looping Vibration Engine**: Synchronized seamlessly with active audio playback. Employs a custom asynchronous vibration pattern generator via device haptic motors (`VIBRATOR_SERVICE`) that safely stops and tears down when action flags (Take, Skip, or Mute) are dispatched.
5.  **Lockscreen Interrupt Overhead**: Uses the `USE_FULL_SCREEN_INTENT` permission, initializing a high-importance `NotificationChannel` with `fullScreenIntent` parameters mapping directly to `AlarmActivity`. On waking, standard window flags (`FLAG_SHOW_WHEN_LOCKED` / `FLAG_TURN_SCREEN_ON`) pull the activity into view above the lock screen.

---

## 6. UI & UX Layout Polish (Material Design 3)

The user interface utilizes custom Material 3 components aligned on an asymmetric layout designed with elegant spacing:

*   **Adaptive Header Workspace**: Features a stylized status brand logo incorporating clinic cross geometries, coupled with a distinct "Add Profile" action key.
*   **Profile Tag Ribbon**: High-contrast animated horizontal chips allowing quick profile traversal. Each family profile is represented by a specific custom tag color mapping to visual borders out of the box.
*   **Search Box Navigation**: Real-time debounce filters mapped to searching lists, isolating notes and dosage constraints instantly.
*   **Medication Cards Layout**: Uses spacious layouts featuring:
    *   Automatic opacity dims for disabled (switched off) active states.
    *   Dynamic border colors derived from the active family member's chosen theme.
    *   High-contrast, dual-colored command elements (Primary "Log Taken" button paired with Outlined Secondary "Log Skipped" control).
*   **Logs Trace Timeline**: A simplified ledger monitoring recent historical actions. Uses visual indicators for success logs (primary containers mapping a check icon) or warnings (neutral bounds mapping blockage alerts).

---

## 7. Accessibility & Validation Tags

To support automatic instrumented test automation and comply with Google Play Accessibility expectations, all interactive components are fully configured:
*   **Touch Targets**: Minimum interactive layout size is constrained to `48.dp x 48.dp` utilizing surrounding padding configurations.
*   **Testing Tags**:
    *   Search Bar: `Modifier.testTag("medicine_search")`
    *   Floating Action Button: `Modifier.testTag("add_medication_fab")`
    *   Save Reminders Option: `Modifier.testTag("save_medication_button")`
    *   Save Profiles Option: `Modifier.testTag("save_profile_button")`
    *   Specific Profile Tag Chips: `Modifier.testTag("profile_pill_grandma")`, `Modifier.testTag("profile_pill_all")`

---

## 8. Development Verification Tasks
To verify complete compilation and execute Robolectric unit/screenshot regressions locally, engineers can utilize Gradle commands:

```bash
# 1. Compile the complete application
gradle assembleDebug

# 2. Run target Robolectric and business logic tests
gradle :app:testDebugUnitTest

# 3. Clean project build files (use only when necessary)
gradle clean
```
