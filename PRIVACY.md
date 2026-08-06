# Privacy and security

ParcelPal is designed to keep the package collection on the Android device.

## Data ParcelPal stores locally

- Package names
- Tracking numbers and carrier hints
- Linked handoff tracking numbers
- Tracking events, locations, and timestamps returned by sources
- Source success/failure logs
- User-selected settings

Android cloud backup and device-transfer backup are disabled for the database and preferences. The user may create an explicit local backup. Password-protected backups use PBKDF2-HMAC-SHA256 with 210,000 iterations and AES-256-GCM authenticated encryption.

## Network disclosure

For each enabled source attempted, the source receives the queried tracking number, carrier hint when applicable, the phone's public IP address, request timing, and standard HTTP headers. ParcelPal never sends the package nickname or a batch list of all saved packages.

Every enabled source that supports a number is queried during the same refresh, so a number that belongs to two carriers is disclosed to both of those sources rather than only the first one that answers. Each source can be turned off individually in Settings, which also deletes the events it has already contributed. A source is only offered a number whose format it supports.

The Cainiao adapter makes one HTTPS JSON request to `global.cainiao.com` for the individual tracking number, without cookies or a session. It is offered only for the formats Cainiao carries, which are `1ST`, Cainiao's own `LP`/`LA` numbers, YunExpress, 4PX, UniUni, S10 numbers, and numbers whose format ParcelPal cannot identify at all. A recognized domestic number such as a USPS or UPS label is never sent to it.

The Packy adapter is limited to detected 1ST Group tracking numbers. It makes one HTTPS JSON request to `packyapp.com` for the individual tracking number, does not enable cookies, and parses only the returned carrier status and timeline fields.

The ParcelsApp adapter creates a first-party web session by requesting `parcelsapp.com`, then uses the returned CSRF token and session cookie to submit one structured tracking request to that same hostname. The cookie is held only in memory inside the lookup client and is discarded when the lookup finishes. ParcelPal does not retain it, expose it to other sources, or use it to identify the user across lookups.

The browser adapter uses Android System WebView for carriers whose tracking page requires browser-rendered JavaScript; USPS is currently the only such source. It allows HTTPS requests only to the host suffixes that source declares, which for USPS is `usps.com` and its subdomains. Third-party resources, including advertising and externally hosted fonts, are blocked. Third-party cookies, file access, content-provider access, mixed content, geolocation, popups, multiple windows, and JavaScript interfaces are disabled. After extraction, ParcelPal clears the first-party cookies, cache, and history and destroys the WebView. The browser is not shown to the user and its state is not reused.

## Permissions

- `INTERNET`: query enabled tracking sources.
- `CAMERA`: scan a barcode only after the user starts the scanner.
- `POST_NOTIFICATIONS`: optional package-update notifications on Android 13 and later.

ParcelPal does not request contacts, location, email, phone, advertising ID, or broad file-storage access.

## Excluded components

The project intentionally contains no Firebase, Crashlytics, analytics, advertising SDK, remote configuration, general-purpose embedded browser, or ParcelPal backend. The sole WebView use is the constrained, offscreen browser source described above, and an automated audit fails the build if any other file imports it.

Carrier pages ParcelPal cannot read are opened in the device's own browser through a standard view intent. ParcelPal does not render them, and anything that happens in that browser is governed by the browser, not by ParcelPal.

## Residual risks

Tracking numbers may reveal shipment relationships to a carrier or aggregator. Tracking interfaces can change, return incomplete data, rate-limit requests, or introduce interactive challenges. ParcelPal records these failures locally and does not bypass access controls. A first-party source can still correlate requests through the public IP address and ordinary network metadata even when ParcelPal does not retain cookies.
