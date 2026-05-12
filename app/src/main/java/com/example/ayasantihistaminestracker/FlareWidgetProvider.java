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

public class FlareWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_FLARE_UP = "com.example.ayasantihistaminestracker.ACTION_FLARE_UP";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.flare_widget);

        Intent intent = new Intent(context, FlareWidgetProvider.class);
        intent.setAction(ACTION_FLARE_UP);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.flare_widget_button, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_FLARE_UP.equals(intent.getAction())) {
            logFlareUp(context);
            
            Intent updateIntent = new Intent("com.example.ayasantihistaminestracker.UPDATE_UI");
            context.sendBroadcast(updateIntent);
            
            Toast.makeText(context, "Flare Up Logged!", Toast.LENGTH_SHORT).show();
        }
    }

    private void logFlareUp(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        
        Date now = new Date();
        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String logEntry = fullFormat.format(now);

        String logs = sharedPreferences.getString("flare_logs", "");
        if (logs.isEmpty()) {
            logs = logEntry;
        } else {
            logs = logEntry + "\n" + logs;
        }

        sharedPreferences.edit()
                .putString("flare_logs", logs)
                .apply();
    }
}