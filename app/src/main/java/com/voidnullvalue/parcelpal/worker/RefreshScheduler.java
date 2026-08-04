package com.voidnullvalue.parcelpal.worker;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.voidnullvalue.parcelpal.data.SourcePreferences;

import java.util.concurrent.TimeUnit;

public final class RefreshScheduler {
    private static final String UNIQUE_WORK = "parcel_pal_periodic_refresh";
    private RefreshScheduler() {}

    public static void apply(Context context) {
        Context app = context.getApplicationContext();
        WorkManager manager = WorkManager.getInstance(app);
        if (!new SourcePreferences(app).backgroundEnabled()) {
            manager.cancelUniqueWork(UNIQUE_WORK);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                RefreshWorker.class, 6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
