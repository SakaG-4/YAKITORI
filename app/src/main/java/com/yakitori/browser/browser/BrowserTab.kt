package com.yakitori.browser.browser

import android.webkit.WebView

/** タブが属するブラウジング空間。通常プロファイルまたはシークレット。 */
sealed interface Space {
    data class Normal(val profileId: Long) : Space
    data object Incognito : Space
}

class BrowserTab(
    val id: Long,
    val space: Space,
    var url: String,
    var title: String,
) {
    var webView: WebView? = null
    var progress: Int = 100
    var loading: Boolean = false
    /** window.open 等で開かれた場合の呼び出し元タブ。戻るで閉じたとき復帰する。 */
    var opener: BrowserTab? = null
}

class SpaceState {
    val tabs = mutableListOf<BrowserTab>()
    var active: BrowserTab? = null
}
