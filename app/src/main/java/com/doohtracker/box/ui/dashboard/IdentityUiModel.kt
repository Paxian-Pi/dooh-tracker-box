package com.doohtracker.box.ui.dashboard

import androidx.compose.ui.graphics.Color

data class IdentityUiModel(
    val siteId: String,
    val username: String,
    val status: String,
    val statusColor: Color,
    val currentCampaign: String,
    val allDownloadedAds: String = "",
    val downloadedCount: Int,
    val mediaTitle: String = "NONE",
    val isMismatched: Boolean = false,
    val isDownloading: Boolean = false,
    val campaigns: List<CampaignBrief> = emptyList()
)

data class CampaignBrief(
    val id: String,
    val name: String,
    val isVerified: Boolean
)
