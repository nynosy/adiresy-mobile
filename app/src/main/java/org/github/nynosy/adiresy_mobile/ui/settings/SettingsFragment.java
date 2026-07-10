package org.github.nynosy.adiresy_mobile.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import org.github.nynosy.adiresy_mobile.databinding.FragmentSettingsBinding;
import org.github.nynosy.adiresy_mobile.i18n.LocaleHelper;

public class SettingsFragment extends Fragment {

    private static final String FILE_PROVIDER_AUTHORITY =
            "org.github.nynosy.adiresy_mobile.fileprovider";

    private FragmentSettingsBinding binding;
    private AppPrefs prefs;
    private BookmarkRepository bookmarkRepository;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> importFilePicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) performImport(uri);
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = AppPrefs.get(requireContext());
        bookmarkRepository = BookmarkRepository.getInstance(requireContext());
        refreshLabels();

        binding.prefLanguage.setOnClickListener(v -> showLanguagePicker());
        binding.prefTheme.setOnClickListener(v -> showThemePicker());
        binding.prefExport.setOnClickListener(v -> performExport());
        binding.prefImport.setOnClickListener(v ->
                importFilePicker.launch(new String[]{"application/json", "*/*"}));
        binding.prefOffline.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container,
                            new org.github.nynosy.adiresy_mobile.ui.offline.OfflineDataFragment())
                    .addToBackStack(null)
                    .commit();
        });
        binding.prefAbout.setOnClickListener(v -> showAboutDialog());
        binding.prefPrivacy.setOnClickListener(v -> showPrivacyDialog());
    }

    private void performExport() {
        exportExecutor.execute(() -> {
            try {
                File exportFile = bookmarkRepository.exportJson();
                Uri contentUri = FileProvider.getUriForFile(
                        requireContext(), FILE_PROVIDER_AUTHORITY, exportFile);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                requireActivity().runOnUiThread(() ->
                        startActivity(Intent.createChooser(
                                shareIntent, getString(R.string.export_chooser_title))));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Snackbar.make(requireView(), R.string.import_error_file,
                                Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private void performImport(Uri uri) {
        exportExecutor.execute(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("null stream");
                BookmarkRepository.ImportResult result = bookmarkRepository.importJson(is);
                is.close();

                requireActivity().runOnUiThread(() -> {
                    if (result.schemaError) {
                        Snackbar.make(requireView(), R.string.import_error_schema,
                                Snackbar.LENGTH_LONG).show();
                    } else if (result.truncated > 0) {
                        Snackbar.make(requireView(),
                                getString(R.string.import_truncated, result.imported, result.truncated),
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        Snackbar.make(requireView(),
                                getString(R.string.import_success, result.imported),
                                Snackbar.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Snackbar.make(requireView(), R.string.import_error_file,
                                Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private void refreshLabels() {
        String lang = prefs.getLanguage();
        String langLabel = "en".equals(lang) ? "English"
                : "fr".equals(lang) ? "Français"
                : "Malagasy";
        binding.labelLanguageValue.setText(langLabel);

        String theme = prefs.getTheme();
        int themeLabel = AppPrefs.THEME_LIGHT.equals(theme) ? R.string.theme_light
                : AppPrefs.THEME_DARK.equals(theme) ? R.string.theme_dark
                : R.string.theme_auto;
        binding.labelThemeValue.setText(themeLabel);
    }

    private void showLanguagePicker() {
        String[] labels = {"Malagasy", "Français", "English"};
        String[] codes  = {"mg", "fr", "en"};
        String current = prefs.getLanguage();
        int checked = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(current)) { checked = i; break; }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    prefs.setLanguage(codes[which]);
                    LocaleHelper.apply(codes[which]);
                    dialog.dismiss();
                    requireActivity().recreate();
                })
                .show();
    }

    private void showThemePicker() {
        String[] labels = {
                getString(R.string.theme_auto),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)};
        String[] values = {AppPrefs.THEME_AUTO, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK};
        int[] modes = {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES};
        String current = prefs.getTheme();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) { checked = i; break; }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    prefs.setTheme(values[which]);
                    AppCompatDelegate.setDefaultNightMode(modes[which]);
                    dialog.dismiss();
                    refreshLabels();
                })
                .show();
    }

    private void showAboutDialog() {
        String message = getString(R.string.attribution_openmaptiles) + "\n"
                + getString(R.string.attribution_osm) + "\n"
                + getString(R.string.attribution_buildings) + "\n"
                + getString(R.string.attribution_admin) + "\n\n"
                + getString(R.string.attribution_unofficial);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_about)
                .setMessage(message)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    private void showPrivacyDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_privacy)
                .setMessage(R.string.privacy_body)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.title_settings);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
