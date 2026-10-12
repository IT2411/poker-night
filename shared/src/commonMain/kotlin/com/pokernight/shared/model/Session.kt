package com.pokernight.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "PlayerId cannot be blank" }
    }
}

@Serializable
data class SessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SessionId cannot be blank" }
    }
}

@Serializable
data class Player(
    val id: PlayerId,
    val displayName: String,
    val createdAt: Long,
) {
    init {
        require(displayName.isNotBlank()) { "Player displayName cannot be blank" }
        require(createdAt > 0L) { "Player createdAt timestamp must be positive" }
    }
}

@Serializable
enum class SessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
}

@Serializable
enum class SessionPlayerStatus {
    ACTIVE,
    SITTING_OUT,
    BUSTED_OUT,
    LEFT_SESSION,
}

@Serializable
data class SessionPlayer(
    val playerId: PlayerId,
    val seatIndex: Int,
    val startingStack: Long,
    val currentStack: Long,
    val status: SessionPlayerStatus = SessionPlayerStatus.ACTIVE,
) {
    init {
        require(seatIndex >= 0) { "Seat index must be non-negative: $seatIndex" }
        require(startingStack >= 0L) { "Starting stack cannot be negative: $startingStack" }
        require(currentStack >= 0L) { "Current stack cannot be negative: $currentStack" }
    }
}

@Serializable
data class Session(
    val id: SessionId,
    val name: String,
    val createdAt: Long,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val chipValueInMinorUnits: Int = 1, // e.g., 1 chip = 1 paise
    val smallBlind: Long,
    val bigBlind: Long,
    val dealerPlayerId: PlayerId,
    val players: List<SessionPlayer>,
    val rulesVersion: String = "1.0",
) {
    init {
        require(name.isNotBlank()) { "Session name cannot be blank" }
        require(createdAt > 0L) { "CreatedAt must be positive" }
        require(chipValueInMinorUnits > 0) { "Chip denomination must be positive" }
        require(smallBlind > 0L) { "Small blind must be greater than zero: $smallBlind" }
        require(bigBlind >= smallBlind) { "Big blind ($bigBlind) must be >= small blind ($smallBlind)" }
        require(players.size >= 2) { "Session must have at least 2 players to start" }

        // Invariant: Player IDs must be unique
        val uniquePlayerIds = players.map { it.playerId }.toSet()
        require(uniquePlayerIds.size == players.size) { "All players in a session must have unique PlayerIds" }

        // Invariant: Seat indices must be distinct
        val uniqueSeats = players.map { it.seatIndex }.toSet()
        require(uniqueSeats.size == players.size) { "Every player must occupy a distinct seat index" }

        // Invariant: Dealer must be in the active players list
        require(players.any { it.playerId == dealerPlayerId }) {
            "Dealer $dealerPlayerId must be one of the session players"
        }
    }

    fun getSeatedPlayersClockwise(): List<SessionPlayer> = players.sortedBy { it.seatIndex }
}
