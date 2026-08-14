package com.doohtracker.box.tracker.player

import android.content.Context
import android.util.Log
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
                    repeatMode = Player.REPEAT_MODE_OFF // Handle per-item
                    volume = 0f // Quiet mirroring
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            Log.d("Playback", "State: ${getStateName(playbackState)}")
                        }

                        override fun onRenderedFirstFrame() {
                            Log.d("Playback", "SUCCESS: Video frame rendered for current item!")
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("Playback", "ExoPlayer Error: ${error.message} (Code: ${error.errorCode})", error)
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
            // Observe all billboard pulses AND the hub's aggregate syncing state
            combine(
                gatekeeper.signalMonitor.allSourceMetadata,
                campaignRepository.isHubSyncing
            ) { allMetadata, isHubSyncing ->
                if (isHubSyncing) {
                    Log.d("Playback", "Hub is downloading references. Inhibiting mirror playback.")
                    stopPlayback()
                    return@combine
                }

                // Pickup the most recently active pulse
                val metadata = allMetadata.values.maxByOrNull { it.lastSeen }
                val reportedTitle = metadata?.mediaTitle?.lowercase() ?: "none"
                
                if (reportedTitle == "none") {
                    _mismatchedTitle.value = null
                    stopPlayback()
                    return@combine
                }

                // Find a match in our system across all clients (STRICT COMPLIANCE: Active Only)
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
                    // Check if the matched campaign itself has a local file
                    if (matchedCampaign.localPath == null) {
                        Log.d("Playback", "Match found for ${matchedCampaign.name} but media not yet downloaded. Waiting...")
                        stopPlayback()
                        return@combine
                    }

                    _mismatchedTitle.value = null
                    if (_currentCampaign.value?.id != matchedCampaign.id) {
                        Log.d("Playback", "Billboard Title Changed -> ${matchedCampaign.name}. Mirroring...")
                        playCampaign(matchedCampaign)
                    }
                } else {
                    // Mismatched: Billboard is playing something unrecognized by the Hub
                    Log.d("Playback", "Billboard playing unrecognized content: $reportedTitle.")
                    stopPlayback()
                    _mismatchedTitle.value = metadata?.mediaTitle ?: "Unknown File"
                }
            }.collect()
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
            Log.d("Playback", "Mirroring VIDEO: ${campaign.name}")
            _player?.let { p ->
                try {
                    Log.d("Playback", "Current player state before stop: ${getStateName(p.playbackState)}")
                    p.stop()
                    p.clearMediaItems()
                    
                    val uri = campaign.localPath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists() && file.length() > 0) {
                            Log.d("Playback", "Using local file: $path (size: ${file.length()} bytes)")
                            android.net.Uri.fromFile(file)
                        } else {
                            Log.d("Playback", "Local file missing/empty: $path, falling back to URL")
                            null
                        }
                    } ?: android.net.Uri.parse(campaign.mediaUrl)

                    Log.d("Playback", "Preparing player for ${campaign.name} with URI: $uri")
                    
                    p.setMediaItem(MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(campaign.id)
                        .build())
                    
                    p.repeatMode = Player.REPEAT_MODE_ALL
                    Log.d("Playback", "Calling prepare...")
                    p.prepare()
                    Log.d("Playback", "Calling play...")
                    p.play()
                    Log.d("Playback", "Play called. Current state: ${getStateName(p.playbackState)}")
                } catch (e: Exception) {
                    Log.e("Playback", "Failed to start video playback for ${campaign.name}", e)
                }
            }
        }
    }

    fun release() {
        _player?.release()
        _player = null
        playerScope.cancel()
    }
}
