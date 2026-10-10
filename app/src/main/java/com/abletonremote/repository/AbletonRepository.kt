package com.abletonremote.repository

import com.abletonremote.data.model.*
import com.abletonremote.osc.OscConnection
import com.abletonremote.osc.OscConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AbletonRepository(
    private val connectionManager: OscConnectionManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _transportState = MutableStateFlow(TransportState())
    val transportState: StateFlow<TransportState> = _transportState.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackState>>(emptyList())
    val tracks: StateFlow<List<TrackState>> = _tracks.asStateFlow()

    private val _cuePoints = MutableStateFlow<List<CuePoint>>(emptyList())
    val cuePoints: StateFlow<List<CuePoint>> = _cuePoints.asStateFlow()

    private var listenerJob: Job? = null

    init {
        scope.launch {
            connectionManager.activeConnection.collectLatest { conn ->
                listenerJob?.cancel()
                if (conn == null) return@collectLatest
                // Wait for the connection to actually be CONNECTED
                conn.connectionState.first { it == ConnectionState.CONNECTED || it == ConnectionState.ERROR }
                if (conn.connectionState.value == ConnectionState.CONNECTED) {
                    listenerJob = scope.launch { subscribeToAll(conn) }
                }
            }
        }
    }

    private suspend fun subscribeToAll(conn: OscConnection) = coroutineScope {
        // Start collectors FIRST so they're ready when replies arrive
        launch { subscribeTransport(conn) }
        launch { subscribeTracks(conn) }
        // Small delay to ensure collectors are active before sending queries
        delay(100)
        fetchInitialState(conn)
    }

    private suspend fun fetchInitialState(conn: OscConnection) {
        conn.send("/live/song/get/tempo")
        conn.send("/live/song/get/is_playing")
        conn.send("/live/song/get/metronome")
        conn.send("/live/song/get/loop")
        conn.send("/live/song/get/loop_start")
        conn.send("/live/song/get/loop_length")
        conn.send("/live/song/get/signature_numerator")
        conn.send("/live/song/get/signature_denominator")
        conn.send("/live/song/get/record_mode")
        conn.send("/live/song/get/session_record")
        conn.send("/live/song/get/song_length")
        conn.send("/live/song/get/punch_in")
        conn.send("/live/song/get/punch_out")
        conn.send("/live/song/get/cue_points")
        conn.send("/live/song/get/num_tracks")
    }

    private suspend fun subscribeTransport(conn: OscConnection) = coroutineScope {
        val transportProps = listOf(
            "is_playing", "tempo", "metronome", "loop", "loop_start",
            "loop_length", "current_song_time", "record_mode", "session_record",
            "signature_numerator", "signature_denominator", "beat",
            "song_length", "punch_in", "punch_out"
        )
        transportProps.forEach { prop ->
            conn.startListening("/live/song/get/$prop")
        }

        launch {
            conn.messagesAt("/live/song/get/tempo").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(tempo = (v as Number).toFloat()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/is_playing").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(isPlaying = v == 1 || v == true) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/metronome").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(metronome = v == 1 || v == true) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/loop").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(loop = v == 1 || v == true) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/loop_start").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(loopStart = (v as Number).toFloat()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/loop_length").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(loopLength = (v as Number).toFloat()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/current_song_time").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(currentTime = (v as Number).toFloat()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/beat").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(currentBeat = (v as Number).toInt()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/record_mode").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(isRecording = v == 1 || v == true) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/session_record").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(sessionRecord = v == 1 || v == true) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/signature_numerator").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(signatureNumerator = (v as Number).toInt()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/signature_denominator").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(signatureDenominator = (v as Number).toInt()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/cue_points").collect { args ->
                val points = mutableListOf<CuePoint>()
                var i = 0
                while (i + 1 < args.size) {
                    val name = args[i] as? String ?: ""
                    val time = (args[i + 1] as? Number)?.toFloat() ?: 0f
                    points.add(CuePoint(index = i / 2, name = name, time = time))
                    i += 2
                }
                _cuePoints.value = points
            }
        }
        launch {
            conn.messagesAt("/live/song/get/song_length").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(songLength = (v as Number).toFloat()) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/punch_in").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(punchIn = v == 1 || v == true) }
                }
            }
        }
        launch {
            conn.messagesAt("/live/song/get/punch_out").collect { args ->
                args.firstOrNull()?.let { v ->
                    _transportState.update { it.copy(punchOut = v == 1 || v == true) }
                }
            }
        }
    }

    private suspend fun subscribeTracks(conn: OscConnection) = coroutineScope {
        conn.messagesAt("/live/song/get/num_tracks").collect { args ->
            val numTracks = (args.firstOrNull() as? Number)?.toInt() ?: return@collect
            val trackList = (0 until numTracks).map { i -> TrackState(index = i) }
            _tracks.value = trackList

            for (i in 0 until numTracks) {
                conn.send("/live/track/get/name", i)
                conn.send("/live/track/get/volume", i)
                conn.send("/live/track/get/panning", i)
                conn.send("/live/track/get/mute", i)
                conn.send("/live/track/get/solo", i)
                conn.send("/live/track/get/arm", i)
                conn.send("/live/track/get/color", i)
                conn.send("/live/track/get/input_routing_type", i)
                conn.send("/live/track/get/available_input_routing_types", i)
                conn.send("/live/track/get/input_routing_channel", i)
                conn.send("/live/track/get/available_input_routing_channels", i)

                conn.startListening("/live/track/get/volume", i)
                conn.startListening("/live/track/get/panning", i)
                conn.startListening("/live/track/get/mute", i)
                conn.startListening("/live/track/get/solo", i)
                conn.startListening("/live/track/get/arm", i)
                conn.startListening("/live/track/get/output_meter_left", i)
                conn.startListening("/live/track/get/output_meter_right", i)
            }

            launch { collectTrackProperty(conn, "/live/track/get/name") { track, v -> track.copy(name = v as String) } }
            launch { collectTrackProperty(conn, "/live/track/get/volume") { track, v -> track.copy(volume = (v as Number).toFloat()) } }
            launch { collectTrackProperty(conn, "/live/track/get/panning") { track, v -> track.copy(pan = (v as Number).toFloat()) } }
            launch { collectTrackProperty(conn, "/live/track/get/mute") { track, v -> track.copy(mute = v == 1 || v == true) } }
            launch { collectTrackProperty(conn, "/live/track/get/solo") { track, v -> track.copy(solo = v == 1 || v == true) } }
            launch { collectTrackProperty(conn, "/live/track/get/arm") { track, v -> track.copy(arm = v == 1 || v == true) } }
            launch { collectTrackProperty(conn, "/live/track/get/color") { track, v -> track.copy(color = (v as Number).toInt()) } }
            launch { collectTrackProperty(conn, "/live/track/get/output_meter_left") { track, v -> track.copy(meterLeft = (v as Number).toFloat()) } }
            launch { collectTrackProperty(conn, "/live/track/get/output_meter_right") { track, v -> track.copy(meterRight = (v as Number).toFloat()) } }
            launch { collectTrackProperty(conn, "/live/track/get/input_routing_type") { track, v -> track.copy(inputRoutingType = v as String) } }
            launch { collectTrackProperty(conn, "/live/track/get/input_routing_channel") { track, v -> track.copy(inputRoutingChannel = v as String) } }
            launch { collectTrackListProperty(conn, "/live/track/get/available_input_routing_types") { track, v -> track.copy(availableInputTypes = v) } }
            launch { collectTrackListProperty(conn, "/live/track/get/available_input_routing_channels") { track, v -> track.copy(availableInputChannels = v) } }
        }
    }

    private suspend fun collectTrackProperty(
        conn: OscConnection,
        address: String,
        update: (TrackState, Any) -> TrackState
    ) {
        conn.messagesAt(address).collect { args ->
            if (args.size >= 2) {
                val trackIndex = (args[0] as Number).toInt()
                val value = args[1]
                _tracks.update { list ->
                    list.map { track ->
                        if (track.index == trackIndex) update(track, value) else track
                    }
                }
            }
        }
    }

    private suspend fun collectTrackListProperty(
        conn: OscConnection,
        address: String,
        update: (TrackState, List<String>) -> TrackState
    ) {
        conn.messagesAt(address).collect { args ->
            if (args.isNotEmpty()) {
                val trackIndex = (args[0] as Number).toInt()
                val values = args.drop(1).map { it.toString() }
                _tracks.update { list ->
                    list.map { track ->
                        if (track.index == trackIndex) update(track, values) else track
                    }
                }
            }
        }
    }

    // --- Transport controls ---

    private fun activeConn(): OscConnection? =
        connectionManager.connections.value[connectionManager.activeConnectionId.value]

    fun play() = activeConn()?.send("/live/song/start_playing")
    fun stop() = activeConn()?.send("/live/song/stop_playing")
    fun continuePlaying() = activeConn()?.send("/live/song/continue_playing")
    fun record() = activeConn()?.send("/live/song/trigger_session_record")
    fun undo() = activeConn()?.send("/live/song/undo")
    fun redo() = activeConn()?.send("/live/song/redo")
    fun tapTempo() = activeConn()?.send("/live/song/tap_tempo")
    fun stopAllClips() = activeConn()?.send("/live/song/stop_all_clips")

    fun setTempo(bpm: Float) = activeConn()?.send("/live/song/set/tempo", bpm)
    fun setMetronome(on: Boolean) = activeConn()?.send("/live/song/set/metronome", if (on) 1 else 0)
    fun setLoop(on: Boolean) = activeConn()?.send("/live/song/set/loop", if (on) 1 else 0)
    fun setLoopStart(beats: Float) = activeConn()?.send("/live/song/set/loop_start", beats)
    fun setLoopLength(beats: Float) = activeConn()?.send("/live/song/set/loop_length", beats)
    fun jumpToCue(index: Int) = activeConn()?.send("/live/song/cue_point/jump", index)
    fun setSongPosition(beats: Float) = activeConn()?.send("/live/song/set/current_song_time", beats)
    fun setPunchIn(on: Boolean) = activeConn()?.send("/live/song/set/punch_in", if (on) 1 else 0)
    fun setPunchOut(on: Boolean) = activeConn()?.send("/live/song/set/punch_out", if (on) 1 else 0)

    // --- Track controls ---

    fun setTrackVolume(trackId: Int, volume: Float) =
        activeConn()?.send("/live/track/set/volume", trackId, volume)

    fun setTrackPan(trackId: Int, pan: Float) =
        activeConn()?.send("/live/track/set/panning", trackId, pan)

    fun setTrackMute(trackId: Int, mute: Boolean) =
        activeConn()?.send("/live/track/set/mute", trackId, if (mute) 1 else 0)

    fun setTrackSolo(trackId: Int, solo: Boolean) =
        activeConn()?.send("/live/track/set/solo", trackId, if (solo) 1 else 0)

    fun setTrackArm(trackId: Int, arm: Boolean) =
        activeConn()?.send("/live/track/set/arm", trackId, if (arm) 1 else 0)

    fun setTrackInputRoutingType(trackId: Int, type: String) =
        activeConn()?.send("/live/track/set/input_routing_type", trackId, type)

    fun setTrackInputRoutingChannel(trackId: Int, channel: String) =
        activeConn()?.send("/live/track/set/input_routing_channel", trackId, channel)

    fun createAudioTrack(index: Int = -1) =
        activeConn()?.send("/live/song/create_audio_track", index)
}
