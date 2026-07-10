package org.github.nynosy.adiresy_mobile;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;

import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import org.github.nynosy.adiresy_mobile.i18n.LocaleHelper;

public class AdiresyApplication extends Application {

    private static final String TAG = "AdiresyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        // Required before any MapView is inflated. Token is unused for self-hosted/PMTiles.
        MapLibre.getInstance(this, "adiresy", WellKnownTileServer.MapLibre);
        applyTheme();
        LocaleHelper.applyFromPrefs(this);
        AdiresyRepository.getInstance(this).evictExpiredCache();
        if (BuildConfig.DEBUG) registerMockLocationReceiver();
    }

    private void registerMockLocationReceiver() {
        try {
            Class<?> cls = Class.forName(
                    "org.github.nynosy.adiresy_mobile.debug.MockLocationReceiver");
            BroadcastReceiver receiver = (BroadcastReceiver) cls.getDeclaredConstructor()
                    .newInstance();
            registerReceiver(receiver,
                    new IntentFilter("org.github.nynosy.adiresy_mobile.MOCK_LOCATION"));
        } catch (Exception e) {
            Log.w(TAG, "MockLocationReceiver registration failed", e);
        }
    }

    private void applyTheme() {
        String theme = AppPrefs.get(this).getTheme();
        int mode;
        switch (theme) {
            case AppPrefs.THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case AppPrefs.THEME_DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
