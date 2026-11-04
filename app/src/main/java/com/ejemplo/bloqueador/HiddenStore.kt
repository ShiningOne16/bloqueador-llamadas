package com.ejemplo.bloqueador

import android.Manifest
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra la lista de contactos ocultos con una llave que Android guarda en su almacén seguro. */
private object HiddenCrypto {
    private const val ALIAS = "bloqueador_hidden_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /** Devuelve null si los datos no se pueden descifrar (por ejemplo, la llave ya no existe). */
    fun decrypt(blob: String): String? = try {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    fun deleteKey() {
        try {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Exception) { /* Si no existía, no hay nada que borrar. */ }
    }
}

sealed class PinResult {
    data object Ok : PinResult()
    data class Wrong(val attemptsLeft: Int) : PinResult()
    data class Locked(val seconds: Long) : PinResult()
    data object NoPin : PinResult()
}

/**
 * Contactos ocultos (función Premium). El PIN se guarda solo como hash con sal y la lista de
 * contactos va cifrada; en disco no queda ningún número ni nombre legible.
 */
class HiddenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hidden_contacts", Context.MODE_PRIVATE)

    fun hasPin() = prefs.contains("pin_hash")

    fun setPin(pin: String) {
        require(HiddenRules.validPin(pin)) { "El PIN debe tener ${HiddenRules.PIN_LENGTH} dígitos." }
        val salt = HiddenRules.newSalt()
        val iterations = HiddenRules.iterations()
        val hash = HiddenRules.hashPin(pin, salt, iterations)
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("pin_iter", iterations)
            .remove("lock_failures").remove("lock_until")
            .commit()
    }

    private fun lock() = PinLock(prefs.getInt("lock_failures", 0), prefs.getLong("lock_until", 0L))

    private fun saveLock(lock: PinLock) {
        prefs.edit().putInt("lock_failures", lock.failures).putLong("lock_until", lock.lockedUntil).commit()
    }

    /** Segundos de espera pendientes por intentos fallidos (0 si se puede intentar). */
    fun lockedSeconds() = HiddenRules.lockedSeconds(lock(), System.currentTimeMillis())

    fun verify(pin: String): PinResult {
        val salt = prefs.getString("pin_salt", null)
        val hash = prefs.getString("pin_hash", null)
        if (salt == null || hash == null) return PinResult.NoPin
        val now = System.currentTimeMillis()
        val current = lock()
        val wait = HiddenRules.lockedSeconds(current, now)
        if (wait > 0) return PinResult.Locked(wait)

        val ok = HiddenRules.verifyPin(
            pin, Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP),
            prefs.getInt("pin_iter", HiddenRules.iterations())
        )
        if (ok) {
            saveLock(PinLock())
            return PinResult.Ok
        }
        val next = HiddenRules.afterFailure(current, now)
        saveLock(next)
        val nextWait = HiddenRules.lockedSeconds(next, now)
        return if (nextWait > 0) PinResult.Locked(nextWait) else PinResult.Wrong(HiddenRules.attemptsLeft(next))
    }

    // ---------------- Contactos ocultos ----------------

    fun contacts(): List<HiddenContact> {
        val blob = prefs.getString("data", null) ?: return emptyList()
        val raw = HiddenCrypto.decrypt(blob) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val numbers = o.getJSONArray("numbers")
                HiddenContact(
                    name = o.optString("name"),
                    numbers = (0 until numbers.length()).map { numbers.getString(it) },
                    contactId = o.optLong("id", -1L),
                    lookupKey = o.optString("lookup")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<HiddenContact>) {
        val array = JSONArray()
        list.forEach { c ->
            array.put(
                JSONObject().put("name", c.name).put("numbers", JSONArray(c.numbers))
                    .put("id", c.contactId).put("lookup", c.lookupKey)
            )
        }
        prefs.edit().putString("data", HiddenCrypto.encrypt(array.toString())).commit()
    }

    fun hide(contact: HiddenContact) {
        val current = contacts().filterNot { it.contactId > 0 && it.contactId == contact.contactId }
        write(current + contact)
    }

    fun unhide(contact: HiddenContact) {
        write(contacts().filterNot { it == contact })
    }

    /** Claves (últimos 10 dígitos) de todos los números ocultos; vacío si no hay ninguno. */
    fun hiddenKeys(): Set<String> {
        if (!prefs.contains("data")) return emptySet()
        return HiddenRules.keys(contacts())
    }

    fun isHidden(number: String): Boolean = HiddenRules.isHiddenNumber(hiddenKeys(), number)

    fun matchesSender(sender: String): Boolean {
        if (!prefs.contains("data")) return false
        return HiddenRules.matchesSender(contacts(), sender)
    }

    /** Borra el PIN, el bloqueo, la lista y la llave de cifrado. No toca la agenda ni los registros. */
    fun wipe() {
        prefs.edit().clear().commit()
        HiddenCrypto.deleteKey()
    }
}

data class WipeReport(
    val contactsTotal: Int,
    val contactsDeleted: Int,
    val callsTotal: Int,
    val callsDeleted: Int,
    val smsThreadsTotal: Int,
    val smsMessagesDeleted: Int,
    /** No se pudieron borrar los SMS: Bloqueador no es la app de SMS predeterminada. */
    val smsSkipped: Boolean
) {
    val complete get() = contactsDeleted >= contactsTotal && callsDeleted >= callsTotal && !smsSkipped
}

/** "Olvidé mi PIN": borra los contactos ocultos, sus llamadas y SMS, y deja la función sin PIN. */
object HiddenWipe {
    fun run(context: Context): WipeReport {
        val store = HiddenStore(context)
        val hidden = store.contacts()
        val keys = HiddenRules.keys(hidden)

        val deletable = hidden.filter { it.contactId > 0 && it.lookupKey.isNotEmpty() }
        var contactsDeleted = 0
        if (PhoneData.has(context, Manifest.permission.WRITE_CONTACTS)) {
            deletable.forEach { c ->
                val entry = ContactEntry(c.contactId, c.lookupKey, c.name, c.numbers, false)
                if (ContactStore.eliminar(context, entry) is ContactStore.Resultado.Ok) contactsDeleted++
            }
        }

        val calls = PhoneData.loadCalls(context, limit = 10_000, includeHidden = true)
            .filter { HiddenRules.isHiddenNumber(keys, it.number) }
        var callsDeleted = 0
        if (calls.isNotEmpty() && PhoneData.has(context, Manifest.permission.WRITE_CALL_LOG)) {
            callsDeleted = try { PhoneData.deleteCalls(context, calls) } catch (_: Exception) { 0 }
        }

        val threads = PhoneData.loadThreads(context, emptyMap(), maxRows = 20_000, includeHidden = true)
            .filter { HiddenRules.isHiddenNumber(keys, it.address) }
        var smsDeleted = 0
        var smsSkipped = false
        if (threads.isNotEmpty()) {
            if (SmsRepository.isDefault(context)) {
                smsDeleted = try {
                    SmsRepository.deleteThreads(context, threads.map { SmsDeletionTarget(it.threadId, it.last.id, it.last.date) })
                } catch (_: Exception) {
                    smsSkipped = true
                    0
                }
            } else {
                smsSkipped = true
            }
        }

        NoteStore(context).removeKeys(keys)
        store.wipe()
        return WipeReport(deletable.size, contactsDeleted, calls.size, callsDeleted, threads.size, smsDeleted, smsSkipped)
    }
}
