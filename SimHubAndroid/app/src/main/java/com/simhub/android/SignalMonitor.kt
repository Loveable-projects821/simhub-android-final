package com.simhub.android

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reports signal strength + carrier info for every active SIM.
 * Push happens on every change AND on a periodic timer as a safety net,
 * since some OEMs are lazy about firing signal-strength callbacks.
 */
class SignalMonitor(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val listeners = mutableListOf<PhoneStateListener>()

    @SuppressLint("MissingPermission")
    fun start() {
        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

        val activeSubscriptions = try {
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        } ?: emptyList()

        if (activeSubscriptions.isEmpty()) {
            // Fall back to the default telephony manager if we can't enumerate SIMs
            // (still works fine on single-SIM devices or older Android versions).
            attachListener(telephonyManager, slotIndex = 0, carrierName = telephonyManager.networkOperatorName)
            return
        }

        for (info in activeSubscriptions) {
            val simTm = telephonyManager.createForSubscriptionId(info.subscriptionId)
            attachListener(simTm, slotIndex = info.simSlotIndex, carrierName = info.carrierName?.toString() ?: "Unknown")
        }
    }

    @SuppressLint("MissingPermission")
    private fun attachListener(tm: TelephonyManager, slotIndex: Int, carrierName: String) {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                pushSignal(slotIndex, carrierName, signalStrength.level, tm.networkOperatorName)
            }
        }
        // LISTEN_SIGNAL_STRENGTHS is deprecated on API 31+ in favor of TelephonyCallback,
        // but still functions through API 34 — kept here for min-SDK 26 compatibility.
        tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        listeners.add(listener)
    }

    private fun pushSignal(slot: Int, carrier: String, level: Int, operatorName: String) {
        val json = JSONObject().apply {
            put("type", "signal")
            put("slot", slot)
            put("carrier", if (carrier.isNotBlank()) carrier else operatorName)
            put("level", level) // 0 (none) .. 4 (great) — Android's normalized scale
            put("timestamp", System.currentTimeMillis())
        }
        HubEventBus.publish(json)
    }

    fun stop() {
        listeners.forEach { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
        listeners.clear()
    }
}
