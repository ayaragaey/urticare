package com.example.ayasantihistaminestracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Calendar;
import java.util.List;

public class CycleReminderManager {

    public static void scheduleDailyCheck(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, CycleReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 999, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);

        if (am != null) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    public static int getAverageCycleLength(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        String json = prefs.getString("cycle_history", "[]");
        List<CycleEntry> list = new Gson().fromJson(json, new TypeToken<List<CycleEntry>>(){}.getType());
        
        if (list == null || list.size() < 2) return 28;

        long totalDays = 0;
        int count = 0;
        for (int i = 0; i < list.size() - 1; i++) {
            long diff = list.get(i).start - list.get(i + 1).start;
            long days = diff / (1000 * 60 * 60 * 24);
            if (days > 20 && days < 40) { 
                totalDays += days;
                count++;
            }
        }
        return count > 0 ? (int) (totalDays / count) : 28;
    }
}