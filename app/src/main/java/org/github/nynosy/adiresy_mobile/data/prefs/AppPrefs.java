package org.github.nynosy.adiresy_mobile.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.GeneralSecurityException;
import java.io.IOException;

public class AppPrefs {

    private static final String TAG            = "AppPrefs";
    private static final String PREF_FILE         = "adiresy_prefs";
    private static final String SECURE_PREF_FILE   = "adiresy_secure_prefs";
    private static final String KEY_FIRST_RUN         = "first_run";
    private static final String KEY_LANGUAGE          = "language";
    private static final String KEY_THEME             = "theme";
    private static final String KEY_API_KEY           = "api_key";
    private static final String KEY_DATA_VERSION      = "data_version";
    private static final String KEY_DATA_PATH         = "data_path";
    private static final String KEY_TILE_TIER         = "tile_tier";
    private static final String KEY_BUILDINGS_PATH    = "buildings_path";
    private static final String KEY_BUILDINGS_VERSION = "buildings_version";
    private static final String KEY_POI_PATH          = "poi_path";
    private static final String KEY_POI_VERSION       = "poi_version";
    private static final String KEY_BOUNDARIES_PATH   = "boundaries_path";
    private static final String KEY_BOUNDARIES_VERSION = "boundaries_version";

    public static final String THEME_AUTO  = "auto";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK  = "dark";

    public static final String TIER_Z12 = "z12";
    public static final String TIER_Z13 = "z13";
    public static final String TIER_Z14 = "z14";

    private static volatile AppPrefs instance;

    private final SharedPreferences prefs;
    private final SharedPreferences securePrefs;

    private AppPrefs(Context context) {
        Context appCtx = context.getApplicationContext();
        prefs = appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        securePrefs = createSecurePrefs(appCtx);
    }

    private static SharedPreferences createSecurePrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Fall back to a plain, still device-private file. The device
            // token is per-install and low-value (governs only its own rate
            // limit bucket), so this is a degraded-but-safe fallback, not a
            // security hole.
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e);
            return context.getSharedPreferences(SECURE_PREF_FILE, Context.MODE_PRIVATE);
        }
    }

    public static AppPrefs get(Context context) {
        if (instance == null) {
            synchronized (AppPrefs.class) {
                if (instance == null) instance = new AppPrefs(context);
            }
        }
        return instance;
    }

    // ── First run ─────────────────────────────────────────────────────────────

    public boolean isFirstRun() {
        return prefs.getBoolean(KEY_FIRST_RUN, true);
    }

    public void setFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply();
    }

    // ── Language ──────────────────────────────────────────────────────────────

    /** Returns the stored language code ("en", "fr", "mg") or "" if not set. */
    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "");
    }

    public void setLanguage(String languageCode) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    /** Returns one of THEME_AUTO / THEME_LIGHT / THEME_DARK. */
    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_AUTO);
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
    }

    // ── API key ───────────────────────────────────────────────────────────────
    // Per-install device token from POST /api/v1/auth/device/register/ (see
    // DeviceAuthManager) — not a single key baked into the build. Stored in
    // EncryptedSharedPreferences since it's a bearer credential.

    public String getApiKey() {
        return securePrefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        securePrefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    // ── Offline map data — national Z12 ──────────────────────────────────────

    /** Version string of the national tile download, e.g. "2026-Q3". Empty if not downloaded. */
    public String getDataVersion() {
        return prefs.getString(KEY_DATA_VERSION, "");
    }

    public void setDataVersion(String version) {
        prefs.edit().putString(KEY_DATA_VERSION, version).apply();
    }

    /** Absolute path to the downloaded national Z12 .pmtiles file, or "" if not present. */
    public String getDataPath() {
        return prefs.getString(KEY_DATA_PATH, "");
    }

    public void setDataPath(String path) {
        prefs.edit().putString(KEY_DATA_PATH, path).apply();
    }

    public boolean hasOfflineData() {
        return !getDataPath().isEmpty();
    }

    // ── Tile quality tier ─────────────────────────────────────────────────────

    /** Returns the selected tier key: "z12", "z13", or "z14". Defaults to "z12". */
    public String getTileTier() {
        return prefs.getString(KEY_TILE_TIER, TIER_Z12);
    }

    public void setTileTier(String tier) {
        prefs.edit().putString(KEY_TILE_TIER, tier).apply();
    }

    // ── Buildings overlay ─────────────────────────────────────────────────────

    /** Absolute path to the downloaded buildings overlay .pmtiles file, or "" if not present. */
    public String getBuildingsPath() {
        return prefs.getString(KEY_BUILDINGS_PATH, "");
    }

    public void setBuildingsPath(String path) {
        prefs.edit().putString(KEY_BUILDINGS_PATH, path).apply();
    }

    public String getBuildingsVersion() {
        return prefs.getString(KEY_BUILDINGS_VERSION, "");
    }

    public void setBuildingsVersion(String version) {
        prefs.edit().putString(KEY_BUILDINGS_VERSION, version).apply();
    }

    public boolean hasBuildings() {
        String p = getBuildingsPath();
        return !p.isEmpty() && new java.io.File(p).exists();
    }

    // ── POI overlay ───────────────────────────────────────────────────────────

    /** Absolute path to the downloaded low-zoom POI overlay .pmtiles file, or "" if not present. */
    public String getPoiPath() {
        return prefs.getString(KEY_POI_PATH, "");
    }

    public void setPoiPath(String path) {
        prefs.edit().putString(KEY_POI_PATH, path).apply();
    }

    public String getPoiVersion() {
        return prefs.getString(KEY_POI_VERSION, "");
    }

    public void setPoiVersion(String version) {
        prefs.edit().putString(KEY_POI_VERSION, version).apply();
    }

    public boolean hasPoi() {
        String p = getPoiPath();
        return !p.isEmpty() && new java.io.File(p).exists();
    }

    // ── Boundaries overlay ────────────────────────────────────────────────────

    /** Absolute path to the downloaded boundaries.pmtiles file, or "" if not present. */
    public String getBoundariesPath() {
        return prefs.getString(KEY_BOUNDARIES_PATH, "");
    }

    public void setBoundariesPath(String path) {
        prefs.edit().putString(KEY_BOUNDARIES_PATH, path).apply();
    }

    public String getBoundariesVersion() {
        return prefs.getString(KEY_BOUNDARIES_VERSION, "");
    }

    public void setBoundariesVersion(String version) {
        prefs.edit().putString(KEY_BOUNDARIES_VERSION, version).apply();
    }

    public boolean hasBoundaries() {
        String p = getBoundariesPath();
        return !p.isEmpty() && new java.io.File(p).exists();
    }

    // ── National zoom level ───────────────────────────────────────────────────

    private static final String KEY_NATIONAL_ZOOM = "national_zoom";

    /** 12, 13 or 14 — zoom level of the downloaded national pack; 0 = not downloaded. */
    public int getNationalZoom() {
        return prefs.getInt(KEY_NATIONAL_ZOOM, 0);
    }

    public void setNationalZoom(int zoom) {
        prefs.edit().putInt(KEY_NATIONAL_ZOOM, zoom).apply();
    }

    // ── Province packs (multi-pack) ───────────────────────────────────────────

    private static final String KEY_PROVINCE_PACK_KEYS = "province_pack_keys";

    /**
     * Returns the set of downloaded province pack keys, e.g. {"antananarivo-z13", "toliara-z14"}.
     */
    public java.util.Set<String> getProvincePackKeys() {
        return new java.util.HashSet<>(
                prefs.getStringSet(KEY_PROVINCE_PACK_KEYS, new java.util.HashSet<>()));
    }

    public void addProvincePack(String packKey, String path, String version) {
        java.util.Set<String> keys = getProvincePackKeys();
        keys.add(packKey);
        prefs.edit()
                .putStringSet(KEY_PROVINCE_PACK_KEYS, keys)
                .putString("pack_path_" + packKey, path)
                .putString("pack_ver_" + packKey, version)
                .apply();
    }

    public void removeProvincePack(String packKey) {
        java.util.Set<String> keys = getProvincePackKeys();
        keys.remove(packKey);
        prefs.edit()
                .putStringSet(KEY_PROVINCE_PACK_KEYS, keys)
                .remove("pack_path_" + packKey)
                .remove("pack_ver_" + packKey)
                .remove("pack_bld_" + packKey)
                .remove("pack_poi_" + packKey)
                .apply();
    }

    public String getProvincePackPath(String packKey) {
        return prefs.getString("pack_path_" + packKey, "");
    }

    public String getProvincePackVersion(String packKey) {
        return prefs.getString("pack_ver_" + packKey, "");
    }

    public boolean hasProvincePack(String packKey) {
        String path = getProvincePackPath(packKey);
        return !path.isEmpty() && new java.io.File(path).exists();
    }

    public String getProvinceBuildingsPath(String packKey) {
        return prefs.getString("pack_bld_" + packKey, "");
    }

    public void setProvinceBuildingsPath(String packKey, String path) {
        prefs.edit().putString("pack_bld_" + packKey, path).apply();
    }

    public String getProvincePoiPath(String packKey) {
        return prefs.getString("pack_poi_" + packKey, "");
    }

    public void setProvincePoiPath(String packKey, String path) {
        prefs.edit().putString("pack_poi_" + packKey, path).apply();
    }

    // ── Pause state — national ────────────────────────────────────────────────

    private static final String KEY_NATIONAL_PAUSED      = "national_paused";
    private static final String KEY_NATIONAL_PAUSED_ZOOM = "national_paused_zoom";

    public boolean isNationalPaused() {
        return prefs.getBoolean(KEY_NATIONAL_PAUSED, false);
    }

    public void setNationalPaused(boolean paused, int zoom) {
        prefs.edit()
                .putBoolean(KEY_NATIONAL_PAUSED, paused)
                .putInt(KEY_NATIONAL_PAUSED_ZOOM, zoom)
                .apply();
    }

    public int getNationalPausedZoom() {
        return prefs.getInt(KEY_NATIONAL_PAUSED_ZOOM, 0);
    }

    // ── Pause state — province ────────────────────────────────────────────────

    private static final String KEY_PROVINCE_PAUSED     = "province_paused";
    private static final String KEY_PROVINCE_PAUSED_KEY = "province_paused_key";

    public boolean isProvincePaused() {
        return prefs.getBoolean(KEY_PROVINCE_PAUSED, false);
    }

    public void setProvincePaused(boolean paused, String packKey) {
        prefs.edit()
                .putBoolean(KEY_PROVINCE_PAUSED, paused)
                .putString(KEY_PROVINCE_PAUSED_KEY, packKey != null ? packKey : "")
                .apply();
    }

    public String getProvincePausedKey() {
        return prefs.getString(KEY_PROVINCE_PAUSED_KEY, "");
    }
}
