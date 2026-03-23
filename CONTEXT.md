# DoseCerta — Agent Context

> Last updated: 2026-03-23  
> Purpose: Comprehensive orientation document for coding agents. Read this before touching any file.

---

## 1. What is DoseCerta?

**DoseCerta** is an Android (Kotlin) medication-reminder app. It is a **100% offline, privacy-first** application — all data is stored locally in a Room SQLite database. No backend, no network calls, no analytics. It is aimed at Brazilian users (primary locale: `pt-BR`), with English locale support (`values-en/`).

The app helps users:
- Register medications with dosage, form, and frequency
- Set reminders/alarms (exact alarms via `AlarmManager`)
- Log when they took, skipped, or missed a dose
- Review their medication history with statistics

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (JVM 17) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |
| UI | XML layouts + ViewBinding, Material Design 3 |
| Architecture | MVVM (Fragment → ViewModel → Repository → DAO) |
| Database | Room 2.6.1 |
| Async | Kotlin Coroutines + Flow (StateFlow) |
| Navigation | Jetpack Navigation Component (single-activity) |
| Alarms | `AlarmManager` + `BroadcastReceiver` + `ForegroundService` |
| DI | Manual (factory classes, no Hilt/Dagger) |
| Preferences | DataStore (`SettingsPreferences`) |
| Build | Gradle Kotlin DSL, KSP for Room |

---

## 3. Project Structure

```
app/src/main/java/com/dosecerta/
├── DoseCertaApplication.kt          # Application class
├── alarm/
│   ├── AlarmActivity.kt             # Full-screen alarm UI
│   ├── AlarmScheduler.kt            # Scheduling helper
│   ├── AlarmService.kt              # Foreground service for alarm sound
│   ├── AlarmSoundManager.kt         # Sound/vibration control
│   ├── BootCompletedReceiver.kt     # Re-schedules alarms after reboot
│   └── MedicationAlarmReceiver.kt   # BroadcastReceiver for alarm trigger
├── data/
│   ├── local/
│   │   ├── DoseCertaDatabase.kt     # Room database singleton (v1)
│   │   ├── dao/
│   │   │   ├── MedicationDao.kt
│   │   │   ├── MedicationLogDao.kt  # ⚠️ Recently modified — see §6
│   │   │   └── ScheduleDao.kt
│   │   └── entity/
│   │       ├── Medication.kt
│   │       ├── MedicationLog.kt
│   │       └── Schedule.kt
│   ├── model/
│   │   ├── Enums.kt                 # PharmaceuticalForm, Frequency, MedicationStatus
│   │   ├── MedicationLogWithDetails.kt  # Joined query result
│   │   └── Models.kt
│   └── repository/
│       └── MedicationRepository.kt  # ⚠️ Recently modified — see §6
├── notification/
│   ├── MarkMissedReceiver.kt
│   ├── MissedReminderReceiver.kt
│   ├── NotificationActionReceiver.kt
│   └── NotificationHelper.kt
└── ui/
    ├── MainActivity.kt              # Single Activity, NavHost
    ├── addmedication/
    │   ├── AddMedicationFragment.kt
    │   ├── AddMedicationViewModel.kt
    │   └── ScheduleTimeAdapter.kt
    ├── history/
    │   ├── HistoryFragment.kt       # ⚠️ Recently modified — see §6
    │   ├── HistoryViewModel.kt      # ⚠️ Recently modified — see §6
    │   └── MedicationLogAdapter.kt
    ├── home/
    │   ├── HomeFragment.kt
    │   ├── HomeViewModel.kt
    │   ├── ScheduleAdapter.kt
    │   └── ExtraDoseMedicationAdapter.kt
    ├── medications/
    │   ├── MedicationsFragment.kt
    │   ├── MedicationsViewModel.kt
    │   └── MedicationAdapter.kt
    ├── privacy/
    │   └── PrivacyPolicyFragment.kt
    ├── settings/
    │   └── SettingsFragment.kt
    └── setup/
        ├── SetupActivity.kt         # First-run onboarding flow
        ├── SetupNotificationsFragment.kt
        ├── SetupTermsFragment.kt
        └── SetupTutorialFragment.kt
```

---

## 4. Database Schema

### `medications`
| Column | Type | Notes |
|---|---|---|
| `id` | Long PK | auto-generated |
| `name` | String | e.g. "Paracetamol" |
| `dosage` | String | e.g. "500" |
| `unit` | String | e.g. "mg", "ml" |
| `pharmaceuticalForm` | Enum | TABLET, CAPSULE, SYRUP, DROPS, INJECTION, CREAM, SPRAY, OTHER |
| `frequency` | Enum | DAILY, EVERY\_4\_HOURS, EVERY\_6\_HOURS, EVERY\_8\_HOURS, EVERY\_12\_HOURS, AS\_NEEDED |
| `notes` | String? | optional |
| `color` | Int | ARGB color int for icon |
| `isActive` | Boolean | soft-delete flag |
| `createdAt` | Long | epoch ms |

### `schedules`
| Column | Type | Notes |
|---|---|---|
| `id` | Long PK | auto-generated |
| `medicationId` | Long FK | → medications.id (CASCADE delete) |
| `timeInMinutes` | Int | minutes since midnight (0–1439) |
| `daysOfWeek` | List\<Int\> | 1=Sun … 7=Sat; empty = every day |
| `isActive` | Boolean | |

### `medication_logs`
| Column | Type | Notes |
|---|---|---|
| `id` | Long PK | auto-generated |
| `medicationId` | Long? FK | nullable = custom/ad-hoc dose |
| `scheduleId` | Long? FK | nullable = extra dose |
| `scheduledTime` | Long | epoch ms — indexed |
| `actualTime` | Long? | epoch ms when actually taken |
| `status` | Enum | TAKEN, SKIPPED, MISSED, PENDING |
| `notes` | String? | optional |
| `isExtraDose` | Boolean | true = unscheduled extra dose |
| `customMedicationName` | String? | for ad-hoc meds not in DB |

### Joined Query: `MedicationLogWithDetails`
Used by the History screen. Combines `medication_logs` + `medications`:
```kotlin
data class MedicationLogWithDetails(
    @Embedded val log: MedicationLog,
    val medicationName: String?,
    val dosage: String?,
    val unit: String?,
    val color: Int?
)
```

---

## 5. Repository Pattern

`MedicationRepository` is the single source of truth for UI layers. It wraps the three DAOs and is instantiated manually via `HistoryViewModelFactory` (no Hilt). It exposes Flow-based reactive queries and suspend functions for mutations.

Key pattern used across the app:
```
Fragment → ViewModelFactory → ViewModel → Repository → DAO → Room → Flow → StateFlow → UI
```

---

## 6. Recent Changes (2026-03-23)

### Feature: "Todo o Período" — All-Time History Filter

**Files modified:**

#### `MedicationLogDao.kt`
Added two new unbounded queries (no `startTime`/`endTime`):
```kotlin
fun getAllLogsByStatusWithDetails(status: MedicationStatus): Flow<List<MedicationLogWithDetails>>
// Note: getAllLogsWithDetails() already existed — returns ALL logs with no time filter
```

#### `MedicationRepository.kt`
Exposed the new `getAllLogsByStatusWithDetails()` method.

#### `HistoryViewModel.kt`
- `_daysBack` changed from `MutableStateFlow<Int>` → `MutableStateFlow<Int?>` where **`null` = all time**
- Added `cyclePeriod()` replacing the old `updateDaysBack()`:
  ```kotlin
  fun cyclePeriod() {
      _daysBack.value = when (_daysBack.value) {
          7 -> 30
          30 -> null   // all time
          else -> 7
      }
  }
  ```
- `logs` flow branches: when `days == null`, calls the no-bounds repository methods

#### `HistoryFragment.kt`
- `setupDateRange()` now calls `viewModel.cyclePeriod()` on tap
- Observer maps `daysBack` int? to string resource:
  - `7` → `R.string.history_last_7_days`
  - `30` → `R.string.history_last_30_days`
  - `null` → `R.string.history_all_period`

#### String Resources
- `values/strings.xml` (pt-BR): added `<string name="history_all_period">Todo o Período</string>`
- `values-en/strings.xml` (en): added `<string name="history_all_period">All Time</string>`

---

## 7. Locale Strategy

- **Default locale** (`values/strings.xml`): Brazilian Portuguese (pt-BR)
- **English override** (`values-en/strings.xml`): only strings that differ from pt-BR are needed; Room uses default if key is missing in the locale file
- Always add new strings to BOTH files

---

## 8. Alarm Architecture

- `AlarmScheduler` uses `AlarmManager.setExactAndAllowWhileIdle()`
- `MedicationAlarmReceiver` receives the broadcast and starts `AlarmService` (ForegroundService)
- `AlarmActivity` is shown as a full-screen locked-screen UI
- `BootCompletedReceiver` re-schedules all active alarms on device restart
- Notification actions (Take / Skip / Snooze) are handled by `NotificationActionReceiver`

---

## 9. First-Run Setup Flow

`SetupActivity` (separate from `MainActivity`) shows a 3-screen onboarding:
1. **Terms** (`SetupTermsFragment`) — user must accept privacy policy
2. **Notifications** (`SetupNotificationsFragment`) — request notification permission
3. **Tutorial** (`SetupTutorialFragment`) — 3-step swipeable carousel

After completing setup, `SettingsPreferences` marks setup as done and redirects to `MainActivity`.

---

## 10. Feature Backlog

The following features have been requested. They are **not yet implemented** unless marked as ✅.

---

### ✅ Feature 1 — Histórico: Filtro "Todo o Período"
**Status:** Implemented (2026-03-23) — awaiting build verification  
**Screen:** History (`HistoryFragment`)  
**Description:**  
Added a third date-range option to the History screen. The date label is now a 3-way tap toggle cycling through:
- **Últimos 7 dias** — logs from the last 7 days (default)
- **Últimos 30 dias** — logs from the last 30 days
- **Todo o Período** — full history since first use, no date restriction

Statistics cards (Tomado / Perdido / Pulado) and status filter chips all update reactively for each mode.

---

### 🕐 Feature 2 — Histórico: Relatório PDF
**Status:** Not started  
**Screen:** History (`HistoryFragment`)  
**Description:**  
Add a **"Gerar Relatório PDF"** button on the History screen that generates and downloads a medical-grade PDF report to the device's `Downloads` folder. The report is designed to be presented to a doctor and should be:

**Report structure:**
1. **Header** — App name, patient device fingerprint (optional), generation date/time, selected period
2. **Summary section** (one page)
   - Total doses scheduled, taken, missed, skipped
   - Adherence percentage per medication
   - List of all active medications with name, dosage, unit, form, frequency
3. **Day-by-day detail section**
   - Grouped by date (most recent first)
   - Each entry: medication name, dosage+unit, scheduled time, actual time (if taken), status badge, any notes
   - Extra doses clearly flagged
   - Custom/ad-hoc medications shown with their custom name

**Implementation notes:**
- Use Android's built-in `PdfDocument` API (no 3rd party lib needed, respects privacy)
- Request `WRITE_EXTERNAL_STORAGE` only if SDK < 29; use `MediaStore` API for SDK ≥ 29
- Show a `Snackbar` with "Abrir" action after download completes
- Generate using a `CoroutineScope` launched from the ViewModel (not on main thread)
- Report should cover the currently selected period (7 days / 30 days / All Time) and active status filter
- Design: white background, DoseCerta teal color accent (`#00897B`), clean typography matching app style

**Files to create/modify:**
- `ui/history/PdfReportGenerator.kt` — new class, takes `List<MedicationLogWithDetails>` + metadata
- `HistoryFragment.kt` — add FAB or action button, trigger generation, show Snackbar
- `HistoryViewModel.kt` — expose `generateReport()` suspend fun
- `fragment_history.xml` — add button/FAB
- `AndroidManifest.xml` — add `WRITE_EXTERNAL_STORAGE` permission (legacy)
- Both `strings.xml` files — add PDF-related strings

---
