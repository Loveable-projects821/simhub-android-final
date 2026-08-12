package com.simhub.android

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import org.json.JSONObject

/**
 * Watches real call state (ringing / offhook / idle) and forwards it as an event.
 * NOTE: this only reports metadata (caller number, state) — it does NOT capture call audio.
 * See the Phase 2 plan (WebRTC bridge) for actually getting live voice to the iPhone.
 */
class CallMonitor(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var listener: PhoneStateListener? = null
    private var lastState = TelephonyManager.CALL_STATE_IDLE

    @SuppressLint("MissingPermission")
    fun start() {
        listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state == lastState) return
                lastState = state

                val stateName = when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> "ringing"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "answered"
                    TelephonyManager.CALL_STATE_IDLE -> "ended"
                    else -> "unknown"
                }

                val json = JSONObject().apply {
                    put("type", "call")
                    put("state", stateName)
                    put("number", phoneNumber ?: "")
                    put("timestamp", System.currentTimeMillis())
                }
                HubEventBus.publish(json)
            }
        }
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    fun stop() {
        listener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
        listener = null
    }
}
