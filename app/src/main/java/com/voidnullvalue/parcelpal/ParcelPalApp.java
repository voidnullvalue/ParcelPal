package com.voidnullvalue.parcelpal;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.voidnullvalue.parcelpal.worker.RefreshScheduler;

public final class ParcelPalApp extends Application {
    public static final String CHANNEL_TRACKING = "tracking_updates";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        RefreshScheduler.apply(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_TRACKING,
                    "Package updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications when a saved package status changes");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
