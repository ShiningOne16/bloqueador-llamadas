package com.ejemplo.bloqueador

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
) {
    val isMissedLike get() = type == CallLog.Calls.MISSED_TYPE || isBlockedLike
    val isBlockedLike get() = type == CallLog.Calls.REJECTED_TYPE || type == CallLog.Calls.BLOCKED_TYPE
}

/** Varias llamadas seguidas del mismo número y tipo se muestran como una sola fila. */
data class CallGroup(val latest: CallEntry, val count: Int)

data class ContactEntry(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val numbers: List<String>,
    val starred: Boolean,
    /** Dónde está guardado: Google, SIM, teléfono. */
    val cuentas: Set<TipoCuenta> = emptySet()
) {
    val lookupUri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

data class SmsMsg(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val read: Boolean
)

data class SmsThread(
    val threadId: Long,
    val address: String,
    val name: String?,
    val last: SmsMsg,
    val count: Int,
    val unread: Int
)

/** Lectura del registro de llamadas, contactos y SMS del teléfono. */
object PhoneData {

    val PERMISOS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.GET_ACCOUNTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_SMS
    )

    fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context) = PERMISOS.filterNot { has(context, it) }

    // ---------------- Registro de llamadas ----------------

    fun loadCalls(context: Context, limit: Int = 500): List<CallEntry> {
        if (!has(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = ArrayList<CallEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                out += CallEntry(
                    id = c.getLong(0),
                    number = c.getString(1).orEmpty(),
                    name = c.getString(2)?.takeIf { it.isNotBlank() },
                    type = c.getInt(3),
                    date = c.getLong(4),
                    duration = c.getLong(5)
                )
            }
        }
        return out
    }

    fun groupCalls(calls: List<CallEntry>): List<CallGroup> {
        val out = ArrayList<CallGroup>()
        for (call in calls) {
            val prev = out.lastOrNull()
            if (prev != null &&
                Prefs.normalize(prev.latest.number) == Prefs.normalize(call.number) &&
                prev.latest.type == call.type
            ) {
                out[out.lastIndex] = prev.copy(count = prev.count + 1)
            } else {
                out += CallGroup(call, 1)
            }
        }
        return out
    }

    // ---------------- Contactos ----------------

    fun loadContacts(context: Context): List<ContactEntry> {
        if (!has(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val map = LinkedHashMap<Long, ContactEntry>()
        context.contentResolver.query(
            phone, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(3)?.trim().orEmpty()
                val existing = map[id]
                if (existing == null) {
                    map[id] = ContactEntry(
                        id = id,
                        lookupKey = c.getString(1).orEmpty(),
                        name = c.getString(2)?.takeIf { it.isNotBlank() } ?: number,
                        numbers = if (number.isEmpty()) emptyList() else listOf(number),
                        starred = c.getInt(4) == 1
                    )
                } else if (number.isNotEmpty() &&
                    existing.numbers.none { Prefs.normalize(it) == Prefs.normalize(number) }
                ) {
                    map[id] = existing.copy(numbers = existing.numbers + number)
                }
            }
        }
        val origen = ContactStore.cuentasPorContacto(context)
        return map.values.map { it.copy(cuentas = origen[it.id].orEmpty()) }
    }

    /** Mapa número normalizado → nombre del contacto. */
    fun nameIndex(contacts: List<ContactEntry>): Map<String, String> {
        val m = HashMap<String, String>()
        for (c in contacts) for (n in c.numbers) m.putIfAbsent(Prefs.normalize(n), c.name)
        return m
    }

    // ---------------- SMS ----------------

    private val SMS_PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ
    )

    private fun readSms(c: android.database.Cursor) = SmsMsg(
        id = c.getLong(0),
        threadId = c.getLong(1),
        address = c.getString(2).orEmpty(),
        body = c.getString(3).orEmpty(),
        date = c.getLong(4),
        incoming = c.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX,
        read = c.getInt(6) == 1
    )

    fun loadThreads(context: Context, names: Map<String, String>, maxRows: Int = 4000): List<SmsThread> {
        if (!has(context, Manifest.permission.READ_SMS)) return emptyList()
        val threads = LinkedHashMap<Long, SmsThread>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, SMS_PROJECTION, null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            var rows = 0
            while (c.moveToNext() && rows++ < maxRows) {
                val msg = readSms(c)
                val unread = if (msg.incoming && !msg.read) 1 else 0
                val t = threads[msg.threadId]
                threads[msg.threadId] = if (t == null) {
                    SmsThread(
                        threadId = msg.threadId,
                        address = msg.address,
                        name = names[Prefs.normalize(msg.address)],
                        last = msg,
                        count = 1,
                        unread = unread
                    )
                } else {
                    t.copy(count = t.count + 1, unread = t.unread + unread)
                }
            }
        }
        return threads.values.toList()
    }

    fun loadConversation(context: Context, threadId: Long, limit: Int = 500): List<SmsMsg> {
        if (!has(context, Manifest.permission.READ_SMS)) return emptyList()
        val out = ArrayList<SmsMsg>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, SMS_PROJECTION,
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) out += readSms(c)
        }
        return out // del más nuevo al más viejo
    }
}

/** Acciones que abren el marcador, la app de SMS o Contactos del sistema. */
object Acciones {

    fun llamar(context: Context, number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        val action = if (PhoneData.has(context, Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        abrir(context, Intent(action, uri))
    }

    fun mensaje(context: Context, number: String) {
        abrir(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))
    }

    fun nuevoMensaje(context: Context) {
        abrir(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
    }

    fun nuevoContacto(context: Context, number: String? = null) {
        val i = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
        if (!number.isNullOrBlank()) i.putExtra(ContactsContract.Intents.Insert.PHONE, number)
        abrir(context, i)
    }

    fun verContacto(context: Context, contact: ContactEntry) {
        abrir(context, Intent(Intent.ACTION_VIEW, contact.lookupUri))
    }

    fun editarContacto(context: Context, contact: ContactEntry) {
        abrir(context, Intent(Intent.ACTION_EDIT, contact.lookupUri).putExtra("finishActivityOnSaveCompleted", true))
    }

    fun ajustesDeLaApp(context: Context) {
        abrir(
            context,
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
    }

    private fun abrir(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
