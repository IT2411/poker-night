package com.pokernight.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokernight.android.network.NetworkUtils
import com.pokernight.android.network.PokerHostService
import com.pokernight.shared.data.InMemoryActionRepository
import com.pokernight.shared.data.InMemoryHandRepository
import com.pokernight.shared.data.InMemorySessionRepository
import com.pokernight.shared.data.SessionPersistenceService
import com.pokernight.shared.domain.SeatingNavigator
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.domain.betting.LegalActions
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.history.StateReplayer
import com.pokernight.shared.domain.pot.PotCalculationResult
import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.domain.progression.StreetManager
import com.pokernight.shared.domain.showdown.HandResolutionManager
import com.pokernight.shared.domain.showdown.HandResolutionResult
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.LenderType
import com.pokernight.shared.model.LoanId
import com.pokernight.shared.model.LoanRecord
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.PotId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.SessionPlayerStatus
import com.pokernight.shared.model.Street
import com.pokernight.shared.multiplayer.client.ClientSessionState
import com.pokernight.shared.multiplayer.client.PokerMultiplayerClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CurrentScreen {
    SETUP,
    LIVE_HAND,
    POT_BREAKDOWN,
    SHOWDOWN,
    INTERMISSION,
    HISTORY,
    LOANS,
    JOIN_ROOM,
    CLIENT_GAME,
}

data class HostingStatus(
    val isHosting: Boolean = false,
    val roomId: String = "",
    val hostUrl: String = "",
    val pairingSecret: String = "",
    val connectedClients: Int = 0,
)

data class PokerUiState(
    val currentScreen: CurrentScreen = CurrentScreen.SETUP,
    val session: Session? = null,
    val liveState: BettingState? = null,
    val legalActions: LegalActions? = null,
    val potCalculation: PotCalculationResult? = null,
    val lastResolutionResult: HandResolutionResult? = null,
    val completedHands: List<Hand> = emptyList(),
    val loans: List<LoanRecord> = emptyList(),
    val hostingStatus: HostingStatus = HostingStatus(),
    val hostPlayerId: PlayerId? = null,
    val clientSessionState: ClientSessionState = ClientSessionState.Disconnected,
    val isSearchingHost: Boolean = false,
    val errorMessage: String? = null,
)

class PokerViewModel : ViewModel() {

    private val sessionRepo = InMemorySessionRepository()
    private val handRepo = InMemoryHandRepository()
    private val actionRepo = InMemoryActionRepository()
    private val service = SessionPersistenceService(sessionRepo, handRepo, actionRepo)

    private var hostService: PokerHostService? = null
    val multiplayerClient = PokerMultiplayerClient()

    private val _uiState = MutableStateFlow(PokerUiState())
    val uiState: StateFlow<PokerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            multiplayerClient.sessionState.collect { clientState ->
                _uiState.value = _uiState.value.copy(
                    clientSessionState = clientState,
                    currentScreen = when (clientState) {
                        is ClientSessionState.InGame -> CurrentScreen.CLIENT_GAME
                        is ClientSessionState.SelectingSeat -> CurrentScreen.JOIN_ROOM
                        else -> _uiState.value.currentScreen
                    },
                )
            }
        }

        val recovered = service.recoverActiveSession()
        if (recovered != null) {
            val (session, liveState) = recovered
            val legal = if (liveState != null && liveState.activePlayerId != null) {
                BettingEngine.getLegalActions(liveState, liveState.activePlayerId!!)
            } else {
                null
            }
            val pots = if (liveState != null) PotCalculator.calculatePots(liveState.hand) else null

            _uiState.value = _uiState.value.copy(
                currentScreen = if (liveState != null) CurrentScreen.LIVE_HAND else CurrentScreen.SETUP,
                session = session,
                liveState = liveState,
                legalActions = legal,
                potCalculation = pots,
                hostPlayerId = session.players.firstOrNull()?.playerId,
            )
        }
    }

    fun bindHostService(service: PokerHostService) {
        this.hostService = service

        // Real-time synchronization: Host screen automatically redraws when a remote player acts!
        service.onClientActionReceived = { updatedState ->
            viewModelScope.launch {
                val legal = if (updatedState.activePlayerId != null) {
                    BettingEngine.getLegalActions(updatedState, updatedState.activePlayerId!!)
                } else {
                    null
                }
                val pots = PotCalculator.calculatePots(updatedState.hand)

                _uiState.value = _uiState.value.copy(
                    liveState = updatedState,
                    legalActions = legal,
                    potCalculation = pots,
                )
            }
        }

        viewModelScope.launch {
            launch {
                service.isHosting.collect { hosting ->
                    updateHostingStatus { it.copy(isHosting = hosting) }
                }
            }
            launch {
                service.connectedCount.collect { count ->
                    updateHostingStatus { it.copy(connectedClients = count) }
                }
            }
            launch {
                service.hostUrl.collect { url ->
                    updateHostingStatus { it.copy(hostUrl = url) }
                }
            }
            launch {
                service.roomId.collect { rId ->
                    updateHostingStatus { it.copy(roomId = rId) }
                }
            }
            launch {
                service.pairingSecret.collect { secret ->
                    updateHostingStatus { it.copy(pairingSecret = secret) }
                }
            }
        }
    }

    fun setHostIdentity(playerId: PlayerId) {
        _uiState.value = _uiState.value.copy(hostPlayerId = playerId)
        notifyHostServerStateChanged()
    }

    fun toggleHosting(enable: Boolean) {
        val s = hostService ?: return
        if (enable) {
            s.startHosting()
            _uiState.value.session?.let { session ->
                s.updateGameState(session, _uiState.value.liveState)
            }
        } else {
            s.stopHosting()
        }
    }

    private fun updateHostingStatus(transform: (HostingStatus) -> HostingStatus) {
        _uiState.value = _uiState.value.copy(hostingStatus = transform(_uiState.value.hostingStatus))
    }

    private fun notifyHostServerStateChanged() {
        val session = _uiState.value.session ?: return
        hostService?.updateGameState(session, _uiState.value.liveState)
    }

    fun joinMultiplayerRoom(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        // If the user entered a 6-character room code (e.g. FBE774 or ROOM-FBE774)
        if (!trimmed.contains(":") && !trimmed.startsWith("http")) {
            _uiState.value = _uiState.value.copy(isSearchingHost = true)
            viewModelScope.launch {
                val hostUrl = NetworkUtils.findHostByRoomCode(trimmed)
                _uiState.value = _uiState.value.copy(isSearchingHost = false)
                if (hostUrl != null) {
                    multiplayerClient.connect(hostUrl)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Could not find Host with code $trimmed on this Wi-Fi. Ensure both phones are on the same network.",
                    )
                }
            }
        } else {
            multiplayerClient.connect(trimmed)
        }
    }

    fun claimSeatAsClient(playerId: String) {
        multiplayerClient.claimSeat(playerId)
    }

    fun sendClientAction(actionType: ActionType, targetAmount: Long = 0L) {
        multiplayerClient.sendAction(actionType, targetAmount)
    }

    fun disconnectClient() {
        multiplayerClient.disconnect()
        _uiState.value = _uiState.value.copy(currentScreen = CurrentScreen.SETUP)
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
            val pIds = playerNames.mapIndexed { idx, pName ->
                SessionPlayer(
                    playerId = PlayerId("p-${idx + 1}-$pName"),
                    seatIndex = idx,
                    startingStack = startingStack,
                    currentStack = startingStack,
                )
            }
            val newSession = Session(
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
            _uiState.value = _uiState.value.copy(hostPlayerId = pIds.first().playerId)
            startNewHand(newSession, newSession.dealerPlayerId)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun startNewHand(session: Session, dealerId: PlayerId) {
        try {
            val eligible = SeatingNavigator.getEligibleActivePlayers(session.players)
            require(eligible.size >= 2) { "At least 2 active players with chips required to deal a hand" }

            val activeDealer = SeatingNavigator.getActiveDealerId(session)
            val handNum = _uiState.value.completedHands.size + 1
            val newState = service.startNewHand(session.id, handNum, activeDealer, System.currentTimeMillis())
            val legal = if (newState.activePlayerId != null) {
                BettingEngine.getLegalActions(newState, newState.activePlayerId!!)
            } else {
                null
            }
            val pots = PotCalculator.calculatePots(newState.hand)

            _uiState.value = _uiState.value.copy(
                currentScreen = CurrentScreen.LIVE_HAND,
                session = session,
                liveState = newState,
                legalActions = legal,
                potCalculation = pots,
                errorMessage = null,
            )
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun executeAction(actionType: ActionType, targetAmount: Long = 0L) {
        val state = _uiState.value.liveState ?: return
        val actor = state.activePlayerId ?: return

        try {
            val req = PlayerActionRequest(actor, actionType, targetAmount)
            val updatedState = service.recordAction(state, req, System.currentTimeMillis())
            val legal = if (updatedState.activePlayerId != null) {
                BettingEngine.getLegalActions(updatedState, updatedState.activePlayerId!!)
            } else {
                null
            }
            val pots = PotCalculator.calculatePots(updatedState.hand)

            _uiState.value = _uiState.value.copy(
                liveState = updatedState,
                legalActions = legal,
                potCalculation = pots,
                errorMessage = null,
            )
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun undoAction() {
        val state = _uiState.value.liveState ?: return
        try {
            val actions = actionRepo.getActionsForHand(state.hand.id)
            val effective = StateReplayer.filterEffectiveActions(actions)

            if (effective.size > 2) {
                val updatedState = service.undoLastAction(state, System.currentTimeMillis())
                val legal = if (updatedState.activePlayerId != null) {
                    BettingEngine.getLegalActions(updatedState, updatedState.activePlayerId!!)
                } else {
                    null
                }
                val pots = PotCalculator.calculatePots(updatedState.hand)

                _uiState.value = _uiState.value.copy(
                    liveState = updatedState,
                    legalActions = legal,
                    potCalculation = pots,
                    errorMessage = null,
                )
            } else {
                service.cancelAndPurgeHand(state.hand.id)

                _uiState.value = _uiState.value.copy(
                    liveState = null,
                    legalActions = null,
                    potCalculation = null,
                    currentScreen = CurrentScreen.INTERMISSION,
                    errorMessage = null,
                )
            }
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun advanceStreet(communityCardsToAdd: List<Card>) {
        val state = _uiState.value.liveState ?: return
        try {
            val updated = when (state.hand.currentStreet) {
                Street.PRE_FLOP -> StreetManager.advanceToFlop(state, communityCardsToAdd)
                Street.FLOP -> StreetManager.advanceToTurn(state, communityCardsToAdd.first())
                Street.TURN -> StreetManager.advanceToRiver(state, communityCardsToAdd.first())
                Street.RIVER -> StreetManager.advanceToShowdown(state)
                Street.SHOWDOWN -> state
            }
            val legal = if (updated.activePlayerId != null) {
                BettingEngine.getLegalActions(updated, updated.activePlayerId!!)
            } else {
                null
            }
            val pots = PotCalculator.calculatePots(updated.hand)

            _uiState.value = _uiState.value.copy(
                liveState = updated,
                legalActions = legal,
                potCalculation = pots,
                currentScreen = if (updated.hand.currentStreet == Street.SHOWDOWN) CurrentScreen.SHOWDOWN else CurrentScreen.LIVE_HAND,
                errorMessage = null,
            )
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun resolveHandWithWinners(winnersByPot: Map<PotId, List<PlayerId>>) {
        val state = _uiState.value.liveState ?: return
        val session = _uiState.value.session ?: return

        try {
            val result = if (state.isHandCompleteByFold) {
                HandResolutionManager.resolveUncontestedFold(state.hand, session, System.currentTimeMillis())
            } else {
                HandResolutionManager.resolveWithManualWinners(state.hand, session, winnersByPot, System.currentTimeMillis())
            }

            val updatedSession = session.copy(
                players = result.updatedSessionPlayers,
                dealerPlayerId = result.nextDealerPlayerId,
            )
            sessionRepo.updateSession(updatedSession)

            _uiState.value = _uiState.value.copy(
                session = updatedSession,
                liveState = null,
                lastResolutionResult = result,
                completedHands = _uiState.value.completedHands + result.completedHand,
                currentScreen = CurrentScreen.INTERMISSION,
                errorMessage = null,
            )
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun toggleSittingOut(playerId: PlayerId) {
        val session = _uiState.value.session ?: return
        val updatedPlayers = session.players.map { sp ->
            if (sp.playerId == playerId) {
                val nextStatus = if (sp.status == SessionPlayerStatus.ACTIVE) {
                    SessionPlayerStatus.SITTING_OUT
                } else {
                    SessionPlayerStatus.ACTIVE
                }
                sp.copy(status = nextStatus)
            } else {
                sp
            }
        }
        val updatedSession = session.copy(players = updatedPlayers)
        sessionRepo.updateSession(updatedSession)
        _uiState.value = _uiState.value.copy(session = updatedSession)
        notifyHostServerStateChanged()
    }

    fun issueLoan(
        borrowerId: PlayerId,
        lenderType: LenderType,
        lenderPlayerId: PlayerId?,
        principal: Long,
        interestPercent: Int,
    ) {
        val session = _uiState.value.session ?: return
        try {
            require(principal > 0L) { "Loan principal must be greater than zero" }
            require(interestPercent in 0..10) { "Interest must be between 0% and 10%" }

            if (lenderType == LenderType.PLAYER) {
                requireNotNull(lenderPlayerId) { "Please select a lending player" }
                require(borrowerId != lenderPlayerId) { "Cannot loan chips to oneself" }
                val lender = session.players.first { it.playerId == lenderPlayerId }
                val lName = lender.playerId.value.substringAfterLast("-")
                require(lender.currentStack >= principal) {
                    "$lName only has ${lender.currentStack} chips; cannot lend $principal"
                }
            }

            val totalRepay = LoanRecord.calculateTotalRepay(principal, interestPercent)

            val updatedPlayers = session.players.map { sp ->
                when (sp.playerId) {
                    borrowerId -> sp.copy(currentStack = sp.currentStack + principal)
                    lenderPlayerId -> sp.copy(currentStack = sp.currentStack - principal)
                    else -> sp
                }
            }

            val loan = LoanRecord(
                id = LoanId("loan-${System.currentTimeMillis()}"),
                sessionId = session.id,
                borrowerId = borrowerId,
                lenderType = lenderType,
                lenderPlayerId = lenderPlayerId,
                principalAmount = principal,
                interestPercent = interestPercent,
                totalRepayAmount = totalRepay,
                createdAt = System.currentTimeMillis(),
            )

            val updatedSession = session.copy(players = updatedPlayers)
            sessionRepo.updateSession(updatedSession)

            _uiState.value = _uiState.value.copy(
                session = updatedSession,
                loans = _uiState.value.loans + loan,
                errorMessage = null,
            )
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun repayLoan(loanId: LoanId) {
        val session = _uiState.value.session ?: return
        val loan = _uiState.value.loans.firstOrNull { it.id == loanId && !it.isRepaid } ?: return

        try {
            val borrower = session.players.first { it.playerId == loan.borrowerId }
            val bName = borrower.playerId.value.substringAfterLast("-")
            require(borrower.currentStack >= loan.totalRepayAmount) {
                "$bName does not have enough chips (${borrower.currentStack}) to repay ${loan.totalRepayAmount}"
            }

            val updatedPlayers = session.players.map { sp ->
                when (sp.playerId) {
                    loan.borrowerId -> sp.copy(currentStack = sp.currentStack - loan.totalRepayAmount)
                    loan.lenderPlayerId -> sp.copy(currentStack = sp.currentStack + loan.totalRepayAmount)
                    else -> sp
                }
            }

            val updatedLoans = _uiState.value.loans.map {
                if (it.id == loanId) it.copy(isRepaid = true, repaidAt = System.currentTimeMillis()) else it
            }

            val updatedSession = session.copy(players = updatedPlayers)
            sessionRepo.updateSession(updatedSession)

            _uiState.value = _uiState.value.copy(
                session = updatedSession,
                loans = updatedLoans,
                errorMessage = null,
            )
            notifyHostServerStateChanged()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun resetEntireSession() {
        hostService?.stopHosting()
        multiplayerClient.disconnect()
        _uiState.value = PokerUiState(currentScreen = CurrentScreen.SETUP)
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
