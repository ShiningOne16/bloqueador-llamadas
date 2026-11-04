package com.ejemplo.bloqueador

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Confirmaciones de multipartes duraderas: los callbacks sobreviven al cierre de la interfaz. */
class SmsJournal(context: Context) : SQLiteOpenHelper(context, "sms_sending.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sends(token TEXT PRIMARY KEY, uri TEXT NOT NULL, parts INTEGER NOT NULL, created INTEGER NOT NULL, state TEXT NOT NULL)")
        db.execSQL("CREATE TABLE results(token TEXT NOT NULL, part INTEGER NOT NULL, result INTEGER NOT NULL, PRIMARY KEY(token,part))")
        db.execSQL("CREATE TABLE incoming(receipt TEXT PRIMARY KEY, uri TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS incoming(receipt TEXT PRIMARY KEY, uri TEXT NOT NULL)")
    }

    fun received(receipt: String): Boolean = readableDatabase.query("incoming", arrayOf("receipt"), "receipt=?", arrayOf(receipt), null, null, null).use { it.moveToFirst() }
    fun recordIncoming(receipt: String, uri: String) {
        writableDatabase.insertWithOnConflict("incoming", null, ContentValues().apply { put("receipt", receipt); put("uri", uri) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun completed(): List<Pair<String, String>> = readableDatabase.query("sends", arrayOf("uri", "state"), "state!=?", arrayOf("pending"), null, null, null).use {
        buildList { while (it.moveToNext()) add(it.getString(0) to it.getString(1)) }
    }

    fun create(token: String, uri: String, parts: Int) {
        writableDatabase.insertOrThrow("sends", null, ContentValues().apply {
            put("token", token); put("uri", uri); put("parts", parts)
            put("created", System.currentTimeMillis()); put("state", "pending")
        })
    }

    /** Devuelve el URI y el estado agregado. Los callbacks repetidos son idempotentes. */
    fun record(token: String, part: Int, result: Int): Pair<String, String>? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var uri: String? = null
            var count = 0
            db.query("sends", arrayOf("uri", "parts"), "token=?", arrayOf(token), null, null, null).use {
                if (it.moveToFirst()) { uri = it.getString(0); count = it.getInt(1) }
            }
            if (uri == null || part !in 0 until count) return null
            db.insertWithOnConflict("results", null, ContentValues().apply {
                put("token", token); put("part", part); put("result", result)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            val results = mutableMapOf<Int, Int>()
            db.query("results", arrayOf("part", "result"), "token=?", arrayOf(token), null, null, null).use {
                while (it.moveToNext()) results[it.getInt(0)] = it.getInt(1)
            }
            val state = SmsPolicy.sendState(count, results, android.app.Activity.RESULT_OK)
            db.update("sends", ContentValues().apply { put("state", state) }, "token=?", arrayOf(token))
            db.setTransactionSuccessful()
            return uri!! to state
        } finally { db.endTransaction() }
    }

    fun stale(): List<String> {
        val cutoff = System.currentTimeMillis() - 10 * 60_000L
        return readableDatabase.query("sends", arrayOf("uri"), "state=? AND created<?", arrayOf("pending", cutoff.toString()), null, null, null).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
    }
}
