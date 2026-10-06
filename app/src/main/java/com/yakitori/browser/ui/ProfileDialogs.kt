package com.yakitori.browser.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yakitori.browser.R
import com.yakitori.browser.data.Profile

object ProfileDialogs {

    /** プロファイル作成（[existing] が null）／編集ダイアログ。 */
    fun showEditor(
        context: Context,
        existing: Profile?,
        usedIcons: Collection<String>,
        onSave: (name: String, icon: String) -> Unit,
    ) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_profile_edit, null)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.profileNameLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.profileNameInput)
        val group = view.findViewById<ChipGroup>(R.id.iconGroup)

        val initialIcon = existing?.icon
            ?: ProfileIcons.ALL.firstOrNull { it !in usedIcons }
            ?: ProfileIcons.ALL.first()
        for (icon in ProfileIcons.ALL) {
            val chip = inflater.inflate(R.layout.item_icon_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = icon
            chip.tag = icon
            group.addView(chip)
            if (icon == initialIcon) chip.isChecked = true
        }
        nameInput.setText(existing?.name.orEmpty())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.profile_new_title else R.string.profile_edit_title)
            .setView(view)
            .setPositiveButton(if (existing == null) R.string.create else R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    nameLayout.error = context.getString(R.string.profile_name_required)
                    return@setOnClickListener
                }
                val icon = group.findViewById<Chip>(group.checkedChipId)?.tag as? String ?: initialIcon
                onSave(name, icon)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun confirmDelete(context: Context, profile: Profile, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.profile_delete_title, profile.icon, profile.name))
            .setMessage(R.string.profile_delete_message)
            .setPositiveButton(R.string.delete) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
