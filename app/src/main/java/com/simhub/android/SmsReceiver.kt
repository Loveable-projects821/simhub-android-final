package com.simhub.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val json = JSONObject().apply {
                put("type", "sms")
                put("from", message.originatingAddress ?: "unknown")
                put("body", message.messageBody ?: "")
                put("timestamp", System.currentTimeMillis())
            }
            HubEventBus.publish(json)
        }
    }
}
