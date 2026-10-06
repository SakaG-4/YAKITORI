package com.yakitori.browser.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yakitori.browser.R
import com.yakitori.browser.data.HistoryEntry
import com.yakitori.browser.data.Store
import com.yakitori.browser.engine.UrlUtil

/** 仕様10: プロファイル単位の閲覧履歴。 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyText: TextView
    private var profileId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        store = Store.get(this)
        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        val profile = store.profile(profileId)
        if (profile == null) {
            finish()
            return
        }
        title = getString(R.string.history_title, profile.icon, profile.name)

        emptyText = findViewById(R.id.emptyText)
        emptyText.setText(R.string.history_empty)
        adapter = HistoryAdapter(
            onOpen = { entry ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse(entry.url))
                        .putExtra(MainActivity.EXTRA_PROFILE_ID, profileId)
                        .putExtra(MainActivity.EXTRA_IN_CURRENT_TAB, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                finish()
            },
            onDelete = { entry ->
                store.deleteHistory(entry.id)
                reload()
            },
        )
        findViewById<RecyclerView>(R.id.list).adapter = adapter
        reload()
    }

    private fun reload() {
        val items = store.history(profileId)
        adapter.submit(items)
        emptyText.isVisible = items.isEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_CLEAR_ALL, 0, R.string.history_clear_all)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_CLEAR_ALL) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.history_clear_all_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    store.clearHistory(profileId, 0L)
                    reload()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class HistoryAdapter(
        private val onOpen: (HistoryEntry) -> Unit,
        private val onDelete: (HistoryEntry) -> Unit,
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.historyTitle)
            val url: TextView = view.findViewById(R.id.historyUrl)
            val time: TextView = view.findViewById(R.id.historyTime)
            val delete: ImageButton = view.findViewById(R.id.historyDelete)
        }

        private var items: List<HistoryEntry> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(items: List<HistoryEntry>) {
            this.items = items
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.title.text = entry.title.ifBlank { entry.url }
            holder.url.text = UrlUtil.display(entry.url)
            holder.time.text = DateUtils.formatDateTime(
                holder.itemView.context,
                entry.visitedAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
            )
            holder.itemView.setOnClickListener { onOpen(entry) }
            holder.delete.setOnClickListener { onDelete(entry) }
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val MENU_CLEAR_ALL = 1
    }
}
