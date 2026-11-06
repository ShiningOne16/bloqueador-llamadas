package com.ejemplo.bloqueador

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SpamReportesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val numero = "5512345678"

    private class Connection(url: URL, val status: Int) : HttpURLConnection(url) {
        val body = ByteArrayOutputStream()
        override fun getOutputStream() = body
        override fun getResponseCode() = status
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
    }

    @Test fun explicitReportSendsOnlyTheSelectedNumberCategoryAndInstallation() {
        val connection = Connection(URL("https://test.invalid/reportar"), 200)
        assertNull(SpamComunidad.reportar(context, numero, CategoriaSpam.ESTAFA) { connection })
        val json = JSONObject(connection.body.toString("UTF-8"))
        assertEquals(setOf("numero", "categoria", "instalacion"), json.keys().asSequence().toSet())
        assertEquals(numero, json.getString("numero"))
        assertEquals("estafa", json.getString("categoria"))
        assertTrue(SpamStore(context).reportado(numero))
        assertTrue(Prefs(context).manuallyBlocked(numero))
        assertEquals("POST", connection.requestMethod)
    }

    @Test fun failedReportStillBlocksLocallyAndAllowsRetry() {
        val connection = Connection(URL("https://test.invalid/reportar"), 503)
        assertNotNull(SpamComunidad.reportar(context, numero, CategoriaSpam.OTRO) { connection })
        assertTrue(Prefs(context).manuallyBlocked(numero))
        assertTrue(SpamStore(context).tieneReporteLocal(numero))
        assertFalse(SpamStore(context).reportado(numero))
        assertNull(SpamComunidad.reportar(context, numero, CategoriaSpam.OTRO) { Connection(it, 200) })
        assertTrue(SpamStore(context).reportado(numero))
    }

    @Test fun hiddenNumbersNeverReachTheConnectionFactory() {
        SesionEntrada.entrarFalso(listOf(HiddenContact("Privado", listOf(numero))))
        try {
            assertNotNull(SpamComunidad.reportar(context, numero, CategoriaSpam.ESTAFA) { error("No debe abrir conexión") })
            assertFalse(Prefs(context).manuallyBlocked(numero))
        } finally { SesionEntrada.abrirNormal() }
    }

    @Test fun unreadablePrivateListPreventsExport() {
        context.getSharedPreferences("hidden_contacts", 0).edit().putString("data", "no-descifrable").commit()
        assertNotNull(SpamComunidad.reportar(context, numero, CategoriaSpam.ESTAFA) { error("No debe abrir conexión") })
        assertFalse(SpamStore(context).reportado(numero))
    }

    @Test fun foreignNumbersAreNeverUploadedOrBlockedByReporting() {
        assertNotNull(SpamComunidad.reportar(context, "+345512345678", CategoriaSpam.ESTAFA) { error("No debe abrir conexión") })
        assertFalse(Prefs(context).manuallyBlocked("+345512345678"))
    }

    @Test fun correctionIsImmediateEvenIfWithdrawalFailsAndCanBeRetried() {
        val store = SpamStore(context)
        store.marcarReportado(numero)
        store.marcarReporteLocal(numero)
        Prefs(context).setManualBlock(numero, true)
        SpamComunidad.noEsSpamLocal(context, numero)
        assertTrue(store.permitido(numero))
        assertFalse(Prefs(context).manuallyBlocked(numero))
        assertFalse(store.tieneReporteLocal(numero))
        assertNotNull(SpamComunidad.retirar(context, numero) { Connection(it, 503) })
        assertTrue(store.reportado(numero))
        assertTrue(store.permitido(numero))
        val connection = Connection(URL("https://test.invalid/retirar"), 200)
        assertNull(SpamComunidad.retirar(context, numero) { connection })
        assertFalse(store.reportado(numero))
        assertTrue(store.permitido(numero))
        val json = JSONObject(connection.body.toString("UTF-8"))
        assertEquals(setOf("numero", "instalacion"), json.keys().asSequence().toSet())
    }

    @Test fun choosingNoSpamWithoutAReportDoesNotContactTheServer() {
        SpamComunidad.noEsSpamLocal(context, numero)
        assertNull(SpamComunidad.retirar(context, numero) { error("No hay voto que retirar") })
    }

    @Test fun lostReportResponseCanBeWithdrawnAfterReopeningTheStore() {
        val lostResponse = object : HttpURLConnection(URL("https://test.invalid/reportar")) {
            val body = ByteArrayOutputStream()
            override fun getOutputStream() = body
            override fun getResponseCode(): Int = throw SocketTimeoutException("El servidor guardó el voto, pero se perdió la respuesta")
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun connect() = Unit
        }
        assertNotNull(SpamComunidad.reportar(context, numero, CategoriaSpam.ESTAFA) { lostResponse })
        val sent = JSONObject(lostResponse.body.toString("UTF-8"))
        assertFalse(SpamStore(context).reportado(numero))

        SpamComunidad.noEsSpamLocal(context, numero)
        assertFalse(SpamStore(context).tieneReporteLocal(numero))
        assertTrue(SpamStore(context).reportePorRetirar(numero))
        assertNotNull(SpamComunidad.retirar(context, numero) { Connection(it, 503) })
        assertTrue(SpamStore(context).reportePorRetirar(numero))
        assertTrue(SpamStore(context).permitido(numero))
        assertFalse(Prefs(context).manuallyBlocked(numero))

        val withdrawal = Connection(URL("https://test.invalid/retirar"), 200)
        assertNull(SpamComunidad.retirar(context, numero) { withdrawal })
        assertEquals(sent.getString("instalacion"), JSONObject(withdrawal.body.toString("UTF-8")).getString("instalacion"))
        assertFalse(SpamStore(context).reportePorRetirar(numero))
        assertNull(SpamComunidad.retirar(context, numero) { error("El voto ya se retiró") })
    }

    @Test fun legacyFailedReportCanBeWithdrawnAfterLocalCorrection() {
        // Las versiones anteriores no guardaban pending_reports.
        SpamStore(context).marcarReporteLocal(numero)
        SpamComunidad.noEsSpamLocal(context, numero)
        assertTrue(SpamStore(context).reportePorRetirar(numero))
        assertNull(SpamComunidad.retirar(context, numero) { Connection(it, 200) })
        assertFalse(SpamStore(context).reportePorRetirar(numero))
    }
}
