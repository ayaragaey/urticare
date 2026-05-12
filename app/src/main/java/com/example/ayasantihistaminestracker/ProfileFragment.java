package com.example.ayasantihistaminestracker;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment implements MainActivity.TitleProvider {

    private SharedPreferences prefs;
    private EditText editName, editAge, editDiagnosis, editPregMonth, editChronic;
    private CheckBox cbF, cbM, cbXy, cbXn, cbPregYes, cbPregNo, cbMensOn, cbMensOff;
    private TextView displayMensDate, displayMensEndDate, btnClearMens, btnClearMensEnd, btnCycleLog, displayXolairFreq, btnClearProfile, btnUndoProfile;
    private TextView btnUndoClearMens;
    private LinearLayout sectionFemale, rowPregMonth, containerRecurringMeds, containerAhList, containerCortisoneList;
    private long startTs = 0, endTs = 0;
    private long lastClearedStartTs = 0;
    private final Map<String, Object> backup = new HashMap<>();
    private final Gson gson = new Gson();
    private List<RecurringMedication> recurringMeds = new ArrayList<>();
    private List<RecurringMedication> ahList = new ArrayList<>();
    private List<RecurringMedication> cortisoneList = new ArrayList<>();

    public static class RecurringMedication {
        String id;
        String name;
        String dosage;
        String reasonMain;
        String reasonSub;
        String reasonVitamin;
        String reasonOther;
        boolean isDaily;
        RecurringMedication() { id = java.util.UUID.randomUUID().toString(); }
    }

    @Override
    public String getTitle() {
        return getString(R.string.tab_profile);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        prefs = requireActivity().getSharedPreferences("ProfileData", Context.MODE_PRIVATE);

        editName = view.findViewById(R.id.edit_name);
        editAge = view.findViewById(R.id.edit_age);
        editDiagnosis = view.findViewById(R.id.edit_diagnosis);
        editChronic = view.findViewById(R.id.edit_chronic_conditions);
        editPregMonth = view.findViewById(R.id.edit_preg_month);

        cbF = view.findViewById(R.id.cb_sex_f);
        cbM = view.findViewById(R.id.cb_sex_m);
        cbXy = view.findViewById(R.id.cb_xolair_yes);
        cbXn = view.findViewById(R.id.cb_xolair_no);
        displayXolairFreq = view.findViewById(R.id.display_xolair_freq);
        
        cbPregYes = view.findViewById(R.id.cb_preg_yes);
        cbPregNo = view.findViewById(R.id.cb_preg_no);
        cbMensOn = view.findViewById(R.id.cb_mens_remind_on);
        cbMensOff = view.findViewById(R.id.cb_mens_remind_off);
        
        displayMensDate = view.findViewById(R.id.display_mens_date);
        displayMensEndDate = view.findViewById(R.id.display_mens_end_date);
        btnClearMens = view.findViewById(R.id.btn_clear_mens);
        btnUndoClearMens = view.findViewById(R.id.btn_undo_clear_mens);
        btnClearMensEnd = view.findViewById(R.id.btn_clear_mens_end);
        btnCycleLog = view.findViewById(R.id.btn_cycle_log);
        
        btnClearProfile = view.findViewById(R.id.btn_clear_profile);
        btnUndoProfile = view.findViewById(R.id.btn_undo_profile);
        sectionFemale = view.findViewById(R.id.section_female);
        rowPregMonth = view.findViewById(R.id.row_preg_month);
        containerRecurringMeds = view.findViewById(R.id.container_recurring_meds);
        containerAhList = view.findViewById(R.id.container_ah_list);
        containerCortisoneList = view.findViewById(R.id.container_cortisone_list);

        view.findViewById(R.id.btn_add_recurring_med).setOnClickListener(v -> showMedicationDialog(null, recurringMeds, "recurring_meds"));
        view.findViewById(R.id.btn_add_ah).setOnClickListener(v -> showMedicationDialog(null, ahList, "ah_list"));
        view.findViewById(R.id.btn_add_cortisone).setOnClickListener(v -> showMedicationDialog(null, cortisoneList, "cortisone_list"));

        displayMensDate.setOnClickListener(v -> showDatePicker(true));
        displayMensEndDate.setOnClickListener(v -> showDatePicker(false));
        
        btnClearMens.setOnClickListener(v -> { 
            lastClearedStartTs = startTs;
            startTs = 0; 
            btnUndoClearMens.setVisibility(View.VISIBLE);
            updateDateViews(); 
            saveData(); 
        });
        btnUndoClearMens.setOnClickListener(v -> {
            startTs = lastClearedStartTs;
            btnUndoClearMens.setVisibility(View.GONE);
            updateDateViews();
            saveData();
        });

        btnClearMensEnd.setOnClickListener(v -> { endTs = 0; updateDateViews(); saveData(); });
        btnCycleLog.setOnClickListener(v -> showCycleLog());
        btnClearProfile.setOnClickListener(v -> clearAll());
        btnUndoProfile.setOnClickListener(v -> undo());

        view.findViewById(R.id.btn_save_preg_month).setOnClickListener(v -> {
            saveData();
            Toast.makeText(getContext(), "Pregnancy month saved", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_undo_preg_month).setOnClickListener(v -> {
            editPregMonth.setText("0");
            cbPregYes.setChecked(false);
            cbPregNo.setChecked(true);
            saveData();
            Toast.makeText(getContext(), "Pregnancy reset to No (0 months)", Toast.LENGTH_SHORT).show();
        });

        loadData();
        checkAndIncrementPregnancy();
        checkPregnancyEndReminder();
        setupListeners();
        return view;
    }

    private void checkPregnancyEndReminder() {
        if (!cbPregYes.isChecked()) return;
        
        long lastUpdate = prefs.getLong("preg_last_update", 0);
        if (lastUpdate == 0) return;

        long diff = System.currentTimeMillis() - lastUpdate;
        long days = diff / (1000 * 60 * 60 * 24);
        
        int currentMonth = 1;
        try { currentMonth = Integer.parseInt(editPregMonth.getText().toString()); } catch(Exception e){}
        
        double totalMonths = currentMonth + (days / 30.0);
        
        if (totalMonths >= 9.5) {
            new AlertDialog.Builder(getContext())
                .setTitle("Pregnancy Tracking")
                .setMessage(getString(R.string.prompt_preg_ended))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    cbPregYes.setChecked(false);
                    cbPregNo.setChecked(true);
                    saveData();
                })
                .setNegativeButton(R.string.no, null)
                .show();
        }
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(getContext(), (view, y, m, d) -> {
            Calendar s = Calendar.getInstance();
            s.set(y, m, d);
            if (isStart) startTs = s.getTimeInMillis();
            else {
                endTs = s.getTimeInMillis();
                if (startTs > 0) addToHistory(startTs, endTs);
            }
            updateDateViews();
            saveData();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateViews() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        displayMensDate.setText(startTs == 0 ? "Select Date" : sdf.format(new Date(startTs)));
        displayMensEndDate.setText(endTs == 0 ? "Select Date" : sdf.format(new Date(endTs)));
    }

    private void addToHistory(long s, long e) {
        List<CycleEntry> list = getHistory();
        list.add(0, new CycleEntry(s, e));
        if (list.size() > 12) list.remove(12);
        prefs.edit().putString("cycle_history", gson.toJson(list)).apply();
    }

    private List<CycleEntry> getHistory() {
        String json = prefs.getString("cycle_history", "[]");
        return gson.fromJson(json, new TypeToken<List<CycleEntry>>(){}.getType());
    }

    private void showCycleLog() {
        List<CycleEntry> list = getHistory();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        if (list.isEmpty()) {
            TextView tv = new TextView(getContext());
            tv.setText("No history yet.");
            layout.addView(tv);
        } else {
            for (int i = 0; i < list.size(); i++) {
                final int index = i;
                CycleEntry entry = list.get(i);
                LinearLayout item = new LinearLayout(getContext());
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setPadding(0, 8, 0, 8);

                TextView text = new TextView(getContext());
                text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                text.setText("Start: " + sdf.format(new Date(entry.start)) + " - End: " + sdf.format(new Date(entry.end)));
                text.setTextColor(Color.WHITE);

                TextView clear = new TextView(getContext());
                clear.setText("Clear");
                clear.setTextColor(Color.RED);
                clear.setPadding(16, 0, 0, 0);
                clear.setOnClickListener(v -> {
                    list.remove(index);
                    prefs.edit().putString("cycle_history", gson.toJson(list)).apply();
                    Toast.makeText(getContext(), "Entry cleared", Toast.LENGTH_SHORT).show();
                    showCycleLog(); // Refresh dialog
                });

                item.addView(text);
                item.addView(clear);
                layout.addView(item);
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Cycle Log (Last 12 Months)")
                .setView(layout)
                .setPositiveButton("X", null)
                .setNeutralButton("Export", (d, w) -> showExportChoice())
                .show();
    }

    private void showExportChoice() {
        String[] options = {"CSV", "Excel"};
        new AlertDialog.Builder(getContext()).setTitle("Select Format").setItems(options, (d, w) -> {
            if (w == 0) exportHistory(true); else exportHistory(false);
        }).show();
    }

    private void exportHistory(boolean isCsv) {
        List<CycleEntry> list = getHistory();
        if (list.isEmpty()) return;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            if (isCsv) {
                File file = new File(requireContext().getCacheDir(), "cycle_history.csv");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write("Start Date,End Date\n".getBytes());
                for (CycleEntry entry : list) {
                    fos.write((sdf.format(new Date(entry.start)) + "," + sdf.format(new Date(entry.end)) + "\n").getBytes());
                }
                fos.close();
                share(file, "text/csv");
            } else {
                XSSFWorkbook wb = new XSSFWorkbook();
                Sheet s = wb.createSheet("Cycle History");
                Row h = s.createRow(0); h.createCell(0).setCellValue("Start Date"); h.createCell(1).setCellValue("End Date");
                for (int i=0; i<list.size(); i++) {
                    Row r = s.createRow(i+1);
                    r.createCell(0).setCellValue(sdf.format(new Date(list.get(i).start)));
                    r.createCell(1).setCellValue(sdf.format(new Date(list.get(i).end)));
                }
                File file = new File(requireContext().getCacheDir(), "cycle_history.xlsx");
                FileOutputStream fos = new FileOutputStream(file); wb.write(fos); fos.close(); wb.close();
                share(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void share(File f, String type) {
        Uri path = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_SEND); i.setType(type); i.putExtra(Intent.EXTRA_STREAM, path);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Export History"));
    }

    private void showXolairFreqDialog() {
        String[] options = {"Weekly", "Biweekly", "Monthly"};
        new AlertDialog.Builder(getContext()).setTitle("Select Frequency").setItems(options, (d, w) -> {
            displayXolairFreq.setText(options[w]);
            displayXolairFreq.setVisibility(View.VISIBLE);
            saveData();
        }).show();
    }

    private void clearAll() {
        backup.clear();
        backup.put("name", editName.getText().toString()); backup.put("age", editAge.getText().toString());
        backup.put("diag", editDiagnosis.getText().toString()); 
        backup.put("ch", editChronic.getText().toString());
        backup.put("sex_f", cbF.isChecked()); backup.put("sex_m", cbM.isChecked());
        backup.put("xy", cbXy.isChecked()); backup.put("xn", cbXn.isChecked());
        backup.put("xf", displayXolairFreq.getText().toString());
        backup.put("py", cbPregYes.isChecked()); backup.put("pn", cbPregNo.isChecked());
        backup.put("pm", editPregMonth.getText().toString());
        backup.put("start", startTs); backup.put("end", endTs);
        backup.put("ron", cbMensOn.isChecked()); backup.put("roff", cbMensOff.isChecked());
        backup.put("recurring_meds", gson.toJson(recurringMeds));
        backup.put("ah_list", gson.toJson(ahList));
        backup.put("cortisone_list", gson.toJson(cortisoneList));

        editName.setText(""); editAge.setText(""); editDiagnosis.setText(""); 
        editChronic.setText(""); editPregMonth.setText("1");
        cbF.setChecked(false); cbM.setChecked(false); cbXy.setChecked(false); cbXn.setChecked(false);
        displayXolairFreq.setText(""); displayXolairFreq.setVisibility(View.GONE);
        cbPregYes.setChecked(false); cbPregNo.setChecked(false);
        cbMensOn.setChecked(false); cbMensOff.setChecked(false);
        startTs = 0; endTs = 0; updateDateViews();
        
        recurringMeds.clear(); ahList.clear(); cortisoneList.clear();
        populateMeds(containerRecurringMeds, recurringMeds, "recurring_meds");
        populateMeds(containerAhList, ahList, "ah_list");
        populateMeds(containerCortisoneList, cortisoneList, "cortisone_list");
        
        btnUndoProfile.setVisibility(View.VISIBLE); saveData();
    }

    private void undo() {
        if (backup.isEmpty()) return;
        editName.setText((String)backup.get("name")); editAge.setText((String)backup.get("age"));
        editDiagnosis.setText((String)backup.get("diag")); 
        editChronic.setText((String)backup.get("ch"));
        cbF.setChecked(Boolean.TRUE.equals(backup.get("sex_f"))); cbM.setChecked(Boolean.TRUE.equals(backup.get("sex_m")));
        cbXy.setChecked(Boolean.TRUE.equals(backup.get("xy"))); cbXn.setChecked(Boolean.TRUE.equals(backup.get("xn")));
        displayXolairFreq.setText((String)backup.get("xf"));
        displayXolairFreq.setVisibility(displayXolairFreq.getText().length() > 0 ? View.VISIBLE : View.GONE);
        cbPregYes.setChecked(Boolean.TRUE.equals(backup.get("py"))); cbPregNo.setChecked(Boolean.TRUE.equals(backup.get("pn")));
        editPregMonth.setText((String)backup.get("pm"));
        startTs = (Long)backup.get("start"); endTs = (Long)backup.get("end"); updateDateViews();
        cbMensOn.setChecked(Boolean.TRUE.equals(backup.get("ron"))); cbMensOff.setChecked(Boolean.TRUE.equals(backup.get("roff")));
        
        restoreListFromBackup("recurring_meds", recurringMeds, containerRecurringMeds);
        restoreListFromBackup("ah_list", ahList, containerAhList);
        restoreListFromBackup("cortisone_list", cortisoneList, containerCortisoneList);

        btnUndoProfile.setVisibility(View.GONE); saveData();
    }

    private void restoreListFromBackup(String key, List<RecurringMedication> list, LinearLayout container) {
        String saved = (String)backup.get(key);
        if (saved != null) {
            list.clear();
            list.addAll(gson.fromJson(saved, new TypeToken<List<RecurringMedication>>(){}.getType()));
            populateMeds(container, list, key);
        }
    }

    private void loadData() {
        editName.setText(prefs.getString("name", "")); editAge.setText(prefs.getString("age", ""));
        editDiagnosis.setText(prefs.getString("diagnosis", "")); 
        editChronic.setText(prefs.getString("chronic", ""));
        editPregMonth.setText(prefs.getString("preg_month", "1"));
        cbF.setChecked(prefs.getBoolean("sex_f", false)); cbM.setChecked(prefs.getBoolean("sex_m", false));
        cbXy.setChecked(prefs.getBoolean("xolair_y", false)); cbXn.setChecked(prefs.getBoolean("xolair_n", false));
        cbMensOn.setChecked(prefs.getBoolean("mens_on", true)); cbMensOff.setChecked(prefs.getBoolean("mens_off", false));
        displayXolairFreq.setText(prefs.getString("xolair_freq", ""));
        displayXolairFreq.setVisibility(cbXy.isChecked() && displayXolairFreq.getText().length() > 0 ? View.VISIBLE : View.GONE);
        cbPregYes.setChecked(prefs.getBoolean("preg_y", false)); cbPregNo.setChecked(prefs.getBoolean("preg_n", false));
        startTs = prefs.getLong("mens_start", 0); endTs = prefs.getLong("mens_end", 0); updateDateViews();
        sectionFemale.setVisibility(cbF.isChecked() ? View.VISIBLE : View.GONE);
        rowPregMonth.setVisibility(cbPregYes.isChecked() ? View.VISIBLE : View.GONE);
        
        loadAndMigrateList("ah_list", "ah1", ahList, containerAhList);
        loadAndMigrateList("cortisone_list", "cortisone", cortisoneList, containerCortisoneList);
        loadAndMigrateList("recurring_meds", null, recurringMeds, containerRecurringMeds);
    }

    private void loadAndMigrateList(String key, String oldKey, List<RecurringMedication> list, LinearLayout container) {
        String json = prefs.getString(key, "[]");
        list.clear();
        list.addAll(gson.fromJson(json, new TypeToken<List<RecurringMedication>>(){}.getType()));
        
        if (list.isEmpty() && oldKey != null) {
            String oldVal = prefs.getString(oldKey, "");
            if (!oldVal.isEmpty()) {
                for (String s : oldVal.split(",")) {
                    String name = s.trim();
                    if (!name.isEmpty()) {
                        RecurringMedication med = new RecurringMedication();
                        med.name = name;
                        med.dosage = "";
                        med.reasonMain = "";
                        med.reasonSub = "";
                        med.reasonVitamin = "";
                        med.reasonOther = "";
                        list.add(med);
                    }
                }
                // Save new format immediately if migrated
                saveData();
            }
        }
        populateMeds(container, list, key);
    }

    private void populateMeds(LinearLayout container, List<RecurringMedication> list, String key) {
        if (container == null) return;
        container.removeAllViews();
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            RecurringMedication med = list.get(i);
            
            View row = LayoutInflater.from(getContext()).inflate(R.layout.item_recurring_med, container, false);
            TextView txtName = row.findViewById(R.id.txt_med_name);
            TextView txtDosage = row.findViewById(R.id.txt_dosage_info);
            TextView btnEdit = row.findViewById(R.id.btn_edit_med);
            TextView btnDelete = row.findViewById(R.id.btn_delete_med);
            TextView btnUp = row.findViewById(R.id.btn_up);
            TextView btnDown = row.findViewById(R.id.btn_down);
            
            String display = med.name;
            if (med.isDaily) display += " (Daily)";
            txtName.setText(display);
            
            String info = (med.dosage == null || med.dosage.isEmpty() ? "" : med.dosage) 
                        + getReasonDisplay(med);
            txtDosage.setText(info.isEmpty() ? "No details" : info);
            
            btnEdit.setOnClickListener(v -> showMedicationDialog(med, list, key));
            btnDelete.setOnClickListener(v -> {
                list.remove(index);
                saveData();
                populateMeds(container, list, key);
            });

            btnUp.setOnClickListener(v -> {
                if (index > 0) {
                    java.util.Collections.swap(list, index, index - 1);
                    saveData(); populateMeds(container, list, key);
                }
            });
            
            btnDown.setOnClickListener(v -> {
                if (index < list.size() - 1) {
                    java.util.Collections.swap(list, index, index + 1);
                    saveData(); populateMeds(container, list, key);
                }
            });

            container.addView(row);
        }
    }

    private void showMedicationDialog(RecurringMedication existing, List<RecurringMedication> list, String key) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dv = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_recurring_med, null);
        builder.setView(dv);

        EditText etName = dv.findViewById(R.id.edit_med_name);
        EditText etDosage = dv.findViewById(R.id.edit_med_dosage);
        View reasonContainer = dv.findViewById(R.id.reason_container);
        TextView tvReason = dv.findViewById(R.id.text_med_reason);
        CheckBox cbDaily = dv.findViewById(R.id.cb_daily_entry);

        final String[] selectedMain = {existing != null ? existing.reasonMain : ""};
        final String[] selectedSub = {existing != null ? existing.reasonSub : ""};
        final String[] selectedVitamin = {existing != null ? existing.reasonVitamin : ""};
        final String[] otherNote = {existing != null ? existing.reasonOther : ""};

        if (key.equals("cortisone_list")) {
            reasonContainer.setVisibility(View.VISIBLE);
            updateReasonTextView(tvReason, selectedMain[0], selectedSub[0], selectedVitamin[0], otherNote[0]);
            tvReason.setOnClickListener(v -> showReasonSelectionDialog(tvReason, selectedMain, selectedSub, selectedVitamin, otherNote));
        }

        if (existing != null) {
            etName.setText(existing.name);
            etDosage.setText(existing.dosage);
            cbDaily.setChecked(existing.isDaily);
        }

        builder.setPositiveButton("Save", (d, w) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;

            RecurringMedication med = (existing != null) ? existing : new RecurringMedication();
            med.name = name;
            med.dosage = etDosage.getText().toString().trim();
            med.reasonMain = selectedMain[0];
            med.reasonSub = selectedSub[0];
            med.reasonVitamin = selectedVitamin[0];
            med.reasonOther = otherNote[0];
            med.isDaily = cbDaily.isChecked();

            if (existing == null) list.add(med);
            saveData();
            
            LinearLayout container = containerRecurringMeds;
            if (key.equals("ah_list")) container = containerAhList;
            else if (key.equals("cortisone_list")) container = containerCortisoneList;
            
            populateMeds(container, list, key);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateReasonTextView(TextView tv, String main, String sub, String vit, String other) {
        if (main == null || main.isEmpty()) {
            tv.setText(R.string.possible_reason_short_gap);
            return;
        }
        String display = main;
        if (sub != null && !sub.isEmpty()) {
            display += ": " + sub;
            if ("Medication-related trigger".equals(sub) && vit != null && !vit.isEmpty()) {
                display += " (" + vit + ")";
            }
        }
        if ("Manual Entry / Other".equals(main) && other != null && !other.isEmpty()) {
            display += " (" + other + ")";
        }
        tv.setText(display);
    }

    private String getReasonDisplay(RecurringMedication med) {
        if (med.reasonMain == null || med.reasonMain.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(" | ").append(med.reasonMain);
        if (med.reasonSub != null && !med.reasonSub.isEmpty()) {
            sb.append(": ").append(med.reasonSub);
            if ("Medication-related trigger".equals(med.reasonSub) && med.reasonVitamin != null && !med.reasonVitamin.isEmpty()) {
                sb.append(" (").append(med.reasonVitamin).append(")");
            }
        }
        if ("Manual Entry / Other".equals(med.reasonMain) && med.reasonOther != null && !med.reasonOther.isEmpty()) {
            sb.append(" (").append(med.reasonOther).append(")");
        }
        return sb.toString();
    }

    private void showReasonSelectionDialog(TextView toggleView, String[] selectedMain, String[] selectedSub, String[] selectedVitamin, String[] otherNote) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_reason_selection, null);
        builder.setView(dialogView);

        LinearLayout categoriesContainer = dialogView.findViewById(R.id.categories_container);
        android.widget.Button btnOk = dialogView.findViewById(R.id.btn_reason_ok);
        android.widget.Button btnClear = dialogView.findViewById(R.id.btn_reason_clear);

        // Define reasons
        Map<String, List<String>> reasonsMap = new java.util.LinkedHashMap<>();
        reasonsMap.put("Flare Up", new ArrayList<>());
        reasonsMap.put("CU Activity", new ArrayList<>());
        reasonsMap.put("Precautionary Measure", new ArrayList<>());
        reasonsMap.put("Symptoms Returning", java.util.Arrays.asList("Symptoms returned early", "Severe itching", "Hives worsening", "Swelling / angioedema symptoms", "Symptoms affecting sleep", "Symptoms after waking up", "Sudden flare-up", "Previous dose felt ineffective"));
        reasonsMap.put("Trigger Exposure", java.util.Arrays.asList("Stress or emotional distress", "Heat exposure", "Cold exposure", "Sweating / exercise", "Suspected food trigger", "Environmental trigger", "Pressure/friction on skin", "Medication-related trigger", "Infection / illness", "Unknown trigger exposure"));
        reasonsMap.put("Prevention / Precaution", java.util.Arrays.asList("Preventive dose before expected trigger", "Important event / work / school", "Traveling or disrupted schedule", "Poor sleep", "Anxiety about flare-up", "High activity day"));
        reasonsMap.put("Medication Schedule Issue", java.util.Arrays.asList("Missed previous dose", "Delayed previous dose", "Adjusting medication schedule", "Trying to regain symptom control", "Doctor-directed extra dose"));
        reasonsMap.put("Unsure", java.util.Arrays.asList("Not sure why", "Multiple possible causes"));
        reasonsMap.put("Manual Entry / Other", new ArrayList<>());

        Map<String, List<String>> vitaminOptions = new java.util.HashMap<>();
        vitaminOptions.put("Vitamins", java.util.Arrays.asList("Vit A", "Vit B3", "Vit B6", "Vit B12", "Vit C", "Vit D", "Vit E", "Vit K", "Biotin", "Omega 3", "Folic Acid", "Zinc"));

        AlertDialog dialog = builder.create();
        
        refreshReasonUI(categoriesContainer, reasonsMap, vitaminOptions, selectedMain, selectedSub, selectedVitamin, otherNote);

        btnClear.setOnClickListener(v -> {
            selectedMain[0] = "";
            selectedSub[0] = "";
            selectedVitamin[0] = "";
            otherNote[0] = "";
            updateReasonTextView(toggleView, "", "", "", "");
            dialog.dismiss();
        });

        btnOk.setOnClickListener(v -> {
            updateReasonTextView(toggleView, selectedMain[0], selectedSub[0], selectedVitamin[0], otherNote[0]);
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

            mainLayout.addView(rbMain);
            mainLayout.addView(subContainer);
            container.addView(mainLayout);

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
                    etOther.setHint("Enter reason...");
                    etOther.setTextColor(Color.WHITE);
                    etOther.setHintTextColor(Color.GRAY);
                    etOther.setText(otherNote[0]);
                    etOther.addTextChangedListener(new android.text.TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            otherNote[0] = s.toString();
                        }
                        public void afterTextChanged(android.text.Editable s) {}
                    });
                    subContainer.addView(etOther);
                }
            }
        }
    }

    private void setupListeners() {
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveData(); }
        };
        editName.addTextChangedListener(tw); editAge.addTextChangedListener(tw); editDiagnosis.addTextChangedListener(tw); 
        editChronic.addTextChangedListener(tw);
        cbF.setOnCheckedChangeListener((b, is) -> { if(is) cbM.setChecked(false); sectionFemale.setVisibility(is?View.VISIBLE:View.GONE); saveData(); });
        cbM.setOnCheckedChangeListener((b, is) -> { if(is) cbF.setChecked(false); sectionFemale.setVisibility(View.GONE); saveData(); });
        cbXy.setOnCheckedChangeListener((b, is) -> { if(is) { cbXn.setChecked(false); showXolairFreqDialog(); } else { displayXolairFreq.setVisibility(View.GONE); } saveData(); });
        cbXn.setOnCheckedChangeListener((b, is) -> { if(is) { cbXy.setChecked(false); displayXolairFreq.setVisibility(View.GONE); } saveData(); });
        cbPregYes.setOnCheckedChangeListener((b, is) -> { if(is) { cbPregNo.setChecked(false); rowPregMonth.setVisibility(View.VISIBLE); } else rowPregMonth.setVisibility(View.GONE); saveData(); });
        cbPregNo.setOnCheckedChangeListener((b, is) -> { if(is) cbPregYes.setChecked(false); saveData(); });
        
        cbMensOn.setOnCheckedChangeListener((b, is) -> { if(is) { cbMensOff.setChecked(false); CycleReminderManager.scheduleDailyCheck(requireContext()); } saveData(); });
        cbMensOff.setOnCheckedChangeListener((b, is) -> { if(is) { cbMensOn.setChecked(false); /* Logic handled in manager check */ } saveData(); });
    }

    private void saveData() {
        prefs.edit().putString("name", editName.getText().toString()).putString("age", editAge.getText().toString())
                .putString("diagnosis", editDiagnosis.getText().toString())
                .putString("chronic", editChronic.getText().toString())
                .putBoolean("sex_f", cbF.isChecked()).putBoolean("sex_m", cbM.isChecked())
                .putBoolean("xolair_y", cbXy.isChecked()).putBoolean("xolair_n", cbXn.isChecked())
                .putString("xolair_freq", displayXolairFreq.getText().toString())
                .putBoolean("preg_y", cbPregYes.isChecked()).putBoolean("preg_n", cbPregNo.isChecked())
                .putBoolean("mens_on", cbMensOn.isChecked()).putBoolean("mens_off", cbMensOff.isChecked())
                .putLong("mens_start", startTs).putLong("mens_end", endTs)
                .putString("preg_month", editPregMonth.getText().toString())
                .putString("recurring_meds", gson.toJson(recurringMeds))
                .putString("ah_list", gson.toJson(ahList))
                .putString("cortisone_list", gson.toJson(cortisoneList))
                .apply();
    }

    private void checkAndIncrementPregnancy() {
        if (!cbPregYes.isChecked()) return;
        long last = prefs.getLong("preg_last_update", 0);
        if (last == 0) { prefs.edit().putLong("preg_last_update", System.currentTimeMillis()).apply(); return; }
        long days = (System.currentTimeMillis() - last) / (1000 * 60 * 60 * 24);
        if (days >= 30) {
            int cur = 1; try { cur = Integer.parseInt(editPregMonth.getText().toString()); } catch(Exception e){}
            if (cur < 9) {
                int next = Math.min(9, cur + (int)(days/30));
                editPregMonth.setText(String.valueOf(next));
                prefs.edit().putString("preg_month", String.valueOf(next)).putLong("preg_last_update", System.currentTimeMillis()).apply();
            }
        }
    }
}