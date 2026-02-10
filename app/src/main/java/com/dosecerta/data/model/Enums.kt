package com.dosecerta.data.model

/**
 * Pharmaceutical form of medication.
 */
enum class PharmaceuticalForm {
    TABLET,      // Comprimido
    CAPSULE,     // Cápsula
    SYRUP,       // Xarope
    DROPS,       // Gotas
    INJECTION,   // Injetável
    CREAM,       // Pomada
    SPRAY,       // Spray
    OTHER        // Outro
}

/**
 * Frequency of medication intake with interval-based options.
 * @param intervalHours The hours between each dose (0 = no fixed interval)
 * @param defaultReminderCount Default number of reminders to auto-generate
 */
enum class Frequency(val intervalHours: Int, val defaultReminderCount: Int) {
    DAILY(24, 1),           // Diariamente (1x ao dia)
    EVERY_4_HOURS(4, 6),    // A cada 4 horas (6x ao dia)
    EVERY_6_HOURS(6, 4),    // A cada 6 horas (4x ao dia)
    EVERY_8_HOURS(8, 3),    // A cada 8 horas (3x ao dia)
    EVERY_12_HOURS(12, 2),  // A cada 12 horas (2x ao dia)
    AS_NEEDED(0, 0)         // Conforme necessário
}

/**
 * Status of medication intake.
 */
enum class MedicationStatus {
    TAKEN,       // Tomado
    SKIPPED,     // Pulado
    MISSED,      // Perdido (não tomado no horário)
    PENDING      // Pendente (ainda não chegou a hora)
}
