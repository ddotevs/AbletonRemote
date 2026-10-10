package com.abletonremote.data.model

data class TrackState(
    val index: Int,
    val name: String = "",
    val volume: Float = 0.85f,
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val arm: Boolean = false,
    val canBeArmed: Boolean = true,
    val meterLeft: Float = 0f,
    val meterRight: Float = 0f,
    val color: Int = 0,
    val inputRoutingType: String = "",
    val availableInputTypes: List<String> = emptyList(),
    val inputRoutingChannel: String = "",
    val availableInputChannels: List<String> = emptyList()
)

data class TransportState(
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val sessionRecord: Boolean = false,
    val tempo: Float = 120f,
    val metronome: Boolean = false,
    val loop: Boolean = false,
    val loopStart: Float = 0f,
    val loopLength: Float = 4f,
    val currentTime: Float = 0f,
    val currentBeat: Int = 0,
    val signatureNumerator: Int = 4,
    val signatureDenominator: Int = 4,
    val songLength: Float = 0f,
    val punchIn: Boolean = false,
    val punchOut: Boolean = false
)

data class CuePoint(
    val index: Int,
    val name: String,
    val time: Float
)

data class ServerConfig(
    val id: Long = 0,
    val name: String,
    val host: String,
    val sendPort: Int = 11000,
    val receivePort: Int = 11001,
    val isDefault: Boolean = false
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
