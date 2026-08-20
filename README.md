# WordsTV

WordsTV is a small, standalone Android TV application that displays the dedicated [Words TV route](https://words.atlee.io/display) in a fullscreen Android WebView. The launcher name is **Words**.

The actual Words game is hosted and deployed separately. This repository contains only the Android TV browser wrapper; it does not contain game logic or require changes to the Words server.

The icon and TV banner included in this initial version are placeholder artwork and can be replaced with production assets later.

## Deployment status note

WordsTV always launches `https://words.atlee.io/display`. During verification on August 20, 2026, a browser-like request to that route received HTTP 404 with `Cannot GET /display`. This is an external Words deployment issue; WordsTV shows its native server-unavailable screen until the independently hosted route is available. No Words server or deployment changes were made from this repository.

## Requirements

- JDK 17
- Android SDK Platform 36 and Android SDK Build Tools 36.0.0
- An Android TV or Google TV device with Android 6.0 (API 23) or later

The included Gradle wrapper downloads the required Gradle version. Android Studio can install the required SDK components.

## Build

From the repository root:

```bash
./gradlew assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests and Android lint checks with:

```bash
./gradlew testDebugUnitTest lintDebug
```

## Sideload

Enable developer options and network debugging on the Android TV device, then use ADB:

```bash
adb connect <ANDROID_TV_IP>
adb install app/build/outputs/apk/debug/app-debug.apk
```

To replace an existing debug installation:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
