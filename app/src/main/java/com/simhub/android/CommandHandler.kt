package com.simhub.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes commands sent from the iPhone app. Kept separate from WsServer so the
 * networking code doesn't need to know about TelecomManager/SmsManager/ContactsContract.
 */
class CommandHandler(private val context: Context, private val broadcast: (JSONObject) -> Unit) {

    fun handle(json: JSONObject) {
        when (json.optString("type")) {
            "dial" -> dial(json.optString("number"))
            "send_sms" -> sendSms(json.optString("number"), json.optString("body"))
            "get_contacts" -> sendContacts()
            "answer_call" -> answerRingingCall()
            "end_call" -> endCall()
            // WebRTC signaling is app-to-app audio between the two apps' own mics —
            // Android has no independent peer here in this build; this is a hook point
            // for when Android also runs its own WebRTCClient (symmetric to iOS's).
            "webrtc_offer", "webrtc_answer", "webrtc_ice" -> { /* Phase 2b: relay to local WebRTC peer */ }
        }
    }

    @SuppressLint("MissingPermission")
    private fun dial(number: String) {
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun sendSms(number: String, body: String) {
        if (number.isBlank() || body.isBlank()) return
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(number, null, body, null, null)
    }

    @SuppressLint("MissingPermission")
    private fun answerRingingCall() {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.acceptRingingCall()
    }

    @SuppressLint("MissingPermission")
    private fun endCall() {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.endCall()
    }

    @SuppressLint("Range")
    private fun sendContacts() {
        val contacts = JSONArray()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                contacts.put(JSONObject().apply {
                    put("name", name ?: "Unknown")
                    put("number", number ?: "")
                })
            }
        }
        broadcast(JSONObject().apply {
            put("type", "contacts")
            put("contacts", contacts)
        })
    }
}
