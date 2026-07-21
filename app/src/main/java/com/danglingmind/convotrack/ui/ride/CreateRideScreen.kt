package com.danglingmind.convotrack.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton

// A single route stop. Ordered waypoints are built as START -> WAYPOINT* -> DESTINATION,
// matching the iOS `CreateWaypoint` payload shape in CreateRideView.swift.
private class RouteStop(val id: Long, text: String = "") {
    var text by mutableStateOf(text)
}

@Composable
fun CreateRideScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var startStop by remember { mutableStateOf("") }
    var destStop by remember { mutableStateOf("") }
    val middleStops = remember { mutableStateListOf<RouteStop>() }
    var maxRiders by remember { mutableIntStateOf(5) }
    var nextId by remember { mutableStateOf(0L) }

    val canCreate = title.isNotBlank() && startStop.isNotBlank() && destStop.isNotBlank()

    Column(Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding()) {
        ConvoTrackTopBar(title = "New Ride", showBack = true, onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Ride title
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("RIDE TITLE")
                StopField(
                    value = title,
                    placeholder = "e.g., Midnight Canyon Run",
                    onChange = { title = it },
                )
            }

            // Route stops
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("ROUTE")

                // Start
                StopRow(dotColor = CT.tertiaryFixed) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("START LOCATION", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                        StopField(startStop, "Search start point...", Icons.Filled.MyLocation) { startStop = it }
                    }
                }

                // Middle waypoints
                middleStops.forEach { stop ->
                    StopRow(dotColor = CT.primaryFixed.copy(alpha = 0.6f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                StopField(stop.text, "Search stop...", Icons.Filled.Place) { stop.text = it }
                            }
                            Icon(
                                Icons.Filled.RemoveCircle,
                                contentDescription = "Remove stop",
                                tint = CT.errorColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { middleStops.remove(stop) },
                            )
                        }
                    }
                }

                // Add stop — indented to align with the field edges (dot 14 + spacing 12)
                Row(
                    Modifier
                        .padding(start = 26.dp)
                        .clickable { middleStops.add(RouteStop(nextId++)) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.AddCircleOutline, null, tint = CT.primaryFixed, modifier = Modifier.size(16.dp))
                    Text("ADD STOP", style = CTType.labelCaps, color = CT.primaryFixed, letterSpacing = 2.sp)
                }

                // Destination
                StopRow(dotColor = CT.primaryFixed) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("DESTINATION", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                        StopField(destStop, "Search destination...", Icons.Filled.Flag) { destStop = it }
                    }
                }
            }

            // Route preview + stats
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("ROUTE PREVIEW")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(CT.surfaceContainerHigh, CT.surfaceContainerLow)))
                        .border(1.dp, CT.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Map, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RouteStatCard(Icons.Filled.Timer, "EST. TIME", "--", Modifier.weight(1f))
                    RouteStatCard(Icons.Filled.Place, "DISTANCE", "--", Modifier.weight(1f))
                }
            }

            // Max riders
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("MAX RIDERS")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CT.surfaceContainerLow)
                        .border(1.dp, CT.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$maxRiders riders", style = CTType.bodyLg, color = CT.onSurface)
                    Spacer(Modifier.weight(1f))
                    StepperButton("−") { if (maxRiders > 2) maxRiders-- }
                    Spacer(Modifier.size(12.dp))
                    // Free tier caps at 5; Pro raises this to 25 (see MembershipScreen).
                    StepperButton("+") { if (maxRiders < 25) maxRiders++ }
                }
            }

            LimePrimaryButton(text = "CREATE RIDE", enabled = canCreate, onClick = onCreated)

            Text(
                "READY FOR DEPARTURE",
                style = CTType.labelCaps,
                color = CT.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 8.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp)
}

/** A stop row: the coloured route dot on the left, the field(s) on the right. */
@Composable
private fun StopRow(dotColor: Color, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(2.dp, CT.surfaceDim, CircleShape),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun StopField(
    value: String,
    placeholder: String,
    leading: ImageVector? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
        leadingIcon = leading?.let { { Icon(it, null, tint = CT.primaryFixed, modifier = Modifier.size(18.dp)) } },
        singleLine = true,
        textStyle = CTType.bodyMd,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CT.primaryFixed,
            unfocusedBorderColor = CT.outline.copy(alpha = 0.3f),
            focusedTextColor = CT.onSurface,
            unfocusedTextColor = CT.onSurface,
            cursorColor = CT.primaryFixed,
            unfocusedContainerColor = CT.surfaceContainerHigh,
            focusedContainerColor = CT.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RouteStatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CT.surfaceContainerLow)
            .border(1.dp, CT.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = CT.primaryFixed, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
            Text(value, style = CTType.dataMono, color = CT.onSurface)
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(CT.surfaceContainerHighest).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CTType.headlineMd.copy(fontWeight = FontWeight.Bold), color = CT.primaryFixed)
    }
}
