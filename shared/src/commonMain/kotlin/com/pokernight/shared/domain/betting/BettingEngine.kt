package com.pokernight.shared.domain.betting

import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.ActionId
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.PlayerId
import kotlin.math.max
import kotlin.math.min

object BettingEngine {
    /**
     * Creates the initial BettingState when a hand starts pre-flop after blinds have been posted.
     */
    fun createPreFlopState(
        hand: Hand,
        bigBlind: Long,
        firstToActId: PlayerId,
        blindActions: List<ActionEvent>,
    ): BettingState {
        val highestStreetContribution = hand.players.maxOfOrNull { it.streetContribution } ?: bigBlind
        return BettingState(
            hand = hand,
            bigBlind = bigBlind,
            currentLiveBet = max(bigBlind, highestStreetContribution),
            minRaiseIncrement = bigBlind,
            activePlayerId = firstToActId,
            playersWhoActed = emptySet(),
            isRoundComplete = false,
            isHandCompleteByFold = false,
            actionHistory = blindActions,
        )
    }

    /**
     * Computes the available legal actions for a given player.
     */
    fun getLegalActions(
        state: BettingState,
        playerId: PlayerId,
    ): LegalActions {
        val player =
            state.hand.players.firstOrNull { it.playerId == playerId }
                ?: return LegalActions(playerId, canCheck = false, canFold = false, canCall = false)

        if (state.isRoundComplete || state.isHandCompleteByFold || player.folded || player.allIn || state.activePlayerId != playerId) {
            return LegalActions(playerId, canCheck = false, canFold = false, canCall = false)
        }

        val amountToCall = max(0L, state.currentLiveBet - player.streetContribution)
        val canCheck = amountToCall == 0L
        val canFold = !canCheck // In standard poker, one can always fold, but folding when check is free is discouraged

        val canCall = amountToCall > 0L && player.remainingStack > 0L
        val actualCallAmount = min(amountToCall, player.remainingStack)
        val isCallAllIn = canCall && actualCallAmount == player.remainingStack

        val canAllIn = player.remainingStack > 0L
        val allInTargetStreet = player.streetContribution + player.remainingStack

        // Betting (opening bet when live bet is 0)
        val canBet = state.currentLiveBet == 0L && player.remainingStack > 0L
        val minBetAmount = if (canBet) min(state.bigBlind, player.remainingStack) else 0L
        val maxBetAmount = if (canBet) player.remainingStack else 0L

        // Raising (when facing an existing live bet)
        val minFullRaiseTo = state.currentLiveBet + state.minRaiseIncrement
        val canRaise = state.currentLiveBet > 0L && allInTargetStreet > state.currentLiveBet
        val minRaiseTo = if (canRaise) min(minFullRaiseTo, allInTargetStreet) else 0L
        val maxRaiseTo = if (canRaise) allInTargetStreet else 0L

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

    /**
     * Applies a player action, validating rules and updating the state deterministically.
     */
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
                require(
                    legal.canCheck,
                ) { "Cannot check when facing a bet. Amount to call is ${state.currentLiveBet - player.streetContribution}" }
                val event =
                    ActionEvent(
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
                val event =
                    ActionEvent(
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
                val updatedPlayers =
                    state.hand.players.map {
                        if (it.playerId == player.playerId) it.copy(folded = true) else it
                    }
                val nonFolded = updatedPlayers.filter { !it.folded }
                if (nonFolded.size == 1) {
                    // Everyone else folded: hand is immediately won by the last remaining player
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

                val updatedPlayer =
                    player.copy(
                        remainingStack = newRemaining,
                        totalContribution = newTotalContr,
                        streetContribution = newStreetContr,
                        allIn = isAllIn,
                    )

                val event =
                    ActionEvent(
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

                val updatedPlayers =
                    state.hand.players.map {
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

                val updatedPlayer =
                    player.copy(
                        remainingStack = newRemaining,
                        totalContribution = newTotalContr,
                        streetContribution = newStreetContr,
                        allIn = isAllIn,
                    )

                val event =
                    ActionEvent(
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

                val updatedPlayers =
                    state.hand.players.map {
                        if (it.playerId == player.playerId) updatedPlayer else it
                    }
                val updatedHand = state.hand.copy(players = updatedPlayers)
                // Opening bet reopens action to all other non-folded players
                val updatedActed = setOf(player.playerId)
                val newMinIncrement = max(state.bigBlind, betAmount)
                advanceState(state, updatedHand, updatedActed, newStreetContr, newMinIncrement, event)
            }

            ActionType.RAISE -> {
                require(legal.canRaise) { "Cannot raise in current state" }
                val target = request.targetAmount
                require(target in legal.minRaiseTo..legal.maxRaiseTo) {
                    "Raise to $target must be between ${legal.minRaiseTo} and ${legal.maxRaiseTo}"
                }

                val addedAmount = target - player.streetContribution
                require(addedAmount in 1L..player.remainingStack) { "Illegal raise chip addition: $addedAmount" }

                val newRemaining = player.remainingStack - addedAmount
                val isAllIn = newRemaining == 0L
                val raiseDiff = target - state.currentLiveBet
                val isFullRaise = raiseDiff >= state.minRaiseIncrement

                val updatedPlayer =
                    player.copy(
                        remainingStack = newRemaining,
                        totalContribution = player.totalContribution + addedAmount,
                        streetContribution = target,
                        allIn = isAllIn,
                    )

                val event =
                    ActionEvent(
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

                val updatedPlayers =
                    state.hand.players.map {
                        if (it.playerId == player.playerId) updatedPlayer else it
                    }
                val updatedHand = state.hand.copy(players = updatedPlayers)

                // If full raise: reset acted players to re-open betting.
                // If short all-in raise: only the raiser is recorded; players who already acted at the previous full level cannot re-raise, but must still call.
                val updatedActed =
                    if (isFullRaise) {
                        setOf(player.playerId)
                    } else {
                        state.playersWhoActed + player.playerId
                    }

                val newMinIncrement = if (isFullRaise) raiseDiff else state.minRaiseIncrement
                advanceState(state, updatedHand, updatedActed, target, newMinIncrement, event)
            }

            ActionType.ALL_IN -> {
                require(legal.canAllIn) { "Cannot go all-in" }
                val allInAdded = player.remainingStack
                val targetStreet = player.streetContribution + allInAdded

                if (state.currentLiveBet == 0L) {
                    applyAction(state, PlayerActionRequest(player.playerId, ActionType.BET, targetStreet), timestamp)
                } else if (targetStreet > state.currentLiveBet) {
                    applyAction(state, PlayerActionRequest(player.playerId, ActionType.RAISE, targetStreet), timestamp)
                } else {
                    applyAction(state, PlayerActionRequest(player.playerId, ActionType.CALL), timestamp)
                }
            }

            else -> throw IllegalArgumentException("Unsupported live betting action: ${request.actionType}")
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

        // Round completes when:
        // 1. At most 1 player has chips remaining and all non-folded players have matched the live bet (or are all-in)
        // 2. All eligible players have acted AND their street contributions match the live bet
        val allMatched = nonFolded.all { it.allIn || it.streetContribution == currentLiveBet }
        val allEligibleHaveActed = eligibleToAct.all { updatedActed.contains(it.playerId) }

        val roundFinished = (allMatched && allEligibleHaveActed) || eligibleToAct.size <= 1 && allMatched

        val nextActor =
            if (roundFinished) {
                null
            } else {
                findNextActorClockwise(updatedHand, state.activePlayerId!!)
            }

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

    private fun findNextActorClockwise(
        hand: Hand,
        currentActorId: PlayerId,
    ): PlayerId? {
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
