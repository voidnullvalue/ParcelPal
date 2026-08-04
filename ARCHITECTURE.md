# Architecture

## Data flow

1. A tracking number and optional package name are stored in the local SQLite database.
2. `CarrierDetector` selects a carrier hint unless the user supplies one.
3. `SourceRegistry` loads allowlisted source recipes from `assets/sources.json`.
4. `ShipmentRepository` tries matching carrier-owned sources followed by enabled aggregators.
5. `SafeHttpClient` enforces HTTPS, exact hostname allowlists, no cookies, bounded redirects, timeouts, and a 3 MB response cap.
6. `HeuristicTrackingParser` extracts status, events, ETA text, carrier hints, and possible handoff numbers from public HTML or embedded JSON.
7. Events and linked legs are written locally. The package's displayed status is selected from the most recently timestamped successful leg.

## Components

- `data/DatabaseHelper`: SQLite schema, migrations, backup serialization, and local CRUD.
- `data/ShipmentRepository`: refresh orchestration, source fallback, linked-leg refresh, and aggregate status selection.
- `source/SourceRegistry`: data-driven source configuration.
- `source/GenericHtmlSource`: constrained HTTP fetch plus parsing.
- `network/SafeHttpClient`: network security boundary.
- `backup/BackupCodec`: PBKDF2-HMAC-SHA256 plus AES-256-GCM encrypted backup format.
- `worker/RefreshWorker`: optional periodic refresh and notifications.
- `ui`: Android views for package management, timelines, settings, scanning, and backup.

## Database

`shipments` stores the user-facing package and aggregate state. `tracking_legs` stores discovered handoff numbers. `tracking_events` stores events for the root tracking number and each linked leg, with deterministic SHA-256 event keys for deduplication.

## Source maintenance

A source recipe defines its ID, display name, kind, URL template, exact allowed hosts, and supported carrier names. HTML and embedded JSON formats still change, so parser fixtures and source recipes should be updated together.
