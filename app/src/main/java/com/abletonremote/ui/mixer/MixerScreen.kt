package com.abletonremote.ui.mixer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val stripWidth = if (isLandscape) 120.dp else 100.dp

    Column(modifier = Modifier.fillMaxSize()) {
        // Add track button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = { viewModel.createAudioTrack() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AbletonAccent.copy(alpha = 0.2f),
                    contentColor = AbletonAccent
                )
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Audio Track")
            }
        }

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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks, key = { it.index }) { track ->
                    ChannelStrip(
                        track = track,
                        stripWidth = stripWidth,
                        isLandscape = isLandscape,
                        onVolumeChange = { viewModel.setVolume(track.index, it) },
                        onPanChange = { viewModel.setPan(track.index, it) },
                        onMuteToggle = { viewModel.toggleMute(track.index, track.mute) },
                        onSoloToggle = { viewModel.toggleSolo(track.index, track.solo) },
                        onArmToggle = { viewModel.toggleArm(track.index, track.arm) },
                        onInputTypeChange = { viewModel.setInputType(track.index, it) },
                        onInputChannelChange = { viewModel.setInputChannel(track.index, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelStrip(
    track: TrackState,
    stripWidth: androidx.compose.ui.unit.Dp,
    isLandscape: Boolean,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onArmToggle: () -> Unit,
    onInputTypeChange: (String) -> Unit,
    onInputChannelChange: (String) -> Unit
) {
    val trackColor = if (track.color != 0) Color(track.color or 0xFF000000.toInt()) else AbletonAccent

    Card(
        modifier = Modifier.width(stripWidth).fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = AbletonSurface),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(trackColor)
            )

            // Track name
            Text(
                track.name.ifBlank { "Track ${track.index + 1}" },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Input routing dropdown
            InputRoutingDropdown(
                label = track.inputRoutingType.ifBlank { "Input" },
                options = track.availableInputTypes,
                onSelect = onInputTypeChange,
                modifier = Modifier.fillMaxWidth()
            )
            if (track.availableInputChannels.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                InputRoutingDropdown(
                    label = track.inputRoutingChannel.ifBlank { "Ch" },
                    options = track.availableInputChannels,
                    onSelect = onInputChannelChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))

            // Pan
            Text("Pan", fontSize = 9.sp, color = AbletonTextSecondary)
            Slider(
                value = track.pan,
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AbletonTextPrimary,
                    activeTrackColor = AbletonBlue,
                    inactiveTrackColor = AbletonCard
                )
            )

            // Meters + volume fader
            Row(
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LevelMeter(level = track.meterLeft, modifier = Modifier.width(8.dp))
                Spacer(Modifier.width(2.dp))
                LevelMeter(level = track.meterRight, modifier = Modifier.width(8.dp))
                Spacer(Modifier.width(4.dp))
                VerticalSlider(
                    value = track.volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(40.dp).fillMaxHeight()
                )
            }

            // dB readout
            Text(
                text = volumeToDb(track.volume),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AbletonTextSecondary
            )

            Spacer(Modifier.height(6.dp))

            // M / S / R buttons — stacked in portrait, row in landscape
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ChannelButton("M", track.mute, AbletonYellow, onMuteToggle, 36.dp)
                    ChannelButton("S", track.solo, AbletonBlue, onSoloToggle, 36.dp)
                    if (track.canBeArmed) {
                        ChannelButton("R", track.arm, AbletonRed, onArmToggle, 36.dp)
                    }
                }
            } else {
                // Portrait: bigger buttons, stacked vertically
                ChannelButtonWide("MUTE", track.mute, AbletonYellow, onMuteToggle)
                Spacer(Modifier.height(4.dp))
                ChannelButtonWide("SOLO", track.solo, AbletonBlue, onSoloToggle)
                if (track.canBeArmed) {
                    Spacer(Modifier.height(4.dp))
                    ChannelButtonWide("REC", track.arm, AbletonRed, onArmToggle)
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun InputRoutingDropdown(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { if (options.isNotEmpty()) expanded = true },
            color = AbletonCard,
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = AbletonTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown, null,
                    modifier = Modifier.size(14.dp),
                    tint = AbletonTextSecondary
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 12.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
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
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 36.dp
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(size),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeColor else AbletonCard,
            contentColor = if (isActive) AbletonDark else AbletonTextSecondary
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChannelButtonWide(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(36.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeColor else AbletonCard,
            contentColor = if (isActive) AbletonDark else AbletonTextSecondary
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun volumeToDb(volume: Float): String {
    if (volume <= 0.0001f) return "-inf"
    val db = 20 * kotlin.math.log10(volume.toDouble())
    return "%.1f".format(db)
}
