package com.example.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PlayerProfile
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SquadGreen
import com.example.ui.theme.StrikeAmber
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ProfileScreen(
    profile: PlayerProfile,
    isBusy: Boolean,
    onUpdateCallsign: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var callsignInput by remember(profile.callsign) { mutableStateOf(profile.callsign) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("DOSSIER DE COMBAT & STATS", style = MaterialTheme.typography.headlineMedium, color = StrikeAmber)

        // Callsign Editor Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, TacticalBorder),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = CyberCyan)
                    Text("IDENTITÉ OPÉRATEUR", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = callsignInput,
                        onValueChange = { callsignInput = it.take(24) },
                        singleLine = true,
                        label = { Text("Indicatif d'appel") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                        modifier = Modifier.weight(1f).testTag("callsign_input"),
                    )
                    Button(
                        onClick = { onUpdateCallsign(callsignInput) },
                        enabled = !isBusy && callsignInput.trim().length >= 3,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.height(52.dp).testTag("save_callsign_button"),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Enregistrer", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Career Stats Grid
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, TacticalBorder),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = StrikeAmber)
                    Text("STATISTIQUES MULTIJOUEUR", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("RATIO K/D", "%.2f".format(profile.kdRatio), StrikeAmber, Modifier.weight(1f))
                    StatTile("ÉLIMINATIONS", "${profile.kills}", CyberCyan, Modifier.weight(1f))
                    StatTile("MORTS", "${profile.deaths}", AlertCrimson, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("ASSISTANCES", "${profile.assists}", TextPrimary, Modifier.weight(1f))
                    StatTile("VICTOIRES", "${profile.wins}", SquadGreen, Modifier.weight(1f))
                    StatTile("SCORESTREAKS", "${profile.scorestreaksCalled}", StrikeAmber, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("PARTIES JOUÉES", "${profile.matchesPlayed}", CyberCyan, Modifier.weight(1f))
                    StatTile("OBJETS DÉBLOQUÉS", "${profile.ownedItemIds.size}", SquadGreen, Modifier.weight(1f))
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            border = BorderStroke(1.dp, AlertCrimson),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button"),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = AlertCrimson)
            Spacer(modifier = Modifier.width(8.dp))
            Text("DÉCONNEXION DU COMPTE", color = AlertCrimson, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ObsidianElevated,
        border = BorderStroke(1.dp, TacticalBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}
