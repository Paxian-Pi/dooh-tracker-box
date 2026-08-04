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
    private val gatekeeper: VerificationGatekeeper
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
                    repeatMode = Player.REPEAT_MODE_ALL // Repeat the same item until title changes
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            Log.d("Playback", "SUCCESS: Video frame rendered for current item!")
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("Playback", "ExoPlayer Error: ${error.message} (Code: ${error.errorCode})", error)
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
            // Observe the billboard's current title from the Source Monitor
            gatekeeper.signalMonitor.sourceMetadata.collectLatest { metadata ->
                val reportedTitle = metadata?.mediaTitle?.lowercase() ?: "none"
                
                if (reportedTitle == "none") {
                    _mismatchedTitle.value = null
                    stopPlayback()
                    return@collectLatest
                }

                // Find a match in our system
                val allCampaigns = campaignRepository.allCampaigns.first()
                val matchedCampaign = allCampaigns.find { campaign ->
                    val fileName = campaign.sourceFilename?.lowercase() ?: ""
                    val campaignName = campaign.name.lowercase()
                    (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                    reportedTitle.contains(campaignName)
                }

                if (matchedCampaign != null) {
                    _mismatchedTitle.value = null
                    if (_currentCampaign.value?.id != matchedCampaign.id) {
                        Log.d("Playback", "Billboard Title Changed -> ${matchedCampaign.name}. Mirroring...")
                        playCampaign(matchedCampaign)
                    }
                } else {
                    // Mismatched: Billboard is playing something we don't have media for
                    Log.d("Playback", "Billboard playing unrecognized content: $reportedTitle. Alerting monitor.")
                    stopPlayback()
                    _mismatchedTitle.value = metadata?.mediaTitle ?: "Unknown File"
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
            // No timer needed, will stay on screen until collectLatest triggers next change
        } else {
            Log.d("Playback", "Mirroring VIDEO: ${campaign.name}")
            _player?.let { p ->
                val uri = campaign.localPath?.let { android.net.Uri.fromFile(java.io.File(it)) }
                    ?: android.net.Uri.parse(campaign.mediaUrl)

                p.setMediaItem(MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(campaign.id)
                    .build())
                p.prepare()
                p.play()
            }
        }
    }

    fun release() {
        _player?.release()
        _player = null
        playerScope.cancel()
    }
}
