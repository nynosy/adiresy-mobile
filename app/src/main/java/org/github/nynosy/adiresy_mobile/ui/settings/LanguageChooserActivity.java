package org.github.nynosy.adiresy_mobile.ui.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.github.nynosy.adiresy_mobile.MainActivity;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import org.github.nynosy.adiresy_mobile.databinding.ActivityLanguageChooserBinding;
import org.github.nynosy.adiresy_mobile.i18n.LocaleHelper;

public class LanguageChooserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityLanguageChooserBinding binding =
                ActivityLanguageChooserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pre-select the system-locale language button
        String defaultLang = LocaleHelper.defaultLanguageCode();
        if ("fr".equals(defaultLang)) {
            applyOutlineStyle(binding);
        } else if ("mg".equals(defaultLang)) {
            applyOutlineStyle(binding);
        }

        binding.btnEnglish.setOnClickListener(v -> choose("en"));
        binding.btnFrench.setOnClickListener(v -> choose("fr"));
        binding.btnMalagasy.setOnClickListener(v -> choose("mg"));
    }

    private void choose(String lang) {
        AppPrefs prefs = AppPrefs.get(this);
        prefs.setLanguage(lang);
        prefs.setFirstRunComplete();
        LocaleHelper.apply(lang);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // Makes the default-language button filled, others outlined — no-op for now;
    // button styles are set in XML. Real visual selection added in M6 polish pass.
    private void applyOutlineStyle(ActivityLanguageChooserBinding b) {}
}
