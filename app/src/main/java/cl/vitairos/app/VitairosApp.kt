package cl.vitairos.app

import android.app.Application
import cl.vitairos.app.data.ApiSimulada
import cl.vitairos.app.data.AppDatabase
import cl.vitairos.app.data.Fotos
import cl.vitairos.app.data.Preferencias
import cl.vitairos.app.data.Repositorio

/** Crea una sola vez la base de datos, las preferencias, el manejo de fotos y la API simulada. */
class VitairosApp : Application() {
    lateinit var prefs: Preferencias
        private set
    lateinit var fotos: Fotos
        private set
    lateinit var repo: Repositorio
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Preferencias(this)
        fotos = Fotos(this)
        val db = AppDatabase.crear(this)
        repo = Repositorio(db.reporteDao(), fotos, ApiSimulada(this, prefs))
    }
}
