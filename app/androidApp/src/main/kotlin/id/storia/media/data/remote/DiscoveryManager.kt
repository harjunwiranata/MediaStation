package id.storia.media.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DiscoveryManager(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredServers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<ServerInfo>> = _discoveredServers

    private val serviceCallbacks = mutableMapOf<String, Any>()
    private val resolveQueue = mutableListOf<NsdServiceInfo>()
    private var isResolving = false

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private inner class TiramisuServiceInfoCallback(
        private val serviceName: String
    ) : NsdManager.ServiceInfoCallback {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            Log.e("DiscoveryManager", "Callback registration failed: $errorCode")
        }

        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            updateServerList(serviceInfo)
        }

        override fun onServiceLost() {
            Log.d("DiscoveryManager", "Service lost (callback): $serviceName")
        }

        override fun onServiceInfoCallbackUnregistered() {}
    }

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerCallback(service: NsdServiceInfo) {
        val callback = TiramisuServiceInfoCallback(service.serviceName)
        serviceCallbacks[service.serviceName] = callback
        nsdManager.registerServiceInfoCallback(service, context.mainExecutor, callback)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun unregisterCallback(serviceName: String) {
        val callback = serviceCallbacks.remove(serviceName) as? NsdManager.ServiceInfoCallback
        callback?.let { nsdManager.unregisterServiceInfoCallback(it) }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d("DiscoveryManager", "Discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d("DiscoveryManager", "Service found: ${service.serviceName}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                @Suppress("NewApi")
                registerCallback(service)
            } else {
                synchronized(resolveQueue) {
                    resolveQueue.add(service)
                    processNextResolve()
                }
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d("DiscoveryManager", "Service lost: ${service.serviceName}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                @Suppress("NewApi")
                unregisterCallback(service.serviceName)
            }
            // Remove from list
            _discoveredServers.value = _discoveredServers.value.filterNot { it.name == service.serviceName }
        }

        override fun onDiscoveryStopped(regType: String) {
            Log.d("DiscoveryManager", "Discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("DiscoveryManager", "Start discovery failed: $errorCode")
            stopDiscovery()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("DiscoveryManager", "Stop discovery failed: $errorCode")
        }
    }

    private fun processNextResolve() {
        synchronized(resolveQueue) {
            if (isResolving || resolveQueue.isEmpty()) return
            isResolving = true
            val service = resolveQueue.removeAt(0)
            
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e("DiscoveryManager", "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    synchronized(resolveQueue) {
                        isResolving = false
                        processNextResolve()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    updateServerList(serviceInfo)
                    synchronized(resolveQueue) {
                        isResolving = false
                        processNextResolve()
                    }
                }
            })
        }
    }

    fun startDiscovery() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("MediaStationDiscovery").apply {
                setReferenceCounted(true)
                acquire()
            }
        }
        nsdManager.discoverServices("_mediastation._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val names = serviceCallbacks.keys.toList()
                @Suppress("NewApi")
                names.forEach { unregisterCallback(it) }
            }
            synchronized(resolveQueue) {
                resolveQueue.clear()
                isResolving = false
            }
            multicastLock?.let {
                if (it.isHeld) it.release()
                multicastLock = null
            }
        } catch (e: Exception) {
            Log.e("DiscoveryManager", "Error stopping discovery", e)
        }
    }

    private fun updateServerList(serviceInfo: NsdServiceInfo) {
        val host = serviceInfo.host?.hostAddress ?: return
        val port = serviceInfo.port
        val name = serviceInfo.serviceName

        val currentList = _discoveredServers.value
        if (currentList.none { it.host == host && it.port == port }) {
            _discoveredServers.value = currentList + ServerInfo(name, host, port)
        }
    }

    data class ServerInfo(val name: String, val host: String, val port: Int)
}
