package org.github.nynosy.adiresy_mobile.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TileDownloadWorker extends Worker {

    private static final String TAG = "TileDownloadWorker";

    /** URL to download. */
    public static final String KEY_URL          = "download_url";
    /** Expected SHA-256 hex digest, from manifest.json (see ManifestClient).
     *  Optional — verification is skipped when absent (manifest unavailable),
     *  never blocks a download that would otherwise have succeeded. */
    public static final String KEY_SHA256       = "expected_sha256";
    /** Data version string to persist on success, e.g. "2026-Q3". */
    public static final String KEY_VERSION      = "data_version";
    /** Output filename inside getExternalFilesDir("map"), e.g. "madagascar-z12.pmtiles". */
    public static final String KEY_DEST_FILENAME = "dest_filename";
    /** Scope of the download: which prefs entry the downloaded file's path/version
     *  gets saved to on success. */
    public static final String KEY_SCOPE        = "scope";
    /** Zoom level (int 12/13) stored on success when scope == SCOPE_NATIONAL. */
    public static final String KEY_NATIONAL_ZOOM = "national_zoom_dl";

    public static final String SCOPE_NATIONAL   = "national";
    public static final String SCOPE_BUILDINGS  = "buildings";
    /** National low-zoom POI overlay (see docs/Issues.md issue 4). */
    public static final String SCOPE_POI        = "poi";
    public static final String SCOPE_BOUNDARIES = "boundaries";

    /** Progress keys exposed via setProgressAsync(). */
    public static final String KEY_BYTES_TOTAL = "bytes_total";
    public static final String KEY_BYTES_DONE  = "bytes_done";

    /**
     * Give up after this many attempts instead of retrying forever. Without
     * a cap, a permanently broken URL/server retries silently via
     * WorkManager's backoff indefinitely — from the UI this is
     * indistinguishable from a hung download (see onNationalWorkInfo, which
     * only leaves the indeterminate spinner on RUNNING/ENQUEUED and never
     * reaches FAILED).
     */
    private static final int MAX_ATTEMPTS = 6;

    private static volatile OkHttpClient sharedClient;

    private static OkHttpClient httpClient() {
        if (sharedClient == null) {
            synchronized (TileDownloadWorker.class) {
                if (sharedClient == null) sharedClient = new OkHttpClient();
            }
        }
        return sharedClient;
    }

    private final AppPrefs prefs;

    public TileDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        prefs = AppPrefs.get(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        String url            = getInputData().getString(KEY_URL);
        String version        = getInputData().getString(KEY_VERSION);
        String filename       = getInputData().getString(KEY_DEST_FILENAME);
        String scope          = getInputData().getString(KEY_SCOPE);
        String expectedSha256 = getInputData().getString(KEY_SHA256);

        if (url == null || url.isEmpty()) return Result.failure();
        if (filename == null || filename.isEmpty()) return Result.failure();
        if (scope == null) scope = SCOPE_NATIONAL;

        File destDir = getApplicationContext().getExternalFilesDir("map");
        if (destDir == null) return Result.failure();
        destDir.mkdirs();

        File dest = new File(destDir, filename);
        long resumeFrom = dest.exists() ? dest.length() : 0;

        Request.Builder reqBuilder = new Request.Builder().url(url);
        if (resumeFrom > 0) {
            reqBuilder.addHeader("Range", "bytes=" + resumeFrom + "-");
        }

        try (Response response = httpClient().newCall(reqBuilder.build()).execute()) {
            if (response.code() == 416) {
                // Range start == file size: file already fully downloaded, skip to prefs update.
                Log.d(TAG, "HTTP 416 — file already complete: " + filename);
            } else if (!response.isSuccessful() && response.code() != 206) {
                Log.e(TAG, "Download failed: " + response.code());
                return giveUpOrRetry();
            } else {
                long contentLength = response.body() != null
                        ? response.body().contentLength() : -1;
                long total = resumeFrom + contentLength;

                try (InputStream in = response.body().byteStream();
                     FileOutputStream out = new FileOutputStream(dest, resumeFrom > 0)) {
                    byte[] buf = new byte[8192];
                    long done = resumeFrom;
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        if (isStopped()) return Result.failure();
                        out.write(buf, 0, read);
                        done += read;
                        setProgressAsync(new Data.Builder()
                                .putLong(KEY_BYTES_DONE, done)
                                .putLong(KEY_BYTES_TOTAL, total)
                                .build());
                    }
                }
            }

            if (expectedSha256 != null && !expectedSha256.isEmpty()) {
                String actual = sha256Of(dest);
                if (actual == null || !actual.equalsIgnoreCase(expectedSha256)) {
                    Log.e(TAG, "Checksum mismatch for " + filename
                            + " (expected " + expectedSha256 + ", got " + actual + ")");
                    dest.delete();
                    return giveUpOrRetry();
                }
            }

            switch (scope) {
                case SCOPE_BUILDINGS:
                    prefs.setBuildingsPath(dest.getAbsolutePath());
                    if (version != null) prefs.setBuildingsVersion(version);
                    break;
                case SCOPE_POI:
                    prefs.setPoiPath(dest.getAbsolutePath());
                    if (version != null) prefs.setPoiVersion(version);
                    break;
                case SCOPE_BOUNDARIES:
                    prefs.setBoundariesPath(dest.getAbsolutePath());
                    if (version != null) prefs.setBoundariesVersion(version);
                    break;
                default: // SCOPE_NATIONAL
                    prefs.setDataPath(dest.getAbsolutePath());
                    if (version != null) prefs.setDataVersion(version);
                    int zoom = getInputData().getInt(KEY_NATIONAL_ZOOM, 12);
                    prefs.setNationalZoom(zoom);
                    break;
            }

            return Result.success();

        } catch (IOException e) {
            Log.e(TAG, "Download IO error", e);
            return giveUpOrRetry();
        }
    }

    /** Result.retry() until MAX_ATTEMPTS is reached, then Result.failure()
     *  so a permanently broken download surfaces to the UI instead of
     *  backing off forever. */
    private Result giveUpOrRetry() {
        if (getRunAttemptCount() + 1 >= MAX_ATTEMPTS) {
            Log.e(TAG, "Giving up after " + (getRunAttemptCount() + 1) + " attempts");
            return Result.failure();
        }
        return Result.retry();
    }

    /** Streams the file rather than loading it whole — tiers run up to ~300 MB. */
    private static String sha256Of(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to hash " + file, e);
            return null;
        }
    }
}
