package kittoku.osc.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kittoku.osc.R
import kittoku.osc.activity.BLANK_ACTIVITY_TYPE_APPS
import kittoku.osc.activity.BlankActivity
import kittoku.osc.activity.EXTRA_KEY_TYPE
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.setURIPrefValue
import kittoku.osc.preference.custom.DirectoryPreference

internal class SettingFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var certDirPref: DirectoryPreference
    private lateinit var logDirPref: DirectoryPreference

    private val certDirLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data?.also {
            requireContext().contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } else null
        setURIPrefValue(uri, OscPrefKey.SSL_CERT_DIR, prefs)
        certDirPref.updateView()
    }

    private val logDirLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data?.also {
            requireContext().contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } else null
        setURIPrefValue(uri, OscPrefKey.LOG_DIR, prefs)
        logDirPref.updateView()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        prefs = preferenceManager.sharedPreferences!!
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        refreshAllCustomPreferences()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        refreshAllCustomPreferences()
    }

    private fun refreshAllCustomPreferences() {
        listOf(
            OscPrefKey.SSL_PORT,
            OscPrefKey.SSL_VERSION,
            OscPrefKey.SSL_DO_VERIFY,
            OscPrefKey.SSL_DO_SPECIFY_CERT,
            OscPrefKey.SSL_CERT_DIR,
            OscPrefKey.SSL_DO_SELECT_SUITES,
            OscPrefKey.SSL_SUITES,
            OscPrefKey.SSL_DO_USE_CUSTOM_SNI,
            OscPrefKey.SSL_CUSTOM_SNI,
            OscPrefKey.PROXY_HOSTNAME,
            OscPrefKey.PROXY_PORT,
            OscPrefKey.PPP_MRU,
            OscPrefKey.PPP_MTU,
            OscPrefKey.PPP_STATIC_IPv4_ADDRESS,
            OscPrefKey.DNS_CUSTOM_ADDRESS,
            OscPrefKey.LOG_DIR
        ).forEach { key ->
            findPreference<Preference>(key.name)?.let {
                if (it is DirectoryPreference) it.updateView()
                // Diğer özel tipler varsa buraya ekleyin
            }
        }
    }

    private fun setCertDirListener() {
        certDirPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).also { intent ->
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                certDirLauncher.launch(intent)
            }
            true
        }
    }

    private fun setLogDirListener() {
        logDirPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).also { intent ->
                intent.flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                logDirLauncher.launch(intent)
            }
            true
        }
    }

    private fun setAllowedAppsListener() {
        findPreference<Preference>(OscPrefKey.ROUTE_ALLOWED_APPS.name)!!.also {
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(
                    Intent(requireContext(), BlankActivity::class.java)
                        .putExtra(EXTRA_KEY_TYPE, BLANK_ACTIVITY_TYPE_APPS)
                )
                true
            }
        }
    }

    fun refreshPortSslVerify() {
        // SSL_PORT
        findPreference<kittoku.osc.preference.custom.SSLPortPreference>(OscPrefKey.SSL_PORT.name)
            ?.let { it.summary = preferenceManager.sharedPreferences!!.getString(it.key, "") }

        // SSL_VERSION
        findPreference<kittoku.osc.preference.custom.SSLVersionPreference>(OscPrefKey.SSL_VERSION.name)
            ?.let { it.summary = preferenceManager.sharedPreferences!!.getString(it.key, "") }

        // SSL_DO_VERIFY
        findPreference<kittoku.osc.preference.custom.SSLDoVerifyPreference>(OscPrefKey.SSL_DO_VERIFY.name)
            ?.let { it.isChecked = preferenceManager.sharedPreferences!!.getBoolean(it.key, false) }
    }
}