package com.yakitori.browser.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.yakitori.browser.R
import com.yakitori.browser.browser.Space
import com.yakitori.browser.data.Profile

/** 画面上部の横スクロール式プロファイルバー。[プロファイル...] [🕶 シークレット] [＋] */
class ProfileBarAdapter(
    private val onSelect: (Space) -> Unit,
    private val onLongPress: (View, Space) -> Unit,
    private val onAdd: () -> Unit,
) : RecyclerView.Adapter<ProfileBarAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.chipIcon)
        val label: TextView = view.findViewById(R.id.chipLabel)
    }

    private var profiles: List<Profile> = emptyList()
    private var selected: Space? = null
    private var incognitoOpen = false
    private var foreground = Color.BLACK

    @SuppressLint("NotifyDataSetChanged")
    fun submit(profiles: List<Profile>, selected: Space?, incognitoOpen: Boolean, foreground: Int) {
        this.profiles = profiles
        this.selected = selected
        this.incognitoOpen = incognitoOpen
        this.foreground = foreground
        notifyDataSetChanged()
    }

    fun positionOf(space: Space): Int = when (space) {
        is Space.Normal -> profiles.indexOfFirst { it.id == space.profileId }
        Space.Incognito -> profiles.size
    }

    override fun getItemCount(): Int = profiles.size + 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile_chip, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val context = holder.itemView.context
        when {
            position < profiles.size -> {
                val profile = profiles[position]
                val space = Space.Normal(profile.id)
                bind(holder, profile.icon, profile.name, ProfileIcons.accent(profile.icon), selected == space)
                holder.itemView.setOnClickListener { onSelect(space) }
                holder.itemView.setOnLongClickListener { onLongPress(it, space); true }
                holder.itemView.contentDescription = profile.name
            }
            position == profiles.size -> {
                val label = context.getString(R.string.incognito)
                bind(holder, ProfileIcons.INCOGNITO_ICON, label, ProfileIcons.INCOGNITO_ACCENT, selected == Space.Incognito)
                holder.itemView.alpha = if (incognitoOpen || selected == Space.Incognito) 1f else 0.7f
                holder.itemView.setOnClickListener { onSelect(Space.Incognito) }
                holder.itemView.setOnLongClickListener { onLongPress(it, Space.Incognito); true }
                holder.itemView.contentDescription = label
            }
            else -> {
                bind(holder, "＋", "", foreground, false)
                holder.itemView.setOnClickListener { onAdd() }
                holder.itemView.setOnLongClickListener(null)
                holder.itemView.contentDescription = context.getString(R.string.profile_add)
            }
        }
    }

    private fun bind(holder: Holder, icon: String, label: String, accent: Int, isSelected: Boolean) {
        holder.itemView.alpha = 1f
        holder.icon.text = icon
        holder.label.text = label
        holder.label.isVisible = label.isNotEmpty()
        holder.label.setTextColor(foreground)
        holder.icon.setTextColor(foreground)
        holder.label.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        holder.itemView.isSelected = isSelected
        holder.itemView.background = GradientDrawable().apply {
            cornerRadius = 18.dp().toFloat()
            if (isSelected) {
                setColor(ColorUtils.setAlphaComponent(accent, 0x44))
                setStroke(2.dp(), accent)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(1.dp(), ColorUtils.setAlphaComponent(foreground, 0x40))
            }
        }
    }
}
