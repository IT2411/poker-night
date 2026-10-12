package com.pokernight.shared.domain.betting

import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.ActionId
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.PlayerId
import kotlin.math.max
import kotlin.math.min

object BettingEngine {

    const val MIN_CHIP_INCREMENT = 5L

    fun createPreFlopState(
        hand: Hand,
        smallBlind: Long,
        bigBlind: Long,
        firstToActId: PlayerId,
        blindActions: List<ActionEvent>,
    ): BettingState {
        val highestStreetContribution = hand.players.maxOfOrNull { it.streetContribution } ?: bigBlind
        return BettingState(
            hand = hand,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            currentLiveBet = max(bigBlind, highestStreetContribution),
            minRaiseIncrement = MIN_CHIP_INCREMENT,
            activePlayerId = firstToActId,
            playersWhoActed = emptySet(),
            isRoundComplete = false,
            isHandCompleteByFold = false,
            actionHistory = blindActions,
        )
    }

    fun getLegalActions(state: BettingState, playerId: PlayerId): LegalActions {
        val player = state.hand.players.firstOrNull { it.playerId == playerId }
            ?: return LegalActions(playerId, canCheck = false, canFold = false, canCall = false)

        if (state.isRoundComplete || state.isHandCompleteByFold || player.folded || player.allIn || state.activePlayerId != playerId) {
            return LegalActions(playerId, canCheck = false, canFold = false, canCall = false)
        }

        val otherNonFolded = state.hand.players.filter { !it.folded && it.playerId != player.playerId }

        val amountToCall = max(0L, state.currentLiveBet - player.streetContribution)
        val canCheck = amountToCall == 0L
        val canFold = !canCheck

        val canCall = amountToCall > 0L && player.remainingStack > 0L
        val actualCallAmount = min(amountToCall, player.remainingStack)
        val isCallAllIn = canCall && actualCallAmount == player.remainingStack

        val canAllIn = player.remainingStack > 0L
        val playerTotalMaxCommitment = player.streetContribution + player.remainingStack

        val canBet = state.currentLiveBet == 0L && player.remainingStack > 0L
        val minBetAmount = if (canBet) min(MIN_CHIP_INCREMENT, player.remainingStack) else 0L
        val maxBetAmount = if (canBet) player.remainingStack else 0L

        // Raising requires opponents with chips behind
        val opponentsCanMatchRaise = otherNonFolded.any {
            !it.allIn && (it.streetContribution + it.remainingStack) > state.currentLiveBet
        }
        val canRaise = state.currentLiveBet > 0L && opponentsCanMatchRaise && playerTotalMaxCommitment > state.currentLiveBet

        val minFullRaiseTo = state.currentLiveBet + MIN_CHIP_INCREMENT
        val minRaiseTo = if (canRaise) min(minFullRaiseTo, playerTotalMaxCommitment) else 0L
        val maxRaiseTo = if (canRaise) playerTotalMaxCommitment else 0L

        return LegalActions(
            playerId = playerId,
            canCheck = canCheck,
            canFold = canFold || canCheck,
            canCall = canCall,
            callAmount = actualCallAmount,
            isCallAllIn = isCallAllIn,
            canBet = canBet,
            minBetAmount = minBetAmount,
            maxBetAmount = maxBetAmount,
            canRaise = canRaise,
            minRaiseTo = minRaiseTo,
            maxRaiseTo = maxRaiseTo,
            canAllIn = canAllIn,
            allInAmount = player.remainingStack,
        )
    }

    fun applyAction(
        state: BettingState,
        request: PlayerActionRequest,
        timestamp: Long,
    ): BettingState {
        require(!state.isRoundComplete) { "Cannot act when betting round is already complete" }
        require(!state.isHandCompleteByFold) { "Cannot act when hand has ended" }
        require(state.activePlayerId == request.playerId) {
            "It is not player ${request.playerId.value}'s turn to act"
        }

        val legal = getLegalActions(state, request.playerId)
        val player = state.hand.players.first { it.playerId == request.playerId }
        val seq = state.actionHistory.size + 1
        val actionId = ActionId("${state.hand.id.value}-act-$seq")

        return when (request.actionType) {
            ActionType.CHECK -> {
                require(legal.canCheck) { "Cannot check when facing a bet" }
                val event = ActionEvent(
                    id = actionId,
                    handId = state.hand.id,
                    sequenceNumber = seq,
                    playerId = player.playerId,
                    street = state.hand.currentStreet,
                    actionType = ActionType.CHECK,
                    amount = 0L,
                    toTotalStreetAmount = player.streetContribution,
                    createdAt = timestamp,
                )
                val updatedActed = state.playersWhoActed + player.playerId
                advanceState(state, state.hand, updatedActed, state.currentLiveBet, state.minRaiseIncrement, event)
            }

            ActionType.FOLD -> {
                val event = ActionEvent(
                    id = actionId,
                    handId = state.hand.id,
                    sequenceNumber = seq,
                    playerId = player.playerId,
                    street = state.hand.currentStreet,
                    actionType = ActionType.FOLD,
                    amount = 0L,
                    toTotalStreetAmount = player.streetContribution,
                    createdAt = timestamp,
                )
                val updatedPlayers = state.hand.players.map {
                    if (it.playerId == player.playerId) it.copy(folded = true) else it
                }
                val nonFolded = updatedPlayers.filter { !it.folded }
                if (nonFolded.size == 1) {
                    return state.copy(
                        hand = state.hand.copy(players = updatedPlayers),
                        isRoundComplete = true,
                        isHandCompleteByFold = true,
                        activePlayerId = null,
                        actionHistory = state.actionHistory + event,
                    )
                }
                val updatedHand = state.hand.copy(players = updatedPlayers)
                val updatedActed = state.playersWhoActed + player.playerId
                advanceState(state, updatedHand, updatedActed, state.currentLiveBet, state.minRaiseIncrement, event)
            }

            ActionType.CALL -> {
                require(legal.canCall) { "Cannot call in current state" }
                val callAmount = legal.callAmount
                val newRemaining = player.remainingStack - callAmount
                val newTotalContr = player.totalContribution + callAmount
                val newStreetContr = player.streetContribution + callAmount
                val isAllIn = newRemaining == 0L

                val updatedPlayer = player.copy(
                    remainingStack = newRemaining,
                    totalContribution = newTotalContr,
                    streetContribution = newStreetContr,
                    allIn = isAllIn,
                )

                val event = ActionEvent(
                    id = actionId,
                    handId = state.hand.id,
                    sequenceNumber = seq,
                    playerId = player.playerId,
                    street = state.hand.currentStreet,
                    actionType = if (isAllIn) ActionType.ALL_IN else ActionType.CALL,
                    amount = callAmount,
                    toTotalStreetAmount = newStreetContr,
                    createdAt = timestamp,
                )

                val updatedPlayers = state.hand.players.map {
                    if (it.playerId == player.playerId) updatedPlayer else it
                }
                val updatedHand = state.hand.copy(players = updatedPlayers)
                val updatedActed = state.playersWhoActed + player.playerId
                advanceState(state, updatedHand, updatedActed, state.currentLiveBet, state.minRaiseIncrement, event)
            }

            ActionType.BET -> {
                require(legal.canBet) { "Cannot make an opening bet in current state" }
                require(request.targetAmount in legal.minBetAmount..legal.maxBetAmount) {
                    "Bet amount ${request.targetAmount} must be between ${legal.minBetAmount} and ${legal.maxBetAmount}"
                }

                val betAmount = request.targetAmount
                val newRemaining = player.remainingStack - betAmount
                val newTotalContr = player.totalContribution + betAmount
                val newStreetContr = player.streetContribution + betAmount
                val isAllIn = newRemaining == 0L

                val updatedPlayer = player.copy(
                    remainingStack = newRemaining,
                    totalContribution = newTotalContr,
                    streetContribution = newStreetContr,
                    allIn = isAllIn,
                )

                val event = ActionEvent(
                    id = actionId,
                    handId = state.hand.id,
                    sequenceNumber = seq,
                    playerId = player.playerId,
                    street = state.hand.currentStreet,
                    actionType = if (isAllIn) ActionType.ALL_IN else ActionType.BET,
                    amount = betAmount,
                    toTotalStreetAmount = newStreetContr,
                    createdAt = timestamp,
                )

                val updatedPlayers = state.hand.players.map {
                    if (it.playerId == player.playerId) updatedPlayer else it
                }
                val updatedHand = state.hand.copy(players = updatedPlayers)
                val updatedActed = setOf(player.playerId)
                val newMinIncrement = max(MIN_CHIP_INCREMENT, betAmount)
                advanceState(state, updatedHand, updatedActed, newStreetContr, newMinIncrement, event)
            }

            ActionType.RAISE -> {
                require(legal.canRaise) { "Cannot raise in current state" }
                val target = request.targetAmount
                require(target in legal.minRaiseTo..legal.maxRaiseTo) {
                    "Raise to $target must be between ${legal.minRaiseTo} and ${legal.maxRaiseTo}"
                }

                val addedAmount = target - player.streetContribution
                require(addedAmount in 1L..player.remainingStack) { "Illegal raise addition: $addedAmount" }

                val newRemaining = player.remainingStack - addedAmount
                val isAllIn = newRemaining == 0L
                val raiseDiff = target - state.currentLiveBet
                val isFullRaise = raiseDiff >= MIN_CHIP_INCREMENT

                val updatedPlayer = player.copy(
                    remainingStack = newRemaining,
                    totalContribution = player.totalContribution + addedAmount,
                    streetContribution = target,
                    allIn = isAllIn,
                )

                val event = ActionEvent(
                    id = actionId,
                    handId = state.hand.id,
                    sequenceNumber = seq,
                    playerId = player.playerId,
                    street = state.hand.currentStreet,
                    actionType = if (isAllIn) ActionType.ALL_IN else ActionType.RAISE,
                    amount = addedAmount,
                    toTotalStreetAmount = target,
                    createdAt = timestamp,
                )

                val updatedPlayers = state.hand.players.map {
                    if (it.playerId == player.playerId) updatedPlayer else it
                }
                val updatedHand = state.hand.copy(players = updatedPlayers)
                val updatedActed = if (isFullRaise) setOf(player.playerId) else state.playersWhoActed + player.playerId
                val newMinIncrement = if (isFullRaise) raiseDiff else state.minRaiseIncrement
                advanceState(state, updatedHand, updatedActed, target, newMinIncrement, event)
            }

            ActionType.ALL_IN -> {
                require(legal.canAllIn) { "Cannot go all-in" }
                val allInAdded = player.remainingStack
                val targetStreet = player.streetContribution + allInAdded

                if (state.currentLiveBet == 0L) {
                    applyAction(state, PlayerActionRequest(player.playerId, ActionType.BET, min(targetStreet, legal.maxBetAmount)), timestamp)
                } else if (targetStreet > state.currentLiveBet && legal.canRaise) {
                    applyAction(state, PlayerActionRequest(player.playerId, ActionType.RAISE, min(targetStreet, legal.maxRaiseTo)), timestamp)
                } else {
                    applyAction(state, PlayerActionRequest(player.playerId, ActionType.CALL), timestamp)
                }
            }

            else -> throw IllegalArgumentException("Unsupported action: ${request.actionType}")
        }
    }

    private fun advanceState(
        state: BettingState,
        updatedHand: Hand,
        updatedActed: Set<PlayerId>,
        currentLiveBet: Long,
        minRaiseIncrement: Long,
        newEvent: ActionEvent,
    ): BettingState {
        val nonFolded = updatedHand.players.filter { !it.folded }
        val eligibleToAct = nonFolded.filter { !it.allIn }

        val allMatched = nonFolded.all { it.allIn || it.streetContribution == currentLiveBet }
        val allEligibleHaveActed = eligibleToAct.all { updatedActed.contains(it.playerId) }

        val roundFinished = (allMatched && allEligibleHaveActed) || eligibleToAct.size <= 1 && allMatched
        val nextActor = if (roundFinished) null else findNextActorClockwise(updatedHand, state.activePlayerId!!)

        return state.copy(
            hand = updatedHand,
            currentLiveBet = currentLiveBet,
            minRaiseIncrement = minRaiseIncrement,
            activePlayerId = nextActor,
            playersWhoActed = updatedActed,
            isRoundComplete = roundFinished,
            actionHistory = state.actionHistory + newEvent,
        )
    }

    private fun findNextActorClockwise(hand: Hand, currentActorId: PlayerId): PlayerId? {
        val sorted = hand.players.sortedBy { it.seatIndex }
        val currentIndex = sorted.indexOfFirst { it.playerId == currentActorId }
        val count = sorted.size

        for (i in 1 until count) {
            val candidate = sorted[(currentIndex + i) % count]
            if (!candidate.folded && !candidate.allIn) {
                return candidate.playerId
            }
        }
        return null
    }
}
