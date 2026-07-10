package org.github.nynosy.adiresy_mobile.ui.offline;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import org.github.nynosy.adiresy_mobile.download.TileDownloadWorker;

public class OfflineDataViewModel extends AndroidViewModel {

    private static final String WORK_TAG_NATIONAL = "tile_download_national";
    private static final String WORK_TAG_PROVINCE = "tile_download_province";

    private final WorkManager workManager;
    private final AppPrefs prefs;

    public OfflineDataViewModel(@NonNull Application application) {
        super(application);
        workManager = WorkManager.getInstance(application);
        prefs = AppPrefs.get(application);
    }

    // ── National ──────────────────────────────────────────────────────────────

    public LiveData<WorkInfo> getNationalWorkInfo() {
        return Transformations.map(
                workManager.getWorkInfosByTagLiveData(WORK_TAG_NATIONAL),
                OfflineDataViewModel::pickActive);
    }

    public void startNationalDownload(String url, String version, int zoom, boolean allowMobileData) {
        prefs.setNationalPaused(false, 0);
        Data mapInput = new Data.Builder()
                .putString(TileDownloadWorker.KEY_URL, url)
                .putString(TileDownloadWorker.KEY_VERSION, version)
                .putString(TileDownloadWorker.KEY_DEST_FILENAME, "madagascar-z" + zoom + ".pmtiles")
                .putString(TileDownloadWorker.KEY_SCOPE, TileDownloadWorker.SCOPE_NATIONAL)
                .putInt(TileDownloadWorker.KEY_NATIONAL_ZOOM, zoom)
                .build();
        // Base URL derived from map URL: replace filename
        String buildingsUrl = url.substring(0, url.lastIndexOf('/') + 1)
                + "buildings-madagascar-z" + zoom + ".pmtiles";
        Data buildingsInput = new Data.Builder()
                .putString(TileDownloadWorker.KEY_URL, buildingsUrl)
                .putString(TileDownloadWorker.KEY_VERSION, version)
                .putString(TileDownloadWorker.KEY_DEST_FILENAME, "buildings-madagascar-z" + zoom + ".pmtiles")
                .putString(TileDownloadWorker.KEY_SCOPE, TileDownloadWorker.SCOPE_BUILDINGS)
                .build();
        enqueueChain(mapInput, buildingsInput, WORK_TAG_NATIONAL, allowMobileData);
    }

    public void pauseNationalDownload(int zoom) {
        workManager.cancelAllWorkByTag(WORK_TAG_NATIONAL);
        prefs.setNationalPaused(true, zoom);
    }

    public void resumeNationalDownload(String url, String version, int zoom, boolean allowMobileData) {
        prefs.setNationalPaused(false, 0);
        startNationalDownload(url, version, zoom, allowMobileData);
    }

    public void discardNationalDownload() {
        workManager.cancelAllWorkByTag(WORK_TAG_NATIONAL);
        int zoom = prefs.getNationalPausedZoom();
        if (zoom == 0) zoom = prefs.getNationalZoom();
        if (zoom > 0) {
            java.io.File mapDir = getApplication().getExternalFilesDir("map");
            if (mapDir != null) {
                new java.io.File(mapDir, "madagascar-z" + zoom + ".pmtiles").delete();
                new java.io.File(mapDir, "buildings-madagascar-z" + zoom + ".pmtiles").delete();
            }
        }
        prefs.setNationalPaused(false, 0);
    }

    public boolean isNationalPaused() {
        return prefs.isNationalPaused();
    }

    public int getNationalPausedZoom() {
        return prefs.getNationalPausedZoom();
    }

    public void cancelNationalDownload() {
        workManager.cancelAllWorkByTag(WORK_TAG_NATIONAL);
    }

    /** Deletes national map + buildings tiles and clears all national prefs. */
    public void deleteNationalData() {
        String path = prefs.getDataPath();
        if (!path.isEmpty()) new File(path).delete();
        String bPath = prefs.getBuildingsPath();
        if (!bPath.isEmpty()) new File(bPath).delete();
        prefs.setDataPath("");
        prefs.setDataVersion("");
        prefs.setBuildingsPath("");
        prefs.setBuildingsVersion("");
        prefs.setNationalZoom(0);
    }

    public boolean hasNationalData() {
        return prefs.hasOfflineData();
    }

    public String getNationalVersion() {
        return prefs.getDataVersion();
    }

    public String getNationalTilePath() {
        return prefs.getDataPath();
    }

    /** 12, 13 or 14; 0 if not downloaded. */
    public int getNationalZoom() {
        return prefs.getNationalZoom();
    }

    // ── Province packs ────────────────────────────────────────────────────────

    public LiveData<WorkInfo> getProvinceWorkInfo() {
        return Transformations.map(
                workManager.getWorkInfosByTagLiveData(WORK_TAG_PROVINCE),
                OfflineDataViewModel::pickActive);
    }

    /**
     * @param packKey  composite key, e.g. "antananarivo-z13"
     * @param url      download URL for the province map tile file
     */
    public void startProvincePackDownload(String packKey, String url, String version,
                                          boolean allowMobileData) {
        prefs.setProvincePaused(false, null);
        Data mapInput = new Data.Builder()
                .putString(TileDownloadWorker.KEY_URL, url)
                .putString(TileDownloadWorker.KEY_VERSION, version)
                .putString(TileDownloadWorker.KEY_DEST_FILENAME, "province-" + packKey + ".pmtiles")
                .putString(TileDownloadWorker.KEY_SCOPE, TileDownloadWorker.SCOPE_PROVINCE_PACK)
                .putString(TileDownloadWorker.KEY_PACK_KEY, packKey)
                .build();
        String buildingsUrl = url.substring(0, url.lastIndexOf('/') + 1)
                + "buildings-province-" + packKey + ".pmtiles";
        Data buildingsInput = new Data.Builder()
                .putString(TileDownloadWorker.KEY_URL, buildingsUrl)
                .putString(TileDownloadWorker.KEY_VERSION, version)
                .putString(TileDownloadWorker.KEY_DEST_FILENAME, "buildings-province-" + packKey + ".pmtiles")
                .putString(TileDownloadWorker.KEY_SCOPE, TileDownloadWorker.SCOPE_PROVINCE_BUILDINGS)
                .putString(TileDownloadWorker.KEY_PACK_KEY, packKey)
                .build();
        enqueueChain(mapInput, buildingsInput, WORK_TAG_PROVINCE, allowMobileData);
    }

    public void pauseProvinceDownload(String packKey) {
        workManager.cancelAllWorkByTag(WORK_TAG_PROVINCE);
        prefs.setProvincePaused(true, packKey);
    }

    public void resumeProvincePackDownload(String packKey, String url, String version,
                                           boolean allowMobileData) {
        prefs.setProvincePaused(false, null);
        startProvincePackDownload(packKey, url, version, allowMobileData);
    }

    public void discardProvinceDownload() {
        workManager.cancelAllWorkByTag(WORK_TAG_PROVINCE);
        String packKey = prefs.getProvincePausedKey();
        if (!packKey.isEmpty()) {
            java.io.File mapDir = getApplication().getExternalFilesDir("map");
            if (mapDir != null) {
                new java.io.File(mapDir, "province-" + packKey + ".pmtiles").delete();
                new java.io.File(mapDir, "buildings-province-" + packKey + ".pmtiles").delete();
            }
        }
        prefs.setProvincePaused(false, null);
    }

    public boolean isProvincePaused() {
        return prefs.isProvincePaused();
    }

    public String getProvincePausedKey() {
        return prefs.getProvincePausedKey();
    }

    public void cancelProvinceDownload() {
        workManager.cancelAllWorkByTag(WORK_TAG_PROVINCE);
    }

    public void deleteProvincePack(String packKey) {
        String path = prefs.getProvincePackPath(packKey);
        if (!path.isEmpty()) new File(path).delete();
        String bPath = prefs.getProvinceBuildingsPath(packKey);
        if (!bPath.isEmpty()) new File(bPath).delete();
        prefs.removeProvincePack(packKey);
    }

    public Set<String> getDownloadedProvincePackKeys() {
        return prefs.getProvincePackKeys();
    }

    public String getProvincePackPath(String packKey) {
        return prefs.getProvincePackPath(packKey);
    }

    public String getNationalBuildingsPath() {
        return prefs.getBuildingsPath();
    }

    public String getProvinceBuildingsPath(String packKey) {
        return prefs.getProvinceBuildingsPath(packKey);
    }

    public String getProvincePackVersion(String packKey) {
        return prefs.getProvincePackVersion(packKey);
    }

    public boolean hasProvincePack(String packKey) {
        return prefs.hasProvincePack(packKey);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Picks the most relevant WorkInfo from a chained pair: RUNNING > ENQUEUED > last terminal. */
    private static WorkInfo pickActive(List<WorkInfo> list) {
        if (list == null || list.isEmpty()) return null;
        for (WorkInfo w : list) {
            if (w.getState() == WorkInfo.State.RUNNING) return w;
        }
        for (WorkInfo w : list) {
            if (w.getState() == WorkInfo.State.ENQUEUED) return w;
        }
        return list.get(list.size() - 1);
    }

    private void enqueueChain(Data first, Data second, String tag, boolean allowMobileData) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(
                        allowMobileData ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .build();
        OneTimeWorkRequest req1 = new OneTimeWorkRequest.Builder(TileDownloadWorker.class)
                .setConstraints(constraints).setInputData(first).addTag(tag).build();
        OneTimeWorkRequest req2 = new OneTimeWorkRequest.Builder(TileDownloadWorker.class)
                .setConstraints(constraints).setInputData(second).addTag(tag).build();
        workManager.beginWith(req1).then(req2).enqueue();
    }

    private void enqueue(Data input, String tag, boolean allowMobileData) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(
                        allowMobileData ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TileDownloadWorker.class)
                .setConstraints(constraints).setInputData(input).addTag(tag).build();
        workManager.enqueue(request);
    }
}
