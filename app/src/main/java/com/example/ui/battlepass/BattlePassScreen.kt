package com.example.ui.battlepass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BattlePassTier
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
fun BattlePassScreen(
    profile: PlayerProfile,
    isBusy: Boolean,
    onUnlockPremium: () -> Unit,
    onClaimTier: (BattlePassTier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Season Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianCard,
            border = BorderStroke(1.dp, StrikeAmber.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth().height(175.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_arena_nightfall),
                    contentDescription = "Saison 01 Opération Zéro",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.55f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ObsidianVoid.copy(alpha = 0.45f), ObsidianVoid.copy(alpha = 0.94f))
                            )
                        )
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("SAISON 01 • OPÉRATION ZÉRO", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                            Text("PASSE DE COMBAT", style = MaterialTheme.typography.headlineMedium, color = StrikeAmber)
                        }
                        if (!profile.battlePassPremium) {
                            Button(
                                onClick = onUnlockPremium,
                                enabled = !isBusy && profile.strikePoints >= 150L,
                                colors = ButtonDefaults.buttonColors(containerColor = StrikeAmber, contentColor = ObsidianVoid),
                                modifier = Modifier.testTag("unlock_bp_premium"),
                            ) {
                                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PREMIUM 150 SP", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SquadGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, SquadGreen),
                            ) {
                                Text(
                                    text = "PREMIUM ACTIF",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SquadGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Niveau Actuel : ${profile.level}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${profile.xp} / ${profile.xpForNextLevel} XP", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        }
                        LinearProgressIndicator(
                            progress = { profile.xpProgressFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = CyberCyan,
                            trackColor = ObsidianElevated,
                        )
                    }
                }
            }
        }

        Text("PALIERS DE RÉCOMPENSES (1 - 10)", style = MaterialTheme.typography.titleMedium, color = CyberCyan)

        GameCatalog.battlePassTiers.forEach { tier ->
            val unlockedByLevel = profile.level >= tier.tier.toLong()
            val unlockedByPass = !tier.isPremiumOnly || profile.battlePassPremium
            val claimed = profile.claimedPassTiers.contains(tier.tier.toLong())
            val canClaim = unlockedByLevel && unlockedByPass && !claimed

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCard,
                border = BorderStroke(
                    1.dp,
                    when {
                        claimed -> SquadGreen.copy(alpha = 0.55f)
                        canClaim -> StrikeAmber
                        else -> TacticalBorder
                    }
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (unlockedByLevel) StrikeAmber.copy(alpha = 0.2f) else ObsidianElevated,
                            border = BorderStroke(1.dp, if (unlockedByLevel) StrikeAmber else TacticalBorder),
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${tier.tier}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (unlockedByLevel) StrikeAmber else TextMuted,
                                )
                            }
                        }

                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tier.rewardTitle, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                if (tier.isPremiumOnly) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = StrikeAmber.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            text = "PREMIUM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StrikeAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(tier.rewardSubtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    }

                    when {
                        claimed -> Icon(Icons.Filled.CheckCircle, contentDescription = "Réclamé", tint = SquadGreen)
                        canClaim -> Button(
                            onClick = { onClaimTier(tier) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = StrikeAmber, contentColor = ObsidianVoid),
                            modifier = Modifier.testTag("claim_tier_${tier.tier}"),
                        ) {
                            Icon(Icons.Filled.MilitaryTech, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RÉCLAMER", fontWeight = FontWeight.Bold)
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (!unlockedByLevel) "NIV. ${tier.tier}" else "PREMIUM",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
