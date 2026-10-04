package com.dosecerta.ui.addmedication

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.RecurrenceCalculator
import com.dosecerta.domain.RecurrenceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Draft values are primitive SavedStateHandle entries, safe across process recreation. */
class AddMedicationViewModel(
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val medicationId: Long = -1L,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    val isEditMode = medicationId != -1L
    private val saveRequestId = savedState.get<String>("saveRequestId") ?: java.util.UUID.randomUUID().toString().also { savedState["saveRequestId"] = it }
    private var original: Medication? = null
    private val _medicationName = MutableStateFlow(savedState.get<String>("name") ?: "")
    val medicationName = _medicationName.asStateFlow()
    private val _dosage = MutableStateFlow(savedState.get<String>("dosage") ?: "")
    val dosage = _dosage.asStateFlow()
    private val _unit = MutableStateFlow(savedState.get<String>("unit") ?: "mg")
    val unit = _unit.asStateFlow()
    private val _form = MutableStateFlow(PharmaceuticalForm.valueOf(savedState.get<String>("form") ?: "TABLET"))
    val form = _form.asStateFlow()
    private val _frequency = MutableStateFlow(Frequency.valueOf(savedState.get<String>("frequency") ?: "DAILY"))
    val frequency = _frequency.asStateFlow()
    private val _notes = MutableStateFlow(savedState.get<String>("notes") ?: "")
    val notes = _notes.asStateFlow()
    private val _color = MutableStateFlow(savedState.get<Int>("color") ?: 0xFF00897B.toInt())
    val color = _color.asStateFlow()
    private val _days = MutableStateFlow(savedState.get<ArrayList<Int>>("days")?.toSet() ?: setOf(2))
    val days = _days.asStateFlow()
    private val _monthDay = MutableStateFlow(savedState.get<String>("monthDay") ?: "1")
    val monthDay = _monthDay.asStateFlow()
    private val _scheduleTimes = MutableStateFlow((savedState.get<ArrayList<Int>>("times") ?: arrayListOf()).mapIndexed { index, time ->
        ScheduleTime(savedState.get<ArrayList<Long>>("timeIds")?.getOrNull(index) ?: 0, time)
    })
    val scheduleTimes = _scheduleTimes.asStateFlow()
    private val _saveState = MutableStateFlow<SaveState>(if (savedState.get<Boolean>("saved") == true) SaveState.Success else SaveState.Idle)
    val saveState = _saveState.asStateFlow()
    private val _reminderWarning = MutableStateFlow(savedState.get<Boolean>("reminderWarning") ?: false)
    val reminderWarning = _reminderWarning.asStateFlow()
    private val _loaded = MutableStateFlow(!isEditMode)
    val loaded = _loaded.asStateFlow()
    private val _errors = MutableStateFlow<Set<Field>>(emptySet())
    val errors = _errors.asStateFlow()
    enum class Field { NAME, DOSAGE, UNIT, TIMES, DAYS, MONTH_DAY }

    init { if (isEditMode) loadMedication() }
    fun loadMedication() {
        _saveState.value = SaveState.Idle
        viewModelScope.launch {
            try {
                original = repository.getMedicationByIdSync(medicationId)
                val med = original ?: error("Medication unavailable")
                if (savedState.get<Boolean>("draft") != true) {
                    updateName(med.name); updateDosage(med.dosage); updateUnit(med.unit)
                    updateForm(med.pharmaceuticalForm); updateFrequency(med.frequency)
                    updateNotes(med.notes.orEmpty()); updateColor(med.color)
                    val schedules = repository.getSchedulesForMedicationSync(medicationId).filter { it.isActive }
                    setTimes(schedules.map { ScheduleTime(it.id, it.timeInMinutes) })
                    schedules.firstOrNull()?.let { updateDays(it.daysOfWeek.toSet()); updateMonthDay(it.monthDay.toString()) }
                    savedState["draft"] = false
                }
                _loaded.value = true
            } catch (e: Exception) { _saveState.value = SaveState.LoadError; }
        }
    }
    private fun draft() { savedState["draft"] = true; _errors.value = emptySet() }
    fun updateName(value: String) { _medicationName.value = value; savedState["name"] = value; draft() }
    fun updateDosage(value: String) { _dosage.value = value; savedState["dosage"] = value; draft() }
    fun updateUnit(value: String) { _unit.value = value; savedState["unit"] = value; draft() }
    fun updateForm(value: PharmaceuticalForm) { _form.value = value; savedState["form"] = value.name; draft() }
    fun updateFrequency(value: Frequency) { _frequency.value = value; savedState["frequency"] = value.name; draft() }
    fun updateNotes(value: String) { _notes.value = value; savedState["notes"] = value; draft() }
    fun updateColor(value: Int) { _color.value = value; savedState["color"] = value; draft() }
    fun updateDays(value: Set<Int>) { _days.value = value; savedState["days"] = ArrayList(value); draft() }
    fun updateMonthDay(value: String) { _monthDay.value = value; savedState["monthDay"] = value; draft() }
    private fun setTimes(value: List<ScheduleTime>) {
        _scheduleTimes.value = value.sortedBy { it.timeInMinutes }
        savedState["times"] = ArrayList(_scheduleTimes.value.map { it.timeInMinutes })
        savedState["timeIds"] = ArrayList(_scheduleTimes.value.map { it.id }); draft()
    }
    fun addScheduleTime(value: Int): Boolean {
        if (value !in 0..1439) return false
        if (_scheduleTimes.value.any { it.timeInMinutes == value }) return false
        setTimes(_scheduleTimes.value + ScheduleTime(0, value)); return true
    }
    fun editScheduleTime(originalTime: ScheduleTime, value: Int): Boolean {
        val current = _scheduleTimes.value
        val index = current.indexOf(originalTime)
        if (index == -1 || value !in 0..1439 || current.any { it != originalTime && it.timeInMinutes == value }) return false
        setTimes(current.toMutableList().apply { set(index, originalTime.copy(timeInMinutes = value)) })
        return true
    }
    fun removeScheduleTime(value: ScheduleTime) = setTimes(_scheduleTimes.value - value)
    /** Suggestions only append absent slots; a frequency change itself never removes a user's slots. */
    fun generateDefaultReminders(value: Frequency) {
        if (value.intervalHours <= 0) return
        val existing = _scheduleTimes.value
        val slots = (0 until value.defaultReminderCount).map { (480 + value.intervalHours * it * 60) % 1440 }
        setTimes(existing + slots.filter { time -> existing.none { it.timeInMinutes == time } }.map { ScheduleTime(0, it) })
    }
    private fun kind() = when (_frequency.value) {
        Frequency.AS_NEEDED -> RecurrenceKind.AS_NEEDED
        Frequency.WEEKLY -> RecurrenceKind.WEEKLY
        Frequency.MONTHLY -> RecurrenceKind.MONTHLY
        Frequency.SELECTED_DAYS -> RecurrenceKind.SELECTED_DAYS
        Frequency.DAILY -> RecurrenceKind.DAILY
        else -> RecurrenceKind.INTERVAL
    }
    fun schedules(): List<Schedule> = if (_frequency.value == Frequency.AS_NEEDED) emptyList() else _scheduleTimes.value.map {
        Schedule(id = it.id, medicationId = medicationId.coerceAtLeast(0), timeInMinutes = it.timeInMinutes,
            daysOfWeek = if (kind() in listOf(RecurrenceKind.WEEKLY, RecurrenceKind.SELECTED_DAYS)) _days.value.sorted() else emptyList(),
            recurrenceKind = kind(), monthDay = _monthDay.value.toIntOrNull() ?: 1)
    }
    fun preview() = if (validate().none { it in setOf(Field.TIMES, Field.DAYS, Field.MONTH_DAY) }) schedules().flatMap { RecurrenceCalculator().preview(it, 5) }
        .distinctBy { it.originalDueAt }.sortedBy { it.originalDueAt }.take(5) else emptyList()
    private fun validate(): Set<Field> = buildSet {
        if (_medicationName.value.isBlank()) add(Field.NAME)
        if (_dosage.value.isBlank()) add(Field.DOSAGE)
        if (_unit.value.isBlank()) add(Field.UNIT)
        if (_frequency.value != Frequency.AS_NEEDED && _scheduleTimes.value.isEmpty()) add(Field.TIMES)
        if (kind() in listOf(RecurrenceKind.WEEKLY, RecurrenceKind.SELECTED_DAYS) && _days.value.isEmpty()) add(Field.DAYS)
        if (kind() == RecurrenceKind.MONTHLY && _monthDay.value.toIntOrNull() !in 1..31) add(Field.MONTH_DAY)
    }
    fun saveMedication() {
        if (!_loaded.value || _saveState.value is SaveState.Saving || _saveState.value is SaveState.Success) return
        _errors.value = validate()
        if (_errors.value.isNotEmpty()) return
        _saveState.value = SaveState.Saving // synchronously prevents two taps launching two transactions
        viewModelScope.launch {
            try {
                val medication = (original ?: Medication(name = "", dosage = "", unit = "mg", pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY)).copy(
                    name = _medicationName.value.trim(), dosage = _dosage.value.trim(), unit = _unit.value.trim(),
                    pharmaceuticalForm = _form.value, frequency = _frequency.value, notes = _notes.value.trim(), color = _color.value)
                val saved = repository.saveMedicationWithSchedules(medication, schedules(), saveRequestId)
                // Commit succeeded; an alarm-capability failure must not invite a duplicate new save.
                original = medication.copy(id = saved.id)
                savedState["saved"] = true
                var limited = false
                saved.retiredScheduleIds.forEach { runCatching { alarmScheduler.cancelAlarm(saved.id, it) }.onFailure { limited = true } }
                val scheduled = runCatching { alarmScheduler.scheduleAlarmsForMedication(saved.id, saved.activeSchedules) }
                limited = limited || scheduled.isFailure || scheduled.getOrDefault(emptyList()).any {
                    it !is com.dosecerta.alarm.ScheduleResult.Scheduled || !it.exact
                }
                _reminderWarning.value = limited
                savedState["reminderWarning"] = limited
                _saveState.value = SaveState.Success
            } catch (e: Exception) { _saveState.value = SaveState.Error() }
        }
    }
    fun resetSaveState() { if (_saveState.value !is SaveState.Success) _saveState.value = SaveState.Idle }
    sealed class SaveState { data object Idle : SaveState(); data object Saving : SaveState(); data object Success : SaveState(); data object LoadError : SaveState(); class Error : SaveState() }
}
