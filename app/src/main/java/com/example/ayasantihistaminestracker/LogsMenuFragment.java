package com.example.ayasantihistaminestracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class LogsMenuFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs_menu, container, false);

        view.findViewById(R.id.button_pills_log).setOnClickListener(v -> loadFragment(new LogFragment()));
        view.findViewById(R.id.button_flare_log).setOnClickListener(v -> loadFragment(new FlareLogFragment()));
        view.findViewById(R.id.button_xolair_log).setOnClickListener(v -> loadFragment(new XolairLogFragment()));
        
        view.findViewById(R.id.button_back).setOnClickListener(v -> {
            com.google.android.material.tabs.TabLayout tabLayout = getActivity().findViewById(R.id.tab_layout);
            if (tabLayout != null) {
                tabLayout.getTabAt(0).select();
            }
        });

        return view;
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}