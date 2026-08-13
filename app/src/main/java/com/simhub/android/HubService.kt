package com.simhub.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import kotlin.random.Random

class HubService : Service() {

    companion object {
        const val PORT = 8765
        const val CHANNEL_ID = "simhub_channel"
        const val NOTIF_ID = 1

        // Simple in-memory PIN, regenerated each time the hub starts.
        // Shown in MainActivity so the user can type it into the iPhone app once.
        var currentPin: String = ""
            private set
    }

    private lateinit var wsServer: WsServer
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var callMonitor: CallMonitor

    private val eventListener: (JSONObject) -> Unit = { event ->
        if (::wsServer.isInitialized) wsServer.broadcastToPaired(event)
    }

    override fun onCreate() {
        super.onCreate()
        currentPin = Random.nextInt(100000, 999999).toString()

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Hub running — PIN $currentPin"))

        val commandHandler = CommandHandler(applicationContext) { json -> wsServer.broadcastToPaired(json) }
        wsServer = WsServer(
            PORT, currentPin,
            onEvent = { log -> updateNotification(log) },
            onCommand = { json -> commandHandler.handle(json) }
        )
        wsServer.start()

        signalMonitor = SignalMonitor(applicationContext)
        callMonitor = CallMonitor(applicationContext)

        HubEventBus.subscribe(eventListener)

        signalMonitor.start()
        callMonitor.start()
        // SmsReceiver is manifest-registered, already active independently.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        signalMonitor.stop()
        callMonitor.stop()
        HubEventBus.unsubscribe(eventListener)
        wsServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SimHub Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SimHub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }
}
