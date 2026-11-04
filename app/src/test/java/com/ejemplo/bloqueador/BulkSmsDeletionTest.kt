package com.ejemplo.bloqueador

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.Telephony
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowTelephony

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BulkSmsDeletionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: SelectionSmsProvider
    @Before fun setup() {
        ShadowTelephony.ShadowSms.setDefaultSmsPackage(context.packageName)
        provider = Robolectric.buildContentProvider(SelectionSmsProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("sms", provider)
    }
    private fun add(id: Long, thread: Long, date: Long) {
        provider.insert(Telephony.Sms.CONTENT_URI, ContentValues().apply { put("_id", id); put("thread_id", thread); put("date", date) })
    }
    private fun ids() = provider.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), null, null, "_id")!!.use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
    }
    @Test fun deletingSelectedConversationsKeepsNewAndUnselectedMessages() {
        add(1, 10, 100); add(2, 10, 200); add(3, 20, 150); add(4, 30, 100)
        val selection = listOf(SmsDeletionTarget(10, 2, 200), SmsDeletionTarget(20, 3, 150))
        add(5, 10, 300); add(6, 20, 350)
        assertEquals(3, SmsRepository.deleteThreads(context, selection))
        assertEquals(listOf(4L, 5L, 6L), ids())
    }
    @Test fun individualBulkDeletionCannotDeleteAReusedIdWithDifferentDate() {
        add(7, 10, 900)
        val old = SmsMsg(7, 10, "5512345678", "Anterior", 100, true, false)
        assertEquals(0, SmsRepository.deleteMessages(context, listOf(old)))
        assertEquals(listOf(7L), ids())
    }
    @Test fun deletionUsesMultipleBatchesAndKeepsUnselectedMessages() {
        val selected = (1L..205L).map { id -> add(id, 10, id * 100); SmsMsg(id, 10, "5512345678", "SMS", id * 100, true, false) }
        add(300, 10, 99999)
        assertEquals(205, SmsRepository.deleteMessages(context, selected))
        assertEquals(listOf(300L), ids())
    }
    @Test fun nonDefaultSmsAppCannotBulkDelete() {
        add(1, 10, 100)
        ShadowTelephony.ShadowSms.setDefaultSmsPackage("other.app")
        try { SmsRepository.deleteThreads(context, listOf(SmsDeletionTarget(10, 1, 100))); fail("Debe exigir el rol de SMS") }
        catch (_: IllegalStateException) { }
        assertEquals(listOf(1L), ids())
    }
    @Test fun manualCallBlockCanBeReversedAndIgnoresCountryFormatting() {
        val prefs = Prefs(context)
        prefs.setManualBlock("+52 55 1234 5678", true)
        assertTrue(prefs.manuallyBlocked("5512345678"))
        assertFalse(prefs.manuallyBlocked("5587654321"))
        prefs.setManualBlock("5512345678", false)
        assertFalse(prefs.manuallyBlocked("+52 55 1234 5678"))
    }
}

class SelectionSmsProvider : ContentProvider() {
    private val db = SQLiteDatabase.create(null).apply { execSQL("CREATE TABLE sms(_id INTEGER PRIMARY KEY, thread_id INTEGER, date INTEGER)") }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/sms"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
        db.query("sms", projection, selection, args, null, null, sort)
    override fun insert(uri: Uri, values: ContentValues?): Uri = ContentUris.withAppendedId(uri, db.insertOrThrow("sms", null, values))
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = db.delete("sms", selection, args)
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = db.update("sms", values, selection, args)
}
