package com.yakitori.browser.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.yakitori.browser.R
import com.yakitori.browser.data.Profile
import com.yakitori.browser.data.Store
import com.yakitori.browser.engine.ProfileOps

/** 仕様14: プロファイル管理（追加・編集・削除）。 */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: ProfileListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        store = Store.get(this)
        adapter = ProfileListAdapter(onEdit = this::edit, onDelete = this::delete)
        findViewById<RecyclerView>(R.id.list).adapter = adapter
        reload()
    }

    private fun reload() {
        adapter.submit(store.profiles())
    }

    private fun add() {
        ProfileDialogs.showEditor(this, null, store.profiles().map { it.icon }) { name, icon ->
            store.createProfile(name, icon)
            reload()
        }
    }

    private fun edit(profile: Profile) {
        ProfileDialogs.showEditor(this, profile, store.profiles().map { it.icon }) { name, icon ->
            store.updateProfile(profile.id, name, icon)
            reload()
        }
    }

    private fun delete(profile: Profile) {
        if (store.profiles().size <= 1) {
            Toast.makeText(this, R.string.profile_delete_last, Toast.LENGTH_SHORT).show()
            return
        }
        ProfileDialogs.confirmDelete(this, profile) {
            ProfileOps.delete(this, profile.id)
            reload()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ADD, 0, R.string.profile_add)
            .setIcon(R.drawable.ic_add)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_ADD) {
            add()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class ProfileListAdapter(
        private val onEdit: (Profile) -> Unit,
        private val onDelete: (Profile) -> Unit,
    ) : RecyclerView.Adapter<ProfileListAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: TextView = view.findViewById(R.id.rowIcon)
            val name: TextView = view.findViewById(R.id.rowName)
            val edit: ImageButton = view.findViewById(R.id.rowEdit)
            val delete: ImageButton = view.findViewById(R.id.rowDelete)
        }

        private var items: List<Profile> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(items: List<Profile>) {
            this.items = items
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile_row, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val profile = items[position]
            holder.icon.text = profile.icon
            holder.name.text = profile.name
            holder.itemView.setOnClickListener { onEdit(profile) }
            holder.edit.setOnClickListener { onEdit(profile) }
            holder.delete.setOnClickListener { onDelete(profile) }
        }
    }

    companion object {
        private const val MENU_ADD = 1
    }
}
