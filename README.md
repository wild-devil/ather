# AetherScan

**Precision CDN IP Scanner for Android**

A beautiful, modern Android app with soft glassmorphism + neon UI that probes CDN IP ranges (Cloudflare, Fastly, Akamai, or custom) using real TCP + TLS (SNI) + HTTP requests.

## Features

- Multi-threaded high-performance scanning
- Real TCP connect + TLS handshake + HTTP request timing
- CDN provider & colo detection from headers
- Cloudflare / Fastly / Akamai presets + custom CIDR / IP ranges
- Adjustable max IPs, threads, host/SNI
- Live progress + sorted results by latency
- One-tap copy IPs or full CSV-style export
- Dark neon glass UI, English primary

## Requirements

- Android Studio Ladybug (2024.2) or newer
- JDK 17+
- Android device / emulator with API 26+

## Build & Install

1. Open the project folder in **Android Studio**
2. Let Gradle sync (first time downloads dependencies)
3. Connect a phone (USB debugging) or start an emulator
4. Click **Run** ▶ or build APK:

```bash
./gradlew assembleRelease
```

APK will be at:

```
app/build/outputs/apk/release/app-release.apk
```

(For release you may need to add a signing config.)

## Debug APK

```bash
./gradlew assembleDebug
```

→ `app/build/outputs/apk/debug/app-debug.apk`

## How it works

1. Select a CDN provider (or Custom)
2. Optionally change Host/SNI and max IPs / threads
3. Tap **Start Scan**
4. App samples public IPs from the official ranges
5. Each IP is tested with TCP → TLS (SNI) → HTTP
6. Results appear sorted by total latency

## Notes

- Scanning uses the network of the phone. For best results use a good connection.
- Some networks or carriers may throttle or block unusual TLS/SNI traffic.
- The app trusts all certificates only for the probe sockets so it can measure pure latency against any CDN edge.

## License

MIT — do whatever you want with it.

Made with care. Enjoy the glass.
