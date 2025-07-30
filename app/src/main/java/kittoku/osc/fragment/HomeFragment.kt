package kittoku.osc.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.checkPreferences
import kittoku.osc.preference.custom.HomeConnectorPreference
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.*
import androidx.preference.PreferenceManager
import android.Manifest
import android.content.pm.PackageManager

class HomeFragment : PreferenceFragmentCompat() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val preparationLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startVpnAction(ACTION_VPN_CONNECT)
        }

    private var lastServer: Server? = null
    private var monitorJob: Job? = null
    private var lastHost: String = ""
    private var lastPort: Int = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.home, rootKey)
        setupPreferences() // İlk bağlama
    }
    private fun setupPreferences() {
        (findPreference("SSL_PORT") as? kittoku.osc.preference.custom.SSLPortPreference)?.let {
            it.summary = preferenceManager.sharedPreferences!!.getString("SSL_PORT", "995")
        }
        (findPreference("SSL_VERSION") as? kittoku.osc.preference.custom.SSLVersionPreference)?.let {
            it.summary = preferenceManager.sharedPreferences!!.getString("SSL_VERSION", "TLSv1.3")
        }
        (findPreference("SSL_DO_VERIFY") as? kittoku.osc.preference.custom.SSLDoVerifyPreference)?.let {
            it.isChecked = preferenceManager.sharedPreferences!!.getBoolean("SSL_DO_VERIFY", false)
        }

        findPreference<Preference>("auto_test")?.apply {
            title = "Bagla"
            setOnPreferenceClickListener {
                Log.d("VPN_MONITOR", "Bağla butonuna basıldı")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        monitorJob?.cancel() // Gözetlemeyi durdur
                        monitorJob = null
                        Log.d("VPN_MONITOR", "Monitor durduruldu")
                        val cfg = configIndir() ?: run {
                            showToast("Config indirilemedi")
                            return@launch
                        }
                        val servers = sunuculariCoz(cfg)
                        if (servers.isEmpty()) {
                            showToast("Sunucu yok")
                            return@launch
                        }
                        val candidates995 = servers.filter { it.port == 995 }
                        val testList = if (candidates995.isNotEmpty()) candidates995 else servers
                        val best = pickFastest(testList) ?: run {
                            showToast("Çalışan sunucu yok")
                            return@launch
                        }
                        lastHost = best.first
                        lastPort = best.second
                        lastServer = Server(lastHost, lastPort)
                        aktarOpenSSTP(lastHost, lastPort.toString())
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                findPreference<HomeConnectorPreference>(OscPrefKey.HOME_CONNECTOR.name)?.updateView()
                                preferenceScreen.removeAll()
                                setPreferencesFromResource(R.xml.home, null)
                                setupPreferences() // Yeniden bağla
                                showToast("Bağlanıldı: $lastHost:$lastPort")
                            }
                        }
                        startVpnAction(ACTION_VPN_CONNECT)
                        startMonitor()
                    } catch (e: Exception) {
                        Log.e("VPN_MONITOR", "Bağla hata: ${e.message}", e)
                        withContext(Dispatchers.Main) { if (isAdded) showToast("Hata: ${e.message}") }
                    }
                }
                true
            }
        } ?: Log.e("VPN_MONITOR", "auto_test preference bulunamadı")

        findPreference<Preference>("refresh_server")?.apply {
            title = "Sunucu Yenile"
            setOnPreferenceClickListener {
                Log.d("VPN_MONITOR", "Sunucu Yenile butonuna basıldı")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        monitorJob?.cancel() // Gözetlemeyi durdur
                        monitorJob = null
                        Log.d("VPN_MONITOR", "Monitor durduruldu")
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                startVpnAction(ACTION_VPN_DISCONNECT)
                                showToast("VPN kapatılıyor")
                            }
                        }
                        delay(5000)
                        if (isVpnConnected()) {
                            Log.e("VPN_MONITOR", "VPN kapatılamadı")
                            withContext(Dispatchers.Main) { if (isAdded) showToast("VPN kapatılamadı") }
                            return@launch
                        }
                        val cfg = configIndir() ?: run {
                            Log.e("VPN_MONITOR", "Config indirilemedi")
                            withContext(Dispatchers.Main) { if (isAdded) showToast("Config indirilemedi") }
                            return@launch
                        }
                        val servers = sunuculariCoz(cfg)
                        Log.d("VPN_MONITOR", "Bulunan sunucu sayısı: ${servers.size}")
                        if (servers.isEmpty()) {
                            Log.e("VPN_MONITOR", "Hiç sunucu bulunamadı")
                            withContext(Dispatchers.Main) { if (isAdded) showToast("Sunucu yok") }
                            return@launch
                        }
                        val currentServer = lastServer ?: Server(lastHost, lastPort)
                        val candidates = servers.filter { it.port != 443 && it != currentServer }
                        Log.d("VPN_MONITOR", "Uygun sunucu sayısı: ${candidates.size}, hariç tutulan: $currentServer")
                        if (candidates.isEmpty()) {
                            Log.e("VPN_MONITOR", "Uygun sunucu yok")
                            withContext(Dispatchers.Main) { if (isAdded) showToast("Uygun sunucu yok") }
                            return@launch
                        }
                        val best = pickFastest(candidates, lastHost) ?: run {
                            Log.e("VPN_MONITOR", "Çalışan sunucu bulunamadı")
                            withContext(Dispatchers.Main) { if (isAdded) showToast("Çalışan yok") }
                            return@launch
                        }
                        lastHost = best.first
                        lastPort = best.second
                        lastServer = Server(lastHost, lastPort)
                        Log.d("VPN_MONITOR", "Yeni sunucu: $lastHost:$lastPort")
                        aktarOpenSSTP(lastHost, lastPort.toString())
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                findPreference<HomeConnectorPreference>(OscPrefKey.HOME_CONNECTOR.name)?.updateView()
                                preferenceScreen.removeAll()
                                setPreferencesFromResource(R.xml.home, null)
                                setupPreferences() // Yeniden bağla
                                showToast("Bağlanıldı: $lastHost:$lastPort")
                            }
                        }
                        startVpnAction(ACTION_VPN_CONNECT)
                        startMonitor()
                    } catch (e: Exception) {
                        Log.e("VPN_MONITOR", "Hata: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            if (isAdded) showToast("Hata: ${e.message}")
                        }
                    }
                }
                true
            }
        } ?: Log.e("VPN_MONITOR", "refresh_server preference bulunamadı")

        findPreference<Preference>("stop_all")?.apply {
            setOnPreferenceClickListener {
                lifecycleScope.launch {
                    // 1) İzlemeyi durdur
                    monitorJob?.cancel()
                    monitorJob = null
                    Log.d("VPN_MONITOR", "Monitor durduruldu")

                    // 2) VPN’i kapat
                    withContext(Dispatchers.Main) {
                        startVpnAction(ACTION_VPN_DISCONNECT)
                        showToast("VPN ve izleme kapatıldı")
                    }
                }
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        baglantiDugmesiniAyarla()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            requireActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
        }
    }

    private fun startVpnAction(action: String) {
        val intent = Intent(requireContext(), SstpVpnService::class.java).setAction(action)
        Log.d("HOME_FRAGMENT", "VPN başlatma çağrısı gönderildi")
        if (action == ACTION_VPN_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    private fun baglantiDugmesiniAyarla() {
        findPreference<HomeConnectorPreference>(OscPrefKey.HOME_CONNECTOR.name)!!.also {
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newState ->
                if (newState == true) {
                    checkPreferences(preferenceManager.sharedPreferences!!)?.also { mesaj ->
                        Toast.makeText(requireContext(), mesaj, Toast.LENGTH_LONG).show()
                        return@OnPreferenceChangeListener false
                    }
                    VpnService.prepare(requireContext())?.also { intent ->
                        preparationLauncher.launch(intent)
                    } ?: startVpnAction(ACTION_VPN_CONNECT)
                } else {
                    startVpnAction(ACTION_VPN_DISCONNECT)
                }
                true
            }
        }
    }

    private fun configIndir(): String? {
        return try {
            val url = "https://drive.google.com/uc?export=download&id=1IxQSQn5xoir4FVcqO0Lng4uIftracM-T"
            Log.d("CONFIG_INDIR", "Config URL: $url")
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { response ->
                Log.d("CONFIG_INDIR", "HTTP response code: ${response.code}")
                if (!response.isSuccessful) {
                    Log.e("CONFIG_INDIR", "HTTP error: ${response.code}")
                    throw Exception("HTTP ${response.code}")
                }
                response.body?.string() ?: run {
                    Log.e("CONFIG_INDIR", "Response body is null")
                    throw Exception("Response body is null")
                }
            }
        } catch (e: Exception) {
            Log.e("CONFIG_INDIR", "Config download failed: ${e.message}", e)
            null
        }
    }

    private fun sunuculariCoz(text: String): List<Server> {
        val hashMap = mapOf(
            "d." to ".",
            "z0" to "0", "o1" to "1", "t2" to "2", "th" to "3",
            "f4" to "4", "fv" to "5", "s6" to "6", "sv" to "7",
            "e8" to "8", "n9" to "9",
            "a1" to "A", "b2" to "B", "c3" to "C", "d4" to "D",
            "e5" to "E", "f6" to "F", "g7" to "G", "h8" to "H", "i9" to "I",
            "j0" to "J", "k1" to "K", "l2" to "L", "m3" to "M", "n4" to "N",
            "o5" to "O", "p6" to "P", "q7" to "Q", "r8" to "R", "s9" to "S",
            "t0" to "T", "u1" to "U", "v2" to "V", "w3" to "W", "x4" to "X",
            "y5" to "Y", "z6" to "Z"
        )

        fun decode(encoded: String): String {
            var out = encoded
            hashMap.entries
                .sortedByDescending { it.key.length }
                .forEach { (k, v) -> out = out.replace(k, v) }
            return out
        }

        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 3) return@mapNotNull null
                val hostRaw = parts[2]
                val portRaw = parts[3]
                val host = decode(hostRaw)
                val portStr = decode(portRaw)
                val port = portStr.toIntOrNull()
                if (port == null || !host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) return@mapNotNull null
                Server(host, port)
            }.toList()
    }

    private suspend fun testTcpSoftipStyle(host: String, port: Int, timeoutMs: Int = 3000): Pair<Boolean, Double> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                val ping = (System.currentTimeMillis() - start).toDouble()
                Pair(true, ping)
            }.getOrDefault(Pair(false, 0.0))
        }

    private fun aktarOpenSSTP(hostname: String, port: String) {
        val prefs = preferenceManager.sharedPreferences!!
        prefs.edit()
            .putString(OscPrefKey.HOME_HOSTNAME.name, hostname)
            .putString(OscPrefKey.SSL_PORT.name, port)
            .putString(OscPrefKey.HOME_USERNAME.name, "vpn")
            .putString(OscPrefKey.HOME_PASSWORD.name, "vpn")
            .putString(OscPrefKey.SSL_VERSION.name, "TLSv1.3")
            .putBoolean(OscPrefKey.SSL_DO_VERIFY.name, false)
            .apply()

        // 2) Home ekranındaki preference’ları canlı güncelle
        requireActivity().runOnUiThread {
            try {
                (findPreference("SSL_PORT") as? kittoku.osc.preference.custom.SSLPortPreference)?.summary = port
                (findPreference("SSL_VERSION") as? kittoku.osc.preference.custom.SSLVersionPreference)?.summary = "TLSv1.3"
                (findPreference("SSL_DO_VERIFY") as? kittoku.osc.preference.custom.SSLDoVerifyPreference)?.isChecked = false
            } catch (e: Exception) {
                Log.e("HOME_UI", "UI güncelleme hatası: ${e.message}")
            }
        }

        // 3) Yardımcı değişkenler
        lastServer = Server(hostname, port.toInt())
        Log.d("VPN_MONITOR", "SSTP settings updated: $hostname:$port")
        showToast("AutoSSTP dolduruldu: $hostname:$port")
    }

    data class Server(val host: String, val port: Int)

    private fun showToast(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appScope.cancel()
        monitorJob?.cancel()
        monitorJob = null
        Log.d("VPN_MONITOR", "onDestroyView called, monitor and appScope stopped")
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            var retrySame = 0
            while (isActive) {
                try {
                    val ctx = context?.applicationContext ?: run {
                        Log.e("VPN_MONITOR", "Application context unavailable")
                        return@launch
                    }
                    if (!isVpnConnected()) {
                        retrySame++
                        Log.d("VPN_MONITOR", "VPN disconnected, retrying ($retrySame)")
                        withContext(Dispatchers.Main) {
                            if (isAdded) startVpnAction(ACTION_VPN_CONNECT)
                            else {
                                val intent = Intent(ctx, SstpVpnService::class.java).setAction(ACTION_VPN_CONNECT)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    ctx.startForegroundService(intent)
                                } else {
                                    ctx.startService(intent)
                                }
                            }
                        }
                        delay(5_000)
                        if (retrySame >= 3) {
                            retrySame = 0
                            autoReconnect()
                            delay(5_000)
                        }
                    } else {
                        retrySame = 0
                        Log.d("VPN_MONITOR", "VPN connected: $lastHost:$lastPort")
                    }
                } catch (e: Exception) {
                    Log.e("VPN_MONITOR", "Monitor error: ${e.message}", e)
                }
                delay(5_000)
            }
        }
    }

    private fun isVpnConnected(): Boolean {
        val ctx = context ?: return false
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private suspend fun autoReconnect() {
        val appCtx = requireContext().applicationContext
        val prefs  = PreferenceManager.getDefaultSharedPreferences(appCtx)

        val cfg = configIndir() ?: return
        val servers = sunuculariCoz(cfg)
        if (servers.isEmpty()) return

        val best = pickFastest(servers.filterNot { it.port == 443 }) ?: return
        prefs.edit()
            .putString(OscPrefKey.HOME_HOSTNAME.name, best.first)
            .putString(OscPrefKey.SSL_PORT.name, best.second.toString())
            .apply()

        startVpnAction(ACTION_VPN_CONNECT)
    }

    private suspend fun pickFastest(list: List<Server>, currentHost: String = ""): Pair<String, Int>? =
        list
            .filter { it.host != currentHost }
            .map { srv ->
                lifecycleScope.async(Dispatchers.IO) {
                    val ip = srv.host.replace(Regex("""^[A-Z]{2,4}\d+"""), "")
                    val (ok, ping) = testTcpSoftipStyle(ip, srv.port)
                    if (ok) Triple(ip, srv.port, ping) else null
                }
            }.awaitAll()
            .filterNotNull()
            .minByOrNull { it.third }
            ?.let { Pair(it.first, it.second) }
}