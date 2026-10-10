package com.abletonremote.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    SERVERS("servers", "Servers", Icons.Default.Dns),
    TRANSPORT("transport", "Transport", Icons.Default.PlayCircle),
    MIXER("mixer", "Mixer", Icons.Default.Tune)
}
