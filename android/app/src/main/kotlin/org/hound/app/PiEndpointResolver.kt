package org.hound.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class PiEndpoint(
    val host: String,
    val port: Int
)

class PiEndpointResolver(
    private val controlPort: Int = 8766
) {
    suspend fun resolve(context: Context): PiEndpoint? = withContext(Dispatchers.IO) {
        val candidates = buildCandidateHosts(context).distinct()
        for (host in candidates) {
            if (canConnect(host, controlPort)) {
                return@withContext PiEndpoint(host = host, port = controlPort)
            }
        }
        null
    }

    private fun buildCandidateHosts(context: Context): List<String> {
        val hosts = mutableListOf<String>()
        hosts += "192.168.4.1"

        val gateway = getGatewayAddress(context)
        if (gateway != null) {
            hosts += gateway
        }

        val wifiIp = getWifiAddress(context)
        if (wifiIp != null) {
            val parts = wifiIp.split(".")
            if (parts.size == 4) {
                val prefix = parts.take(3).joinToString(".")
                hosts += "$prefix.1"
                hosts += "$prefix.2"
                hosts += "$prefix.10"
                hosts += "$prefix.100"
            }
        }

        hosts += "192.168.1.1"
        hosts += "192.168.0.1"
        return hosts
    }

    private fun getWifiAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ip = wifiManager?.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    }

    private fun getGatewayAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return null
        }
        return null
    }

    private fun canConnect(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName(host), port), 400)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
