package com.yakitori.browser.engine

import android.util.Patterns
import java.net.URLEncoder

object UrlUtil {
    private val DIRECT_SCHEME = Regex("^(https?|about):", RegexOption.IGNORE_CASE)
    private val LOCAL_HOST = Regex(
        "^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$",
        RegexOption.IGNORE_CASE,
    )

    /** アドレスバーの入力を URL として解釈できればそのまま、できなければ検索 URL に変換する。 */
    fun resolve(input: String, searchTemplate: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        if (!text.contains(' ')) {
            if (DIRECT_SCHEME.containsMatchIn(text)) return text
            if (LOCAL_HOST.matches(text)) return "http://$text"
            if (text.contains('.') && Patterns.WEB_URL.matcher(text).matches()) return "https://$text"
        }
        return searchTemplate.replace("%s", URLEncoder.encode(text, "UTF-8"))
    }

    /** アドレスバー非フォーカス時の表示用 URL。 */
    fun display(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        var shown = url.removePrefix("https://")
        if (shown.count { it == '/' } == 1 && shown.endsWith("/")) shown = shown.dropLast(1)
        return shown
    }
}
