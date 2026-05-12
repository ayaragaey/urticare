package com.example.ayasantihistaminestracker;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TrackerFragment extends Fragment implements MainActivity.TitleProvider {

    private Button buttonTakePill;
    private Button buttonResetLogs;
    private Button buttonUndoClearPill;
    private LinearLayout logContainer;
    private TextView timeDifferenceView;
    private TextView txt24h, txt48h, txt72h;
    private TextView pillsSummaryMonth;
    private TextView cortisoneSummaryMonth;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Button buttonFlare;
    private Button buttonCortisone;
    private Button buttonViewSummary;
    private LinearLayout pillsStatsContainer;
    private View btnPrevMonthsAh, btnPrevMonthsCortisone;

    private String lastClearedLogs = "";
    private long lastClearedPillTs = -1;

    private SharedPreferences sharedPreferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            displayPillLogs();
            displayTimeDifference();
        }
    };
    
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            displayTimeDifference();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public String getTitle() {
        return getString(R.string.tab_tracker);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tracker, container, false);

        buttonTakePill = view.findViewById(R.id.button_take_pill);
        buttonResetLogs = view.findViewById(R.id.button_reset_logs);
        buttonUndoClearPill = view.findViewById(R.id.button_undo_clear_pill);
        logContainer = view.findViewById(R.id.log_container);
        timeDifferenceView = view.findViewById(R.id.time_difference);
        
        txt24h = view.findViewById(R.id.txt_count_24h);
        txt48h = view.findViewById(R.id.txt_count_48h);
        txt72h = view.findViewById(R.id.txt_count_72h);
        
        pillsSummaryMonth = view.findViewById(R.id.pills_summary_month);
        cortisoneSummaryMonth = view.findViewById(R.id.cortisone_summary_month);
        btnPrevMonthsAh = view.findViewById(R.id.btn_prev_months_ah);
        btnPrevMonthsCortisone = view.findViewById(R.id.btn_prev_months_cortisone);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        buttonFlare = view.findViewById(R.id.button_flare);
        buttonCortisone = view.findViewById(R.id.button_cortisone);
        buttonViewSummary = view.findViewById(R.id.button_view_summary);
        pillsStatsContainer = view.findViewById(R.id.pills_stats_container);

        sharedPreferences = requireActivity().getSharedPreferences("PillLogs", Context.MODE_PRIVATE);

        buttonTakePill.setOnClickListener(v -> logPillTaken());
        buttonResetLogs.setOnClickListener(v -> resetPillLogs());
        buttonUndoClearPill.setOnClickListener(v -> undoClearPill());
        buttonFlare.setOnClickListener(v -> logFlare());
        buttonCortisone.setOnClickListener(v -> logCortisone());

        buttonViewSummary.setOnClickListener(v -> {
            if (pillsStatsContainer.getVisibility() == View.GONE) {
                pillsStatsContainer.setVisibility(View.VISIBLE);
            } else {
                pillsStatsContainer.setVisibility(View.GONE);
            }
        });

        btnPrevMonthsAh.setOnClickListener(v -> showMonthlyHistoryDialog("AH"));
        btnPrevMonthsCortisone.setOnClickListener(v -> showMonthlyHistoryDialog("Cortisone"));

        swipeRefreshLayout.setOnRefreshListener(() -> {
            displayPillLogs();
            displayTimeDifference();
            swipeRefreshLayout.setRefreshing(false);
        });

        return view;
    }

    private void showMonthlyHistoryDialog(String type) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] logArray = logs.split("\n");
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);

        List<String> results = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -i);
            int m = cal.get(Calendar.MONTH);
            int y = cal.get(Calendar.YEAR);
            String monthName = new SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.getTime());

            int count = 0;
            for (String log : logArray) {
                if (log.isEmpty() || log.startsWith("EFFECT:")) continue;
                try {
                    String base = log;
                    if (log.contains(" - ")) {
                        int td = log.indexOf(" - ", log.indexOf(" - ") + 1);
                        if (td != -1) base = log.substring(0, td);
                    }
                    Date d = dateFormat.parse(base);
                    if (d != null) {
                        Calendar lCal = Calendar.getInstance();
                        lCal.setTime(d);
                        if (lCal.get(Calendar.MONTH) == m && lCal.get(Calendar.YEAR) == y) {
                            boolean isCortisone = log.contains("Cortisone Taken") || log.contains("Cortisone Pill");
                            if (type.equals("AH") && !isCortisone) count++;
                            else if (type.equals("Cortisone") && isCortisone) count++;
                        }
                    }
                } catch (Exception ignored) {}
            }
            results.add(monthName + ": " + count + (type.equals("AH") ? " AH pill(s)" : " Cortisone dosage(s)"));
        }

        StringBuilder message = new StringBuilder();
        for (String r : results) message.append(r).append("\n");

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("History: Last 6 Months (" + type + ")")
                .setMessage(message.toString().trim())
                .setPositiveButton("X", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(updateReceiver, new IntentFilter("com.example.ayasantihistaminestracker.UPDATE_UI"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireActivity().registerReceiver(updateReceiver, new IntentFilter("com.example.ayasantihistaminestracker.UPDATE_UI"));
        }
        displayPillLogs();
        displayTimeDifference();
        handler.post(updateTimeRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(updateReceiver);
        handler.removeCallbacks(updateTimeRunnable);
    }

    private void resetPillLogs() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String logs = sharedPreferences.getString("pill_logs", "");
        if (!logs.isEmpty()) {
            lastClearedLogs = logs;
            lastClearedPillTs = sharedPreferences.getLong("last_pill_timestamp", -1);
            buttonUndoClearPill.setVisibility(View.VISIBLE);

            String[] logArray = logs.split("\n");
            if (logArray.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < logArray.length; i++) {
                    sb.append(logArray[i]);
                    if (i < logArray.length - 1) sb.append("\n");
                }
                editor.putString("pill_logs", sb.toString());
                if (logArray.length > 1) {
                    try {
                        String nextLine = logArray[1];
                        String base = nextLine;
                        if (nextLine.contains(" - ")) {
                            int td = nextLine.indexOf(" - ", nextLine.indexOf(" - ") + 1);
                            if (td != -1) base = nextLine.substring(0, td);
                        }
                        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
                        Date nextPillDate = fullFormat.parse(base);
                        if (nextPillDate != null) editor.putLong("last_pill_timestamp", nextPillDate.getTime());
                    } catch (Exception e) {
                        editor.remove("last_pill_timestamp");
                    }
                } else {
                    editor.remove("last_pill_timestamp");
                }
            }
        }
        editor.apply();
        displayPillLogs();
        displayTimeDifference();
    }

    private void undoClearPill() {
        if (lastClearedLogs.isEmpty()) return;
        sharedPreferences.edit()
                .putString("pill_logs", lastClearedLogs)
                .putLong("last_pill_timestamp", lastClearedPillTs)
                .apply();
        lastClearedLogs = "";
        buttonUndoClearPill.setVisibility(View.GONE);
        displayPillLogs();
        displayTimeDifference();
        android.widget.Toast.makeText(getContext(), "Cleared log restored!", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showEditLatestPillDialog(int index, String oldLog) {
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String ts = oldLog.contains(" - ") ? oldLog.split(" - ")[0] + " - " + oldLog.split(" - ")[1] : oldLog;
        
        try {
            Date d = fullF.parse(ts);
            Calendar cal = Calendar.getInstance(); if(d != null) cal.setTime(d);

            new android.app.DatePickerDialog(getContext(), (v1, y, m, day) -> {
                cal.set(y, m, day);
                new android.app.TimePickerDialog(getContext(), (v2, hr, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                    
                    SharedPreferences profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    
                    String ahJson = profilePrefs.getString("ah_list", "[]");
                    List<ProfileFragment.RecurringMedication> ahList = gson.fromJson(ahJson, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());
                    
                    String corJson = profilePrefs.getString("cortisone_list", "[]");
                    List<ProfileFragment.RecurringMedication> corList = gson.fromJson(corJson, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());

                    List<String> options = new ArrayList<>();
                    if (ahList != null) for (ProfileFragment.RecurringMedication med : ahList) options.add(med.name);
                    if (corList != null) for (ProfileFragment.RecurringMedication med : corList) options.add("Cortison: " + med.name);
                    
                    // Fallback for generic
                    if (options.isEmpty()) {
                        options.add("Antihistamine");
                        options.add("Cortisone");
                    }

                    String[] arr = options.toArray(new String[0]);
                    new android.app.AlertDialog.Builder(getContext())
                            .setTitle("Edit Pill Type")
                            .setItems(arr, (dialog, which) -> {
                                String newTs = fullF.format(cal.getTime());
                                String selection = arr[which];
                                String finalLog = newTs;
                                if (selection.startsWith("Cortison: ")) {
                                    finalLog += " - Cortisone Taken (" + selection.substring(10) + ")";
                                } else if (selection.equals("Cortisone")) {
                                    finalLog += " - Cortisone Taken";
                                } else if (selection.equals("Antihistamine")) {
                                    finalLog += " - Pill: Antihistamine";
                                } else {
                                    finalLog += " - Pill: " + selection;
                                }
                                updateLogAt(index, finalLog);
                            }).show();

                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            
        } catch (Exception ex) {}
    }

    private void updateLogAt(int index, String newLog) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            arr[index] = newLog;
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(s).append("\n");
            sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
            
            updateLastPillTimestamp();
            displayPillLogs();
            displayTimeDifference();
        }
    }

    private void updateLastPillTimestamp() {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.split("\n");
        long latestTs = -1;
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        for (String s : arr) {
            if (s.isEmpty() || s.startsWith("EFFECT:")) continue;
            try {
                String base = s.contains(" - ") ? s.split(" - ")[0] + " - " + s.split(" - ")[1] : s;
                Date d = f.parse(base);
                if (d != null && !s.contains("Cortisone")) { 
                    latestTs = d.getTime(); 
                    break; 
                }
            } catch (Exception ignored) {}
        }
        if (latestTs != -1) sharedPreferences.edit().putLong("last_pill_timestamp", latestTs).apply();
        else sharedPreferences.edit().remove("last_pill_timestamp").apply();
    }

    private void logPillTaken() {
        SharedPreferences profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = profilePrefs.getString("ah_list", "[]");
        List<ProfileFragment.RecurringMedication> meds = gson.fromJson(json, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());
        
        if (meds == null || meds.isEmpty()) {
            new AlertDialog.Builder(getContext())
                .setTitle("Setup Required")
                .setMessage("Please specify your Current Antihistamine(s) in your Profile first.")
                .setPositiveButton("Go to Profile", (d, w) -> {
                    com.google.android.material.tabs.TabLayout tabs = getActivity().findViewById(R.id.tab_layout);
                    if (tabs != null) tabs.getTabAt(1).select();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        if (meds.size() == 1) {
            String[] options = {meds.get(0).name, "Manual Entry"};
            new AlertDialog.Builder(getContext())
                .setTitle("Select Medication")
                .setItems(options, (d, w) -> {
                    if (w == 0) savePillLog(meds.get(0).name);
                    else showManualMedDialog();
                }).show();
        } else {
            String[] arr = new String[meds.size() + 1];
            for (int i = 0; i < meds.size(); i++) arr[i] = meds.get(i).name;
            arr[meds.size()] = "Manual Entry";
            new AlertDialog.Builder(getContext())
                .setTitle("Select Medication")
                .setItems(arr, (d, w) -> {
                    if (w < meds.size()) savePillLog(arr[w]);
                    else showManualMedDialog();
                }).show();
        }
    }

    private void showManualMedDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Enter medication name...");
        new AlertDialog.Builder(getContext())
            .setTitle("Manual Entry")
            .setView(input)
            .setPositiveButton("Log", (d, w) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) savePillLog(name);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void savePillLog(String medName) {
        Animation pulseAnim = AnimationUtils.loadAnimation(getContext(), R.anim.fire_effect);
        buttonTakePill.startAnimation(pulseAnim);

        Date now = new Date();
        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String logEntry = fullFormat.format(now) + " - Pill: " + medName;

        String logs = sharedPreferences.getString("pill_logs", "");
        if (logs.isEmpty()) logs = logEntry;
        else logs = logEntry + "\n" + logs;

        sharedPreferences.edit()
                .putString("pill_logs", logs)
                .putLong("last_pill_timestamp", now.getTime())
                .apply();

        displayPillLogs();
        displayTimeDifference();
        Toast.makeText(getContext(), medName + " logged!", Toast.LENGTH_SHORT).show();
    }

    private void logCortisone() {
        SharedPreferences profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = profilePrefs.getString("cortisone_list", "[]");
        List<ProfileFragment.RecurringMedication> meds = gson.fromJson(json, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());

        if (meds == null || meds.isEmpty()) {
            String[] options = {"Generic Cortisone", "Manual Entry"};
            new AlertDialog.Builder(getContext())
                .setTitle("Select Cortisone")
                .setItems(options, (d, w) -> {
                    if (w == 0) saveCortisoneLog("Cortisone Taken");
                    else showManualCortisoneDialog();
                }).show();
            return;
        }

        if (meds.size() == 1) {
            String[] options = {meds.get(0).name, "Manual Entry"};
            new AlertDialog.Builder(getContext())
                .setTitle("Select Cortisone")
                .setItems(options, (d, w) -> {
                    if (w == 0) saveCortisoneLog("Cortisone Taken (" + meds.get(0).name + ")");
                    else showManualCortisoneDialog();
                }).show();
        } else {
            String[] arr = new String[meds.size() + 1];
            for (int i = 0; i < meds.size(); i++) arr[i] = meds.get(i).name;
            arr[meds.size()] = "Manual Entry";
            new AlertDialog.Builder(getContext())
                .setTitle("Select Cortisone")
                .setItems(arr, (d, w) -> {
                    if (w < meds.size()) saveCortisoneLog("Cortisone Taken (" + arr[w] + ")");
                    else showManualCortisoneDialog();
                }).show();
        }
    }

    private void showManualCortisoneDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Enter cortisone name...");
        new AlertDialog.Builder(getContext())
            .setTitle("Manual Entry")
            .setView(input)
            .setPositiveButton("Log", (d, w) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) saveCortisoneLog("Cortisone Taken (" + name + ")");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveCortisoneLog(String entryText) {
        Animation pulseAnim = AnimationUtils.loadAnimation(getContext(), R.anim.fire_effect);
        buttonCortisone.startAnimation(pulseAnim);

        Date now = new Date();
        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String logEntry = fullFormat.format(now) + " - " + entryText;

        String logs = sharedPreferences.getString("pill_logs", "");
        if (logs.isEmpty()) logs = logEntry;
        else logs = logEntry + "\n" + logs;

        sharedPreferences.edit()
                .putString("pill_logs", logs)
                .apply();

        displayPillLogs();
        displayTimeDifference();
        android.widget.Toast.makeText(getContext(), entryText + " logged!", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void logFlare() {
        Animation fireAnim = AnimationUtils.loadAnimation(getContext(), R.anim.fire_effect);
        buttonFlare.startAnimation(fireAnim);

        Date now = new Date();
        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String logEntry = fullFormat.format(now);

        String logs = sharedPreferences.getString("flare_logs", "");
        if (logs.isEmpty()) logs = logEntry;
        else logs = logEntry + "\n" + logs;

        sharedPreferences.edit()
                .putString("flare_logs", logs)
                .apply();
        
        android.widget.Toast.makeText(getContext(), "Flare Up logged!", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void displayPillLogs() {
        logContainer.removeAllViews();
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] logArray = logs.split("\n");

        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

        Date now = new Date();
        String todayStr = dateFormat.format(now);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        String yesterdayStr = dateFormat.format(cal.getTime());

        int displayedCount = 0;
        int displayLimit = 4;
        
        for (int i = 0; i < logArray.length && displayedCount < displayLimit; i++) {
            if (logArray[i].isEmpty() || logArray[i].startsWith("EFFECT:") || logArray[i].startsWith("CLEARED:")) continue;
            
            if (logArray[i].contains("Cortisone Taken") || logArray[i].contains("Cortisone Pill")) continue;

            LinearLayout entryLayout = new LinearLayout(getContext());
            entryLayout.setOrientation(LinearLayout.HORIZONTAL);
            entryLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView logTextView = new TextView(getContext());
            try {
                String logEntry = logArray[i];
                String baseLog = logEntry;
                String medLabel = "";
                
                if (logEntry.contains(" - ")) {
                    String[] parts = logEntry.split(" - ");
                    if (parts.length >= 2) {
                        baseLog = parts[0] + " - " + parts[1];
                        for (String p : parts) if (p.startsWith("Pill: ")) medLabel = p.substring(6);
                    }
                }

                Date logDate = fullFormat.parse(baseLog);
                if (logDate != null) {
                    String timePart = timeFormat.format(logDate);
                    String datePart = dateFormat.format(logDate);
                    String relativeDate;
                    if (datePart.equals(todayStr)) relativeDate = "Today";
                    else if (datePart.equals(yesterdayStr)) relativeDate = "Yesterday";
                    else relativeDate = TimeUnit.MILLISECONDS.toDays(now.getTime() - logDate.getTime()) + " days ago";
                    
                    if (displayedCount > 0) {
                        SpannableStringBuilder ssb = new SpannableStringBuilder(timePart + " - " + relativeDate + " (" + datePart + ")");
                        
                        int startIdx = ssb.length();
                        
                        if (i + 1 < logArray.length) {
                            for (int j = i + 1; j < logArray.length; j++) {
                                if (logArray[j].startsWith("EFFECT:") || logArray[j].startsWith("CLEARED:")) continue;
                                if (!logArray[j].contains("Cortisone Taken") && !logArray[j].contains("Cortisone Pill")) {
                                    try {
                                        String prevBase = logArray[j];
                                        if (prevBase.contains(" - ")) {
                                            String[] pParts = prevBase.split(" - ");
                                            if (pParts.length >= 2) prevBase = pParts[0] + " - " + pParts[1];
                                        }
                                        Date prevDate = fullFormat.parse(prevBase);
                                        if (prevDate != null) {
                                            long diff = logDate.getTime() - prevDate.getTime();
                                            long h = TimeUnit.MILLISECONDS.toHours(diff);
                                            String intervalText = " (+" + h + "h)";
                                            ssb.append(intervalText);
                                            
                                            int color = getResources().getColor(R.color.text_secondary_grey);
                                            if (h < 8) color = Color.RED;

                                            ssb.setSpan(new android.text.style.RelativeSizeSpan(0.75f), startIdx, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            ssb.setSpan(new ForegroundColorSpan(color), startIdx, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        }
                                    } catch (Exception ignored) {}
                                    break;
                                }
                            }
                        }
                        logTextView.setText("• ");
                        logTextView.append(ssb);
                    } else {
                        String latestText = "Latest Pill Taken: " + timePart + " - " + relativeDate;
                        SpannableStringBuilder lssb = new SpannableStringBuilder(latestText);
                        logTextView.setText(lssb);

                        TextView editBtn = new TextView(getContext());
                        editBtn.setText(getString(R.string.edit_label));
                        editBtn.setTextColor(Color.BLACK);
                        editBtn.setTextSize(10);
                        editBtn.setPadding(12, 4, 12, 4);
                        editBtn.setGravity(android.view.Gravity.CENTER);
                        editBtn.setBackgroundResource(R.drawable.pill_button_selector);
                        
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, 
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        lp.setMargins(16, 0, 0, 8);
                        editBtn.setLayoutParams(lp);

                        final String entryToEdit = logArray[i];
                        final int entryIndex = i;
                        editBtn.setOnClickListener(v -> showEditLatestPillDialog(entryIndex, entryToEdit));
                        entryLayout.addView(editBtn);
                    }
                    
                    entryLayout.addView(logTextView, 0);
                }
            } catch (Exception ignored) {}

            if (displayedCount == 0) {
                logTextView.setTextColor(getResources().getColor(android.R.color.white));
                logTextView.setTextSize(16);
                logTextView.setTypeface(null, android.graphics.Typeface.BOLD);
                logTextView.setPadding(0, 0, 0, 2);
            } else {
                logTextView.setTextColor(getResources().getColor(R.color.pill_button_blue));
                logTextView.setTextSize(16);
                logTextView.setPadding(0, 0, 0, 0);
            }
            logContainer.addView(entryLayout);
            displayedCount++;
        }
    }

    private void displayTimeDifference() {
        long lastPillTimestamp = sharedPreferences.getLong("last_pill_timestamp", -1);
        String logs = sharedPreferences.getString("pill_logs", "");

        if (lastPillTimestamp != -1) {
            try {
                Date currentDate = new Date();
                long diffInMillis = currentDate.getTime() - lastPillTimestamp;
                long h = TimeUnit.MILLISECONDS.toHours(diffInMillis);
                long m = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) - TimeUnit.HOURS.toMinutes(h);
                long s = TimeUnit.MILLISECONDS.toSeconds(diffInMillis) - TimeUnit.MINUTES.toSeconds(m) - TimeUnit.HOURS.toSeconds(h);

                timeDifferenceView.setText("Last pill was: " + h + "h " + m + "m " + s + "s ago");
                timeDifferenceView.setTextColor(getResources().getColor(android.R.color.white));

                String[] logArray = logs.split("\n");
                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
                int c24 = 0, c48 = 0, c72 = 0, cMonth = 0, cCortisoneMonth = 0;
                Calendar curCal = Calendar.getInstance();
                int curMonth = curCal.get(Calendar.MONTH), curYear = curCal.get(Calendar.YEAR);
                
                for (String log : logArray) {
                    if (log.isEmpty() || log.startsWith("EFFECT:") || log.startsWith("CLEARED:")) continue;
                    try {
                        String base = log;
                        if (log.contains(" - ")) {
                            String[] p = log.split(" - ");
                            if (p.length >= 2) base = p[0] + " - " + p[1];
                        }
                        Date d = dateFormat.parse(base);
                        if (d != null) {
                            long diff = currentDate.getTime() - d.getTime();
                            boolean isCortisone = log.contains("Cortisone Taken") || log.contains("Cortisone Pill");
                            
                            if (!isCortisone) {
                                if (diff <= TimeUnit.HOURS.toMillis(24)) c24++;
                                if (diff <= TimeUnit.HOURS.toMillis(48)) c48++;
                                if (diff <= TimeUnit.HOURS.toMillis(72)) c72++;
                            }
                            
                            Calendar lCal = Calendar.getInstance();
                            lCal.setTime(d);
                            if (lCal.get(Calendar.MONTH) == curMonth && lCal.get(Calendar.YEAR) == curYear) {
                                if (isCortisone) cCortisoneMonth++;
                                else cMonth++;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                
                String mName = new SimpleDateFormat("MMMM", Locale.US).format(currentDate);
                txt24h.setText(c24 + " AH pill(s)");
                txt48h.setText(c48 + " AH pill(s)");
                txt72h.setText(c72 + " AH pill(s)");
                
                pillsSummaryMonth.setText(cMonth + " AH pill(s) in " + mName);
                cortisoneSummaryMonth.setText(cCortisoneMonth + " Cortisone dosage(s) in " + mName);
                
            } catch (Exception e) {
                timeDifferenceView.setText("Error calculating time difference");
            }
        } else {
            timeDifferenceView.setText("No pill taken yet");
            txt24h.setText("-"); txt48h.setText("-"); txt72h.setText("-");
            pillsSummaryMonth.setText(""); cortisoneSummaryMonth.setText("");
        }
    }
}