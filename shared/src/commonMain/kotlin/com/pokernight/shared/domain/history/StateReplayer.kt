package com.pokernight.shared.domain.history

import com.pokernight.shared.domain.HandInitializer
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.progression.StreetManager
import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.Street

object StateReplayer {

    fun filterEffectiveActions(actions: List<ActionEvent>): List<ActionEvent> {
        val reversedIds = actions.mapNotNull { it.reversalOfActionId }.toSet()
        return actions.filter { !reversedIds.contains(it.id) && it.reversalOfActionId == null }
    }

    fun replayHand(
        session: Session,
        initialHandTemplate: Hand,
        allActions: List<ActionEvent>,
    ): BettingState {
        val effective = filterEffectiveActions(allActions)

        val initResult = HandInitializer.initializeHand(
            session = session,
            handId = initialHandTemplate.id,
            handNumber = initialHandTemplate.handNumber,
            dealerId = initialHandTemplate.dealerPlayerId,
            timestamp = initialHandTemplate.startedAt,
        )

        var state = BettingEngine.createPreFlopState(
            hand = initResult.hand,
            smallBlind = session.smallBlind,
            bigBlind = session.bigBlind,
            firstToActId = initResult.positions.firstToActPreFlopId,
            blindActions = initResult.initialActions,
        )

        val bettingActions = effective.drop(initResult.initialActions.size)
        var currentCommunityCardIndex = 0

        for (action in bettingActions) {
            val req = when (action.actionType) {
                ActionType.CHECK -> PlayerActionRequest(action.playerId, ActionType.CHECK)
                ActionType.FOLD -> PlayerActionRequest(action.playerId, ActionType.FOLD)
                ActionType.CALL -> PlayerActionRequest(action.playerId, ActionType.CALL)
                ActionType.BET -> PlayerActionRequest(action.playerId, ActionType.BET, action.amount)
                ActionType.RAISE -> PlayerActionRequest(action.playerId, ActionType.RAISE, action.toTotalStreetAmount)
                ActionType.ALL_IN -> PlayerActionRequest(action.playerId, ActionType.ALL_IN)
                else -> null
            }

            if (req != null) {
                state = BettingEngine.applyAction(state, req, action.createdAt)
            }

            if (state.isRoundComplete && !state.isHandCompleteByFold && initialHandTemplate.communityCards.isNotEmpty()) {
                state = advanceIfApplicable(state, initialHandTemplate, currentCommunityCardIndex)
                currentCommunityCardIndex = state.hand.communityCards.size
            }
        }

        return state
    }

    private fun advanceIfApplicable(
        state: BettingState,
        template: Hand,
        currentCardCount: Int,
    ): BettingState {
        val totalAvailable = template.communityCards.size
        return when (state.hand.currentStreet) {
            Street.PRE_FLOP -> {
                if (totalAvailable >= 3 && currentCardCount < 3) {
                    StreetManager.advanceToFlop(state, template.communityCards.take(3))
                } else {
                    state
                }
            }
            Street.FLOP -> {
                if (totalAvailable >= 4 && currentCardCount < 4) {
                    StreetManager.advanceToTurn(state, template.communityCards[3])
                } else {
                    state
                }
            }
            Street.TURN -> {
                if (totalAvailable >= 5 && currentCardCount < 5) {
                    StreetManager.advanceToRiver(state, template.communityCards[4])
                } else {
                    state
                }
            }
            Street.RIVER -> StreetManager.advanceToShowdown(state)
            Street.SHOWDOWN -> state
        }
    }
}
