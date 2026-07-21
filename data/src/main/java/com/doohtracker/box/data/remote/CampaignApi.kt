package com.doohtracker.box.data.remote

import com.doohtracker.box.data.model.MediaResponse
import com.doohtracker.box.data.model.SendProofOfPlayRequest
import com.doohtracker.box.data.model.TrackHourlyRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CampaignApi {

    @POST("track/")
    suspend fun sendProofOfPlay(@Body request: SendProofOfPlayRequest): Response<Unit>

    @POST("time-track/")
    suspend fun trackHourly(@Body request: TrackHourlyRequest): Response<Unit>

    @GET("media/")
    suspend fun getMedia(): Response<MediaResponse>
    @GET("campaigns")
    suspend fun getCampaigns(): List<CampaignDto>

    @POST("track")
    suspend fun sendTrackingPing(@Body ping: TrackingPingDto)

    @POST("activate/")
    suspend fun activateDevice(@Body request: DeviceActivationRequest): Response<Unit>
}

data class DeviceActivationRequest(
    val activationCode: String,
    val username: String,
    val installationNotes: String,
    val deviceId: String
)

data class TrackingPingDto(
    val campaignId: String,
    val timestamp: Long
)
