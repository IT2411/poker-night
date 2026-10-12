package com.pokernight.shared.domain.progression

import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Street

object StreetManager {
    /**
     * Determines whether the hand has reached an all-in runout where
     * no further betting decisions can ever be made.
     */
    fun isAllInRunout(hand: Hand): Boolean {
        val nonFolded = hand.players.filter { !it.folded }
        if (nonFolded.size < 2) return false
        val playersWithChips = nonFolded.filter { !it.allIn }
        // Runout if 0 or only 1 player has chips left to bet
        return playersWithChips.size <= 1
    }

    /**
     * Advances from PRE_FLOP to FLOP with exactly 3 community cards.
     */
    fun advanceToFlop(
        state: BettingState,
        flopCards: List<Card>,
    ): BettingState {
        require(state.hand.currentStreet == Street.PRE_FLOP) {
            "Can only advance to Flop from Pre-Flop. Current: ${state.hand.currentStreet}"
        }
        require(state.isRoundComplete) { "Cannot advance street while betting is still in progress" }
        require(!state.isHandCompleteByFold) { "Cannot advance street for an uncontested hand won by fold" }
        require(flopCards.size == 3) { "Flop requires exactly 3 community cards, received: ${flopCards.size}" }
        Card.validateNoDuplicates(flopCards)

        val updatedHand = transitionHand(state.hand, Street.FLOP, flopCards)
        return setupNewStreetState(state, updatedHand)
    }

    /**
     * Advances from FLOP to TURN with 1 community card.
     */
    fun advanceToTurn(
        state: BettingState,
        turnCard: Card,
    ): BettingState {
        require(state.hand.currentStreet == Street.FLOP) {
            "Can only advance to Turn from Flop. Current: ${state.hand.currentStreet}"
        }
        require(state.isRoundComplete) { "Cannot advance street while betting is still in progress" }
        require(!state.isHandCompleteByFold) { "Cannot advance street for an uncontested hand won by fold" }
        val newCards = state.hand.communityCards + turnCard
        require(newCards.size == 4) { "Turn requires exactly 4 community cards" }
        Card.validateNoDuplicates(newCards)

        val updatedHand = transitionHand(state.hand, Street.TURN, newCards)
        return setupNewStreetState(state, updatedHand)
    }

    /**
     * Advances from TURN to RIVER with 1 community card.
     */
    fun advanceToRiver(
        state: BettingState,
        riverCard: Card,
    ): BettingState {
        require(state.hand.currentStreet == Street.TURN) {
            "Can only advance to River from Turn. Current: ${state.hand.currentStreet}"
        }
        require(state.isRoundComplete) { "Cannot advance street while betting is still in progress" }
        require(!state.isHandCompleteByFold) { "Cannot advance street for an uncontested hand won by fold" }
        val newCards = state.hand.communityCards + riverCard
        require(newCards.size == 5) { "River requires exactly 5 community cards" }
        Card.validateNoDuplicates(newCards)

        val updatedHand = transitionHand(state.hand, Street.RIVER, newCards)
        return setupNewStreetState(state, updatedHand)
    }

    /**
     * Advances from RIVER to SHOWDOWN.
     */
    fun advanceToShowdown(state: BettingState): BettingState {
        require(state.hand.currentStreet == Street.RIVER) {
            "Can only advance to Showdown from River. Current: ${state.hand.currentStreet}"
        }
        require(state.isRoundComplete) { "Cannot advance to showdown while river betting is still active" }
        require(!state.isHandCompleteByFold) { "Cannot advance to showdown for a hand won by fold" }

        val updatedHand =
            state.hand.copy(
                currentStreet = Street.SHOWDOWN,
                status = HandStatus.SHOWDOWN,
            )

        return state.copy(
            hand = updatedHand,
            activePlayerId = null,
            isRoundComplete = true,
        )
    }

    private fun transitionHand(
        hand: Hand,
        nextStreet: Street,
        communityCards: List<Card>,
    ): Hand {
        // Reset street contributions, keep remainingStack and totalContribution
        val updatedPlayers = hand.players.map { it.copy(streetContribution = 0L) }
        return hand.copy(
            currentStreet = nextStreet,
            communityCards = communityCards,
            players = updatedPlayers,
        )
    }

    private fun setupNewStreetState(
        state: BettingState,
        updatedHand: Hand,
    ): BettingState {
        val isRunout = isAllInRunout(updatedHand)
        val firstActor = if (isRunout) null else findFirstPostFlopActor(updatedHand)

        return state.copy(
            hand = updatedHand,
            currentLiveBet = 0L,
            minRaiseIncrement = state.bigBlind,
            activePlayerId = firstActor,
            playersWhoActed = emptySet(),
            isRoundComplete = isRunout,
        )
    }

    /**
     * Finds the first active, non-folded, non-all-in player clockwise from the dealer button.
     */
    private fun findFirstPostFlopActor(hand: Hand): PlayerId? {
        val sorted = hand.players.sortedBy { it.seatIndex }
        val dealerIndex = sorted.indexOfFirst { it.playerId == hand.dealerPlayerId }
        val count = sorted.size

        for (i in 1..count) {
            val candidate = sorted[(dealerIndex + i) % count]
            if (!candidate.folded && !candidate.allIn) {
                return candidate.playerId
            }
        }
        return null
    }
}
