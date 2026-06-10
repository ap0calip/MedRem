# Architectural Layer Specification: MedRed

This document outlines the system architectural design, package layers, and the component interaction flow of the MedRed application. MedRed follows a clean, modern Android **MVVM (Model-View-ViewModel)** pattern coupled with a unidirectional data flow (UDF).

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
