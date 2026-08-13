package com.simhub.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_PHONE_NUMBERS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var pinText: TextView

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startHub()
        } else {
            statusText.text = "Permissions denied — hub needs all of them to work"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)
        pinText = findViewById(R.id.pinText)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            requestPermissionsAndStart()
        }

        val resultText = findViewById<TextView>(R.id.captureResultText)
        findViewById<Button>(R.id.testCaptureButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                return@setOnClickListener
            }
            resultText.text = "Testing… stay on the call for ~15 seconds"
            Thread {
                val results = VoiceCallCapture.runAllTests()
                val summary = results.joinToString("\n") { r ->
                    "${r.source}: init=${r.initialized} " +
                        "gotAudio=${r.gotNonSilentAudio} avgAmp=${r.averageAmplitude}"
                }
                runOnUiThread {
                    resultText.text = summary
                }
            }.start()
        }
    }

    private fun requestPermissionsAndStart() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startHub()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startHub() {
        val serviceIntent = Intent(this, HubService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        statusText.text = "Hub running on port ${HubService.PORT}"
        ipText.text = "IP: ${getLocalIpAddress()}"

        // Give the service a moment to generate the PIN, then reflect it.
        pinText.postDelayed({
            pinText.text = "PIN: ${HubService.currentPin}"
        }, 500)
    }

    /**
     * Returns the device's hotspot/WiFi IP so the iPhone knows what address to connect to.
     * When Android's hotspot is active, this is typically something like 192.168.43.1.
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (e: Exception) {
            return "unknown"
        }
        return "unknown"
    }
}
