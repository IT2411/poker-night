package com.pokernight.android.ui

import androidx.lifecycle.ViewModel
import com.pokernight.shared.data.InMemoryActionRepository
import com.pokernight.shared.data.InMemoryHandRepository
import com.pokernight.shared.data.InMemorySessionRepository
import com.pokernight.shared.data.SessionPersistenceService
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.domain.betting.LegalActions
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.pot.PotCalculationResult
import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.domain.progression.StreetManager
import com.pokernight.shared.domain.showdown.HandResolutionManager
import com.pokernight.shared.domain.showdown.HandResolutionResult
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.PotId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.Street
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CurrentScreen {
    SETUP,
    LIVE_HAND,
    POT_BREAKDOWN,
    SHOWDOWN,
    HISTORY,
}

data class PokerUiState(
    val currentScreen: CurrentScreen = CurrentScreen.SETUP,
    val session: Session? = null,
    val liveState: BettingState? = null,
    val legalActions: LegalActions? = null,
    val potCalculation: PotCalculationResult? = null,
    val lastResolutionResult: HandResolutionResult? = null,
    val completedHands: List<Hand> = emptyList(),
    val errorMessage: String? = null,
)

class PokerViewModel : ViewModel() {
    private val sessionRepo = InMemorySessionRepository()
    private val handRepo = InMemoryHandRepository()
    private val actionRepo = InMemoryActionRepository()
    private val service = SessionPersistenceService(sessionRepo, handRepo, actionRepo)

    private val _uiState = MutableStateFlow(PokerUiState())
    val uiState: StateFlow<PokerUiState> = _uiState.asStateFlow()

    init {
        // Attempt crash recovery on start
        val recovered = service.recoverActiveSession()
        if (recovered != null) {
            val (session, liveState) = recovered
            val legal =
                if (liveState != null && liveState.activePlayerId != null) {
                    BettingEngine.getLegalActions(liveState, liveState.activePlayerId!!)
                } else {
                    null
                }
            val pots = if (liveState != null) PotCalculator.calculatePots(liveState.hand) else null

            _uiState.value =
                _uiState.value.copy(
                    currentScreen = if (liveState != null) CurrentScreen.LIVE_HAND else CurrentScreen.SETUP,
                    session = session,
                    liveState = liveState,
                    legalActions = legal,
                    potCalculation = pots,
                )
        }
    }

    fun createSession(
        name: String,
        smallBlind: Long,
        bigBlind: Long,
        chipValueInPaise: Int,
        playerNames: List<String>,
        startingStack: Long,
    ) {
        try {
            val pIds =
                playerNames.mapIndexed { idx, pName ->
                    SessionPlayer(
                        playerId = PlayerId("p-${idx + 1}-$pName"),
                        seatIndex = idx,
                        startingStack = startingStack,
                        currentStack = startingStack,
                    )
                }
            val newSession =
                Session(
                    id = SessionId("session-${System.currentTimeMillis()}"),
                    name = name,
                    createdAt = System.currentTimeMillis(),
                    chipValueInMinorUnits = chipValueInPaise,
                    smallBlind = smallBlind,
                    bigBlind = bigBlind,
                    dealerPlayerId = pIds.first().playerId,
                    players = pIds,
                )
            service.createSession(newSession)
            startNewHand(newSession, newSession.dealerPlayerId)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun startNewHand(
        session: Session,
        dealerId: PlayerId,
    ) {
        try {
            val handNum = _uiState.value.completedHands.size + 1
            val newState = service.startNewHand(session.id, handNum, dealerId, System.currentTimeMillis())
            val legal =
                if (newState.activePlayerId != null) {
                    BettingEngine.getLegalActions(newState, newState.activePlayerId!!)
                } else {
                    null
                }
            val pots = PotCalculator.calculatePots(newState.hand)

            _uiState.value =
                _uiState.value.copy(
                    currentScreen = CurrentScreen.LIVE_HAND,
                    session = session,
                    liveState = newState,
                    legalActions = legal,
                    potCalculation = pots,
                    errorMessage = null,
                )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun executeAction(
        actionType: ActionType,
        targetAmount: Long = 0L,
    ) {
        val state = _uiState.value.liveState ?: return
        val actor = state.activePlayerId ?: return

        try {
            val req = PlayerActionRequest(actor, actionType, targetAmount)
            val updatedState = service.recordAction(state, req, System.currentTimeMillis())
            val legal =
                if (updatedState.activePlayerId != null) {
                    BettingEngine.getLegalActions(updatedState, updatedState.activePlayerId!!)
                } else {
                    null
                }
            val pots = PotCalculator.calculatePots(updatedState.hand)

            _uiState.value =
                _uiState.value.copy(
                    liveState = updatedState,
                    legalActions = legal,
                    potCalculation = pots,
                    errorMessage = null,
                )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun undoAction() {
        val state = _uiState.value.liveState ?: return
        try {
            val updatedState = service.undoLastAction(state, System.currentTimeMillis())
            val legal =
                if (updatedState.activePlayerId != null) {
                    BettingEngine.getLegalActions(updatedState, updatedState.activePlayerId!!)
                } else {
                    null
                }
            val pots = PotCalculator.calculatePots(updatedState.hand)

            _uiState.value =
                _uiState.value.copy(
                    liveState = updatedState,
                    legalActions = legal,
                    potCalculation = pots,
                    errorMessage = null,
                )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun advanceStreet(communityCardsToAdd: List<Card>) {
        val state = _uiState.value.liveState ?: return
        try {
            val updated =
                when (state.hand.currentStreet) {
                    Street.PRE_FLOP -> StreetManager.advanceToFlop(state, communityCardsToAdd)
                    Street.FLOP -> StreetManager.advanceToTurn(state, communityCardsToAdd.first())
                    Street.TURN -> StreetManager.advanceToRiver(state, communityCardsToAdd.first())
                    Street.RIVER -> StreetManager.advanceToShowdown(state)
                    Street.SHOWDOWN -> state
                }
            val legal =
                if (updated.activePlayerId != null) {
                    BettingEngine.getLegalActions(updated, updated.activePlayerId!!)
                } else {
                    null
                }
            val pots = PotCalculator.calculatePots(updated.hand)

            _uiState.value =
                _uiState.value.copy(
                    liveState = updated,
                    legalActions = legal,
                    potCalculation = pots,
                    currentScreen = if (updated.hand.currentStreet == Street.SHOWDOWN) CurrentScreen.SHOWDOWN else CurrentScreen.LIVE_HAND,
                    errorMessage = null,
                )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun resolveHandWithWinners(winnersByPot: Map<PotId, List<PlayerId>>) {
        val state = _uiState.value.liveState ?: return
        val session = _uiState.value.session ?: return

        try {
            val result =
                if (state.isHandCompleteByFold) {
                    HandResolutionManager.resolveUncontestedFold(state.hand, session, System.currentTimeMillis())
                } else {
                    HandResolutionManager.resolveWithManualWinners(state.hand, session, winnersByPot, System.currentTimeMillis())
                }

            val updatedSession =
                session.copy(
                    players = result.updatedSessionPlayers,
                    dealerPlayerId = result.nextDealerPlayerId,
                )
            sessionRepo.updateSession(updatedSession)

            _uiState.value =
                _uiState.value.copy(
                    session = updatedSession,
                    lastResolutionResult = result,
                    completedHands = _uiState.value.completedHands + result.completedHand,
                    currentScreen = CurrentScreen.HISTORY,
                    errorMessage = null,
                )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun navigateTo(screen: CurrentScreen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun formatChipValue(chips: Long): String {
        val session = _uiState.value.session ?: return "$chips chips"
        val totalPaise = chips * session.chipValueInMinorUnits
        val rupees = totalPaise / 100.0
        return "$chips chips (₹${"%.2f".format(rupees)})"
    }
}
