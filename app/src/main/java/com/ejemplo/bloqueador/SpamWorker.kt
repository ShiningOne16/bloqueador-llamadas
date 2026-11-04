package com.ejemplo.bloqueador

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Descarga la lista pública de spam (un solo archivo) cuando hay red. */
class SpamListWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!SpamStore(applicationContext).activo || !SpamComunidad.configurado(applicationContext)) return Result.success()
        return if (SpamLista.actualizar(applicationContext)) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

object SpamProgramacion {
    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Una actualización al día mientras la función esté activa; si se apaga, se cancela. */
    fun programar(context: Context) {
        val trabajo = WorkManager.getInstance(context.applicationContext)
        if (SpamStore(context).activo && SpamComunidad.configurado(context)) {
            trabajo.enqueueUniquePeriodicWork(
                "spam-list-daily", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SpamListWorker>(24, TimeUnit.HOURS).setConstraints(conRed).build()
            )
        } else {
            trabajo.cancelUniqueWork("spam-list-daily")
        }
    }

    fun actualizarYa(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "spam-list-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SpamListWorker>().setConstraints(conRed).build()
        )
    }
}
