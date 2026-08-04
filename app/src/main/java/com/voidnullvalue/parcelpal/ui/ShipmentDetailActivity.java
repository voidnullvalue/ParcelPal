package com.voidnullvalue.parcelpal.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.voidnullvalue.parcelpal.R;
import com.voidnullvalue.parcelpal.data.ShipmentRepository;
import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingLeg;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ShipmentDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SHIPMENT_ID = "shipment_id";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ShipmentRepository repository;
    private long shipmentId;
    private Shipment shipment;
    private TextView name;
    private TextView tracking;
    private TextView status;
    private TextView estimate;
    private TextView source;
    private TextView legsView;
    private TextView error;
    private TextView networkAudit;
    private LinearLayout timeline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        shipmentId = getIntent().getLongExtra(EXTRA_SHIPMENT_ID, -1);
        repository = new ShipmentRepository(this);
        name = findViewById(R.id.name);
        tracking = findViewById(R.id.tracking);
        status = findViewById(R.id.status);
        estimate = findViewById(R.id.estimate);
        source = findViewById(R.id.source);
        legsView = findViewById(R.id.legs);
        error = findViewById(R.id.error);
        networkAudit = findViewById(R.id.networkAudit);
        timeline = findViewById(R.id.timeline);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.refreshButton).setOnClickListener(v -> refresh());
        findViewById(R.id.editButton).setOnClickListener(v -> showEditDialog());
        findViewById(R.id.archiveButton).setOnClickListener(v -> toggleArchive());
        findViewById(R.id.deleteButton).setOnClickListener(v -> confirmDelete());
        name.setOnClickListener(v -> showEditDialog());
        tracking.setOnClickListener(v -> copyTracking());
        load();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        io.execute(() -> {
            Shipment loaded = repository.getShipment(shipmentId);
            List<TrackingEvent> events = repository.listEvents(shipmentId);
            List<TrackingLeg> legs = repository.listLegs(shipmentId);
            runOnUiThread(() -> render(loaded, events, legs));
        });
    }

    private void render(Shipment loaded, List<TrackingEvent> events, List<TrackingLeg> legs) {
        if (loaded == null) {
            finish();
            return;
        }
        shipment = loaded;
        name.setText(loaded.displayName());
        tracking.setText(loaded.trackingNumber + " · " + loaded.carrierHint);
        status.setText(loaded.statusText == null || loaded.statusText.trim().isEmpty() ? "Not checked" : loaded.statusText);
        estimate.setText(loaded.estimatedDelivery == null || loaded.estimatedDelivery.trim().isEmpty() ? "" : "Estimated delivery: " + loaded.estimatedDelivery);
        estimate.setVisibility(loaded.estimatedDelivery == null || loaded.estimatedDelivery.trim().isEmpty() ? View.GONE : View.VISIBLE);
        String checked = loaded.lastCheckedAt == 0 ? "Never checked" : "Checked " + DateUtils.getRelativeTimeSpanString(loaded.lastCheckedAt);
        source.setText(checked + (loaded.sourceName == null || loaded.sourceName.trim().isEmpty() ? "" : "\nSource: " + loaded.sourceName));
        findViewById(R.id.archiveButton).setContentDescription(loaded.archived ? "Restore package" : "Archive package");
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.archiveButton)).setText(loaded.archived ? "Restore" : "Archive");

        if (legs.isEmpty()) {
            legsView.setVisibility(View.GONE);
        } else {
            StringBuilder text = new StringBuilder("Linked tracking legs:");
            for (TrackingLeg leg : legs) {
                text.append("\n").append(leg.trackingNumber).append(" · ").append(leg.carrierHint);
                if (leg.statusText != null && !leg.statusText.trim().isEmpty() && !"Not checked".equals(leg.statusText)) text.append("\n  ").append(leg.statusText);
            }
            legsView.setText(text.toString());
            legsView.setVisibility(View.VISIBLE);
        }

        String errorText = loaded.lastError == null ? "" : loaded.lastError.trim();
        error.setText(errorText);
        error.setVisibility(errorText.isEmpty() ? View.GONE : View.VISIBLE);
        String attemptLog = loaded.lastAttemptLog == null ? "" : loaded.lastAttemptLog.trim();
        networkAudit.setText(attemptLog.isEmpty() ? "" : "Last network attempts:\n" + attemptLog);
        networkAudit.setVisibility(attemptLog.isEmpty() ? View.GONE : View.VISIBLE);

        timeline.removeAllViews();
        if (events.isEmpty()) {
            timeline.addView(makeTimelineText("No events stored yet.", false));
        } else {
            for (TrackingEvent event : events) {
                String when = event.eventTime == 0 ? "Time unavailable" :
                        DateFormat.getMediumDateFormat(this).format(new Date(event.eventTime)) + " " +
                                DateFormat.getTimeFormat(this).format(new Date(event.eventTime));
                String carrier = event.carrierName == null || event.carrierName.trim().isEmpty() ? "" : event.carrierName + " · ";
                String leg = event.trackingNumber == null || event.trackingNumber.equals(loaded.trackingNumber) ? "" : "\n" + event.trackingNumber;
                String location = event.location == null || event.location.trim().isEmpty() ? "" : "\n" + event.location;
                timeline.addView(makeTimelineText(carrier + when + leg + "\n" + event.description + location, true));
            }
        }
    }

    private TextView makeTimelineText(String text, boolean divider) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        view.setPadding(0, 12, 0, divider ? 20 : 8);
        view.setTextIsSelectable(true);
        return view;
    }

    private void refresh() {
        View button = findViewById(R.id.refreshButton);
        button.setEnabled(false);
        Toast.makeText(this, "Refreshing", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            ShipmentRepository.RefreshOutcome outcome = repository.refresh(shipmentId);
            runOnUiThread(() -> {
                button.setEnabled(true);
                if (!outcome.success) Toast.makeText(this, outcome.error, Toast.LENGTH_LONG).show();
                load();
            });
        });
    }

    private void toggleArchive() {
        if (shipment == null) return;
        boolean archive = !shipment.archived;
        io.execute(() -> {
            repository.setArchived(shipmentId, archive);
            runOnUiThread(() -> {
                Toast.makeText(this, archive ? "Archived" : "Restored", Toast.LENGTH_SHORT).show();
                if (archive) finish(); else load();
            });
        });
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete package permanently?")
                .setMessage("This deletes the tracking number, linked legs, and locally stored event history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> io.execute(() -> {
                    repository.deleteShipment(shipmentId);
                    runOnUiThread(this::finish);
                }))
                .show();
    }

    private void showEditDialog() {
        if (shipment == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_shipment, null, false);
        TextInputEditText nameInput = view.findViewById(R.id.nameInput);
        TextInputEditText trackingInput = view.findViewById(R.id.trackingInput);
        AutoCompleteTextView carrierInput = view.findViewById(R.id.carrierInput);
        nameInput.setText(shipment.name);
        trackingInput.setText(shipment.trackingNumber);
        trackingInput.setEnabled(false);
        carrierInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CarrierDetector.carrierChoices()));
        carrierInput.setText(shipment.carrierHint, false);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit package")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> io.execute(() -> {
                    String newName = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                    String newCarrier = carrierInput.getText() == null ? "Auto-detect" : carrierInput.getText().toString().trim();
                    repository.renameShipment(shipmentId, newName);
                    repository.changeCarrier(shipmentId, newCarrier);
                    runOnUiThread(this::load);
                }))
                .show();
    }

    private void copyTracking() {
        if (shipment == null) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("Tracking number", shipment.trackingNumber));
        Toast.makeText(this, "Tracking number copied", Toast.LENGTH_SHORT).show();
    }
}
