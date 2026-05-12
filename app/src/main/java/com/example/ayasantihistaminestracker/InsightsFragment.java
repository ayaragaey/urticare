package com.example.ayasantihistaminestracker;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class InsightsFragment extends Fragment implements MainActivity.TitleProvider {

    @Override
    public String getTitle() {
        return getString(R.string.tab_insights);
    }

    private TextView txtProfileSummary;
    private TextView txtAvgAh, txtAvgCortisone, txtAvgPillInterval, txtAvgFlareInterval, txtAngioRate;
    private TextView txtTopPillReasons, txtTopFlareReasons, txtCommonFoods, txtFlarePatterns;
    private Button btnFilterDate, btnGoFilter;

    private SharedPreferences pillPrefs, profilePrefs;
    private final SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
    private final SimpleDateFormat myF = new SimpleDateFormat("MMMM yyyy", Locale.US);

    private long filterStartTime = 0;
    private long filterEndTime = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_insights, container, false);

        txtProfileSummary = view.findViewById(R.id.txt_profile_summary);
        txtAvgAh = view.findViewById(R.id.txt_avg_ah);
        txtAvgCortisone = view.findViewById(R.id.txt_avg_cortisone);
        txtAvgPillInterval = view.findViewById(R.id.txt_avg_pill_interval);
        txtAvgFlareInterval = view.findViewById(R.id.txt_avg_flare_interval);
        txtAngioRate = view.findViewById(R.id.txt_angioedema_rate);
        txtTopPillReasons = view.findViewById(R.id.txt_top_pill_reasons);
        txtTopFlareReasons = view.findViewById(R.id.txt_top_flare_reasons);
        txtCommonFoods = view.findViewById(R.id.txt_common_foods);
        txtFlarePatterns = view.findViewById(R.id.txt_flare_patterns);
        btnFilterDate = view.findViewById(R.id.btn_filter_by_date);
        btnGoFilter = view.findViewById(R.id.btn_go_filter);

        pillPrefs = requireActivity().getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);

        loadProfileSummary();

        btnFilterDate.setOnClickListener(v -> showDateRangeDialog());
        btnGoFilter.setOnClickListener(v -> calculateInsights());

        calculateInsights(); 

        return view;
    }

    private void loadProfileSummary() {
        String name = profilePrefs.getString("name", "N/A");
        String age = profilePrefs.getString("age", "N/A");
        boolean isF = profilePrefs.getBoolean("sex_f", false);
        boolean isM = profilePrefs.getBoolean("sex_m", false);
        String sex = isF ? "F" : (isM ? "M" : "N/A");

        txtProfileSummary.setText(String.format("User: %s | Age: %s | Sex: %s", name, age, sex));
    }

    private void showDateRangeDialog() {
        String[] options = {"All Time", "Select Month", "Custom Range"};
        new AlertDialog.Builder(getContext()).setTitle("Filter Data").setItems(options, (d, w) -> {
            if (w == 0) {
                filterStartTime = 0;
                filterEndTime = 0;
                btnFilterDate.setText("All Time");
            } else if (w == 1) {
                showMonthSelector();
            } else if (w == 2) {
                showCustomRangeSelector();
            }
        }).show();
    }

    private void showMonthSelector() {
        Set<String> months = new HashSet<>();
        String pLogs = pillPrefs.getString("pill_logs", "");
        String fLogs = pillPrefs.getString("flare_logs", "");
        collectMonths(pLogs, months);
        collectMonths(fLogs, months);

        if (months.isEmpty()) {
            Toast.makeText(getContext(), "No data available", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> sortedMonths = new ArrayList<>(months);
        Collections.sort(sortedMonths, (m1, m2) -> {
            try {
                Date d1 = myF.parse(m1);
                Date d2 = myF.parse(m2);
                if (d1 != null && d2 != null) return d2.compareTo(d1);
            } catch (Exception ignored) {}
            return m2.compareTo(m1);
        });
        String[] monthArray = sortedMonths.toArray(new String[0]);

        new AlertDialog.Builder(getContext()).setTitle("Select Month").setItems(monthArray, (d, w) -> {
            String selected = monthArray[w];
            btnFilterDate.setText(selected);
            try {
                Date date = myF.parse(selected);
                if (date != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);
                    filterStartTime = cal.getTimeInMillis();
                    cal.add(Calendar.MONTH, 1);
                    filterEndTime = cal.getTimeInMillis() - 1;
                }
            } catch (Exception ignored) {}
        }).show();
    }

    private void collectMonths(String logs, Set<String> months) {
        if (logs.isEmpty()) return;
        for (String log : logs.split("\n")) {
            if (log.isEmpty() || log.startsWith("EFFECT:") || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
            try {
                String base = log.contains(" - ") ? log.split(" - ")[0] + " - " + log.split(" - ")[1] : log;
                Date d = fullF.parse(base);
                if (d != null) months.add(myF.format(d));
            } catch (Exception ignored) {}
        }
    }

    private void showCustomRangeSelector() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (v1, y1, m1, d1) -> {
            Calendar start = Calendar.getInstance(); start.set(y1, m1, d1, 0, 0, 0);
            new DatePickerDialog(requireContext(), (v2, y2, m2, d2) -> {
                Calendar end = Calendar.getInstance(); end.set(y2, m2, d2, 23, 59, 59);
                filterStartTime = start.getTimeInMillis();
                filterEndTime = end.getTimeInMillis();
                SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                btnFilterDate.setText(String.format("%s - %s", df.format(start.getTime()), df.format(end.getTime())));
            }, y1, m1, d1).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void calculateInsights() {
        String pLogs = pillPrefs.getString("pill_logs", "");
        String fLogs = pillPrefs.getString("flare_logs", "");

        List<PillEntry> filteredPills = getFilteredPills(pLogs);
        List<FlareEntry> filteredFlares = getFilteredFlares(fLogs);

        displayAverages(filteredPills, filteredFlares);
        displayTopReasons(filteredPills, filteredFlares);
        displayCommonFoods(filteredPills, filteredFlares);
        displayFlarePatterns(filteredFlares);
    }

    private void displayAverages(List<PillEntry> pills, List<FlareEntry> flares) {
        int ahCount = 0;
        int cortCount = 0;
        long totalPillGap = 0;
        int pillGapCount = 0;

        for (int i = 0; i < pills.size(); i++) {
            PillEntry p = pills.get(i);
            if (p.isCortisone) cortCount++; 
            if (p.isAntihistamine) ahCount++;

            if (i < pills.size() - 1) {
                totalPillGap += (pills.get(i).timestamp - pills.get(i+1).timestamp); // Assuming sorted desc (latest first)
                pillGapCount++;
            }
        }

        long totalFlareGap = 0;
        int flareGapCount = 0;
        int angioCount = 0;

        for (int i = 0; i < flares.size(); i++) {
            FlareEntry f = flares.get(i);
            if (f.hasAngioedema) angioCount++;
            if (i < flares.size() - 1) {
                totalFlareGap += (flares.get(i).timestamp - flares.get(i+1).timestamp);
                flareGapCount++;
            }
        }

        long days = 1;
        if (filterStartTime > 0 && filterEndTime > 0) {
            days = TimeUnit.MILLISECONDS.toDays(filterEndTime - filterStartTime) + 1;
        } else {
            long min = Long.MAX_VALUE; long max = 0;
            if (!pills.isEmpty() || !flares.isEmpty()) {
                for (PillEntry p : pills) { min = Math.min(min, p.timestamp); max = Math.max(max, p.timestamp); }
                for (FlareEntry f : flares) { min = Math.min(min, f.timestamp); max = Math.max(max, f.timestamp); }
                days = TimeUnit.MILLISECONDS.toDays(max - min) + 1;
            }
        }
        if (days <= 0) days = 1;

        txtAvgAh.setText(String.format(Locale.US, "Avg Antihistamines: %.2f/day | %.1f/month", (double) ahCount / days, (double) ahCount / (days / 30.0 + 0.1)));
        txtAvgCortisone.setText(String.format(Locale.US, "Avg Cortisone: %.2f/day | %.1f/month", (double) cortCount / days, (double) cortCount / (days / 30.0 + 0.1)));
        
        if (pillGapCount > 0) {
            long avgM = TimeUnit.MILLISECONDS.toMinutes(Math.abs(totalPillGap / pillGapCount));
            txtAvgPillInterval.setText(String.format(Locale.US, "Avg time between pills: %dh %dm", avgM / 60, avgM % 60));
        } else txtAvgPillInterval.setText("Avg time between pills: N/A");

        if (flareGapCount > 0) {
            long avgM = TimeUnit.MILLISECONDS.toMinutes(Math.abs(totalFlareGap / flareGapCount));
            txtAvgFlareInterval.setText(String.format(Locale.US, "Avg time between flares: %dh %dm", avgM / 60, avgM % 60));
        } else txtAvgFlareInterval.setText("Avg time between flares: N/A");

        if (!flares.isEmpty()) {
            txtAngioRate.setText(String.format(Locale.US, "Angioedema occurrence rate: %.1f%%", (double) angioCount * 100 / flares.size()));
        } else txtAngioRate.setText("Angioedema occurrence rate: N/A");
    }

    private void displayTopReasons(List<PillEntry> pills, List<FlareEntry> flares) {
        Map<String, Integer> pillReasons = new HashMap<>();
        for (PillEntry p : pills) {
            String main = pillPrefs.getString("pill_main_reason_" + p.rawLog, "");
            if (main.isEmpty()) main = "CU Activity";
            Integer count = pillReasons.get(main);
            pillReasons.put(main, (count == null ? 0 : count) + 1);
        }
        txtTopPillReasons.setText(getTop5Formatted(pillReasons));

        Map<String, Integer> flareReasons = new HashMap<>();
        for (FlareEntry f : flares) {
            String main = pillPrefs.getString("flare_main_reason_" + f.rawLog, "");
            if (main.isEmpty()) main = "Unknown";
            Integer count = flareReasons.get(main);
            flareReasons.put(main, (count == null ? 0 : count) + 1);
        }
        txtTopFlareReasons.setText(getTop5Formatted(flareReasons));
    }

    private void displayCommonFoods(List<PillEntry> pills, List<FlareEntry> flares) {
        Map<String, Integer> foods = new HashMap<>();
        for (PillEntry p : pills) {
            String f = pillPrefs.getString("pill_food_item_" + p.rawLog, "");
            if (!f.isEmpty()) {
                Integer count = foods.get(f);
                foods.put(f, (count == null ? 0 : count) + 1);
            }
        }
        for (FlareEntry fl : flares) {
            String f = pillPrefs.getString("flare_food_item_" + fl.rawLog, "");
            if (!f.isEmpty()) {
                Integer count = foods.get(f);
                foods.put(f, (count == null ? 0 : count) + 1);
            }
        }
        txtCommonFoods.setText(getTop5Formatted(foods));
    }

    private void displayFlarePatterns(List<FlareEntry> flares) {
        int hormonalCount = 0;
        int afterXolairCount = 0;
        int beforeCycleCount = 0;
        long dayInMs = 24 * 60 * 60 * 1000L;
        
        String xLogs = pillPrefs.getString("xolair_logs", "");
        List<Long> xolairTimestamps = new ArrayList<>();
        if (!xLogs.isEmpty()) {
            for (String line : xLogs.split("\n")) {
                try {
                    String[] p = line.split(" - ");
                    Date d = fullF.parse(p[0] + " - " + p[1]);
                    if (d != null) xolairTimestamps.add(d.getTime());
                } catch (Exception ignored) {}
            }
        }

        // Get cycle starts
        List<Long> cycleStarts = new ArrayList<>();
        long currentStart = profilePrefs.getLong("mens_start", 0);
        if (currentStart > 0) cycleStarts.add(currentStart);
        String historyJson = profilePrefs.getString("cycle_history", "[]");
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            List<Map<String, Object>> history = gson.fromJson(historyJson, new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType());
            if (history != null) {
                for (Map<String, Object> entry : history) {
                    Object startVal = entry.get("start");
                    if (startVal instanceof Number) cycleStarts.add(((Number) startVal).longValue());
                }
            }
        } catch (Exception ignored) {}

        for (FlareEntry f : flares) {
            String r = pillPrefs.getString("flare_cause_" + f.rawLog, "");
            if (r.contains("Hormonal trigger")) hormonalCount++;
            
            for (Long xts : xolairTimestamps) {
                long diff = f.timestamp - xts;
                if (diff > 0 && diff <= 3 * dayInMs) {
                    afterXolairCount++;
                    break;
                }
            }

            for (Long cstart : cycleStarts) {
                long diff = cstart - f.timestamp;
                if (diff > 0 && diff <= 4 * dayInMs) {
                    beforeCycleCount++;
                    break;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (hormonalCount > 0) sb.append("• Possible hormonal association (").append(hormonalCount).append(" logs)\n");
        if (beforeCycleCount > 0) sb.append("• Flares identified before cycle start (").append(beforeCycleCount).append(" logs)\n");
        if (afterXolairCount > 0) sb.append("• Flares within 3 days of Xolair/Alternative (").append(afterXolairCount).append(" logs)\n");
        if (sb.length() == 0) sb.append("No clear patterns detected yet.");
        txtFlarePatterns.setText(sb.toString().trim());
    }

    private String getTop5Formatted(Map<String, Integer> counts) {
        if (counts.isEmpty()) return "No data available";
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            sb.append(i + 1).append(". ").append(list.get(i).getKey())
                    .append(" (").append(list.get(i).getValue()).append(")\n");
        }
        return sb.toString().trim();
    }

    private List<PillEntry> getFilteredPills(String logs) {
        List<PillEntry> list = new ArrayList<>();
        if (logs.isEmpty()) return list;
        for (String log : logs.split("\n")) {
            if (log.isEmpty() || log.startsWith("EFFECT:") || log.startsWith("CLEARED:")) continue;
            try {
                String base = log.contains(" - ") ? log.split(" - ")[0] + " - " + log.split(" - ")[1] : log;
                Date d = fullF.parse(base);
                if (d != null && isWithinFilter(d.getTime())) {
                    PillEntry e = new PillEntry();
                    e.timestamp = d.getTime();
                    e.isCortisone = log.contains("Cortisone");
                    e.isAntihistamine = log.contains("Pill: ");
                    e.rawLog = log;
                    list.add(e);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private List<FlareEntry> getFilteredFlares(String logs) {
        List<FlareEntry> list = new ArrayList<>();
        if (logs.isEmpty()) return list;
        for (String log : logs.split("\n")) {
            if (log.isEmpty() || log.startsWith("CONTROLLED:") || log.startsWith("CLEARED:")) continue;
            try {
                Date d = fullF.parse(log);
                if (d != null && isWithinFilter(d.getTime())) {
                    FlareEntry e = new FlareEntry();
                    e.timestamp = d.getTime();
                    e.rawLog = log;
                    String angio = pillPrefs.getString("flare_angio_" + log, "");
                    e.hasAngioedema = angio.equals("Angioedema");
                    list.add(e);
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private boolean isWithinFilter(long ts) {
        if (filterStartTime == 0 && filterEndTime == 0) return true;
        return ts >= filterStartTime && ts <= filterEndTime;
    }

    private static class PillEntry {
        long timestamp;
        boolean isCortisone;
        boolean isAntihistamine;
        String rawLog;
    }

    private static class FlareEntry {
        long timestamp;
        String rawLog;
        boolean hasAngioedema;
    }
}
