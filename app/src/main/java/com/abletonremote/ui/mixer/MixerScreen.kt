package com.abletonremote.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abletonremote.data.model.TrackState
import com.abletonremote.ui.theme.*

@Composable
fun MixerScreen(viewModel: MixerViewModel) {
    val tracks by viewModel.tracks.collectAsState()

    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Connect to Ableton to see tracks", color = AbletonTextSecondary)
        }
    } else {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tracks, key = { it.index }) { track ->
                ChannelStrip(
                    track = track,
                    onVolumeChange = { viewModel.setVolume(track.index, it) },
                    onPanChange = { viewModel.setPan(track.index, it) },
                    onMuteToggle = { viewModel.toggleMute(track.index, track.mute) },
                    onSoloToggle = { viewModel.toggleSolo(track.index, track.solo) },
                    onArmToggle = { viewModel.toggleArm(track.index, track.arm) }
                )
            }
        }
    }
}

@Composable
fun ChannelStrip(
    track: TrackState,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onArmToggle: () -> Unit
) {
    val trackColor = if (track.color != 0) Color(track.color or 0xFF000000.toInt()) else AbletonAccent

    Card(
        modifier = Modifier.width(80.dp).fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = AbletonSurface),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(trackColor)
            )
            Text(
                track.name.ifBlank { "Track ${track.index + 1}" },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(4.dp))
            Text("Pan", style = MaterialTheme.typography.labelSmall, color = AbletonTextSecondary,
                fontSize = 8.sp)
            Slider(
                value = track.pan,
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.width(72.dp).height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AbletonTextPrimary,
                    activeTrackColor = AbletonBlue,
                    inactiveTrackColor = AbletonCard
                )
            )

            Row(
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LevelMeter(level = track.meterLeft, modifier = Modifier.width(6.dp))
                Spacer(Modifier.width(2.dp))
                LevelMeter(level = track.meterRight, modifier = Modifier.width(6.dp))
                Spacer(Modifier.width(4.dp))
                VerticalSlider(
                    value = track.volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(32.dp).fillMaxHeight()
                )
            }

            Text(
                text = volumeToDb(track.volume),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = AbletonTextSecondary
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ChannelButton("M", track.mute, AbletonYellow, onMuteToggle)
                ChannelButton("S", track.solo, AbletonBlue, onSoloToggle)
                if (track.canBeArmed) {
                    ChannelButton("R", track.arm, AbletonRed, onArmToggle)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(2.dp))
            .background(AbletonCard)
    ) {
        val meterColor = when {
            level > 0.9f -> AbletonRed
            level > 0.7f -> AbletonYellow
            else -> AbletonGreen
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(level.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(meterColor)
        )
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .requiredWidth(200.dp)
                .wrapContentHeight()
                .align(Alignment.Center)
                .graphicsLayer {
                    rotationZ = -90f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                },
            colors = SliderDefaults.colors(
                thumbColor = AbletonTextPrimary,
                activeTrackColor = AbletonAccent,
                inactiveTrackColor = AbletonCard
            )
        )
    }
}

@Composable
private fun ChannelButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(22.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeColor else AbletonCard,
            contentColor = if (isActive) AbletonDark else AbletonTextSecondary
        ),
        shape = RoundedCornerShape(2.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun volumeToDb(volume: Float): String {
    if (volume <= 0.0001f) return "-inf"
    val db = 20 * kotlin.math.log10(volume.toDouble())
    return "%.1f".format(db)
}
