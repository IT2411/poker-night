package com.pokernight.shared.multiplayer.protocol

import com.pokernight.shared.model.ActionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientMessage(
    val actionId: String,
    val seatToken: String? = null,
    val clientSequence: Long = 0L,
    val payload: ClientPayload,
)

@Serializable
sealed interface ClientPayload {

    @Serializable
    @SerialName("claim_seat")
    data class ClaimSeat(
        val requestedPlayerId: String,
        val pairingToken: String,
    ) : ClientPayload

    @Serializable
    @SerialName("request_action")
    data class RequestAction(
        val actionType: ActionType,
        val targetAmount: Long = 0L,
    ) : ClientPayload

    @Serializable
    @SerialName("reconnect")
    data class Reconnect(
        val lastSeenRevision: Long,
    ) : ClientPayload

    @Serializable
    @SerialName("heartbeat")
    data object Heartbeat : ClientPayload
}

@Serializable
data class ServerMessage(
    val stateRevision: Long,
    val ackActionId: String? = null,
    val errorMessage: String? = null,
    val payload: ServerPayload,
)

@Serializable
sealed interface ServerPayload {

    @Serializable
    @SerialName("room_lobby")
    data class RoomLobby(
        val roomId: String,
        val sessionName: String,
        val availableSeats: List<AvailableSeatDto>,
        val requiresPairingToken: Boolean,
    ) : ServerPayload

    @Serializable
    @SerialName("seat_confirmed")
    data class SeatConfirmed(
        val seatToken: String,
        val playerId: String,
        val seatIndex: Int,
    ) : ServerPayload

    @Serializable
    @SerialName("game_snapshot")
    data class GameSnapshot(
        val snapshot: SanitizedGameStateSnapshot,
    ) : ServerPayload

    @Serializable
    @SerialName("action_ack")
    data class ActionAck(
        val actionId: String,
        val sequenceNumber: Int,
    ) : ServerPayload

    @Serializable
    @SerialName("action_nack")
    data class ActionNack(
        val actionId: String,
        val reason: String,
    ) : ServerPayload

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
    ) : ServerPayload
}
