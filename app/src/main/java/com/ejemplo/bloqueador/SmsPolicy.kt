package com.ejemplo.bloqueador

/** Un destinatario SMS, incluidos códigos cortos; nunca números de servicio ni listas de destinatarios. */
object SmsPolicy {
    fun recipient(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.any { it !in "0123456789+ ()-." }) return null
        val compact = trimmed.filterNot { it in " ()-." }
        val digits = compact.removePrefix("+")
        return compact.takeIf { digits.length in 2..20 && digits.all { it in '0'..'9' } }
    }

    /** Todos los segmentos deben confirmar el envío. Un fallo no se oculta tras un éxito posterior. */
    fun sendState(expected: Int, results: Map<Int, Int>, success: Int): String = when {
        results.values.any { it != success } -> "failed"
        results.size == expected && expected > 0 -> "sent"
        else -> "pending"
    }
}

data class SmsOpenRequest(val token: String, val address: String = "", val body: String = "", val threadId: Long? = null, val compose: Boolean = true)
