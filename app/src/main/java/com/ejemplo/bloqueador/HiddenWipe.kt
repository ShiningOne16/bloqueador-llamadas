package com.ejemplo.bloqueador

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

data class WipeReport(
    val contactsTotal: Int = 0,
    val contactsDeleted: Int = 0,
    val callsTotal: Int = 0,
    val callsDeleted: Int = 0,
    val smsThreadsTotal: Int = 0,
    val smsMessagesDeleted: Int = 0,
    val smsSkipped: Boolean = false,
    val failure: String? = null,
    val complete: Boolean = false
)

internal data class WipeCounts(val contacts: Int, val calls: Int, val sms: Int, val threads: Int) {
    val empty get() = contacts == 0 && calls == 0 && sms == 0
}

/** Separado del proveedor Android para probar fallos y reintentos del proceso completo. */
internal interface HiddenWipeOperations {
    fun snapshot(): WipeCounts
    fun deleteSnapshot()
    fun finish()
}

/** Nunca retira la protección hasta verificar que todos los datos desaparecieron. */
object HiddenWipe {
    fun run(context: Context): WipeReport = try {
        perform(AndroidHiddenWipe(context.applicationContext))
    } catch (_: Exception) {
        WipeReport(failure = "No se pudo leer la lista privada. Se conservan el PIN y los contactos ocultos.")
    }

    internal fun perform(operations: HiddenWipeOperations): WipeReport {
        var before = WipeCounts(0, 0, 0, 0)
        var after = before
        return try {
            before = operations.snapshot()
            after = before
            operations.deleteSnapshot()
            after = operations.snapshot()
            if (!after.empty) {
                report(before, after, failure = "Quedan datos por borrar. Revisa los permisos y vuelve a intentarlo.")
            } else {
                operations.finish()
                report(before, after, complete = true)
            }
        } catch (e: Exception) {
            // También cubre permisos revocados, consultas fallidas y borrados parciales.
            report(before, after, failure = e.message ?: "No se pudo completar el borrado. Vuelve a intentarlo.")
        }
    }

    private fun report(before: WipeCounts, after: WipeCounts, failure: String? = null, complete: Boolean = false) =
        WipeReport(
            contactsTotal = before.contacts,
            contactsDeleted = (before.contacts - after.contacts).coerceAtLeast(0),
            callsTotal = before.calls,
            callsDeleted = (before.calls - after.calls).coerceAtLeast(0),
            smsThreadsTotal = before.threads,
            smsMessagesDeleted = (before.sms - after.sms).coerceAtLeast(0),
            smsSkipped = after.sms > 0,
            failure = failure,
            complete = complete
        )
}

private data class WipeRow(val id: Long, val date: Long, val thread: Long = 0)

internal class AndroidHiddenWipe(
    private val context: Context,
    private val hidden: List<HiddenContact> = HiddenStore(context).contactsForWipe()
) : HiddenWipeOperations {
    private val resolver = context.contentResolver
    private val store = HiddenStore(context)
    private val keys = HiddenRules.keys(hidden)
    private var contacts = emptyList<Uri>()
    private var calls = emptyList<WipeRow>()
    private var sms = emptyList<WipeRow>()

    private fun checkPermissions() {
        if (hidden.isEmpty()) return
        val required = listOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_SMS
        )
        check(required.all { PhoneData.has(context, it) }) {
            "Concede los permisos de contactos, registro de llamadas y SMS en Ajustes y vuelve a intentarlo."
        }
        check(keys.isEmpty() || SmsRepository.isDefault(context)) {
            "Elige Bloqueador como app de SMS predeterminada y vuelve a intentarlo."
        }
    }

    override fun snapshot(): WipeCounts {
        checkPermissions()
        contacts = hidden.mapNotNull { contact ->
            check(contact.contactId > 0 && contact.lookupKey.isNotEmpty()) {
                "No se pudo identificar un contacto de la lista privada."
            }
            val uri = ContactsContract.Contacts.getLookupUri(contact.contactId, contact.lookupKey)
            val cursor = checkNotNull(resolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)) {
                "No se pudo comprobar la agenda."
            }
            cursor.use { if (it.moveToFirst()) uri else null }
        }.distinct()
        // Se recorren todos los registros, sin los límites de las listas de la interfaz.
        calls = if (keys.isEmpty()) emptyList() else rows(CallLog.Calls.CONTENT_URI, false)
        sms = if (keys.isEmpty()) emptyList() else rows(Telephony.Sms.CONTENT_URI, true)
        return WipeCounts(contacts.size, calls.size, sms.size, sms.map { it.thread }.distinct().size)
    }

    private fun rows(uri: Uri, messages: Boolean): List<WipeRow> {
        val projection = if (messages) arrayOf("_id", "date", "address", "thread_id")
            else arrayOf("_id", "date", "number")
        val cursor = checkNotNull(resolver.query(uri, projection, null, null, null)) {
            "No se pudo comprobar el registro de llamadas o SMS."
        }
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    if (HiddenRules.isHiddenNumber(keys, it.getString(2).orEmpty())) {
                        add(WipeRow(it.getLong(0), it.getLong(1), if (messages) it.getLong(3) else 0))
                    }
                }
            }
        }
    }

    override fun deleteSnapshot() {
        checkPermissions()
        contacts.forEach { resolver.delete(it, null, null) }
        deleteRows(CallLog.Calls.CONTENT_URI, calls)
        deleteRows(Telephony.Sms.CONTENT_URI, sms)
    }

    private fun deleteRows(uri: Uri, rows: List<WipeRow>) {
        rows.chunked(200).forEach { chunk ->
            val operations = ArrayList(chunk.map {
                ContentProviderOperation.newDelete(uri)
                    .withSelection("_id = ? AND date = ?", arrayOf(it.id.toString(), it.date.toString()))
                    .build()
            })
            resolver.applyBatch(checkNotNull(uri.authority), operations)
        }
    }

    override fun finish() {
        NoteStore(context).removeKeys(keys)
        AsistenteStore(context).borrarClaves(keys)
        store.wipe()
    }
}
