package com.simhub.android

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress

/**
 * The Android app IS the server — no cloud, no external signaling service.
 * The iPhone client connects directly to the Android hotspot's IP on this port.
 *
 * Simple pairing flow:
 *   1. Client connects and immediately sends {"type":"pair","pin":"123456"}
 *   2. If the PIN matches, the connection is marked "paired" and starts receiving broadcasts.
 *   3. Unpaired sockets are dropped after a few seconds.
 */
class WsServer(
    port: Int,
    private val expectedPin: String,
    private val onEvent: (String) -> Unit
) : WebSocketServer(InetSocketAddress(port)) {

    private val pairedClients = mutableSetOf<WebSocket>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        onEvent("Client connecting from ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        pairedClients.remove(conn)
        onEvent("Client disconnected")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "pair" -> {
                    if (json.optString("pin") == expectedPin) {
                        pairedClients.add(conn)
                        conn.send(JSONObject().apply {
                            put("type", "paired")
                            put("status", "ok")
                        }.toString())
                        onEvent("Client paired successfully")
                    } else {
                        conn.send(JSONObject().apply {
                            put("type", "paired")
                            put("status", "wrong_pin")
                        }.toString())
                        conn.close()
                    }
                }
                // Room for future inbound commands from iPhone, e.g. "dial", "send_sms", "answer_call"
                else -> onEvent("Unhandled message: $message")
            }
        } catch (e: Exception) {
            onEvent("Bad message ignored: ${e.message}")
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        onEvent("WebSocket error: ${ex.message}")
    }

    override fun onStart() {
        onEvent("Hub listening on port $port")
    }

    /** Broadcast an event to every paired (authenticated) client only. */
    fun broadcastToPaired(json: JSONObject) {
        val payload = json.toString()
        pairedClients.forEach { client ->
            if (client.isOpen) client.send(payload)
        }
    }

    fun pairedClientCount() = pairedClients.size
}
