package org.github.nynosy.adiresy_mobile;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import org.github.nynosy.adiresy_mobile.databinding.ActivityMainBinding;
import org.github.nynosy.adiresy_mobile.ui.mainmap.MainMapFragment;
import org.github.nynosy.adiresy_mobile.ui.saved.SavedFragment;
import org.github.nynosy.adiresy_mobile.ui.saved.SharedMapViewModel;
import org.github.nynosy.adiresy_mobile.ui.settings.LanguageChooserActivity;
import org.github.nynosy.adiresy_mobile.ui.about.AboutFragment;
import org.github.nynosy.adiresy_mobile.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private int currentTabId = R.id.nav_map;
    private SharedMapViewModel sharedMapViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AppPrefs.get(this).isFirstRun()) {
            startActivity(new Intent(this, LanguageChooserActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedMapViewModel = new ViewModelProvider(this).get(SharedMapViewModel.class);

        setupBackNavigation();
        setupBottomNav();
        observeBookmarkFocus();

        if (savedInstanceState == null) {
            int startTab = tabIdFromExtra(getIntent().getStringExtra("tab"));
            binding.bottomNav.setSelectedItemId(startTab);
            currentTabId = startTab;
            showFragment(fragmentForId(startTab), startTab);
        } else {
            currentTabId = savedInstanceState.getInt("current_tab", R.id.nav_map);
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                if (currentTabId != R.id.nav_map) {
                    binding.bottomNav.setSelectedItemId(R.id.nav_map);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    /** When a bookmark is tapped in the detail screen, switch to Map tab and let
     *  MainMapFragment pick up the focusBookmark LiveData. */
    private void observeBookmarkFocus() {
        sharedMapViewModel.focusBookmark.observe(this, bookmark -> {
            if (bookmark == null) return;
            binding.bottomNav.setSelectedItemId(R.id.nav_map);
        });
    }

    private int tabIdFromExtra(String tab) {
        if ("settings".equals(tab)) return R.id.nav_settings;
        if ("saved".equals(tab))    return R.id.nav_saved;
        return R.id.nav_map;
    }

    private Fragment fragmentForId(int id) {
        if (id == R.id.nav_settings) return new SettingsFragment();
        if (id == R.id.nav_about)    return new AboutFragment();
        if (id == R.id.nav_saved)    return new SavedFragment();
        return new MainMapFragment();
    }

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentTabId && getSupportFragmentManager().getBackStackEntryCount() == 0)
                return false;
            getSupportFragmentManager().popBackStack(null,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            showFragment(fragmentForId(id), id);
            return true;
        });
    }

    private void showFragment(Fragment fragment, int tabId) {
        currentTabId = tabId;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_tab", currentTabId);
    }
}
