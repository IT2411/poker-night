package com.pokernight.shared.data

import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersistenceAndRecoveryTest {
    private val p1 = PlayerId("p1")
    private val p2 = PlayerId("p2")
    private val p3 = PlayerId("p3")

    private fun setupService(): Pair<SessionPersistenceService, Session> {
        val sessionRepo = InMemorySessionRepository()
        val handRepo = InMemoryHandRepository()
        val actionRepo = InMemoryActionRepository()

        val service = SessionPersistenceService(sessionRepo, handRepo, actionRepo)

        val session =
            Session(
                id = SessionId("s1"),
                name = "Persistence Test",
                createdAt = 1000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players =
                    listOf(
                        SessionPlayer(p1, 0, 500L, 500L),
                        SessionPlayer(p2, 1, 500L, 500L),
                        SessionPlayer(p3, 2, 500L, 500L),
                    ),
            )
        service.createSession(session)
        return service to session
    }

    @Test
    fun testReplayAndUndoRestoresPriorState() {
        val (service, session) = setupService()

        var state = service.startNewHand(session.id, 1, p1, 1000L)
        assertEquals(p1, state.activePlayerId)
        assertEquals(20L, state.currentLiveBet)

        // p1 calls 20
        state = service.recordAction(state, PlayerActionRequest(p1, ActionType.CALL), 1001L)
        assertEquals(p2, state.activePlayerId)

        // p2 raises to 80
        state = service.recordAction(state, PlayerActionRequest(p2, ActionType.RAISE, 80L), 1002L)
        assertEquals(80L, state.currentLiveBet)
        assertEquals(p3, state.activePlayerId)

        // UNDO the raise!
        val stateAfterUndo = service.undoLastAction(state, 1003L)

        // Must restore state to before the raise:
        // p2 is the active player, live bet is 20 again!
        assertEquals(p2, stateAfterUndo.activePlayerId)
        assertEquals(20L, stateAfterUndo.currentLiveBet)
        assertFalse(stateAfterUndo.isRoundComplete)

        // Verify player 2 stack was restored
        val p2HandPlayer = stateAfterUndo.hand.players.first { it.playerId == p2 }
        assertEquals(10L, p2HandPlayer.streetContribution) // only original SB
        assertEquals(490L, p2HandPlayer.remainingStack)
    }

    @Test
    fun testCrashRecoveryRebuildsExactActiveState() {
        val sessionRepo = InMemorySessionRepository()
        val handRepo = InMemoryHandRepository()
        val actionRepo = InMemoryActionRepository()

        val service = SessionPersistenceService(sessionRepo, handRepo, actionRepo)

        val session =
            Session(
                id = SessionId("s1"),
                name = "Crash Test",
                createdAt = 1000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players =
                    listOf(
                        SessionPlayer(p1, 0, 500L, 500L),
                        SessionPlayer(p2, 1, 500L, 500L),
                        SessionPlayer(p3, 2, 500L, 500L),
                    ),
            )
        service.createSession(session)

        var state = service.startNewHand(session.id, 1, p1, 1000L)
        state = service.recordAction(state, PlayerActionRequest(p1, ActionType.CALL), 1001L)

        // SIMULATE APP CRASH & RESTART
        // Create new service instance pointing to the same persisted repositories
        val restartedService = SessionPersistenceService(sessionRepo, handRepo, actionRepo)

        val recoveryResult = restartedService.recoverActiveSession()
        assertNotNull(recoveryResult)

        val (recoveredSession, recoveredState) = recoveryResult
        assertEquals(session.id, recoveredSession.id)
        assertNotNull(recoveredState)

        // Recovered state must match state prior to crash
        assertEquals(p2, recoveredState.activePlayerId)
        assertEquals(20L, recoveredState.currentLiveBet)
        assertEquals(50L, recoveredState.hand.totalPotChips) // 10 (SB) + 20 (BB) + 20 (p1 Call)
    }

    @Test
    fun testSessionExportProducesValidJson() {
        val (service, session) = setupService()
        val state = service.startNewHand(session.id, 1, p1, 1000L)
        service.recordAction(state, PlayerActionRequest(p1, ActionType.CALL), 1001L)

        val jsonOutput = service.exportSessionToJson(session.id)
        assertTrue(jsonOutput.isNotBlank())
        assertTrue(jsonOutput.contains("Persistence Test"))
        assertTrue(jsonOutput.contains("POST_SMALL_BLIND"))
    }
}
