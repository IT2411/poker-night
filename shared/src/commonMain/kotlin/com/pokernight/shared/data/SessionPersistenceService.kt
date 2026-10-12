package com.pokernight.shared.data

import com.pokernight.shared.domain.HandInitializer
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.history.StateReplayer
import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.ActionId
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SessionExportData(
    val session: Session,
    val hands: List<Hand>,
    val actionLogs: List<ActionEvent>,
)

class SessionPersistenceService(
    private val sessionRepo: SessionRepository,
    private val handRepo: HandRepository,
    private val actionRepo: ActionRepository,
) {
    private val json = Json { prettyPrint = true }

    fun createSession(session: Session): Session {
        sessionRepo.saveSession(session)
        return session
    }

    fun startNewHand(
        sessionId: SessionId,
        handNumber: Int,
        dealerId: PlayerId,
        timestamp: Long,
    ): BettingState {
        val session =
            sessionRepo.getSession(sessionId)
                ?: throw IllegalArgumentException("Session not found: ${sessionId.value}")

        val handId = HandId("${sessionId.value}-hand-$handNumber")
        val initResult = HandInitializer.initializeHand(session, handId, handNumber, dealerId, timestamp)

        handRepo.saveHand(initResult.hand)
        for (action in initResult.initialActions) {
            actionRepo.appendAction(action)
        }

        return BettingEngine.createPreFlopState(
            hand = initResult.hand,
            bigBlind = session.bigBlind,
            firstToActId = initResult.positions.firstToActPreFlopId,
            blindActions = initResult.initialActions,
        )
    }

    fun recordAction(
        currentState: BettingState,
        request: PlayerActionRequest,
        timestamp: Long,
    ): BettingState {
        val newState = BettingEngine.applyAction(currentState, request, timestamp)
        val newAction = newState.actionHistory.last()

        actionRepo.appendAction(newAction)
        handRepo.updateHand(newState.hand)

        return newState
    }

    fun undoLastAction(
        currentState: BettingState,
        timestamp: Long,
    ): BettingState {
        val actions = actionRepo.getActionsForHand(currentState.hand.id)
        val effective = StateReplayer.filterEffectiveActions(actions)

        require(effective.size > 2) { "Cannot undo blind postings; cancel or reset hand instead" }

        val actionToUndo = effective.last()
        val reversalId = ActionId("${actionToUndo.id.value}-reversal")

        val reversalEvent =
            ActionEvent(
                id = reversalId,
                handId = currentState.hand.id,
                sequenceNumber = actions.size + 1,
                playerId = actionToUndo.playerId,
                street = actionToUndo.street,
                actionType = actionToUndo.actionType,
                amount = actionToUndo.amount,
                toTotalStreetAmount = actionToUndo.toTotalStreetAmount,
                createdAt = timestamp,
                reversalOfActionId = actionToUndo.id,
            )

        actionRepo.appendAction(reversalEvent)

        val session = sessionRepo.getSession(currentState.hand.sessionId)!!
        val reconstructed =
            StateReplayer.replayHand(
                session = session,
                initialHandTemplate = currentState.hand,
                allActions = actionRepo.getActionsForHand(currentState.hand.id),
            )
        handRepo.updateHand(reconstructed.hand)

        return reconstructed
    }

    fun recoverActiveSession(): Pair<Session, BettingState?>? {
        val activeSession = sessionRepo.getActiveSession() ?: return null
        val activeHand = handRepo.getActiveHandForSession(activeSession.id) ?: return activeSession to null

        val actions = actionRepo.getActionsForHand(activeHand.id)
        val state = StateReplayer.replayHand(activeSession, activeHand, actions)
        return activeSession to state
    }

    fun exportSessionToJson(sessionId: SessionId): String {
        val session =
            sessionRepo.getSession(sessionId)
                ?: throw IllegalArgumentException("Session not found: ${sessionId.value}")
        val hands = handRepo.getHandsForSession(sessionId)
        val allActions = hands.flatMap { actionRepo.getActionsForHand(it.id) }

        val export = SessionExportData(session, hands, allActions)
        return json.encodeToString(export)
    }
}
