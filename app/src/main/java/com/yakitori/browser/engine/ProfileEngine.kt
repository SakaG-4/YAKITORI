package com.yakitori.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yakitori.browser.data.Store
import androidx.webkit.Profile as EngineProfile

/**
 * YAKITORI のプロファイルと Chromium（Android System WebView）側のプロファイルを対応付ける。
 *
 * androidx.webkit の Multi-Profile API を使い、YAKITORI プロファイル 1 つにつき
 * WebView プロファイル 1 つ（"yk_p_<id>"）を割り当てる。これにより Cookie・ログイン状態・
 * Local Storage・IndexedDB・キャッシュ・Service Worker 等が Chromium の BrowserContext 単位で分離される。
 * シークレットモードは使い捨ての一時プロファイル（"yk_incognito_<時刻>"）で実現する。
 */
@SuppressLint("RequiresFeature")
object ProfileEngine {
    private const val PROFILE_PREFIX = "yk_p_"
    private const val INCOGNITO_PREFIX = "yk_incognito_"

    /** WebView 123 以降（MULTI_PROFILE 対応）でのみ true。 */
    val isSupported: Boolean by lazy {
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)
    }

    fun nameFor(profileId: Long): String = PROFILE_PREFIX + profileId

    fun newIncognitoName(): String = INCOGNITO_PREFIX + System.currentTimeMillis()

    /** 生成直後（何もロードする前）の WebView にプロファイルを割り当てる。 */
    fun bind(webView: WebView, engineName: String) {
        if (!isSupported) return
        ProfileStore.getInstance().getOrCreateProfile(engineName)
        WebViewCompat.setProfile(webView, engineName)
    }

    fun cookieManager(engineName: String): CookieManager =
        if (isSupported) {
            ProfileStore.getInstance().getOrCreateProfile(engineName).cookieManager
        } else {
            CookieManager.getInstance()
        }

    private fun existing(engineName: String): EngineProfile? =
        if (isSupported) ProfileStore.getInstance().getProfile(engineName) else null

    fun flush(engineName: String) {
        if (isSupported) existing(engineName)?.cookieManager?.flush() else CookieManager.getInstance().flush()
    }

    fun clearCookies(engineName: String) {
        val cm: CookieManager = if (isSupported) {
            existing(engineName)?.cookieManager ?: return
        } else {
            CookieManager.getInstance()
        }
        cm.removeAllCookies(null)
        cm.flush()
    }

    /** Local Storage / IndexedDB / Web SQL / Cache Storage / 位置情報の許可 等を削除する。 */
    fun clearSiteData(engineName: String) {
        if (isSupported) {
            val profile = existing(engineName) ?: return
            profile.webStorage.deleteAllData()
            profile.geolocationPermissions.clearAll()
        } else {
            WebStorage.getInstance().deleteAllData()
            GeolocationPermissions.getInstance().clearAll()
        }
    }

    /** HTTP キャッシュを削除する（キャッシュはプロファイル単位）。 */
    fun clearCache(context: Context, engineName: String) {
        if (isSupported && existing(engineName) == null) return
        val webView = WebView(context)
        bind(webView, engineName)
        webView.clearCache(true)
        webView.destroy()
    }

    /**
     * エンジン側プロファイルの削除を予約する。
     * 中身（Cookie・サイトデータ）は即時に消去し、ディレクトリ自体は使用中でなくなった時点
     * （遅くとも次回起動時）に削除する。
     */
    fun scheduleDeletion(context: Context, engineName: String) {
        if (!isSupported) return
        runCatching {
            clearCookies(engineName)
            clearSiteData(engineName)
        }
        Store.get(context).addPendingEngineDeletion(engineName)
        processPendingDeletions(context)
    }

    fun processPendingDeletions(context: Context) {
        if (!isSupported) return
        val store = Store.get(context)
        for (name in store.pendingEngineDeletions()) {
            if (tryDelete(name)) store.removePendingEngineDeletion(name)
        }
    }

    private fun tryDelete(name: String): Boolean =
        try {
            ProfileStore.getInstance().deleteProfile(name)
            true
        } catch (e: IllegalStateException) {
            // まだ WebView から使用中。次の機会に再試行する。
            false
        } catch (e: IllegalArgumentException) {
            true
        }

    /** 起動直後（WebView 生成前）に、残存したシークレット用プロファイルや孤立プロファイルを削除する。 */
    fun cleanupOnStartup(context: Context) {
        if (!isSupported) return
        val store = Store.get(context)
        val valid = store.profiles().map { nameFor(it.id) }.toSet()
        for (name in ProfileStore.getInstance().allProfileNames) {
            val orphan = name.startsWith(PROFILE_PREFIX) && name !in valid
            if (name.startsWith(INCOGNITO_PREFIX) || orphan) store.addPendingEngineDeletion(name)
        }
        processPendingDeletions(context)
    }
}
