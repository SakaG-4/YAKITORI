package com.yakitori.browser.data

data class Profile(
    val id: Long,
    val name: String,
    val icon: String,
    val position: Int,
)

data class TabRecord(
    val id: Long,
    val profileId: Long,
    val url: String,
    val title: String,
)

data class HistoryEntry(
    val id: Long,
    val profileId: Long,
    val url: String,
    val title: String,
    val visitedAt: Long,
)
