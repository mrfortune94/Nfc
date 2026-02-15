package com.nfc.reader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nfc.reader.data.NfcLog
import com.nfc.reader.databinding.ItemLogBinding
import com.nfc.reader.utils.toDateString

class LogsAdapter : ListAdapter<NfcLog, LogsAdapter.LogViewHolder>(LogDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: NfcLog) {
            binding.timestampText.text = log.timestamp.toDateString()
            binding.uidText.text = "UID: ${log.uid}"
            binding.typeText.text = "${log.tagType} - ${log.isoStandard}"
            binding.operationText.text = log.operation
        }
    }
    
    private class LogDiffCallback : DiffUtil.ItemCallback<NfcLog>() {
        override fun areItemsTheSame(oldItem: NfcLog, newItem: NfcLog): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: NfcLog, newItem: NfcLog): Boolean {
            return oldItem == newItem
        }
    }
}
