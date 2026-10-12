package com.pokernight.shared.domain

import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.SessionPlayerStatus

object SeatingNavigator {
    /**
     * Filters for active players eligible to receive cards and post blinds.
     * Must have ACTIVE status and a non-zero stack.
     */
    fun getEligibleActivePlayers(players: List<SessionPlayer>): List<SessionPlayer> =
        players
            .filter { it.status == SessionPlayerStatus.ACTIVE && it.currentStack > 0L }
            .sortedBy { it.seatIndex }

    /**
     * Returns the next active player clockwise from the given reference player.
     */
    fun nextPlayerClockwise(
        eligiblePlayersClockwise: List<SessionPlayer>,
        fromPlayerId: PlayerId,
    ): SessionPlayer {
        require(eligiblePlayersClockwise.isNotEmpty()) { "Eligible players list cannot be empty" }
        val currentIndex = eligiblePlayersClockwise.indexOfFirst { it.playerId == fromPlayerId }
        require(currentIndex != -1) { "Reference player $fromPlayerId is not among eligible players" }

        val nextIndex = (currentIndex + 1) % eligiblePlayersClockwise.size
        return eligiblePlayersClockwise[nextIndex]
    }

    /**
     * Rotates the dealer button clockwise to the next eligible player.
     */
    fun rotateDealer(
        eligiblePlayersClockwise: List<SessionPlayer>,
        currentDealerId: PlayerId,
    ): PlayerId = nextPlayerClockwise(eligiblePlayersClockwise, currentDealerId).playerId

    /**
     * Calculates positions according to official Texas Hold'em rules:
     * - 3+ Players: Dealer -> SB (next clockwise) -> BB (next clockwise) -> UTG (next clockwise).
     * - 2 Players (Heads-Up): Dealer is SB and acts first pre-flop. Opponent is BB.
     */
    fun calculatePositions(
        eligiblePlayersClockwise: List<SessionPlayer>,
        dealerId: PlayerId,
    ): PositionAssignment {
        require(eligiblePlayersClockwise.size >= 2) {
            "At least 2 eligible players required to assign positions"
        }

        val dealerIndex = eligiblePlayersClockwise.indexOfFirst { it.playerId == dealerId }
        require(dealerIndex != -1) {
            "Dealer $dealerId is not among eligible players"
        }

        return if (eligiblePlayersClockwise.size == 2) {
            // Heads-Up: Dealer is Small Blind and acts first pre-flop. Non-dealer is Big Blind.
            val otherPlayer = eligiblePlayersClockwise[(dealerIndex + 1) % 2]
            PositionAssignment(
                dealerPlayerId = dealerId,
                smallBlindPlayerId = dealerId,
                bigBlindPlayerId = otherPlayer.playerId,
                firstToActPreFlopId = dealerId,
                isHeadsUp = true,
            )
        } else {
            // 3+ Players
            val sbPlayer = eligiblePlayersClockwise[(dealerIndex + 1) % eligiblePlayersClockwise.size]
            val bbPlayer = eligiblePlayersClockwise[(dealerIndex + 2) % eligiblePlayersClockwise.size]
            val utgPlayer = eligiblePlayersClockwise[(dealerIndex + 3) % eligiblePlayersClockwise.size]

            PositionAssignment(
                dealerPlayerId = dealerId,
                smallBlindPlayerId = sbPlayer.playerId,
                bigBlindPlayerId = bbPlayer.playerId,
                firstToActPreFlopId = utgPlayer.playerId,
                isHeadsUp = false,
            )
        }
    }
}
