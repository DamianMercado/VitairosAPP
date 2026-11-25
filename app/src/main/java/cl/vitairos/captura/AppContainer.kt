package cl.vitairos.captura

import android.content.Context
import cl.vitairos.captura.data.files.AttachmentStorage
import cl.vitairos.captura.data.local.AppDatabase
import cl.vitairos.captura.data.model.DemoUser
import cl.vitairos.captura.data.model.DemoUsers
import cl.vitairos.captura.data.prefs.AppSettings
import cl.vitairos.captura.data.prefs.SettingsStore
import cl.vitairos.captura.data.remote.MockApiInterceptor
import cl.vitairos.captura.data.remote.MockServerState
import cl.vitairos.captura.data.remote.VitairosApi
import cl.vitairos.captura.data.repo.ReportRepository
import cl.vitairos.captura.sync.AppEvents
import cl.vitairos.captura.sync.ConnectivityMonitor
import cl.vitairos.captura.sync.SyncManager
import cl.vitairos.captura.sync.SyncWorker
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: DemoUser) : SessionState
}

/**
 * Contenedor de dependencias manual (sin frameworks de DI para mantener el MVP simple).
 * Capas: UI (Compose + ViewModel) → Repositorio / SyncManager → Room · DataStore · Archivos · API mock.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Ámbito de vida de la app. Un error en segundo plano se registra (sin datos sensibles) y no cierra la app. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> android.util.Log.e("VitairosApp", "Error en segundo plano: ${e.javaClass.simpleName}") }
    )
    val gson = Gson()
    val events = AppEvents()

    val settingsStore = SettingsStore(appContext)
    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val connectivity = ConnectivityMonitor(appContext)

    /** Conectividad efectiva = red real AND NOT "simular sin conexión". */
    val online: StateFlow<Boolean> =
        combine(connectivity.isNetworkAvailable, settings) { net, s -> s.loaded && net && !s.simulateOffline }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    val session: StateFlow<SessionState> = settings
        .map { s ->
            when {
                !s.loaded -> SessionState.Loading
                else -> DemoUsers.byId(s.userId)?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut
            }
        }
        .stateIn(appScope, SharingStarted.Eagerly, SessionState.Loading)

    fun currentUser(): DemoUser? = (session.value as? SessionState.LoggedIn)?.user

    val database: AppDatabase = AppDatabase.build(appContext)
    val storage = AttachmentStorage(appContext)
    val repository = ReportRepository(database, storage, settingsStore)

    private val mockServer = MockServerState(appContext)

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(
            MockApiInterceptor(
                settings = { settings.value },
                networkAvailable = { connectivity.isNetworkAvailable.value },
                server = mockServer,
                gson = gson,
            )
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: VitairosApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(VitairosApi::class.java)

    val syncManager = SyncManager(
        repo = repository,
        api = api,
        gson = gson,
        online = online,
        events = events,
        scope = appScope,
        scheduleBackground = { SyncWorker.schedule(appContext) },
    )
}
