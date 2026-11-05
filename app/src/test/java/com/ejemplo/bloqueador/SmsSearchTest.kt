package com.ejemplo.bloqueador

import android.Manifest
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SmsSearchTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: SearchSmsProvider

    @Before fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_SMS)
        provider = Robolectric.buildContentProvider(SearchSmsProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("sms", provider)
    }

    private fun add(id: Long, thread: Long, body: String) {
        provider.insert(Telephony.Sms.CONTENT_URI, ContentValues().apply {
            put("_id", id); put("thread_id", thread); put("address", "5512345678"); put("body", body)
        })
    }

    @Test fun matchesNamesNumbersAndAlphanumericSenders() {
        assertTrue(SmsSearch.matchesSender("José López", "+52 (55) 1234-5678", " jose "))
        assertTrue(SmsSearch.matchesSender(null, "+52 (55) 1234-5678", "55 1234"))
        assertTrue(SmsSearch.matchesSender(null, "TELCEL", "telcel"))
        assertFalse(SmsSearch.matchesSender(null, "5512345678", "pedido 1234"))
        assertFalse(SmsSearch.matchesSender("Ana", "5512345678", "José"))
    }

    @Test fun findsOlderMessagesAndDeduplicatesWithinAllowedThreads() = runBlocking {
        add(1, 10, "Tu CÓDIGO de entrega")
        add(2, 10, "Código anterior")
        add(3, 10, "Este es el SMS más reciente")
        add(4, 20, "Otro código")
        add(5, 30, "Sin coincidencias")
        assertEquals(setOf(10L), PhoneData.searchSmsThreads(context, " codigo ", setOf(10L, 30L)))
        assertEquals(emptySet<Long>(), PhoneData.searchSmsThreads(context, "inexistente", setOf(10L, 30L)))
    }

    @Test fun treatsWildcardCharactersAsLiteralText() = runBlocking {
        add(1, 10, "Descuento del 50%")
        add(2, 20, "Sin descuento")
        assertEquals(setOf(10L), PhoneData.searchSmsThreads(context, "%", setOf(10L, 20L)))
        assertEquals(emptySet<Long>(), PhoneData.searchSmsThreads(context, "_", setOf(10L, 20L)))
    }

    @Test fun blankQueryOrEmptyThreadScopeDoesNotSearchEverything() = runBlocking {
        add(1, 10, "Mensaje")
        assertEquals(emptySet<Long>(), PhoneData.searchSmsThreads(context, " ", setOf(10L)))
        assertEquals(emptySet<Long>(), PhoneData.searchSmsThreads(context, "Mensaje", emptySet()))
    }

    @Test fun missingPermissionReportsFailure() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_SMS)
        try {
            runBlocking { PhoneData.searchSmsThreads(context, "Mensaje", setOf(10L)) }
            fail("Debe informar que falta el permiso de SMS")
        } catch (_: IllegalStateException) { }
    }

    @Test fun hiddenSenderCannotProduceASearchResult() = runBlocking {
        add(1, 10, "Contenido privado")
        SesionEntrada.entrarFalso(listOf(HiddenContact("Privado", listOf("5512345678"))))
        try {
            assertEquals(emptySet<Long>(), PhoneData.searchSmsThreads(context, "privado", setOf(10L)))
        } finally { SesionEntrada.abrirNormal() }
    }
}

class SearchSmsProvider : ContentProvider() {
    private val db = SQLiteDatabase.create(null).apply {
        execSQL("CREATE TABLE sms(_id INTEGER PRIMARY KEY, thread_id INTEGER, address TEXT, body TEXT)")
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/sms"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
        db.query("sms", projection, selection, args, null, null, sort)
    override fun insert(uri: Uri, values: ContentValues?): Uri = ContentUris.withAppendedId(uri, db.insertOrThrow("sms", null, values))
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = db.delete("sms", selection, args)
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = db.update("sms", values, selection, args)
}
