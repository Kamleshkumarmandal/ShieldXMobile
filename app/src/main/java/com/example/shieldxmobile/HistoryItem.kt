package com.example.shieldxmobile

data class HistoryItem(
    val sender: String,
    val message: String,
    val result: String,
    val category: String,
    val risk: Int
)