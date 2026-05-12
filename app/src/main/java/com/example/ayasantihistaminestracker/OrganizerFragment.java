package com.example.ayasantihistaminestracker;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class OrganizerFragment extends Fragment implements MainActivity.TitleProvider, MainActivity.BackHandler {

    private SharedPreferences orgPrefs, profilePrefs;
    private TextView summaryName, summaryGender, summaryAge;
    private TextView navUrticaria, navChronic, navAcute;
    private LinearLayout layoutUrticaria, layoutChronic, layoutAcute;
    private LinearLayout containerUrticaria, containerOther, containerAcute;
    private Button btnUndoUrticaria, btnUndoOther, btnUndoAcute;
    
    private Button btnNavMeds, btnNavApps, btnNavWater, btnNavVitamins, btnNavTriggersManager, btnNavStressNavigator, btnNavConsumablesTracker;
    private LinearLayout layoutHub, sectionMeds, sectionApps, sectionWater, sectionVitamins;
    private LinearLayout containerDoctor, containerFollowUp, containerCustom, containerVitamins;
    private View scrollSections;
    
    private EditText editWaterInterval;
    private Button btnSetWater, btnCancelWater;
    private TextView statusWater;
    private View boxHydrationInfo;
    private TextView txtWhyHydration, btnGotIt;
    
    private List<DosageReminder> urticariaReminders = new ArrayList<>();
    private List<DosageReminder> otherReminders = new ArrayList<>();
    private List<DosageReminder> acuteReminders = new ArrayList<>();
    private List<DosageReminder> vitaminsReminders = new ArrayList<>();
    
    private List<AppointmentReminder> doctorReminders = new ArrayList<>();
    private List<AppointmentReminder> followupReminders = new ArrayList<>();
    private List<AppointmentReminder> customReminders = new ArrayList<>();

    private DosageReminder lastDeletedUrticaria, lastDeletedOther, lastDeletedAcute, lastDeletedVitamin;

    private final Gson gson = new Gson();
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private int selectedColor;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(getContext(), "Notification permission is required for reminders", Toast.LENGTH_SHORT).show();
                }
            });

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateAllCountdowns();
            if (countdownHandler != null) countdownHandler.postDelayed(this, 30000); 
        }
    };

    private int currentSection = -1;

    @Override
    public String getTitle() {
        if (canGoBack()) {
            switch (currentSection) {
                case 0: return getString(R.string.organizer_meds);
                case 1: return getString(R.string.organizer_apps);
                case 2: return getString(R.string.organizer_water);
                case 3: return getString(R.string.organizer_vitamins);
            }
        }
        return getString(R.string.tab_organizer);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_organizer, container, false);
        orgPrefs = requireActivity().getSharedPreferences("OrganizerData", Context.MODE_PRIVATE);
        profilePrefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);

        selectedColor = getResources().getColor(R.color.pill_button_blue);

        summaryName = view.findViewById(R.id.summary_name);
        summaryGender = view.findViewById(R.id.summary_gender);
        summaryAge = view.findViewById(R.id.summary_age);

        layoutHub = view.findViewById(R.id.layout_organizer_hub);
        scrollSections = view.findViewById(R.id.scroll_sections);

        btnNavMeds = view.findViewById(R.id.btn_nav_meds);
        btnNavApps = view.findViewById(R.id.btn_nav_apps);
        btnNavWater = view.findViewById(R.id.btn_nav_water);
        btnNavVitamins = view.findViewById(R.id.btn_nav_vitamins);
        btnNavTriggersManager = view.findViewById(R.id.btn_nav_triggers_manager);
        btnNavStressNavigator = view.findViewById(R.id.btn_nav_stress_navigator);
        btnNavConsumablesTracker = view.findViewById(R.id.btn_nav_consumables_tracker);
        
        sectionMeds = view.findViewById(R.id.section_medication_organizer);
        sectionApps = view.findViewById(R.id.section_appointments_organizer);
        sectionWater = view.findViewById(R.id.section_water_reminder);
        sectionVitamins = view.findViewById(R.id.section_vitamins_organizer);

        navUrticaria = view.findViewById(R.id.nav_urticaria);
        navChronic = view.findViewById(R.id.nav_chronic);
        navAcute = view.findViewById(R.id.nav_acute);

        layoutUrticaria = view.findViewById(R.id.layout_urticaria_flow);
        layoutChronic = view.findViewById(R.id.layout_chronic_flow);
        layoutAcute = view.findViewById(R.id.layout_acute_flow);

        containerUrticaria = view.findViewById(R.id.container_urticaria);
        containerOther = view.findViewById(R.id.container_other);
        containerAcute = view.findViewById(R.id.container_acute);

        containerDoctor = view.findViewById(R.id.container_doctor_apps);
        containerFollowUp = view.findViewById(R.id.container_follow_ups);
        containerCustom = view.findViewById(R.id.container_custom_reminders);
        containerVitamins = view.findViewById(R.id.container_vitamins);

        btnUndoUrticaria = view.findViewById(R.id.btn_undo_urticaria);
        btnUndoOther = view.findViewById(R.id.btn_undo_other);
        btnUndoAcute = view.findViewById(R.id.btn_undo_acute);
        
        editWaterInterval = view.findViewById(R.id.edit_water_interval);
        btnSetWater = view.findViewById(R.id.btn_set_water);
        btnCancelWater = view.findViewById(R.id.btn_cancel_water);
        statusWater = view.findViewById(R.id.status_water_reminder);

        boxHydrationInfo = view.findViewById(R.id.box_hydration_info);
        txtWhyHydration = view.findViewById(R.id.txt_why_hydration);
        btnGotIt = view.findViewById(R.id.btn_got_it);

        btnNavMeds.setOnClickListener(v -> openSection(0));
        btnNavApps.setOnClickListener(v -> openSection(1));
        btnNavWater.setOnClickListener(v -> openSection(2));
        btnNavVitamins.setOnClickListener(v -> openSection(3));

        btnNavTriggersManager.setVisibility(View.VISIBLE);
        btnNavTriggersManager.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new TriggersManagerFragment())
                .addToBackStack(null)
                .commit();
        });

        btnNavStressNavigator.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new StressNavigatorFragment())
                .addToBackStack(null)
                .commit();
        });

        btnNavConsumablesTracker.setOnClickListener(v -> {
            com.google.android.material.tabs.TabLayout tabs = getActivity().findViewById(R.id.tab_layout);
            if (tabs != null) tabs.getTabAt(4).select();
        });

        txtWhyHydration.setOnClickListener(v -> boxHydrationInfo.setVisibility(View.VISIBLE));
        btnGotIt.setOnClickListener(v -> boxHydrationInfo.setVisibility(View.GONE));

        navUrticaria.setOnClickListener(v -> switchMedsSubSection(0));
        navChronic.setOnClickListener(v -> switchMedsSubSection(1));
        navAcute.setOnClickListener(v -> switchMedsSubSection(2));

        view.findViewById(R.id.btn_add_urticaria).setOnClickListener(v -> addMedEntry("Urticaria"));
        view.findViewById(R.id.btn_add_other).setOnClickListener(v -> addMedEntry("Other"));
        view.findViewById(R.id.btn_add_acute).setOnClickListener(v -> addMedEntry("Acute"));

        view.findViewById(R.id.btn_add_doctor_app).setOnClickListener(v -> addAppEntry("Doctor"));
        view.findViewById(R.id.btn_add_follow_up).setOnClickListener(v -> addAppEntry("FollowUp"));
        view.findViewById(R.id.btn_add_custom_rem).setOnClickListener(v -> addAppEntry("Custom"));
        view.findViewById(R.id.btn_add_vitamin).setOnClickListener(v -> addMedEntry("Vitamin"));

        btnUndoUrticaria.setOnClickListener(v -> restoreMedEntry("Urticaria"));
        btnUndoOther.setOnClickListener(v -> restoreMedEntry("Other"));
        btnUndoAcute.setOnClickListener(v -> restoreMedEntry("Acute"));
        
        btnSetWater.setOnClickListener(v -> setWaterReminder());
        btnCancelWater.setOnClickListener(v -> cancelWaterReminder());

        loadProfileSummary();
        loadReminders();
        loadWaterSettings();
        checkPermissions();
        
        showHub();

        return view;
    }

    @Override
    public boolean handleBack() {
        if (canGoBack()) {
            showHub();
            return true;
        }
        return false;
    }

    @Override
    public boolean canGoBack() {
        return layoutHub != null && layoutHub.getVisibility() == View.GONE;
    }

    private void showHub() {
        currentSection = -1;
        if (layoutHub != null) layoutHub.setVisibility(View.VISIBLE);
        if (scrollSections != null) scrollSections.setVisibility(View.GONE);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateBackNavigationUI();
        }
    }

    private void openSection(int index) {
        currentSection = index;
        if (layoutHub != null) layoutHub.setVisibility(View.GONE);
        if (scrollSections != null) scrollSections.setVisibility(View.VISIBLE);
        
        if (sectionMeds != null) sectionMeds.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (sectionApps != null) sectionApps.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (sectionWater != null) sectionWater.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (sectionVitamins != null) sectionVitamins.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        if (index == 0) switchMedsSubSection(0);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateBackNavigationUI();
        }
    }

    private void switchMedsSubSection(int index) {
        navUrticaria.setTextColor(index == 0 ? selectedColor : Color.WHITE);
        navChronic.setTextColor(index == 1 ? selectedColor : Color.WHITE);
        navAcute.setTextColor(index == 2 ? selectedColor : Color.WHITE);

        layoutUrticaria.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        layoutChronic.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        layoutAcute.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    private void setWaterReminder() {
        String input = editWaterInterval.getText().toString();
        if (input.isEmpty()) {
            Toast.makeText(getContext(), "Enter interval", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int interval = Integer.parseInt(input);
            if (interval < 1 || interval > 24) {
                Toast.makeText(getContext(), "Enter 1-24 hours", Toast.LENGTH_SHORT).show();
                return;
            }
            orgPrefs.edit()
                    .putInt("water_interval", interval)
                    .putBoolean("water_active", true)
                    .apply();
            ReminderManager.scheduleWaterReminder(requireContext(), interval);
            updateWaterUI(true, interval);
            Toast.makeText(getContext(), "Water reminder set!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {}
    }

    private void cancelWaterReminder() {
        orgPrefs.edit().putBoolean("water_active", false).apply();
        ReminderManager.cancelWaterReminder(requireContext());
        updateWaterUI(false, 0);
        Toast.makeText(getContext(), "Water reminder cancelled", Toast.LENGTH_SHORT).show();
    }

    private void loadWaterSettings() {
        boolean active = orgPrefs.getBoolean("water_active", false);
        int interval = orgPrefs.getInt("water_interval", 0);
        if (interval > 0) editWaterInterval.setText(String.valueOf(interval));
        updateWaterUI(active, interval);
    }

    private void updateWaterUI(boolean active, int interval) {
        if (active) {
            btnSetWater.setVisibility(View.GONE);
            btnCancelWater.setVisibility(View.VISIBLE);
            statusWater.setText(getString(R.string.status_water_active, interval));
        } else {
            btnSetWater.setVisibility(View.VISIBLE);
            btnCancelWater.setVisibility(View.GONE);
            statusWater.setText("");
        }
    }

    private void restoreMedEntry(String section) {
        if (section.equals("Urticaria") && lastDeletedUrticaria != null) {
            urticariaReminders.add(lastDeletedUrticaria);
            inflateMedEntryView(containerUrticaria, lastDeletedUrticaria, urticariaReminders, "urticaria_list");
            saveMedList("urticaria_list", urticariaReminders);
            lastDeletedUrticaria = null; btnUndoUrticaria.setVisibility(View.GONE);
        } else if (section.equals("Other") && lastDeletedOther != null) {
            otherReminders.add(lastDeletedOther);
            inflateMedEntryView(containerOther, lastDeletedOther, otherReminders, "other_list");
            saveMedList("other_list", otherReminders);
            lastDeletedOther = null; btnUndoOther.setVisibility(View.GONE);
        } else if (section.equals("Acute") && lastDeletedAcute != null) {
            acuteReminders.add(lastDeletedAcute);
            inflateMedEntryView(containerAcute, lastDeletedAcute, acuteReminders, "acute_list");
            saveMedList("acute_list", acuteReminders);
            lastDeletedAcute = null; btnUndoAcute.setVisibility(View.GONE);
        }
        Toast.makeText(getContext(), "Medication restored!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        countdownHandler.post(countdownRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void loadProfileSummary() {
        summaryName.setText("Name: " + profilePrefs.getString("name", "N/A"));
        String gender = profilePrefs.getBoolean("sex_f", false) ? "Female" : (profilePrefs.getBoolean("sex_m", false) ? "Male" : "N/A");
        summaryGender.setText("Gender: " + gender);
        summaryAge.setText("Age: " + profilePrefs.getString("age", "N/A"));
    }

    private void loadReminders() {
        urticariaReminders = getMedList("urticaria_list");
        otherReminders = getMedList("other_list");
        acuteReminders = getMedList("acute_list");
        vitaminsReminders = getMedList("vitamins_list");

        if (containerUrticaria != null) {
            containerUrticaria.removeAllViews();
            for (DosageReminder r : urticariaReminders) if (r != null) inflateMedEntryView(containerUrticaria, r, urticariaReminders, "urticaria_list");
        }
        if (containerOther != null) {
            containerOther.removeAllViews();
            for (DosageReminder r : otherReminders) if (r != null) inflateMedEntryView(containerOther, r, otherReminders, "other_list");
        }
        if (containerAcute != null) {
            containerAcute.removeAllViews();
            for (DosageReminder r : acuteReminders) if (r != null) inflateMedEntryView(containerAcute, r, acuteReminders, "acute_list");
        }
        if (containerVitamins != null) {
            containerVitamins.removeAllViews();
            for (DosageReminder r : vitaminsReminders) if (r != null) inflateMedEntryView(containerVitamins, r, vitaminsReminders, "vitamins_list");
        }

        doctorReminders = getAppList("doctor_list");
        followupReminders = getAppList("followup_list");
        customReminders = getAppList("custom_list");

        if (containerDoctor != null) {
            containerDoctor.removeAllViews();
            for (AppointmentReminder r : doctorReminders) if (r != null) inflateAppEntryView(containerDoctor, r, doctorReminders, "doctor_list");
        }
        if (containerFollowUp != null) {
            containerFollowUp.removeAllViews();
            for (AppointmentReminder r : followupReminders) if (r != null) inflateAppEntryView(containerFollowUp, r, followupReminders, "followup_list");
        }
        if (containerCustom != null) {
            containerCustom.removeAllViews();
            for (AppointmentReminder r : customReminders) if (r != null) inflateAppEntryView(containerCustom, r, customReminders, "custom_list");
        }
    }

    private void addMedEntry(String section) {
        DosageReminder reminder = new DosageReminder(section);
        if (section.equals("Urticaria")) {
            urticariaReminders.add(reminder);
            inflateMedEntryView(containerUrticaria, reminder, urticariaReminders, "urticaria_list");
            saveMedList("urticaria_list", urticariaReminders);
        } else if (section.equals("Other")) {
            otherReminders.add(reminder);
            inflateMedEntryView(containerOther, reminder, otherReminders, "other_list");
            saveMedList("other_list", otherReminders);
        } else if (section.equals("Vitamin")) {
            vitaminsReminders.add(reminder);
            inflateMedEntryView(containerVitamins, reminder, vitaminsReminders, "vitamins_list");
            saveMedList("vitamins_list", vitaminsReminders);
        } else {
            acuteReminders.add(reminder);
            inflateMedEntryView(containerAcute, reminder, acuteReminders, "acute_list");
            saveMedList("acute_list", acuteReminders);
        }
    }

    private void inflateMedEntryView(LinearLayout parent, DosageReminder reminder, List<DosageReminder> list, String prefKey) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.layout_dosage_entry, parent, false);
        LinearLayout rowCondition = v.findViewById(R.id.row_condition);
        EditText editCondition = v.findViewById(R.id.edit_condition);
        EditText editMed = v.findViewById(R.id.edit_med_name);
        EditText editInt = v.findViewById(R.id.edit_interval);
        Button btnRemind = v.findViewById(R.id.btn_remind);
        Button btnPause = v.findViewById(R.id.btn_pause);
        Button btnClear = v.findViewById(R.id.btn_clear_entry);
        TextView txtCountdown = v.findViewById(R.id.txt_countdown);
        
        TextView labelDosage = v.findViewById(R.id.label_dosage);
        TextView labelHours = v.findViewById(R.id.label_hours);

        if ("Vitamin".equals(reminder.section)) {
            rowCondition.setVisibility(View.GONE);
            if (labelDosage != null) labelDosage.setText("Vitamin Name:");
            editMed.setHint("Vitamin Name");
            if (labelHours != null) labelHours.setText("hour(s) (1-24)");
        } else if ("Urticaria".equals(reminder.section)) {
            rowCondition.setVisibility(View.GONE);
            editMed.setHint(getString(R.string.hint_med_name));
        } else {
            rowCondition.setVisibility(View.VISIBLE);
            editCondition.setText(reminder.condition != null ? reminder.condition : "");
            editCondition.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { 
                    reminder.condition = s.toString(); 
                    saveMedList(prefKey, list);
                }
            });
        }

        editMed.setText(reminder.medName != null ? reminder.medName : "");
        editInt.setText(String.valueOf(reminder.intervalHours));
        v.setTag(reminder.id);

        if (reminder.isActive) {
            btnRemind.setText("Active");
            btnRemind.setEnabled(false);
            updateCountdownText(txtCountdown, reminder);
        }

        editMed.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { 
                reminder.medName = s.toString(); 
                saveMedList(prefKey, list);
            }
        });

        editInt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty()) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val < 1) val = 1;
                        if (val > 24) val = 24;
                        reminder.intervalHours = val;
                        saveMedList(prefKey, list);
                    } catch (Exception e) {}
                }
            }
        });

        btnRemind.setOnClickListener(view -> {
            String med = editMed.getText().toString();
            if (med.isEmpty()) { Toast.makeText(getContext(), "Enter name first", Toast.LENGTH_SHORT).show(); return; }
            if (!"Urticaria".equals(reminder.section) && !"Vitamin".equals(reminder.section)) {
                if (editCondition.getText().toString().isEmpty()) {
                    Toast.makeText(getContext(), "Enter condition name first", Toast.LENGTH_SHORT).show(); return;
                }
            }
            reminder.isActive = true;
            reminder.medName = med;
            if ("Vitamin".equals(reminder.section)) {
                reminder.nextTriggerTime = ReminderManager.scheduleVitaminReminder(requireContext(), reminder.id, reminder.medName, reminder.intervalHours);
            } else {
                reminder.condition = editCondition.getText().toString();
                String title = "Urticaria".equals(reminder.section) ? "Urticaria" : reminder.condition;
                reminder.nextTriggerTime = ReminderManager.scheduleReminder(requireContext(), reminder.id, reminder.medName, reminder.intervalHours, title);
            }
            btnRemind.setText("Active"); btnRemind.setEnabled(false);
            updateCountdownText(txtCountdown, reminder);
            saveMedList(prefKey, list);
            Toast.makeText(getContext(), "Reminder scheduled!", Toast.LENGTH_SHORT).show();
        });

        btnPause.setOnClickListener(view -> {
            reminder.isActive = false;
            reminder.nextTriggerTime = 0;
            ReminderManager.cancelReminder(requireContext(), reminder.id);
            btnRemind.setText(getString(R.string.btn_remind_me));
            btnRemind.setEnabled(true);
            txtCountdown.setVisibility(View.GONE);
            saveMedList(prefKey, list);
            Toast.makeText(getContext(), "Reminder paused", Toast.LENGTH_SHORT).show();
        });

        btnClear.setOnClickListener(view -> {
            ReminderManager.cancelReminder(requireContext(), reminder.id);
            if (reminder.section.equals("Urticaria")) { lastDeletedUrticaria = reminder; btnUndoUrticaria.setVisibility(View.VISIBLE); }
            else if (reminder.section.equals("Other")) { lastDeletedOther = reminder; btnUndoOther.setVisibility(View.VISIBLE); }
            else if (reminder.section.equals("Vitamin")) { lastDeletedVitamin = reminder; }
            else { lastDeletedAcute = reminder; btnUndoAcute.setVisibility(View.VISIBLE); }
            list.remove(reminder); parent.removeView(v); saveMedList(prefKey, list);
            Toast.makeText(getContext(), "Entry cleared", Toast.LENGTH_SHORT).show();
        });

        parent.addView(v);
    }

    private void addAppEntry(String type) {
        AppointmentReminder reminder = new AppointmentReminder(type);
        if (type.equals("Doctor")) {
            doctorReminders.add(reminder);
            inflateAppEntryView(containerDoctor, reminder, doctorReminders, "doctor_list");
            saveAppList("doctor_list", doctorReminders);
        } else if (type.equals("FollowUp")) {
            followupReminders.add(reminder);
            inflateAppEntryView(containerFollowUp, reminder, followupReminders, "followup_list");
            saveAppList("followup_list", followupReminders);
        } else {
            customReminders.add(reminder);
            inflateAppEntryView(containerCustom, reminder, customReminders, "custom_list");
            saveAppList("custom_list", customReminders);
        }
    }

    private void inflateAppEntryView(LinearLayout parent, AppointmentReminder reminder, List<AppointmentReminder> list, String prefKey) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.layout_appointment_entry, parent, false);
        TextView labelType = v.findViewById(R.id.label_type);
        EditText editTitle = v.findViewById(R.id.edit_title);
        TextView txtDateTime = v.findViewById(R.id.txt_date_time);
        Button btnDate = v.findViewById(R.id.btn_select_date);
        Button btnRepeat = v.findViewById(R.id.btn_repeat);
        Button btnRemind = v.findViewById(R.id.btn_remind);
        Button btnClear = v.findViewById(R.id.btn_clear);
        Button btnUndo = v.findViewById(R.id.btn_undo);

        if ("Doctor".equals(reminder.type)) labelType.setText(getString(R.string.label_doctor_app));
        else if ("FollowUp".equals(reminder.type)) labelType.setText(getString(R.string.label_follow_up));
        else labelType.setText(getString(R.string.label_custom_rem));

        editTitle.setText(reminder.title != null ? reminder.title : "");
        updateAppDateTimeText(txtDateTime, reminder);

        if (reminder.isActive) { btnRemind.setText("Active"); btnRemind.setEnabled(false); }

        editTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { reminder.title = s.toString(); saveAppList(prefKey, list); }
        });

        btnDate.setOnClickListener(view -> {
            Calendar cal = Calendar.getInstance();
            if (reminder.dateTime > 0) cal.setTimeInMillis(reminder.dateTime);
            new DatePickerDialog(getContext(), (dp, y, m, d) -> {
                cal.set(y, m, d);
                new TimePickerDialog(getContext(), (tp, hr, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                    reminder.dateTime = cal.getTimeInMillis();
                    updateAppDateTimeText(txtDateTime, reminder);
                    saveAppList(prefKey, list);
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnRepeat.setOnClickListener(view -> {
            String[] patterns = {"None", "Weekly", "Biweekly", "Monthly"};
            new AlertDialog.Builder(getContext()).setTitle("Select Repeat Pattern").setItems(patterns, (dialog, which) -> {
                reminder.repeatPattern = patterns[which];
                Toast.makeText(getContext(), "Repeat set to: " + reminder.repeatPattern, Toast.LENGTH_SHORT).show();
                saveAppList(prefKey, list);
            }).show();
        });

        btnRemind.setOnClickListener(view -> {
            String title = editTitle.getText().toString();
            if (title.isEmpty()) { Toast.makeText(getContext(), "Enter title first", Toast.LENGTH_SHORT).show(); return; }
            if (reminder.dateTime <= System.currentTimeMillis()) { Toast.makeText(getContext(), "Select a future date/time", Toast.LENGTH_SHORT).show(); return; }
            reminder.isActive = true;
            reminder.title = title;
            String fullTitle = "Doctor".equals(reminder.type) ? "Doctor " + reminder.title : reminder.title;
            ReminderManager.scheduleAppointment(requireContext(), reminder.id, fullTitle, reminder.dateTime, reminder.repeatPattern);
            btnRemind.setText("Active"); btnRemind.setEnabled(false);
            saveAppList(prefKey, list);
            Toast.makeText(getContext(), "Reminder scheduled!", Toast.LENGTH_SHORT).show();
        });

        btnClear.setOnClickListener(view -> {
            ReminderManager.cancelReminder(requireContext(), reminder.id);
            AppointmentReminder deleted = reminder;
            list.remove(reminder); parent.removeView(v); saveAppList(prefKey, list);
            btnUndo.setVisibility(View.VISIBLE);
            btnUndo.setOnClickListener(uv -> {
                list.add(deleted); inflateAppEntryView(parent, deleted, list, prefKey); saveAppList(prefKey, list);
            });
            Toast.makeText(getContext(), "Reminder cleared", Toast.LENGTH_SHORT).show();
        });

        parent.addView(v);
    }

    private void updateAppDateTimeText(TextView tv, AppointmentReminder r) {
        if (tv == null || r == null) return;
        if (r.dateTime == 0) tv.setText(getString(R.string.status_no_date));
        else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
            String rep = ("None".equals(r.repeatPattern) || r.repeatPattern == null) ? "" : " (" + r.repeatPattern + ")";
            tv.setText("Scheduled for: " + sdf.format(new Date(r.dateTime)) + rep);
        }
    }

    private void updateAllCountdowns() {
        updateContainerCountdowns(containerUrticaria, urticariaReminders);
        updateContainerCountdowns(containerOther, otherReminders);
        updateContainerCountdowns(containerAcute, acuteReminders);
        updateContainerCountdowns(containerVitamins, vitaminsReminders);
    }

    private void updateContainerCountdowns(LinearLayout container, List<DosageReminder> reminders) {
        if (container == null || reminders == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            String id = (String) v.getTag();
            if (id == null) continue;
            for (DosageReminder r : reminders) {
                if (r != null && id.equals(r.id)) {
                    TextView txt = v.findViewById(R.id.txt_countdown);
                    if (txt != null) updateCountdownText(txt, r);
                    break;
                }
            }
        }
    }

    private void updateCountdownText(TextView txt, DosageReminder r) {
        if (!r.isActive || r.nextTriggerTime <= System.currentTimeMillis()) {
            txt.setVisibility(View.GONE);
            return;
        }
        long diff = r.nextTriggerTime - System.currentTimeMillis();
        long h = TimeUnit.MILLISECONDS.toHours(diff);
        long m = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        txt.setText(getString(R.string.label_next_dose) + h + "h " + m + "m");
        txt.setVisibility(View.VISIBLE);
    }

    private void saveMedList(String key, List<DosageReminder> list) {
        orgPrefs.edit().putString(key, gson.toJson(list)).apply();
    }

    private List<DosageReminder> getMedList(String key) {
        String json = orgPrefs.getString(key, "[]");
        return gson.fromJson(json, new TypeToken<List<DosageReminder>>(){}.getType());
    }

    private void saveAppList(String key, List<AppointmentReminder> list) {
        orgPrefs.edit().putString(key, gson.toJson(list)).apply();
    }

    private List<AppointmentReminder> getAppList(String key) {
        String json = orgPrefs.getString(key, "[]");
        return gson.fromJson(json, new TypeToken<List<AppointmentReminder>>(){}.getType());
    }
}