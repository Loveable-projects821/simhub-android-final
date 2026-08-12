# SimHub — Android Hub App (Phase 1)

This is the Android half of the SIM-signal / call-event / SMS relay hub, built to run
**fully locally** — no cloud, no external server. The app itself runs an embedded
WebSocket server on the phone's own hotspot IP, and the iOS companion app connects
directly to it.

## What this build does
- Reports live signal strength + carrier name per SIM (`SignalMonitor.kt`)
- Reports real call state: ringing / answered / ended + caller number (`CallMonitor.kt`)
- Relays incoming SMS content (`SmsReceiver.kt`)
- Runs as a foreground service so it survives screen-off (`HubService.kt`)
- Simple 6-digit PIN pairing so random devices on the hotspot can't connect (`WsServer.kt`)

## What this build does NOT do yet (by design — see the plan discussed)
- No live call **audio** relay yet. Real GSM call audio cannot be captured by a
  third-party app on stock, non-rooted Android (OS-level restriction, not a bug here).
  Phase 2 adds a WebRTC voice channel between the two apps (a real VoIP call, like
  WhatsApp), triggered when `CallMonitor` reports "ringing" — that's the actually
  working path for "talk through the iPhone."
- No outbound call/SMS commands from iPhone yet — the `WsServer.onMessage` handler
  has a slot ready for `"dial"` / `"send_sms"` / `"answer_call"` commands; wire these
  to `TelecomManager` / `SmsManager` when ready.

## How to open and build
1. Open this folder in Android Studio (Hedgehog/2023.1+ recommended). Android Studio
   will auto-generate the Gradle wrapper on first sync — you don't need to add one.
2. Let Gradle sync — it pulls one extra dependency, `Java-WebSocket`, from Maven Central.
3. Run on a real Android phone (not an emulator — you need real SIM/telephony hardware).
4. Grant all requested permissions (phone state, SMS, phone numbers).
5. Turn on the phone's mobile hotspot (Settings → Hotspot) so the iPhone has a network
   to join and an IP to connect to.
6. Tap **Start Hub** — the screen shows the device's IP and a 6-digit PIN.
7. On the iPhone app, join the Android's hotspot WiFi, enter that IP + PIN to pair.

## Protocol (for building the iOS client against this)
Connect via WebSocket to `ws://<android-ip>:8765`.

**First message you must send (pairing):**
```json
{ "type": "pair", "pin": "123456" }
```
Server replies:
```json
{ "type": "paired", "status": "ok" }
```
or `"status": "wrong_pin"` (socket closes after).

**Events broadcast to paired clients:**
```json
{ "type": "signal", "slot": 0, "carrier": "Jazz", "level": 3, "timestamp": 1234567890 }
{ "type": "call", "state": "ringing", "number": "+923001234567", "timestamp": 1234567890 }
{ "type": "sms", "from": "+923001234567", "body": "hello", "timestamp": 1234567890 }
```

## Permissions this app requests, and why
| Permission | Why |
|---|---|
| READ_PHONE_STATE / READ_PHONE_NUMBERS | Read SIM signal + carrier info |
| ANSWER_PHONE_CALLS | Reserved for Phase 2 remote-answer command |
| RECEIVE_SMS / READ_SMS / SEND_SMS | SMS relay |
| FOREGROUND_SERVICE | Keep the hub alive with screen off |
| POST_NOTIFICATIONS | Show the persistent "hub running" notification (Android 13+) |

## Next step (Phase 2)
Add `org.webrtc:google-webrtc` to `app/build.gradle`, open a WebRTC `PeerConnection`
signaled over this same WebSocket, and trigger it automatically when a `"call":"ringing"`
event fires — the iPhone gets a CallKit prompt, and accepting starts a live two-way
VoIP audio stream over the hotspot, same as a WhatsApp call.
