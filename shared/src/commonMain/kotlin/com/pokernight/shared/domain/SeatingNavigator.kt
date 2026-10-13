package com.pokernight.shared.domain

import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.SessionPlayerStatus

object SeatingNavigator {

    fun getEligibleActivePlayers(players: List<SessionPlayer>): List<SessionPlayer> = players
        .filter { it.status == SessionPlayerStatus.ACTIVE && it.currentStack > 0L }
        .sortedBy { it.seatIndex }

    fun nextPlayerClockwise(
        eligiblePlayersClockwise: List<SessionPlayer>,
        fromPlayerId: PlayerId,
    ): SessionPlayer {
        require(eligiblePlayersClockwise.isNotEmpty()) { "Eligible players list cannot be empty" }
        val currentIndex = eligiblePlayersClockwise.indexOfFirst { it.playerId == fromPlayerId }
        val index = if (currentIndex != -1) (currentIndex + 1) % eligiblePlayersClockwise.size else 0
        return eligiblePlayersClockwise[index]
    }

    fun rotateDealer(
        eligiblePlayersClockwise: List<SessionPlayer>,
        currentDealerId: PlayerId,
    ): PlayerId = nextPlayerClockwise(eligiblePlayersClockwise, currentDealerId).playerId

    fun getActiveDealerId(session: Session): PlayerId {
        val eligible = getEligibleActivePlayers(session.players)
        return if (eligible.any { it.playerId == session.dealerPlayerId }) {
            session.dealerPlayerId
        } else {
            eligible.first().playerId
        }
    }

    fun calculatePositions(
        eligiblePlayersClockwise: List<SessionPlayer>,
        dealerId: PlayerId,
    ): PositionAssignment {
        require(eligiblePlayersClockwise.size >= 2) {
            "At least 2 eligible active players required to assign positions"
        }

        val foundIndex = eligiblePlayersClockwise.indexOfFirst { it.playerId == dealerId }
        val dealerIndex = if (foundIndex != -1) foundIndex else 0
        val effectiveDealerId = eligiblePlayersClockwise[dealerIndex].playerId

        return if (eligiblePlayersClockwise.size == 2) {
            val otherPlayer = eligiblePlayersClockwise[(dealerIndex + 1) % 2]
            PositionAssignment(
                dealerPlayerId = effectiveDealerId,
                smallBlindPlayerId = effectiveDealerId,
                bigBlindPlayerId = otherPlayer.playerId,
                firstToActPreFlopId = effectiveDealerId,
                isHeadsUp = true,
            )
        } else {
            val sbPlayer = eligiblePlayersClockwise[(dealerIndex + 1) % eligiblePlayersClockwise.size]
            val bbPlayer = eligiblePlayersClockwise[(dealerIndex + 2) % eligiblePlayersClockwise.size]
            val utgPlayer = eligiblePlayersClockwise[(dealerIndex + 3) % eligiblePlayersClockwise.size]

            PositionAssignment(
                dealerPlayerId = effectiveDealerId,
                smallBlindPlayerId = sbPlayer.playerId,
                bigBlindPlayerId = bbPlayer.playerId,
                firstToActPreFlopId = utgPlayer.playerId,
                isHeadsUp = false,
            )
        }
    }
}
