# Architectural Layer Specification: MedRem

This document outlines the system architectural design, package layers, and the component interaction flow of the MedRem application. MedRem follows a clean, modern Android **MVVM (Model-View-ViewModel)** pattern coupled with a unidirectional data flow (UDF).

---

## 1. Architectural Diagram

The interaction flow across the key components behaves according to standard Android architecture recommendations:

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

---

## 2. Layers Breakdown

### A. Data Layer (`com.example.data`)
The data layer forms the base of the application's offline-first capabilities.

1. **Entity Models (`com.example.data.entity`)**:
   * **`FamilyMember`**: Holds user profile details. Includes names and distinct aesthetic hex theme keys.
   * **`Medication`**: Holds scheduled medication reminders linked to a family member via custom foreign key relationships.
   * **`DoseRecord`**: Stores the absolute ingestion logs (`TAKEN`, `SKIPPED`, `MISSED`) of recorded doses.
2. **DataAccess Object (`com.example.data.dao.AppDao`)**:
   * Declares Room query mechanisms using reactive streams (`Flow<List<T>>`). Room handles dynamic query updates, propagating new logs or reminders automatically to observers.
3. **Database (`com.example.data.database.AppDatabase`)**:
   * Uses a custom SQLite initialization helper. On first creation, it automatically executes migration steps and pre-populates a default profile "Me" ensuring clean default state on startup.
4. **Repository (`com.example.data.repository.MedicationRepository`)**:
   * Serves as the single source of truth for database access. Handles transaction coordination and abstracts SQLite queries into simple high-level suspend methods for the ViewModel and background components.

### B. Scheduling & Reminder Layer (`com.example.reminder`)
Designed to deliver strict, wake-on-time medication alert loops.

1. **`ReminderScheduler`**:
   * Translates active user medication settings into future Unix timestamps in milliseconds. Uses a precise calculation engine to map active days or interval margins, registering exact wake clocks via the native Android `AlarmManager` SDK.
2. **`MedicationAlarmReceiver`**:
   * A low-overhead `BroadcastReceiver` that captures intent actions dispatched by the operating system when a reminder's schedule time triggers. Launches the high-importance alert handler service immediately.
3. **`MedicationAlarmService`**:
   * A premium Foreground Service managing critical system-feedback states. It runs a looping `MediaPlayer` utilizing system ringtone/alarm audio resources, controls device haptic motors via the Android vibrating service, and handles foreground notifications with elevated lock-screen priority.
4. **`AlarmActivity`**:
   * A resilient patient intake interface designed to bypass lockscreens using window parameters (`setShowWhenLocked`, `setTurnScreenOn`). Provides the patient with prominent, direct click interactions to mark a reminder as **Taken** or **Snoozed** (30 mins snooze window).
5. **`BootReceiver`**:
   * Automatically re-registers all saved medication reminders with `AlarmManager` on phone system boot events (`ACTION_BOOT_COMPLETED`), protecting schedule continuity.

### C. UI & State Layer (`com.example.viewmodel` / `com.example.ui`)
Translates database objects to rich, responsive Compose elements.
1. **`MedicationViewModel`**:
   * Coordinates input actions and system statuses. Filters medication lists of active schedules and logs, handles real-time search queries across profiles and medicine properties, and exposes reactive Material theme schemas for Jetpack Compose observers.
2. **MainActivity & Compose Views**:
   * Render visually delightful screen structures. Includes customized add-medicine workflows, animated profile pill selection tracks, and real-time history logs with responsive scrolling.

---

## 3. Resource Customization Guide (Icon, Logo, and App Name)

To brand or customize the visual identity of MedRem, modifications must be applied across multiple resource points. Below is the precise layout map detailing where and how to swap these system assets.

### A. How to Change the Application Name

The application name is configured at both the platform, manifest, resource, and UI rendering layers:

1. **Android Resource Name (`strings.xml`)**:
   * **Path**: `/app/src/main/res/values/strings.xml`
   * **Action**: Change the value of the `app_name` string:
     ```xml
     <resources>
         <string name="app_name">MyCustomAppName</string>
     </resources>
     ```
2. **Platform Sidebar Naming (`metadata.json`)**:
   * **Path**: `/metadata.json`
   * **Action**: To maintain platform synchronization with AI Studio and avoid mismatches, alter the `"name"` field:
     ```json
     {
       "name": "MyCustomAppName: Brand Descriptor",
       ...
     }
     ```
3. **Jetpack Compose UI Text Title (`MainActivity.kt`)**:
   * **Path**: `/app/src/main/java/com/example/MainActivity.kt`
   * **Action**: Change the text literal rendered in the adaptive UI header around line 160:
     ```kotlin
     Text(
         text = "MyCustomAppName",
         fontSize = 24.sp,
         fontWeight = FontWeight.Bold,
         ...
     )
     ```

---

### B. How to Change the App Logos

MedRem uses two forms of logos: a designed physical launcher icon and an inline dynamic brand header vector logo.

1. **The Modern App Launcher Logo (Adaptive Icon)**:
   * **Path for foreground outline**: `/app/src/main/res/drawable/ic_launcher_foreground.xml`
     * By default, this uses a Layer-list that centers the high-contrast branding PNG symbol `@drawable/m4`.
     * To change this, replace `/app/src/main/res/drawable/m4.png` with your new 512x512 logo asset (maintaining name `m4.png`), or change `android:drawable="@drawable/YOUR_NEW_PNG"` inside the foreground vector layer.
   * **Path for background canvas**: `/app/src/main/res/drawable/ic_launcher_background.xml`
     * Contains standard background vector curves or custom brand gradient shaders.
     * Edit the solid color code or the linear gradient hex numbers to align with your new brand color scheme palette.

2. **The App Dashboard Brand Logo (Compose Core)**:
   * **Path**: `/app/src/main/java/com/example/MainActivity.kt` (Inside the Header row)
   * **Action**: Look for the `Box` container wrapped with a background color around lines 145-157:
     ```kotlin
     Box(
         modifier = Modifier
             .size(36.dp)
             .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
         contentAlignment = Alignment.Center
     ) {
         Icon(
             imageVector = Icons.Default.MedicalServices, // <-- Swap this Vector for a new branding symbol
             contentDescription = "Clinic Logo",
             tint = Color.White,
             modifier = Modifier.size(20.dp)
         )
     }
     ```
     You can substitute `Icons.Default.MedicalServices` with another vector identifier (e.g., `Icons.Default.Favorite` for a heart-based emblem or `Icons.Default.Healing`).

---

### C. How to Change Every Icon in the Application

Icons are used across system notifications, active status overlays, navigation controls, and general status tags.

1. **The Notification Bar / System Tray Alert Icon**:
   * **Path**: `/app/src/main/java/com/example/reminder/MedicationAlarmService.kt` and `MedicationAlarmReceiver.kt`
   * **Action**: When constructing foreground alerts, the system uses Android default drawables:
     ```kotlin
     .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
     ```
     To substitute this with a custom vector, import your vector XML asset (for example, `ic_alert_custom.xml`) into `/app/src/main/res/drawable/` and swap the call:
     ```kotlin
     .setSmallIcon(R.drawable.ic_alert_custom)
     ```

2. **The Navigation Tab Panel Icons (Compose Navigation Bar)**:
   * **Path**: `/app/src/main/java/com/example/MainActivity.kt` (Inside `NavigationBar`)
   * **Action**: Locate the navigation item declarations mapping tabs (e.g., around lines 290-310). Change the `icon` argument vectors:
     ```kotlin
     // Tab 0 (Schedules list):
     icon = { Icon(Icons.Default.Vaccines, contentDescription = "Schedules List") } // Swap Vaccines
     
     // Tab 1 (Logs history):
     icon = { Icon(Icons.Default.History, contentDescription = "Logs History") } // Swap History
     ```

3. **General Action Button Icons (Check, Delete, Snooze, Clear, Search)**:
   * **Path**: `/app/src/main/java/com/example/MainActivity.kt`
   * **Action**: Change standard Material Symbols instantiated in Compose blocks:
     * **Add Profile**: `Icons.Default.PersonAdd`
     * **Import/Export Data**: `Icons.Default.ImportExport`
     * **Confirm Check Action**: `Icons.Default.Check`
     * **Dismiss / Delete**: `Icons.Default.Delete` or `Icons.Default.Close`
     * **Search**: `Icons.Default.Search`
     * **Active Alarm Banner Action Triggers**:
       * Taken Action: `Icons.Default.Check`
       * Snooze Action: `Icons.Default.Alarm`
       * Skip Action: `Icons.Default.Block`
     * To change any of these, simply reference another Material icon vector from the `androidx.compose.material.icons.Icons` packages.

