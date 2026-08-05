package com.voidnullvalue.parcelpal.ui;

import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.voidnullvalue.parcelpal.R;
import com.voidnullvalue.parcelpal.backup.BackupService;
import com.voidnullvalue.parcelpal.data.DatabaseHelper;
import com.voidnullvalue.parcelpal.data.SourcePreferences;
import com.voidnullvalue.parcelpal.worker.RefreshScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Uri pendingImport;
    private char[] pendingExportPassword = new char[0];

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });
    private final ActivityResultLauncher<String> createBackup = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                if (uri != null) exportBackup(uri, pendingExportPassword);
                pendingExportPassword = new char[0];
            });
    private final ActivityResultLauncher<String> openBackup = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) inspectImport(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        SourcePreferences preferences = new SourcePreferences(this);
        MaterialSwitch direct = findViewById(R.id.directSwitch);
        MaterialSwitch packy = findViewById(R.id.packySwitch);
        MaterialSwitch parcels = findViewById(R.id.parcelsSwitch);
        MaterialSwitch postalNinja = findViewById(R.id.postalNinjaSwitch);
        MaterialSwitch trackGlobal = findViewById(R.id.trackGlobalSwitch);
        MaterialSwitch background = findViewById(R.id.backgroundSwitch);
        MaterialSwitch deliveredOnly = findViewById(R.id.deliveredOnlySwitch);
        direct.setChecked(preferences.directEnabled());
        packy.setChecked(preferences.packyEnabled());
        parcels.setChecked(preferences.parcelsEnabled());
        postalNinja.setChecked(preferences.postalNinjaEnabled());
        trackGlobal.setChecked(preferences.trackGlobalEnabled());
        background.setChecked(preferences.backgroundEnabled());
        deliveredOnly.setChecked(preferences.notifyDeliveredOnly());

        direct.setOnCheckedChangeListener((v, checked) -> preferences.setDirectEnabled(checked));
        packy.setOnCheckedChangeListener((v, checked) -> preferences.setPackyEnabled(checked));
        parcels.setOnCheckedChangeListener((v, checked) -> preferences.setParcelsEnabled(checked));
        postalNinja.setOnCheckedChangeListener((v, checked) -> preferences.setPostalNinjaEnabled(checked));
        trackGlobal.setOnCheckedChangeListener((v, checked) -> preferences.setTrackGlobalEnabled(checked));
        deliveredOnly.setOnCheckedChangeListener((v, checked) -> preferences.setNotifyDeliveredOnly(checked));
        background.setOnCheckedChangeListener((v, checked) -> {
            preferences.setBackgroundEnabled(checked);
            RefreshScheduler.apply(this);
            if (checked && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        });

        findViewById(R.id.exportButton).setOnClickListener(v -> askExportPassword());
        findViewById(R.id.importButton).setOnClickListener(v -> openBackup.launch("*/*"));
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void askExportPassword() {
        EditText input = passwordInput();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Encrypt backup")
                .setMessage("Enter a password, or leave blank for readable JSON.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose file", (dialog, which) -> {
                    pendingExportPassword = input.getText().toString().toCharArray();
                    createBackup.launch(pendingExportPassword.length == 0 ? "parcelpal-backup.json" : "parcelpal-backup.ppb");
                })
                .show();
    }

    private void inspectImport(Uri uri) {
        pendingImport = uri;
        io.execute(() -> {
            try {
                boolean encrypted = new BackupService(this).requiresPassword(uri);
                runOnUiThread(() -> {
                    if (encrypted) askImportPassword(); else importBackup(uri, new char[0]);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not read backup: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void askImportPassword() {
        EditText input = passwordInput();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Backup password")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (dialog, which) -> importBackup(pendingImport, input.getText().toString().toCharArray()))
                .show();
    }

    private void exportBackup(Uri uri, char[] password) {
        io.execute(() -> {
            try {
                new BackupService(this).exportTo(uri, password);
                runOnUiThread(() -> Toast.makeText(this, "Backup exported", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void importBackup(Uri uri, char[] password) {
        if (uri == null) return;
        io.execute(() -> {
            try {
                DatabaseHelper.ImportSummary summary = new BackupService(this).importFrom(uri, password);
                runOnUiThread(() -> Toast.makeText(this,
                        "Imported " + summary.inserted + "; updated " + summary.updated,
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private EditText passwordInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, 0, padding, 0);
        return input;
    }
}
