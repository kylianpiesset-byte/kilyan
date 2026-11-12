package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

data class SquadMemberInfo(
    val uid: String,
    val callsign: String,
    val level: Int,
    val isReady: Boolean,
    val team: String,
) {
    fun encode(): String {
        val safeCallsign = callsign.replace("|", "_").take(24)
        return "$uid|$safeCallsign|$level|${if (isReady) 1 else 0}|$team"
    }

    companion object {
        fun decode(raw: String): SquadMemberInfo? {
            val parts = raw.split("|")
            if (parts.size < 5) return null
            return SquadMemberInfo(
                uid = parts[0],
                callsign = parts[1],
                level = parts[2].toIntOrNull() ?: 1,
                isReady = parts[3] == "1",
                team = parts[4],
            )
        }
    }
}

@IgnoreExtraProperties
data class SquadLobby(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val lobbyCode: String = "",
    val mode: String = "tdm",
    val status: String = "waiting",
    val activeMatchId: String = "",
    val memberUids: List<String> = emptyList(),
    val memberRoster: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedMembers: List<SquadMemberInfo>
        get() = memberRoster.mapNotNull { SquadMemberInfo.decode(it) }
}
