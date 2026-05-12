package com.example.ayasantihistaminestracker;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class XolairLogFragment extends Fragment {

    private LinearLayout xolairLogContainer, containerAlternativeInput;
    private EditText editAlternativeName;
    private Button btnAlternative;
    private SharedPreferences sharedPreferences;
    private String lastClearedLogs = "";
    private TextView btnUndoClear;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_xolair_log, container, false);
        xolairLogContainer = view.findViewById(R.id.xolair_log_container);
        containerAlternativeInput = view.findViewById(R.id.container_alternative_input);
        editAlternativeName = view.findViewById(R.id.edit_alternative_name);
        btnAlternative = view.findViewById(R.id.button_xolair_alternative);
        
        Button btnManual = view.findViewById(R.id.button_xolair_manual);
        Button btn300 = view.findViewById(R.id.button_xolair_300);
        Button btn150 = view.findViewById(R.id.button_xolair_150);
        Button btnSaveAlt = view.findViewById(R.id.button_save_alternative);
        Button btnExport = view.findViewById(R.id.button_export_xolair);
        Button btnClear = view.findViewById(R.id.button_clear_xolair);
        Button btnReset = view.findViewById(R.id.button_reset_xolair_all);
        btnUndoClear = view.findViewById(R.id.button_undo_clear_xolair);
        
        sharedPreferences = requireActivity().getSharedPreferences("PillLogs", Context.MODE_PRIVATE);
        
        btnManual.setOnClickListener(v -> showManualEntryDialog());
        btn300.setOnClickListener(v -> logXolair("300 mg", new Date()));
        btn150.setOnClickListener(v -> logXolair("150 mg", new Date()));
        
        btnAlternative.setOnClickListener(v -> {
            btnAlternative.setVisibility(View.GONE);
            containerAlternativeInput.setVisibility(View.VISIBLE);
        });

        btnSaveAlt.setOnClickListener(v -> {
            String alt = editAlternativeName.getText().toString().trim();
            if (!alt.isEmpty()) {
                logXolair(alt, new Date());
                editAlternativeName.setText("");
                containerAlternativeInput.setVisibility(View.GONE);
                btnAlternative.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
            }
        });

        btnExport.setOnClickListener(v -> showExportDialog());
        btnClear.setOnClickListener(v -> clearLatest());
        btnReset.setOnClickListener(v -> resetAll());
        btnUndoClear.setOnClickListener(v -> undoClear());
        
        return view;
    }

    private void showManualEntryDialog() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(getContext(), (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);

            new TimePickerDialog(getContext(), (timeView, hour, min) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, min);
                selected.set(Calendar.SECOND, 0);

                String[] options = {"Xolair 300 mg", "Xolair 150 mg", "Alternative"};
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Select Treatment")
                        .setItems(options, (dialog, which) -> {
                            if (which == 2) {
                                showAlternativeManualInputDialog(selected.getTime());
                            } else {
                                String dose = which == 0 ? "300 mg" : "150 mg";
                                logXolair(dose, selected.getTime());
                            }
                        }).show();

            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showAlternativeManualInputDialog(Date date) {
        EditText input = new EditText(getContext());
        input.setHint("Enter alternative name...");
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Alternative Treatment")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String alt = input.getText().toString().trim();
                    if (!alt.isEmpty()) logXolair(alt, date);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        displayLogs();
    }

    private void logXolair(String dose, Date date) {
        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        String entry;
        if (dose.equals("300 mg") || dose.equals("150 mg")) {
            entry = fullFormat.format(date) + " - Xolair dose " + dose;
        } else {
            entry = fullFormat.format(date) + " - " + dose;
        }
        
        String logs = sharedPreferences.getString("xolair_logs", "");
        if (logs.isEmpty()) logs = entry;
        else logs = entry + "\n" + logs;
        
        sharedPreferences.edit().putString("xolair_logs", logs).apply();
        displayLogs();
        Toast.makeText(getContext(), (dose.contains("mg") ? "Xolair " : "") + dose + " logged!", Toast.LENGTH_SHORT).show();
    }

    private void displayLogs() {
        xolairLogContainer.removeAllViews();
        String logs = sharedPreferences.getString("xolair_logs", "");
        if (logs.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No entries found.");
            empty.setTextColor(Color.WHITE);
            xolairLogContainer.addView(empty);
            return;
        }

        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

        String[] arr = logs.split("\n");
        for (int i = 0; i < arr.length; i++) {
            final int index = i;
            String log = arr[i];
            if (log.isEmpty()) continue;
            
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, 0);

            TextView tv = new TextView(getContext());
            String textToSet = log;
            Date date = null;
            try {
                String[] parts = log.split(" - ");
                if (parts.length >= 2) {
                    String dateStr = parts[0] + " - " + parts[1];
                    date = fullFormat.parse(dateStr);
                    if (date != null) {
                        textToSet = timeFormat.format(date) + " - " + dateFormat.format(date);
                        if (parts.length >= 3) {
                            textToSet += " - " + parts[2];
                        }
                    }
                }
            } catch (Exception ignored) {}
            
            SpannableStringBuilder ssb = new SpannableStringBuilder("• " + textToSet);
            int textColor = Color.parseColor("#C9A7FF"); 
            
            if (date != null && i + 1 < arr.length) {
                try {
                    String prevLog = arr[i+1];
                    String[] prevParts = prevLog.split(" - ");
                    if (prevParts.length >= 2) {
                        String prevDateStr = prevParts[0] + " - " + prevParts[1];
                        Date prevDate = fullFormat.parse(prevDateStr);
                        if (prevDate != null) {
                            long diff = date.getTime() - prevDate.getTime();
                            long days = TimeUnit.MILLISECONDS.toDays(diff);
                            if (days < 14) textColor = Color.parseColor("#6A1B9A"); 
                            String diffText = " (+" + days + " days)";
                            int start = ssb.length();
                            ssb.append(diffText);
                            ssb.setSpan(new RelativeSizeSpan(0.75f), start, ssb.length(), 0);
                            ssb.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.text_secondary_grey)), start, ssb.length(), 0);
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            tv.setText(ssb); tv.setTextColor(textColor); tv.setTextSize(16);
            row.addView(tv);

            TextView editBtn = new TextView(getContext());
            editBtn.setText(" [" + getString(R.string.edit_label) + "]");
            editBtn.setTextColor(Color.GRAY); editBtn.setTextSize(12); editBtn.setPadding(16, 0, 0, 0);
            editBtn.setOnClickListener(v -> showEditDialog(index, log));
            row.addView(editBtn);

            xolairLogContainer.addView(row);
        }
    }

    private void showEditDialog(int index, String oldLog) {
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        try {
            String[] parts = oldLog.split(" - ");
            if (parts.length < 2) return;
            String dateStr = parts[0] + " - " + parts[1];
            Date d = fullF.parse(dateStr);
            Calendar cal = Calendar.getInstance(); if(d != null) cal.setTime(d);
            new DatePickerDialog(getContext(), (v1, y, m, day) -> {
                cal.set(y, m, day);
                new TimePickerDialog(getContext(), (v2, hr, min) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hr); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                    
                    String[] options = {"Xolair 300 mg", "Xolair 150 mg", "Alternative"};
                    new android.app.AlertDialog.Builder(getContext())
                            .setTitle("Edit Entry")
                            .setItems(options, (dialog, which) -> {
                                if (which == 2) {
                                    showAlternativeEditInputDialog(index, cal.getTime());
                                } else {
                                    String dose = which == 0 ? "300 mg" : "150 mg";
                                    String newLog = fullF.format(cal.getTime()) + " - Xolair dose " + dose;
                                    updateLogAt(index, newLog);
                                }
                            }).show();
                            
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        } catch (Exception ex) {}
    }

    private void showAlternativeEditInputDialog(int index, Date date) {
        SimpleDateFormat fullF = new SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US);
        EditText input = new EditText(getContext());
        input.setHint("Enter alternative name...");
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Alternative Treatment")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String alt = input.getText().toString().trim();
                    if (!alt.isEmpty()) {
                        String newLog = fullF.format(date) + " - " + alt;
                        updateLogAt(index, newLog);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLogAt(int index, String newLog) {
        String logs = sharedPreferences.getString("xolair_logs", "");
        String[] arr = logs.split("\n");
        if (index >= 0 && index < arr.length) {
            arr[index] = newLog;
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(s).append("\n");
            sharedPreferences.edit().putString("xolair_logs", sb.toString().trim()).apply();
            displayLogs();
        }
    }

    private void clearLatest() {
        String logs = sharedPreferences.getString("xolair_logs", "");
        if (!logs.isEmpty()) {
            lastClearedLogs = logs;
            btnUndoClear.setVisibility(View.VISIBLE);
            String[] arr = logs.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < arr.length; i++) sb.append(arr[i]).append("\n");
            sharedPreferences.edit().putString("xolair_logs", sb.toString().trim()).apply();
            displayLogs();
            Toast.makeText(getContext(), "Entry cleared", Toast.LENGTH_SHORT).show();
        }
    }

    private void undoClear() {
        if (lastClearedLogs.isEmpty()) return;
        sharedPreferences.edit().putString("xolair_logs", lastClearedLogs).apply();
        lastClearedLogs = "";
        btnUndoClear.setVisibility(View.GONE);
        displayLogs();
        Toast.makeText(getContext(), "Cleared logs restored!", Toast.LENGTH_SHORT).show();
    }

    private void resetAll() {
        String logs = sharedPreferences.getString("xolair_logs", "");
        if (!logs.isEmpty()) {
            lastClearedLogs = logs;
            btnUndoClear.setVisibility(View.VISIBLE);
            sharedPreferences.edit().remove("xolair_logs").apply();
            displayLogs();
            Toast.makeText(getContext(), "All logs cleared", Toast.LENGTH_SHORT).show();
        }
    }

    private void showExportDialog() {
        String[] options = {getString(R.string.export_as_csv), getString(R.string.export_as_excel)};
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.export_choice_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) exportToCsv();
                    else exportToExcel();
                })
                .show();
    }

    private void exportToCsv() {
        String logs = sharedPreferences.getString("xolair_logs", "");
        if (logs.isEmpty()) return;
        try {
            File file = new File(requireContext().getCacheDir(), "xolair_log_export.csv");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write("Date,Time,Dose\n".getBytes());
            String[] arr = logs.split("\n");
            for (String log : arr) {
                if (log.isEmpty()) continue;
                String[] parts = log.split(" - ");
                if (parts.length >= 3) {
                    fos.write((parts[1] + "," + parts[0] + "," + parts[2] + "\n").getBytes());
                }
            }
            fos.close();
            share(file, "text/csv");
        } catch (Exception e) {}
    }

    private void exportToExcel() {
        String logs = sharedPreferences.getString("xolair_logs", "");
        if (logs.isEmpty()) return;
        try {
            XSSFWorkbook wb = new XSSFWorkbook(); Sheet s = wb.createSheet("Xolair");
            Row h = s.createRow(0); h.createCell(0).setCellValue("Date"); h.createCell(1).setCellValue("Time"); h.createCell(2).setCellValue("Dose");
            String[] arr = logs.split("\n"); int rowIdx = 1;
            for (String log : arr) {
                if (log.isEmpty()) continue;
                String[] parts = log.split(" - "); 
                if (parts.length >= 3) { 
                    Row r = s.createRow(rowIdx++);
                    r.createCell(0).setCellValue(parts[1]); 
                    r.createCell(1).setCellValue(parts[0]); 
                    r.createCell(2).setCellValue(parts[2]); 
                }
            }
            File f = new File(requireContext().getCacheDir(), "xolair_log_export.xlsx");
            FileOutputStream fos = new FileOutputStream(f); wb.write(fos); fos.close(); wb.close();
            share(f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {}
    }

    private void share(File f, String type) {
        Uri path = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_SEND); i.setType(type); i.putExtra(Intent.EXTRA_STREAM, path);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Export Xolair Logs"));
    }
}