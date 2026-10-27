package com.ejemplo.bloqueador

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log

/** Dónde vive un contacto. */
enum class TipoCuenta { GOOGLE, SIM, TELEFONO, OTRA }

/**
 * Un lugar donde se puede guardar un contacto, igual que en la app nativa.
 * [viaTarjeta] = no hay cuenta SIM en el sistema; se escribe directo en la tarjeta (content://icc/adn).
 */
data class CuentaContacto(
    val tipo: TipoCuenta,
    val accountType: String?,
    val accountName: String?,
    val viaTarjeta: Boolean = false,
    val ranuraSim: Int? = null
) {
    val id: String get() = "${tipo.name}|${accountType.orEmpty()}|${accountName.orEmpty()}|$viaTarjeta"

    val titulo: String
        get() = when (tipo) {
            TipoCuenta.GOOGLE -> "Google"
            TipoCuenta.SIM -> if (ranuraSim != null) "SIM ${ranuraSim + 1}" else "Tarjeta SIM"
            TipoCuenta.TELEFONO -> "Solo este teléfono"
            TipoCuenta.OTRA -> accountName ?: accountType ?: "Otra cuenta"
        }

    /** Segunda línea en la lista: el correo de la cuenta de Google, por ejemplo. */
    val subtitulo: String?
        get() = when (tipo) {
            TipoCuenta.GOOGLE, TipoCuenta.OTRA -> accountName
            else -> null
        }

    /** Explicación breve de qué implica guardar aquí. */
    val detalle: String
        get() = when (tipo) {
            TipoCuenta.GOOGLE -> "Se respalda en tu cuenta y aparece en contacts.google.com."
            TipoCuenta.SIM -> "Solo nombre y un número. Se queda en la tarjeta."
            TipoCuenta.TELEFONO -> "No se respalda. Si cambias de teléfono, se pierde."
            TipoCuenta.OTRA -> "Se sincroniza con esa cuenta."
        }
}

/** Datos de un contacto en el formulario. */
data class TelefonoForm(val numero: String, val tipo: Int = Phone.TYPE_MOBILE)

data class ContactoForm(
    val nombre: String = "",
    val apellidos: String = "",
    val telefonos: List<TelefonoForm> = listOf(TelefonoForm("")),
    val correo: String = "",
    val favorito: Boolean = false
)

/** Contacto existente cargado para editar. */
data class ContactoEditable(
    val contactId: Long,
    val rawContactId: Long,
    val cuenta: CuentaContacto,
    val datos: ContactoForm
)

object ContactStore {

    private const val TAG = "ContactStore"
    private val ICC_URI: Uri = Uri.parse("content://icc/adn")

    /** "com.android.contacts.sim", "vnd.sec.contact.sim2", "com.oplus.contacts.sim"… */
    private val PATRON_SIM = Regex("(^|[._])sim\\d*($|[._])")

    fun clasificar(accountType: String?): TipoCuenta {
        val t = accountType?.lowercase() ?: return TipoCuenta.TELEFONO
        return when {
            t == "com.google" -> TipoCuenta.GOOGLE
            PATRON_SIM.containsMatchIn(t) -> TipoCuenta.SIM
            "phone" in t || "local" in t || t.endsWith(".default") -> TipoCuenta.TELEFONO
            else -> TipoCuenta.OTRA
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cuentas disponibles
    // ---------------------------------------------------------------------------------------

    /** Lugares donde se puede guardar: Google primero, luego SIM, luego el teléfono. */
    fun cuentasDisponibles(context: Context): List<CuentaContacto> {
        val cr = context.contentResolver
        val out = LinkedHashMap<String, CuentaContacto>()
        fun agregar(c: CuentaContacto) { out.putIfAbsent(c.id, c) }

        // Tipos de cuenta que aceptan contactos nuevos (como hace la app nativa)
        val escribibles = ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .map { it.accountType }
            .toSet()

        // 1) Cuentas del sistema (Google, Samsung, Xiaomi…). Con permiso de contactos son visibles.
        try {
            AccountManager.get(context).accounts
                .filter { it.type in escribibles || it.type == "com.google" }
                .forEach { agregar(CuentaContacto(clasificar(it.type), it.type, it.name)) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer las cuentas", e)
        }

        // 2) Cuentas SIM oficiales (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ContactsContract.SimContacts.getSimAccounts(cr).forEach {
                    agregar(CuentaContacto(TipoCuenta.SIM, it.accountType, it.accountName, ranuraSim = it.simSlotIndex))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sin cuentas SIM oficiales", e)
            }
        }

        // 3) Cuentas que ya tienen contactos (aquí aparecen la SIM y el "Teléfono" de cada marca)
        if (PhoneData.has(context, Manifest.permission.READ_CONTACTS)) {
            try {
                cr.query(
                    RawContacts.CONTENT_URI,
                    arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                    "${RawContacts.DELETED} = 0", null, null
                )?.use { c ->
                    val vistos = HashSet<String>()
                    while (c.moveToNext()) {
                        val type = c.getString(0)
                        val name = c.getString(1)
                        if (!vistos.add("$type|$name")) continue
                        val tipo = clasificar(type)
                        if (tipo == TipoCuenta.OTRA && type !in escribibles) continue // WhatsApp, Telegram…
                        agregar(CuentaContacto(tipo, type, name))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron leer las cuentas de contactos", e)
            }
        }

        // 4) SIM directa a la tarjeta, si el teléfono no expone una cuenta SIM
        if (out.values.none { it.tipo == TipoCuenta.SIM } && tarjetaDisponible(context)) {
            agregar(CuentaContacto(TipoCuenta.SIM, null, null, viaTarjeta = true))
        }

        // 5) Siempre se puede guardar solo en el teléfono
        if (out.values.none { it.tipo == TipoCuenta.TELEFONO }) {
            agregar(CuentaContacto(TipoCuenta.TELEFONO, null, null))
        }

        return out.values.sortedBy {
            when (it.tipo) {
                TipoCuenta.GOOGLE -> 0
                TipoCuenta.SIM -> 1
                TipoCuenta.TELEFONO -> 2
                TipoCuenta.OTRA -> 3
            }
        }
    }

    private fun tarjetaDisponible(context: Context): Boolean = try {
        context.contentResolver.query(ICC_URI, null, null, null, null)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    // ---------------------------------------------------------------------------------------
    // Leer para editar
    // ---------------------------------------------------------------------------------------

    fun cargar(context: Context, contactId: Long): ContactoEditable? {
        val cr = context.contentResolver

        // Contacto "crudo" de la cuenta principal (si está en varias, preferimos Google)
        data class Crudo(val id: Long, val type: String?, val name: String?)

        val crudos = ArrayList<Crudo>()
        cr.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()), null
        )?.use { c -> while (c.moveToNext()) crudos += Crudo(c.getLong(0), c.getString(1), c.getString(2)) }

        val crudo = crudos.sortedBy {
            when (clasificar(it.type)) {
                TipoCuenta.GOOGLE -> 0; TipoCuenta.TELEFONO -> 1; TipoCuenta.SIM -> 2; TipoCuenta.OTRA -> 3
            }
        }.firstOrNull() ?: return null

        var nombre = ""
        var apellidos = ""
        var nombreCompleto = ""
        val telefonos = ArrayList<TelefonoForm>()
        var correo = ""
        cr.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
            "${Data.RAW_CONTACT_ID} = ?", arrayOf(crudo.id.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> {
                        nombreCompleto = c.getString(1).orEmpty()
                        nombre = c.getString(2).orEmpty()   // GIVEN_NAME
                        apellidos = c.getString(3).orEmpty() // FAMILY_NAME
                    }
                    Phone.CONTENT_ITEM_TYPE -> c.getString(1)?.takeIf { it.isNotBlank() }?.let {
                        telefonos += TelefonoForm(it, if (c.isNull(2)) Phone.TYPE_MOBILE else c.getInt(2))
                    }
                    Email.CONTENT_ITEM_TYPE -> if (correo.isEmpty()) correo = c.getString(1).orEmpty()
                }
            }
        }
        if (nombre.isEmpty() && apellidos.isEmpty()) nombre = nombreCompleto

        var favorito = false
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts._ID} = ?", arrayOf(contactId.toString()), null
        )?.use { if (it.moveToFirst()) favorito = it.getInt(0) == 1 }

        return ContactoEditable(
            contactId = contactId,
            rawContactId = crudo.id,
            cuenta = CuentaContacto(clasificar(crudo.type), crudo.type, crudo.name),
            datos = ContactoForm(
                nombre = nombre,
                apellidos = apellidos,
                telefonos = telefonos.ifEmpty { listOf(TelefonoForm("")) },
                correo = correo,
                favorito = favorito
            )
        )
    }

    // ---------------------------------------------------------------------------------------
    // Guardar
    // ---------------------------------------------------------------------------------------

    sealed class Resultado {
        data object Ok : Resultado()
        data class Error(val mensaje: String) : Resultado()
    }

    /** Crea un contacto nuevo en la cuenta elegida. */
    fun crear(context: Context, cuenta: CuentaContacto, f: ContactoForm): Resultado {
        val telefonos = f.telefonos.filter { it.numero.isNotBlank() }
        return try {
            if (cuenta.viaTarjeta) return crearEnTarjeta(context, f, telefonos)

            val esSim = cuenta.tipo == TipoCuenta.SIM
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, cuenta.accountType)
                .withValue(RawContacts.ACCOUNT_NAME, cuenta.accountName)
                .build()
            agregarDatos(ops, rawIndex = 0, rawId = null, f = f, telefonos = if (esSim) telefonos.take(1) else telefonos, conCorreo = !esSim)

            val res = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (f.favorito && !esSim) {
                res.firstOrNull()?.uri?.let { uri ->
                    context.contentResolver.update(uri, ContentValues().apply { put(RawContacts.STARRED, 1) }, null, null)
                }
            }
            Resultado.Ok
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear el contacto", e)
            // Si la cuenta SIM del sistema no acepta escritura, intentamos directo en la tarjeta
            if (cuenta.tipo == TipoCuenta.SIM && tarjetaDisponible(context)) {
                crearEnTarjeta(context, f, telefonos)
            } else {
                Resultado.Error("No se pudo guardar en ${cuenta.titulo}.")
            }
        }
    }

    private fun crearEnTarjeta(context: Context, f: ContactoForm, telefonos: List<TelefonoForm>): Resultado {
        val numero = telefonos.firstOrNull()?.numero ?: return Resultado.Error("La SIM necesita un número.")
        val nombre = listOf(f.nombre, f.apellidos).filter { it.isNotBlank() }.joinToString(" ")
        val valores = ContentValues().apply {
            put("tag", nombre)
            put("number", numero.filter { it.isDigit() || it == '+' })
        }
        return try {
            val uri = context.contentResolver.insert(ICC_URI, valores)
            if (uri != null) Resultado.Ok else Resultado.Error("La tarjeta SIM no aceptó el contacto (puede estar llena).")
        } catch (e: Exception) {
            Log.e(TAG, "Error al escribir en la SIM", e)
            Resultado.Error("Este teléfono no permite guardar en la SIM desde otras apps.")
        }
    }

    /** Actualiza un contacto existente (en la misma cuenta donde está). */
    fun actualizar(context: Context, c: ContactoEditable, f: ContactoForm): Resultado {
        val esSim = c.cuenta.tipo == TipoCuenta.SIM
        val telefonos = f.telefonos.filter { it.numero.isNotBlank() }
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            val raw = c.rawContactId.toString()
            // Se reemplazan nombre, teléfonos y correo; el resto (fotos, notas…) se conserva
            for (tipo in listOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE)) {
                ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?", arrayOf(raw, tipo))
                    .build()
            }
            agregarDatos(ops, rawIndex = null, rawId = c.rawContactId, f = f,
                telefonos = if (esSim) telefonos.take(1) else telefonos, conCorreo = !esSim)
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            if (!esSim) {
                context.contentResolver.update(
                    ContactsContract.Contacts.CONTENT_URI,
                    ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (f.favorito) 1 else 0) },
                    "${ContactsContract.Contacts._ID} = ?", arrayOf(c.contactId.toString())
                )
            }
            Resultado.Ok
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo actualizar", e)
            Resultado.Error("No se pudieron guardar los cambios.")
        }
    }

    /** Borra el contacto de todas sus cuentas (Google lo borra también en la nube). */
    fun eliminar(context: Context, contact: ContactEntry): Resultado = try {
        val n = context.contentResolver.delete(contact.lookupUri, null, null)
        if (n > 0) Resultado.Ok else Resultado.Error("No se encontró el contacto.")
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo eliminar", e)
        Resultado.Error("No se pudo eliminar el contacto.")
    }

    private fun agregarDatos(
        ops: ArrayList<ContentProviderOperation>,
        rawIndex: Int?,
        rawId: Long?,
        f: ContactoForm,
        telefonos: List<TelefonoForm>,
        conCorreo: Boolean
    ) {
        fun insertar(): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            return if (rawIndex != null) b.withValueBackReference(Data.RAW_CONTACT_ID, rawIndex)
            else b.withValue(Data.RAW_CONTACT_ID, rawId)
        }

        val completo = listOf(f.nombre.trim(), f.apellidos.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (completo.isNotEmpty()) {
            ops += insertar()
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, f.nombre.trim().ifEmpty { null })
                .withValue(StructuredName.FAMILY_NAME, f.apellidos.trim().ifEmpty { null })
                .withValue(StructuredName.DISPLAY_NAME, completo)
                .build()
        }
        telefonos.forEach { t ->
            ops += insertar()
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, t.numero.trim())
                .withValue(Phone.TYPE, t.tipo)
                .build()
        }
        if (conCorreo && f.correo.isNotBlank()) {
            ops += insertar()
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, f.correo.trim())
                .withValue(Email.TYPE, Email.TYPE_HOME)
                .build()
        }
    }

    /** Contacto → tipos de cuenta donde está guardado (para mostrar "Google", "SIM"…). */
    fun cuentasPorContacto(context: Context): Map<Long, Set<TipoCuenta>> {
        val out = HashMap<Long, MutableSet<TipoCuenta>>()
        try {
            context.contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.DELETED} = 0", null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val tipo = clasificar(c.getString(1))
                    if (tipo == TipoCuenta.OTRA) continue
                    out.getOrPut(c.getLong(0)) { mutableSetOf() } += tipo
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer el origen de los contactos", e)
        }
        return out
    }
}
