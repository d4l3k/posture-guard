# Anti Shrimp

*Better shrimpin' through chemistry.*

<img src="docs/shrimp.gif" alt="Shrimp" width="300">

**Posture Guard** is a small Android app that buzzes when you're holding your phone and looking way down at it, to nudge you toward holding it near eye level.

## How it works

- **Angle:** the accelerometer's gravity vector gives the angle between the screen and the horizontal. 0° is flat face-up (looking straight down), 90° is upright at eye level, and above 90° is tipped back overhead. Portrait and landscape are treated the same.
- **In hand:** a hand-held phone always has a little tremor. The app tracks the RMS of the non-gravity acceleration (m/s²), and a phone resting on a table or stand reads as nearly still, so it doesn't trigger. Sensor noise varies by phone, so the threshold (default 0.12) is adjustable: put the phone on its stand and tap **Calibrate** to set it to twice the resting noise.
- **Warning:** while the screen is on, if the phone is in hand and below the threshold (55° by default) for longer than the delay (5 s by default), it vibrates and posts a notification. The warning repeats every 20 s until you raise the phone.
- The sensor is read only while the screen is on. Monitoring runs as a foreground service and restarts after reboot if it was enabled.

The main screen shows the live angle and motion level, so you can tune the threshold.

## Build

```sh
ANDROID_HOME=~/Android/Sdk ./build.sh                 # debug-signed
KEYSTORE=release.jks KEYSTORE_PASSWORD_FILE=release.password ./build.sh
```

Output: `build/posture-guard.apk`. Requires Gradle 9.x and Android SDK 36.

## Install

Download `posture-guard.apk` from the [latest release](https://github.com/d4l3k/anti-shrimp/releases/latest). Requires Android 11+.

## License

MIT
