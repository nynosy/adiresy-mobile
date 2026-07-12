package org.github.nynosy.adiresy_mobile.ui.settings;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

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

        // Pre-select the button matching the system locale (falls back to "mg" —
        // see LocaleHelper.defaultLanguageCode()); the other two render outlined.
        String defaultLang = LocaleHelper.defaultLanguageCode();
        applySelectionStyle(binding.btnMalagasy, "mg".equals(defaultLang));
        applySelectionStyle(binding.btnFrench, "fr".equals(defaultLang));
        applySelectionStyle(binding.btnEnglish, "en".equals(defaultLang));

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

    /** Filled (colorPrimary fill) for the pre-selected button, outlined for the rest.
     *  Read from theme attrs via MaterialColors so it stays correct in light/dark. */
    private void applySelectionStyle(MaterialButton button, boolean selected) {
        int primary = MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimary);
        if (selected) {
            int onPrimary = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimary);
            button.setBackgroundTintList(ColorStateList.valueOf(primary));
            button.setStrokeWidth(0);
            button.setTextColor(onPrimary);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setStrokeColor(ColorStateList.valueOf(primary));
            button.setStrokeWidth(strokeWidthPx());
            button.setTextColor(primary);
        }
    }

    private int strokeWidthPx() {
        return Math.round(1 * getResources().getDisplayMetrics().density);
    }
}
