package org.github.nynosy.adiresy_mobile.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.github.nynosy.adiresy_mobile.ui.home.HomeViewModel;

/**
 * Debug-only receiver. Simulate a GPS fix without LocationManager mock location:
 *
 *   adb shell am broadcast -a org.github.nynosy.adiresy_mobile.MOCK_LOCATION \
 *       --es lat "-18.9148" --es lng "47.5244"
 *
 * The app must be running (foreground or background process alive) for the
 * dynamically registered receiver to fire.
 */
public class MockLocationReceiver extends BroadcastReceiver {

    public static final String ACTION = "org.github.nynosy.adiresy_mobile.MOCK_LOCATION";
    private static final String TAG = "MockLocation";

    @Override
    public void onReceive(Context context, Intent intent) {
        double lat, lng;
        try {
            lat = Double.parseDouble(intent.getStringExtra("lat"));
            lng = Double.parseDouble(intent.getStringExtra("lng"));
        } catch (Exception e) {
            Log.w(TAG, "Missing or invalid lat/lng string extras");
            return;
        }
        HomeViewModel.debugInjectCoords(lat, lng);
        Log.d(TAG, "Debug location injected: " + lat + ", " + lng);
    }
}
