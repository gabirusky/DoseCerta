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
 * Frequency of medication intake.
 */
enum class Frequency {
    DAILY,       // Diariamente
    WEEKLY,      // Semanalmente
    MONTHLY,     // Mensalmente
    AS_NEEDED,   // Conforme necessário
    CUSTOM       // Personalizado (specific days)
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
