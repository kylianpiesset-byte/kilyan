package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "Striker",
    val level: Long = 1L,
    val xp: Long = 0L,
    val credits: Long = 500L,
    val strikePoints: Long = 150L,
    val kills: Long = 0L,
    val deaths: Long = 0L,
    val assists: Long = 0L,
    val wins: Long = 0L,
    val matchesPlayed: Long = 0L,
    val scorestreaksCalled: Long = 0L,
    val ownedItemIds: List<String> = listOf("m4a1_tactical", "skin_m4_standard", "op_vanguard"),
    val claimedPassTiers: List<Long> = emptyList(),
    val equippedPrimaryId: String = "m4a1_tactical",
    val equippedPrimarySkinId: String = "skin_m4_standard",
    val equippedOperatorSkinId: String = "op_vanguard",
    val battlePassPremium: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val kdRatio: Double
        get() = if (deaths <= 0L) kills.toDouble() else kills.toDouble() / deaths.toDouble()

    val xpForNextLevel: Long
        get() = level * 1000L

    val xpProgressFraction: Float
        get() {
            val target = xpForNextLevel.coerceAtLeast(1L)
            return (xp.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        }
}
