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

For each enabled source attempted, the source receives the queried tracking number, the phone's public IP address, request timing, and standard HTTP headers. ParcelPal never sends the package nickname or a batch list of all saved packages.

## Permissions

- `INTERNET`: query enabled tracking sources.
- `CAMERA`: scan a barcode only after the user starts the scanner.
- `POST_NOTIFICATIONS`: optional package-update notifications on Android 13 and later.

ParcelPal does not request contacts, location, email, phone, advertising ID, or broad file-storage access.

## Excluded components

The project intentionally contains no Firebase, Crashlytics, analytics, advertising SDK, embedded WebView, remote configuration, or ParcelPal backend.

## Residual risks

Tracking numbers may reveal shipment relationships to a carrier or aggregator. Public tracking pages can change, return misleading markup, rate-limit requests, or introduce challenges. ParcelPal records these failures locally and does not bypass access controls.
