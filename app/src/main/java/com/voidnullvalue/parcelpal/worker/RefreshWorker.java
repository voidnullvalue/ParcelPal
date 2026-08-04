package com.voidnullvalue.parcelpal.worker;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.voidnullvalue.parcelpal.ParcelPalApp;
import com.voidnullvalue.parcelpal.R;
import com.voidnullvalue.parcelpal.data.ShipmentRepository;
import com.voidnullvalue.parcelpal.data.SourcePreferences;
import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.ui.ShipmentDetailActivity;

import java.util.List;

public final class RefreshWorker extends Worker {
    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        ShipmentRepository repository = new ShipmentRepository(context);
        List<Shipment> shipments = repository.listActiveShipments();
        boolean transientFailure = false;
        for (Shipment shipment : shipments) {
            if (isStopped()) return Result.retry();
            ShipmentRepository.RefreshOutcome outcome = repository.refresh(shipment.id);
            if (!outcome.success) transientFailure = true;
            if (outcome.success && outcome.statusChanged) notifyChanged(shipment.id);
        }
        return transientFailure && getRunAttemptCount() < 2 ? Result.retry() : Result.success();
    }

    private void notifyChanged(long shipmentId) {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Shipment refreshed = new ShipmentRepository(context).getShipment(shipmentId);
        if (refreshed == null) return;
        if (new SourcePreferences(context).notifyDeliveredOnly() && !"DELIVERED".equals(refreshed.normalizedStatus)) return;

        Intent intent = new Intent(context, ShipmentDetailActivity.class)
                .putExtra(ShipmentDetailActivity.EXTRA_SHIPMENT_ID, shipmentId);
        PendingIntent pending = PendingIntent.getActivity(context, (int) shipmentId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ParcelPalApp.CHANNEL_TRACKING)
                .setSmallIcon(R.drawable.ic_package)
                .setContentTitle(refreshed.displayName())
                .setContentText(refreshed.statusText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(refreshed.statusText))
                .setAutoCancel(true)
                .setContentIntent(pending);
        context.getSystemService(NotificationManager.class).notify((int) shipmentId, builder.build());
    }
}
