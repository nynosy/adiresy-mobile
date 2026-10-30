package org.github.nynosy.adiresy_mobile;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;

import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;
import org.github.nynosy.adiresy_mobile.data.ListingSyncWorker;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import org.github.nynosy.adiresy_mobile.i18n.LocaleHelper;

import timber.log.Timber;

public class AdiresyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Planted unconditionally, not just in debug: most of the Log.* call
        // sites this replaced had no BuildConfig.DEBUG guard and were visible
        // in release builds too (e.g. TileDownloadWorker's download-failure
        // logging) — matching that means Timber must forward in release as
        // well. The handful of call sites that *were* debug-gated keep their
        // own `if (BuildConfig.DEBUG)` around the Timber call.
        Timber.plant(new Timber.DebugTree());
        // Required before any MapView is inflated. Token is unused for self-hosted/PMTiles.
        MapLibre.getInstance(this, "adiresy", WellKnownTileServer.MapLibre);
        applyTheme();
        LocaleHelper.applyFromPrefs(this);
        AdiresyRepository.getInstance(this).evictExpiredCache();
        ListingSyncWorker.scheduleImmediateAndPeriodic(this);
        if (BuildConfig.DEBUG) registerMockLocationReceiver();
    }

    private void registerMockLocationReceiver() {
        try {
            Class<?> cls = Class.forName(
                    "org.github.nynosy.adiresy_mobile.debug.MockLocationReceiver");
            BroadcastReceiver receiver = (BroadcastReceiver) cls.getDeclaredConstructor()
                    .newInstance();
            // EXPORTED to match the static <receiver> in the debug manifest — both
            // exist so `adb shell am broadcast` can reach this from outside the app.
            ContextCompat.registerReceiver(this, receiver,
                    new IntentFilter("org.github.nynosy.adiresy_mobile.MOCK_LOCATION"),
                    ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception e) {
            Timber.w(e, "MockLocationReceiver registration failed");
        }
    }

    private void applyTheme() {
        String theme = AppPrefs.get(this).getTheme();
        int mode = switch (theme) {
            case AppPrefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case AppPrefs.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES;
            default                   -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
