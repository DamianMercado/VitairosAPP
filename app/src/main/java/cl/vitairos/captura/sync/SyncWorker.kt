package cl.vitairos.captura.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cl.vitairos.captura.VitairosApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Respaldo con WorkManager: cuando hay reportes en cola, se programa un trabajo que se ejecuta
 * al existir red (incluso si la app fue cerrada) y procesa la cola con el mismo SyncManager.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as VitairosApp).container
        // En arranque en frío (app cerrada) se espera a que carguen las preferencias y la conectividad.
        container.settings.first { it.loaded }
        withTimeoutOrNull(10_000) { container.online.first { it } } ?: return Result.retry()
        container.syncManager.processQueue(waitForLock = true)
        val pendingNow = container.repository.queued().any { it.nextRetryAt == null }
        return if (pendingNow) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "vitairos-sync-queue"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
