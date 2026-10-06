package com.yakitori.browser

import android.app.Application
import android.util.Log
import android.webkit.WebView
import com.yakitori.browser.engine.ProfileEngine

class YakitoriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 仕様13: デベロッパーツールは搭載しない（リモートデバッグも無効化）
        WebView.setWebContentsDebuggingEnabled(false)
        try {
            // 前回終了時に残ったシークレット用データや削除待ちプロファイルを破棄する
            ProfileEngine.cleanupOnStartup(this)
        } catch (t: Throwable) {
            Log.w("YakitoriApp", "Profile cleanup failed", t)
        }
    }
}
