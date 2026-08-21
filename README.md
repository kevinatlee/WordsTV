# WordsTV

WordsTV is a small, standalone Android TV application that displays the dedicated [Words TV route](https://words.atlee.io/display) in a fullscreen Android WebView. The launcher name is **Words**.

The actual Words game is hosted and deployed separately. This repository contains only the Android TV browser wrapper; it does not contain game logic or require changes to the Words server.

The icon and TV banner included in this initial version are placeholder artwork and can be replaced with production assets later.

## TV viewport normalization

WordsTV presents the game as a stable 1440-CSS-pixel-wide 16:9 TV layout (approximately 1440×810) without changing the hosted Words application. The WebView enables wide-viewport and overview modes, then replaces the page viewport metadata at runtime only for `https://words.atlee.io` top-level pages.

The runtime viewport uses `width=1440` and calculates its initial scale from the WebView's measured pixel width and Android display density. This adapts to 1080p surfaces, density-scaled Chromecast/Google TV surfaces, and native 4K surfaces without a fixed zoom percentage or device-model checks. Android and the television remain responsible for final physical output scaling.

The normalization is reapplied after each successful top-level page load or reload. It does not inject component styles or alter the separately hosted Words source.

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

## Debug viewport diagnostics

Debug builds log the normalized page viewport after successful top-level loads. Release builds do not collect or log these diagnostics. View them with:

```bash
adb -s <ANDROID_TV_SERIAL> logcat -s WordsTV:D '*:S'
```

The log entry includes `window.innerWidth`, `window.innerHeight`, `screen.width`, `screen.height`, `window.devicePixelRatio`, `visualViewport.width`, `visualViewport.height`, the native WebView size, Android density, and the applied scale.

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
