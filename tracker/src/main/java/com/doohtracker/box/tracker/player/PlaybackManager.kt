package com.doohtracker.box.tracker.player

import android.content.Context
import android.util.Log
import java.io.EOFException
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.doohtracker.box.data.local.CampaignEntity
import com.doohtracker.box.data.logic.VerificationGatekeeper
import com.doohtracker.box.data.repository.CampaignRepository
import com.doohtracker.box.data.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@androidx.media3.common.util.UnstableApi
@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val campaignRepository: CampaignRepository,
    private val gatekeeper: VerificationGatekeeper,
    private val timeProvider: TimeProvider
) {
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var _player: ExoPlayer? = null
    val player: Player? get() = _player

    private val _currentCampaign = MutableStateFlow<CampaignEntity?>(null)
    val currentCampaign = _currentCampaign.asStateFlow()

    private val _mismatchedTitle = MutableStateFlow<String?>(null)
    val mismatchedTitle = _mismatchedTitle.asStateFlow()

    private val _isPreviewActive = MutableStateFlow(false)
    val isPreviewActive = _isPreviewActive.asStateFlow()

    private var isPlaying = false
    private var imageTimerJob: Job? = null

    init {
        try {
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("DOOHTracker/1.0")
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            _player = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            Log.d("Playback", "State: ${getStateName(playbackState)}")
                        }

                        override fun onRenderedFirstFrame() {
                            Log.d("Playback", "SUCCESS: Video frame rendered for current item!")
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("Playback", "ExoPlayer Error: ${error.message} (Code: ${error.errorCode})", error)

                            val cause = error.cause
                            val isGenericIoError = error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                            val isEof = cause is EOFException || cause?.message?.contains("EOF", ignoreCase = true) == true

                            if (isGenericIoError || isEof) {
                                _currentCampaign.value?.let { campaign ->
                                    val path = campaign.localPath
                                    if (path != null) {
                                        Log.w("Playback", "IO/EOF Error detected while playing local file! Marking as corrupted: $path")
                                        playerScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    val file = java.io.File(path)
                                                    if (file.exists()) {
                                                        Log.d("Playback", "Deleting corrupted file: $path")
                                                        file.delete()
                                                    }
                                                }
                                                Log.d("Playback", "Clearing local path for campaign ${campaign.id} in DB")
                                                campaignRepository.clearLocalPath(campaign.id)

                                                // Trigger re-download immediately
                                                campaignRepository.syncCampaigns()

                                                // Stop player to prevent repeated errors and allow monitor to retry
                                                stopPlayback()

                                            } catch (e: Exception) {
                                                Log.e("Playback", "Failed to handle corrupted media recovery", e)
                                                stopPlayback()
                                            }
                                        }
                                    } else {
                                        Log.e("Playback", "IO Error occurred while playing from URL. No local file to clear. Stopping playback.")
                                        stopPlayback()
                                    }
                                }
                            } else {
                                // For other errors, still stop playback to avoid stuck state
                                stopPlayback()
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            Log.d("Playback", "IsPlaying: $isPlaying")
                        }
                    })
                }
        } catch (e: Exception) {
            Log.e("Playback", "ExoPlayer Init Error", e)
        }

        startLiveMirrorMonitor()
    }

    private fun startLiveMirrorMonitor() {
        playerScope.launch {
            // Observe pulses with a 3-second "Sustain" to prevent flickering
            combine(
                gatekeeper.signalMonitor.allSourceMetadata,
                campaignRepository.isHubSyncing,
                _isPreviewActive
            ) { allMetadata, isHubSyncing, isPreviewActive ->
                // Bundle state for stable consumption
                Triple(allMetadata, isHubSyncing, isPreviewActive)
            }.collectLatest { (allMetadata, isHubSyncing, isPreviewActive) ->
                if (isPreviewActive) return@collectLatest

                if (isHubSyncing) {
                    stopPlayback()
                    return@collectLatest
                }

                val metadata = allMetadata.values.maxByOrNull { it.lastSeen }
                val reportedTitle = metadata?.mediaTitle?.lowercase() ?: "none"
                
                if (reportedTitle == "none") {
                    _mismatchedTitle.value = null
                    // Wait 3 seconds before stopping to handle transient gaps
                    delay(3000.milliseconds)
                    if (!_isPreviewActive.value) stopPlayback()
                    return@collectLatest
                }

                val currentTime = timeProvider.getCurrentTimeMillis()
                val allCampaigns = campaignRepository.allCampaigns.first()
                val matchedCampaign = allCampaigns.filter { 
                    it.startTime <= currentTime && it.endTime >= currentTime 
                }.find { campaign ->
                    val fileName = campaign.sourceFilename?.lowercase() ?: ""
                    val campaignName = campaign.name.lowercase()
                    (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                    reportedTitle.contains(campaignName)
                }

                if (matchedCampaign != null) {
                    val url = matchedCampaign.mediaUrl?.lowercase() ?: ""
                    val isVideo = matchedCampaign.mediaType?.lowercase() == "video" || url.endsWith(".mp4")

                    // VIDEOS: Don't require localPath anymore (Title verification only)
                    // IMAGES: Still require localPath for visual mirroring
                    if (!isVideo && matchedCampaign.localPath == null) {
                        delay(2000.milliseconds) // Wait briefly for sync to finish
                        stopPlayback()
                        return@collectLatest
                    }

                    _mismatchedTitle.value = null
                    if (_currentCampaign.value?.id != matchedCampaign.id) {
                        playCampaign(matchedCampaign)
                    }
                } else {
                    // Mismatched: Billboard is playing something unrecognized by the Hub
                    Log.d("Playback", "Billboard playing unrecognized content: $reportedTitle.")
                    stopPlayback()
                    _mismatchedTitle.value = metadata?.mediaTitle ?: "Unknown File"
                    stopPlayback()
                }
            }
        }
    }

    private fun stopPlayback() {
        imageTimerJob?.cancel()
        _player?.stop()
        _player?.clearMediaItems()
        _currentCampaign.value = null
        isPlaying = false
    }

    private fun getStateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }

    private fun playCampaign(campaign: CampaignEntity) {
        imageTimerJob?.cancel()
        _currentCampaign.value = campaign
        isPlaying = true

        val url = campaign.mediaUrl?.lowercase() ?: ""
        val isExplicitImage = campaign.mediaType?.lowercase() == "image" || 
                              url.endsWith(".jpg") || url.endsWith(".jpeg") || 
                              url.endsWith(".png") || url.endsWith(".webp")

        if (isExplicitImage) {
            Log.d("Playback", "Mirroring IMAGE: ${campaign.name}")
            _player?.stop()
            _player?.clearMediaItems()
        } else {
            /*Log.d("Playback", "Mirroring VIDEO: ${campaign.name}")
            _player?.let { p ->
                try {
                    val localPath = campaign.localPath
                    if (localPath == null) {
                        Log.e("Playback", "ERROR: localPath is null for ${campaign.name}")
                        stopPlayback()
                        return@let
                    }

                    val file = java.io.File(localPath)
                    if (!file.exists() || file.length() == 0L) {
                        Log.e("Playback", "ERROR: Local file for ${campaign.name} is missing or empty at $localPath")
                        stopPlayback()
                        return@let
                    }

                    Log.d("Playback", "ExoPlayer: Starting video playback: $localPath")
                    val uri = android.net.Uri.fromFile(file)

                    p.stop()
                    p.clearMediaItems()
                    p.setMediaItem(MediaItem.fromUri(uri))
                    p.repeatMode = Player.REPEAT_MODE_ALL
                    p.prepare()
                    p.play()

                    Log.d("Playback", "ExoPlayer: play() called for ${campaign.name}")
                } catch (e: Exception) {
                    Log.e("Playback", "Failed to start video playback for ${campaign.name}", e)
                }
            }*/

            Log.d("Playback", "Mirroring VIDEO (Title Verification): ${campaign.name}")
            _player?.stop()
            _player?.clearMediaItems()
        }
    }

    fun release() {
        _player?.release()
        _player = null
        playerScope.cancel()
    }

    fun previewCampaign(campaign: CampaignEntity) {
        playerScope.launch {
            _isPreviewActive.value = true
            Log.d("Playback", "Starting manual 30s preview for: ${campaign.name}")
            playCampaign(campaign)
            delay(30000) // 30 seconds preview
            _isPreviewActive.value = false
            Log.d("Playback", "Manual preview finished. Returning to live mirror.")
        }
    }
}
