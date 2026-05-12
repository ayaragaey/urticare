package com.example.ayasantihistaminestracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

public class CycleReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "CYCLE_REMINDER_CHANNEL";
    private static final String ACTION_YES = "com.example.ayasantihistaminestracker.YES";
    private static final String ACTION_NO = "com.example.ayasantihistaminestracker.NO";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) {
            checkAndNotify(context);
        } else if (intent.getAction().equals(ACTION_YES)) {
            // Open App to Profile
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.putExtra("target", "profile");
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(openIntent);
            dismiss(context);
        } else if (intent.getAction().equals(ACTION_NO)) {
            dismiss(context);
        }
    }

    private void checkAndNotify(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("sex_f", false)) return;

        long start = prefs.getLong("mens_start", 0);
        if (start == 0) return;

        int cycleDays = CycleReminderManager.getAverageCycleLength(context);
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(start);
        next.add(Calendar.DAY_OF_YEAR, cycleDays);

        Calendar today = Calendar.getInstance();
        // Notify if today is >= expected date
        if (today.getTimeInMillis() >= next.getTimeInMillis()) {
            showNotification(context);
        }
    }

    private void showNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Menstruation Cycle", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        Intent yesIntent = new Intent(context, CycleReminderReceiver.class).setAction(ACTION_YES);
        PendingIntent yesPi = PendingIntent.getBroadcast(context, 1, yesIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent noIntent = new Intent(context, CycleReminderReceiver.class).setAction(ACTION_NO);
        PendingIntent noPi = PendingIntent.getBroadcast(context, 2, noIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_pill)
                .setContentTitle(context.getString(R.string.notification_mens_title))
                .setContentText(context.getString(R.string.notification_mens_question))
                .addAction(android.R.drawable.checkbox_on_background, "Yes", yesPi)
                .addAction(android.R.drawable.checkbox_off_background, "No", noPi)
                .setAutoCancel(true);

        manager.notify(1001, builder.build());
    }

    private void dismiss(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(1001);
    }
}