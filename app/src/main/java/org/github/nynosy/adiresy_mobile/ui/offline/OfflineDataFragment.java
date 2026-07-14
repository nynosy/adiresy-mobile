package org.github.nynosy.adiresy_mobile.ui.offline;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.api.dto.ManifestDto;
import org.github.nynosy.adiresy_mobile.databinding.FragmentOfflineDataBinding;
import org.github.nynosy.adiresy_mobile.download.DownloadTarget;
import org.github.nynosy.adiresy_mobile.download.ManifestClient;
import org.github.nynosy.adiresy_mobile.download.TileDownloadWorker;

/** National map data only: pick Z12 (Overview) or Z13 (Standard), download. No per-region
 *  packs, no Z14 — see docs/National-Only-Simplification-Implementation-Spec.md. */
public class OfflineDataFragment extends Fragment {

    private static final String BASE_URL =
            "https://github.com/nynosy/adiresy-tiles/releases/latest/download/";
    private static final String DATA_VERSION = "2026-Q3";

    // Map tiles + buildings tiles combined per zoom level
    private static final int[] NATIONAL_SIZES_MB = {190, 410};

    private FragmentOfflineDataBinding binding;
    private OfflineDataViewModel viewModel;
    private final ExecutorService manifestExecutor = Executors.newSingleThreadExecutor();
    /** Set (possibly to null on failure) once the background fetch in
     *  onViewCreated() completes; volatile for safe cross-thread publication.
     *  Every resolver call tolerates null and falls back to a constructed
     *  URL with no checksum, so a slow/failed fetch never blocks a download. */
    private volatile ManifestDto manifest;

    private int selectedNationalZoom = 12;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOfflineDataBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(OfflineDataViewModel.class);

        // Restore national zoom from prefs so radio reflects current state
        int storedZoom = viewModel.getNationalZoom();
        if (storedZoom > 0) selectedNationalZoom = storedZoom;

        setupNationalZoomRows();
        updateNationalCard();

        binding.btnNationalDownload.setOnClickListener(v -> confirmAndStartNational());
        binding.btnNationalPause.setOnClickListener(v -> {
            viewModel.pauseNationalDownload(selectedNationalZoom);
            showNationalPaused();
        });
        binding.btnNationalResume.setOnClickListener(v -> maybePromptMobileAndResumeNational());
        binding.btnNationalCancel.setOnClickListener(v -> {
            viewModel.discardNationalDownload();
            updateNationalCard();
        });
        binding.btnNationalDelete.setOnClickListener(v -> confirmDeleteNational());

        viewModel.getNationalWorkInfo().observe(getViewLifecycleOwner(), this::onNationalWorkInfo);

        manifestExecutor.execute(() -> manifest = ManifestClient.fetchSync());
    }

    // ── National zoom selection ───────────────────────────────────────────────

    private void setupNationalZoomRows() {
        binding.rowNationalZ12.setOnClickListener(v -> selectNationalZoom(12));
        binding.rowNationalZ13.setOnClickListener(v -> selectNationalZoom(13));
        applyNationalZoomRadio();
    }

    private void selectNationalZoom(int zoom) {
        selectedNationalZoom = zoom;
        applyNationalZoomRadio();
        updateNationalCard();
    }

    private void applyNationalZoomRadio() {
        binding.rbNationalZ12.setChecked(selectedNationalZoom == 12);
        binding.rbNationalZ13.setChecked(selectedNationalZoom == 13);
    }

    // ── National card ─────────────────────────────────────────────────────────

    private void updateNationalCard() {
        applyNationalZoomRadio();

        if (viewModel.isNationalPaused()) {
            showNationalPaused();
            return;
        }

        binding.progressNational.setVisibility(View.GONE);
        binding.btnNationalPause.setVisibility(View.GONE);
        binding.btnNationalResume.setVisibility(View.GONE);
        binding.btnNationalCancel.setVisibility(View.GONE);

        int storedZoom = viewModel.getNationalZoom();
        boolean hasNational = viewModel.hasNationalData();

        if (hasNational) {
            String version = viewModel.getNationalVersion();
            int mb = fileSizeMb(viewModel.getNationalTilePath())
                    + fileSizeMb(viewModel.getNationalBuildingsPath())
                    + fileSizeMb(viewModel.getNationalPoiPath());
            binding.labelNationalStatus.setText(
                    getString(R.string.offline_national_done, "Z" + storedZoom, version, mb));
            binding.btnNationalDelete.setVisibility(View.VISIBLE);
        } else {
            binding.labelNationalStatus.setText(R.string.offline_status_none);
            binding.btnNationalDelete.setVisibility(View.GONE);
        }

        binding.btnNationalDownload.setVisibility(View.VISIBLE);
        boolean alreadyHaveSelected = hasNational && storedZoom == selectedNationalZoom;
        binding.btnNationalDownload.setEnabled(!alreadyHaveSelected);
    }

    private void showNationalPaused() {
        binding.progressNational.setVisibility(View.GONE);
        binding.btnNationalDownload.setVisibility(View.GONE);
        binding.btnNationalDelete.setVisibility(View.GONE);
        binding.btnNationalPause.setVisibility(View.GONE);
        binding.btnNationalResume.setVisibility(View.VISIBLE);
        binding.btnNationalCancel.setVisibility(View.VISIBLE);
        binding.labelNationalStatus.setText(R.string.offline_status_paused);
    }

    private void confirmAndStartNational() {
        int storedZoom = viewModel.getNationalZoom();
        boolean hasOtherZoom = viewModel.hasNationalData() && storedZoom != selectedNationalZoom;

        if (hasOtherZoom) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.offline_replace_national_title)
                    .setMessage(getString(R.string.offline_replace_national_message,
                            "Z" + storedZoom, "Z" + selectedNationalZoom))
                    .setPositiveButton(R.string.btn_download, (d, w) -> {
                        viewModel.deleteNationalData();
                        maybePromptMobileAndStartNational();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            maybePromptMobileAndStartNational();
        }
    }

    private void maybePromptMobileAndStartNational() {
        if (binding.toggleMobileData.isChecked()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.warning_mobile_data_title)
                    .setMessage(R.string.warning_mobile_data_message)
                    .setPositiveButton(R.string.btn_download, (d, w) -> startNational(true))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            startNational(false);
        }
    }

    private void startNational(boolean allowMobile) {
        int zoom = selectedNationalZoom;
        String tier = "z" + zoom;
        ManifestDto m = manifest;
        DownloadTarget mapTarget = resolveTarget(m != null ? m.files : null, tier,
                BASE_URL + "madagascar-z" + zoom + ".pmtiles");
        DownloadTarget buildingsTarget = resolveTarget(m != null ? m.buildings : null, tier,
                BASE_URL + "buildings-madagascar-z" + zoom + ".pmtiles");
        DownloadTarget poiTarget = resolveTarget(m != null ? m.poi : null, tier,
                BASE_URL + "poi-madagascar-z" + zoom + ".pmtiles");
        showNationalDownloading();
        viewModel.startNationalDownload(mapTarget, buildingsTarget, poiTarget, DATA_VERSION, zoom, allowMobile);
    }

    private void maybePromptMobileAndResumeNational() {
        if (binding.toggleMobileData.isChecked()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.warning_mobile_data_title)
                    .setMessage(R.string.warning_mobile_data_message)
                    .setPositiveButton(R.string.btn_resume, (d, w) -> resumeNational(true))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            resumeNational(false);
        }
    }

    private void resumeNational(boolean allowMobile) {
        int zoom = viewModel.getNationalPausedZoom();
        if (zoom == 0) zoom = selectedNationalZoom;
        String tier = "z" + zoom;
        ManifestDto m = manifest;
        DownloadTarget mapTarget = resolveTarget(m != null ? m.files : null, tier,
                BASE_URL + "madagascar-z" + zoom + ".pmtiles");
        DownloadTarget buildingsTarget = resolveTarget(m != null ? m.buildings : null, tier,
                BASE_URL + "buildings-madagascar-z" + zoom + ".pmtiles");
        DownloadTarget poiTarget = resolveTarget(m != null ? m.poi : null, tier,
                BASE_URL + "poi-madagascar-z" + zoom + ".pmtiles");
        showNationalDownloading();
        viewModel.resumeNationalDownload(mapTarget, buildingsTarget, poiTarget, DATA_VERSION, zoom, allowMobile);
    }

    private void showNationalDownloading() {
        binding.btnNationalDownload.setVisibility(View.GONE);
        binding.btnNationalResume.setVisibility(View.GONE);
        binding.btnNationalDelete.setVisibility(View.GONE);
        binding.btnNationalCancel.setVisibility(View.GONE);
        binding.btnNationalPause.setVisibility(View.VISIBLE);
        binding.progressNational.setVisibility(View.VISIBLE);
        binding.progressNational.setIndeterminate(true);
        binding.labelNationalStatus.setText(R.string.offline_status_downloading);
    }

    private void confirmDeleteNational() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    viewModel.deleteNationalData();
                    updateNationalCard();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void onNationalWorkInfo(WorkInfo info) {
        if (info == null) return;
        switch (info.getState()) {
            case ENQUEUED:
                if (binding.progressNational.getVisibility() != View.VISIBLE) {
                    showNationalDownloading();
                } else {
                    binding.progressNational.setIndeterminate(true);
                    binding.labelNationalStatus.setText(info.getRunAttemptCount() > 0
                            ? R.string.offline_status_retrying
                            : R.string.offline_status_downloading);
                }
                break;
            case RUNNING:
                applyProgress(
                        binding.progressNational,
                        binding.labelNationalStatus,
                        info.getProgress().getLong(TileDownloadWorker.KEY_BYTES_DONE, 0),
                        info.getProgress().getLong(TileDownloadWorker.KEY_BYTES_TOTAL, -1));
                break;
            case SUCCEEDED:
                binding.progressNational.setVisibility(View.GONE);
                updateNationalCard();
                break;
            case FAILED:
                if (viewModel.isNationalPaused()) {
                    showNationalPaused();
                } else {
                    updateNationalCard();
                    showDownloadFailedSnackbar();
                }
                break;
            case CANCELLED:
                if (viewModel.isNationalPaused()) {
                    showNationalPaused();
                } else {
                    updateNationalCard();
                }
                break;
            default:
                break;
        }
    }

    private void showDownloadFailedSnackbar() {
        if (binding == null) return;
        Snackbar.make(binding.getRoot(), R.string.offline_download_failed, Snackbar.LENGTH_LONG).show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resolves a download target from the manifest (see ManifestClient), falling
     *  back to a constructed URL with no checksum if the manifest is unavailable
     *  or doesn't have this entry — never blocks a download either way. */
    private DownloadTarget resolveTarget(ManifestDto.LayerDto layer, String tier, String fallbackUrl) {
        DownloadTarget resolved = ManifestClient.resolve(layer, tier);
        return resolved != null ? resolved : new DownloadTarget(fallbackUrl, null, 0);
    }

    private void applyProgress(
            com.google.android.material.progressindicator.LinearProgressIndicator bar,
            android.widget.TextView label,
            long done, long total) {
        bar.setVisibility(View.VISIBLE);
        if (total > 0) {
            bar.setIndeterminate(false);
            bar.setMax(100);
            bar.setProgress((int) (done * 100 / total));
            int doneMb  = (int) (done  / 1048576L);
            int totalMb = (int) (total / 1048576L);
            label.setText(getString(R.string.offline_status_progress, doneMb, totalMb));
        } else {
            bar.setIndeterminate(true);
            label.setText(R.string.offline_status_downloading);
        }
    }

    private int fileSizeMb(String path) {
        if (path == null || path.isEmpty()) return 0;
        File f = new File(path);
        return f.exists() ? (int) Math.ceil(f.length() / 1048576.0) : 0;
    }

    @Override
    public void onResume() {
        super.onResume();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.title_offline_data);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        manifestExecutor.shutdownNow();
        binding = null;
    }
}
