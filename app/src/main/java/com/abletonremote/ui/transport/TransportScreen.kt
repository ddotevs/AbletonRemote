package com.abletonremote.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abletonremote.ui.theme.*

@Composable
fun TransportScreen(viewModel: TransportViewModel) {
    val state by viewModel.transportState.collectAsState()
    val cuePoints by viewModel.cuePoints.collectAsState()

    // Local state for slider dragging (avoids jitter from live updates while dragging)
    var isDraggingPosition by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Beat counter
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AbletonSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatBeatTime(state.currentTime, state.signatureNumerator),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            state.isRecording -> AbletonRed
                            state.isPlaying -> AbletonGreen
                            else -> AbletonTextPrimary
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.signatureNumerator}/${state.signatureDenominator}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AbletonTextSecondary
                    )
                }
            }
        }

        // Song position scrubber
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AbletonSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Position", style = MaterialTheme.typography.titleSmall)
                        Text(
                            formatBeatTime(
                                if (isDraggingPosition) dragPosition else state.currentTime,
                                state.signatureNumerator
                            ),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = AbletonAccent
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    val maxPos = if (state.songLength > 0f) state.songLength else 1000f
                    Slider(
                        value = if (isDraggingPosition) dragPosition else state.currentTime,
                        onValueChange = {
                            isDraggingPosition = true
                            dragPosition = it
                        },
                        onValueChangeFinished = {
                            viewModel.setSongPosition(dragPosition)
                            isDraggingPosition = false
                        },
                        valueRange = 0f..maxPos,
                        colors = SliderDefaults.colors(
                            thumbColor = AbletonAccent,
                            activeTrackColor = AbletonAccent,
                            inactiveTrackColor = AbletonCard
                        )
                    )

                    // Quick jump buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setSongPosition(0f) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("Start", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                val newPos = (state.currentTime - state.signatureNumerator).coerceAtLeast(0f)
                                viewModel.setSongPosition(newPos)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("-1 Bar", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                viewModel.setSongPosition(state.currentTime + state.signatureNumerator)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("+1 Bar", fontSize = 12.sp) }
                    }
                }
            }
        }

        // Transport buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TransportButton(
                    icon = Icons.Default.Stop,
                    label = "Stop",
                    tint = AbletonTextPrimary,
                    isActive = !state.isPlaying
                ) { viewModel.stop() }

                TransportButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Play",
                    tint = AbletonGreen,
                    isActive = state.isPlaying && !state.isRecording
                ) { viewModel.play() }

                TransportButton(
                    icon = Icons.Default.FiberManualRecord,
                    label = "Record",
                    tint = AbletonRed,
                    isActive = state.isRecording
                ) { viewModel.record() }
            }
        }

        // Punch In/Out + Metronome + Loop toggles
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToggleCard(
                    label = "Punch In",
                    isOn = state.punchIn,
                    activeColor = AbletonRed,
                    modifier = Modifier.weight(1f)
                ) { viewModel.setPunchIn(!state.punchIn) }

                ToggleCard(
                    label = "Punch Out",
                    isOn = state.punchOut,
                    activeColor = AbletonRed,
                    modifier = Modifier.weight(1f)
                ) { viewModel.setPunchOut(!state.punchOut) }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToggleCard(
                    label = "Metronome",
                    isOn = state.metronome,
                    activeColor = AbletonBlue,
                    modifier = Modifier.weight(1f)
                ) { viewModel.setMetronome(!state.metronome) }

                ToggleCard(
                    label = "Loop",
                    isOn = state.loop,
                    activeColor = AbletonYellow,
                    modifier = Modifier.weight(1f)
                ) { viewModel.setLoop(!state.loop) }
            }
        }

        // Loop region controls (visible when loop is on)
        if (state.loop) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = AbletonYellow.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Loop Region", style = MaterialTheme.typography.titleSmall,
                            color = AbletonYellow)
                        Spacer(Modifier.height(8.dp))

                        // Loop start
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start", fontSize = 13.sp, color = AbletonTextSecondary)
                            Text(
                                formatBeatTime(state.loopStart, state.signatureNumerator),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                        val maxLoopRange = if (state.songLength > 0f) state.songLength else 1000f
                        Slider(
                            value = state.loopStart,
                            onValueChange = { viewModel.setLoopStart(it) },
                            valueRange = 0f..maxLoopRange,
                            colors = SliderDefaults.colors(
                                thumbColor = AbletonYellow,
                                activeTrackColor = AbletonYellow,
                                inactiveTrackColor = AbletonCard
                            )
                        )

                        Spacer(Modifier.height(4.dp))

                        // Loop end (shown as end point, controlled as length)
                        val loopEnd = state.loopStart + state.loopLength
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("End", fontSize = 13.sp, color = AbletonTextSecondary)
                            Text(
                                formatBeatTime(loopEnd, state.signatureNumerator),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                        Slider(
                            value = state.loopLength,
                            onValueChange = { viewModel.setLoopLength(it) },
                            valueRange = 1f..maxLoopRange,
                            colors = SliderDefaults.colors(
                                thumbColor = AbletonYellow,
                                activeTrackColor = AbletonYellow,
                                inactiveTrackColor = AbletonCard
                            )
                        )

                        // Quick loop length buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(1, 2, 4, 8, 16).forEach { bars ->
                                val beats = bars * state.signatureNumerator.toFloat()
                                OutlinedButton(
                                    onClick = { viewModel.setLoopLength(beats) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (state.loopLength == beats) AbletonYellow
                                            else AbletonTextSecondary
                                    )
                                ) {
                                    Text("${bars}B", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tempo
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AbletonSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tempo", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "%.1f BPM".format(state.tempo),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = state.tempo,
                        onValueChange = { viewModel.setTempo(it) },
                        valueRange = 20f..999f,
                        colors = SliderDefaults.colors(
                            thumbColor = AbletonAccent,
                            activeTrackColor = AbletonAccent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(onClick = { viewModel.tapTempo() }) {
                            Text("Tap Tempo")
                        }
                    }
                }
            }
        }

        // Undo/Redo row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.undo() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Undo, "Undo", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo")
                }
                OutlinedButton(
                    onClick = { viewModel.redo() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Redo, "Redo", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Redo")
                }
                OutlinedButton(
                    onClick = { viewModel.stopAllClips() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop Clips")
                }
            }
        }

        // Cue points
        if (cuePoints.isNotEmpty()) {
            item {
                Text("Cue Points", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp))
            }
            items(cuePoints) { cue ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { viewModel.jumpToCue(cue.index) },
                    colors = CardDefaults.cardColors(containerColor = AbletonSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cue.name.ifBlank { "Cue ${cue.index + 1}" })
                        Text(
                            formatBeatTime(cue.time, state.signatureNumerator),
                            fontFamily = FontFamily.Monospace,
                            color = AbletonTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isActive) tint.copy(alpha = 0.2f) else AbletonSurface)
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AbletonTextSecondary)
    }
}

@Composable
private fun ToggleCard(
    label: String,
    isOn: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) activeColor.copy(alpha = 0.2f) else AbletonSurface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (isOn) activeColor else AbletonTextSecondary,
                fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun formatBeatTime(beats: Float, numerator: Int): String {
    val bar = (beats / numerator).toInt() + 1
    val beat = (beats % numerator).toInt() + 1
    val sub = ((beats % 1f) * 100).toInt()
    return "%d.%d.%02d".format(bar, beat, sub)
}
