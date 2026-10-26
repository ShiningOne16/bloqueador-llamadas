package com.ejemplo.bloqueador

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/** Consultas a la agenda del teléfono. */
object Contacts {

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** ¿Este número de teléfono está guardado en contactos? */
    fun isNumberInContacts(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
        )?.use { return it.count > 0 }
        return false
    }

    /** ¿Hay un contacto con exactamente este nombre? (las apps de SMS muestran el nombre del contacto) */
    fun isNameInContacts(context: Context, name: String): Boolean {
        if (name.isBlank()) return false
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE",
            arrayOf(name.trim()),
            null
        )?.use { return it.count > 0 }
        return false
    }
}
