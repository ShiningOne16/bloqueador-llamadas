package com.ejemplo.bloqueador

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Un contacto oculto: se bloquean sus llamadas y SMS y no se muestra en ninguna lista de la app. */
data class HiddenContact(
    val name: String,
    val numbers: List<String>,
    /** Datos para poder borrar el contacto de la agenda si se restablece el PIN. */
    val contactId: Long = -1L,
    val lookupKey: String = ""
)

/** Intentos fallidos y hasta cuándo está bloqueado el PIN. */
data class PinLock(val failures: Int = 0, val lockedUntil: Long = 0L)

/** Sin dependencias de Android: se prueba con valores concretos. */
object HiddenRules {
    const val PIN_LENGTH = 4
    const val FREE_ATTEMPTS = 5
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 15 * 60_000L

    fun validPin(pin: String) = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun iterations() = ITERATIONS

    fun hashPin(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPin(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean {
        if (!validPin(pin)) return false
        // Comparación en tiempo constante.
        return MessageDigest.isEqual(hashPin(pin, salt, iterations), expected)
    }

    /** Segundos que faltan para poder volver a intentar (0 si no está bloqueado). */
    fun lockedSeconds(lock: PinLock, now: Long): Long =
        if (lock.lockedUntil <= now) 0L else (lock.lockedUntil - now + 999L) / 1000L

    fun afterFailure(lock: PinLock, now: Long): PinLock {
        val failures = lock.failures + 1
        if (failures < FREE_ATTEMPTS) return PinLock(failures, 0L)
        val extra = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        val wait = (BASE_LOCK_MS shl extra).coerceAtMost(MAX_LOCK_MS)
        return PinLock(failures, now + wait)
    }

    fun attemptsLeft(lock: PinLock) = (FREE_ATTEMPTS - lock.failures).coerceAtLeast(0)

    /** Los números se comparan por sus últimos 10 dígitos, igual que en el resto de la app. */
    fun key(number: String) = PremiumRules.numberKey(number)

    fun keys(contacts: List<HiddenContact>): Set<String> =
        contacts.flatMap { c -> c.numbers.map { key(it) } }.filter { it.isNotEmpty() }.toSet()

    fun isHiddenNumber(keys: Set<String>, number: String): Boolean {
        val k = key(number)
        return k.isNotEmpty() && k in keys
    }

    /** Las apps de SMS muestran el nombre del contacto en lugar del número. */
    fun matchesSender(contacts: List<HiddenContact>, sender: String): Boolean {
        val text = sender.trim()
        if (text.isEmpty()) return false
        val keys = keys(contacts)
        val looksLikePhone = text.all { it.isDigit() || it in "+-() ." } && text.any { it.isDigit() }
        return if (looksLikePhone) isHiddenNumber(keys, text)
        else contacts.any { it.name.trim().equals(text, ignoreCase = true) }
    }

    /** Un contacto de la agenda está oculto si su id o alguno de sus números lo está. */
    fun isHiddenEntry(contacts: List<HiddenContact>, keys: Set<String>, id: Long, numbers: List<String>): Boolean =
        contacts.any { it.contactId == id && id > 0 } || numbers.any { isHiddenNumber(keys, it) }
}
