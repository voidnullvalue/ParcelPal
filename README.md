# ParcelPal

ParcelPal is a local-first Android package tracker. It stores package names, tracking numbers, linked carrier legs, statuses, and timelines on the device. It has no account system, advertising, analytics, telemetry, cloud database, or ParcelPal-operated backend.

## Features

- Save tracking numbers and assign human-readable package names.
- Multi-carrier format detection with manual carrier override: a number that belongs to two carriers is looked up in both.
- Query every enabled source that supports a number at the same time, and merge what they return into one timeline.
- Preserve a visible audit of every source domain contacted and every failure.
- Detect and track final-mile or handoff tracking numbers as linked shipment legs.
- Merge linked-leg events into one timeline, folding the same scan reported by several sources into one entry that names them all.
- Open the carrier pages ParcelPal cannot read in your browser instead of pretending they failed.
- Turn any individual source off, which also deletes the events it contributed.
- Archive and restore packages without deleting history.
- Scan shipping-label barcodes using the open-source ZXing scanner.
- Share text containing a tracking number into ParcelPal.
- Optional six-hour WorkManager refresh and status-change notifications.
- Encrypted or plain local backup and restore through Android's Storage Access Framework.
- No broad storage permission; ParcelPal can access only the backup file selected by the user.

## Privacy boundary

ParcelPal does not operate a server. Network requests go directly from the phone to the enabled tracking source. A contacted source can therefore see the tracking number, carrier hint, IP address, request time, and ordinary HTTP metadata. It does not receive the local package name or the complete local package list.

Requests are HTTPS-only, responses are bounded, and requests are restricted to source-specific allowlisted hostnames. Generic page sources do not retain cookies. The ParcelsApp adapter creates one ephemeral first-party session and discards it after the lookup. The Packy 1ST adapter performs one cookie-free JSON request to `packyapp.com` and is used only for tracking numbers in the verified `1ST` plus eleven-digit format. The Cainiao adapter performs one cookie-free JSON request to `global.cainiao.com`, and is offered only for the number formats Cainiao actually carries, so a domestic USPS or UPS number is never sent to it.

USPS requires browser-rendered JavaScript for current tracking results. ParcelPal therefore creates an offscreen Android System WebView only for a USPS lookup. It permits HTTPS traffic only to `usps.com` and its subdomains, blocks third-party resources, disables third-party cookies, file and content access, mixed content, popups, geolocation, and JavaScript bridges, then clears cookies, cache, and browser state and destroys the WebView. No browser UI is exposed to the user and no browser state is reused between lookups.

The camera permission is used only after the user selects **Scan barcode**. Notification permission is used only for optional background status notifications.

See [PRIVACY.md](PRIVACY.md) and [ARCHITECTURE.md](ARCHITECTURE.md).

## Tracking-source limitation

Carrier and aggregator interfaces change, rate-limit clients, and may introduce interactive challenges. ParcelPal uses constrained source-specific clients and reports failures instead of bypassing a challenge or fabricating tracking data.

ParcelPal only ships a source when its protocol has been verified end to end:

| Source | How it is read |
| --- | --- |
| USPS | Offscreen browser, first-party DOM extraction after the page renders |
| Cainiao | Structured JSON endpoint, no session required |
| Packy | Structured JSON endpoint for the verified `1ST` format |
| ParcelsApp | The structured JSON protocol used by its own web client |

Most other carriers publish tracking only through a JavaScript application behind bot protection, which no plain fetch can read. Rather than ship sources that always fail, ParcelPal lists those carriers as links you can open in your browser. Adding one as a real source means adding a verified extraction script for the shared browser source, not a scraper that guesses.

No local-only app can guarantee permanent support for every carrier. The source audit in each package detail screen makes every contacted host and every failure explicit, and `tools/live-source-check.py` tells you whether a failing source changed its protocol or simply has no record of your number.

## Build

Requirements:

- JDK 17
- Android SDK platform 36
- Android SDK Build Tools 35.0.0 or newer
- Gradle 8.11.1

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

Offline source checks, and the live protocol check for a real tracking number:

```bash
bash tools/validate-source.sh
python3 tools/live-source-check.py <tracking-number>
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs source privacy checks, unit tests, Android lint, and APK assembly on every push and pull request. Successful pushes to `main` also publish the APK and SHA-256 checksum as a GitHub Release.

## License

ParcelPal is free software licensed under the GNU General Public License, version 3. See [LICENSE](LICENSE).
