package com.yakitori.browser.data

import android.content.Context
import androidx.preference.PreferenceManager

class Prefs(context: Context) {
    private val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val homepage: String
        get() = sp.getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE).orEmpty().ifBlank { DEFAULT_HOMEPAGE }

    val searchTemplate: String
        get() = when (sp.getString(KEY_SEARCH_ENGINE, "google")) {
            "bing" -> "https://www.bing.com/search?q=%s"
            "duckduckgo" -> "https://duckduckgo.com/?q=%s"
            "yahoo_jp" -> "https://search.yahoo.co.jp/search?p=%s"
            else -> "https://www.google.com/search?q=%s"
        }

    val newTabUrl: String
        get() = if (sp.getString(KEY_NEW_TAB, "home") == "blank") "about:blank" else homepage

    val confirmDownload: Boolean
        get() = sp.getBoolean(KEY_DOWNLOAD_CONFIRM, true)

    val incognitoBlockScreenshots: Boolean
        get() = sp.getBoolean(KEY_INCOGNITO_SECURE, true)

    var lastProfileId: Long
        get() = sp.getLong(KEY_LAST_PROFILE, -1L)
        set(value) = sp.edit().putLong(KEY_LAST_PROFILE, value).apply()

    companion object {
        const val DEFAULT_HOMEPAGE = "https://www.google.com/"
        const val KEY_HOMEPAGE = "homepage"
        const val KEY_SEARCH_ENGINE = "search_engine"
        const val KEY_NEW_TAB = "new_tab_page"
        const val KEY_DOWNLOAD_CONFIRM = "download_confirm"
        const val KEY_INCOGNITO_SECURE = "incognito_block_screenshots"
        private const val KEY_LAST_PROFILE = "last_profile_id"
    }
}
