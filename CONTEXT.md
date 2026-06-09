# DoseCerta — Agent Context

> Last updated: 2026-06-09  
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

### Missed Dose Flow
1. When alarm fires, `AlarmService.loadMedicationAndUpdateAlarm()` creates a MISSED log immediately and schedules a `MissedReminderReceiver` alarm (line 249–272)
2. `MarkMissedReceiver` fires 30 minutes after the alarm if no user action — creates MISSED log and schedules `MissedReminderReceiver`
3. `MissedReminderReceiver` fires N hours later (user-configurable in settings) — shows a notification
4. When user takes action (Take/Skip/Snooze), `NotificationActionReceiver` cancels the missed reminder alarm

---

## 9. First-Run Setup Flow

`SetupActivity` (separate from `MainActivity`) shows a 3-screen onboarding:
1. **Terms** (`SetupTermsFragment`) — user must accept privacy policy
2. **Notifications** (`SetupNotificationsFragment`) — request notification permission
3. **Tutorial** (`SetupTutorialFragment`) — 3-step swipeable carousel

After completing setup, `SettingsPreferences` marks setup as done and redirects to `MainActivity`.

---

## 10. Common Agent Errors & Gotchas

### ❌ Using Hilt/Dagger annotations
This project uses **manual DI**. Every ViewModel has a matching `*ViewModelFactory` class at the bottom of the Fragment file. Do NOT add `@Inject`, `@HiltViewModel`, or any DI framework annotations.

### ❌ Forgetting to add strings to BOTH locale files
Every new string resource MUST go in:
- `res/values/strings.xml` (pt-BR — the default)
- `res/values-en/strings.xml` (English)

### ❌ Using `context` directly in ViewModel
ViewModels should NOT hold `Context`. If you need context for `AlarmScheduler` or `SettingsPreferences`, pass them through the Factory constructor. The `HomeViewModelFactory` already receives `alarmScheduler` which was created with context in the Fragment.

### ❌ Using `lifecycleScope.launch {}` for DB queries that should be reactive
Use `Flow.collect {}` for data that should auto-update the UI. Use `suspend` functions only for one-shot mutations (insert/update/delete). The pattern is:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.someFlow.collect { data ->
        // update UI
    }
}
```

### ❌ Forgetting `runBlocking` is intentional in BroadcastReceivers
`MarkMissedReceiver`, `MissedReminderReceiver`, and `NotificationActionReceiver` all use `runBlocking { ... }` because `BroadcastReceiver.onReceive()` must complete synchronously. This is the correct pattern — do NOT replace with coroutine launches.

### ❌ Breaking the request code formula for PendingIntents
Alarm PendingIntents use specific request code formulas:
- Main alarm: `(medicationId * 1000 + scheduleId).toInt()`
- Missed check: `(medicationId * 1000000 + scheduleId * 1000 + 999).toInt()`
- Missed reminder: `(medicationId * 1000000 + scheduleId * 1000 + 998).toInt()`
- Notification actions: `notificationId * 10 + (1|2|3)`

Changing these formulas will break alarm cancellation — existing alarms won't be found by their PendingIntent.

### ❌ Not rescheduling alarms after save in edit mode
When editing a medication, you MUST:
1. Cancel ALL existing alarms first
2. Delete/update/insert schedules
3. Fetch fresh schedules from DB
4. Schedule NEW alarms with the fresh schedule IDs

### ❌ Using hardcoded notification IDs
`showMissedReminderNotification()` currently uses hardcoded `888888` — this means only one missed reminder notification can exist at a time. If fixing this, generate unique IDs per medication.

### ❌ Forgetting Parcelable for Medication
`Medication` is `@Parcelize` and passed via Intent extras to `AlarmActivity` and `AlarmService`. If you add fields to `Medication`, they must be `Parcelable`-compatible.

---

## 11. Key Frequencies & Their Behavior

| Frequency | `intervalHours` | `defaultReminderCount` | Behavior |
|---|---|---|---|
| DAILY | 24 | 1 | One alarm per day |
| EVERY_4_HOURS | 4 | 6 | 6 alarms spaced 4h apart |
| EVERY_6_HOURS | 6 | 4 | 4 alarms spaced 6h apart |
| EVERY_8_HOURS | 8 | 3 | 3 alarms spaced 8h apart |
| EVERY_12_HOURS | 12 | 2 | 2 alarms spaced 12h apart |
| **AS_NEEDED** | **0** | **0** | **No schedules, no alarms, user-initiated doses only** |

When `AS_NEEDED` is selected in `generateDefaultReminders()`:
- `_scheduleTimes.value = emptyList()` — all reminders are cleared
- `saveMedication()` then fails validation because it requires at least one time

---

## 12. Home Screen Architecture

The home screen (`HomeFragment`) has two main sections:

1. **Welcome Card** — Greeting, weekly progress ring, adherence %
2. **Today's Schedule** — `RecyclerView` with `ScheduleAdapter` showing medications due today

The schedule list is built in `HomeViewModel.todaySchedule` by:
1. Combining `getAllActiveSchedules()` + `getAllLogs()` via `combine()`
2. Filtering to today's day of week (`DateTimeUtils.shouldScheduleToday()`)
3. Looking up medication details via `getMedicationByIdSync()`
4. Finding the matching log for today's scheduled time
5. Determining status: log exists? → use log status. No log + time passed? → MISSED. No log + future? → PENDING.

**AS_NEEDED medications have no schedules**, so they never appear in `todaySchedule`. They need a separate display mechanism.

3. **Extra Dose Card** — Button opening a dialog to record extra/custom doses

---

## 13. Notification Channels

| Channel ID | Name | Usage |
|---|---|---|
| `medication_reminders` | Lembretes de Medicação | Standard notification reminders |
| `medication_alarm_channel` | Alarmes de Medicação | Full-screen alarm notifications (high importance, DND bypass) |

---

## 14. Feature Backlog

### ✅ Feature 1 — Histórico: Filtro "Todo o Período"
**Status:** Implemented (2026-03-23)

### ✅ Feature 2 — Histórico: Relatório PDF
**Status:** Implemented (2026-03-23)

### ✅ Feature 3 — AS_NEEDED Frequency Fix
**Status:** Implemented (2026-06-09)

### ✅ Feature 4 — Missed Dose Reminder Expansion
**Status:** Implemented (2026-06-09)

### 🔧 Feature 5 — Alarm Card Redesign (Swipe/Two-Step/Hold-Slide)
**Status:** In Progress (2026-06-09) — see PLAN.md, TASKS.md

---

## 15. Alarm Card Architecture (Feature 5)

### Current alarm flow
```
MedicationAlarmReceiver → AlarmService (ForegroundService) → AlarmActivity
```

### AlarmActivity responsibilities
- Extracts `Medication`, `medicationId`, `scheduleId`, `scheduledTime` from Intent extras
- Calls `setupWindowFlags()` for lockscreen display (see §15.1)
- Displays medication info
- Routes button actions to `handleTakeAction()`, `handleSkipAction()`, `handleSnoozeAction()`

### 15.1 Lockscreen window flags (DO NOT REMOVE)
These flags are critical and must be preserved in any refactor:
```kotlin
window.addFlags(
    FLAG_SHOW_WHEN_LOCKED or FLAG_DISMISS_KEYGUARD or
    FLAG_TURN_SCREEN_ON or FLAG_KEEP_SCREEN_ON or
    FLAG_FULLSCREEN or FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
    FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS
)
// API 27+:
setShowWhenLocked(true)
setTurnScreenOn(true)
keyguardManager.requestDismissKeyguard(...)
```

### 15.2 New interaction components (Feature 5)

| Component | Class/ID | Responsibility |
|---|---|---|
| Swipe track | `SwipeToConfirmView` | Custom View, drag to take |
| Skip state machine | `AlarmActivity` fields | 2-tap with 4s timeout |
| Snooze slider | `OnTouchListener` on `btn_alarm_snooze` | Hold to expand, drag to select minutes |

### 15.3 Snooze options
`Constants.SNOOZE_OPTIONS_MINUTES = [5, 10, 15, 30, 60]`
Label: "5 min", "10 min", "15 min", "30 min", "1h"
Default remains 10 min.

### 15.4 Key gotchas for Feature 5
- **`SwipeToConfirmView` must call `invalidate()` in every `ACTION_MOVE`** — not `postInvalidate()` — since it runs on the main thread
- **Skip timeout handler must be cancelled in `stopAlarmAndFinish()`** to avoid calling `handleSkipAction()` after the activity is already closing
- **Snooze `ACTION_DOWN` must return `true`** to prevent the touch from being consumed by `onClick` instead
- **Haptic feedback requires `View.performHapticFeedback()`** not `Vibrator` directly — works on all API levels and respects system haptic settings
- **`snoozeAlarm()` new parameter must have a default** (`= Constants.SNOOZE_DURATION_MINUTES`) so `NotificationActionReceiver` doesn't need to be updated
- **`SwipeToConfirmView` thumb hit area**: accept touch within `thumbX ± thumbRadius * 1.5` for comfortable usability — not pixel-perfect
- **Full-screen intent on Android 14**: `USE_FULL_SCREEN_INTENT` permission required in manifest; `notificationManager.canUseFullScreenIntent()` check needed before setting full-screen intent

