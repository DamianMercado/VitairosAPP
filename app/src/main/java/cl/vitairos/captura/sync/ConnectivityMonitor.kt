package cl.vitairos.captura.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** Observa la conectividad REAL del dispositivo (Wi-Fi / datos). */
class ConnectivityMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val networks: MutableSet<Network> = Collections.synchronizedSet(mutableSetOf())
    private val _available = MutableStateFlow(initialState())
    val isNetworkAvailable: StateFlow<Boolean> = _available.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networks.add(network)
                    _available.value = true
                }

                override fun onLost(network: Network) {
                    networks.remove(network)
                    _available.value = networks.isNotEmpty()
                }
            })
        } catch (e: Exception) {
            // Si no se puede registrar el callback, se mantiene el estado inicial.
        }
    }

    private fun initialState(): Boolean = try {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        false
    }
}
