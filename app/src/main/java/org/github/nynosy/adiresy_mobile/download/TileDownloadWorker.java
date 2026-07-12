package org.github.nynosy.adiresy_mobile.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TileDownloadWorker extends Worker {

    private static final String TAG = "TileDownloadWorker";

    /** URL to download. */
    public static final String KEY_URL          = "download_url";
    /** Data version string to persist on success, e.g. "2026-Q3". */
    public static final String KEY_VERSION      = "data_version";
    /** Output filename inside getExternalFilesDir("map"), e.g. "madagascar-z12.pmtiles". */
    public static final String KEY_DEST_FILENAME = "dest_filename";
    /**
     * Scope of the download: "national" saves to the national prefs keys;
     * "regional" saves to the regional prefs keys.
     */
    public static final String KEY_SCOPE        = "scope";
    /** Province key when scope == "regional", e.g. "antananarivo". */
    public static final String KEY_REGION_NAME  = "region_name";

    /** Scope for a province detail pack keyed by {@link #KEY_PACK_KEY}. */
    public static final String SCOPE_PROVINCE_PACK = "province_pack";
    /** Scope for the buildings layer of a province pack, keyed by {@link #KEY_PACK_KEY}. */
    public static final String SCOPE_PROVINCE_BUILDINGS = "province_buildings";
    /** Composite pack key, e.g. "antananarivo-z13". Required when scope == SCOPE_PROVINCE_PACK. */
    public static final String KEY_PACK_KEY      = "pack_key";
    /** Zoom level (int 12/13/14) stored on success when scope == SCOPE_NATIONAL. */
    public static final String KEY_NATIONAL_ZOOM = "national_zoom_dl";

    public static final String SCOPE_NATIONAL   = "national";
    public static final String SCOPE_REGIONAL   = "regional";
    public static final String SCOPE_BUILDINGS  = "buildings";
    public static final String SCOPE_BOUNDARIES = "boundaries";

    /** Progress keys exposed via setProgressAsync(). */
    public static final String KEY_BYTES_TOTAL = "bytes_total";
    public static final String KEY_BYTES_DONE  = "bytes_done";

    /**
     * Give up after this many attempts instead of retrying forever. Without
     * a cap, a permanently broken URL/server retries silently via
     * WorkManager's backoff indefinitely — from the UI this is
     * indistinguishable from a hung download (see onNationalWorkInfo /
     * onProvinceWorkInfo, which only leave the indeterminate spinner on
     * RUNNING/ENQUEUED and never reach FAILED).
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
        String url      = getInputData().getString(KEY_URL);
        String version  = getInputData().getString(KEY_VERSION);
        String filename = getInputData().getString(KEY_DEST_FILENAME);
        String scope    = getInputData().getString(KEY_SCOPE);
        String region   = getInputData().getString(KEY_REGION_NAME);

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

            switch (scope) {
                case SCOPE_PROVINCE_PACK: {
                    String packKey = getInputData().getString(KEY_PACK_KEY);
                    if (packKey != null) prefs.addProvincePack(packKey, dest.getAbsolutePath(),
                            version != null ? version : "");
                    break;
                }
                case SCOPE_PROVINCE_BUILDINGS: {
                    String packKey = getInputData().getString(KEY_PACK_KEY);
                    if (packKey != null) prefs.setProvinceBuildingsPath(packKey, dest.getAbsolutePath());
                    break;
                }
                case SCOPE_BUILDINGS:
                    prefs.setBuildingsPath(dest.getAbsolutePath());
                    if (version != null) prefs.setBuildingsVersion(version);
                    break;
                case SCOPE_BOUNDARIES:
                    prefs.setBoundariesPath(dest.getAbsolutePath());
                    if (version != null) prefs.setBoundariesVersion(version);
                    break;
                default: // SCOPE_NATIONAL (and legacy SCOPE_REGIONAL)
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
}
