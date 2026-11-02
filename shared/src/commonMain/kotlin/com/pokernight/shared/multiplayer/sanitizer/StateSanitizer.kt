package com.pokernight.shared.multiplayer.sanitizer

import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.model.HandPlayer
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.multiplayer.protocol.CardDto
import com.pokernight.shared.multiplayer.protocol.LegalActionsDto
import com.pokernight.shared.multiplayer.protocol.PlayerPrivateStateDto
import com.pokernight.shared.multiplayer.protocol.PublicPlayerDto
import com.pokernight.shared.multiplayer.protocol.SanitizedGameStateSnapshot
import com.pokernight.shared.multiplayer.protocol.TablePublicStateDto

object StateSanitizer {

    fun createSanitizedSnapshot(
        session: Session,
        state: BettingState,
        forPlayerId: PlayerId?,
        stateRevision: Long,
    ): SanitizedGameStateSnapshot {
        val hand = state.hand
        val isShowdown = hand.status == HandStatus.SHOWDOWN || hand.status == HandStatus.COMPLETED

        val publicPlayers = hand.players.map { hp ->
            val isTargetPlayer = forPlayerId != null && hp.playerId == forPlayerId
            val shouldRevealHoleCards = isShowdown && !hp.folded && hp.holeCards != null

            val revealedCards = if (shouldRevealHoleCards || isTargetPlayer) {
                hp.holeCards?.map { CardDto.fromDomain(it) }
            } else {
                null
            }

            PublicPlayerDto(
                playerId = hp.playerId.value,
                displayName = hp.playerId.value.substringAfterLast("-"),
                seatIndex = hp.seatIndex,
                remainingStack = hp.remainingStack,
                totalContribution = hp.totalContribution,
                streetContribution = hp.streetContribution,
                isFolded = hp.folded,
                isAllIn = hp.allIn,
                isDealer = hp.playerId == hand.dealerPlayerId,
                isTurn = hp.playerId == state.activePlayerId,
                revealedHoleCards = revealedCards,
            )
        }

        val tablePublic = TablePublicStateDto(
            handNumber = hand.handNumber,
            street = hand.currentStreet,
            status = hand.status,
            communityCards = hand.communityCards.map { CardDto.fromDomain(it) },
            totalPotChips = hand.totalPotChips,
            currentLiveBet = state.currentLiveBet,
            minRaiseIncrement = state.minRaiseIncrement,
            activePlayerId = state.activePlayerId?.value,
            dealerPlayerId = hand.dealerPlayerId.value,
            players = publicPlayers,
        )

        val targetHandPlayer: HandPlayer? = forPlayerId?.let { pId ->
            hand.players.firstOrNull { it.playerId == pId }
        }

        val privateState = if (targetHandPlayer != null) {
            val isTurn = targetHandPlayer.playerId == state.activePlayerId
            val legalActions = if (isTurn && !state.isRoundComplete && !state.isHandCompleteByFold) {
                val legal = BettingEngine.getLegalActions(state, targetHandPlayer.playerId)
                LegalActionsDto(
                    canCheck = legal.canCheck,
                    canFold = legal.canFold,
                    canCall = legal.canCall,
                    callAmount = legal.callAmount,
                    isCallAllIn = legal.isCallAllIn,
                    canBet = legal.canBet,
                    minBetAmount = legal.minBetAmount,
                    maxBetAmount = legal.maxBetAmount,
                    canRaise = legal.canRaise,
                    minRaiseTo = legal.minRaiseTo,
                    maxRaiseTo = legal.maxRaiseTo,
                    canAllIn = legal.canAllIn,
                    allInAmount = legal.allInAmount,
                )
            } else {
                null
            }

            PlayerPrivateStateDto(
                playerId = targetHandPlayer.playerId.value,
                seatIndex = targetHandPlayer.seatIndex,
                holeCards = targetHandPlayer.holeCards?.map { CardDto.fromDomain(it) },
                isYourTurn = isTurn,
                legalActions = legalActions,
            )
        } else {
            PlayerPrivateStateDto(
                playerId = forPlayerId?.value ?: "spectator",
                seatIndex = -1,
                holeCards = null,
                isYourTurn = false,
                legalActions = null,
            )
        }

        return SanitizedGameStateSnapshot(
            stateRevision = stateRevision,
            table = tablePublic,
            privateState = privateState,
        )
    }
}
