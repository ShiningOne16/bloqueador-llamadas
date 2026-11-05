package com.ejemplo.bloqueador

object SmsSearch {
    fun matchesText(text: String, query: String): Boolean =
        simplificar(text).contains(simplificar(query.trim()))

    fun matchesSender(name: String?, address: String, query: String): Boolean {
        if (matchesText(name.orEmpty(), query) || matchesText(address, query)) return true
        val trimmed = query.trim()
        val digits = trimmed.filter(Char::isDigit)
        return digits.isNotEmpty() && esNumeroTelefono(trimmed) &&
            address.filter(Char::isDigit).contains(digits)
    }
}
