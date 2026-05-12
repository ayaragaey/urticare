package com.example.ayasantihistaminestracker;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class PillApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        CycleReminderManager.scheduleDailyCheck(this);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Med reminders
                NotificationChannel medChannel = new NotificationChannel(
                        "MED_REMINDER_CHANNEL",
                        "Medication Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                manager.createNotificationChannel(medChannel);

                // Cycle reminders
                NotificationChannel cycleChannel = new NotificationChannel(
                        "CYCLE_REMINDER_CHANNEL",
                        "Menstruation Cycle",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                manager.createNotificationChannel(cycleChannel);
            }
        }
    }
}