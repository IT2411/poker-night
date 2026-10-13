package com.pokernight.shared.data

import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.ActionId
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionStatus

interface SessionRepository {
    fun saveSession(session: Session)
    fun getSession(id: SessionId): Session?
    fun getActiveSession(): Session?
    fun updateSession(session: Session)
}

interface HandRepository {
    fun saveHand(hand: Hand)
    fun getHand(id: HandId): Hand?
    fun getHandsForSession(sessionId: SessionId): List<Hand>
    fun getActiveHandForSession(sessionId: SessionId): Hand?
    fun updateHand(hand: Hand)
    fun removeHand(handId: HandId)
}

interface ActionRepository {
    fun appendAction(action: ActionEvent)
    fun getActionsForHand(handId: HandId): List<ActionEvent>
    fun getAction(actionId: ActionId): ActionEvent?
    fun removeActionsForHand(handId: HandId)
}

class InMemorySessionRepository : SessionRepository {
    private val sessions = mutableMapOf<SessionId, Session>()

    override fun saveSession(session: Session) {
        sessions[session.id] = session
    }

    override fun getSession(id: SessionId): Session? = sessions[id]

    override fun getActiveSession(): Session? = sessions.values.firstOrNull { it.status == SessionStatus.ACTIVE }

    override fun updateSession(session: Session) {
        sessions[session.id] = session
    }
}

class InMemoryHandRepository : HandRepository {
    private val hands = mutableMapOf<HandId, Hand>()

    override fun saveHand(hand: Hand) {
        hands[hand.id] = hand
    }

    override fun getHand(id: HandId): Hand? = hands[id]

    override fun getHandsForSession(sessionId: SessionId): List<Hand> = hands.values.filter { it.sessionId == sessionId }.sortedBy { it.handNumber }

    override fun getActiveHandForSession(sessionId: SessionId): Hand? = hands.values.firstOrNull {
        it.sessionId == sessionId && it.status != HandStatus.COMPLETED && it.status != HandStatus.CANCELLED
    }

    override fun updateHand(hand: Hand) {
        hands[hand.id] = hand
    }

    override fun removeHand(handId: HandId) {
        hands.remove(handId)
    }
}

class InMemoryActionRepository : ActionRepository {
    private val actions = mutableListOf<ActionEvent>()

    override fun appendAction(action: ActionEvent) {
        require(actions.none { it.id == action.id }) { "Duplicate action submission: ${action.id.value}" }
        actions.add(action)
    }

    override fun getActionsForHand(handId: HandId): List<ActionEvent> = actions.filter { it.handId == handId }.sortedBy { it.sequenceNumber }

    override fun getAction(actionId: ActionId): ActionEvent? = actions.firstOrNull { it.id == actionId }

    override fun removeActionsForHand(handId: HandId) {
        actions.removeAll { it.handId == handId }
    }
}
