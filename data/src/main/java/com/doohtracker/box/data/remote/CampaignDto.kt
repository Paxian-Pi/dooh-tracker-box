package com.doohtracker.box.data.remote

import com.doohtracker.box.data.local.CampaignEntity
import com.google.gson.annotations.SerializedName

data class CampaignDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("start_time") val startTime: Long,
    @SerializedName("end_time") val endTime: Long,
    @SerializedName("media_url") val mediaUrl: String?
)

fun CampaignDto.toEntity() = CampaignEntity(
    id = id,
    name = name,
    campaign = name, // Use name as fallback
    campaignTag = id, // Use id as fallback
    user = "unknown", // Fallback
    startTime = startTime,
    endTime = endTime,
    mediaUrl = mediaUrl
)
