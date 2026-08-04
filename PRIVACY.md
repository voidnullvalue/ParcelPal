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

The ParcelsApp adapter creates a first-party web session by requesting `parcelsapp.com`, then uses the returned CSRF token and session cookie to submit one structured tracking request to that same hostname. The cookie is held only in memory inside the lookup client and is discarded when the lookup finishes. ParcelPal does not retain it, expose it to other sources, or use it to identify the user across lookups.

The USPS adapter uses Android System WebView because the first-party USPS tracking page requires browser-rendered JavaScript. It allows HTTPS requests only to `usps.com` and its subdomains. Third-party resources, including advertising and externally hosted fonts, are blocked. Third-party cookies, file access, content-provider access, mixed content, geolocation, popups, multiple windows, and JavaScript interfaces are disabled. After extraction, ParcelPal clears the first-party cookies, cache, and history and destroys the WebView. The browser is not shown to the user and its state is not reused.

## Permissions

- `INTERNET`: query enabled tracking sources.
- `CAMERA`: scan a barcode only after the user starts the scanner.
- `POST_NOTIFICATIONS`: optional package-update notifications on Android 13 and later.

ParcelPal does not request contacts, location, email, phone, advertising ID, or broad file-storage access.

## Excluded components

The project intentionally contains no Firebase, Crashlytics, analytics, advertising SDK, remote configuration, general-purpose embedded browser, or ParcelPal backend. The sole WebView use is the constrained, offscreen USPS source described above.

## Residual risks

Tracking numbers may reveal shipment relationships to a carrier or aggregator. Tracking interfaces can change, return incomplete data, rate-limit requests, or introduce interactive challenges. ParcelPal records these failures locally and does not bypass access controls. A first-party source can still correlate requests through the public IP address and ordinary network metadata even when ParcelPal does not retain cookies.
