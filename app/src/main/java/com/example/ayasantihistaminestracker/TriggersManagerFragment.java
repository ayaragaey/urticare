package com.example.ayasantihistaminestracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TriggersManagerFragment extends Fragment implements MainActivity.TitleProvider {

    private LinearLayout containerCategories, summaryCardsContainer;
    private ChipGroup chipGroupQuick, chipGroupSuggestions;
    private EditText editSearch, editNotes;
    private SharedPreferences triggerPrefs, pillPrefs, profilePrefs, orgPrefs, consumablePrefs;
    private final Gson gson = new Gson();
    
    private final Set<String> selectedTriggers = new HashSet<>();
    private final List<Category> categories = new ArrayList<>();
    private final Map<String, View> categoryViews = new HashMap<>();
    private final Set<String> allSuggestionSourceStrings = new HashSet<>();

    @Override
    public String getTitle() {
        return getString(R.string.organizer_triggers);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_triggers_manager, container, false);
        
        triggerPrefs = requireActivity().getSharedPreferences("TriggerLogs", Context.MODE_PRIVATE);
        pillPrefs = requireActivity().getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);
        orgPrefs = requireActivity().getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
        consumablePrefs = requireActivity().getSharedPreferences("ConsumablesData", Context.MODE_PRIVATE);

        containerCategories = view.findViewById(R.id.container_categories);
        summaryCardsContainer = view.findViewById(R.id.summary_cards_container);
        chipGroupQuick = view.findViewById(R.id.chip_group_quick);
        chipGroupSuggestions = view.findViewById(R.id.chip_group_suggestions);
        editSearch = view.findViewById(R.id.edit_search_triggers);
        editNotes = view.findViewById(R.id.edit_trigger_notes);
        
        Button btnSave = view.findViewById(R.id.btn_save_triggers);
        Button btnClear = view.findViewById(R.id.btn_clear_selection);
        Button btnCancel = view.findViewById(R.id.btn_cancel_triggers);
        
        setupCategories();
        populateQuickAdd();
        populateCategories();
        collectSuggestionSources();
        setupSearch();
        updateSummary();

        btnSave.setOnClickListener(v -> saveTriggers());
        btnClear.setOnClickListener(v -> clearSelection());
        btnCancel.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        return view;
    }

    private void setupCategories() {
        categories.add(new Category("Food", 
            "Spicy food", "Fast food", "Fish & Shellfish", "Dairy", "Aged & Processed Cheeses", "Vegetables & Fruits", "Eggs", "Nuts", "Chocolate", "Processed food", "High histamine foods",
            "Skipped meals", "Overeating", "Eating late", "Fasting", "Other Food Trigger"));

        categories.add(new Category("Beverages", 
            "Caffeine", "Energy drinks", "Soda", "Alcohol", "Coffee", "Tea", "Other Beverage Trigger"));

        categories.add(new Category("Smoke", 
            "Smoke", "Cigarette smoke", "Other Smoke Trigger"));

        categories.add(new Category("Emotional", 
            "Stress", "Anxiety", "Panic", "Anger", "Crying", "Emotional shock", "Overthinking", "Burnout", "Mental exhaustion",
            "Work stress", "School stress", "Family stress", "Social stress", "Other Emotional Trigger"));

        categories.add(new Category("Hormonal", 
            "Period started", "Period ending", "PMS", "Ovulation", "Heavy cycle", "Irregular cycle",
            "Hormonal medication", "Birth control", "Pregnancy", "Postpartum", "Menopause", "Other Hormonal Trigger"));

        categories.add(new Category("Medication", 
            "Missed antihistamine", "Delayed antihistamine", "Xolair Shot (or alt)", "Delayed Xolair (or alt)",
            "Antibiotics", "NSAIDs", "Paracetamol (Painkiller)", "Ibuprofen (Painkiller)", "Invasive Cosmetic (Botox, Filler)", "Steroids", "New medication started", "Medication stopped",
            "Vitamins", "Herbal supplements", "Protein supplements", "Other Medication Trigger"));

        categories.add(new Category("Illness", 
            "Cold", "Flu", "Fever", "Infection", "Inflammation", "Sore throat", "Sinus issues", "Stomach illness", "Viral illness", "Autoimmune Disease", "Other Illness Trigger"));

        categories.add(new Category("Other Triggers", "Add Trigger"));
    }

    private void showVitaminSubOptions(Chip chip) {
        String[] vits = {"Vit A", "Vit B3", "Vit B6", "Vit B12", "Vit C", "Vit D", "Vit E", "Vit K", "Biotin", "Omega 3", "Folic Acid", "Zinc"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Select Vitamin (Optional)")
                .setItems(vits, (dialog, which) -> {
                    String full = "Vitamins > " + vits[which];
                    selectedTriggers.remove(chip.getText().toString());
                    selectedTriggers.add(full);
                    chip.setText(full);
                })
                .setNegativeButton("Only Vitamins", null)
                .show();
    }

    private void populateQuickAdd() {
        String[] quick = {"Stress", "Poor Sleep", "Heat", "Period", "Illness", "Other"};
        for (String q : quick) {
            Chip chip = createChip(q);
            chipGroupQuick.addView(chip);
        }
    }

    private void populateCategories() {
        for (Category cat : categories) {
            View section = createCategorySection(cat);
            containerCategories.addView(section);
            categoryViews.put(cat.name, section);
        }
    }

    private View createCategorySection(Category cat) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(0, 0, 0, 16);

        TextView header = new TextView(getContext());
        String headerText = cat.name + " ▼";
        header.setText(headerText);
        header.setTextColor(getResources().getColor(R.color.pill_button_blue));
        header.setTextSize(16);
        header.setPadding(8, 16, 8, 16);
        header.setClickable(true);
        header.setFocusable(true);
        
        ChipGroup group = new ChipGroup(getContext());
        group.setPadding(8, 8, 8, 8);
        group.setChipSpacingHorizontal(8);
        group.setChipSpacingVertical(8);
        group.setVisibility(View.GONE);

        header.setOnClickListener(v -> {
            boolean visible = group.getVisibility() == View.VISIBLE;
            group.setVisibility(visible ? View.GONE : View.VISIBLE);
            header.setText(cat.name + (visible ? " ▼" : " ▲"));
        });

        for (String item : cat.items) {
            group.addView(createChip(item));
        }

        layout.addView(header);
        layout.addView(group);
        return layout;
    }

    private Chip createChip(String text) {
        Chip chip = new Chip(getContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
        chip.setTextColor(Color.WHITE);
        chip.setRippleColor(ColorStateList.valueOf(Color.parseColor("#4DFFFFFF")));
        
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedTriggers.add(chip.getText().toString());
                chip.setChipBackgroundColor(ColorStateList.valueOf(getResources().getColor(R.color.pill_button_blue)));
                chip.setTextColor(Color.BLACK);
                if (text.startsWith("Other") || text.equals("Add Trigger") || text.equals("Fish & Shellfish") || text.equals("Vegetables & Fruits") || text.equals("Alcohol")) {
                    showOtherInputDialog(text, chip);
                } else if (text.equals("Vitamins")) {
                    showVitaminSubOptions(chip);
                }
            } else {
                selectedTriggers.remove(chip.getText().toString());
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
                chip.setTextColor(Color.WHITE);
            }
        });
        return chip;
    }

    private void showOtherInputDialog(String trigger, Chip chip) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Specify " + trigger);
        final EditText input = new EditText(getContext());
        input.setHint("Type details...");
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!val.isEmpty()) {
                String full = trigger + ": " + val;
                selectedTriggers.remove(chip.getText().toString());
                selectedTriggers.add(full);
                chip.setText(full);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> chip.setChecked(false));
        builder.setCancelable(false);
        builder.show();
    }

    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                filterTriggers(query);
                updateSuggestions(query);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        editSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) chipGroupSuggestions.setVisibility(View.GONE);
            else if (editSearch.getText().length() > 0) chipGroupSuggestions.setVisibility(View.VISIBLE);
        });
    }

    private void collectSuggestionSources() {
        allSuggestionSourceStrings.clear();
        
        // 1. Existing trigger items from categories
        for (Category cat : categories) {
            for (String item : cat.items) {
                allSuggestionSourceStrings.add(item);
            }
        }

        // 2. Trigger History (Custom triggers)
        List<TriggerEntry> logs = getLogs();
        for (TriggerEntry e : logs) {
            for (String t : e.triggers) {
                allSuggestionSourceStrings.add(t.split(":")[0].trim());
            }
        }

        // 3. Consumables
        String consumablesJson = consumablePrefs.getString("consumable_logs", "[]");
        try {
            java.lang.reflect.Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> cLogs = gson.fromJson(consumablesJson, type);
            if (cLogs != null) {
                for (Map<String, Object> c : cLogs) {
                    Object name = c.get("name");
                    if (name instanceof String) allSuggestionSourceStrings.add(((String) name).trim());
                }
            }
        } catch (Exception ignored) {}

        // 4. Medications (Profile)
        String recJson = profilePrefs.getString("recurring_meds", "[]");
        addNamesFromJson(recJson);
        String ahJson = profilePrefs.getString("ah_list", "[]");
        addNamesFromJson(ahJson);
        String corJson = profilePrefs.getString("cortisone_list", "[]");
        addNamesFromJson(corJson);

        // 5. Medications (Organizer)
        String[] medKeys = {"urticaria_list", "other_list", "acute_list", "vitamins_list"};
        for (String key : medKeys) {
            String json = orgPrefs.getString(key, "[]");
            try {
                java.util.List<Map<String, Object>> list = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
                if (list != null) {
                    for (Map<String, Object> m : list) {
                        Object medName = m.get("medName");
                        if (medName instanceof String) allSuggestionSourceStrings.add(((String) medName).trim());
                    }
                }
            } catch (Exception ignored) {}
        }

        // 6. Flare Reasons & Short Gap Reasons
        String flareLogs = pillPrefs.getString("flare_logs", "");
        if (!flareLogs.isEmpty()) {
            for (String line : flareLogs.split("\n")) {
                if (line.isEmpty() || line.startsWith("CONTROLLED:")) continue;
                String cause = pillPrefs.getString("flare_cause_" + line, "");
                if (!cause.isEmpty()) parseAndAddCauses(cause);
            }
        }
        
        String pillLogs = pillPrefs.getString("pill_logs", "");
        if (!pillLogs.isEmpty()) {
            for (String line : pillLogs.split("\n")) {
                if (line.isEmpty() || line.startsWith("EFFECT:")) continue;
                String main = pillPrefs.getString("pill_main_reason_" + line, "");
                if (!main.isEmpty()) allSuggestionSourceStrings.add(main);
                String sub = pillPrefs.getString("pill_sub_reason_" + line, "");
                if (!sub.isEmpty()) allSuggestionSourceStrings.add(sub);
            }
        }
    }

    private void addNamesFromJson(String json) {
        try {
            java.util.List<Map<String, Object>> list = gson.fromJson(json, new TypeToken<java.util.List<Map<String, Object>>>(){}.getType());
            if (list != null) {
                for (Map<String, Object> m : list) {
                    Object name = m.get("name");
                    if (name instanceof String) allSuggestionSourceStrings.add(((String) name).trim());
                }
            }
        } catch (Exception ignored) {}
    }

    private void parseAndAddCauses(String causeString) {
        // Format: "Category: Sub1, Sub2; Category2: Sub3"
        for (String block : causeString.split("; ")) {
            if (block.contains(": ")) {
                String subs = block.split(": ")[1];
                for (String s : subs.split(", ")) {
                    String clean = s.trim();
                    if (clean.contains(" (")) clean = clean.substring(0, clean.indexOf(" (")).trim();
                    allSuggestionSourceStrings.add(clean);
                }
            }
        }
    }

    private void updateSuggestions(String query) {
        chipGroupSuggestions.removeAllViews();
        if (query.isEmpty()) {
            chipGroupSuggestions.setVisibility(View.GONE);
            return;
        }

        List<String> prefixMatches = new ArrayList<>();
        List<String> containsMatches = new ArrayList<>();
        
        for (String s : allSuggestionSourceStrings) {
            String lowerS = s.toLowerCase();
            if (lowerS.startsWith(query)) {
                prefixMatches.add(s);
            } else if (lowerS.contains(query)) {
                containsMatches.add(s);
            }
        }

        // Sort each list by length
        prefixMatches.sort((a, b) -> Integer.compare(a.length(), b.length()));
        containsMatches.sort((a, b) -> Integer.compare(a.length(), b.length()));

        List<String> finalMatches = new ArrayList<>(prefixMatches);
        for (String s : containsMatches) {
            if (!finalMatches.contains(s)) finalMatches.add(s);
        }

        if (finalMatches.isEmpty()) {
            chipGroupSuggestions.setVisibility(View.GONE);
            return;
        }

        chipGroupSuggestions.setVisibility(View.VISIBLE);

        for (int i = 0; i < Math.min(8, finalMatches.size()); i++) {
            String match = finalMatches.get(i);
            Chip chip = new Chip(getContext());
            chip.setText(match);
            chip.setChipBackgroundColorResource(R.color.safety_green_bg);
            chip.setTextColor(Color.WHITE);
            chip.setOnClickListener(v -> {
                editSearch.setText(match);
                editSearch.setSelection(match.length());
                chipGroupSuggestions.setVisibility(View.GONE);
            });
            chipGroupSuggestions.addView(chip);
        }
    }

    private void filterTriggers(String query) {
        for (Category cat : categories) {
            View section = categoryViews.get(cat.name);
            if (section == null) continue;
            
            if (query.isEmpty()) {
                section.setVisibility(View.VISIBLE);
                continue;
            }

            boolean match = false;
            if (cat.name.toLowerCase().contains(query)) match = true;
            else {
                for (String item : cat.items) {
                    if (item.toLowerCase().contains(query)) {
                        match = true;
                        break;
                    }
                }
            }
            section.setVisibility(match ? View.VISIBLE : View.GONE);
        }
    }

    private void clearSelection() {
        selectedTriggers.clear();
        for (int i = 0; i < chipGroupQuick.getChildCount(); i++) {
            Chip c = (Chip) chipGroupQuick.getChildAt(i);
            c.setChecked(false);
            // Reset text if it was an "Other" field
            String originalText = findOriginalText(c);
            if (originalText != null) c.setText(originalText);
        }
        for (View v : categoryViews.values()) {
            LinearLayout layout = (LinearLayout) v;
            ChipGroup group = (ChipGroup) layout.getChildAt(1);
            for (int i = 0; i < group.getChildCount(); i++) {
                Chip c = (Chip) group.getChildAt(i);
                c.setChecked(false);
                String originalText = findOriginalText(c);
                if (originalText != null) c.setText(originalText);
            }
        }
        editNotes.setText("");
    }

    private String findOriginalText(Chip chip) {
        String current = chip.getText().toString();
        if (!current.contains(": ")) return null;
        for (Category cat : categories) {
            for (String item : cat.items) {
                if (current.startsWith(item)) return item;
            }
        }
        if (current.startsWith("Other: ")) return "Other";
        return null;
    }

    private void saveTriggers() {
        if (selectedTriggers.isEmpty()) {
            Toast.makeText(getContext(), "No triggers selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        List<TriggerEntry> logs = getLogs();
        TriggerEntry entry = new TriggerEntry(System.currentTimeMillis(), new ArrayList<>(selectedTriggers), editNotes.getText().toString(), "Manual Entry");
        logs.add(0, entry);
        saveLogs(logs);
        
        collectSuggestionSources(); // Refresh sources with new custom triggers
        
        Toast.makeText(getContext(), "Triggers logged successfully!", Toast.LENGTH_SHORT).show();
        clearSelection();
        updateSummary();
    }

    private void updateSummary() {
        List<TriggerEntry> logs = getLogs();
        summaryCardsContainer.removeAllViews();

        if (logs.isEmpty()) {
            addSummaryCard("No Data", "Start logging triggers to see intelligence.");
            return;
        }

        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> sourceCounts = new HashMap<>();

        for (TriggerEntry e : logs) {
            sourceCounts.merge(e.source, 1, Integer::sum);
            for (String t : e.triggers) {
                String base = t.split(":")[0].trim();
                counts.merge(base, 1, Integer::sum);
            }
        }

        // Card 1: Most Frequent Trigger
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (!sorted.isEmpty()) {
            addSummaryCard("Most Frequent", sorted.get(0).getKey() + " logged " + sorted.get(0).getValue() + " times");
        }

        // Card 2: Most Logged Source
        List<Map.Entry<String, Integer>> sortedSources = new ArrayList<>(sourceCounts.entrySet());
        sortedSources.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (!sortedSources.isEmpty()) {
            addSummaryCard("Source Intelligence", "Most data comes from " + sortedSources.get(0).getKey());
        }

        // Card 3: Hormonal Correlation (Simplified)
        int periodCount = 0;
        for (TriggerEntry e : logs) {
            for (String t : e.triggers) if (t.toLowerCase().contains("period")) periodCount++;
        }
        if (periodCount > 0) {
            addSummaryCard("Hormonal Association", "Possible correlation with cycle detected (" + periodCount + " logs)");
        }

        // Card 4: Recent Increase
        addSummaryCard("Patterns", "The system is learning your associations automatically.");
    }

    private void addSummaryCard(String title, String content) {
        View card = LayoutInflater.from(getContext()).inflate(R.layout.item_trigger_summary_card, summaryCardsContainer, false);
        ((TextView) card.findViewById(R.id.txt_card_title)).setText(title);
        ((TextView) card.findViewById(R.id.txt_card_content)).setText(content);
        summaryCardsContainer.addView(card);
    }

    private void showInsightsDialog() {
        List<TriggerEntry> logs = getLogs();
        if (logs.isEmpty()) {
            Toast.makeText(getContext(), "No data for insights", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder insights = new StringBuilder();
        insights.append("Historical Patterns:\n\n");

        Map<String, Integer> counts = new HashMap<>();
        for (TriggerEntry e : logs) {
            for (String t : e.triggers) {
                String base = t.split(":")[0].trim();
                Integer c = counts.get(base);
                counts.put(base, (c == null ? 0 : c) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        Collections.sort(sorted, (a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            insights.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" times\n");
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_trigger_insights)
            .setMessage(insights.toString())
            .setPositiveButton("Close", null)
            .show();
    }

    private void showHistoryDialog() {
        List<TriggerEntry> logs = getLogs();
        if (logs.isEmpty()) {
            Toast.makeText(getContext(), "No history available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
        for (TriggerEntry e : logs) {
            sb.append(sdf.format(new Date(e.timestamp))).append(":\n");
            for (String t : e.triggers) sb.append(" - ").append(t).append("\n");
            if (e.notes != null && !e.notes.isEmpty()) sb.append(" Notes: ").append(e.notes).append("\n");
            sb.append("\n");
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Trigger History")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private List<TriggerEntry> getLogs() {
        String json = triggerPrefs.getString("logs", "[]");
        return gson.fromJson(json, new TypeToken<List<TriggerEntry>>(){}.getType());
    }

    private void saveLogs(List<TriggerEntry> logs) {
        triggerPrefs.edit().putString("logs", gson.toJson(logs)).apply();
    }

    private static class Category {
        String name;
        List<String> items = new ArrayList<>();
        Category(String n, String... i) { name = n; Collections.addAll(items, i); }
    }

    private static class TriggerEntry {
        long timestamp;
        List<String> triggers;
        String notes;
        String source;
        TriggerEntry(long t, List<String> tr, String n, String s) { timestamp = t; triggers = tr; notes = n; source = s; }
    }
}