package com.example.ayasantihistaminestracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderManager {

    public static long scheduleReminder(Context context, String id, String medName, int intervalHours, String title) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("med_name", medName);
        intent.putExtra("reminder_id", id);
        intent.putExtra("interval", intervalHours);
        intent.putExtra("title", title);
        intent.putExtra("type", "medication");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerTime = System.currentTimeMillis() + (intervalHours * 60 * 60 * 1000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else if (alarmManager != null) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } else {
            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        }
        return triggerTime;
    }

    public static long scheduleVitaminReminder(Context context, String id, String vitName, int intervalHours) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("med_name", vitName); // Using med_name extra for consistency in Receiver
        intent.putExtra("reminder_id", id);
        intent.putExtra("interval", intervalHours);
        intent.putExtra("type", "vitamin");

        PendingIntent pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long triggerTime = System.currentTimeMillis() + (intervalHours * 60 * 60 * 1000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am != null && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else if (am != null) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        } else {
            if (am != null) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        }
        return triggerTime;
    }

    public static void scheduleAppointment(Context context, String id, String title, long triggerTime, String repeat) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", id);
        intent.putExtra("title", title);
        intent.putExtra("type", "appointment");
        intent.putExtra("repeat", repeat);

        PendingIntent pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am != null && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else if (am != null) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        } else {
            if (am != null) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        }
    }

    public static void scheduleWaterReminder(Context context, int intervalHours) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("type", "water");
        intent.putExtra("interval", intervalHours);

        PendingIntent pi = PendingIntent.getBroadcast(context, 2001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerTime = System.currentTimeMillis() + (intervalHours * 60 * 60 * 1000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am != null && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            } else if (am != null) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        } else {
            if (am != null) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
            }
        }
    }

    public static void cancelWaterReminder(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 2001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) {
            am.cancel(pi);
        }
    }

    public static void cancelReminder(Context context, String id) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}