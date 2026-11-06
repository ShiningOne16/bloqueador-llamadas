package com.ejemplo.bloqueador

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.CallLog
import org.junit.Assert.*
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
class BlockedCallLoadingTest {
    @Test fun blockedFilterLoadsOldRejectedAndBlockedCallsBeyondFiveHundredRecentCalls() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALL_LOG)
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        val provider = Robolectric.buildContentProvider(BlockedCallsProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("call_log", provider)
        fun add(id: Long, type: Int) {
            provider.insert(CallLog.Calls.CONTENT_URI, ContentValues().apply {
                put("_id", id); put("number", "5512345678"); put("type", type); put("date", id)
            })
        }
        add(1, CallLog.Calls.BLOCKED_TYPE)
        add(2, CallLog.Calls.REJECTED_TYPE)
        for (id in 3L..603L) add(id, CallLog.Calls.INCOMING_TYPE)
        val recent = PhoneData.loadCalls(context)
        assertEquals(500, recent.size)
        assertTrue(recent.none { it.isBlockedLike })
        val blocked = PhoneData.loadCalls(context, blockedOnly = true)
        assertEquals(listOf(2L, 1L), blocked.map { it.id })
        assertTrue(blocked.all { it.isBlockedLike })
        assertEquals(listOf(1L), PhoneData.loadCalls(context, onlyIds = setOf(1, 603), blockedOnly = true).map { it.id })
    }
}

class BlockedCallsProvider : ContentProvider() {
    private val db = SQLiteDatabase.create(null).apply {
        execSQL("CREATE TABLE calls(_id INTEGER PRIMARY KEY, number TEXT, name TEXT, type INTEGER, date INTEGER, duration INTEGER DEFAULT 0)")
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/calls"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) =
        db.query("calls", projection, selection, args, null, null, sort)
    override fun insert(uri: Uri, values: ContentValues?): Uri? { db.insertOrThrow("calls", null, values); return uri }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = db.delete("calls", selection, args)
}
