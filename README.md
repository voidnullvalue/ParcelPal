# ParcelPal

ParcelPal is a local-first Android package tracker. It stores package names, tracking numbers, linked carrier legs, statuses, and timelines on the device. It has no account system, advertising, analytics, telemetry, cloud database, or ParcelPal-operated backend.

## Features

- Save tracking numbers and assign human-readable package names.
- Automatic carrier-format detection with manual carrier override.
- Try carrier-owned public tracking pages first, then configurable public aggregator pages.
- Preserve a visible audit of every source domain contacted and every failure.
- Detect and track final-mile or handoff tracking numbers as linked shipment legs.
- Merge linked-leg events into one timeline.
- Archive and restore packages without deleting history.
- Scan shipping-label barcodes using the open-source ZXing scanner.
- Share text containing a tracking number into ParcelPal.
- Optional six-hour WorkManager refresh and status-change notifications.
- Encrypted or plain local backup and restore through Android's Storage Access Framework.
- No broad storage permission; ParcelPal can access only the backup file selected by the user.

## Privacy boundary

ParcelPal does not operate a server. Network requests go directly from the phone to the enabled tracking source. A contacted source can therefore see the tracking number, IP address, request time, and ordinary HTTP metadata. It does not receive the local package name or the complete local package list.

Requests are HTTPS-only, cookies are disabled, responses are size-limited, and redirects are rejected unless the destination hostname is explicitly allowlisted for that source. The app does not execute tracking-site JavaScript or load advertising pixels.

The camera permission is used only after the user selects **Scan barcode**. Notification permission is used only for optional background status notifications.

See [PRIVACY.md](PRIVACY.md) and [ARCHITECTURE.md](ARCHITECTURE.md).

## Tracking-source limitation

Many carriers and aggregators intentionally require JavaScript, anti-bot challenges, accounts, or paid API credentials. ParcelPal parses public HTTPS responses and will report a source failure rather than bypass a challenge or claim false tracking data. Source definitions and parsers are isolated so changed pages can be repaired without changing the database or UI.

This means no local-only app can guarantee permanent support for every carrier. The source audit in each package detail screen makes failures explicit.

## Build

Requirements:

- JDK 17
- Android SDK platform 36
- Android SDK Build Tools 35.0.0 or newer
- Gradle 8.11.1

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs source privacy checks, unit tests, Android lint, and APK assembly on every push and pull request. The APK is uploaded as a workflow artifact.

## License

ParcelPal is free software licensed under the GNU General Public License, version 3. See [LICENSE](LICENSE).
