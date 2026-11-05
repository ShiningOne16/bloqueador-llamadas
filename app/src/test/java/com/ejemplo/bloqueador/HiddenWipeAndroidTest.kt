package com.ejemplo.bloqueador

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
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
import org.robolectric.shadows.ShadowTelephony

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HiddenWipeAndroidTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("hidden_contacts", 0)
    private val hidden = listOf(HiddenContact("Privado", listOf("5512345678"), 11, "key"))
    private lateinit var contacts: HiddenResetProvider
    private lateinit var calls: HiddenResetProvider
    private lateinit var sms: HiddenResetProvider

    @Before fun setup() {
        shadowOf(context).grantPermissions(
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_SMS
        )
        ShadowTelephony.ShadowSms.setDefaultSmsPackage(context.packageName)
        fun provider(authority: String): HiddenResetProvider {
            val p = Robolectric.buildContentProvider(HiddenResetProvider::class.java).create().get()
            ShadowContentResolver.registerProviderInternal(authority, p)
            return p
        }
        contacts = provider("com.android.contacts")
        calls = provider("call_log")
        sms = provider("sms")
        prefs.edit().clear().putString("pin_hash", "existing-hash").putString("data", "encrypted-list").commit()
    }

    private fun reset() = HiddenWipe.perform(AndroidHiddenWipe(context, hidden))
    private fun assertProtected(report: WipeReport) {
        assertFalse(report.complete)
        assertTrue(HiddenStore(context).hasPin())
        assertEquals("encrypted-list", prefs.getString("data", null))
    }

    @Test fun unreadableEncryptedListDoesNotEraseThePinOrList() {
        assertProtected(HiddenWipe.run(context))
    }

    @Test fun deniedReadPermissionDoesNotDeleteAnything() {
        contacts.add(11)
        shadowOf(context).denyPermissions(Manifest.permission.READ_SMS)
        assertProtected(reset())
        assertEquals(1, contacts.count())
    }

    @Test fun losingDefaultSmsRoleDoesNotEraseProtection() {
        contacts.add(11)
        ShadowTelephony.ShadowSms.setDefaultSmsPackage("another.app")
        assertProtected(reset())
        assertEquals(1, contacts.count())
    }

    @Test fun nullProviderCursorIsAnErrorRatherThanAnEmptyHistory() {
        calls.nullCursor = true
        assertProtected(reset())
    }

    @Test fun partialSmsDeletionCanBeRetriedAfterContactWasAlreadyDeleted() {
        contacts.add(11)
        calls.add(1)
        sms.add(1)
        sms.refuseDeletes = true
        assertProtected(reset())
        assertEquals(0, contacts.count())
        assertEquals(0, calls.count())
        assertEquals(1, sms.count())
        sms.refuseDeletes = false
        assertTrue(reset().complete)
        assertFalse(HiddenStore(context).hasPin())
        assertFalse(prefs.contains("data"))
    }

    @Test fun resetFindsOldRecordsBeyondUiLimitsAndKeepsOtherNumbers() {
        calls.seedOthers(10001)
        sms.seedOthers(20001)
        calls.add(99999)
        sms.add(99999)
        val report = reset()
        assertTrue(report.complete)
        assertEquals(1, report.callsDeleted)
        assertEquals(1, report.smsMessagesDeleted)
        assertEquals(10001, calls.count())
        assertEquals(20001, sms.count())
    }
}

class HiddenResetProvider : ContentProvider() {
    private val db = SQLiteDatabase.create(null).apply {
        execSQL("CREATE TABLE records(_id INTEGER PRIMARY KEY, date INTEGER, number TEXT, address TEXT, thread_id INTEGER)")
    }
    var nullCursor = false
    var refuseDeletes = false
    fun add(id: Long, number: String = "5512345678") {
        db.execSQL("INSERT INTO records VALUES (?, ?, ?, ?, ?)", arrayOf(id, id, number, number, id))
    }
    fun seedOthers(count: Int) {
        db.beginTransaction()
        try {
            for (i in 1..count) add(i.toLong(), "5599999999")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    fun count() = db.rawQuery("SELECT COUNT(*) FROM records", null).use { it.moveToFirst(); it.getInt(0) }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/records"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? =
        if (nullCursor) null else db.query("records", projection, selection, args, null, null, sort)
    override fun insert(uri: Uri, values: ContentValues?): Uri = uri
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int =
        if (refuseDeletes) 0 else db.delete("records", selection, args)
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
