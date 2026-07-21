package com.doohtracker.box.data.model

import com.doohtracker.box.data.local.CampaignEntity
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

data class MediaResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("next") val next: String? = null,
    @SerializedName("previous") val previous: String? = null,
    @SerializedName("results") val results: List<MediaResult>
)

data class MediaResult(
    @SerializedName("id") val id: Int,
    @SerializedName("user") val user: String,
    @SerializedName("campaign") val campaign: String,
    @SerializedName("campaign_tag") val campaignTag: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("media_url") val mediaURL: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("status") val status: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("unique_id") val uniqueID: String,
    @SerializedName("uploaded_at") val uploadedAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

fun MediaResult.toEntity(): CampaignEntity {
    val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val start = try {
        dateTimeFormat.parse("$startDate $startTime")?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
    val end = try {
        dateTimeFormat.parse("$endDate $endTime")?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    return CampaignEntity(
        id = uniqueID,
        name = title,
        campaign = campaign,
        campaignTag = campaignTag,
        user = user,
        startTime = start,
        endTime = end,
        mediaUrl = mediaURL
    )
}

//<-----------------||----------------->//

/**
 * Flat model for Proof of Play tracking as per backend requirements.
 */
data class SendProofOfPlayRequest(
    @SerializedName("user") val user: String,
    @SerializedName("campaign") val campaign: String,
    @SerializedName("campaign_tag") val campaignTag: String,
    @SerializedName("time") val time: String,
    @SerializedName("date") val date: String,
    @SerializedName("comment") val comment: String,
    @SerializedName("day") val day: String,
    @SerializedName("location") val location: String,
    @SerializedName("unique_id") val uniqueID: String,
    @SerializedName("duration_count") val durationCount: Long,
    @SerializedName("confidence") val confidence: Float
)

data class TrackHourlyRequest(
    @SerializedName("user") val user: String,
    @SerializedName("campaign") val campaign: String,
    @SerializedName("campaign_tag") val campaignTag: String,
    @SerializedName("hours") val hours: String,
    @SerializedName("date") val date: String,
    @SerializedName("count") val count: Int,
    @SerializedName("week_day") val weekDay: String,
    @SerializedName("location") val location: String,
    @SerializedName("unique_id") val uniqueID: String
)