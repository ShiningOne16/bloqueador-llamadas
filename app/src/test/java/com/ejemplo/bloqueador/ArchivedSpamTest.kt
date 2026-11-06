package com.ejemplo.bloqueador

import android.provider.CallLog
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ArchivedSpamTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val first = "5512345678"
    private val second = "5598765432"
    private val third = "5511112222"

    private fun call(id: Long, number: String, type: Int = CallLog.Calls.INCOMING_TYPE) = CallEntry(id, number, "Nombre local", type, id, 10)
    private fun plan(vararg numbers: String): ArchivedSpamPlan {
        val calls = numbers.mapIndexed { index, number -> call(index.toLong(), number) }
        return ArchivedSpam.prepare(context, calls, calls.map { it.archiveKey() }.toSet())
    }
    private class Connection(url: URL, private val status: Int) : HttpURLConnection(url) {
        val body = ByteArrayOutputStream()
        override fun getOutputStream() = body
        override fun getResponseCode() = status
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
    }

    @Test fun preparationUsesOnlyArchivedMexicanNumbersAndRespectsPrivacyAndNoSpam() {
        val allowed = "5522223333"
        SpamStore(context).permitir(allowed, true)
        val calls = listOf(call(1, first), call(2, "+525512345678"), call(3, second),
            call(4, allowed), call(5, "+345512345678"), call(6, ""), call(7, third))
        SesionEntrada.entrarFalso(listOf(HiddenContact("Oculto", listOf(second))))
        try {
            val result = ArchivedSpam.prepare(context, calls, calls.dropLast(1).map { it.archiveKey() }.toSet())
            assertEquals(listOf(first), result.numbers)
            assertEquals(4, result.omitted)
            assertFalse(Prefs(context).manuallyBlocked(first)) // La revisión no bloquea ni envía.
            assertTrue(SpamStore(context).permitido(allowed))
        } finally { SesionEntrada.abrirNormal() }
    }

    @Test fun unreadableHiddenListPreventsPreparingAnyReport() {
        context.getSharedPreferences("hidden_contacts", 0).edit().putString("data", "ilegible").commit()
        try { plan(first); fail("Debe impedir preparar el envío")
        } catch (_: IllegalStateException) { }
        assertFalse(Prefs(context).manuallyBlocked(first))
    }

    @Test fun blockedPreparationIncludesOnlyItsFilterAndKeepsPrivacyAndNoSpam() {
        val allowed = "5522223333"
        val hidden = "5533334444"
        SpamStore(context).permitir(allowed, true)
        val calls = listOf(
            call(1, first, CallLog.Calls.BLOCKED_TYPE),
            call(2, "+525512345678", CallLog.Calls.REJECTED_TYPE),
            call(3, third), call(4, third, CallLog.Calls.MISSED_TYPE),
            call(5, third, CallLog.Calls.OUTGOING_TYPE),
            call(6, second, CallLog.Calls.BLOCKED_TYPE),
            call(7, allowed, CallLog.Calls.REJECTED_TYPE),
            call(8, hidden, CallLog.Calls.BLOCKED_TYPE),
            call(9, "", CallLog.Calls.BLOCKED_TYPE),
            call(10, "+345512345678", CallLog.Calls.REJECTED_TYPE)
        )
        SesionEntrada.entrarFalso(listOf(HiddenContact("Oculto", listOf(hidden))))
        try {
            val result = ArchivedSpam.prepare(context, calls, setOf(calls[5].archiveKey()), SpamCallSource.BLOCKED)
            assertEquals(listOf(first), result.numbers)
            assertEquals(4, result.omitted)
            assertFalse(Prefs(context).manuallyBlocked(first))
            assertTrue(SpamStore(context).permitido(allowed))
        } finally { SesionEntrada.abrirNormal() }
    }

    @Test fun sendsIndividualReportsWithoutNamesHistoryOrDuplicateNumbersAndKeepsArchives() = runBlocking {
        val calls = listOf(call(1, first), call(2, "+525512345678"), call(3, second))
        val archive = ArchiveState(calls = calls.map { it.archiveKey() }.toSet())
        ArchiveStore(context).save(archive)
        val selected = ArchivedSpam.prepare(context, calls, archive.calls)
        val connections = mutableListOf<Connection>()
        val result = ArchivedSpam.report(context, selected, CategoriaSpam.ESTAFA) {
            Connection(it, 200).also(connections::add)
        }
        assertEquals(2, result.blocked)
        assertEquals(2, result.sent)
        assertEquals(0, result.pending)
        assertEquals(2, connections.size)
        for (connection in connections) {
            val body = JSONObject(connection.body.toString("UTF-8"))
            assertEquals(setOf("numero", "categoria", "instalacion"), body.keys().asSequence().toSet())
            assertEquals("estafa", body.getString("categoria"))
            assertEquals("POST", connection.requestMethod)
            assertEquals("/reportar", connection.url.path)
        }
        assertEquals(archive, ArchiveStore(context).read())
        val retry = ArchivedSpam.report(context, selected, CategoriaSpam.ESTAFA) { error("No debe repetir un reporte confirmado") }
        assertEquals(0, retry.sent)
        assertEquals(2, retry.alreadyReported)
        assertEquals(0, retry.pending)
    }

    @Test fun rateLimitStopsRequestsButBlocksEveryEligibleNumberAndRetrySkipsSuccesses() = runBlocking {
        val selected = plan(first, second, third)
        var attempts = 0
        val result = ArchivedSpam.report(context, selected, CategoriaSpam.OTRO) {
            attempts++
            Connection(it, if (attempts == 1) 200 else 429)
        }
        assertEquals(2, attempts)
        assertEquals(3, result.blocked)
        assertEquals(1, result.sent)
        assertEquals(2, result.pending)
        assertNotNull(result.error)
        for (number in selected.numbers) assertTrue(Prefs(context).manuallyBlocked(number))
        attempts = 0
        val retried = ArchivedSpam.report(context, selected, CategoriaSpam.OTRO) { attempts++; Connection(it, 200) }
        assertEquals(2, attempts)
        assertEquals(2, retried.sent)
        assertEquals(1, retried.alreadyReported)
        assertEquals(0, retried.pending)
    }

    @Test fun networkFailureLeavesLocalBlocksAndDoesNotTryTheRemainingNumbers() = runBlocking {
        var attempts = 0
        val result = ArchivedSpam.report(context, plan(first, second), CategoriaSpam.COBRANZA) {
            attempts++; throw IOException("Sin conexión")
        }
        assertEquals(1, attempts)
        assertEquals(2, result.pending)
        assertEquals(0, result.sent)
        assertTrue(Prefs(context).manuallyBlocked(second))
        assertTrue(SpamStore(context).tieneReporteLocal(second))
    }

    @Test fun correctionsAndPrivacyAreRecheckedAfterConfirmation() = runBlocking {
        val selected = plan(first, second, third)
        SpamStore(context).permitir(third, true)
        var attempts = 0
        try {
            val result = ArchivedSpam.report(context, selected, CategoriaSpam.OTRO) {
                attempts++
                SesionEntrada.entrarFalso(listOf(HiddenContact("Ahora oculto", listOf(second))))
                Connection(it, 200)
            }
            assertEquals(1, attempts)
            assertEquals(2, result.omitted)
            assertTrue(SpamStore(context).permitido(third))
            assertFalse(Prefs(context).manuallyBlocked(third))
            assertFalse(SpamStore(context).reportado(second))
        } finally { SesionEntrada.abrirNormal() }
    }

    @Test fun stoppingDoesNotStartTheNextRequestAndConfirmedReportsRemainSaved() = runBlocking {
        val selected = plan(first, second)
        val cancellation = Job()
        var attempts = 0
        try {
            withContext(cancellation) {
                ArchivedSpam.report(context, selected, CategoriaSpam.OTRO) {
                    attempts++
                    cancellation.cancel()
                    Connection(it, 200)
                }
            }
            fail("Debe detener el proceso")
        } catch (_: CancellationException) { }
        assertEquals(1, attempts)
        assertTrue(SpamStore(context).reportado(first))
        assertFalse(SpamStore(context).reportado(second))
    }
}
