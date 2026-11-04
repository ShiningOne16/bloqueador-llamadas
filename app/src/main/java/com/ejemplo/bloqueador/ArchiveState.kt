package com.ejemplo.bloqueador

/** Identidades con fecha: reutilizar un ID no debe ocultar una llamada o conversación nueva. */
data class ArchiveState(val calls: Set<String> = emptySet(), val sms: Set<String> = emptySet()) {
    fun archiveCalls(keys: Collection<String>) = copy(calls = calls + keys)
    fun restoreCalls(keys: Collection<String>) = copy(calls = calls - keys.toSet())
    fun archiveThread(id: Long, key: String) = copy(sms = sms.filterNot { it.startsWith("$id:") }.toSet() + key)
    fun restoreThread(id: Long) = copy(sms = sms.filterNot { it.startsWith("$id:") }.toSet())
    fun refreshThread(id: Long, latestKey: String): ArchiveState =
        if (sms.any { it.startsWith("$id:") } && latestKey !in sms) restoreThread(id) else this

    companion object {
        fun callKey(id: Long, date: Long) = "$id:$date"
        fun threadKey(id: Long, lastId: Long, date: Long, address: String) = "$id:$lastId:$date:$address"
    }
}
