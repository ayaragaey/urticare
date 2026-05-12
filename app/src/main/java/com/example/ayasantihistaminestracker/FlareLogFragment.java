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
import androidx.core.content.ContextCompat;
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

public class FlareLogFragment extends Fragment {

    private LinearLayout flareLogContainer;
    private SharedPreferences sharedPreferences;
    private SharedPreferences profilePrefs;
    private final List<String> selectedMonths = new ArrayList<>();
    private String lastClearedLogs = "";
    private TextView btnUndoClear;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_flare_log, container, false);
        flareLogContainer = view.findViewById(R.id.flare_log_container);
        Button buttonLogControlled = view.findViewById(R.id.button_log_controlled);
        Button buttonExport = view.findViewById(R.id.button_export_flare_csv);
        Button buttonClearFlare = view.findViewById(R.id.button_clear_flare);
        Button buttonResetAll = view.findViewById(R.id.button_reset_flare_all);
        btnUndoClear = view.findViewById(R.id.button_undo_clear_flare);
        Button buttonManual = view.findViewById(R.id.button_manual_flare);
        
        sharedPreferences = requireActivity().getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        
        buttonLogControlled.setOnClickListener(v -> logControlled());
        buttonExport.setOnClickListener(v -> showExportModeDialog());
        buttonClearFlare.setOnClickListener(v -> clearLatestFlare());
        buttonResetAll.setOnClickListener(v -> resetAllFlares());
        btnUndoClear.setOnClickListener(v -> undoClear());
        buttonManual.setOnClickListener(v -> showManualEntryDialog());
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        displayFlareLogs();
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
        String logs = sharedPreferences.getString("flare_logs", "");
        if (logs.isEmpty()) { Toast.makeText(getContext(), "No data", Toast.LENGTH_SHORT).show(); return; }
        Set<String> monthsSet = new HashSet<>();
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
        for (String log : logs.split("\n")) {
            if (log.isEmpty() || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
            try { Date d = fullF.parse(log); if (d != null) monthsSet.add(myF.format(d)); } catch (Exception ignored) {}
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
            if (mode.equals("month")) {
                if (isCsv) exportMonthlyToCsv(ms); else exportMonthlyToExcel(ms);
            } else {
                if (isCsv) exportCsv(mode, s, e, ms); else exportExcel(mode, s, e, ms);
            }
        }).show();
    }

    private void logControlled() {
        String logs = sharedPreferences.getString("flare_logs", "");
        if (logs.isEmpty()) {
            Toast.makeText(getContext(), "No flare-up entry found. Log a flare-up first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] logArray = logs.split("\n");
        int latestIdx = -1;
        for (int i = 0; i < logArray.length; i++) {
            if (!logArray[i].startsWith("CONTROLLED:") && !logArray[i].startsWith("CLEARED:")) {
                latestIdx = i; break;
            }
        }
        if (latestIdx == -1) { Toast.makeText(getContext(), "No flare-up entry found.", Toast.LENGTH_SHORT).show(); return; }
        try {
            String base = logArray[latestIdx];
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            Date flareDate = f.parse(base);
            if (flareDate != null) {
                long diff = System.currentTimeMillis() - flareDate.getTime();
                long h = TimeUnit.MILLISECONDS.toHours(diff);
                long m = (TimeUnit.MILLISECONDS.toMinutes(diff)) % 60;
                
                String tag = "CONTROLLED:Flare Up Controlled After " + h + "h" + m + "m";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < logArray.length; i++) {
                    sb.append(logArray[i]).append("\n");
                    if (i == latestIdx) sb.append(tag).append("\n");
                }
                sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
                displayFlareLogs();
            }
        } catch (Exception ex) {}
    }

    private void removeControlled(int index) {
        String logs = sharedPreferences.getString("flare_logs", "");
        String[] arr = logs.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i == index) continue;
            sb.append(arr[i]).append("\n");
        }
        sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
        displayFlareLogs();
    }

    private void displayFlareLogs() {
        flareLogContainer.removeAllViews();
        String logs = sharedPreferences.getString("flare_logs", "");
        if (logs.isEmpty()) return;
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String[] arr = logs.split("\n");

        long mStart = profilePrefs.getLong("mens_start", 0);
        long mEnd = profilePrefs.getLong("mens_end", 0);
        boolean isMensActive = (mStart > 0 && mEnd == 0);

        for (int i = 0; i < arr.length; i++) {
            final int index = i;
            String log = arr[i]; if (log.isEmpty()) continue;
            LinearLayout entryLayout = new LinearLayout(getContext());
            entryLayout.setOrientation(LinearLayout.VERTICAL);
            entryLayout.setPadding(0, 0, 0, 0);
            
            LinearLayout header = new LinearLayout(getContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            if (log.startsWith("CONTROLLED:")) {
                TextView tv = new TextView(getContext());
                tv.setText(log.substring(11));
                tv.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                tv.setTextSize(14); tv.setPadding(32, 0, 0, 0); 
                header.addView(tv);

                TextView rm = new TextView(getContext());
                rm.setText(" [" + getString(R.string.remove_controlled) + "]");
                rm.setTextColor(Color.GRAY); rm.setTextSize(12); rm.setPadding(16, 0, 0, 0);
                rm.setOnClickListener(v -> removeControlled(index));
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
                TextView tv = new TextView(getContext());
                String display = log; Date d = null;
                try { d = f.parse(log);
                    if (d != null) display = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.US).format(d);
                } catch (Exception ignored) {}
                
                SpannableStringBuilder ssb = new SpannableStringBuilder("• " + display);
                int color = getResources().getColor(R.color.flare_pink);
                if (d != null) {
                    for (int j = i + 1; j < arr.length; j++) {
                        if (arr[j].startsWith("CONTROLLED:") || arr[j].startsWith("CLEARED:")) continue;
                        try {
                            Date pd = f.parse(arr[j]);
                            if (pd != null) {
                                long diff = d.getTime() - pd.getTime();
                                long hours = TimeUnit.MILLISECONDS.toHours(diff);
                                if (hours < 8) color = Color.RED;
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

                boolean inMens = isInMensWindow(log) || (isMensActive && d != null && d.getTime() >= mStart);
                boolean isPregnant = profilePrefs.getBoolean("preg_y", false);

                if (inMens) {
                    TextView hint = new TextView(getContext());
                    hint.setText(getString(R.string.msg_gestational_urticaria));
                    hint.setTextColor(Color.parseColor("#E91E63"));
                    hint.setTextSize(12); hint.setTypeface(null, Typeface.ITALIC);
                    hint.setPadding(40, 0, 0, 0);
                    entryLayout.addView(hint);
                } else if (isPregnant) {
                    TextView autoNote = new TextView(getContext());
                    autoNote.setText(getString(R.string.msg_pup_auto_note));
                    autoNote.setTextColor(Color.parseColor("#80DEEA")); // Light cyan
                    autoNote.setTextSize(12); autoNote.setTypeface(null, Typeface.ITALIC);
                    autoNote.setPadding(40, 0, 0, 0);
                    entryLayout.addView(autoNote);
                }

                addAngioedemaUI(entryLayout, log);
                addIntensityAndReasonUI(entryLayout, log);
            }
            flareLogContainer.addView(entryLayout);
        }
    }

    private void showManualEntryDialog() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);

        new DatePickerDialog(getContext(), (v1, y, m, day) -> {
            cal.set(y, m, day);
            new TimePickerDialog(getContext(), (v2, hr, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                saveManualFlare(f.format(cal.getTime()));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveManualFlare(String entry) {
        String logs = sharedPreferences.getString("flare_logs", "");
        String[] arr = logs.isEmpty() ? new String[0] : logs.split("\n");
        List<String> list = new ArrayList<>(Arrays.asList(arr));
        list.add(entry);

        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        Collections.sort(list, (a, b) -> {
            try {
                if (a.startsWith("CONTROLLED:") || a.startsWith("CLEARED:")) return 1;
                if (b.startsWith("CONTROLLED:") || b.startsWith("CLEARED:")) return -1;
                Date da = f.parse(a);
                Date db = f.parse(b);
                if (da == null) return 1;
                if (db == null) return -1;
                return db.compareTo(da);
            } catch (Exception e) { return 0; }
        });

        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s).append("\n");
        sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
        displayFlareLogs();
        Toast.makeText(getContext(), "Manual flare added!", Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(int index, String oldLog) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        try {
            Date d = f.parse(oldLog);
            Calendar cal = Calendar.getInstance(); if(d != null) cal.setTime(d);
            new DatePickerDialog(getContext(), (v1, y, m, day) -> {
                cal.set(y, m, day);
                new TimePickerDialog(getContext(), (v2, hr, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                    updateLogAt(index, f.format(cal.getTime()));
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        } catch (Exception ex) {}
    }

    private void updateLogAt(int index, String newLog) {
        String logs = sharedPreferences.getString("flare_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            arr[index] = newLog;
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(s).append("\n");
            sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
            displayFlareLogs();
        }
    }

    private void clearEntryAt(int index) {
        String logs = sharedPreferences.getString("flare_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            arr[index] = "CLEARED:" + arr[index];
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(s).append("\n");
            sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
            displayFlareLogs();
            Toast.makeText(getContext(), "Entry hidden", Toast.LENGTH_SHORT).show();
        }
    }

    private void undoEntryClear(int index) {
        String logs = sharedPreferences.getString("flare_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            if (arr[index].startsWith("CLEARED:")) {
                arr[index] = arr[index].substring(8);
                StringBuilder sb = new StringBuilder();
                for (String s : arr) sb.append(s).append("\n");
                sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
                displayFlareLogs();
                Toast.makeText(getContext(), "Entry restored", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isInMensWindow(String log) {
        if (!profilePrefs.getBoolean("sex_f", false)) return false;
        long start = profilePrefs.getLong("mens_start", 0);
        long end = profilePrefs.getLong("mens_end", 0);
        try {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            Date d = f.parse(log); if (d == null) return false;
            
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

    private void addAngioedemaUI(LinearLayout parent, String key) {
        LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(40, 5, 0, 5);
        android.widget.CheckBox a = new android.widget.CheckBox(getContext()); a.setText("Angioedema"); a.setTextColor(Color.parseColor("#D9A441")); a.setTextSize(12);
        android.widget.CheckBox na = new android.widget.CheckBox(getContext()); na.setText("No Angioedema"); na.setTextColor(Color.parseColor("#D9A441")); na.setTextSize(12);
        String s = sharedPreferences.getString("flare_angio_" + key, ""); if (s.equals("Angioedema")) a.setChecked(true); else if (s.equals("No Angioedema")) na.setChecked(true);
        a.setOnCheckedChangeListener((bv, is) -> { if (is) { na.setChecked(false); sharedPreferences.edit().putString("flare_angio_" + key, "Angioedema").apply(); } else if (!na.isChecked()) sharedPreferences.edit().remove("flare_angio_" + key).apply(); });
        na.setOnCheckedChangeListener((bv, is) -> { if (is) { a.setChecked(false); sharedPreferences.edit().putString("flare_angio_" + key, "No Angioedema").apply(); } else if (!a.isChecked()) sharedPreferences.edit().remove("flare_angio_" + key).apply(); });
        l.addView(a); l.addView(na); parent.addView(l);
    }

    private void addIntensityAndReasonUI(LinearLayout parent, String key) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(40, 0, 0, 0);
        
        String savedMain = sharedPreferences.getString("flare_main_reason_" + key, "");
        String savedSub = sharedPreferences.getString("flare_sub_reason_" + key, "");
        String savedNote = sharedPreferences.getString("flare_other_note_" + key, "");
        
        android.widget.CheckBox m = new android.widget.CheckBox(getContext()); m.setText(getString(R.string.label_mild)); m.setTextColor(Color.WHITE); m.setTextSize(12);
        android.widget.CheckBox s = new android.widget.CheckBox(getContext()); s.setText(getString(R.string.label_severe)); s.setTextColor(Color.WHITE); s.setTextSize(12);
        String savedInt = sharedPreferences.getString("flare_intensity_" + key, ""); if (savedInt.equals("Mild")) m.setChecked(true); else if (savedInt.equals("Severe")) s.setChecked(true);
        m.setOnCheckedChangeListener((bv, is) -> { if (is) { s.setChecked(false); sharedPreferences.edit().putString("flare_intensity_" + key, "Mild").apply(); } else if (!s.isChecked()) sharedPreferences.edit().remove("flare_intensity_" + key).apply(); });
        s.setOnCheckedChangeListener((bv, is) -> { if (is) { m.setChecked(false); sharedPreferences.edit().putString("flare_intensity_" + key, "Severe").apply(); } else if (!m.isChecked()) sharedPreferences.edit().remove("flare_intensity_" + key).apply(); });
        
        TextView reasonToggle = new TextView(getContext()); 

        String display = "";
        if (!savedMain.isEmpty()) {
            display = savedMain;
            if (!savedSub.isEmpty()) display += ": " + savedSub;
            String savedVit = sharedPreferences.getString("flare_vitamin_" + key, "");
            if (savedSub.equals("Medication-related trigger") && !savedVit.isEmpty()) display += " (" + savedVit + ")";
            if (savedMain.equals("Other") && !savedNote.isEmpty()) display += " (" + savedNote + ")";
        }

        reasonToggle.setText(display.isEmpty() ? "Possible Reason" : display); 
        reasonToggle.setTextColor(Color.parseColor("#6B2E2E")); reasonToggle.setTextSize(13); reasonToggle.setPadding(16, 5, 0, 5); 
        reasonToggle.setPaintFlags(reasonToggle.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        
        row.addView(m); row.addView(s); row.addView(reasonToggle); parent.addView(row);

        reasonToggle.setOnClickListener(v -> showReasonSelectionDialog(key, reasonToggle));
    }

    private void showReasonSelectionDialog(String key, TextView toggleView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_reason_selection, null);
        builder.setView(dialogView);

        LinearLayout categoriesContainer = dialogView.findViewById(R.id.categories_container);
        Button btnOk = dialogView.findViewById(R.id.btn_reason_ok);
        Button btnClear = dialogView.findViewById(R.id.btn_reason_clear);

        // Define reasons (Unified)
        Map<String, List<String>> reasonsMap = new LinkedHashMap<>();
        reasonsMap.put("Symptoms Returning", Arrays.asList("Symptoms returned early", "Severe itching", "Hives worsening", "Swelling / angioedema symptoms", "Symptoms affecting sleep", "Symptoms after waking up", "Sudden flare-up", "Previous dose felt ineffective"));
        reasonsMap.put("Trigger Exposure", Arrays.asList("Stress or emotional distress", "Heat exposure", "Cold exposure", "Sweating / exercise", "Suspected food trigger", "Environmental trigger", "Pressure/friction on skin", "Medication-related trigger", "Infection / illness", "Unknown trigger exposure"));
        reasonsMap.put("Prevention / Precaution", Arrays.asList("Preventive dose before expected trigger", "Important event / work / school", "Traveling or disrupted schedule", "Poor sleep", "Anxiety about flare-up", "High activity day"));
        reasonsMap.put("Medication Schedule Issue", Arrays.asList("Missed previous dose", "Delayed previous dose", "Adjusting medication schedule", "Trying to regain symptom control", "Doctor-directed extra dose"));
        reasonsMap.put("Unsure", Arrays.asList("Not sure why", "Multiple possible causes"));
        reasonsMap.put("Other", new ArrayList<>());

        Map<String, List<String>> vitaminOptions = new HashMap<>();
        vitaminOptions.put("Vitamins", Arrays.asList("Vit A", "Vit B3", "Vit B6", "Vit B12", "Vit C", "Vit D", "Vit E", "Vit K", "Biotin", "Omega 3", "Folic Acid", "Zinc"));

        final String[] selectedMain = {sharedPreferences.getString("flare_main_reason_" + key, "")};
        final String[] selectedSub = {sharedPreferences.getString("flare_sub_reason_" + key, "")};
        final String[] selectedVitamin = {sharedPreferences.getString("flare_vitamin_" + key, "")};
        final String[] otherNote = {sharedPreferences.getString("flare_other_note_" + key, "")};

        refreshReasonUI(categoriesContainer, reasonsMap, vitaminOptions, selectedMain, selectedSub, selectedVitamin, otherNote);

        AlertDialog dialog = builder.create();
        btnClear.setOnClickListener(v -> {
            sharedPreferences.edit()
                .remove("flare_main_reason_" + key)
                .remove("flare_sub_reason_" + key)
                .remove("flare_vitamin_" + key)
                .remove("flare_other_note_" + key)
                .apply();
            toggleView.setText("Possible Reason");
            dialog.dismiss();
        });

        btnOk.setOnClickListener(v -> {
            if (selectedMain[0].isEmpty()) {
                Toast.makeText(getContext(), "Please select a main reason", Toast.LENGTH_SHORT).show();
                return;
            }
            
            sharedPreferences.edit()
                .putString("flare_main_reason_" + key, selectedMain[0])
                .putString("flare_sub_reason_" + key, selectedSub[0])
                .putString("flare_vitamin_" + key, selectedVitamin[0])
                .putString("flare_other_note_" + key, otherNote[0])
                .apply();
            
            String display = selectedMain[0];
            if (!selectedSub[0].isEmpty()) {
                display += ": " + selectedSub[0];
                if (selectedSub[0].equals("Medication-related trigger") && !selectedVitamin[0].isEmpty()) {
                    display += " (" + selectedVitamin[0] + ")";
                }
            }
            if (selectedMain[0].equals("Other") && !otherNote[0].isEmpty()) display += " (" + otherNote[0] + ")";
            
            toggleView.setText(display);
            
            // Intelligence: Log to triggers
            autoLogToTriggers(display, "Flare Ups Log");
            
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

                            List<String> vits = vitaminMap.get("Vitamins");
                            if (vits != null) {
                                for (String vit : vits) {
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
                            }
                            subContainer.addView(vitContainer);
                        }
                    }
                }
                
                if (main.equals("Other")) {
                    EditText etOther = new EditText(getContext());
                    etOther.setHint("Custom note...");
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
            // The reasonString now passed is display string like "Trigger Exposure: Stress" or "Symptoms Returning"
            if (reasonString.startsWith("Trigger Exposure: ")) {
                triggerList.add(reasonString.substring(18));
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
        String logs = sharedPreferences.getString("flare_logs", ""); if (logs.isEmpty()) return;
        try {
            File f = new File(requireContext().getCacheDir(), "flares_export.csv");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write("Date,Time,Warning (Too close),Angioedema,Intensity,Main Reason,Sub Reason,Optional Notes,Controlled,Elapsed,Cycle Hint,Pregnancy Hint\n".getBytes());
            SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            SimpleDateFormat dateF = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat timeF = new SimpleDateFormat("HH:mm:ss", Locale.US);
            SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
            String[] arr = logs.split("\n");
            for (int i = 0; i < arr.length; i++) {
                String log = arr[i]; if (log.isEmpty() || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
                Date d = fullF.parse(log); if (d == null) continue;
                boolean inc = false; if (mode.equals("all")) inc = true;
                else if (mode.equals("date") && d.getTime() >= s && d.getTime() <= e) inc = true;
                else if (mode.equals("month") && ms.contains(myF.format(d))) inc = true;
                if (!inc) continue;
                String warning = "No"; for (int j = i + 1; j < arr.length; j++) { if (arr[j].startsWith("CONTROLLED:") || arr[j].startsWith("CLEARED:")) continue; try { Date pd = fullF.parse(arr[j]); if (pd != null && d.getTime() - pd.getTime() < TimeUnit.HOURS.toMillis(8)) warning = "Yes"; } catch (Exception ex) {} break; }
                String angio = sharedPreferences.getString("flare_angio_" + log, "");
                String intensity = sharedPreferences.getString("flare_intensity_" + log, "");
                
                String mainReason = sharedPreferences.getString("flare_main_reason_" + log, "").replace(",", ";");
                String subReason = sharedPreferences.getString("flare_sub_reason_" + log, "").replace(",", ";");
                String vitamin = sharedPreferences.getString("flare_vitamin_" + log, "");
                if (!vitamin.isEmpty()) subReason += " (" + vitamin + ")";
                String notes = sharedPreferences.getString("flare_other_note_" + log, "").replace(",", ";");
                
                String controlled = "No", elapsed = ""; if (i > 0 && arr[i-1].startsWith("CONTROLLED:")) { controlled = "Yes"; String line = arr[i-1]; elapsed = line.substring(line.indexOf("After ") + 6); }
                String cHint = isInMensWindow(log) ? getString(R.string.msg_progesterone_sensitivity) : "";
                String pHint = profilePrefs.getBoolean("preg_y", false) ? getString(R.string.msg_gestational_urticaria) : "";
                fos.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", dateF.format(d), timeF.format(d), warning, angio, intensity, mainReason, subReason, notes, controlled, elapsed, cHint, pHint).getBytes());
            }
            fos.close(); share(f, "text/csv");
        } catch (Exception ex) {}
    }

    private void exportExcel(String mode, long s, long e, List<String> ms) {
        String logs = sharedPreferences.getString("flare_logs", ""); if (logs.isEmpty()) return;
        try {
            XSSFWorkbook wb = new XSSFWorkbook(); Sheet sh = wb.createSheet("Flares");
            String[] headers = {"Date", "Time", "Warning (Too close)", "Angioedema", "Intensity", "Main Reason", "Sub Reason", "Optional Notes", "Controlled", "Elapsed", "Cycle Hint", "Pregnancy Hint"};
            Row hr = sh.createRow(0); for (int i=0; i<headers.length; i++) hr.createCell(i).setCellValue(headers[i]);
            SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            SimpleDateFormat dateF = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat timeF = new SimpleDateFormat("HH:mm:ss", Locale.US);
            SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
            String[] arr = logs.split("\n"); int rowIdx = 1;
            for (int i = 0; i < arr.length; i++) {
                String log = arr[i]; if (log.isEmpty() || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
                Date d = fullF.parse(log); if (d == null) continue;
                boolean inc = false; if (mode.equals("all")) inc = true;
                else if (mode.equals("date") && d.getTime() >= s && d.getTime() <= e) inc = true;
                else if (mode.equals("month") && ms.contains(myF.format(d))) inc = true;
                if (!inc) continue;
                String warning = "No"; for (int j = i + 1; j < arr.length; j++) { if (arr[j].startsWith("CONTROLLED:") || arr[j].startsWith("CLEARED:")) continue; try { Date pd = fullF.parse(arr[j]); if (pd != null && d.getTime() - pd.getTime() < TimeUnit.HOURS.toMillis(8)) warning = "Yes"; } catch (Exception ex) {} break; }
                String angio = sharedPreferences.getString("flare_angio_" + log, "");
                String intensity = sharedPreferences.getString("flare_intensity_" + log, "");
                
                String mainReason = sharedPreferences.getString("flare_main_reason_" + log, "");
                String subReason = sharedPreferences.getString("flare_sub_reason_" + log, "");
                String vitamin = sharedPreferences.getString("flare_vitamin_" + log, "");
                if (!vitamin.isEmpty()) subReason += " (" + vitamin + ")";
                String notes = sharedPreferences.getString("flare_other_note_" + log, "");

                String controlled = "No", elapsed = ""; if (i > 0 && arr[i-1].startsWith("CONTROLLED:")) { controlled = "Yes"; String line = arr[i-1]; elapsed = line.substring(line.indexOf("After ") + 6); }
                String cHint = isInMensWindow(log) ? getString(R.string.msg_progesterone_sensitivity) : "";
                String pHint = profilePrefs.getBoolean("preg_y", false) ? getString(R.string.msg_gestational_urticaria) : "";
                Row r = sh.createRow(rowIdx++); r.createCell(0).setCellValue(dateF.format(d)); r.createCell(1).setCellValue(timeF.format(d)); r.createCell(2).setCellValue(warning); r.createCell(3).setCellValue(angio); r.createCell(4).setCellValue(intensity); r.createCell(5).setCellValue(mainReason); r.createCell(6).setCellValue(subReason); r.createCell(7).setCellValue(notes); r.createCell(8).setCellValue(controlled); r.createCell(9).setCellValue(elapsed); r.createCell(10).setCellValue(cHint); r.createCell(11).setCellValue(pHint);
            }
            File f = new File(requireContext().getCacheDir(), "flares_export.xlsx");
            FileOutputStream fos = new FileOutputStream(f); wb.write(fos); fos.close(); wb.close();
            share(f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception ex) {}
    }

    private void exportMonthlyToCsv(List<String> months) {
        try {
            File file = new File(requireContext().getCacheDir(), "monthly_flare_ups_totals.csv");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write("Month Year,Total Flares,Angioedema,No Angioedema,Mild,Severe,Controlled\n".getBytes());
            String logs = sharedPreferences.getString("flare_logs", "");
            SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
            for (String month : months) {
                int total=0, angio=0, noAngio=0, mild=0, severe=0, controlled=0;
                String[] arr = logs.split("\n");
                for (int i=0; i<arr.length; i++) {
                    String log = arr[i]; if (log.isEmpty() || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
                    try { Date d = fullF.parse(log);
                        if (d != null && myF.format(d).equals(month)) {
                            total++; String a = sharedPreferences.getString("flare_angio_" + log, "");
                            if (a.equals("Angioedema")) angio++; else if (a.equals("No Angioedema")) noAngio++;
                            String intensity = sharedPreferences.getString("flare_intensity_" + log, "");
                            if (intensity.equals("Mild")) mild++; else if (intensity.equals("Severe")) severe++;
                            if (i > 0 && arr[i-1].startsWith("CONTROLLED:")) controlled++;
                        }
                    } catch (Exception ignored) {}
                }
                fos.write((month + "," + total + "," + angio + "," + noAngio + "," + mild + "," + severe + "," + controlled + "\n").getBytes());
            }
            fos.close(); share(file, "text/csv");
        } catch (Exception e) {}
    }

    private void exportMonthlyToExcel(List<String> months) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(); Sheet sheet = workbook.createSheet("Monthly Flares");
            String[] h = {"Month Year", "Total Flares", "Angioedema", "No Angioedema", "Mild", "Severe", "Controlled"};
            Row hr = sheet.createRow(0); for (int i=0; i<h.length; i++) hr.createCell(i).setCellValue(h[i]);
            String logs = sharedPreferences.getString("flare_logs", "");
            SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
            SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);
            int rIdx = 1;
            for (String month : months) {
                int total=0, angio=0, noAngio=0, mild=0, severe=0, controlled=0;
                String[] arr = logs.split("\n");
                for (int i=0; i<arr.length; i++) {
                    String log = arr[i]; if (log.isEmpty() || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
                    try { Date d = fullF.parse(log);
                        if (d != null && myF.format(d).equals(month)) {
                            total++; String a = sharedPreferences.getString("flare_angio_" + log, "");
                            if (a.equals("Angioedema")) angio++; else if (a.equals("No Angioedema")) noAngio++;
                            String intensity = sharedPreferences.getString("flare_intensity_" + log, "");
                            if (intensity.equals("Mild")) mild++; else if (intensity.equals("Severe")) severe++;
                            if (i > 0 && arr[i-1].startsWith("CONTROLLED:")) controlled++;
                        }
                    } catch (Exception ignored) {}
                }
                Row row = sheet.createRow(rIdx++); row.createCell(0).setCellValue(month);
                row.createCell(1).setCellValue(total); row.createCell(2).setCellValue(angio);
                row.createCell(3).setCellValue(noAngio); row.createCell(4).setCellValue(mild);
                row.createCell(5).setCellValue(severe); row.createCell(6).setCellValue(controlled);
            }
            File file = new File(requireContext().getCacheDir(), "monthly_flare_ups_totals.xlsx");
            FileOutputStream fos = new FileOutputStream(file); workbook.write(fos); fos.close(); workbook.close();
            share(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {}
    }

    private void share(File f, String type) {
        Uri path = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_SEND); i.setType(type); i.putExtra(Intent.EXTRA_STREAM, path);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Export Logs"));
    }

    private void clearLatestFlare() {
        String logs = sharedPreferences.getString("flare_logs", ""); if (logs.isEmpty()) return;
        lastClearedLogs = logs;
        btnUndoClear.setVisibility(View.VISIBLE);

        String[] arr = logs.split("\n"); StringBuilder sb = new StringBuilder();
        for (int i = 1; i < arr.length; i++) sb.append(arr[i]).append("\n");
        sharedPreferences.edit().putString("flare_logs", sb.toString().trim()).apply();
        displayFlareLogs();
        Toast.makeText(getContext(), "Entry cleared", Toast.LENGTH_SHORT).show();
    }

    private void undoClear() {
        if (lastClearedLogs.isEmpty()) return;
        sharedPreferences.edit().putString("flare_logs", lastClearedLogs).apply();
        lastClearedLogs = "";
        btnUndoClear.setVisibility(View.GONE);
        displayFlareLogs();
        Toast.makeText(getContext(), "Cleared flares restored!", Toast.LENGTH_SHORT).show();
    }

    private void resetAllFlares() {
        String logs = sharedPreferences.getString("flare_logs", ""); if (logs.isEmpty()) return;
        lastClearedLogs = logs;
        btnUndoClear.setVisibility(View.VISIBLE);

        sharedPreferences.edit().remove("flare_logs").apply();
        displayFlareLogs();
        Toast.makeText(getContext(), "All flare logs cleared", Toast.LENGTH_SHORT).show();
    }
}
