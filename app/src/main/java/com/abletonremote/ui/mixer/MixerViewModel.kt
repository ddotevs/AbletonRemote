package com.abletonremote.ui.mixer

import androidx.lifecycle.ViewModel
import com.abletonremote.repository.AbletonRepository

class MixerViewModel(
    private val repository: AbletonRepository
) : ViewModel() {

    val tracks = repository.tracks

    fun setVolume(trackId: Int, volume: Float) = repository.setTrackVolume(trackId, volume)
    fun setPan(trackId: Int, pan: Float) = repository.setTrackPan(trackId, pan)
    fun toggleMute(trackId: Int, currentMute: Boolean) = repository.setTrackMute(trackId, !currentMute)
    fun toggleSolo(trackId: Int, currentSolo: Boolean) = repository.setTrackSolo(trackId, !currentSolo)
    fun toggleArm(trackId: Int, currentArm: Boolean) = repository.setTrackArm(trackId, !currentArm)
    fun setInputType(trackId: Int, type: String) = repository.setTrackInputRoutingType(trackId, type)
    fun setInputChannel(trackId: Int, channel: String) = repository.setTrackInputRoutingChannel(trackId, channel)
    fun createAudioTrack() = repository.createAudioTrack()
}
