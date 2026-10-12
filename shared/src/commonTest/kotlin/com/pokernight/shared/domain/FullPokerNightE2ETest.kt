package com.pokernight.shared.domain

import com.pokernight.shared.data.InMemoryActionRepository
import com.pokernight.shared.data.InMemoryHandRepository
import com.pokernight.shared.data.InMemorySessionRepository
import com.pokernight.shared.data.SessionPersistenceService
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.domain.progression.StreetManager
import com.pokernight.shared.domain.showdown.HandResolutionManager
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandPlayer
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.PotType
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.Street
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FullPokerNightE2ETest {

    private val p1 = PlayerId("p1-Alice")
    private val p2 = PlayerId("p2-Bob")
    private val p3 = PlayerId("p3-Charlie")
    private val p4 = PlayerId("p4-Dave")

    private fun createFourPlayerSession(startingStack: Long = 1000L): Session = Session(
        id = SessionId("session-e2e"),
        name = "Championship Night",
        createdAt = 1000L,
        smallBlind = 10L,
        bigBlind = 20L,
        dealerPlayerId = p1,
        players = listOf(
            SessionPlayer(p1, seatIndex = 0, startingStack = startingStack, currentStack = startingStack),
            SessionPlayer(p2, seatIndex = 1, startingStack = startingStack, currentStack = startingStack),
            SessionPlayer(p3, seatIndex = 2, startingStack = startingStack, currentStack = startingStack),
            SessionPlayer(p4, seatIndex = 3, startingStack = startingStack, currentStack = startingStack),
        ),
    )

    @Test
    fun test01_SessionSetupAndDealerSelection() {
        val session = createFourPlayerSession()
        assertEquals(4, session.players.size)
        assertEquals(p1, session.dealerPlayerId)
        assertEquals(10L, session.smallBlind)
        assertEquals(20L, session.bigBlind)
        assertEquals(4000L, session.players.sumOf { it.currentStack })
    }

    @Test
    fun test02_CompleteHandThroughAllStreets() {
        val session = createFourPlayerSession()
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        var state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p4, init.initialActions)

        // Preflop: p4 calls 20, p1 calls 20, p2 calls 10 (reaches 20), p3 checks (BB)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p4, ActionType.CALL), 1001L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CALL), 1002L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CALL), 1003L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1004L)
        assertTrue(state.isRoundComplete)
        assertEquals(80L, state.hand.totalPotChips)

        // Deal Flop: Ah, Kd, Qc
        state = StreetManager.advanceToFlop(state, listOf(Card.parse("Ah"), Card.parse("Kd"), Card.parse("Qc")))
        assertEquals(Street.FLOP, state.hand.currentStreet)
        assertEquals(3, state.hand.communityCards.size)
        // Flop check-through (p2, p3, p4, p1)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CHECK), 1005L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1006L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p4, ActionType.CHECK), 1007L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1008L)
        assertTrue(state.isRoundComplete)

        // Deal Turn: Jc
        state = StreetManager.advanceToTurn(state, Card.parse("Jc"))
        assertEquals(Street.TURN, state.hand.currentStreet)
        assertEquals(4, state.hand.communityCards.size)
        // Turn check-through
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CHECK), 1009L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1010L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p4, ActionType.CHECK), 1011L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1012L)

        // Deal River: 10s
        state = StreetManager.advanceToRiver(state, Card.parse("10s"))
        assertEquals(Street.RIVER, state.hand.currentStreet)
        assertEquals(5, state.hand.communityCards.size)
        // River check-through
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CHECK), 1013L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1014L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p4, ActionType.CHECK), 1015L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1016L)

        // Showdown
        state = StreetManager.advanceToShowdown(state)
        assertEquals(Street.SHOWDOWN, state.hand.currentStreet)
        assertEquals(HandStatus.SHOWDOWN, state.hand.status)
    }

    @Test
    fun test03_DealerRotationAndBlindAssignmentNextHand() {
        val session = createFourPlayerSession()
        val init1 = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)

        // Hand 1: Dealer is p1, SB is p2, BB is p3, UTG is p4
        assertEquals(p1, init1.positions.dealerPlayerId)
        assertEquals(p2, init1.positions.smallBlindPlayerId)
        assertEquals(p3, init1.positions.bigBlindPlayerId)
        assertEquals(p4, init1.positions.firstToActPreFlopId)

        // Rotate for Hand 2
        val nextDealer = SeatingNavigator.rotateDealer(session.players, p1)
        assertEquals(p2, nextDealer)

        val init2 = HandInitializer.initializeHand(session, HandId("h2"), 2, nextDealer, 2000L)
        // Hand 2: Dealer is p2, SB is p3, BB is p4, UTG is p1
        assertEquals(p2, init2.positions.dealerPlayerId)
        assertEquals(p3, init2.positions.smallBlindPlayerId)
        assertEquals(p4, init2.positions.bigBlindPlayerId)
        assertEquals(p1, init2.positions.firstToActPreFlopId)
    }

    @Test
    fun test04_FoldsRaisesAllInsAndShortAllIns() {
        val session = createFourPlayerSession()
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        var state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p4, init.initialActions)

        // p4 raises to 50
        state = BettingEngine.applyAction(state, PlayerActionRequest(p4, ActionType.RAISE, 50L), 1001L)
        assertEquals(50L, state.currentLiveBet)

        // p1 folds
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.FOLD), 1002L)
        assertTrue(state.hand.players.first { it.playerId == p1 }.folded)

        // p2 calls 40 (reaches 50)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CALL), 1003L)

        // p3 goes all-in (commits all 980 remaining chips -> total street 1000)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.ALL_IN), 1004L)
        val p3Player = state.hand.players.first { it.playerId == p3 }
        assertTrue(p3Player.allIn)
        assertEquals(0L, p3Player.remainingStack)
        assertEquals(1000L, state.currentLiveBet)
    }

    @Test
    fun test05_MultipleAllInsCreatingSeveralSidePots() {
        // Player A = 50 (all-in), B = 120 (all-in), C = 200 (all-in), D = 200 (all-in)
        val players = listOf(
            HandPlayer(p1, 0, 50L, 0L, totalContribution = 50L, allIn = true),
            HandPlayer(p2, 1, 120L, 0L, totalContribution = 120L, allIn = true),
            HandPlayer(p3, 2, 200L, 0L, totalContribution = 200L, allIn = true),
            HandPlayer(p4, 3, 200L, 0L, totalContribution = 200L, allIn = true),
        )

        val hand = Hand(
            id = HandId("h-allins"),
            sessionId = SessionId("s1"),
            handNumber = 1,
            dealerPlayerId = p1,
            smallBlindPlayerId = p1,
            bigBlindPlayerId = p2,
            currentStreet = Street.RIVER,
            status = HandStatus.IN_PROGRESS,
            communityCards = listOf(Card.parse("2h"), Card.parse("3d"), Card.parse("4c"), Card.parse("5s"), Card.parse("9h")),
            players = players,
            startedAt = 1000L,
        )

        val potResult = PotCalculator.calculatePots(hand)

        // Exactly 3 pots: Main (200), Side 1 (210), Side 2 (160)
        assertEquals(3, potResult.pots.size)
        assertEquals(PotType.MAIN, potResult.pots[0].potType)
        assertEquals(200L, potResult.pots[0].amount)
        assertEquals(setOf(p1, p2, p3, p4), potResult.pots[0].eligiblePlayerIds)

        assertEquals(PotType.SIDE, potResult.pots[1].potType)
        assertEquals(210L, potResult.pots[1].amount)
        assertEquals(setOf(p2, p3, p4), potResult.pots[1].eligiblePlayerIds)

        assertEquals(PotType.SIDE, potResult.pots[2].potType)
        assertEquals(160L, potResult.pots[2].amount)
        assertEquals(setOf(p3, p4), potResult.pots[2].eligiblePlayerIds)
    }

    @Test
    fun test06_FoldedContributorsCannotWinPots() {
        val players = listOf(
            HandPlayer(p1, 0, 500L, 400L, totalContribution = 100L, folded = true),
            HandPlayer(p2, 1, 500L, 400L, totalContribution = 100L),
            HandPlayer(p3, 2, 500L, 400L, totalContribution = 100L),
        )
        val hand = Hand(
            id = HandId("h-folds"),
            sessionId = SessionId("s1"),
            handNumber = 1,
            dealerPlayerId = p1,
            smallBlindPlayerId = p1,
            bigBlindPlayerId = p2,
            currentStreet = Street.RIVER,
            status = HandStatus.IN_PROGRESS,
            communityCards = listOf(Card.parse("2h"), Card.parse("3d"), Card.parse("4c"), Card.parse("5s"), Card.parse("9h")),
            players = players,
            startedAt = 1000L,
        )

        val potResult = PotCalculator.calculatePots(hand)
        val mainPot = potResult.pots[0]

        // 300 total chips
        assertEquals(300L, mainPot.amount)
        // Folded player p1 CANNOT be eligible to win
        assertFalse(mainPot.eligiblePlayerIds.contains(p1))
        assertEquals(setOf(p2, p3), mainPot.eligiblePlayerIds)
    }

    @Test
    fun test07_SplitPotsOddChipsAndUnmatchedReturns() {
        // Player A = 350 (unmatched 150), B = 200, C = 200
        val players = listOf(
            HandPlayer(p1, 0, 500L, 150L, totalContribution = 350L),
            HandPlayer(p2, 1, 500L, 300L, totalContribution = 200L),
            HandPlayer(p3, 2, 500L, 300L, totalContribution = 200L),
        )
        val hand = Hand(
            id = HandId("h-split"),
            sessionId = SessionId("s1"),
            handNumber = 1,
            dealerPlayerId = p1,
            smallBlindPlayerId = p1,
            bigBlindPlayerId = p2,
            currentStreet = Street.RIVER,
            status = HandStatus.IN_PROGRESS,
            communityCards = listOf(Card.parse("2h"), Card.parse("3d"), Card.parse("4c"), Card.parse("5s"), Card.parse("9h")),
            players = players,
            startedAt = 1000L,
        )

        val potResult = PotCalculator.calculatePots(hand)
        // Uncalled return of 150 directly to p1
        assertEquals(150L, potResult.totalRefundedChips)
        assertEquals(150L, potResult.uncalledExcessRefunds[p1])
        assertEquals(600L, potResult.totalContestedChips)

        // Split pot between p2 and p3 (300 chips each)
        val payouts = PotCalculator.distributePot(
            pot = potResult.pots[0],
            winnerPlayerIds = listOf(p2, p3),
            dealerPlayerId = p1,
            playersClockwise = players,
        )
        assertEquals(2, payouts.size)
        assertEquals(300L, payouts[0].amount)
        assertEquals(300L, payouts[1].amount)
    }

    @Test
    fun test08_ForceCloseAndReopenDuringUnfinishedHand() {
        val sRepo = InMemorySessionRepository()
        val hRepo = InMemoryHandRepository()
        val aRepo = InMemoryActionRepository()
        val service = SessionPersistenceService(sRepo, hRepo, aRepo)

        val session = createFourPlayerSession()
        service.createSession(session)

        var liveState = service.startNewHand(session.id, 1, p1, 1000L)
        // p4 calls 20
        liveState = service.recordAction(liveState, PlayerActionRequest(p4, ActionType.CALL), 1001L)

        // Simulate crash & reboot
        val recoveredService = SessionPersistenceService(sRepo, hRepo, aRepo)
        val recovered = recoveredService.recoverActiveSession()
        assertNotNull(recovered)

        val (_, recoveredState) = recovered
        assertNotNull(recoveredState)
        assertEquals(p1, recoveredState.activePlayerId)
        assertEquals(20L, recoveredState.currentLiveBet)
        assertEquals(50L, recoveredState.hand.totalPotChips)
    }

    @Test
    fun test09_UndoActionAuditHistoryAndChipBalances() {
        val sRepo = InMemorySessionRepository()
        val hRepo = InMemoryHandRepository()
        val aRepo = InMemoryActionRepository()
        val service = SessionPersistenceService(sRepo, hRepo, aRepo)

        val session = createFourPlayerSession()
        service.createSession(session)

        var liveState = service.startNewHand(session.id, 1, p1, 1000L)
        // p4 raises to 60
        liveState = service.recordAction(liveState, PlayerActionRequest(p4, ActionType.RAISE, 60L), 1001L)
        assertEquals(60L, liveState.currentLiveBet)
        assertEquals(p1, liveState.activePlayerId)

        // UNDO the raise
        val restored = service.undoLastAction(liveState, 1002L)
        assertEquals(p4, restored.activePlayerId)
        assertEquals(20L, restored.currentLiveBet)
        assertEquals(0L, restored.hand.players.first { it.playerId == p4 }.streetContribution)
        assertEquals(1000L, restored.hand.players.first { it.playerId == p4 }.remainingStack)
    }

    @Test
    fun test10_ConsecutiveHandsTotalChipConservation() {
        val session = createFourPlayerSession(1000L)
        val totalInitialChips = session.players.sumOf { it.currentStack }
        assertEquals(4000L, totalInitialChips)

        // Hand 1: Everyone checks through, p2 wins
        val init1 = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        val hand1Completed = init1.hand.copy(
            currentStreet = Street.SHOWDOWN,
            status = HandStatus.SHOWDOWN,
            communityCards = listOf(Card.parse("2c"), Card.parse("3c"), Card.parse("4c"), Card.parse("5c"), Card.parse("9s")),
        )
        val potResult1 = PotCalculator.calculatePots(hand1Completed)
        val res1 = HandResolutionManager.resolveWithManualWinners(
            hand = hand1Completed,
            session = session,
            winnersByPot = mapOf(potResult1.pots[0].id to listOf(p2)),
            timestamp = 1500L,
        )

        // Verify chips after Hand 1
        val chipsAfterHand1 = res1.updatedSessionPlayers.sumOf { it.currentStack }
        assertEquals(4000L, chipsAfterHand1)

        // Hand 2: Next hand with rotated dealer
        val sessionHand2 = session.copy(
            players = res1.updatedSessionPlayers,
            dealerPlayerId = res1.nextDealerPlayerId,
        )
        val init2 = HandInitializer.initializeHand(sessionHand2, HandId("h2"), 2, sessionHand2.dealerPlayerId, 2000L)
        val hand2Completed = init2.hand.copy(
            currentStreet = Street.SHOWDOWN,
            status = HandStatus.SHOWDOWN,
            communityCards = listOf(Card.parse("Kh"), Card.parse("Qh"), Card.parse("Jh"), Card.parse("10h"), Card.parse("2s")),
        )
        val potResult2 = PotCalculator.calculatePots(hand2Completed)
        val res2 = HandResolutionManager.resolveWithManualWinners(
            hand = hand2Completed,
            session = sessionHand2,
            winnersByPot = mapOf(potResult2.pots[0].id to listOf(p3)),
            timestamp = 2500L,
        )

        // Verify chips after Hand 2
        val chipsAfterHand2 = res2.updatedSessionPlayers.sumOf { it.currentStack }
        assertEquals(4000L, chipsAfterHand2)
    }
}
