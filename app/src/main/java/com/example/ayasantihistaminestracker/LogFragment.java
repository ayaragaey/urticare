package com.example.ayasantihistaminestracker;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LogFragment extends Fragment {

    private LinearLayout fullLogContainer;
    private SharedPreferences sharedPreferences;
    private SharedPreferences profilePrefs;
    private final List<String> selectedMonths = new ArrayList<>();
    private String lastClearedLogs = "";
    private long lastClearedPillTs = -1;
    private TextView btnUndoClear;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);
        fullLogContainer = view.findViewById(R.id.full_log_container);
        Button buttonLogEffect = view.findViewById(R.id.button_log_effect);
        Button buttonExport = view.findViewById(R.id.button_export_csv);
        Button buttonClearLog = view.findViewById(R.id.button_clear_log);
        Button buttonResetAll = view.findViewById(R.id.button_reset_all);
        btnUndoClear = view.findViewById(R.id.button_undo_clear);
        Button buttonManual = view.findViewById(R.id.button_manual_pill);
        
        sharedPreferences = requireActivity().getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        
        buttonLogEffect.setOnClickListener(v -> logEffect());
        buttonExport.setOnClickListener(v -> showExportModeDialog());
        buttonClearLog.setOnClickListener(v -> clearLatestLog());
        buttonResetAll.setOnClickListener(v -> resetAllLogs());
        btnUndoClear.setOnClickListener(v -> undoClear());
        buttonManual.setOnClickListener(v -> showManualEntryDialog());
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        displayFullLogs();
    }

    private void showExportModeDialog() {
        String[] modes = {getString(R.string.export_all), getString(R.string.export_date), getString(R.string.export_monthly)};
        new AlertDialog.Builder(getContext()).setTitle("Select Export Type").setItems(modes, (d, w) -> {
            switch(w) {
                case 0: showFormatDialog("all", 0, 0, null); break;
                case 1: showDateRangeDialog(); break;
                case 2: showMonthSelectionDialog(); break;
            }
        }).show();
    }

    private void showDateRangeDialog() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog startDialog = new DatePickerDialog(requireContext(), (v1, y1, m1, d1) -> {
            Calendar start = Calendar.getInstance(); start.set(y1, m1, d1, 0, 0, 0);
            DatePickerDialog endDialog = new DatePickerDialog(requireContext(), (v2, y2, m2, d2) -> {
                Calendar end = Calendar.getInstance(); end.set(y2, m2, d2, 23, 59, 59);
                showFormatDialog("date", start.getTimeInMillis(), end.getTimeInMillis(), null);
            }, y1, m1, d1);
            endDialog.setTitle("End Date"); endDialog.show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        startDialog.setTitle("Start Date"); startDialog.show();
    }

    private void showMonthSelectionDialog() {
        String logs = sharedPreferences.getString("pill_logs", "");
        if (logs.isEmpty()) { Toast.makeText(getContext(), "No data", Toast.LENGTH_SHORT).show(); return; }
        Set<String> monthsSet = new HashSet<>();
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
        for (String log : logs.split("\n")) {
            if (log.isEmpty() || log.startsWith("EFFECT:")) continue;
            try {
                String base = log.contains(" - ") ? log.split(" - ")[0] + " - " + log.split(" - ")[1] : log;
                Date date = fullF.parse(base); if (date != null) monthsSet.add(myF.format(date));
            } catch (Exception ignored) {}
        }
        List<String> sorted = new ArrayList<>(monthsSet);
        Collections.sort(sorted, Collections.reverseOrder());
        String[] arr = sorted.toArray(new String[0]);
        boolean[] checked = new boolean[arr.length];
        selectedMonths.clear();
        new AlertDialog.Builder(getContext()).setTitle("Select Months")
                .setMultiChoiceItems(arr, checked, (d, w, is) -> {
                    if (is) selectedMonths.add(arr[w]); else selectedMonths.remove(arr[w]);
                })
                .setPositiveButton("Next", (d, w) -> {
                    if (!selectedMonths.isEmpty()) showFormatDialog("month", 0, 0, new ArrayList<>(selectedMonths));
                }).show();
    }

    private void showFormatDialog(String mode, long s, long e, List<String> ms) {
        String[] formats = {getString(R.string.export_as_csv), getString(R.string.export_as_excel)};
        new AlertDialog.Builder(getContext()).setTitle(R.string.export_choice_title).setItems(formats, (d, w) -> {
            boolean isCsv = (w == 0);
            if (isCsv) exportCsv(mode, s, e, ms); else exportExcel(mode, s, e, ms);
        }).show();
    }

    private void logEffect() {
        String logs = sharedPreferences.getString("pill_logs", "");
        if (logs.isEmpty()) {
            Toast.makeText(getContext(), "No pill entry found. Take a pill first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] logArray = logs.split("\n");
        int latestIdx = -1;
        for (int i = 0; i < logArray.length; i++) {
            if (!logArray[i].startsWith("EFFECT:") && !logArray[i].contains("Cortison Taken")) {
                latestIdx = i; break;
            }
        }
        if (latestIdx == -1) { Toast.makeText(getContext(), "No pill entry found.", Toast.LENGTH_SHORT).show(); return; }
        try {
            String base = logArray[latestIdx];
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            String ts = base.contains(" - ") ? base.split(" - ")[0] + " - " + base.split(" - ")[1] : base;
            Date date = f.parse(ts);
            if (date != null) {
                long diff = System.currentTimeMillis() - date.getTime();
                long h = TimeUnit.MILLISECONDS.toHours(diff);
                long m = (TimeUnit.MILLISECONDS.toMinutes(diff)) % 60;
                String tag = "EFFECT:Effect is active after " + h + " hour" + (h!=1?"s ":" ") + m + " minute" + (m!=1?"s":"") + ".";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < logArray.length; i++) {
                    sb.append(logArray[i]).append("\n");
                    if (i == latestIdx) sb.append(tag).append("\n");
                }
                sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
                displayFullLogs();
            }
        } catch (Exception ex) {}
    }

    private void removeEffect(int index) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i == index) continue;
            sb.append(arr[i]).append("\n");
        }
        sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
        displayFullLogs();
    }

    private void displayFullLogs() {
        fullLogContainer.removeAllViews();
        String logs = sharedPreferences.getString("pill_logs", "");
        if (logs.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No entries found.");
            empty.setTextColor(Color.WHITE);
            fullLogContainer.addView(empty);
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String[] arr = logs.split("\n");
        for (int i = 0; i < arr.length; i++) {
            final int index = i;
            String log = arr[i]; if (log.isEmpty()) continue;

            LinearLayout entryLayout = new LinearLayout(getContext());
            entryLayout.setOrientation(LinearLayout.VERTICAL);
            entryLayout.setPadding(0, 0, 0, 0);
            
            LinearLayout header = new LinearLayout(getContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            if (log.startsWith("EFFECT:")) {
                TextView tv = new TextView(getContext());
                tv.setText(log.substring(7));
                tv.setTextColor(getResources().getColor(R.color.text_elapsed_orange));
                tv.setTextSize(14); tv.setPadding(32, 0, 0, 0); 
                header.addView(tv);

                TextView rm = new TextView(getContext());
                rm.setText(" [" + getString(R.string.remove_controlled) + "]");
                rm.setTextColor(Color.GRAY); rm.setTextSize(12); rm.setPadding(16, 0, 0, 0);
                rm.setOnClickListener(v -> removeEffect(index));
                header.addView(rm);
                entryLayout.addView(header);
            } else if (log.startsWith("CLEARED:")) {
                TextView tv = new TextView(getContext());
                tv.setText("Entry Cleared");
                tv.setTextColor(Color.GRAY);
                tv.setTextSize(14); tv.setTypeface(null, Typeface.ITALIC);
                header.addView(tv);

                TextView undoBtn = new TextView(getContext());
                undoBtn.setText(" [Undo Clear]");
                undoBtn.setTextColor(getResources().getColor(R.color.pill_button_blue));
                undoBtn.setTextSize(12); undoBtn.setPadding(16, 0, 0, 0);
                undoBtn.setOnClickListener(v -> undoEntryClear(index));
                header.addView(undoBtn);
                entryLayout.addView(header);
            } else {
                String display = log; Date d = null;
                String medLabel = "";
                try {
                    if (log.contains(" - ")) {
                        String[] parts = log.split(" - ");
                        if (parts.length >= 2) {
                            d = f.parse(parts[0] + " - " + parts[1]);
                            if (d != null) display = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.US).format(d);
                            for (String p : parts) if (p.startsWith("Pill: ")) medLabel = p.substring(6);
                            if (log.contains("Cortison Taken") || log.contains("Cortisone Taken")) display += " - Cortisone Taken";
                        }
                    }
                } catch (Exception ignored) {}

                TextView tv = new TextView(getContext());
                SpannableStringBuilder ssb = new SpannableStringBuilder("• " + display);
                
                boolean isCortisone = log.contains("Cortisone") || log.contains("Cortison");
                int color = isCortisone ? Color.parseColor("#FFA500") : getResources().getColor(R.color.pill_button_blue);

                boolean warning = false;
                if (d != null) {
                    for (int j = i + 1; j < arr.length; j++) {
                        if (arr[j].startsWith("EFFECT:") || arr[j].startsWith("CLEARED:")) continue;
                        try {
                            String pts = arr[j].contains(" - ") ? arr[j].split(" - ")[0] + " - " + arr[j].split(" - ")[1] : arr[j];
                            Date pd = f.parse(pts);
                            if (pd != null) {
                                long diff = d.getTime() - pd.getTime();
                                long hours = TimeUnit.MILLISECONDS.toHours(diff);
                                if (hours < 8) { 
                                    if (!isCortisone) color = Color.RED; 
                                    warning = true; 
                                }
                                String diffText = " (+" + hours + "h)";
                                ssb.append(diffText);
                                ssb.setSpan(new RelativeSizeSpan(0.75f), ssb.length()-diffText.length(), ssb.length(), 0);
                                ssb.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.text_secondary_grey)), ssb.length()-diffText.length(), ssb.length(), 0);
                            }
                        } catch (Exception ignored) {}
                        break;
                    }
                }
                tv.setText(ssb); tv.setTextColor(color); tv.setTextSize(16);
                tv.setIncludeFontPadding(false);
                header.addView(tv);

                TextView editBtn = new TextView(getContext());
                editBtn.setText(" [" + getString(R.string.edit_label) + "]");
                editBtn.setTextColor(Color.GRAY); editBtn.setTextSize(12); editBtn.setPadding(16, 0, 0, 0);
                editBtn.setOnClickListener(v -> showEditDialog(index, log));
                header.addView(editBtn);

                TextView clearBtn = new TextView(getContext());
                clearBtn.setText(" [Clear]");
                clearBtn.setTextColor(Color.parseColor("#88FF5252"));
                clearBtn.setTextSize(12); clearBtn.setPadding(16, 0, 0, 0);
                clearBtn.setOnClickListener(v -> clearEntryAt(index));
                header.addView(clearBtn);

                entryLayout.addView(header);

                if (!medLabel.isEmpty()) {
                    TextView medTv = new TextView(getContext());
                    medTv.setText(medLabel);
                    medTv.setTextColor(Color.LTGRAY);
                    medTv.setTextSize(12);
                    medTv.setTypeface(null, Typeface.BOLD);
                    medTv.setPadding(40, 0, 0, 0);
                    medTv.setIncludeFontPadding(false);
                    entryLayout.addView(medTv);
                } else if (isCortisone) {
                    TextView medTv = new TextView(getContext());
                    medTv.setText("Cortisone Pill");
                    medTv.setTextColor(Color.LTGRAY);
                    medTv.setTextSize(12);
                    medTv.setTypeface(null, Typeface.BOLD);
                    medTv.setPadding(40, 0, 0, 0);
                    medTv.setIncludeFontPadding(false);
                    entryLayout.addView(medTv);
                }

                if (warning && isInMensWindow(log)) {
                    TextView hint = new TextView(getContext());
                    hint.setText(getString(R.string.msg_progesterone_sensitivity));
                    hint.setTextColor(Color.parseColor("#FFC107"));
                    hint.setTextSize(12); hint.setTypeface(null, Typeface.ITALIC);
                    hint.setPadding(40, 0, 0, 0);
                    entryLayout.addView(hint);
                }
                addReasonUI(entryLayout, log, warning);
            }
            fullLogContainer.addView(entryLayout);
        }
    }

    private void showManualEntryDialog() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);

        new DatePickerDialog(getContext(), (v1, y, m, day) -> {
            cal.set(y, m, day);
            new TimePickerDialog(getContext(), (v2, hr, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                
                String ahJson = profilePrefs.getString("ah_list", "[]");
                com.google.gson.Gson gson = new com.google.gson.Gson();
                List<ProfileFragment.RecurringMedication> ahList = gson.fromJson(ahJson, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());
                
                String corJson = profilePrefs.getString("cortisone_list", "[]");
                List<ProfileFragment.RecurringMedication> corList = gson.fromJson(corJson, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());

                List<String> options = new ArrayList<>();
                if (ahList != null) for (ProfileFragment.RecurringMedication med : ahList) options.add(med.name);
                if (corList != null) for (ProfileFragment.RecurringMedication med : corList) options.add("Cortison: " + med.name);
                
                if (options.isEmpty()) {
                    options.add("Antihistamine");
                    options.add("Cortisone");
                }

                String[] arr = options.toArray(new String[0]);
                new AlertDialog.Builder(getContext())
                    .setTitle("Manual Pill Entry")
                    .setItems(arr, (dialog, which) -> {
                        String ts = fullF.format(cal.getTime());
                        String selection = arr[which];
                        String logEntry = ts;
                        if (selection.startsWith("Cortison: ")) {
                            logEntry += " - Cortisone Taken (" + selection.substring(10) + ")";
                        } else if (selection.equals("Cortisone")) {
                            logEntry += " - Cortisone Taken";
                        } else if (selection.equals("Antihistamine")) {
                            logEntry += " - Pill: Antihistamine";
                        } else {
                            logEntry += " - Pill: " + selection;
                        }
                        
                        saveManualLog(logEntry);
                    }).show();

            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveManualLog(String entry) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.isEmpty() ? new String[0] : logs.split("\n");
        List<String> list = new ArrayList<>(Arrays.asList(arr));
        list.add(entry);
        
        // Sort chronologically (descending)
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        Collections.sort(list, (a, b) -> {
            try {
                if (a.startsWith("EFFECT:") || a.startsWith("CLEARED:")) return 1;
                if (b.startsWith("EFFECT:") || b.startsWith("CLEARED:")) return -1;
                String tsa = a.contains(" - ") ? a.split(" - ")[0] + " - " + a.split(" - ")[1] : a;
                String tsb = b.contains(" - ") ? b.split(" - ")[0] + " - " + b.split(" - ")[1] : b;
                Date da = f.parse(tsa);
                Date db = f.parse(tsb);
                if (da == null) return 1;
                if (db == null) return -1;
                return db.compareTo(da);
            } catch (Exception e) { return 0; }
        });

        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s).append("\n");
        sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
        
        updateLastPillTimestamp();
        displayFullLogs();
        Toast.makeText(getContext(), "Manual entry added!", Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(int index, String oldLog) {
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String baseTs = oldLog;
        if (oldLog.contains(" - ")) {
            String[] parts = oldLog.split(" - ");
            if (parts.length >= 2) baseTs = parts[0] + " - " + parts[1];
        }

        try {
            Date d = fullF.parse(baseTs);
            Calendar cal = Calendar.getInstance(); if(d != null) cal.setTime(d);

            new DatePickerDialog(getContext(), (v1, y, m, day) -> {
                cal.set(y, m, day);
                new TimePickerDialog(getContext(), (v2, hr, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                    
                    String newTs = fullF.format(cal.getTime());
                    
                    if (oldLog.contains("Cortison Taken") || oldLog.contains("Cortisone")) {
                        updateLogAt(index, newTs + " - Cortisone Taken");
                    } else {
                        String currentMed = "";
                        if (oldLog.contains("Pill: ")) {
                            int start = oldLog.indexOf("Pill: ") + 6;
                            currentMed = oldLog.substring(start).trim();
                        }
                        
                        SharedPreferences profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String json = profilePrefs.getString("ah_list", "[]");
                        List<ProfileFragment.RecurringMedication> ahList = gson.fromJson(json, new com.google.gson.reflect.TypeToken<List<ProfileFragment.RecurringMedication>>(){}.getType());

                        List<String> options = new ArrayList<>();
                        if (ahList != null) {
                            for (ProfileFragment.RecurringMedication med : ahList) {
                                if (!options.contains(med.name)) options.add(med.name);
                            }
                        }
                        if (!currentMed.isEmpty() && !options.contains(currentMed)) options.add(currentMed);
                        
                        if (options.isEmpty()) {
                            updateLogAt(index, newTs);
                        } else {
                            String[] arr = options.toArray(new String[0]);
                            new AlertDialog.Builder(getContext())
                                .setTitle("Select Medication")
                                .setItems(arr, (dialog, which) -> {
                                    updateLogAt(index, newTs + " - Pill: " + arr[which]);
                                }).show();
                        }
                    }
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            
        } catch (Exception ex) {}
    }

    private void clearEntryAt(int index) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            arr[index] = "CLEARED:" + arr[index];
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(s).append("\n");
            sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
            updateLastPillTimestamp();
            displayFullLogs();
            Toast.makeText(getContext(), "Entry hidden (statistics updated)", Toast.LENGTH_SHORT).show();
        }
    }

    private void undoEntryClear(int index) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            if (arr[index].startsWith("CLEARED:")) {
                arr[index] = arr[index].substring(8);
                StringBuilder sb = new StringBuilder();
                for (String s : arr) sb.append(s).append("\n");
                sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
                updateLastPillTimestamp();
                displayFullLogs();
                Toast.makeText(getContext(), "Entry restored", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateLogAt(int index, String newLog) {
        String logs = sharedPreferences.getString("pill_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            arr[index] = newLog;
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(s).append("\n");
            sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
            
            // Sync last pill timestamp if latest was edited
            updateLastPillTimestamp();
            displayFullLogs();
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
                if (d != null) { latestTs = d.getTime(); break; }
            } catch (Exception ignored) {}
        }
        if (latestTs != -1) sharedPreferences.edit().putLong("last_pill_timestamp", latestTs).apply();
        else sharedPreferences.edit().remove("last_pill_timestamp").apply();
    }

    private boolean isInMensWindow(String log) {
        if (!profilePrefs.getBoolean("sex_f", false)) return false;
        long start = profilePrefs.getLong("mens_start", 0);
        long end = profilePrefs.getLong("mens_end", 0);
        try {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            String ts = log.contains(" - ") ? log.split(" - ")[0] + " - " + log.split(" - ")[1] : log;
            Date d = f.parse(ts); if (d == null) return false;
            
            if (start > 0) {
                if (end == 0) { if (d.getTime() >= start) return true; }
                else { if (d.getTime() >= start && d.getTime() <= end) return true; }
            }
            
            String json = profilePrefs.getString("cycle_history", "[]");
            List<CycleEntry> history = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<List<CycleEntry>>(){}.getType());
            if (history != null) {
                for (CycleEntry entry : history) {
                    if (d.getTime() >= entry.start && d.getTime() <= entry.end) return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private static class CycleEntry {
        long start, end;
    }

    private void addReasonUI(LinearLayout parent, String key, boolean warning) {
        if (!warning) return;

        String main = sharedPreferences.getString("pill_main_reason_" + key, "");
        String sub = sharedPreferences.getString("pill_sub_reason_" + key, "");
        String note = sharedPreferences.getString("pill_other_note_" + key, "");

        String display = "";
        if (!main.isEmpty()) {
            display = main;
            if (!sub.isEmpty()) display += ": " + sub;
            if (main.equals("Other") && !note.isEmpty()) display += " (" + note + ")";
        }

        // Only create and add the UI if there is actually a reason to show, 
        // OR if it's a short gap (warning) and we want to show the "Possible Reason" placeholder.
        // But the user wants NO space, so let's be strict.
        
        LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(40, 0, 0, 0);
        TextView t = new TextView(getContext()); 

        t.setText(display.isEmpty() ? getString(R.string.possible_reason_short_gap) : display);
        t.setTextColor(Color.parseColor("#6B2E2E")); t.setTextSize(13); t.setPadding(0, 0, 0, 0);
        t.setIncludeFontPadding(false);
        t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        l.addView(t); parent.addView(l);

        t.setOnClickListener(v -> showReasonSelectionDialog(key, t));
    }

    private void showReasonSelectionDialog(String key, TextView toggleView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_reason_selection, null);
        builder.setView(dialogView);

        LinearLayout categoriesContainer = dialogView.findViewById(R.id.categories_container);
        Button btnOk = dialogView.findViewById(R.id.btn_reason_ok);
        Button btnClear = dialogView.findViewById(R.id.btn_reason_clear);

        // Define reasons
        Map<String, List<String>> reasonsMap = new LinkedHashMap<>();
        reasonsMap.put("Flare Up", new ArrayList<>());
        reasonsMap.put("CU Activity", new ArrayList<>());
        reasonsMap.put("Precautionary Measure", new ArrayList<>());
        reasonsMap.put("Symptoms Returning", Arrays.asList("Symptoms returned early", "Severe itching", "Hives worsening", "Swelling / angioedema symptoms", "Symptoms affecting sleep", "Symptoms after waking up", "Sudden flare-up", "Previous dose felt ineffective"));
        reasonsMap.put("Trigger Exposure", Arrays.asList("Stress or emotional distress", "Heat exposure", "Cold exposure", "Sweating / exercise", "Suspected food trigger", "Environmental trigger", "Pressure/friction on skin", "Medication-related trigger", "Infection / illness", "Unknown trigger exposure"));
        reasonsMap.put("Prevention / Precaution", Arrays.asList("Preventive dose before expected trigger", "Important event / work / school", "Traveling or disrupted schedule", "Poor sleep", "Anxiety about flare-up", "High activity day"));
        reasonsMap.put("Medication Schedule Issue", Arrays.asList("Missed previous dose", "Delayed previous dose", "Adjusting medication schedule", "Trying to regain symptom control", "Doctor-directed extra dose"));
        reasonsMap.put("Unsure", Arrays.asList("Not sure why", "Multiple possible causes"));
        reasonsMap.put("Manual Entry / Other", new ArrayList<>());

        Map<String, List<String>> vitaminOptions = new HashMap<>();
        vitaminOptions.put("Vitamins", Arrays.asList("Vit A", "Vit B3", "Vit B6", "Vit B12", "Vit C", "Vit D", "Vit E", "Vit K", "Biotin", "Omega 3", "Folic Acid", "Zinc"));

        final String[] selectedMain = {sharedPreferences.getString("pill_main_reason_" + key, "")};
        final String[] selectedSub = {sharedPreferences.getString("pill_sub_reason_" + key, "")};
        final String[] selectedVitamin = {sharedPreferences.getString("pill_vitamin_" + key, "")};
        final String[] otherNote = {sharedPreferences.getString("pill_other_note_" + key, "")};

        refreshReasonUI(categoriesContainer, reasonsMap, vitaminOptions, selectedMain, selectedSub, selectedVitamin, otherNote);

        AlertDialog dialog = builder.create();
        btnClear.setOnClickListener(v -> {
            sharedPreferences.edit()
                .remove("pill_main_reason_" + key)
                .remove("pill_sub_reason_" + key)
                .remove("pill_vitamin_" + key)
                .remove("pill_other_note_" + key)
                .apply();
            toggleView.setText(getString(R.string.possible_reason_short_gap));
            dialog.dismiss();
        });

        btnOk.setOnClickListener(v -> {
            if (selectedMain[0].isEmpty()) {
                Toast.makeText(getContext(), "Please select a main reason", Toast.LENGTH_SHORT).show();
                return;
            }
            
            sharedPreferences.edit()
                .putString("pill_main_reason_" + key, selectedMain[0])
                .putString("pill_sub_reason_" + key, selectedSub[0])
                .putString("pill_vitamin_" + key, selectedVitamin[0])
                .putString("pill_other_note_" + key, otherNote[0])
                .apply();
            
            String display = selectedMain[0];
            if (!selectedSub[0].isEmpty()) {
                display += ": " + selectedSub[0];
                if (selectedSub[0].equals("Medication-related trigger") && !selectedVitamin[0].isEmpty()) {
                    display += " (" + selectedVitamin[0] + ")";
                }
            }
            if (selectedMain[0].equals("Manual Entry / Other") && !otherNote[0].isEmpty()) display += " (" + otherNote[0] + ")";
            
            toggleView.setText(display);
            
            if (selectedMain[0].equals("Trigger Exposure") && !selectedSub[0].isEmpty()) {
                String trigger = selectedSub[0];
                if (trigger.equals("Medication-related trigger") && !selectedVitamin[0].isEmpty()) trigger = "Vitamins > " + selectedVitamin[0];
                autoLogToTriggers("Trigger Exposure: " + trigger, "Pills Log (Short Gap)");
            }
            
            dialog.dismiss();
        });
        dialog.show();
    }

    private void refreshReasonUI(LinearLayout container, Map<String, List<String>> map, Map<String, List<String>> vitaminMap, String[] selectedMain, String[] selectedSub, String[] selectedVitamin, String[] otherNote) {
        container.removeAllViews();
        for (String main : map.keySet()) {
            LinearLayout mainLayout = new LinearLayout(getContext());
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(0, 10, 0, 10);
            
            android.widget.RadioButton rbMain = new android.widget.RadioButton(getContext());
            rbMain.setText(main);
            rbMain.setTextColor(Color.WHITE);
            rbMain.setChecked(main.equals(selectedMain[0]));
            
            LinearLayout subContainer = new LinearLayout(getContext());
            subContainer.setOrientation(LinearLayout.VERTICAL);
            subContainer.setPadding(60, 0, 0, 0);
            subContainer.setVisibility(main.equals(selectedMain[0]) ? View.VISIBLE : View.GONE);

            rbMain.setOnClickListener(v -> {
                selectedMain[0] = main;
                selectedSub[0] = "";
                selectedVitamin[0] = "";
                refreshReasonUI(container, map, vitaminMap, selectedMain, selectedSub, selectedVitamin, otherNote);
            });

            if (main.equals(selectedMain[0])) {
                List<String> subs = map.get(main);
                if (subs != null && !subs.isEmpty()) {
                    TextView subTitle = new TextView(getContext());
                    subTitle.setText("Sub Reason (Optional)");
                    subTitle.setTextColor(Color.GRAY);
                    subTitle.setTextSize(12);
                    subTitle.setPadding(0, 10, 0, 10);
                    subContainer.addView(subTitle);

                    for (String sub : subs) {
                        android.widget.RadioButton rbSub = new android.widget.RadioButton(getContext());
                        rbSub.setText(sub);
                        rbSub.setTextColor(Color.LTGRAY);
                        rbSub.setTextSize(14);
                        rbSub.setChecked(sub.equals(selectedSub[0]));
                        rbSub.setOnClickListener(v1 -> {
                            selectedSub[0] = sub;
                            selectedVitamin[0] = "";
                            refreshReasonUI(container, map, vitaminMap, selectedMain, selectedSub, selectedVitamin, otherNote);
                        });
                        subContainer.addView(rbSub);

                        if (sub.equals("Medication-related trigger") && sub.equals(selectedSub[0])) {
                            LinearLayout vitContainer = new LinearLayout(getContext());
                            vitContainer.setOrientation(LinearLayout.VERTICAL);
                            vitContainer.setPadding(60, 0, 0, 0);
                            
                            TextView vitTitle = new TextView(getContext());
                            vitTitle.setText("Select Vitamin (Optional)");
                            vitTitle.setTextColor(Color.parseColor("#D9A441"));
                            vitTitle.setTextSize(11);
                            vitContainer.addView(vitTitle);

                            for (String vit : vitaminMap.get("Vitamins")) {
                                android.widget.RadioButton rbVit = new android.widget.RadioButton(getContext());
                                rbVit.setText(vit);
                                rbVit.setTextColor(Color.LTGRAY);
                                rbVit.setTextSize(12);
                                rbVit.setChecked(vit.equals(selectedVitamin[0]));
                                rbVit.setOnClickListener(v2 -> {
                                    selectedVitamin[0] = vit;
                                });
                                vitContainer.addView(rbVit);
                            }
                            subContainer.addView(vitContainer);
                        }
                    }
                }
                
                if (main.equals("Manual Entry / Other")) {
                    EditText etOther = new EditText(getContext());
                    etOther.setHint("Type custom reason here...");
                    etOther.setTextColor(Color.WHITE);
                    etOther.setHintTextColor(Color.GRAY);
                    etOther.setText(otherNote[0]);
                    etOther.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { otherNote[0] = s.toString(); }
                        @Override public void afterTextChanged(Editable s) {}
                    });
                    subContainer.addView(etOther);
                }
            }

            mainLayout.addView(rbMain);
            mainLayout.addView(subContainer);
            container.addView(mainLayout);
        }
    }

    private void autoLogToTriggers(String reasonString, String source) {
        try {
            SharedPreferences triggerPrefs = requireActivity().getSharedPreferences("TriggerLogs", Context.MODE_PRIVATE);
            String json = triggerPrefs.getString("logs", "[]");
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> logs = gson.fromJson(json, type);
            if (logs == null) logs = new ArrayList<>();

            List<String> triggerList = new ArrayList<>();
            if (reasonString.startsWith("Trigger Exposure: ")) {
                triggerList.add(reasonString.substring(18));
            } else {
                // Fallback for old/other format
                for (String block : reasonString.split("; ")) {
                    if (block.contains(": ")) {
                        String[] parts = block.split(": ");
                        for (String sub : parts[1].split(", ")) {
                            triggerList.add(sub.trim());
                        }
                    } else if (!reasonString.contains(":")) {
                        triggerList.add(reasonString.trim());
                    }
                }
            }

            if (!triggerList.isEmpty()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("timestamp", System.currentTimeMillis());
                entry.put("triggers", triggerList);
                entry.put("notes", "Auto-logged from " + source);
                entry.put("source", source);
                logs.add(0, entry);
                triggerPrefs.edit().putString("logs", gson.toJson(logs)).apply();
            }
        } catch (Exception ignored) {}
    }


    private void areaStyle(LinearLayout l) { l.setOrientation(LinearLayout.VERTICAL); l.setVisibility(View.GONE); l.setPadding(40, 10, 20, 10); l.setBackgroundColor(Color.parseColor("#1A1A1A")); }

    private void exportCsv(String mode, long s, long e, List<String> ms) {
        String logs = sharedPreferences.getString("pill_logs", ""); if (logs.isEmpty()) return;
        try {
            File f = new File(requireContext().getCacheDir(), "pills_export.csv");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write("Type,Date,Time,Effect,Elapsed,Short Gap Main Reason,Short Gap Sub Reason,Optional Notes,Warning (<8h),Female Cycle Hint\n".getBytes());
            SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            SimpleDateFormat dateF = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat timeF = new SimpleDateFormat("HH:mm:ss", Locale.US);
            SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);

            String[] arr = logs.split("\n");
            for (int i = 0; i < arr.length; i++) {
                String log = arr[i]; if (log.isEmpty() || log.startsWith("EFFECT:")) continue;
                String ts = log.contains(" - ") ? log.split(" - ")[0] + " - " + log.split(" - ")[1] : log;
                Date d = fullF.parse(ts); if (d == null) continue;
                
                boolean include = false;
                if (mode.equals("all")) include = true;
                else if (mode.equals("date") && d.getTime() >= s && d.getTime() <= e) include = true;
                else if (mode.equals("month") && ms.contains(myF.format(d))) include = true;
                if (!include) continue;

                String type = log.contains("Cortison Taken") ? "Cortison" : "Antihistamine";
                String effect = "", elapsed = "";
                if (i > 0 && arr[i-1].startsWith("EFFECT:")) {
                    effect = "Active"; String line = arr[i-1];
                    elapsed = line.substring(line.indexOf("after ") + 6).replace(".", "");
                }
                String warning = "No";
                for (int j = i + 1; j < arr.length; j++) {
                    if (arr[j].startsWith("EFFECT:")) continue;
                    try {
                        String pts = arr[j].contains(" - ") ? arr[j].split(" - ")[0] + " - " + arr[j].split(" - ")[1] : arr[j];
                        Date pd = fullF.parse(pts);
                        if (pd != null && d.getTime() - pd.getTime() < TimeUnit.HOURS.toMillis(8)) warning = "Yes";
                    } catch (Exception ex) {}
                    break;
                }
                String hint = (warning.equals("Yes") && isInMensWindow(log)) ? getString(R.string.msg_progesterone_sensitivity) : "";
                
                String mainReason = sharedPreferences.getString("pill_main_reason_" + log, "").replace(",", ";");
                String subReason = sharedPreferences.getString("pill_sub_reason_" + log, "").replace(",", ";");
                String notes = sharedPreferences.getString("pill_other_note_" + log, "").replace(",", ";");
                
                fos.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", type, dateF.format(d), timeF.format(d), effect, elapsed, mainReason, subReason, notes, warning, hint).getBytes());
            }
            fos.close(); share(f, "text/csv");
        } catch (Exception ex) {}
    }

    private void exportExcel(String mode, long s, long e, List<String> ms) {
        String logs = sharedPreferences.getString("pill_logs", ""); if (logs.isEmpty()) return;
        try {
            XSSFWorkbook wb = new XSSFWorkbook(); Sheet sh = wb.createSheet("Pills");
            String[] headers = {"Type", "Date", "Time", "Effect", "Elapsed", "Short Gap Main Reason", "Short Gap Sub Reason", "Optional Notes", "Warning (<8h)", "Female Cycle Hint"};
            Row hr = sh.createRow(0); for (int i=0; i<headers.length; i++) hr.createCell(i).setCellValue(headers[i]);
            SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            SimpleDateFormat dateF = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat timeF = new SimpleDateFormat("HH:mm:ss", Locale.US);
            SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
            String[] arr = logs.split("\n"); int rowIdx = 1;
            for (int i = 0; i < arr.length; i++) {
                String log = arr[i]; if (log.isEmpty() || log.startsWith("EFFECT:")) continue;
                String ts = log.contains(" - ") ? log.split(" - ")[0] + " - " + log.split(" - ")[1] : log;
                Date d = fullF.parse(ts); if (d == null) continue;
                boolean inc = false; if (mode.equals("all")) inc = true;
                else if (mode.equals("date") && d.getTime() >= s && d.getTime() <= e) inc = true;
                else if (mode.equals("month") && ms.contains(myF.format(d))) inc = true;
                if (!inc) continue;
                String type = log.contains("Cortison Taken") ? "Cortison" : "Antihistamine";
                String effect = "", elapsed = "";
                if (i > 0 && arr[i-1].startsWith("EFFECT:")) { effect = "Active"; String line = arr[i-1]; elapsed = line.substring(line.indexOf("after ") + 6).replace(".", ""); }
                String warning = "No";
                for (int j = i + 1; j < arr.length; j++) {
                    if (arr[j].startsWith("EFFECT:")) continue;
                    try { 
                        String pts = arr[j].contains(" - ") ? arr[j].split(" - ")[0] + " - " + arr[j].split(" - ")[1] : arr[j];
                        Date pd = fullF.parse(pts); if (pd != null && d.getTime() - pd.getTime() < TimeUnit.HOURS.toMillis(8)) warning = "Yes"; } catch (Exception ex) {}
                    break;
                }
                String hint = (warning.equals("Yes") && isInMensWindow(log)) ? getString(R.string.msg_progesterone_sensitivity) : "";
                
                String mainReason = sharedPreferences.getString("pill_main_reason_" + log, "");
                String subReason = sharedPreferences.getString("pill_sub_reason_" + log, "");
                String notes = sharedPreferences.getString("pill_other_note_" + log, "");

                Row r = sh.createRow(rowIdx++); r.createCell(0).setCellValue(type); r.createCell(1).setCellValue(dateF.format(d));
                r.createCell(2).setCellValue(timeF.format(d)); r.createCell(3).setCellValue(effect); r.createCell(4).setCellValue(elapsed);
                r.createCell(5).setCellValue(mainReason); r.createCell(6).setCellValue(subReason); r.createCell(7).setCellValue(notes);
                r.createCell(8).setCellValue(warning); r.createCell(9).setCellValue(hint);
            }
            File f = new File(requireContext().getCacheDir(), "pills_export.xlsx");
            FileOutputStream fos = new FileOutputStream(f); wb.write(fos); fos.close(); wb.close();
            share(f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception ex) {}
    }

    private void share(File f, String type) {
        Uri path = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_SEND); i.setType(type); i.putExtra(Intent.EXTRA_STREAM, path);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Export Logs"));
    }

    private void clearLatestLog() {
        String logs = sharedPreferences.getString("pill_logs", ""); if (logs.isEmpty()) return;
        lastClearedLogs = logs;
        lastClearedPillTs = sharedPreferences.getLong("last_pill_timestamp", -1);
        btnUndoClear.setVisibility(View.VISIBLE);

        String[] arr = logs.split("\n"); StringBuilder sb = new StringBuilder();
        for (int i = 1; i < arr.length; i++) sb.append(arr[i]).append("\n");
        sharedPreferences.edit().putString("pill_logs", sb.toString().trim()).apply();
        updateLastPillTimestamp();
        displayFullLogs();
        Toast.makeText(getContext(), "Entry cleared", Toast.LENGTH_SHORT).show();
    }

    private void undoClear() {
        if (lastClearedLogs.isEmpty()) return;
        sharedPreferences.edit()
            .putString("pill_logs", lastClearedLogs)
            .putLong("last_pill_timestamp", lastClearedPillTs)
            .apply();
        lastClearedLogs = "";
        btnUndoClear.setVisibility(View.GONE);
        displayFullLogs();
        Toast.makeText(getContext(), "Cleared logs restored!", Toast.LENGTH_SHORT).show();
    }

    private void resetAllLogs() {
        String logs = sharedPreferences.getString("pill_logs", ""); if (logs.isEmpty()) return;
        lastClearedLogs = logs;
        lastClearedPillTs = sharedPreferences.getLong("last_pill_timestamp", -1);
        btnUndoClear.setVisibility(View.VISIBLE);

        sharedPreferences.edit().remove("pill_logs").remove("last_pill_timestamp").apply();
        displayFullLogs();
        Toast.makeText(getContext(), "All pill logs cleared", Toast.LENGTH_SHORT).show();
    }
}