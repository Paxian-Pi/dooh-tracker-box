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

    private val fullPlaylist = mutableListOf<CampaignEntity>()
    private var currentIndex = 0
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
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            Log.d("Playback", "SUCCESS: Video frame rendered for current item!")
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                Log.d("Playback", "ExoPlayer: Video ended.")
                                handleItemFinished()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("Playback", "ExoPlayer Error: ${error.message} (Code: ${error.errorCode})", error)
                            handleItemFinished()
                        }
                    })
                }
        } catch (e: Exception) {
            Log.e("Playback", "ExoPlayer Init Error", e)
        }
        
        startPlaylistMonitor()
    }

    private fun startPlaylistMonitor() {
        playerScope.launch {
            while (isActive) {
                val now = timeProvider.getCurrentTimeMillis()
                val active = campaignRepository.allCampaigns.first().filter { 
                    it.startTime <= now && it.endTime >= now && 
                    !it.mediaUrl.isNullOrBlank() && !it.localPath.isNullOrBlank()
                }
                
                if (active.map { it.id } != fullPlaylist.map { it.id }) {
                    Log.d("Playback", "Playlist Updated. Syncing ${active.size} campaigns.")
                    fullPlaylist.clear()
                    fullPlaylist.addAll(active)
                    if (!isPlaying && fullPlaylist.isNotEmpty()) {
                        currentIndex = 0
                        startCurrentItem()
                    }
                }
                delay(15.seconds)
            }
        }
    }

    private fun startCurrentItem() {
        imageTimerJob?.cancel()
        if (fullPlaylist.isEmpty()) {
            _currentCampaign.value = null
            isPlaying = false
            return
        }

        if (currentIndex >= fullPlaylist.size) currentIndex = 0
        
        val campaign = fullPlaylist[currentIndex]
        _currentCampaign.value = campaign
        isPlaying = true

        val url = campaign.mediaUrl?.lowercase() ?: ""
        val isExplicitImage = campaign.mediaType?.lowercase() == "image" || 
                              url.endsWith(".jpg") || url.endsWith(".jpeg") || 
                              url.endsWith(".png") || url.endsWith(".webp")

        if (isExplicitImage) {
            Log.d("Playback", "Playing IMAGE: ${campaign.name}")
            imageTimerJob = playerScope.launch {
                delay(5.seconds)
                handleItemFinished()
            }
        } else {
            Log.d("Playback", "Playing VIDEO: ${campaign.name} -> ${campaign.mediaUrl}")
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

    private fun handleItemFinished() {
        val finishedCampaign = _currentCampaign.value ?: return
        
        playerScope.launch(Dispatchers.IO) {
            val isAllowed = gatekeeper.isTrackingAllowed.first()
            if (isAllowed) {
                // Use 5s for images, actual for videos
                val durationSeconds = if (finishedCampaign.mediaType?.lowercase() == "image") {
                    5L
                } else {
                    val duration = _player?.duration ?: 0L
                    if (duration > 0) (duration / 1000.0).roundToLong() else 0L
                }
                campaignRepository.trackPlayback(finishedCampaign.id, durationSeconds)
            } else {
                Log.d("Playback", "Tracking skipped for ${finishedCampaign.name} (Source Monitor not Active)")
            }
        }

        // Cycle next
        currentIndex++
        startCurrentItem()
    }

    fun release() {
        _player?.release()
        _player = null
        playerScope.cancel()
    }
}
