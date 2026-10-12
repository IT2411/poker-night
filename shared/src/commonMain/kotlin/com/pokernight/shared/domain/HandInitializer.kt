package com.pokernight.shared.domain

import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.ActionId
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandPlayer
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.Street
import kotlin.math.min

data class InitializedHandResult(
    val hand: Hand,
    val initialActions: List<ActionEvent>,
    val positions: PositionAssignment,
)

object HandInitializer {
    fun initializeHand(
        session: Session,
        handId: HandId,
        handNumber: Int,
        dealerId: PlayerId,
        timestamp: Long,
    ): InitializedHandResult {
        val eligiblePlayers = SeatingNavigator.getEligibleActivePlayers(session.players)
        require(eligiblePlayers.size >= 2) {
            "Cannot start a hand with fewer than 2 active players with chips"
        }

        val positions = SeatingNavigator.calculatePositions(eligiblePlayers, dealerId)

        val actions = mutableListOf<ActionEvent>()
        var sequenceCounter = 1

        val handPlayers =
            eligiblePlayers.map { sessionPlayer ->
                val pId = sessionPlayer.playerId
                val startStack = sessionPlayer.currentStack

                when (pId) {
                    positions.smallBlindPlayerId -> {
                        val sbToPost = min(startStack, session.smallBlind)
                        val remaining = startStack - sbToPost
                        val isAllIn = remaining == 0L

                        actions.add(
                            ActionEvent(
                                id = ActionId("${handId.value}-act-${sequenceCounter++}"),
                                handId = handId,
                                sequenceNumber = actions.size + 1,
                                playerId = pId,
                                street = Street.PRE_FLOP,
                                actionType = ActionType.POST_SMALL_BLIND,
                                amount = sbToPost,
                                toTotalStreetAmount = sbToPost,
                                createdAt = timestamp,
                            ),
                        )

                        HandPlayer(
                            playerId = pId,
                            seatIndex = sessionPlayer.seatIndex,
                            startingStack = startStack,
                            remainingStack = remaining,
                            totalContribution = sbToPost,
                            streetContribution = sbToPost,
                            allIn = isAllIn,
                        )
                    }

                    positions.bigBlindPlayerId -> {
                        val bbToPost = min(startStack, session.bigBlind)
                        val remaining = startStack - bbToPost
                        val isAllIn = remaining == 0L

                        actions.add(
                            ActionEvent(
                                id = ActionId("${handId.value}-act-${sequenceCounter++}"),
                                handId = handId,
                                sequenceNumber = actions.size + 1,
                                playerId = pId,
                                street = Street.PRE_FLOP,
                                actionType = ActionType.POST_BIG_BLIND,
                                amount = bbToPost,
                                toTotalStreetAmount = bbToPost,
                                createdAt = timestamp,
                            ),
                        )

                        HandPlayer(
                            playerId = pId,
                            seatIndex = sessionPlayer.seatIndex,
                            startingStack = startStack,
                            remainingStack = remaining,
                            totalContribution = bbToPost,
                            streetContribution = bbToPost,
                            allIn = isAllIn,
                        )
                    }

                    else -> {
                        // Regular player entering hand with zero initial contribution
                        HandPlayer(
                            playerId = pId,
                            seatIndex = sessionPlayer.seatIndex,
                            startingStack = startStack,
                            remainingStack = startStack,
                            totalContribution = 0L,
                            streetContribution = 0L,
                            allIn = false,
                        )
                    }
                }
            }

        val hand =
            Hand(
                id = handId,
                sessionId = session.id,
                handNumber = handNumber,
                dealerPlayerId = positions.dealerPlayerId,
                smallBlindPlayerId = positions.smallBlindPlayerId,
                bigBlindPlayerId = positions.bigBlindPlayerId,
                currentStreet = Street.PRE_FLOP,
                status = HandStatus.IN_PROGRESS,
                communityCards = emptyList(),
                players = handPlayers,
                startedAt = timestamp,
            )

        return InitializedHandResult(
            hand = hand,
            initialActions = actions,
            positions = positions,
        )
    }
}
