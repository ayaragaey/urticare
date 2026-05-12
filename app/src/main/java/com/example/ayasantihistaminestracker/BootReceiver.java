package com.example.ayasantihistaminestracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
            Gson gson = new Gson();
            
            reScheduleList(context, prefs, gson, "urticaria_list", "Urticaria");
            reScheduleList(context, prefs, gson, "other_list", null);
            reScheduleList(context, prefs, gson, "acute_list", null);
            reScheduleList(context, prefs, gson, "vitamins_list", "Vitamin");

            reScheduleAppointments(context, prefs, gson, "doctor_list", "Doctor");
            reScheduleAppointments(context, prefs, gson, "followup_list", null);
            reScheduleAppointments(context, prefs, gson, "custom_list", null);
            // Removed reScheduleAppointments for vitamins_list from here

            if (prefs.getBoolean("water_active", false)) {
                int interval = prefs.getInt("water_interval", 0);
                if (interval > 0) {
                    ReminderManager.scheduleWaterReminder(context, interval);
                }
            }
        }
    }

    private void reScheduleList(Context context, SharedPreferences prefs, Gson gson, String key, String defaultTitle) {
        String json = prefs.getString(key, "[]");
        List<DosageReminder> list = gson.fromJson(json, new TypeToken<List<DosageReminder>>(){}.getType());
        if (list != null) {
            for (DosageReminder r : list) {
                if (r.isActive && r.medName != null && !r.medName.isEmpty()) {
                    if ("Vitamin".equals(r.section)) {
                        ReminderManager.scheduleVitaminReminder(context, r.id, r.medName, r.intervalHours);
                    } else {
                        String title = (r.condition != null && !r.condition.isEmpty()) ? r.condition : defaultTitle;
                        ReminderManager.scheduleReminder(context, r.id, r.medName, r.intervalHours, title);
                    }
                }
            }
        }
    }

    private void reScheduleAppointments(Context context, SharedPreferences prefs, Gson gson, String key, String defaultType) {
        String json = prefs.getString(key, "[]");
        List<AppointmentReminder> list = gson.fromJson(json, new TypeToken<List<AppointmentReminder>>(){}.getType());
        if (list != null) {
            for (AppointmentReminder r : list) {
                if (r.isActive && r.dateTime > System.currentTimeMillis()) {
                    String fullTitle = r.type.equals("Doctor") ? "Doctor " + r.title : r.title;
                    ReminderManager.scheduleAppointment(context, r.id, fullTitle, r.dateTime, r.repeatPattern);
                }
            }
        }
    }
}