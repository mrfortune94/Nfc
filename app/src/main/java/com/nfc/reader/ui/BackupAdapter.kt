package com.nfc.reader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nfc.reader.R
import com.nfc.reader.data.CardBackup
import com.nfc.reader.databinding.ItemBackupBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView Adapter for displaying card backups
 */
class BackupAdapter(
    private val onViewClick: (CardBackup) -> Unit,
    private val onDeleteClick: (CardBackup) -> Unit,
    private val onEmulateClick: (CardBackup) -> Unit
) : ListAdapter<CardBackup, BackupAdapter.BackupViewHolder>(BackupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder {
        val binding = ItemBackupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BackupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BackupViewHolder(
        private val binding: ItemBackupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(backup: CardBackup) {
            binding.apply {
                cardNameText.text = backup.cardName
                cardUidText.text = backup.uid
                cardTypeText.text = backup.cardType
                cardIsoText.text = backup.isoStandard
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                backupDateText.text = dateFormat.format(Date(backup.timestamp))

                emulationBadge.setBackgroundResource(
                    if (backup.canEmulate) R.drawable.badge_emulate_supported
                    else R.drawable.badge_emulate_unsupported
                )
                emulationBadge.text = if (backup.canEmulate) 
                    root.context.getString(R.string.can_emulate)
                else 
                    root.context.getString(R.string.cannot_emulate)

                viewButton.setOnClickListener { onViewClick(backup) }
                deleteButton.setOnClickListener { onDeleteClick(backup) }
                emulateButton.setOnClickListener { onEmulateClick(backup) }
                emulateButton.isEnabled = backup.canEmulate
                emulateButton.alpha = if (backup.canEmulate) 1.0f else 0.5f
            }
        }
    }

    class BackupDiffCallback : DiffUtil.ItemCallback<CardBackup>() {
        override fun areItemsTheSame(oldItem: CardBackup, newItem: CardBackup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CardBackup, newItem: CardBackup): Boolean {
            return oldItem == newItem
        }
    }
}
