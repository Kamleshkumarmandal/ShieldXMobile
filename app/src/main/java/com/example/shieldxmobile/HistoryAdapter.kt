package com.example.shieldxmobile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private val historyList: List<HistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSender: TextView = itemView.findViewById(R.id.tvHistorySender)
        val tvMessage: TextView = itemView.findViewById(R.id.tvHistoryMessage)
        val tvResult: TextView = itemView.findViewById(R.id.tvHistoryResult)
        val tvCategoryRisk: TextView = itemView.findViewById(R.id.tvHistoryCategoryRisk)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]

        holder.tvSender.text = item.sender
        holder.tvMessage.text = item.message
        holder.tvResult.text = item.result
        holder.tvCategoryRisk.text = "${item.category} | Risk: ${item.risk}%"

        when {
            item.result.contains("High Risk") -> holder.tvResult.setTextColor(Color.RED)
            item.result.contains("Suspicious") -> holder.tvResult.setTextColor(Color.parseColor("#E65100"))
            else -> holder.tvResult.setTextColor(Color.parseColor("#2E7D32"))
        }
    }

    override fun getItemCount(): Int {
        return historyList.size
    }
}