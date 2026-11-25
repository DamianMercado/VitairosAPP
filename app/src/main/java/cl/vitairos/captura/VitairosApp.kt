package cl.vitairos.captura

import android.app.Application

class VitairosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Reanuda envíos interrumpidos y escucha la conectividad para sincronizar automáticamente.
        container.syncManager.start()
    }
}
