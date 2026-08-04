package com.voidnullvalue.parcelpal.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.voidnullvalue.parcelpal.R;
import com.voidnullvalue.parcelpal.data.ShipmentRepository;
import com.voidnullvalue.parcelpal.data.SourcePreferences;
import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.TrackingTextExtractor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });
    private final ActivityResultLauncher<ScanOptions> barcodeScanner = registerForActivityResult(
            new ScanContract(), result -> {
                if (result.getContents() != null) showAddDialog(result.getContents());
            });

    private ShipmentRepository repository;
    private ShipmentAdapter adapter;
    private RecyclerView recycler;
    private TextView empty;
    private TextView modeBanner;
    private MaterialToolbar toolbar;
    private boolean showingArchived;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = new ShipmentRepository(this);
        recycler = findViewById(R.id.recyclerView);
        empty = findViewById(R.id.emptyView);
        modeBanner = findViewById(R.id.modeBanner);
        toolbar = findViewById(R.id.toolbar);
        adapter = new ShipmentAdapter(this::openShipment);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        findViewById(R.id.addButton).setOnClickListener(v -> showAddDialog(""));
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_scan) {
                scanBarcode();
                return true;
            }
            if (item.getItemId() == R.id.action_refresh_all) {
                refreshAll();
                return true;
            }
            if (item.getItemId() == R.id.action_toggle_archive) {
                showingArchived = !showingArchived;
                updateModeUi();
                loadShipments();
                return true;
            }
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        handleSharedText(getIntent());
        requestNotificationsIfNeeded();
        updateModeUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadShipments();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedText(intent);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }

    private void updateModeUi() {
        modeBanner.setVisibility(showingArchived ? View.VISIBLE : View.GONE);
        Menu menu = toolbar.getMenu();
        if (menu != null) menu.findItem(R.id.action_toggle_archive).setTitle(showingArchived ? "Show active" : "Show archived");
        empty.setText(showingArchived ? "No archived packages." : "No packages. Add or scan a tracking number.");
    }

    private void loadShipments() {
        int generation = loadGeneration.incrementAndGet();
        io.execute(() -> {
            List<Shipment> shipments = repository.listShipments(showingArchived);
            runOnUiThread(() -> {
                if (!isUiActive() || generation != loadGeneration.get()) return;
                adapter.submit(shipments);
                recycler.setVisibility(shipments.isEmpty() ? View.GONE : View.VISIBLE);
                empty.setVisibility(shipments.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showAddDialog(String prefilledTracking) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_shipment, null, false);
        TextInputEditText name = view.findViewById(R.id.nameInput);
        TextInputEditText tracking = view.findViewById(R.id.trackingInput);
        AutoCompleteTextView carrier = view.findViewById(R.id.carrierInput);
        carrier.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CarrierDetector.carrierChoices()));
        carrier.setText(CarrierDetector.defaultChoice(), false);
        String extracted = TrackingTextExtractor.bestCandidate(prefilledTracking);
        tracking.setText(extracted.isEmpty() ? prefilledTracking : extracted);

        AddShipmentFlow flow = new AddShipmentFlow();
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add package")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();
        dialog.setOnCancelListener(ignored -> flow.cancelNavigation());
        dialog.setOnDismissListener(ignored -> flow.cancelNavigation());
        dialog.setOnShowListener(ignored -> {
            Button addButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener(v -> {
                String number = text(tracking);
                if (!CarrierDetector.plausible(number)) {
                    tracking.setError("Enter a plausible tracking number");
                    return;
                }
                if (!flow.beginSubmit()) return;
                addButton.setEnabled(false);
                String packageName = text(name);
                String carrierHint = CarrierDetector.normalizeChoice(carrier.getText());
                io.execute(() -> {
                    try {
                        long id = repository.addShipment(packageName, number, carrierHint);
                        Shipment shipment = repository.getShipment(id);
                        runOnUiThread(() -> {
                            if (!isUiActive()) {
                                flow.cancelNavigation();
                                return;
                            }
                            boolean navigate = flow.completeSubmit();
                            if (dialog.isShowing()) dialog.dismiss();
                            if (navigate) openShipment(shipment, true);
                            else loadShipments();
                        });
                    } catch (RuntimeException e) {
                        flow.failSubmit();
                        runOnUiThread(() -> {
                            if (!isUiActive()) return;
                            addButton.setEnabled(true);
                            tracking.setError(e.getMessage());
                        });
                    }
                });
            });
        });
        dialog.show();
    }

    private void openShipment(Shipment shipment) {
        openShipment(shipment, false);
    }

    private void openShipment(Shipment shipment, boolean autoRefresh) {
        if (shipment == null || !isUiActive()) return;
        startActivity(new Intent(this, ShipmentDetailActivity.class)
                .putExtra(ShipmentDetailActivity.EXTRA_SHIPMENT_ID, shipment.id)
                .putExtra(ShipmentDetailActivity.EXTRA_AUTO_REFRESH, autoRefresh));
    }

    private void refreshAll() {
        Toast.makeText(this, "Refreshing packages", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            int successes = 0;
            List<Shipment> shipments = repository.listShipments(showingArchived);
            for (Shipment shipment : shipments) if (repository.refresh(shipment.id).success) successes++;
            int finalSuccesses = successes;
            runOnUiThread(() -> {
                if (!isUiActive()) return;
                loadShipments();
                Toast.makeText(this, "Updated " + finalSuccesses + " of " + shipments.size(), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void scanBarcode() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a shipping label barcode");
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
        barcodeScanner.launch(options);
    }

    private void handleSharedText(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        String best = TrackingTextExtractor.bestCandidate(shared);
        if (!best.isEmpty()) showAddDialog(best);
        intent.setAction(null);
    }

    private void requestNotificationsIfNeeded() {
        if (!new SourcePreferences(this).backgroundEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private boolean isUiActive() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
