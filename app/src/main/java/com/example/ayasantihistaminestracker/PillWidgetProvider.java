package com.example.ayasantihistaminestracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PillWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_PILL_TAKEN = "com.example.ayasantihistaminestracker.ACTION_PILL_TAKEN";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.pill_widget);

        Intent intent = new Intent(context, PillWidgetProvider.class);
        intent.setAction(ACTION_PILL_TAKEN);
        
        // Using FLAG_IMMUTABLE or FLAG_MUTABLE depending on target SDK, 
        // but since we are working on a modern app, FLAG_IMMUTABLE is safer.
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_PILL_TAKEN.equals(intent.getAction())) {
            logPillTaken(context);
            
            // Broadcast intent to update app if it's open
            Intent updateIntent = new Intent("com.example.ayasantihistaminestracker.UPDATE_UI");
            context.sendBroadcast(updateIntent);
            
            Toast.makeText(context, "Pill Taken Logged!", Toast.LENGTH_SHORT).show();
        }
    }

    private void logPillTaken(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        
        Date now = new Date();
        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String logEntry = fullFormat.format(now);

        String logs = sharedPreferences.getString("pill_logs", "");
        if (logs.isEmpty()) {
            logs = logEntry;
        } else {
            logs = logEntry + "\n" + logs;
        }

        sharedPreferences.edit()
                .putString("pill_logs", logs)
                .putLong("last_pill_timestamp", now.getTime())
                .apply();
    }
}