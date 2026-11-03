package com.example.game.multiplayer

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Lobby(
    @DocumentId val id: String = "",
    val lobbyCode: String = "",
    val name: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val status: String = "waiting", // "waiting", "racing", "finished"
    val maxPlayers: Int = 4,
    val playerCount: Int = 1,
    val trackId: String = "pro_speedway",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlayerState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val carColor: String = "#E53935",
    val isReady: Boolean = false,
    val isHost: Boolean = false,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotY: Float = 0f,
    val speedKmh: Float = 0f,
    val isNitro: Boolean = false,
    val isBraking: Boolean = false,
    val currentLap: Int = 1,
    val checkpointIndex: Int = 0,
    val lapTime: Float = 0f,
    val bestLapTime: Float = 0f,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val trackId: String = "pro_speedway",
    val bestLapTime: Float = 0f,
    val vehicleClass: String = "Formula V",
    @ServerTimestamp val recordedAt: Timestamp? = null
)

enum class CarColorOption(val label: String, val hex: String, val colorValue: Long) {
    CRIMSON_RED("Crimson Red", "#E53935", 0xFFE53935),
    ELECTRIC_CYAN("Electric Cyan", "#00E5FF", 0xFF00E5FF),
    NEON_PURPLE("Neon Purple", "#A855F7", 0xFFA855F7),
    HYPER_GREEN("Hyper Green", "#22C55E", 0xFF22C55E),
    SUNBURST_GOLD("Sunburst Gold", "#F59E0B", 0xFFF59E0B),
    SUNSET_ORANGE("Sunset Orange", "#FF6B00", 0xFFFF6B00)
}
