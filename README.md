# Notify Double

**English** · [Русский](README.ru.md)

**iPhone notifications on a Xiaomi Smart Band through an Android phone.**

The band stays paired with the Android phone through Mi Fitness, as usual. The receiver app reads iPhone notifications over Bluetooth through Apple's built-in Apple Notification Center Service (ANCS) and republishes them on the Android phone. Mi Fitness picks them up like notifications from any other app and sends them to the band.

```
iPhone ──ANCS, Bluetooth LE──▶ Receiver on Android ──notification──▶ Mi Fitness ──▶ band
```

No app on the iPhone, no jailbreak, no changes to Mi Fitness or the band.

## Features

- **No iPhone app.** The iPhone is needed only once, for pairing with the free LightBlue app.
- **Allowlist.** Titles and text are requested only for the iPhone apps you turn on. For other apps the receiver learns only which app sent a notification. The list starts empty, so bank and SMS codes never reach the Android phone.
- **Silent on Android.** Notifications are posted on a channel with no sound and no status bar icon, and Mi Fitness still forwards them to the band.
- **Kept in sync.** A notification read or dismissed on the iPhone disappears from the band too.
- **Reconnects by itself.** After leaving Bluetooth range, turning Bluetooth off or a reboot, the receiver reconnects on its own. Messages that arrive while disconnected are delivered after reconnection.
- **Battery friendly.** No polling and no wake locks: everything happens in Bluetooth callbacks. A week of daily use showed no noticeable difference in battery drain.
- **Log.** Connections, disconnect codes and events, never notification text.
- **English and Russian.** The UI and the log follow the phone's language.

## Requirements

| | |
|---|---|
| Android phone | Android 12 or later with Bluetooth LE; Google Play services for the UI font (otherwise the system font is used) |
| Band | Paired with this phone through Mi Fitness |
| iPhone | Near the Android phone, within Bluetooth range (about 10 m) |
| For setup | The free apps [LightBlue](https://apps.apple.com/app/id557428110) on the iPhone and [nRF Connect for Mobile](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp) on Android |

Tested with Nothing Phone 1 (Android 15), Mi Fitness 3.59.2i, Xiaomi Smart Band 10, and Telegram and phone calls on the iPhone. Other phones and bands should work the same way but have not been tested. To check your own "Mi Fitness + band" combination, use the probe app in [`tools/mifitness-probe`](tools/mifitness-probe) (Russian UI).

## Installation

**Ready-made APK (recommended).** Download `notify-double-<version>.apk` from [Releases](../../releases/latest) and install it, allowing installs from that source. Releases are signed with one key, so new versions install over old ones and keep your settings.

**From source.** Clone the repository, open its root in Android Studio, connect the phone with USB debugging on and press **Run**. That build is signed with your debug key: to switch between it and a release build, uninstall the app first.

## Setup

### 1. Pair the iPhone with Android (once)

Pair **from nRF Connect**, not from the Bluetooth settings: a regular pairing may go over classic Bluetooth, while ANCS works only over Bluetooth LE.

1. **iPhone.** Open LightBlue → **Virtual Devices** tab → **+** → any template (for example, Blank) → save. A blue check mark appears next to the device. Keep LightBlue on screen and the iPhone unlocked.
2. **Android.** Open nRF Connect → **Scanner** tab.
   - Tap the filter bar and remove **Apple** from the excluded vendors: nRF Connect hides Apple devices by default.
   - Find the device with the template's name (for example, **Blank**) and a strong signal (about −30 to −60 dBm). Do not search by MAC address: the iPhone uses a random one.
3. Tap **Connect**. **Apple Notification Center Service** should be in the list of services.
4. Menu **⋮** → **Bond**. Confirm the pairing on both phones and compare the code. The status changes to **BONDED**.
5. Tap **Disconnect** in nRF Connect. You can close LightBlue on the iPhone: it is no longer needed.

A detailed guide with decoding of ANCS events and a manual check is in [docs/ancs-check-nrf-connect.md](docs/ancs-check-nrf-connect.md) (Russian).

### 2. Receiver

1. Open **iPhone Receiver** and allow access to nearby devices and notifications.
2. **iPhone** → choose the paired device (the iPhone's name or the LightBlue template's name).
3. **Battery** → allow unrestricted use, otherwise the system may stop the service.
4. **Start**. Within a few seconds the status card turns green: **Connected**.

### 3. Mi Fitness

Device → Notifications and calls → App notifications → turn on **iPhone Receiver**. The **Mi Fitness** button on the receiver's home screen opens the app.

### 4. iPhone apps

Send yourself messages from the apps you need on the iPhone: they appear on the **Apps** tab. Turn on the switch for the apps to forward. Their next notifications arrive on the band as "Telegram: John".

Do not turn on banks, authenticators or Messages (`com.apple.MobileSMS`): they carry verification codes.

## Troubleshooting

| Symptom | What to do |
|---|---|
| The card shows "Waiting for iPhone" for a long time | Keep the iPhone nearby with Bluetooth on. The receiver renews its connection request every 10 minutes; if that does not help, press Stop and Start |
| The log says "ANCS service not found" or shows subscription errors | iPhone: Settings → Bluetooth → **i** next to the device → turn on the system notifications switch |
| Notifications reach the receiver but not the band | Check that iPhone Receiver is on in Mi Fitness and that Mi Fitness may autostart and run in the background |
| The log says "Skipped: … is not allowed" | The app is not turned on in the Apps tab |
| The service stops | Turn off battery optimization for the receiver |
| You need to pair again | Remove the pairing on both phones and repeat step 1 |

The **Log** tab shows connections and disconnect codes: 8 – link lost, 19 – closed by the iPhone, 147 – connection timeout.

## Limitations

- You cannot reply to a message or decline a call from the band: the band does not know about the receiver, and ANCS has no text replies.
- An incoming call reaches the band as a regular notification, without the call screen.
- The notification icon on the band is chosen by Mi Fitness or the band itself.
- Without a link to the iPhone nothing is forwarded. Messages that arrived while disconnected come through after reconnection if they are still unread on the iPhone.

## Privacy

- Text is requested from the iPhone only for allowed apps and is never stored: it exists only in the posted notifications, which expire after 12 hours.
- The log holds events, numbers, app identifiers and names, but no text.
- The receiver does not use the network.

## How it works

| File | Purpose |
|---|---|
| [`ble/AncsLink.kt`](app/src/main/java/com/notifydouble/receiver/ble/AncsLink.kt) | Connecting to the iPhone, ANCS subscription, reconnection |
| [`ble/GattQueue.kt`](app/src/main/java/com/notifydouble/receiver/ble/GattQueue.kt) | GATT operation queue: Android runs them one at a time |
| [`ancs/`](app/src/main/java/com/notifydouble/receiver/ancs) | ANCS protocol: events, requests, assembling fragmented responses |
| [`forward/Forwarder.kt`](app/src/main/java/com/notifydouble/receiver/forward/Forwarder.kt) | Forwarding rules and the allowlist |
| [`ReceiverService.kt`](app/src/main/java/com/notifydouble/receiver/ReceiverService.kt) | Foreground service, autostart, reconnection watchdog |
| [`ui/`](app/src/main/java/com/notifydouble/receiver/ui) | Jetpack Compose UI |

Key decisions:

- Android connects to the iPhone as the central and starts pairing itself. Without an ANCS subscription the iPhone closes the link after about 30 seconds, so the receiver subscribes right after service discovery.
- Control Point requests go strictly one at a time. A Data Source response is assembled from fragments and is complete once every requested attribute has arrived.
- Notifications that existed before the connection are not forwarded; the iPhone reports the ones that arrived during a disconnect as new.

The research history, on-device checks and all decisions are in [docs/research-plan.md](docs/research-plan.md) (Russian).

**Tests.** Unit tests for ANCS parsing, the request queue and the forwarding rules run without a phone: in Android Studio right-click `app/src/test` → Run, or `gradle testDebugUnitTest`.

**Releases.** A tag such as `v1.2.0` builds a signed APK and creates a release (`.github/workflows/release.yml`). The signing key is kept in the repository secrets.

## Credits

- The UI design and components are based on [leager-app](https://github.com/Expl0iD-Dev-Team/leager-app).
- Apple's [ANCS specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Introduction/Introduction.html).
- [WearBridge](https://github.com/k97/WearBridge), [ancs4linux](https://github.com/pzmarzly/ancs4linux) and [ancs-client](https://codeberg.org/chf/ancs-client) helped choose the connection scheme.

## License

[MIT](LICENSE). Not affiliated with Apple, Xiaomi or Nothing. iPhone and Apple are trademarks of Apple Inc.
