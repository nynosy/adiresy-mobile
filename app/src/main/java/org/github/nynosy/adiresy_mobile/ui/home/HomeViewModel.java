package org.github.nynosy.adiresy_mobile.ui.home;

import android.annotation.SuppressLint;
import android.app.Application;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.github.nynosy.adiresy_mobile.BuildConfig;
import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;

public class HomeViewModel extends AndroidViewModel {

    public enum LocationState { IDLE, REQUESTING, ACQUIRED, FAILED }

    private static final long GPS_TIMEOUT_MS = 30_000;
    /** GPS struggles indoors — exactly when a user wants their current building's
     *  address. If it hasn't produced a fix shortly, also listen on the network
     *  provider (WiFi/cell — coarser, but works indoors) and take whichever
     *  source responds first. */
    private static final long NETWORK_FALLBACK_DELAY_MS = 8_000;

    // Debug-only: MockLocationReceiver posts here; observed in constructor.
    private static final MutableLiveData<double[]> sDebugInject = new MutableLiveData<>();

    /** Called from MockLocationReceiver to simulate a GPS fix (debug builds only). */
    public static void debugInjectCoords(double lat, double lng) {
        sDebugInject.postValue(new double[]{lat, lng});
    }

    private final MutableLiveData<LocationState> locationState = new MutableLiveData<>(LocationState.IDLE);
    private final MutableLiveData<Location>      location      = new MutableLiveData<>();
    private final MutableLiveData<double[]>      nearbyLocation = new MutableLiveData<>();

    private final AdiresyRepository repository;
    private Observer<double[]> debugObserver;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Set<String> activeProviders = new HashSet<>();
    private final Handler  timeoutHandler  = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = () -> {
        stopLocationUpdates();
        locationState.setValue(LocationState.FAILED);
    };
    private Runnable networkFallbackRunnable;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = AdiresyRepository.getInstance(application);
        if (BuildConfig.DEBUG) {
            debugObserver = coords -> {
                if (coords == null) return;
                sDebugInject.postValue(null); // consume
                Location loc = new Location("debug");
                loc.setLatitude(coords[0]);
                loc.setLongitude(coords[1]);
                loc.setAltitude(1300);
                loc.setAccuracy(5f);
                loc.setTime(System.currentTimeMillis());
                loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                location.postValue(loc);
                nearbyLocation.postValue(coords);
                locationState.postValue(LocationState.ACQUIRED);
            };
            sDebugInject.observeForever(debugObserver);
        }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    public LiveData<LocationState> getLocationState() { return locationState; }
    public LiveData<Location>      getLocation()      { return location; }

    @SuppressLint("MissingPermission")
    public void startLocating() {
        locationState.setValue(LocationState.REQUESTING);

        locationManager = (LocationManager) getApplication()
                .getSystemService(android.content.Context.LOCATION_SERVICE);

        boolean gpsEnabled     = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gpsEnabled && !networkEnabled) {
            locationState.setValue(LocationState.FAILED);
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location loc) {
                stopLocationUpdates();
                location.setValue(loc);
                locationState.setValue(LocationState.ACQUIRED);
                loadNearbyForLocation(loc);
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                activeProviders.remove(provider);
                if (activeProviders.isEmpty()) {
                    stopLocationUpdates();
                    locationState.setValue(LocationState.FAILED);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        String primaryProvider = gpsEnabled
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;

        // Serve a cached fix immediately if one is available
        Location lastKnown = locationManager.getLastKnownLocation(primaryProvider);
        if (lastKnown != null) {
            location.setValue(lastKnown);
            locationState.setValue(LocationState.ACQUIRED);
            loadNearbyForLocation(lastKnown);
            return;
        }

        // No cached fix — register for updates and start the timeout guard
        registerProvider(primaryProvider);
        timeoutHandler.postDelayed(timeoutRunnable, GPS_TIMEOUT_MS);

        // GPS-only would leave indoor users waiting the full timeout with no
        // fix at all; bring in the network provider partway through instead.
        if (gpsEnabled && networkEnabled) {
            networkFallbackRunnable = () -> {
                networkFallbackRunnable = null;
                registerProvider(LocationManager.NETWORK_PROVIDER);
            };
            timeoutHandler.postDelayed(networkFallbackRunnable, NETWORK_FALLBACK_DELAY_MS);
        }
    }

    private void registerProvider(String provider) {
        activeProviders.add(provider);
        locationManager.requestLocationUpdates(provider, 0, 0, locationListener,
                Looper.getMainLooper());
    }

    public void cancelLocating() {
        stopLocationUpdates();
        if (locationState.getValue() == LocationState.REQUESTING) {
            locationState.setValue(LocationState.IDLE);
        }
    }

    private void stopLocationUpdates() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        if (networkFallbackRunnable != null) {
            timeoutHandler.removeCallbacks(networkFallbackRunnable);
            networkFallbackRunnable = null;
        }
        activeProviders.clear();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    // ── Tap-to-identify (reverse geocode a map tap) ───────────────────────────

    private final MutableLiveData<double[]> tapLocation = new MutableLiveData<>();

    public void identifyAt(double lat, double lng) {
        tapLocation.setValue(new double[]{lat, lng});
    }

    public LiveData<Result<AddressEntity>> getTapResult() {
        return Transformations.switchMap(tapLocation, coords ->
                coords != null
                        ? repository.reverseGeocode(coords[0], coords[1])
                        : new MutableLiveData<>());
    }

    // ── Nearby buildings ──────────────────────────────────────────────────────

    public LiveData<Result<List<AddressEntity>>> getNearbyBuildings() {
        return Transformations.switchMap(nearbyLocation, coords ->
                coords != null
                        ? repository.nearbyBuildings(coords[0], coords[1])
                        : new MutableLiveData<>());
    }

    private void loadNearbyForLocation(Location loc) {
        nearbyLocation.postValue(new double[]{loc.getLatitude(), loc.getLongitude()});
    }

    @Override
    protected void onCleared() {
        stopLocationUpdates();
        if (BuildConfig.DEBUG && debugObserver != null) {
            sDebugInject.removeObserver(debugObserver);
        }
    }
}
