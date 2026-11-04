package com.ejemplo.bloqueador

import android.content.Context

/** Archivo local de Bloqueador. Los registros originales siguen en los proveedores del sistema. */
class ArchiveStore(context: Context) {
    private val prefs = context.getSharedPreferences("history_archive", Context.MODE_PRIVATE)
    fun read() = ArchiveState(prefs.getStringSet("calls", emptySet()).orEmpty().toSet(),
        prefs.getStringSet("sms", emptySet()).orEmpty().toSet())
    fun save(state: ArchiveState) {
        prefs.edit().putStringSet("calls", state.calls).putStringSet("sms", state.sms).apply()
    }
}

fun CallEntry.archiveKey() = ArchiveState.callKey(id, date)
fun SmsThread.archiveKey() = ArchiveState.threadKey(threadId, last.id, last.date, address)
