package com.ejemplo.bloqueador

import java.time.LocalDateTime

data class ProtectionSchedule(
    val enabled: Boolean = false,
    val days: Set<Int> = (1..7).toSet(),
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60
) : java.io.Serializable {
    init {
        require(days.all { it in 1..7 })
        require(startMinute in 0..1439 && endMinute in 0..1439)
    }

    /** Los días corresponden al inicio del turno; 22:00–07:00 incluye la mañana siguiente. */
    fun contains(time: LocalDateTime): Boolean {
        if (!enabled) return true
        val today = time.dayOfWeek.value
        val minute = time.hour * 60 + time.minute
        return when {
            startMinute == endMinute -> today in days // 24 horas del día seleccionado
            startMinute < endMinute -> today in days && minute >= startMinute && minute < endMinute
            minute >= startMinute -> today in days
            minute < endMinute -> time.dayOfWeek.minus(1).value in days
            else -> false
        }
    }
}

enum class NumberAction { ALLOW, BLOCK }
data class NumberException(val number: String, val label: String = "", val action: NumberAction) : java.io.Serializable
data class ScheduledContact(val number: String, val name: String) : java.io.Serializable

data class ProtectionProfile(
    val id: String,
    val name: String,
    val blockUnknown: Boolean = true,
    val blockHidden: Boolean = true,
    val allowRepeat: Boolean = false,
    val silenceUnknownSms: Boolean = true,
    val includeBusinessSms: Boolean = false,
    val schedule: ProtectionSchedule = ProtectionSchedule(),
    val exceptions: List<NumberException> = emptyList(),
    val scheduledContacts: List<ScheduledContact> = emptyList()
) : java.io.Serializable

data class PremiumConfiguration(
    val profiles: List<ProtectionProfile> = defaultProfiles(),
    val activeProfileId: String? = null
) {
    val activeProfile: ProtectionProfile? get() = profiles.firstOrNull { it.id == activeProfileId }
}

fun defaultProfiles() = listOf(
    ProtectionProfile("work", "Trabajo", schedule = ProtectionSchedule(true, (1..5).toSet(), 9 * 60, 18 * 60)),
    ProtectionProfile("night", "Noche", schedule = ProtectionSchedule(true, (1..7).toSet(), 22 * 60, 7 * 60)),
    ProtectionProfile("vacation", "Vacaciones", allowRepeat = true, schedule = ProtectionSchedule(enabled = false))
)

enum class CallDecision { DEFAULT, ALLOW, BLOCK }

/** Sin dependencias de Android: la misma lógica se prueba con fechas y horas concretas. */
object PremiumRules {
    fun validNumber(number: String): Boolean =
        number.isNotBlank() && number.all { it in '0'..'9' || it in "+-() ." } &&
            number.count { it in '0'..'9' } in 7..15

    fun numberKey(number: String) = number.filter { it in '0'..'9' }.takeLast(10)

    fun callDecision(profile: ProtectionProfile?, number: String, hidden: Boolean, time: LocalDateTime): CallDecision {
        if (profile == null) return CallDecision.DEFAULT
        // Las excepciones son explícitas y se aplican también fuera del horario del perfil.
        if (!hidden && validNumber(number)) {
            val key = numberKey(number)
            profile.exceptions.firstOrNull { numberKey(it.number) == key }?.let {
                return if (it.action == NumberAction.ALLOW) CallDecision.ALLOW else CallDecision.BLOCK
            }
        }
        if (!profile.schedule.contains(time)) return CallDecision.ALLOW
        if (hidden) return if (profile.blockHidden) CallDecision.BLOCK else CallDecision.ALLOW
        if (validNumber(number) && profile.scheduledContacts.any { numberKey(it.number) == numberKey(number) }) {
            return CallDecision.BLOCK
        }
        return if (profile.blockUnknown) CallDecision.DEFAULT else CallDecision.ALLOW
    }
}

fun formatMinute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

fun scheduleDescription(schedule: ProtectionSchedule): String {
    if (!schedule.enabled) return "Todo el día, todos los días"
    val names = mapOf(1 to "Lun", 2 to "Mar", 3 to "Mié", 4 to "Jue", 5 to "Vie", 6 to "Sáb", 7 to "Dom")
    val days = if (schedule.days.size == 7) "Todos los días" else schedule.days.sorted().joinToString(", ") { names.getValue(it) }
    val hours = if (schedule.startMinute == schedule.endMinute) "24 horas" else
        "${formatMinute(schedule.startMinute)}–${formatMinute(schedule.endMinute)}" +
            if (schedule.startMinute > schedule.endMinute) " del día siguiente" else ""
    return "$days · $hours"
}
