package com.audibleclone.service.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultDataSourceFactory
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * AudiobookExoPlayer - ExoPlayer wrapper optimized for audiobook playback.
 * Handles media items, chapters, caching, and playback state with Flow-based API.
 */
class AudiobookExoPlayer(
    @ApplicationContext private val context: Context
) : Player {
    
    private val exoPlayer: ExoPlayer
    private var dataSourceFactory: DataSource.Factory
    
    // Playback state flows
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val playbackState: StateFlow<Int> = _playbackState
    
    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition
    
    private val _bufferedPosition = MutableStateFlow(0L)
    override val bufferedPosition: StateFlow<Long> = _bufferedPosition
    
    private val _duration = MutableStateFlow(C.TIME_UNSET)
    override val duration: StateFlow<Long> = _duration
    
    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed
    
    private val _playWhenReady = MutableStateFlow(true)
    override val playWhenReady: StateFlow<Boolean> = _playWhenReady
    
    private var listeners = mutableListOf<Player.Listener>()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Executors.newSingleThreadExecutor())

    init {
        exoPlayer = ExoPlayer.Builder(context).build()
        
        dataSourceFactory = DefaultDataSourceFactory(
            context,
            Util.getUserAgent(context, "AudibleClone"),
            true
        )
        
        // Forward events to listeners
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                _isPlaying.value = isPlaying
                listeners.forEach { it.onIsPlayingChanged(isPlaying) }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                _playbackState.value = playbackState
                listeners.forEach { it.onPlaybackStateChanged(playbackState) }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                listeners.forEach { it.onPositionDiscontinuity(oldPosition, newPosition, reason) }
            }
            
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                listeners.forEach { it.onEvents(player, events) }
            }
            
            override fun onPlayerError(error: ExoPlayer.ExoPlaybackException) {
                super.onPlayerError(error)
                listeners.forEach { it.onPlayerError(error) }
            }
        })
        
        // Observe position updates
        coroutineScope.launch {
            // Note: In practice, you'd use a callback or Flow from ExoPlayer
            // This is simplified for the example
        }
    }

    // MARK: - Player Interface Implementation
    
    override fun getTrackSelection(): Any = exoPlayer.trackSelection
    override fun getAudioComponent(): Any = exoPlayer.audioComponent
    override fun getVideoComponent(): Any = exoPlayer.videoComponent
    override fun getCacheOffsetMs(): Long = exoPlayer.cacheOffsetMs
    override fun getBufferedPosition(): Long = exoPlayer.bufferedPosition
    override fun getCurrentMediaItem(): Any = exoPlayer.currentMediaItem
    override fun getCurrentMediaItemIndex(): Int = exoPlayer.currentMediaItemIndex
    override fun getCurrentPosition(): Long = exoPlayer.currentPosition
    override fun getCurrentTimecode(): Float = exoPlayer.currentTimecode
    override fun getDevices(): Any = exoPlayer.devices
    override fun getDiscardedBuffers(): Float = exoPlayer.discardedBuffers
    override fun getEarliestParentSequenceKey(): Int = exoPlayer.earliestParentSequenceKey
    override fun getElapsedRealtime(): Long = exoPlayer.elapsedRealtime
    override fun getEventListener(): Any = exoPlayer.eventListener
    override fun getPlaybackState(): Int = exoPlayer.playbackState
    override fun getPlaybackSuppressionReason(): Int = exoPlayer.playbackSuppressionReason
    override fun getPlaybackType(): Int = exoPlayer.playbackType
    override fun getPlayedDurationMs(): Long = exoPlayer.playedDurationMs
    override fun getPreviousWindowSequenceKey(): Int = exoPlayer.previousWindowSequenceKey
    override fun getPrimaryTrackInfo(): Any = exoPlayer.primaryTrackInfo
    override fun getRenderedFormat(): Any = exoPlayer.renderedFormat
    override fun getSeekPositionHint(): Long = exoPlayer.seekPositionHint
    override fun getShuffleOrder(): Any = exoPlayer.shuffleOrder
    override fun getTotalBufferedTime(): Long = exoPlayer.totalBufferedTime
    override fun getVolume(): Float = exoPlayer.volume
    
    override fun hasNextMediaItem(): Boolean = exoPlayer.hasNextMediaItem()
    override fun hasPreviousMediaItem(): Boolean = exoPlayer.hasPreviousMediaItem()
    override fun isLoading(): Boolean = exoPlayer.isLoading
    override fun playWhenReady: Boolean
        get() = exoPlayer.playWhenReady
        set(value) {
            exoPlayer.playWhenReady = value
            _playWhenReady.value = value
        }
    
    override fun windowedSeekTo(
        windowIndex: Int,
        positionMs: Long,
        isWindowed: Boolean
    ) {
        exoPlayer.seekTo(windowIndex, positionMs)
    }

    // MARK: - Player Methods
    
    override fun play() {
        exoPlayer.play()
    }
    
    override fun pause() {
        exoPlayer.pause()
    }
    
    override fun stop() {
       exoPlayer.stop()
    }
    
    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }
    
    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _playbackSpeed.value = speed
    }
    
    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume
    }
    
    override fun setAudioAttributes(
        audioAttributes: androidx.media3.common.AudioAttributes,
        handleAudioFocus: Boolean
    ) {
        exoPlayer.setAudioAttributes(audioAttributes, handleAudioFocus)
    }
    
    override fun setWakeMode(wakeMode: Int) {
        exoPlayer.setWakeMode(wakeMode)
    }

    // MARK: - Media Item Management
    
    fun setMediaItems(items: List<MediaItem>) {
        exoPlayer.setMediaItems(items)
    }
    
    fun addMediaItem(item: MediaItem) {
        exoPlayer.addMediaItem(item)
    }
    
    fun removeMediaItem(index: Int) {
        exoPlayer.removeMediaItem(index)
    }
    
    fun clearMediaItems() {
        exoPlayer.clearMediaItems()
    }
    
    fun prepare() {
        exoPlayer.prepare()
    }
    
    fun release() {
        exoPlayer.release()
    }
    
    // MARK: - Listener Management
    
    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        exoPlayer.addListener(listener)
    }
    
    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        exoPlayer.removeListener(listener)
    }
    
    // MARK: - Helper Methods
    
    fun getBufferedPercentage(): Int = exoPlayer.bufferedPercentage
    fun getExoPlayer(): ExoPlayer = exoPlayer
    fun windowCount(): Int = exoPlayer.windowCount
    fun previousMediaItemIndex(): Int = exoPlayer.previousMediaItemIndex
    fun readSourceInfoFromHash(): Int = exoPlayer.readSourceInfoFromHash()
}