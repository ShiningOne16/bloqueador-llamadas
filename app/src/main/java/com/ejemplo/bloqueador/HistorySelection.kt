package com.ejemplo.bloqueador

/** Solo una acción explícita añade elementos; una actualización de datos nunca selecciona registros nuevos. */
object HistorySelection {
    fun key(id: Long, date: Long) = "$id:$date"
    fun toggle(selected: List<String>, group: Collection<String>): List<String> =
        if (group.isNotEmpty() && selected.containsAll(group)) selected.filterNot { it in group }
        else (selected + group).distinct()
    fun prune(selected: List<String>, available: Collection<String>) = selected.filter { it in available }
}

/** Corte de borrado tomado al seleccionar, sin guardar el texto de las conversaciones en el estado de UI. */
data class SmsDeletionTarget(val threadId: Long, val maxId: Long, val maxDate: Long) : java.io.Serializable
