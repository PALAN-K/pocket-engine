package kr.co.palank.pocketserver.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

data class NetworkState(
    val isWifiConnected: Boolean = false,
    val ipAddress: String? = null
)

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (networkCallback != null) return

        updateIpAddress()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "WiFi available")
                updateIpAddress()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "WiFi lost")
                _state.value = NetworkState(isWifiConnected = false, ipAddress = null)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                updateIpAddress()
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                updateIpAddress()
            }
        }

        networkCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)
        Log.i(TAG, "Network monitoring started")
    }

    fun stop() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
        Log.i(TAG, "Network monitoring stopped")
    }

    private fun updateIpAddress() {
        val ip = getWifiIpAddress()
        val previous = _state.value.ipAddress
        _state.value = NetworkState(isWifiConnected = ip != null, ipAddress = ip)
        if (ip != null && previous != null && ip != previous) {
            Log.i(TAG, "IP changed: $previous -> $ip")
        } else {
            Log.d(TAG, "IP updated: $ip")
        }
    }

    private fun getWifiIpAddress(): String? {
        val network = connectivityManager.activeNetwork ?: return null
        val linkProps = connectivityManager.getLinkProperties(network) ?: return null
        return linkProps.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
