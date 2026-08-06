package com.voidnullvalue.parcelpal.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingLeg;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.source.SourceRecipe;
import com.voidnullvalue.parcelpal.source.SourceRegistry;
import com.voidnullvalue.parcelpal.source.TrackingSource;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.TimelineMerger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ShipmentRepository {
    private static final int MAX_LEGS_PER_REFRESH = 6;
    /** Concurrent source lookups. Enough to overlap slow sources without flooding a phone radio. */
    private static final int MAX_PARALLEL_SOURCES = 3;
    /** Ceiling for one target's whole fan-out, above the slowest single source. */
    private static final long TARGET_TIMEOUT_SECONDS = 75;

    private final DatabaseHelper database;
    private final SourceRegistry sourceRegistry;

    public ShipmentRepository(Context context) {
        Context app = context.getApplicationContext();
        database = new DatabaseHelper(app);
        sourceRegistry = new SourceRegistry(app, new SourcePreferences(app));
    }

    public long addShipment(String name, String trackingNumber, String carrierHint) {
        String normalizedNumber = CarrierDetector.normalizeTrackingNumber(trackingNumber);
        if (!CarrierDetector.plausible(normalizedNumber)) throw new IllegalArgumentException("Enter a plausible tracking number");
        String carrier = normalizedCarrier(normalizedNumber, carrierHint);
        Shipment existing = database.findShipmentByTracking(normalizedNumber);
        if (existing != null) {
            if (name != null && !name.trim().isEmpty()) database.updateShipmentName(existing.id, name);
            database.updateShipmentCarrier(existing.id, carrier);
            database.setArchived(existing.id, false);
            return existing.id;
        }
        return database.insertShipment(name, normalizedNumber, carrier);
    }

    public List<Shipment> listShipments(boolean archived) { return database.listShipments(archived); }
    public List<Shipment> listActiveShipments() { return database.listShipments(false); }
    public Shipment getShipment(long id) { return database.getShipment(id); }
    public List<TrackingEvent> listEvents(long shipmentId) { return database.listEvents(shipmentId); }
    public List<TrackingLeg> listLegs(long shipmentId) { return database.listLegs(shipmentId); }
    public void deleteShipment(long id) { database.deleteShipment(id); }
    public void renameShipment(long id, String name) { database.updateShipmentName(id, name); }
    public void changeCarrier(long id, String carrier) { database.updateShipmentCarrier(id, carrier); }
    public void setArchived(long id, boolean archived) { database.setArchived(id, archived); }
    public JSONObject exportJson() throws JSONException { return database.exportJson(); }
    public DatabaseHelper.ImportSummary importJson(JSONObject json) throws JSONException { return database.importJson(json); }

    /** The stored timeline with every source's duplicate reports of the same scan folded together. */
    public List<TimelineMerger.MergedEvent> listMergedEvents(long shipmentId) {
        return TimelineMerger.merge(database.listEvents(shipmentId), sourceRegistry.trustBySourceId());
    }

    /** Carrier pages ParcelPal cannot read itself, offered to the user as external links. */
    public List<SourceRecipe> linksFor(Shipment shipment) {
        if (shipment == null) return Collections.emptyList();
        return sourceRegistry.linksFor(targetFor(shipment.trackingNumber, shipment.carrierHint));
    }

    public List<SourceRecipe> fetchableRecipes() { return sourceRegistry.fetchableRecipes(); }

    public void setSourceEnabled(Context context, String sourceId, boolean enabled) {
        new SourcePreferences(context).setSourceEnabled(sourceId, enabled);
        if (!enabled) database.deleteEventsBySource(sourceId);
    }

    public RefreshOutcome refresh(long shipmentId) {
        Shipment shipment = database.getShipment(shipmentId);
        if (shipment == null) return RefreshOutcome.failure("Package no longer exists");

        purgeLegacyFalsePositives(shipmentId);
        shipment = database.getShipment(shipmentId);
        if (shipment == null) return RefreshOutcome.failure("Package no longer exists");

        String previousStatus = shipment.statusText;
        String previousNormalized = shipment.normalizedStatus;
        List<String> attempts = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<TargetSuccess> successes = new ArrayList<>();

        TrackingTarget rootTarget = targetFor(shipment.trackingNumber, shipment.carrierHint);
        if (isAutoCarrier(shipment.carrierHint) && !CarrierDetector.AUTO_DETECT.equalsIgnoreCase(rootTarget.carrierHint)) {
            database.updateShipmentCarrier(shipmentId, rootTarget.carrierHint);
        }

        List<SourceOutcome> rootOutcomes = fetchTarget(rootTarget, attempts, errors);
        Set<String> discoveredLegs = new LinkedHashSet<>();
        for (SourceOutcome outcome : rootOutcomes) {
            TrackingResult result = outcome.result;
            database.replaceEventsForSource(shipmentId, rootTarget.trackingNumber, outcome.sourceId,
                    safeCarrier(result.carrierName, rootTarget.carrierHint), result.sourceName, result.events);
            discoveredLegs.addAll(result.linkedTrackingNumbers);
            successes.add(new TargetSuccess(rootTarget.trackingNumber, outcome));
        }
        if (!discoveredLegs.isEmpty()) {
            database.upsertDiscoveredLegs(shipmentId, shipment.trackingNumber, new ArrayList<>(discoveredLegs));
        }

        List<TrackingLeg> legs = database.listLegs(shipmentId);
        int processed = 0;
        for (TrackingLeg leg : legs) {
            if (processed++ >= MAX_LEGS_PER_REFRESH) break;
            TrackingTarget target = targetFor(leg.trackingNumber, leg.carrierHint);
            List<SourceOutcome> legOutcomes = fetchTarget(target, attempts, errors);
            if (legOutcomes.isEmpty()) {
                database.updateLegError(leg.id, "No enabled source returned data for " + leg.trackingNumber);
                continue;
            }
            for (SourceOutcome outcome : legOutcomes) {
                database.replaceEventsForSource(shipmentId, leg.trackingNumber, outcome.sourceId,
                        safeCarrier(outcome.result.carrierName, target.carrierHint),
                        outcome.result.sourceName, outcome.result.events);
                successes.add(new TargetSuccess(leg.trackingNumber, outcome));
            }
            SourceOutcome best = bestOutcome(legOutcomes);
            database.updateLegResult(leg.id, best.result.normalizedStatus, best.result.statusText,
                    best.result.estimatedDelivery, best.result.sourceId, sourceLabel(legOutcomes), "");
        }

        if (successes.isEmpty()) {
            String error = errors.isEmpty() ? "No enabled source returned usable tracking data" : String.join("\n", errors);
            database.updateShipmentError(shipmentId, error, String.join("\n", attempts));
            return RefreshOutcome.failure(error);
        }

        TargetSuccess current = chooseCurrent(successes);
        TrackingResult result = current.outcome.result;
        String sourceName = sourceLabel(outcomesFor(successes, current.trackingNumber));
        if (!current.trackingNumber.equals(shipment.trackingNumber)) sourceName += " · linked leg";
        database.updateShipmentResult(shipmentId, result.normalizedStatus, result.statusText,
                estimatedDelivery(successes, result), result.sourceId, sourceName,
                errors.isEmpty() ? "" : String.join("\n", errors), String.join("\n", attempts));

        boolean changed = !safe(previousStatus).equals(safe(result.statusText)) ||
                !safe(previousNormalized).equals(safe(result.normalizedStatus));
        return RefreshOutcome.success(result, changed, errors);
    }

    private void purgeLegacyFalsePositives(long shipmentId) {
        SQLiteDatabase db = database.getWritableDatabase();
        List<String> staleLegs = new ArrayList<>();
        db.beginTransaction();
        try {
            try (Cursor cursor = db.query("tracking_legs", new String[]{"tracking_number"},
                    "shipment_id=? AND normalized_status='UNKNOWN'",
                    new String[]{Long.toString(shipmentId)}, null, null, null)) {
                while (cursor.moveToNext()) staleLegs.add(cursor.getString(0));
            }

            for (String trackingNumber : staleLegs) {
                db.delete("tracking_events", "shipment_id=? AND tracking_number=?",
                        new String[]{Long.toString(shipmentId), trackingNumber});
            }
            db.delete("tracking_legs", "shipment_id=? AND normalized_status='UNKNOWN'",
                    new String[]{Long.toString(shipmentId)});

            String genericLabels = "'delivery time','estimated delivery','expected delivery','delivery date'," +
                    "'shipment tracking','tracking details','tracking information','package status'," +
                    "'shipment status','delivery status','status'";
            db.execSQL("DELETE FROM tracking_events WHERE shipment_id=? AND lower(trim(description)) IN (" + genericLabels + ")",
                    new Object[]{shipmentId});
            db.execSQL("UPDATE shipments SET normalized_status='UNKNOWN', status_text='Unknown', " +
                            "estimated_delivery='', source_id='', source_name='' " +
                            "WHERE id=? AND lower(trim(status_text)) IN (" + genericLabels + ")",
                    new Object[]{shipmentId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Queries every enabled source for one tracking number at the same time and keeps all of them
     * that answered.
     *
     * <p>A number can be live in several systems at once: the issuing carrier, the line-haul
     * operator, and one or more aggregators. Stopping at the first answer hides the rest, so this
     * collects every successful result and lets the caller merge them.
     */
    private List<SourceOutcome> fetchTarget(TrackingTarget target, List<String> attempts, List<String> errors) {
        List<TrackingSource> sources = sourceRegistry.sourcesFor(target);
        if (sources.isEmpty()) {
            String message = "No enabled source supports " + target.carrierHint;
            errors.add(message);
            return Collections.emptyList();
        }

        List<SourceOutcome> outcomes = new ArrayList<>();
        List<String> targetErrors = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL_SOURCES, sources.size()));
        try {
            List<Future<TrackingResult>> futures = new ArrayList<>(sources.size());
            for (TrackingSource source : sources) {
                futures.add(pool.submit((Callable<TrackingResult>) () -> source.fetch(target)));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TARGET_TIMEOUT_SECONDS);
            for (int index = 0; index < sources.size(); index++) {
                TrackingSource source = sources.get(index);
                String hosts = String.join(", ", source.allowedHosts());
                try {
                    long remaining = Math.max(0, deadline - System.nanoTime());
                    TrackingResult result = futures.get(index).get(remaining, TimeUnit.NANOSECONDS);
                    attempts.add(source.displayName() + " [" + hosts + "] -> success");
                    outcomes.add(new SourceOutcome(source.id(), source.trust(), result));
                } catch (TimeoutException timeout) {
                    futures.get(index).cancel(true);
                    String message = "timed out";
                    attempts.add(source.displayName() + " [" + hosts + "] -> failed: " + message);
                    targetErrors.add(source.displayName() + ": " + message);
                } catch (ExecutionException failed) {
                    String message = safeMessage(failed.getCause());
                    attempts.add(source.displayName() + " [" + hosts + "] -> failed: " + message);
                    targetErrors.add(source.displayName() + ": " + message);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    futures.get(index).cancel(true);
                    targetErrors.add(source.displayName() + ": interrupted");
                    break;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        if (!targetErrors.isEmpty()) {
            errors.add(target.trackingNumber + ": " + String.join("; ", targetErrors));
        }
        outcomes.sort((first, second) -> Integer.compare(second.trust, first.trust));
        return outcomes;
    }

    /**
     * Picks the result that describes the package right now.
     *
     * <p>Recency decides first, because a source that has seen a later scan knows more. Source
     * trust breaks ties, and event count breaks the remaining ties, so a result with no usable
     * timestamps can never displace one that has them purely by arriving later.
     */
    private static TargetSuccess chooseCurrent(List<TargetSuccess> successes) {
        TargetSuccess chosen = successes.get(0);
        for (int i = 1; i < successes.size(); i++) {
            if (isBetter(successes.get(i), chosen)) chosen = successes.get(i);
        }
        return chosen;
    }

    private static boolean isBetter(TargetSuccess candidate, TargetSuccess incumbent) {
        long candidateTime = candidate.outcome.result.newestEventTime();
        long incumbentTime = incumbent.outcome.result.newestEventTime();
        if (candidateTime != incumbentTime) return candidateTime > incumbentTime;
        if (candidate.outcome.trust != incumbent.outcome.trust) {
            return candidate.outcome.trust > incumbent.outcome.trust;
        }
        return candidate.outcome.result.events.size() > incumbent.outcome.result.events.size();
    }

    private static SourceOutcome bestOutcome(List<SourceOutcome> outcomes) {
        SourceOutcome chosen = outcomes.get(0);
        for (SourceOutcome candidate : outcomes) {
            long candidateTime = candidate.result.newestEventTime();
            long chosenTime = chosen.result.newestEventTime();
            if (candidateTime > chosenTime || (candidateTime == chosenTime && candidate.trust > chosen.trust)) {
                chosen = candidate;
            }
        }
        return chosen;
    }

    private static List<SourceOutcome> outcomesFor(List<TargetSuccess> successes, String trackingNumber) {
        List<SourceOutcome> outcomes = new ArrayList<>();
        for (TargetSuccess success : successes) {
            if (success.trackingNumber.equals(trackingNumber)) outcomes.add(success.outcome);
        }
        return outcomes;
    }

    /** Names every source that contributed, so the detail screen can show corroboration. */
    private static String sourceLabel(List<SourceOutcome> outcomes) {
        List<String> names = new ArrayList<>();
        for (SourceOutcome outcome : outcomes) {
            String name = safe(outcome.result.sourceName);
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        return names.isEmpty() ? "" : String.join(" + ", names);
    }

    /** Uses the chosen result's estimate, falling back to any other source that published one. */
    private static String estimatedDelivery(List<TargetSuccess> successes, TrackingResult chosen) {
        String preferred = safe(chosen.estimatedDelivery);
        if (!preferred.isEmpty()) return preferred;
        for (TargetSuccess success : successes) {
            String estimate = safe(success.outcome.result.estimatedDelivery);
            if (!estimate.isEmpty()) return estimate;
        }
        return "";
    }

    private static TrackingTarget targetFor(String trackingNumber, String carrierHint) {
        if (isAutoCarrier(carrierHint)) {
            return new TrackingTarget(trackingNumber, CarrierDetector.AUTO_DETECT);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(carrierHint.trim());
        for (String detected : CarrierDetector.detectAll(trackingNumber)) {
            if (!CarrierDetector.AUTO_DETECT.equals(detected)) candidates.add(detected);
        }
        return new TrackingTarget(trackingNumber, carrierHint.trim(), candidates);
    }

    private static String normalizedCarrier(String trackingNumber, String requested) {
        if (isAutoCarrier(requested)) return CarrierDetector.detect(trackingNumber);
        return requested.trim();
    }

    private static boolean isAutoCarrier(String carrier) {
        return carrier == null || carrier.trim().isEmpty() ||
                CarrierDetector.AUTO_DETECT.equalsIgnoreCase(carrier.trim());
    }

    private static String safeCarrier(String parsed, String fallback) {
        return parsed == null || parsed.trim().isEmpty() || "Auto-detect".equalsIgnoreCase(parsed) ? fallback : parsed;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "failed";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static final class SourceOutcome {
        final String sourceId;
        final int trust;
        final TrackingResult result;

        SourceOutcome(String sourceId, int trust, TrackingResult result) {
            this.sourceId = sourceId;
            this.trust = trust;
            this.result = result;
        }
    }

    private static final class TargetSuccess {
        final String trackingNumber;
        final SourceOutcome outcome;

        TargetSuccess(String trackingNumber, SourceOutcome outcome) {
            this.trackingNumber = trackingNumber;
            this.outcome = outcome;
        }
    }

    public static final class RefreshOutcome {
        public final boolean success;
        public final TrackingResult result;
        public final String error;
        public final boolean statusChanged;
        public final List<String> warnings;

        private RefreshOutcome(boolean success, TrackingResult result, String error, boolean statusChanged, List<String> warnings) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.statusChanged = statusChanged;
            this.warnings = warnings;
        }

        public static RefreshOutcome success(TrackingResult result, boolean statusChanged, List<String> warnings) {
            return new RefreshOutcome(true, result, "", statusChanged, new ArrayList<>(warnings));
        }

        public static RefreshOutcome failure(String error) {
            return new RefreshOutcome(false, null, error, false, new ArrayList<>());
        }
    }
}
