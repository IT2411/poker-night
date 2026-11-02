package com.pokernight.shared.multiplayer

import com.pokernight.shared.model.ActionType
import com.pokernight.shared.multiplayer.protocol.ClientMessage
import com.pokernight.shared.multiplayer.protocol.ClientPayload
import com.pokernight.shared.multiplayer.protocol.ServerMessage
import com.pokernight.shared.multiplayer.protocol.ServerPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testClientActionMessageRoundTrip() {
        val clientMsg = ClientMessage(
            actionId = "act-uuid-1234",
            seatToken = "token-secret-5678",
            clientSequence = 1L,
            payload = ClientPayload.RequestAction(
                actionType = ActionType.RAISE,
                targetAmount = 50L,
            ),
        )

        val serialized = json.encodeToString(clientMsg)
        assertTrue(serialized.contains("request_action"))
        assertTrue(serialized.contains("RAISE"))

        val deserialized = json.decodeFromString<ClientMessage>(serialized)
        assertEquals(clientMsg, deserialized)
    }

    @Test
    fun testServerAckMessageRoundTrip() {
        val serverMsg = ServerMessage(
            stateRevision = 42L,
            ackActionId = "act-uuid-1234",
            errorMessage = null,
            payload = ServerPayload.ActionAck(
                actionId = "act-uuid-1234",
                sequenceNumber = 5,
            ),
        )

        val serialized = json.encodeToString(serverMsg)
        assertTrue(serialized.contains("action_ack"))

        val deserialized = json.decodeFromString<ServerMessage>(serialized)
        assertEquals(serverMsg, deserialized)
    }

    @Test
    fun testServerNackErrorMessageRoundTrip() {
        val serverMsg = ServerMessage(
            stateRevision = 12L,
            ackActionId = "act-uuid-9999",
            errorMessage = "Illegal bet amount: 15 < minimum 20",
            payload = ServerPayload.ActionNack(
                actionId = "act-uuid-9999",
                reason = "Illegal bet amount: 15 < minimum 20",
            ),
        )

        val serialized = json.encodeToString(serverMsg)
        assertTrue(serialized.contains("action_nack"))

        val deserialized = json.decodeFromString<ServerMessage>(serialized)
        assertEquals(serverMsg, deserialized)
    }
}
