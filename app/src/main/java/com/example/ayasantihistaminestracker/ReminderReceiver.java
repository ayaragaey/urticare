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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Calendar;
import java.util.List;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "MED_REMINDER_CHANNEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        String type = intent.getStringExtra("type");
        if (type == null) type = "medication";

        String reminderId = intent.getStringExtra("reminder_id");
        String titleExtra = intent.getStringExtra("title");

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Medication Reminders", NotificationManager.IMPORTANCE_HIGH);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, reminderId.hashCode(), mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (type.equals("appointment")) {
            String repeat = intent.getStringExtra("repeat");
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo_pill)
                    .setContentTitle(titleExtra + " Reminder")
                    .setContentText("It's time for your " + titleExtra)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent);

            if (manager != null) manager.notify(reminderId.hashCode(), builder.build());

            if (repeat != null && !repeat.equals("None")) {
                long nextTime = calculateNextRepeat(repeat);
                ReminderManager.scheduleAppointment(context, reminderId, titleExtra, nextTime, repeat);
                updateAppTriggerTimeInPrefs(context, reminderId, nextTime);
            } else {
                updateAppActiveInPrefs(context, reminderId, false);
            }

        } else if (type.equals("water")) {
            int interval = intent.getIntExtra("interval", 1);
            SharedPreferences profilePrefs = context.getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
            SharedPreferences orgPrefs = context.getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
            
            String name = profilePrefs.getString("name", "Friend");
            if (name.isEmpty()) name = "Friend";
            
            String[] messages = {
                "drink water before your organs file a complaint against you.",
                "hydrate or die-drate. Your choice.",
                "even your phone is at 20%—imagine your battery right now.",
                "water check: because coffee doesn’t count, nice try.",
                "your skin called. It wants water, not vibes.",
                "be a responsible adult… drink water like you pay bills (reluctantly).",
                "reminder: you are not a cactus 🌵",
                "drink water so your brain stops buffering.",
                "if you feel dramatic, it might just be dehydration.",
                "go drink water before I personally send you a glass."
            };
            
            int lastIndex = orgPrefs.getInt("water_msg_index", -1);
            int nextIndex = (lastIndex + 1) % messages.length;
            orgPrefs.edit().putInt("water_msg_index", nextIndex).apply();
            
            String message = name + ", " + messages[nextIndex];
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo_pill)
                    .setContentTitle("Sip Time 💦")
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent);

            if (manager != null) manager.notify(2001, builder.build());
            
            // Reschedule
            ReminderManager.scheduleWaterReminder(context, interval);

        } else if (type.equals("vitamin")) {
            String medName = intent.getStringExtra("med_name");
            int interval = intent.getIntExtra("interval", 1);
            SharedPreferences profilePrefs = context.getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
            String name = profilePrefs.getString("name", "Friend");
            if (name.isEmpty()) name = "Friend";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo_pill)
                    .setContentTitle("Hey " + name + ", don’t forget your vitamins!")
                    .setContentText("Vitamin: " + medName)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent);

            if (manager != null) manager.notify(reminderId.hashCode(), builder.build());

            long nextTime = ReminderManager.scheduleVitaminReminder(context, reminderId, medName, interval);
            updateMedTriggerTimeInPrefs(context, reminderId, nextTime);

        } else {
            String medName = intent.getStringExtra("med_name");
            int interval = intent.getIntExtra("interval", 1);
            SharedPreferences profilePrefs = context.getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
            String name = profilePrefs.getString("name", "Friend");
            if (name.isEmpty()) name = "Friend";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo_pill)
                    .setContentTitle("💊 Time for your meds, " + name)
                    .setContentText("Dose: " + medName)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent);

            if (manager != null) manager.notify(reminderId.hashCode(), builder.build());

            long nextTime = ReminderManager.scheduleReminder(context, reminderId, medName, interval, titleExtra);
            updateMedTriggerTimeInPrefs(context, reminderId, nextTime);
        }
    }

    private long calculateNextRepeat(String pattern) {
        Calendar cal = Calendar.getInstance();
        if (pattern.equals("Weekly")) cal.add(Calendar.DAY_OF_YEAR, 7);
        else if (pattern.equals("Biweekly")) cal.add(Calendar.DAY_OF_YEAR, 14);
        else if (pattern.equals("Monthly")) cal.add(Calendar.MONTH, 1);
        return cal.getTimeInMillis();
    }

    private void updateMedTriggerTimeInPrefs(Context context, String id, long nextTime) {
        SharedPreferences prefs = context.getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String[] keys = {"urticaria_list", "other_list", "acute_list"};
        for (String key : keys) {
            String json = prefs.getString(key, "[]");
            List<DosageReminder> list = gson.fromJson(json, new TypeToken<List<DosageReminder>>(){}.getType());
            if (list != null) {
                boolean found = false;
                for (DosageReminder r : list) {
                    if (r.id.equals(id)) {
                        r.nextTriggerTime = nextTime;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    prefs.edit().putString(key, gson.toJson(list)).apply();
                    break;
                }
            }
        }
    }

    private void updateAppTriggerTimeInPrefs(Context context, String id, long nextTime) {
        SharedPreferences prefs = context.getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String[] keys = {"doctor_list", "followup_list", "custom_list", "vitamins_list"};
        for (String key : keys) {
            String json = prefs.getString(key, "[]");
            List<AppointmentReminder> list = gson.fromJson(json, new TypeToken<List<AppointmentReminder>>(){}.getType());
            if (list != null) {
                boolean found = false;
                for (AppointmentReminder r : list) {
                    if (r.id.equals(id)) {
                        r.dateTime = nextTime;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    prefs.edit().putString(key, gson.toJson(list)).apply();
                    break;
                }
            }
        }
    }

    private void updateAppActiveInPrefs(Context context, String id, boolean active) {
        SharedPreferences prefs = context.getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String[] keys = {"doctor_list", "followup_list", "custom_list", "vitamins_list"};
        for (String key : keys) {
            String json = prefs.getString(key, "[]");
            List<AppointmentReminder> list = gson.fromJson(json, new TypeToken<List<AppointmentReminder>>(){}.getType());
            if (list != null) {
                boolean found = false;
                for (AppointmentReminder r : list) {
                    if (r.id.equals(id)) {
                        r.isActive = active;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    prefs.edit().putString(key, gson.toJson(list)).apply();
                    break;
                }
            }
        }
    }
}