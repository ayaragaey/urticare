package com.example.ayasantihistaminestracker;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class LogsContainerFragment extends Fragment implements MainActivity.TitleProvider {

    private TextView tabPills, tabFlares, tabXolair;
    private int selectedColor;

    @Override
    public String getTitle() {
        return getString(R.string.tab_logs);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs_container, container, false);
        
        selectedColor = getResources().getColor(R.color.pill_button_blue);

        tabPills = view.findViewById(R.id.tab_pills);
        tabFlares = view.findViewById(R.id.tab_flares);
        tabXolair = view.findViewById(R.id.tab_xolair);

        tabPills.setOnClickListener(v -> switchTab(0));
        tabFlares.setOnClickListener(v -> switchTab(1));
        tabXolair.setOnClickListener(v -> switchTab(2));

        // Default to Pills Log
        switchTab(0);

        return view;
    }

    private void switchTab(int index) {
        tabPills.setTextColor(index == 0 ? selectedColor : Color.WHITE);
        tabFlares.setTextColor(index == 1 ? selectedColor : Color.WHITE);
        tabXolair.setTextColor(index == 2 ? selectedColor : Color.WHITE);

        Fragment fragment;
        switch (index) {
            case 1: fragment = new FlareLogFragment(); break;
            case 2: fragment = new XolairLogFragment(); break;
            default: fragment = new LogFragment(); break;
        }

        getChildFragmentManager().beginTransaction()
                .replace(R.id.logs_content_container, fragment)
                .commit();
    }
}