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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType
import com.danglingmind.convotrack.ui.theme.ConvoTrackTopBar
import com.danglingmind.convotrack.ui.theme.LimePrimaryButton

@Composable
fun CreateRideScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var maxRiders by remember { mutableIntStateOf(8) }

    Column(
        Modifier.fillMaxSize().background(CT.surfaceDim).statusBarsPadding(),
    ) {
        ConvoTrackTopBar(title = "New Ride", showBack = true, onBack = onBack)

        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Map placeholder
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(CT.surfaceContainerHigh, CT.surfaceContainerLow)))
                    .border(1.dp, CT.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Map, null, tint = CT.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("ROUTE PREVIEW", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 2.sp)
                }
            }

            LabeledField("RIDE NAME", title, "Coastal Highway Run") { title = it }
            LabeledField("DESTINATION", destination, "Big Sur Overlook", Icons.Filled.Place) { destination = it }

            // Max riders stepper
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MAX RIDERS", style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CT.surfaceContainerLow)
                        .border(1.dp, CT.outlineVariant, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$maxRiders riders", style = CTType.bodyLg, color = CT.onSurface)
                    Spacer(Modifier.weight(1f))
                    StepperButton("−") { if (maxRiders > 2) maxRiders-- }
                    Spacer(Modifier.size(12.dp))
                    StepperButton("+") { if (maxRiders < 20) maxRiders++ }
                }
            }

            Spacer(Modifier.weight(1f))
            LimePrimaryButton(text = "CREATE RIDE", enabled = title.isNotBlank() && destination.isNotBlank(), onClick = onCreated)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = CTType.labelCaps, color = CT.onSurfaceVariant, letterSpacing = 1.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = CT.onSurfaceVariant.copy(alpha = 0.4f)) },
            leadingIcon = leading?.let { { Icon(it, null, tint = CT.primaryFixed) } },
            singleLine = true,
            textStyle = CTType.bodyLg,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CT.primaryFixed,
                unfocusedBorderColor = CT.outlineVariant,
                focusedTextColor = CT.onSurface,
                unfocusedTextColor = CT.onSurface,
                cursorColor = CT.primaryFixed,
                unfocusedContainerColor = CT.surfaceContainerLow,
                focusedContainerColor = CT.surfaceContainerLow,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
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
