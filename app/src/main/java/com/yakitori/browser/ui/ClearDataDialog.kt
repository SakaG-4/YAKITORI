package com.yakitori.browser.ui

import android.app.Activity
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yakitori.browser.R
import com.yakitori.browser.data.Profile
import com.yakitori.browser.data.Store
import com.yakitori.browser.engine.ProfileEngine

/** 仕様9: プロファイル単位の閲覧データ削除。 */
object ClearDataDialog {
    private const val HOUR = 60L * 60L * 1000L
    private const val DAY = 24L * HOUR

    fun show(activity: Activity, profile: Profile, onCleared: (() -> Unit)? = null) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_clear_data, null)
        view.findViewById<TextView>(R.id.clearTarget).text =
            activity.getString(R.string.clear_target, profile.icon, profile.name)
        val history = view.findViewById<CheckBox>(R.id.checkHistory)
        val cookies = view.findViewById<CheckBox>(R.id.checkCookies)
        val cache = view.findViewById<CheckBox>(R.id.checkCache)
        val range = view.findViewById<RadioGroup>(R.id.rangeGroup)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.clear_data_title)
            .setView(view)
            .setPositiveButton(R.string.delete) { _, _ ->
                val now = System.currentTimeMillis()
                val since = when (range.checkedRadioButtonId) {
                    R.id.range1h -> now - HOUR
                    R.id.range24h -> now - DAY
                    R.id.range7d -> now - 7 * DAY
                    else -> 0L
                }
                val engineName = ProfileEngine.nameFor(profile.id)
                if (history.isChecked) Store.get(activity).clearHistory(profile.id, since)
                if (cookies.isChecked) {
                    ProfileEngine.clearCookies(engineName)
                    ProfileEngine.clearSiteData(engineName)
                }
                if (cache.isChecked) ProfileEngine.clearCache(activity, engineName)
                Toast.makeText(activity, R.string.cleared, Toast.LENGTH_SHORT).show()
                onCleared?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
