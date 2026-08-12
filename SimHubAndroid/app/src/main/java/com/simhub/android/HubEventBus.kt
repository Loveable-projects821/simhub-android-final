package com.simhub.android

import org.json.JSONObject

/**
 * Every monitor (signal, calls, SMS) pushes JSON events here.
 * HubService subscribes once and forwards everything straight to the WebSocket clients.
 * Keeps the monitors decoupled from networking code.
 */
object HubEventBus {

    private val listeners = mutableListOf<(JSONObject) -> Unit>()

    fun subscribe(listener: (JSONObject) -> Unit) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: (JSONObject) -> Unit) {
        listeners.remove(listener)
    }

    fun publish(event: JSONObject) {
        listeners.forEach { it(event) }
    }
}
