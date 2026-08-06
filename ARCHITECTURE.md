# Architecture

## Data flow

1. A tracking number and optional package name are stored in the local SQLite database.
2. `CarrierDetector.detectAll` returns every carrier whose number format matches, most likely first. A number can belong to more than one carrier at once: a `1ST` consignment is issued by 1ST Group and moved by Cainiao, and an S10 number ending in a country code is handled by both that country's post and the origin consolidator. An explicit carrier chosen by the user is kept as the primary candidate.
3. `SourceRegistry` loads allowlisted source recipes from `assets/sources.json` and returns every enabled source that supports any candidate carrier, carrier-owned sources first and then aggregators, each ordered by trust.
4. `ShipmentRepository` queries those sources **concurrently** and keeps every one that answers, rather than stopping at the first success. Three lookups run at a time and the whole fan-out for one number is bounded at 75 seconds.
5. Each source's events are written under its own `source_id`, so refreshing one source never deletes another's history.
6. Discovered handoff numbers from all sources are merged into one leg set, and each leg is fetched the same way.
7. The package's displayed status is chosen by recency first, then source trust, then event count. The timeline shown to the user is produced by `TimelineMerger`, which folds the same physical scan reported by several sources into one entry that names all of them.
8. Sources ParcelPal cannot read at all are declared with kind `link` and offered on the detail screen as external browser links instead of being fetched and failing.

## Components

- `data/DatabaseHelper`: SQLite schema, migrations, backup serialization, and local CRUD.
- `data/ShipmentRepository`: concurrent source fan-out, per-source persistence, linked-leg refresh, and aggregate status selection.
- `data/SourcePreferences`: per-source opt-out keyed by source id, honoring the pre-3.0 preference keys.
- `source/SourceRegistry`: data-driven source configuration and source-specific client selection.
- `source/GenericHtmlSource`: constrained HTTP fetch plus parsing, for sources that serve tracking data in server-rendered HTML.
- `source/CainiaoTrackingSource` / `CainiaoJsonParser`: cookie-free JSON lookup against `global.cainiao.com`, which holds the origin-side scans for AliExpress consignments.
- `source/PackyTrackingSource` / `PackyJsonParser`: cookie-free, hostname-restricted JSON lookup for the verified 1ST Group format.
- `source/ParcelsAppWebSource` / `ParcelsAppJsonParser`: ephemeral first-party session and structured JSON tracking request; every detected carrier is offered as a slug before falling back to the site's own auto-detection.
- `source/BrowserSource`: reusable offscreen browser session for carriers that only render tracking client-side, configured entirely from a recipe.
- `source/BrowserHostPolicy`: HTTPS and per-source host-suffix network boundary for browser sources.
- `source/BrowserExtractionParser` / `UspsDomParser`: the extraction-script JSON contract and the USPS implementation of it.
- `network/SafeHttpClient`: generic network security boundary.
- `util/TimelineMerger`: cross-source event deduplication.
- `backup/BackupCodec`: PBKDF2-HMAC-SHA256 plus AES-256-GCM encrypted backup format.
- `worker/RefreshWorker`: optional periodic refresh and notifications.
- `ui`: Android views for package management, timelines, settings, scanning, and backup.

## Browser source lifecycle

A browser source is invoked from the repository's background refresh path. It creates the WebView on the main looper, clears any existing WebView cookies, enables only the browser capabilities the carrier's page requires, and loads the recipe's tracking URL. Top-level navigation and subresources are limited to HTTPS URLs on the host suffixes the recipe declares. Third-party cookies and all JavaScript bridges are disabled. The source polls the recipe's local extraction script until it reports `ready` or `challenge`, parses the returned JSON, then clears cookies, history, and cache and destroys the WebView. The synchronous source call has a bounded timeout and never exposes the WebView in the UI.

USPS is currently the only browser source. Adding another requires a recipe with `browserHosts` and `script`, a captured-fixture parser registered in `SourceRegistry.browserParser`, and an extraction script verified against the live page.

## Database

`shipments` stores the user-facing package and aggregate state. `tracking_legs` stores discovered handoff numbers. `tracking_events` stores events for the root tracking number and each linked leg, keyed by `(shipment_id, source_id, event_key)` with deterministic SHA-256 event keys. Schema version 3 introduced the `source_id` column and its unique constraint; the migration rebuilds the table because SQLite cannot alter one in place.

## Merging

`TimelineMerger` groups events by tracking number and normalized wording, then clusters them in time. Two entries merge when they are within a minute of each other, when one has no timestamp, or when they are within three hours and their locations are compatible. The surviving entry is the most detailed one: a timestamp beats none, a location beats none, and source trust breaks the remaining ties. The raw per-source rows are never destroyed, so the merge can change without a re-fetch.

## Source maintenance

A source recipe defines its id, display name, kind, URL template, allowed hosts, supported carrier names, trust score, and — for browser sources — its host suffixes and extraction script. Source-specific protocols and DOM formats change, so parser fixtures, extraction contracts, and source recipes must be updated together.

- `app/src/test/.../*ParserTest` pin each parser against captured fixtures.
- `app/src/test/.../SourceManifestTest` pins `sources.json` itself: placeholders, HTTPS, host/URL agreement, browser script presence, and the carrier routing that sends a `1ST` number to both its issuing carrier and Cainiao.
- `tools/live-source-check.py` performs the exact request each adapter performs against a real number and asserts that the fields the parser reads are still present. Run it when a source starts failing; it distinguishes a changed protocol from a number the source simply does not know.
