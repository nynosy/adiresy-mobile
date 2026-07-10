package org.github.nynosy.adiresy_mobile.i18n;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;

public class LocaleHelper {

    /**
     * Applies a locale by BCP-47 language tag ("en", "fr", "mg").
     * Pass "" to clear the per-app override and follow the system locale.
     * Call from Application.onCreate() and after every language change.
     */
    public static void apply(String languageTag) {
        LocaleListCompat locales = languageTag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    /**
     * Reads the stored language from AppPrefs and applies it.
     * Falls back to system locale if no preference is stored.
     */
    public static void applyFromPrefs(Context context) {
        String lang = AppPrefs.get(context).getLanguage();
        apply(lang);
    }

    /**
     * Returns the language code to pre-select in the first-run chooser.
     * Mirrors the system locale if it is one of en/fr/mg; defaults to "mg"
     * for any unknown locale (spec §10 — Malagasy is the primary local language).
     */
    public static String defaultLanguageCode() {
        LocaleListCompat system = LocaleListCompat.getAdjustedDefault();
        if (system.isEmpty()) return "mg";
        String lang = system.get(0).getLanguage();
        if ("fr".equals(lang) || "en".equals(lang) || "mg".equals(lang)) {
            return lang;
        }
        return "mg";
    }
}
