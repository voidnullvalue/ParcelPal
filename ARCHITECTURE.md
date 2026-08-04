# Architecture

## Data flow

1. A tracking number and optional package name are stored in the local SQLite database.
2. `CarrierDetector` selects a carrier hint unless the user supplies one.
3. `SourceRegistry` loads allowlisted source recipes from `assets/sources.json`.
4. `ShipmentRepository` tries matching carrier-owned sources followed by enabled aggregators.
5. Generic sources use `SafeHttpClient`, which enforces HTTPS, exact hostname allowlists, bounded redirects, timeouts, and a 3 MB response cap.
6. ParcelsApp uses its structured first-party JSON web protocol.
7. USPS uses an offscreen Android System WebView because the official tracking result is browser-rendered. `UspsUrlPolicy` blocks every non-USPS request, `usps_extract.js` returns only the relevant DOM fields, and `UspsDomParser` converts them to the common tracking model.
8. Events and linked legs are written locally. The package's displayed status is selected from the most recently timestamped successful leg.

## Components

- `data/DatabaseHelper`: SQLite schema, migrations, backup serialization, and local CRUD.
- `data/ShipmentRepository`: refresh orchestration, source fallback, linked-leg refresh, and aggregate status selection.
- `source/SourceRegistry`: data-driven source configuration and source-specific client selection.
- `source/GenericHtmlSource`: constrained HTTP fetch plus parsing.
- `source/ParcelsAppWebSource`: ephemeral first-party session and structured JSON tracking request.
- `source/UspsBrowserSource`: ephemeral offscreen WebView for the first-party USPS tracking page.
- `source/UspsUrlPolicy`: HTTPS and `usps.com`-subdomain network boundary.
- `source/UspsDomParser`: USPS status, event, timestamp, location, and ETA normalization.
- `network/SafeHttpClient`: generic network security boundary.
- `backup/BackupCodec`: PBKDF2-HMAC-SHA256 plus AES-256-GCM encrypted backup format.
- `worker/RefreshWorker`: optional periodic refresh and notifications.
- `ui`: Android views for package management, timelines, settings, scanning, and backup.

## USPS browser lifecycle

The USPS source is invoked from the repository's background refresh path. It creates the WebView on the main looper, clears any existing WebView cookies, enables only the browser capabilities required by USPS, and loads the official tracking URL. Top-level navigation and subresources are limited to HTTPS URLs on `usps.com` or its subdomains. Third-party cookies and all JavaScript bridges are disabled. The source polls the versioned local extraction script until USPS status nodes appear, parses the returned JSON, then clears cookies, history, and cache and destroys the WebView. The synchronous source call has a bounded timeout and never exposes the WebView in the UI.

## Database

`shipments` stores the user-facing package and aggregate state. `tracking_legs` stores discovered handoff numbers. `tracking_events` stores events for the root tracking number and each linked leg, with deterministic SHA-256 event keys for deduplication.

## Source maintenance

A source recipe defines its ID, display name, kind, URL template, allowed hosts, and supported carrier names. Source-specific protocols and DOM formats can change, so parser fixtures, extraction contracts, and source recipes must be updated together. Live USPS verification uses the same `usps_extract.js` file as the Android source while blocking every non-USPS network request.
