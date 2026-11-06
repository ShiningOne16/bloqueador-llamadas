package com.ejemplo.bloqueador

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class SpamCallSource(val label: String) {
    ARCHIVED("archivadas"), BLOCKED("bloqueadas");

    fun contains(call: CallEntry, archivedKeys: Set<String>): Boolean = when (this) {
        ARCHIVED -> call.archiveKey() in archivedKeys
        BLOCKED -> call.isBlockedLike && call.archiveKey() !in archivedKeys
    }
}

data class ArchivedSpamPlan(val numbers: List<String>, val omitted: Int)
data class ArchivedSpamResult(
    val blocked: Int, val sent: Int, val alreadyReported: Int,
    val pending: Int, val omitted: Int, val error: String?
)

/** Solo los números revisados en la confirmación; nunca transmite registros, nombres ni fechas. */
object ArchivedSpam {
    fun prepare(context: Context, calls: List<CallEntry>, archivedKeys: Set<String>,
        source: SpamCallSource = SpamCallSource.ARCHIVED): ArchivedSpamPlan {
        // Un fallo de descifrado impide preparar cualquier envío, igual que en el reporte individual.
        val hidden = HiddenStore(context).contactsForReporting() + SesionEntrada.aOcultar()
        val store = SpamStore(context)
        val numbers = linkedSetOf<String>()
        var omitted = 0
        for (call in calls) {
            if (!source.contains(call, archivedKeys)) continue
            if (!SpamRules.reportable(call.number) || HiddenRules.matchesSender(hidden, call.number) || store.permitido(call.number)) {
                omitted++
                continue
            }
            numbers += PremiumRules.numberKey(call.number)
        }
        return ArchivedSpamPlan(numbers.toList(), omitted)
    }

    suspend fun report(context: Context, plan: ArchivedSpamPlan, category: CategoriaSpam,
        connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): ArchivedSpamResult = withContext(Dispatchers.IO) {
        val store = SpamStore(context)
        val eligible = mutableListOf<String>()
        var omitted = plan.omitted
        for (number in plan.numbers) {
            currentCoroutineContext().ensureActive()
            if (store.permitido(number) || SpamComunidad.prepararReporteLocal(context, number) != null) omitted++
            else eligible += number
        }
        // El bloqueo local se completa aunque el servidor limite o falle el primer envío.
        val alreadyReported = eligible.count { store.reportado(it) }
        var sent = 0
        var skipped = 0
        var error: String? = null
        for (number in eligible.filterNot { store.reportado(it) }) {
            currentCoroutineContext().ensureActive()
            // Revisa de nuevo: la privacidad y las correcciones pueden cambiar durante el proceso.
            if (store.permitido(number) || SpamComunidad.errorReporte(context, number) != null) {
                omitted++; skipped++
                continue
            }
            error = SpamComunidad.reportar(context, number, category, connect)
            if (error != null) break // Incluye 429: no consume el límite con el resto de los números.
            sent++
        }
        ArchivedSpamResult(eligible.size, sent, alreadyReported,
            eligible.size - alreadyReported - sent - skipped, omitted, error)
    }
}
