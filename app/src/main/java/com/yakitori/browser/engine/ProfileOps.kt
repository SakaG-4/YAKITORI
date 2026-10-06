package com.yakitori.browser.engine

import android.content.Context
import com.yakitori.browser.data.Store

object ProfileOps {
    /** プロファイルと、それに属するタブ・履歴・Cookie・サイトデータ・キャッシュをすべて削除する。 */
    fun delete(context: Context, profileId: Long) {
        Store.get(context).deleteProfile(profileId)
        ProfileEngine.scheduleDeletion(context, ProfileEngine.nameFor(profileId))
    }
}
