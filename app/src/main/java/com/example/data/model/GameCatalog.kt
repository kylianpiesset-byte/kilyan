package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class CurrencyType {
    CREDITS,
    STRIKE_POINTS,
}

enum class ItemCategory(val labelFr: String) {
    WEAPON("Armes"),
    WEAPON_SKIN("Skins d'armes"),
    OPERATOR("Opérateurs"),
    BUNDLE("Packs"),
}

enum class Rarity(val labelFr: String, val accentHex: Long) {
    STANDARD("Standard", 0xFF8B949E),
    TACTICAL("Tactique", 0xFF00E5FF),
    EPIC("Épique", 0xFFA855F7),
    LEGENDARY("Légendaire", 0xFFFFB800),
}

data class WeaponSpec(
    val id: String,
    val name: String,
    val categoryLabel: String,
    val damage: Int,
    val fireRateMs: Long,
    val magazineSize: Int,
    val reserveAmmo: Int,
    val reloadMs: Long,
    val recoilAmount: Float,
    val spreadHip: Float,
    val spreadAds: Float,
    val rangeMeters: Int,
    val mobility: Int,
    val accentColor: Color,
)

data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val category: ItemCategory,
    val rarity: Rarity,
    val currency: CurrencyType,
    val price: Long,
    val targetWeaponId: String? = null,
    val includedItemIds: List<String> = emptyList(),
    val previewColorHex: Long = 0xFF00E5FF,
)

data class StrikePointsPack(
    val id: String,
    val title: String,
    val strikePoints: Long,
    val bonusLabel: String,
    val priceDisplay: String,
)

data class BattlePassTier(
    val tier: Int,
    val rewardId: String,
    val rewardTitle: String,
    val rewardSubtitle: String,
    val isPremiumOnly: Boolean,
    val creditsReward: Long = 0L,
    val strikePointsReward: Long = 0L,
    val itemRewardId: String? = null,
)

data class ScorestreakSpec(
    val id: String,
    val name: String,
    val shortName: String,
    val costPoints: Int,
    val durationSeconds: Int,
    val description: String,
)

object GameCatalog {
    val weapons = listOf(
        WeaponSpec(
            id = "m4a1_tactical",
            name = "M4A1 Tactique",
            categoryLabel = "Fusil d'assaut",
            damage = 28,
            fireRateMs = 105L,
            magazineSize = 30,
            reserveAmmo = 150,
            reloadMs = 1500L,
            recoilAmount = 0.022f,
            spreadHip = 0.032f,
            spreadAds = 0.006f,
            rangeMeters = 45,
            mobility = 80,
            accentColor = Color(0xFF00E5FF),
        ),
        WeaponSpec(
            id = "ak47_apex",
            name = "AK-47 Apex",
            categoryLabel = "Fusil lourd",
            damage = 38,
            fireRateMs = 140L,
            magazineSize = 28,
            reserveAmmo = 140,
            reloadMs = 1750L,
            recoilAmount = 0.036f,
            spreadHip = 0.042f,
            spreadAds = 0.008f,
            rangeMeters = 52,
            mobility = 72,
            accentColor = Color(0xFFFFB800),
        ),
        WeaponSpec(
            id = "mp5_phantom",
            name = "MP5 Phantom",
            categoryLabel = "Mitraillette SMG",
            damage = 23,
            fireRateMs = 78L,
            magazineSize = 35,
            reserveAmmo = 175,
            reloadMs = 1200L,
            recoilAmount = 0.016f,
            spreadHip = 0.024f,
            spreadAds = 0.007f,
            rangeMeters = 30,
            mobility = 94,
            accentColor = Color(0xFF22C55E),
        ),
        WeaponSpec(
            id = "vector_volt",
            name = "Vector Volt",
            categoryLabel = "SMG Haute Cadence",
            damage = 21,
            fireRateMs = 64L,
            magazineSize = 32,
            reserveAmmo = 192,
            reloadMs = 1150L,
            recoilAmount = 0.019f,
            spreadHip = 0.026f,
            spreadAds = 0.009f,
            rangeMeters = 26,
            mobility = 98,
            accentColor = Color(0xFFA855F7),
        ),
    )

    val scorestreaks = listOf(
        ScorestreakSpec(
            id = "uav_drone",
            name = "Drone Radar (UAV)",
            shortName = "DRONE",
            costPoints = 300,
            durationSeconds = 15,
            description = "Révèle tous les ennemis sur le radar tactique pendant 15s.",
        ),
        ScorestreakSpec(
            id = "sentry_turret",
            name = "Tourelle Auto",
            shortName = "TOURELLE",
            costPoints = 500,
            durationSeconds = 20,
            description = "Déploie une tourelle autonome qui verrouille les ennemis à vue.",
        ),
        ScorestreakSpec(
            id = "airstrike",
            name = "Frappe Aérienne",
            shortName = "FRAPPE",
            costPoints = 800,
            durationSeconds = 4,
            description = "Bombardement de précision sur la zone ennemie la plus dense.",
        ),
    )

    val shopItems = listOf(
        ShopItem(
            id = "ak47_apex",
            name = "AK-47 Apex",
            description = "Fusil d'assaut haut calibre à fort pouvoir d'arrêt.",
            category = ItemCategory.WEAPON,
            rarity = Rarity.TACTICAL,
            currency = CurrencyType.CREDITS,
            price = 350L,
            previewColorHex = 0xFFFFB800,
        ),
        ShopItem(
            id = "mp5_phantom",
            name = "MP5 Phantom",
            description = "Mitraillette ultra-mobile idéale pour le rush et le tir au jugé.",
            category = ItemCategory.WEAPON,
            rarity = Rarity.TACTICAL,
            currency = CurrencyType.CREDITS,
            price = 300L,
            previewColorHex = 0xFF22C55E,
        ),
        ShopItem(
            id = "vector_volt",
            name = "Vector Volt",
            description = "Cadence de tir extrême pour les combats rapprochés.",
            category = ItemCategory.WEAPON,
            rarity = Rarity.EPIC,
            currency = CurrencyType.CREDITS,
            price = 480L,
            previewColorHex = 0xFFA855F7,
        ),
        ShopItem(
            id = "skin_cyber_flux",
            name = "M4A1 Cyber Flux",
            description = "Finition carbone néon cyan avec traceurs plasma.",
            category = ItemCategory.WEAPON_SKIN,
            rarity = Rarity.LEGENDARY,
            currency = CurrencyType.STRIKE_POINTS,
            price = 120L,
            targetWeaponId = "m4a1_tactical",
            previewColorHex = 0xFF00E5FF,
        ),
        ShopItem(
            id = "skin_obsidian_gold",
            name = "Obsidienne Dorée",
            description = "Alliage noir mat et gravures dorées de prestige.",
            category = ItemCategory.WEAPON_SKIN,
            rarity = Rarity.LEGENDARY,
            currency = CurrencyType.CREDITS,
            price = 450L,
            targetWeaponId = "m4a1_tactical",
            previewColorHex = 0xFFFFB800,
        ),
        ShopItem(
            id = "skin_crimson_spec",
            name = "Spectre Carmin",
            description = "Revêtement tactique rouge infrarouge anti-reflet.",
            category = ItemCategory.WEAPON_SKIN,
            rarity = Rarity.EPIC,
            currency = CurrencyType.CREDITS,
            price = 250L,
            previewColorHex = 0xFFFF3B4E,
        ),
        ShopItem(
            id = "op_specter_zero",
            name = "Opérateur Specter-0",
            description = "Armure furtive Night-Ops avec visière holo cyan.",
            category = ItemCategory.OPERATOR,
            rarity = Rarity.EPIC,
            currency = CurrencyType.STRIKE_POINTS,
            price = 140L,
            previewColorHex = 0xFF00E5FF,
        ),
        ShopItem(
            id = "op_titan_gold",
            name = "Opérateur Titan Or",
            description = "Exosquelette lourd d'assaut édition Commandant.",
            category = ItemCategory.OPERATOR,
            rarity = Rarity.LEGENDARY,
            currency = CurrencyType.CREDITS,
            price = 600L,
            previewColorHex = 0xFFFFB800,
        ),
        ShopItem(
            id = "bundle_cyber_vanguard",
            name = "Pack Cyber Avant-Garde",
            description = "Inclut M4A1 Cyber Flux, Vector Volt et Opérateur Specter-0.",
            category = ItemCategory.BUNDLE,
            rarity = Rarity.LEGENDARY,
            currency = CurrencyType.STRIKE_POINTS,
            price = 240L,
            includedItemIds = listOf("skin_cyber_flux", "vector_volt", "op_specter_zero"),
            previewColorHex = 0xFF00E5FF,
        ),
    )

    val strikePointsPacks = listOf(
        StrikePointsPack("sp_pack_100", "Pack Recrue", 100L, "+10% Bonus", "1,99 € (Simulé)"),
        StrikePointsPack("sp_pack_500", "Pack Commando", 550L, "Meilleure offre", "4,99 € (Simulé)"),
        StrikePointsPack("sp_pack_1200", "Pack Commandant", 1350L, "+150 SP Offerts", "9,99 € (Simulé)"),
    )

    val battlePassTiers = listOf(
        BattlePassTier(1, "bp_1", "200 Crédits", "Bonus de déploiement", false, creditsReward = 200L),
        BattlePassTier(2, "bp_2", "50 Points Strike", "Monnaie Premium", true, strikePointsReward = 50L),
        BattlePassTier(3, "bp_3", "Skin Spectre Carmin", "Camouflage d'arme", false, itemRewardId = "skin_crimson_spec"),
        BattlePassTier(4, "bp_4", "300 Crédits", "Fonds d'armurerie", false, creditsReward = 300L),
        BattlePassTier(5, "bp_5", "MP5 Phantom", "Mitraillette SMG", false, itemRewardId = "mp5_phantom"),
        BattlePassTier(6, "bp_6", "100 Points Strike", "Réserve tactique", true, strikePointsReward = 100L),
        BattlePassTier(7, "bp_7", "Opérateur Specter-0", "Tenue Night-Ops", true, itemRewardId = "op_specter_zero"),
        BattlePassTier(8, "bp_8", "450 Crédits", "Prime d'escouade", false, creditsReward = 450L),
        BattlePassTier(9, "bp_9", "Obsidienne Dorée", "Camouflage Légendaire", true, itemRewardId = "skin_obsidian_gold"),
        BattlePassTier(10, "bp_10", "M4A1 Cyber Flux", "Plan d'arme Mythique", false, itemRewardId = "skin_cyber_flux"),
    )

    fun weaponById(id: String): WeaponSpec =
        weapons.firstOrNull { it.id == id } ?: weapons.first()

    fun skinAccentColor(skinId: String): Color = when (skinId) {
        "skin_cyber_flux" -> Color(0xFF00E5FF)
        "skin_obsidian_gold" -> Color(0xFFFFB800)
        "skin_crimson_spec" -> Color(0xFFFF3B4E)
        else -> Color(0xFF94A3B8)
    }

    fun skinDisplayName(skinId: String): String = when (skinId) {
        "skin_cyber_flux" -> "Cyber Flux"
        "skin_obsidian_gold" -> "Obsidienne Dorée"
        "skin_crimson_spec" -> "Spectre Carmin"
        else -> "Finition Standard"
    }

    fun operatorDisplayName(opId: String): String = when (opId) {
        "op_specter_zero" -> "Specter-0"
        "op_titan_gold" -> "Titan Or"
        else -> "Avant-Garde"
    }
}
