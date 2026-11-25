package cl.vitairos.captura.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vitairos_prefs")

/** Comportamiento de la API simulada, configurable desde Ajustes para demostrar errores y reintentos. */
enum class MockApiMode(val label: String, val description: String) {
    NORMAL("Normal", "La API mock responde OK."),
    INTERMITENTE("Intermitente", "≈50 % de los envíos fallan (HTTP 503)."),
    SIEMPRE_FALLA("Siempre falla", "Todos los envíos fallan (HTTP 500)."),
}

data class AppSettings(
    val loaded: Boolean = false,
    val userId: String? = null,
    val simulateOffline: Boolean = false,
    val mockApiMode: MockApiMode = MockApiMode.NORMAL,
)

/**
 * Preferencias simples (DataStore). La sesión guarda SOLO el id del usuario ficticio:
 * nunca contraseñas ni tokens.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("session_user_id")
        val INSTALL_ID = stringPreferencesKey("install_id")
        val SIMULATE_OFFLINE = booleanPreferencesKey("simulate_offline")
        val MOCK_MODE = stringPreferencesKey("mock_api_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            loaded = true,
            userId = p[Keys.USER_ID],
            simulateOffline = p[Keys.SIMULATE_OFFLINE] ?: false,
            mockApiMode = p[Keys.MOCK_MODE]?.let { runCatching { MockApiMode.valueOf(it) }.getOrNull() }
                ?: MockApiMode.NORMAL,
        )
    }

    suspend fun setUser(userId: String?) {
        context.dataStore.edit { p ->
            if (userId == null) p.remove(Keys.USER_ID) else p[Keys.USER_ID] = userId
        }
    }

    suspend fun setSimulateOffline(value: Boolean) {
        context.dataStore.edit { it[Keys.SIMULATE_OFFLINE] = value }
    }

    suspend fun setMockApiMode(mode: MockApiMode) {
        context.dataStore.edit { it[Keys.MOCK_MODE] = mode.name }
    }

    /** Identificador técnico aleatorio de la instalación (no es IMEI ni dato personal). */
    suspend fun installId(): String {
        context.dataStore.data.first()[Keys.INSTALL_ID]?.let { return it }
        var id = ""
        context.dataStore.edit { p ->
            id = p[Keys.INSTALL_ID] ?: ("INST-" + UUID.randomUUID().toString().take(8).uppercase())
            p[Keys.INSTALL_ID] = id
        }
        return id
    }
}
