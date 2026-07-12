package org.github.nynosy.adiresy_mobile.ui.offline;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.databinding.FragmentOfflineDataBinding;
import org.github.nynosy.adiresy_mobile.download.TileDownloadWorker;

public class OfflineDataFragment extends Fragment {

    private static final String BASE_URL =
            "https://github.com/nynosy/adiresy-tiles/releases/latest/download/";
    private static final String DATA_VERSION = "2026-Q3";

    private static final String[] PROVINCE_KEYS = {
            "antananarivo", "fianarantsoa", "toamasina",
            "mahajanga", "toliara", "antsiranana"
    };

    // Map tiles + buildings tiles combined per zoom level
    private static final int[] NATIONAL_SIZES_MB = {190, 410, 950};

    // [provinceIndex][zoomIndex 0=z12,1=z13,2=z14] = map + buildings combined
    private static final int[][] PROVINCE_SIZES_MB = {
            {52,  110, 247},  // antananarivo
            {76,  160, 369},  // fianarantsoa
            {59,  127, 288},  // toamasina
            {23,   48,  99},  // mahajanga
            {30,   63, 135},  // toliara
            {30,   65, 156},  // antsiranana
    };

    private FragmentOfflineDataBinding binding;
    private OfflineDataViewModel viewModel;

    private int selectedNationalZoom = 12;
    private int selectedProvinceIndex = 0;
    private int selectedProvinceZoom = 13;

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
        setupProvinceSpinner();

        updateNationalCard();
        updateProvinceCard();
        refreshDownloadedPacksList();

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

        binding.btnProvinceDownload.setOnClickListener(v -> confirmAndStartProvince());
        binding.btnProvincePause.setOnClickListener(v -> {
            viewModel.pauseProvinceDownload(currentProvincePackKey());
            showProvincePaused();
        });
        binding.btnProvinceResume.setOnClickListener(v -> maybePromptMobileAndResumeProvince());
        binding.btnProvinceCancel.setOnClickListener(v -> {
            viewModel.discardProvinceDownload();
            updateProvinceCard();
        });

        viewModel.getNationalWorkInfo().observe(getViewLifecycleOwner(), this::onNationalWorkInfo);
        viewModel.getProvinceWorkInfo().observe(getViewLifecycleOwner(), this::onProvinceWorkInfo);
    }

    // ── National zoom selection ───────────────────────────────────────────────

    private void setupNationalZoomRows() {
        binding.rowNationalZ12.setOnClickListener(v -> selectNationalZoom(12));
        binding.rowNationalZ13.setOnClickListener(v -> selectNationalZoom(13));
        binding.rowNationalZ14.setOnClickListener(v -> selectNationalZoom(14));
        applyNationalZoomRadio();
    }

    private void selectNationalZoom(int zoom) {
        selectedNationalZoom = zoom;
        applyNationalZoomRadio();
        updateNationalCard();
        updateProvinceCard(); // available province levels depend on national zoom
    }

    private void applyNationalZoomRadio() {
        binding.rbNationalZ12.setChecked(selectedNationalZoom == 12);
        binding.rbNationalZ13.setChecked(selectedNationalZoom == 13);
        binding.rbNationalZ14.setChecked(selectedNationalZoom == 14);
    }

    // ── Province spinner ──────────────────────────────────────────────────────

    private void setupProvinceSpinner() {
        String[] labels = provinceLabels();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, labels);
        binding.spinnerRegion.setAdapter(adapter);
        binding.spinnerRegion.setText(labels[selectedProvinceIndex], false);
        binding.spinnerRegion.setOnItemClickListener((parent, v, pos, id) -> {
            selectedProvinceIndex = pos;
            updateProvinceCard();
        });
    }

    // ── Province zoom selection ───────────────────────────────────────────────

    private void setupProvinceZoomRows() {
        binding.rowProvinceZ12.setOnClickListener(v -> selectProvinceZoom(12));
        binding.rowProvinceZ13.setOnClickListener(v -> selectProvinceZoom(13));
        binding.rowProvinceZ14.setOnClickListener(v -> selectProvinceZoom(14));
    }

    private void selectProvinceZoom(int zoom) {
        selectedProvinceZoom = zoom;
        applyProvinceZoomRadio();
        updateProvinceDownloadButton();
    }

    private void applyProvinceZoomRadio() {
        binding.rbProvinceZ12.setChecked(selectedProvinceZoom == 12);
        binding.rbProvinceZ13.setChecked(selectedProvinceZoom == 13);
        binding.rbProvinceZ14.setChecked(selectedProvinceZoom == 14);
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
                    + fileSizeMb(viewModel.getNationalBuildingsPath());
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
        String url = BASE_URL + "madagascar-z" + zoom + ".pmtiles";
        showNationalDownloading();
        viewModel.startNationalDownload(url, DATA_VERSION, zoom, allowMobile);
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
        String url = BASE_URL + "madagascar-z" + zoom + ".pmtiles";
        showNationalDownloading();
        viewModel.resumeNationalDownload(url, DATA_VERSION, zoom, allowMobile);
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
                    updateProvinceCard(); // available province levels change
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
                updateProvinceCard();
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

    // ── Province card ─────────────────────────────────────────────────────────

    private void updateProvinceCard() {
        int nationalZoom = viewModel.getNationalZoom(); // 0 = not downloaded
        // Province zoom must be HIGHER than national zoom (or any level if no national)
        int minProvinceZoom = nationalZoom > 0 ? nationalZoom + 1 : 12;

        if (nationalZoom == 14) {
            // No province packs possible
            binding.labelProvinceN14Note.setVisibility(View.VISIBLE);
            binding.layoutProvinceSpinner.setVisibility(View.GONE);
            binding.rowProvinceZ12.setVisibility(View.GONE);
            binding.rowProvinceZ13.setVisibility(View.GONE);
            binding.rowProvinceZ14.setVisibility(View.GONE);
            binding.labelProvinceStatus.setVisibility(View.GONE);
            binding.layoutProvinceBtns.setVisibility(View.GONE);
            return;
        }

        binding.labelProvinceN14Note.setVisibility(View.GONE);
        binding.layoutProvinceSpinner.setVisibility(View.VISIBLE);

        // Show/hide province zoom rows based on available levels
        binding.rowProvinceZ12.setVisibility(minProvinceZoom <= 12 ? View.VISIBLE : View.GONE);
        binding.rowProvinceZ13.setVisibility(minProvinceZoom <= 13 ? View.VISIBLE : View.GONE);
        binding.rowProvinceZ14.setVisibility(View.VISIBLE); // always available (max zoom)

        // Clamp selectedProvinceZoom to available range
        if (selectedProvinceZoom < minProvinceZoom) {
            selectedProvinceZoom = minProvinceZoom;
        }

        setupProvinceZoomRows();
        applyProvinceZoomRadio();
        updateProvinceSizeDescriptions();
        updateProvinceDownloadButton();

        binding.layoutProvinceBtns.setVisibility(View.VISIBLE);
        if (viewModel.isProvincePaused()) {
            showProvincePaused();
        } else {
            binding.btnProvincePause.setVisibility(View.GONE);
            binding.btnProvinceResume.setVisibility(View.GONE);
            binding.btnProvinceCancel.setVisibility(View.GONE);
            binding.btnProvinceDownload.setVisibility(View.VISIBLE);
        }
    }

    private void updateProvinceSizeDescriptions() {
        int[] sizes = PROVINCE_SIZES_MB[selectedProvinceIndex];
        binding.descProvinceZ12.setText("~" + sizes[0] + " MB · " +
                getString(R.string.offline_zoom_z12_national_desc));
        binding.descProvinceZ13.setText("~" + sizes[1] + " MB · " +
                getString(R.string.offline_zoom_z13_national_desc));
        binding.descProvinceZ14.setText("~" + sizes[2] + " MB · " +
                getString(R.string.offline_zoom_z14_national_desc));
    }

    private void updateProvinceDownloadButton() {
        String packKey = currentProvincePackKey();
        boolean alreadyDownloaded = viewModel.hasProvincePack(packKey);
        if (alreadyDownloaded) {
            String version = viewModel.getProvincePackVersion(packKey);
            int mb = fileSizeMb(viewModel.getProvincePackPath(packKey))
                    + fileSizeMb(viewModel.getProvinceBuildingsPath(packKey));
            binding.labelProvinceStatus.setText(
                    getString(R.string.offline_pack_downloaded,
                            provinceLabels()[selectedProvinceIndex], version, mb));
            binding.labelProvinceStatus.setVisibility(View.VISIBLE);
            binding.btnProvinceDownload.setEnabled(false);
        } else {
            binding.labelProvinceStatus.setVisibility(View.GONE);
            binding.btnProvinceDownload.setEnabled(true);
        }
    }

    private void confirmAndStartProvince() {
        String existingPackKey = findExistingProvincePackForCurrentProvince();
        if (existingPackKey != null) {
            String existingName = packDisplayName(existingPackKey);
            String newName = packDisplayName(currentProvincePackKey());
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.offline_replace_province_title)
                    .setMessage(getString(R.string.offline_replace_province_message,
                            existingName, newName))
                    .setPositiveButton(R.string.btn_download, (d, w) -> {
                        viewModel.deleteProvincePack(existingPackKey);
                        refreshDownloadedPacksList();
                        maybePromptMobileAndStartProvince();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            maybePromptMobileAndStartProvince();
        }
    }

    private void maybePromptMobileAndStartProvince() {
        if (binding.toggleMobileData.isChecked()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.warning_mobile_data_title)
                    .setMessage(R.string.warning_mobile_data_message)
                    .setPositiveButton(R.string.btn_download, (d, w) -> startProvince(true))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            startProvince(false);
        }
    }

    /** Returns the first downloaded pack key for the selected province at a different zoom, or null. */
    private String findExistingProvincePackForCurrentProvince() {
        String province = PROVINCE_KEYS[selectedProvinceIndex];
        String currentKey = currentProvincePackKey();
        Set<String> all = viewModel.getDownloadedProvincePackKeys();
        for (String key : all) {
            if (key.startsWith(province + "-") && !key.equals(currentKey)) {
                return key;
            }
        }
        return null;
    }

    private void startProvince(boolean allowMobile) {
        String packKey = currentProvincePackKey();
        String url = BASE_URL + "province-" + packKey + ".pmtiles";
        showProvinceDownloading();
        viewModel.startProvincePackDownload(packKey, url, DATA_VERSION, allowMobile);
    }

    private void maybePromptMobileAndResumeProvince() {
        if (binding.toggleMobileData.isChecked()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.warning_mobile_data_title)
                    .setMessage(R.string.warning_mobile_data_message)
                    .setPositiveButton(R.string.btn_resume, (d, w) -> resumeProvince(true))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            resumeProvince(false);
        }
    }

    private void resumeProvince(boolean allowMobile) {
        String packKey = viewModel.getProvincePausedKey();
        if (packKey.isEmpty()) packKey = currentProvincePackKey();
        String url = BASE_URL + "province-" + packKey + ".pmtiles";
        showProvinceDownloading();
        viewModel.resumeProvincePackDownload(packKey, url, DATA_VERSION, allowMobile);
    }

    private void showProvinceDownloading() {
        binding.btnProvinceDownload.setVisibility(View.GONE);
        binding.btnProvinceResume.setVisibility(View.GONE);
        binding.btnProvinceCancel.setVisibility(View.GONE);
        binding.btnProvincePause.setVisibility(View.VISIBLE);
        binding.progressProvince.setVisibility(View.VISIBLE);
        binding.progressProvince.setIndeterminate(true);
        binding.labelProvinceStatus.setText(R.string.offline_status_downloading);
        binding.labelProvinceStatus.setVisibility(View.VISIBLE);
    }

    private void showProvincePaused() {
        binding.progressProvince.setVisibility(View.GONE);
        binding.btnProvinceDownload.setVisibility(View.GONE);
        binding.btnProvincePause.setVisibility(View.GONE);
        binding.btnProvinceResume.setVisibility(View.VISIBLE);
        binding.btnProvinceCancel.setVisibility(View.VISIBLE);
        binding.labelProvinceStatus.setText(R.string.offline_status_paused);
        binding.labelProvinceStatus.setVisibility(View.VISIBLE);
    }

    private void onProvinceWorkInfo(WorkInfo info) {
        if (info == null) return;
        switch (info.getState()) {
            case ENQUEUED:
                if (binding.progressProvince.getVisibility() != View.VISIBLE) {
                    showProvinceDownloading();
                } else {
                    binding.progressProvince.setIndeterminate(true);
                    binding.labelProvinceStatus.setText(info.getRunAttemptCount() > 0
                            ? R.string.offline_status_retrying
                            : R.string.offline_status_downloading);
                }
                break;
            case RUNNING:
                applyProgress(
                        binding.progressProvince,
                        binding.labelProvinceStatus,
                        info.getProgress().getLong(TileDownloadWorker.KEY_BYTES_DONE, 0),
                        info.getProgress().getLong(TileDownloadWorker.KEY_BYTES_TOTAL, -1));
                binding.labelProvinceStatus.setVisibility(View.VISIBLE);
                break;
            case SUCCEEDED:
                binding.progressProvince.setVisibility(View.GONE);
                binding.btnProvincePause.setVisibility(View.GONE);
                binding.btnProvinceResume.setVisibility(View.GONE);
                binding.btnProvinceCancel.setVisibility(View.GONE);
                binding.btnProvinceDownload.setVisibility(View.VISIBLE);
                updateProvinceDownloadButton();
                refreshDownloadedPacksList();
                break;
            case FAILED:
                if (viewModel.isProvincePaused()) {
                    showProvincePaused();
                } else {
                    resetProvinceButtonsAfterStop();
                    showDownloadFailedSnackbar();
                }
                break;
            case CANCELLED:
                if (viewModel.isProvincePaused()) {
                    showProvincePaused();
                } else {
                    resetProvinceButtonsAfterStop();
                }
                break;
            default:
                break;
        }
    }

    private void resetProvinceButtonsAfterStop() {
        binding.progressProvince.setVisibility(View.GONE);
        binding.btnProvincePause.setVisibility(View.GONE);
        binding.btnProvinceResume.setVisibility(View.GONE);
        binding.btnProvinceCancel.setVisibility(View.GONE);
        binding.btnProvinceDownload.setVisibility(View.VISIBLE);
        binding.btnProvinceDownload.setEnabled(true);
        binding.labelProvinceStatus.setVisibility(View.GONE);
    }

    private void showDownloadFailedSnackbar() {
        if (binding == null) return;
        Snackbar.make(binding.getRoot(), R.string.offline_download_failed, Snackbar.LENGTH_LONG).show();
    }

    // ── Downloaded packs list ─────────────────────────────────────────────────

    private void refreshDownloadedPacksList() {
        binding.containerDownloadedPacks.removeAllViews();
        Set<String> packKeys = viewModel.getDownloadedProvincePackKeys();

        if (packKeys.isEmpty()) {
            binding.labelNoPacks.setVisibility(View.VISIBLE);
            return;
        }
        binding.labelNoPacks.setVisibility(View.GONE);

        List<String> sorted = new ArrayList<>(packKeys);
        Collections.sort(sorted);

        for (String packKey : sorted) {
            addPackRow(packKey);
        }
    }

    private void addPackRow(String packKey) {
        String displayName = packDisplayName(packKey);
        String version = viewModel.getProvincePackVersion(packKey);
        int mb = fileSizeMb(viewModel.getProvincePackPath(packKey))
                + fileSizeMb(viewModel.getProvinceBuildingsPath(packKey));

        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_province_pack, binding.containerDownloadedPacks, false);

        ((TextView) row.findViewById(R.id.label_pack))
                .setText(displayName + " · v" + version + " · " + mb + " MB");

        row.findViewById(R.id.btn_delete).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.confirm_delete_title)
                        .setMessage(R.string.confirm_delete_message)
                        .setPositiveButton(R.string.btn_delete, (d, w) -> {
                            viewModel.deleteProvincePack(packKey);
                            refreshDownloadedPacksList();
                            updateProvinceDownloadButton();
                        })
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show());

        binding.containerDownloadedPacks.addView(row);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String currentProvincePackKey() {
        return PROVINCE_KEYS[selectedProvinceIndex] + "-z" + selectedProvinceZoom;
    }

    private String[] provinceLabels() {
        return new String[]{
                getString(R.string.province_antananarivo),
                getString(R.string.province_fianarantsoa),
                getString(R.string.province_toamasina),
                getString(R.string.province_mahajanga),
                getString(R.string.province_toliara),
                getString(R.string.province_antsiranana)
        };
    }

    private String packDisplayName(String packKey) {
        // "antananarivo-z13" → "Antananarivo Z13"
        int dash = packKey.lastIndexOf('-');
        if (dash < 0) return packKey;
        String province = packKey.substring(0, dash);
        String zoom = packKey.substring(dash + 1).toUpperCase();
        for (int i = 0; i < PROVINCE_KEYS.length; i++) {
            if (PROVINCE_KEYS[i].equals(province)) {
                return provinceLabels()[i] + " " + zoom;
            }
        }
        String cap = province.isEmpty() ? "" :
                Character.toUpperCase(province.charAt(0)) + province.substring(1);
        return cap + " " + zoom;
    }

    private void applyProgress(
            com.google.android.material.progressindicator.LinearProgressIndicator bar,
            TextView label,
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

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
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
        binding = null;
    }
}
