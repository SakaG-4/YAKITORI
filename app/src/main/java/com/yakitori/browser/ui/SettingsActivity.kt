package com.yakitori.browser.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.webkit.WebViewCompat
import com.yakitori.browser.BuildConfig
import com.yakitori.browser.R
import com.yakitori.browser.data.Prefs
import com.yakitori.browser.data.Profile
import com.yakitori.browser.data.Store
import com.yakitori.browser.engine.ProfileEngine

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("history")?.setOnPreferenceClickListener {
                currentProfile()?.let { profile ->
                    startActivity(
                        Intent(requireContext(), HistoryActivity::class.java)
                            .putExtra(HistoryActivity.EXTRA_PROFILE_ID, profile.id),
                    )
                }
                true
            }
            findPreference<Preference>("clear_data")?.setOnPreferenceClickListener {
                currentProfile()?.let { ClearDataDialog.show(requireActivity(), it) }
                true
            }
            findPreference<Preference>("profiles")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), ProfilesActivity::class.java))
                true
            }
            findPreference<Preference>("autofill")?.setOnPreferenceClickListener {
                openAutofillSettings()
                true
            }
        }

        override fun onResume() {
            super.onResume()
            val profile = currentProfile()
            val target = profile?.let { getString(R.string.pref_target_profile, it.icon, it.name) }
            findPreference<Preference>("history")?.summary = target
            findPreference<Preference>("clear_data")?.summary = target
            findPreference<Preference>("autofill")?.summary = autofillSummary()
            findPreference<Preference>("about")?.summary = aboutSummary()
        }

        private fun currentProfile(): Profile? {
            val store = Store.get(requireContext())
            val id = Prefs(requireContext()).lastProfileId
            return store.profile(id) ?: store.profiles().firstOrNull()
        }

        /** 仕様12: 独自のパスワード管理は持たず、Android の自動入力サービス（Google 等）に委ねる。 */
        private fun openAutofillSettings() {
            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(Uri.parse("package:$GOOGLE_AUTOFILL_PACKAGE"))
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        private fun autofillSummary(): String {
            val manager = requireContext().getSystemService(AutofillManager::class.java)
                ?: return getString(R.string.autofill_unavailable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!manager.isAutofillSupported) return getString(R.string.autofill_unavailable)
                val service = manager.autofillServiceComponentName
                return when {
                    service == null -> getString(R.string.autofill_not_set)
                    service.packageName == GOOGLE_AUTOFILL_PACKAGE -> getString(R.string.autofill_google)
                    else -> getString(R.string.autofill_other, service.packageName)
                }
            }
            return getString(if (manager.isEnabled) R.string.autofill_enabled else R.string.autofill_not_set)
        }

        private fun aboutSummary(): String {
            val webView = WebViewCompat.getCurrentWebViewPackage(requireContext())
            val engine = webView?.let { "${it.packageName} ${it.versionName}" } ?: "-"
            val isolation = getString(
                if (ProfileEngine.isSupported) R.string.isolation_supported else R.string.isolation_unsupported,
            )
            return getString(R.string.about_summary, BuildConfig.VERSION_NAME, engine, isolation)
        }

        companion object {
            private const val GOOGLE_AUTOFILL_PACKAGE = "com.google.android.gms"
        }
    }
}
