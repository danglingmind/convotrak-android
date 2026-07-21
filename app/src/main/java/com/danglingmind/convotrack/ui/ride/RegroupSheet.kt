package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton

/**
 * Regroup reasons, mirroring iOS `CoordinationOverlayView.RegroupReason`.
 *
 * NOTE for backend wiring: EMERGENCY must emit the `ride:emergency` socket event —
 * the other three emit `ride:regroup`. See CLAUDE.md architectural decisions.
 */
enum class RegroupReason(val label: String, val icon: ImageVector, val isEmergency: Boolean = false) {
    FUEL("Fuel Stop", Icons.Filled.LocalGasStation),
    FOOD("Food Stop", Icons.Filled.Restaurant),
    SCENIC("Scenic Stop", Icons.Filled.PhotoCamera),
    EMERGENCY("Emergency", Icons.Filled.Warning, isEmergency = true),
}

private enum class LocationMode { MY_POSITION, SEARCHED }

@Composable
fun RegroupSheet(onBroadcast: (RegroupReason) -> Unit) {
    var selected by remember { mutableStateOf<RegroupReason?>(null) }
    var mode by remember { mutableStateOf(LocationMode.MY_POSITION) }
    var query by remember { mutableStateOf("") }

    val blocked = selected == null || (mode == LocationMode.SEARCHED && query.isBlank())

    Column(Modifier.padding(bottom = 32.dp)) {
        // Header
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Flag, null, tint = CT.primaryFixed, modifier = Modifier.size(32.dp))
            Text("REGROUP", style = CTType.headlineLg, color = CT.primaryFixed)
        }

        Text(
            "Select a reason for the broadcast:",
            style = CTType.bodyLg,
            color = CT.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        )

        // Reason grid (2x2)
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RegroupReason.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { reason ->
                        ReasonTile(
                            reason = reason,
                            isSelected = selected == reason,
                            modifier = Modifier.weight(1f),
                            onClick = { selected = reason },
                        )
                    }
                }
            }
        }

        // Meet location
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("MEET LOCATION", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CT.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            ) {
                ModeTab("My Position", mode == LocationMode.MY_POSITION, Modifier.weight(1f)) {
                    mode = LocationMode.MY_POSITION
                    query = ""
                }
                ModeTab("Search Location", mode == LocationMode.SEARCHED, Modifier.weight(1f)) {
                    mode = LocationMode.SEARCHED
                }
            }

            if (mode == LocationMode.SEARCHED) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search meet point...", color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
                    singleLine = true,
                    textStyle = CTType.bodyMd,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CT.primaryFixed,
                        unfocusedBorderColor = CT.outlineVariant,
                        focusedTextColor = CT.onSurface,
                        unfocusedTextColor = CT.onSurface,
                        cursorColor = CT.primaryFixed,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
            LimePrimaryButton(
                text = "BROADCAST REGROUP",
                enabled = !blocked,
                onClick = { selected?.let(onBroadcast) },
            )
        }
    }
}

@Composable
private fun ReasonTile(
    reason: RegroupReason,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (reason.isEmergency) CT.errorColor else CT.primaryFixed
    val borderColor = when {
        isSelected -> CT.primaryFixed
        reason.isEmergency -> CT.errorColor.copy(alpha = 0.4f)
        else -> CT.outlineVariant.copy(alpha = 0.3f)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (reason.isEmergency) CT.errorContainer.copy(alpha = 0.1f) else Color(0xFF1C1C1C))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(reason.icon, null, tint = accent, modifier = Modifier.size(28.dp))
        Text(reason.label, style = CTType.labelCaps, color = accent, letterSpacing = 1.sp)
    }
}

@Composable
private fun ModeTab(label: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (isActive) CT.primaryFixed else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = CTType.toggleLabel,
            color = if (isActive) CT.onPrimaryFixed else CT.onSurfaceVariant,
        )
    }
}
