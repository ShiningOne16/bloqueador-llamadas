package com.ejemplo.bloqueador

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
data class CallGroup(val latest: CallEntry, val count: Int, val entries: List<CallEntry> = listOf(latest))

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
    val read: Boolean,
    val type: Int = if (incoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
    val subscriptionId: Int = -1
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

    fun loadCalls(context: Context, limit: Int = 500, onlyIds: Set<Long>? = null, includeHidden: Boolean = false): List<CallEntry> {
        if (!has(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        if (onlyIds != null && onlyIds.isEmpty()) return emptyList()
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
            CallLog.Calls.CONTENT_URI, projection,
            onlyIds?.let { "${CallLog.Calls._ID} IN (${it.joinToString(",")})" }, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && (onlyIds != null || out.size < limit)) {
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

        // Los contactos ocultos no aparecen en el registro.
        if (!includeHidden) {
            val ocultos = VistaOculta.claves(context)
            if (ocultos.isNotEmpty()) out.removeAll { HiddenRules.isHiddenNumber(ocultos, it.number) }
        }

        // El nombre que guarda Android (CACHED_NAME) es el del momento de la llamada y no se
        // actualiza al guardar, editar o borrar un contacto. Se busca en la agenda actual.
        if (!has(context, Manifest.permission.READ_CONTACTS)) return out
        val nombres = nameIndex(loadContacts(context))
        return out.map { call ->
            val clave = Prefs.normalize(call.number)
            call.copy(name = if (clave.isEmpty()) null else nombres[clave])
        }
    }

    fun groupCalls(calls: List<CallEntry>): List<CallGroup> {
        val out = ArrayList<CallGroup>()
        for (call in calls) {
            val prev = out.lastOrNull()
            if (prev != null &&
                Prefs.normalize(prev.latest.number) == Prefs.normalize(call.number) &&
                prev.latest.type == call.type
            ) {
                out[out.lastIndex] = prev.copy(count = prev.count + 1, entries = prev.entries + call)
            } else {
                out += CallGroup(call, 1)
            }
        }
        return out
    }

    /** Elimina solo los registros confirmados, sin incluir llamadas nuevas del mismo número. */
    fun deleteCalls(context: Context, calls: List<CallEntry>): Int {
        check(has(context, Manifest.permission.WRITE_CALL_LOG)) { "Falta permiso para borrar llamadas" }
        if (calls.isEmpty()) return 0
        val operations = calls.distinctBy { it.id }.map { call ->
            android.content.ContentProviderOperation.newDelete(CallLog.Calls.CONTENT_URI)
                .withSelection("${CallLog.Calls._ID} = ? AND ${CallLog.Calls.DATE} = ?", arrayOf(call.id.toString(), call.date.toString()))
                .build()
        }
        return operations.chunked(200).sumOf { chunk -> context.contentResolver.applyBatch(CallLog.AUTHORITY, ArrayList(chunk)).sumOf { it.count ?: 0 } }
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
        val todos = map.values.map { it.copy(cuentas = origen[it.id].orEmpty()) }
        // Los contactos ocultos no se muestran en ninguna lista de la app.
        val ocultos = VistaOculta.contactos(context)
        if (ocultos.isEmpty()) return todos
        val claves = HiddenRules.keys(ocultos)
        return todos.filterNot { HiddenRules.isHiddenEntry(ocultos, claves, it.id, it.numbers) }
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
        Telephony.Sms.READ,
        Telephony.Sms.SUBSCRIPTION_ID
    )

    private fun readSms(c: android.database.Cursor) = SmsMsg(
        id = c.getLong(0),
        threadId = c.getLong(1),
        address = c.getString(2).orEmpty(),
        body = c.getString(3).orEmpty(),
        date = c.getLong(4),
        incoming = c.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX,
        read = c.getInt(6) == 1,
        type = c.getInt(5),
        subscriptionId = c.getInt(7)
    )

    /** Resume todos los SMS: un límite de filas dejaría conversaciones antiguas fuera del buscador. */
    fun loadThreads(context: Context, names: Map<String, String>, onlyIds: Set<Long>? = null, includeHidden: Boolean = false): List<SmsThread> {
        if (!has(context, Manifest.permission.READ_SMS)) return emptyList()
        if (onlyIds != null && onlyIds.isEmpty()) return emptyList()
        val threads = LinkedHashMap<Long, SmsThread>()
        val ocultos = if (includeHidden) emptySet() else VistaOculta.claves(context)
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, SMS_PROJECTION,
            onlyIds?.let { "${Telephony.Sms.THREAD_ID} IN (${it.joinToString(",")})" }, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val msg = readSms(c)
                if (ocultos.isNotEmpty() && HiddenRules.isHiddenNumber(ocultos, msg.address)) continue
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

    /** Busca también en SMS antiguos, sin cargar sus textos en la interfaz. */
    suspend fun searchSmsThreads(context: Context, query: String, threadIds: Set<Long>): Set<Long> {
        if (query.isBlank() || threadIds.isEmpty()) return emptySet()
        check(has(context, Manifest.permission.READ_SMS)) { "Falta el permiso de SMS" }
        val matches = mutableSetOf<Long>()
        val ocultos = VistaOculta.claves(context)
        val coroutineContext = currentCoroutineContext()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            "${Telephony.Sms.THREAD_ID} IN (${threadIds.joinToString(",")})", null, null
        ) ?: error("No se pudieron consultar los SMS")
        cursor.use { c ->
            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val threadId = c.getLong(0)
                if (threadId in matches) continue
                val address = c.getString(1).orEmpty()
                if (ocultos.isNotEmpty() && HiddenRules.isHiddenNumber(ocultos, address)) continue
                if (SmsSearch.matchesText(c.getString(2).orEmpty(), query)) matches += threadId
            }
        }
        return matches
    }

    fun loadConversation(context: Context, threadId: Long, limit: Int = 500): List<SmsMsg> {
        if (!has(context, Manifest.permission.READ_SMS)) return emptyList()
        val out = ArrayList<SmsMsg>()
        val ocultos = VistaOculta.claves(context)
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, SMS_PROJECTION,
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val msg = readSms(c)
                if (ocultos.isEmpty() || !HiddenRules.isHiddenNumber(ocultos, msg.address)) out += msg
            }
        }
        return out // del más nuevo al más viejo
    }
}

/** Acciones que abren el marcador, la app de SMS o Contactos del sistema. */
object Acciones {

    fun whatsapp(context: Context, number: String): Boolean {
        val country = try { context.getSystemService(android.telephony.TelephonyManager::class.java).networkCountryIso.uppercase() }
            catch (_: Exception) { "" }
        val e164 = if (number.trim().startsWith("+")) SmsPolicy.recipient(number)
            else android.telephony.PhoneNumberUtils.formatNumberToE164(number, country.ifBlank { java.util.Locale.getDefault().country })
        if (e164 == null) {
            android.widget.Toast.makeText(context, "Guarda el número con código de país (por ejemplo, +52) para abrir WhatsApp.", android.widget.Toast.LENGTH_LONG).show()
            return false
        }
        val uri = Uri.parse("https://wa.me/${e164.filter { it.isDigit() }}")
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { if (pkg != null) setPackage(pkg) }); return true }
            catch (_: android.content.ActivityNotFoundException) { }
        }
        android.widget.Toast.makeText(context, "No se pudo abrir WhatsApp.", android.widget.Toast.LENGTH_LONG).show()
        return false
    }

    /** El borrado de SMS se completa en la app de SMS predeterminada de Android. */
    fun manageSms(context: Context, address: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", address, null))
        Telephony.Sms.getDefaultSmsPackage(context)?.let { intent.setPackage(it) }
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }

    fun manageCallLog(context: Context): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI))
        true
    } catch (_: Exception) { false }

    /** Pide marcar: si la espera está activa, antes sale la cuenta regresiva con "Cancelar". */
    fun llamar(context: Context, number: String) {
        if (number.isBlank()) return
        if (Prefs(context).delayCall) RetrasoLlamada.pedir(number) else llamarYa(context, number)
    }

    fun llamarYa(context: Context, number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        val action = if (PhoneData.has(context, Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        abrir(context, Intent(action, uri))
    }

    fun mensaje(context: Context, number: String) {
        abrir(context, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SENDTO).setData(Uri.fromParts("smsto", number, null)))
    }

    fun nuevoMensaje(context: Context) {
        abrir(context, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:")))
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

