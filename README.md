# InkMirror

Mirror your iPhone to an e-ink Android tablet and control it from there: touch to tap, page buttons to turn pages. Works with AirPlay screen mirroring.

InkMirror is a fork of [AirPlay Receiver for Android](https://github.com/jqssun/android-airplay-server) (jqssun/android-airplay-server) that adds a fit-to-reader mirror and two-way control, so you can read in iPhone apps on an e-ink screen.

This is a personal project, built for and tested only on the Xteink S4 (480x800, Android 11). Other Android 9+ readers may work but are untested.

What this fork adds:

- **Mirror zoom and pan.** The mirrored phone screen can fill the reader: pinch with two fingers to zoom (100-400%), drag with two fingers to move around. Zoom and position are remembered. Also in Settings > Mirror.
- **Touch control of the phone.** The app registers as a Bluetooth mouse and keyboard (Android's HID device profile, Android 9+). On the full-screen mirror: tap = the phone's pointer moves there and clicks, long-press = press and hold, one-finger drag = scroll. iOS needs AssistiveTouch on (Settings > Accessibility > Touch > AssistiveTouch) for the pointer; set its Tracking Speed to the lowest.
- **Page buttons turn pages.** While the mirror is showing, the reader's page/volume buttons send Right/Left arrow keys to the phone, which turn pages in most reading apps. Swap the direction with the `hid_swap_page_keys` setting.
- **Pointer calibration.** iOS mice are relative, so the app pushes the pointer into the bottom-left corner and moves it by calibrated amounts. Tap the top-right corner of the mirror for a bar with Calibrate (two taps) and Exit.
- Installs beside the original app (`io.github.imshentastic.inkmirror`), receiver name "InkMirror" by default.

Setup: pair the reader with the phone over Bluetooth while this app is running (it must be the only Bluetooth mouse app), mirror from Control Center, then calibrate once. If the phone was paired before with another mouse app, forget the device on both sides and pair again. Close other Bluetooth mouse apps first.

## Requirements and limits

- **No root needed.** Mirroring and touch control use standard Android APIs.
- **Touch control and page buttons need Android 9 or newer** (the Bluetooth HID device profile). On Android 6-8 the app still mirrors, without control.
- **Android 12 and newer** ask for the Bluetooth ("Nearby devices") permission the first time; touch control stays off until it is granted.
- **Some device makers switch off the Bluetooth HID device profile.** On those devices mirroring works but touch control and page buttons do not; the status bar (tap the top-right corner of the mirror) says "Touch control not supported on this device".
- **Page buttons** must send volume or page up/down keys, as most e-readers do. Only confirmed on the Xteink S4.
- **Touch control is tested with an iPhone** (AssistiveTouch on). The mirror itself works with any AirPlay screen-mirroring sender.
- If the tablet stops appearing as a mirroring target, restart the tablet.

## Based on AirPlay Receiver for Android

Everything else - the AirPlay receiver itself, its features, settings, ADB configuration and build instructions - is documented in the original project: **[AirPlay Receiver for Android](https://github.com/jqssun/android-airplay-server)**. Build this fork the same way (`git submodule update --init`, then `./gradlew assembleDebug`).

## Credits and license

- [AirPlay Receiver for Android](https://github.com/jqssun/android-airplay-server) by jqssun, which this fork builds on
- [UxPlay](https://github.com/FDH2/UxPlay) for the AirPlay/RAOP server implementation
- [FFmpeg](https://ffmpeg.org) for the lossless audio decoder
- [Next Player](https://github.com/anilbeesetti/nextplayer) for the video player

Licensed under GPL-3.0, like the original. AirPlay is a trademark of Apple Inc. InkMirror is not affiliated with or endorsed by Apple.
