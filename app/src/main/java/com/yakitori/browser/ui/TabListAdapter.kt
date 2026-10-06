package com.yakitori.browser.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.yakitori.browser.R
import com.yakitori.browser.browser.BrowserTab
import com.yakitori.browser.engine.UrlUtil

class TabListAdapter(
    private val onSelect: (BrowserTab) -> Unit,
    private val onClose: (BrowserTab) -> Unit,
) : RecyclerView.Adapter<TabListAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tabTitle)
        val url: TextView = view.findViewById(R.id.tabUrl)
        val close: ImageButton = view.findViewById(R.id.tabClose)
    }

    private var tabs: List<BrowserTab> = emptyList()
    private var active: BrowserTab? = null
    private var accent: Int = Color.GRAY

    @SuppressLint("NotifyDataSetChanged")
    fun submit(tabs: List<BrowserTab>, active: BrowserTab?, accent: Int) {
        this.tabs = tabs
        this.active = active
        this.accent = accent
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tabs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tab = tabs[position]
        val context = holder.itemView.context
        holder.title.text = tab.title.ifBlank { context.getString(R.string.new_tab) }
        holder.url.text = UrlUtil.display(tab.url)
        holder.itemView.setOnClickListener { onSelect(tab) }
        holder.close.setOnClickListener { onClose(tab) }
        val outline = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutlineVariant)
        holder.itemView.background = GradientDrawable().apply {
            cornerRadius = 12.dp().toFloat()
            if (tab === active) {
                setColor(ColorUtils.setAlphaComponent(accent, 0x30))
                setStroke(2.dp(), accent)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(1.dp(), outline)
            }
        }
    }
}
