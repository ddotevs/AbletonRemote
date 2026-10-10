package com.abletonremote.ui.transport

import androidx.lifecycle.ViewModel
import com.abletonremote.repository.AbletonRepository

class TransportViewModel(
    private val repository: AbletonRepository
) : ViewModel() {

    val transportState = repository.transportState
    val cuePoints = repository.cuePoints

    fun play() = repository.play()
    fun stop() = repository.stop()
    fun continuePlaying() = repository.continuePlaying()
    fun record() = repository.record()
    fun undo() = repository.undo()
    fun redo() = repository.redo()
    fun tapTempo() = repository.tapTempo()
    fun stopAllClips() = repository.stopAllClips()
    fun setTempo(bpm: Float) = repository.setTempo(bpm)
    fun setMetronome(on: Boolean) = repository.setMetronome(on)
    fun setLoop(on: Boolean) = repository.setLoop(on)
    fun jumpToCue(index: Int) = repository.jumpToCue(index)
}
