package com.example.ui.armory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GameCatalog
import com.example.data.model.PlayerProfile
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
fun ArmoryScreen(
    profile: PlayerProfile,
    onEquipWeapon: (String) -> Unit,
    onEquipSkin: (String) -> Unit,
    onEquipOperator: (String) -> Unit,
    onGoToShop: () -> Unit,
) {
    val skinOptions = listOf(
        "skin_m4_standard" to "Standard Tactique",
        "skin_crimson_spec" to "Spectre Carmin",
        "skin_obsidian_gold" to "Obsidienne Dorée",
        "skin_cyber_flux" to "Cyber Flux",
    )
    val operatorOptions = listOf(
        "op_vanguard" to "Avant-Garde",
        "op_specter_zero" to "Specter-0",
        "op_titan_gold" to "Titan Or",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ARMURERIE & ÉQUIPEMENT", style = MaterialTheme.typography.headlineMedium, color = StrikeAmber)

        // Primary Weapons
        Text("ARMES PRINCIPALES", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
        GameCatalog.weapons.forEach { weapon ->
            val isOwned = profile.ownedItemIds.contains(weapon.id)
            val isEquipped = profile.equippedPrimaryId == weapon.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCard,
                border = BorderStroke(1.5.dp, if (isEquipped) StrikeAmber else TacticalBorder),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(weapon.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text(
                                "${weapon.categoryLabel} • Chargeur ${weapon.magazineSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                        when {
                            isEquipped -> StatusPill("ÉQUIPÉ", StrikeAmber)
                            isOwned -> Button(
                                onClick = { onEquipWeapon(weapon.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                                modifier = Modifier.testTag("equip_weapon_${weapon.id}"),
                            ) {
                                Text("ÉQUIPER", fontWeight = FontWeight.Bold)
                            }
                            else -> Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianElevated,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onGoToShop),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    Text("BOUTIQUE", style = MaterialTheme.typography.labelSmall, color = StrikeAmber)
                                }
                            }
                        }
                    }
                    WeaponStatRow("Dégâts", weapon.damage / 45f, weapon.accentColor)
                    WeaponStatRow("Mobilité", weapon.mobility / 100f, CyberCyan)
                    WeaponStatRow("Portée", weapon.rangeMeters / 60f, SquadGreen)
                }
            }
        }

        // Weapon Skins
        Text("CAMOUFLAGES D'ARMES", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            skinOptions.forEach { (id, title) ->
                val owned = profile.ownedItemIds.contains(id)
                val equipped = profile.equippedPrimarySkinId == id
                val color = GameCatalog.skinAccentColor(id)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (equipped) color.copy(alpha = 0.2f) else ObsidianCard,
                    border = BorderStroke(1.5.dp, if (equipped) color else TacticalBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (owned) onEquipSkin(id) else onGoToShop()
                        }
                        .testTag("skin_$id"),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(color))
                        Text(title, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 2)
                        Icon(
                            imageVector = if (!owned) Icons.Filled.Lock else Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (equipped) color else TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Operators
        Text("OPÉRATEURS", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            operatorOptions.forEach { (id, title) ->
                val owned = profile.ownedItemIds.contains(id)
                val equipped = profile.equippedOperatorSkinId == id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (equipped) StrikeAmber.copy(alpha = 0.2f) else ObsidianCard,
                    border = BorderStroke(1.5.dp, if (equipped) StrikeAmber else TacticalBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (owned) onEquipOperator(id) else onGoToShop()
                        }
                        .testTag("operator_$id"),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = when {
                                equipped -> "DÉPLOYÉ"
                                owned -> "ÉQUIPER"
                                else -> "BOUTIQUE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (equipped) StrikeAmber else if (owned) CyberCyan else TextMuted,
                        )
                    }
                }
            }
        }

        // Scorestreaks Loadout Reference
        Text("SÉRIES DE POINTS (SCORESTREAKS)", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
        GameCatalog.scorestreaks.forEach { streak ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, TacticalBorder),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(streak.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(streak.description, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    StatusPill("${streak.costPoints} PTS", StrikeAmber)
                }
            }
        }
    }
}

@Composable
private fun WeaponStatRow(label: String, fraction: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(0.28f))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0.1f, 1f) },
            modifier = Modifier.weight(0.72f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = ObsidianElevated,
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
