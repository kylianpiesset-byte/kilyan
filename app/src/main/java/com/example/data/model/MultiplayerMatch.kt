package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class MultiplayerMatch(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val mode: String = "tdm",
    val status: String = "active",
    val alphaScore: Long = 0L,
    val bravoScore: Long = 0L,
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MatchPlayerState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "Striker",
    val team: String = "alpha",
    val x: Double = 3.5,
    val y: Double = 3.5,
    val yaw: Double = 0.0,
    val health: Long = 100L,
    val kills: Long = 0L,
    val deaths: Long = 0L,
    val assists: Long = 0L,
    val killPoints: Long = 0L,
    val activeStreak: String = "",
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
