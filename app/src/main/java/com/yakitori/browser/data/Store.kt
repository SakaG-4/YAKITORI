package com.yakitori.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * アプリ側で管理するデータ（プロファイル定義・タブ状態・閲覧履歴）。
 * Cookie / Storage / キャッシュ等の Web データは WebView のプロファイル側で分離される。
 */
class Store private constructor(context: Context) {

    private class Helper(context: Context) : SQLiteOpenHelper(context, "yakitori.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            // AUTOINCREMENT: 削除済みプロファイルの ID（= エンジン側プロファイル名）を再利用しない
            db.execSQL(
                "CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                    "icon TEXT NOT NULL, position INTEGER NOT NULL, active_tab_id INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE tabs (id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, visited_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX history_profile ON history (profile_id, visited_at)")
            db.execSQL("CREATE TABLE pending_engine_deletions (name TEXT PRIMARY KEY)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val helper = Helper(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ---- profiles ----

    fun profiles(): List<Profile> =
        db.rawQuery("SELECT id, name, icon, position FROM profiles ORDER BY position, id", null).use { c ->
            val out = ArrayList<Profile>()
            while (c.moveToNext()) out += Profile(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3))
            out
        }

    fun profile(id: Long): Profile? = profiles().firstOrNull { it.id == id }

    fun createProfile(name: String, icon: String): Profile {
        val position = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM profiles", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
            put("position", position)
        }
        val id = db.insertOrThrow("profiles", null, values)
        return Profile(id, name, icon, position)
    }

    fun updateProfile(id: Long, name: String, icon: String) {
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
        }
        db.update("profiles", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteProfile(id: Long) {
        val args = arrayOf(id.toString())
        db.beginTransaction()
        try {
            db.delete("tabs", "profile_id = ?", args)
            db.delete("history", "profile_id = ?", args)
            db.delete("profiles", "id = ?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun activeTabId(profileId: Long): Long? =
        db.rawQuery("SELECT active_tab_id FROM profiles WHERE id = ?", arrayOf(profileId.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    fun setActiveTabId(profileId: Long, tabId: Long) {
        val values = ContentValues().apply { put("active_tab_id", tabId) }
        db.update("profiles", values, "id = ?", arrayOf(profileId.toString()))
    }

    // ---- tabs ----

    fun tabs(profileId: Long): List<TabRecord> =
        db.rawQuery(
            "SELECT id, profile_id, url, title FROM tabs WHERE profile_id = ? ORDER BY id",
            arrayOf(profileId.toString()),
        ).use { c ->
            val out = ArrayList<TabRecord>()
            while (c.moveToNext()) out += TabRecord(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3))
            out
        }

    fun insertTab(profileId: Long, url: String, title: String): Long {
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("url", url)
            put("title", title)
        }
        return db.insertOrThrow("tabs", null, values)
    }

    fun updateTab(id: Long, url: String, title: String) {
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
        }
        db.update("tabs", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTab(id: Long) {
        db.delete("tabs", "id = ?", arrayOf(id.toString()))
    }

    // ---- history ----

    fun addHistory(profileId: Long, url: String, title: String, time: Long = System.currentTimeMillis()) {
        val last = db.rawQuery(
            "SELECT id, url FROM history WHERE profile_id = ? ORDER BY visited_at DESC, id DESC LIMIT 1",
            arrayOf(profileId.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null }

        if (last != null && last.second == url) {
            val values = ContentValues().apply {
                if (title.isNotBlank()) put("title", title)
                put("visited_at", time)
            }
            db.update("history", values, "id = ?", arrayOf(last.first.toString()))
        } else {
            val values = ContentValues().apply {
                put("profile_id", profileId)
                put("url", url)
                put("title", title)
                put("visited_at", time)
            }
            db.insert("history", null, values)
        }
    }

    fun history(profileId: Long, limit: Int = 1000): List<HistoryEntry> =
        db.rawQuery(
            "SELECT id, profile_id, url, title, visited_at FROM history WHERE profile_id = ? " +
                "ORDER BY visited_at DESC, id DESC LIMIT $limit",
            arrayOf(profileId.toString()),
        ).use { c ->
            val out = ArrayList<HistoryEntry>()
            while (c.moveToNext()) {
                out += HistoryEntry(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getLong(4))
            }
            out
        }

    fun deleteHistory(id: Long) {
        db.delete("history", "id = ?", arrayOf(id.toString()))
    }

    /** [since] 以降（ミリ秒）の履歴を削除する。0 なら全期間。 */
    fun clearHistory(profileId: Long, since: Long) {
        db.delete("history", "profile_id = ? AND visited_at >= ?", arrayOf(profileId.toString(), since.toString()))
    }

    // ---- WebView プロファイル削除待ち ----

    fun addPendingEngineDeletion(name: String) {
        val values = ContentValues().apply { put("name", name) }
        db.insertWithOnConflict("pending_engine_deletions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pendingEngineDeletions(): List<String> =
        db.rawQuery("SELECT name FROM pending_engine_deletions", null).use { c ->
            val out = ArrayList<String>()
            while (c.moveToNext()) out += c.getString(0)
            out
        }

    fun removePendingEngineDeletion(name: String) {
        db.delete("pending_engine_deletions", "name = ?", arrayOf(name))
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context).also { instance = it }
            }
    }
}
