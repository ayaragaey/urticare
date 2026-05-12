package com.example.ayasantihistaminestracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private View btnGlobalBack;
    private TextView txtGlobalTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tabLayout = findViewById(R.id.tab_layout);
        btnGlobalBack = findViewById(R.id.btn_global_back);
        txtGlobalTitle = findViewById(R.id.txt_global_title);

        btnGlobalBack.setOnClickListener(v -> handleBackNavigation());
        
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateBackNavigationUI);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                switch (tab.getPosition()) {
                    case 0: loadFragment(new TrackerFragment(), false); break;
                    case 1: loadFragment(new ProfileFragment(), false); break;
                    case 2: loadFragment(new LogsContainerFragment(), false); break;
                    case 3: loadFragment(new OrganizerFragment(), false); break;
                    case 4: loadFragment(new ConsumablesScreenFragment(), false); break;
                    case 5: loadFragment(new InsightsFragment(), false); break;
                }
                updateBackNavigationUI();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {
                if (tab.getPosition() == 2) {
                    getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    loadFragment(new LogsContainerFragment(), false);
                }
                updateBackNavigationUI();
            }
        });

        handleIntent(getIntent());
        updateBackNavigationUI();
    }

    private void handleBackNavigation() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment currentFragment = fm.findFragmentById(R.id.fragment_container);
        
        if (currentFragment instanceof BackHandler && ((BackHandler) currentFragment).handleBack()) {
            updateBackNavigationUI();
            return;
        }

        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else if (tabLayout.getSelectedTabPosition() != 0) {
            tabLayout.getTabAt(0).select();
        } else {
            // At root (TrackerFragment), close app
            finish();
        }
    }

    public void updateBackNavigationUI() {
        FragmentManager fm = getSupportFragmentManager();
        boolean hasBackStack = fm.getBackStackEntryCount() > 0;
        boolean notAtRootTab = tabLayout.getSelectedTabPosition() != 0;
        
        Fragment currentFragment = fm.findFragmentById(R.id.fragment_container);
        boolean fragmentWantsBack = currentFragment instanceof BackHandler && ((BackHandler) currentFragment).canGoBack();

        // Show back button if we can go back in stack OR if we can go back to first tab OR if fragment has sub-state
        if (hasBackStack || notAtRootTab || fragmentWantsBack) {
            btnGlobalBack.setVisibility(View.VISIBLE);
        } else {
            btnGlobalBack.setVisibility(View.GONE);
        }
        
        // Update title based on current fragment
        if (currentFragment instanceof TitleProvider) {
            txtGlobalTitle.setText(((TitleProvider) currentFragment).getTitle());
            txtGlobalTitle.setVisibility(View.VISIBLE);
        } else {
            txtGlobalTitle.setVisibility(View.GONE);
        }
    }

    public interface TitleProvider {
        String getTitle();
    }

    public interface BackHandler {
        boolean handleBack();
        boolean canGoBack();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "profile".equals(intent.getStringExtra("target"))) {
            tabLayout.getTabAt(1).select();
        } else {
            loadFragment(new TrackerFragment(), false);
        }
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        if (addToBackStack) transaction.addToBackStack(null);
        transaction.commit();
    }
}