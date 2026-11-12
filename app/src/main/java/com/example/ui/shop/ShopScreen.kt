package com.example.ui.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CurrencyType
import com.example.data.model.GameCatalog
import com.example.data.model.ItemCategory
import com.example.data.model.PlayerProfile
import com.example.data.model.ShopItem
import com.example.data.model.StrikePointsPack
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
fun ShopScreen(
    profile: PlayerProfile,
    isBusy: Boolean,
    onPurchaseItem: (ShopItem) -> Unit,
    onPurchasePack: (StrikePointsPack) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<ItemCategory?>(null) }
    var pendingPackConfirm by remember { mutableStateOf<StrikePointsPack?>(null) }

    val featuredBundle = remember {
        GameCatalog.shopItems.first { it.id == "bundle_cyber_vanguard" }
    }
    val filteredItems = remember(selectedCategory) {
        if (selectedCategory == null) GameCatalog.shopItems
        else GameCatalog.shopItems.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("BOUTIQUE TACTIQUE", style = MaterialTheme.typography.headlineMedium, color = StrikeAmber)

        // Featured Hero Bundle
        val ownsFeatured = profile.ownedItemIds.contains(featuredBundle.id)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianCard,
            border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth().height(190.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_shop_bundle_cyber),
                    contentDescription = featuredBundle.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.75f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ObsidianVoid.copy(alpha = 0.92f), ObsidianVoid.copy(alpha = 0.35f))
                            )
                        )
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StrikeAmber,
                    ) {
                        Text(
                            text = "PACK VEDETTE • LÉGENDAIRE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ObsidianVoid,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    Column {
                        Text(featuredBundle.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text(featuredBundle.description, style = MaterialTheme.typography.bodyMedium, color = CyberCyan)
                    }

                    Button(
                        onClick = { onPurchaseItem(featuredBundle) },
                        enabled = !ownsFeatured && !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ownsFeatured) SquadGreen else CyberCyan,
                            contentColor = ObsidianVoid,
                        ),
                        modifier = Modifier.testTag("buy_featured_bundle"),
                    ) {
                        Text(
                            text = if (ownsFeatured) "DÉJÀ POSSÉDÉ" else "ACHETER • ${featuredBundle.price} SP",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Category Filter Row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("Tout") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StrikeAmber,
                    selectedLabelColor = ObsidianVoid,
                ),
            )
            ItemCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.labelFr) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StrikeAmber,
                        selectedLabelColor = ObsidianVoid,
                    ),
                )
            }
        }

        // Shop Items
        filteredItems.forEach { item ->
            val owned = profile.ownedItemIds.contains(item.id)
            val rarityColor = Color(item.rarity.accentHex)
            val currencyLabel = if (item.currency == CurrencyType.CREDITS) "CR" else "SP"
            val canAfford = if (item.currency == CurrencyType.CREDITS) {
                profile.credits >= item.price
            } else {
                profile.strikePoints >= item.price
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, if (owned) SquadGreen.copy(alpha = 0.6f) else rarityColor.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = item.rarity.labelFr.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = rarityColor,
                            )
                            Text(
                                text = "• ${item.category.labelFr}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                        Text(item.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(item.description, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (owned) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SquadGreen, modifier = Modifier.size(18.dp))
                            Text("ACQUIS", style = MaterialTheme.typography.labelLarge, color = SquadGreen)
                        }
                    } else {
                        Button(
                            onClick = { onPurchaseItem(item) },
                            enabled = canAfford && !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.currency == CurrencyType.CREDITS) StrikeAmber else CyberCyan,
                                contentColor = ObsidianVoid,
                            ),
                            modifier = Modifier.testTag("buy_item_${item.id}"),
                        ) {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${item.price} $currencyLabel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Simulated In-App Purchase Currency Packs
        Text("RECHARGER POINTS STRIKE (ACHATS INTÉGRÉS SIMULÉS)", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GameCatalog.strikePointsPacks.forEach { pack ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianElevated,
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Stars, contentDescription = null, tint = CyberCyan)
                        Text("+${pack.strikePoints} SP", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
                        Text(pack.bonusLabel, style = MaterialTheme.typography.labelSmall, color = StrikeAmber)
                        Button(
                            onClick = { pendingPackConfirm = pack },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = StrikeAmber, contentColor = ObsidianVoid),
                            modifier = Modifier.fillMaxWidth().testTag("buy_pack_${pack.id}"),
                        ) {
                            Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(pack.title.removePrefix("Pack "), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (pendingPackConfirm != null) {
        val pack = pendingPackConfirm!!
        AlertDialog(
            onDismissRequest = { pendingPackConfirm = null },
            containerColor = ObsidianCard,
            title = { Text("Achat intégré simulé", color = StrikeAmber) },
            text = {
                Text(
                    "Confirmer l'ajout de +${pack.strikePoints} Points Strike (${pack.priceDisplay}) à votre compte Strike Ops ?",
                    color = TextPrimary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPurchasePack(pack)
                        pendingPackConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                ) {
                    Text("CONFIRMER", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPackConfirm = null }) {
                    Text("Annuler", color = TextMuted)
                }
            },
        )
    }
}
