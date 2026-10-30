package org.github.nynosy.adiresy_mobile.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import timber.log.Timber;

public class AppPrefs {

    private static final String PREF_FILE         = "adiresy_prefs";
    private static final String SECURE_PREF_FILE   = "adiresy_secure_prefs";
    private static final String KEY_FIRST_RUN         = "first_run";
    private static final String KEY_LANGUAGE          = "language";
    private static final String KEY_THEME             = "theme";
    // "_v2": the previous value (security-crypto's EncryptedSharedPreferences,
    // dropped — see KEY_ALIAS doc below) lives under the old "api_key" name in
    // the same file. A distinct name means a post-upgrade read never collides
    // with — or attempts to decrypt — that leftover value; it's simply orphaned.
    // No migration: this token is cheap to replace, see DeviceAuthManager.
    private static final String KEY_API_KEY           = "api_key_v2";
    private static final String KEY_DATA_VERSION      = "data_version";
    private static final String KEY_DATA_PATH         = "data_path";
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

    private static volatile AppPrefs instance;

    private final SharedPreferences prefs;
    private final SharedPreferences securePrefs;

    private AppPrefs(Context context) {
        Context appCtx = context.getApplicationContext();
        prefs = appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        securePrefs = appCtx.getSharedPreferences(SECURE_PREF_FILE, Context.MODE_PRIVATE);
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

    // ── Offline data prompt (shown once) ─────────────────────────────────────

    private static final String KEY_OFFLINE_PROMPT_SHOWN = "offline_prompt_shown";

    public boolean hasShownOfflinePrompt() {
        return prefs.getBoolean(KEY_OFFLINE_PROMPT_SHOWN, false);
    }

    public void setOfflinePromptShown() {
        prefs.edit().putBoolean(KEY_OFFLINE_PROMPT_SHOWN, true).apply();
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
    // DeviceAuthManager) — not a single key baked into the build. Encrypted
    // with an AES-256-GCM key generated directly in AndroidKeyStore (see
    // encrypt()/decrypt() below), not androidx.security's
    // MasterKey/EncryptedSharedPreferences: that whole API was deprecated in
    // security-crypto 1.1.0 with no replacement shipped, in favour of exactly
    // this — direct Keystore use. No migration from the old library's stored
    // value: KEY_API_KEY is a new pref name, so it's simply never read; the
    // token is cheap and self-healing (DeviceAuthManager re-registers
    // whenever this returns empty), not worth a migration path for.

    public String getApiKey() {
        String stored = securePrefs.getString(KEY_API_KEY, "");
        if (stored.isEmpty()) return "";
        try {
            return decrypt(stored);
        } catch (GeneralSecurityException e) {
            // Corrupt, from an incompatible format, or the key was
            // invalidated (e.g. keystore reset) — treat as absent.
            Timber.w(e, "Could not decrypt stored API key, treating as absent");
            return "";
        }
    }

    public void setApiKey(String apiKey) {
        try {
            securePrefs.edit().putString(KEY_API_KEY, encrypt(apiKey)).apply();
        } catch (GeneralSecurityException e) {
            // Don't persist a value getApiKey() could never decrypt back —
            // leaves the key simply unset, which DeviceAuthManager already
            // treats as "needs to register" and retries next time it's needed.
            Timber.w(e, "Could not encrypt API key, not persisting it");
        }
    }

    // ── AndroidKeyStore-backed encryption for the API key ───────────────────
    // AES-256-GCM, key held entirely inside AndroidKeyStore (hardware-backed
    // where available) — only Cipher operations cross that boundary, the raw
    // key material never does. Stored value is base64(IV || ciphertext); GCM's
    // 12-byte IV and 128-bit auth tag are standard, not a judgment call here.

    private static final String ANDROID_KEYSTORE   = "AndroidKeyStore";
    private static final String PREFS_KEY_ALIAS    = "adiresy_prefs_key";
    private static final String AES_GCM_TRANSFORM  = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH       = 12;
    private static final int    GCM_TAG_LENGTH_BITS = 128;

    /** Synchronized: AppPrefs is a singleton reached from multiple background
     *  threads at once (e.g. WorkManager's ListingSyncWorker racing a direct
     *  call, both triggering DeviceAuthManager.ensureToken() concurrently on
     *  a fresh install). Without this lock, two threads can both see the
     *  alias absent and each call generateKey() for it — the second call
     *  replaces the keystore entry out from under the first, which then
     *  fails its in-flight Cipher.init() with
     *  KeyPermanentlyInvalidatedException. Reproduced and confirmed fixed
     *  on-device (2026-10-30). */
    private static synchronized SecretKey getOrCreatePrefsKey() throws GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        try {
            ks.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        if (ks.containsAlias(PREFS_KEY_ALIAS)) {
            KeyStore.Entry entry = ks.getEntry(PREFS_KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
                return secretKeyEntry.getSecretKey();
            }
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(PREFS_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private static String encrypt(String plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreatePrefsKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private static String decrypt(String stored) throws GeneralSecurityException {
        byte[] combined = Base64.decode(stored, Base64.NO_WRAP);
        if (combined.length <= GCM_IV_LENGTH) {
            throw new GeneralSecurityException("Stored value too short to contain an IV");
        }
        byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
        Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORM);
        AlgorithmParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreatePrefsKey(), spec);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
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

    /** 12 or 13 — zoom level of the downloaded national pack; 0 = not downloaded. */
    public int getNationalZoom() {
        return prefs.getInt(KEY_NATIONAL_ZOOM, 0);
    }

    public void setNationalZoom(int zoom) {
        prefs.edit().putInt(KEY_NATIONAL_ZOOM, zoom).apply();
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

    // ── Directory listings sync cursor ───────────────────────────────────────
    // The server's own max `updated_at` seen across the last fully-completed
    // sync pass (not the device clock — see docs/directory-api-integration.md
    // §5 Q6) passed back as `updated_since` on the next call.

    private static final String KEY_LISTINGS_SYNC_CURSOR = "listings_sync_cursor";

    /** ISO-8601 timestamp, or "" if a full sync has never completed. */
    public String getListingsSyncCursor() {
        return prefs.getString(KEY_LISTINGS_SYNC_CURSOR, "");
    }

    public void setListingsSyncCursor(String cursor) {
        prefs.edit().putString(KEY_LISTINGS_SYNC_CURSOR, cursor).apply();
    }
}
