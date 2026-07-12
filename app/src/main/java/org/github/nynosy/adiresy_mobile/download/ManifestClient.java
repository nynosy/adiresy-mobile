package org.github.nynosy.adiresy_mobile.download;

import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.github.nynosy.adiresy_mobile.data.api.dto.ManifestDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ManifestFileEntry;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches and parses adiresy-tiles' manifest.json, the source of truth for
 * download URLs and SHA-256 checksums (see docs/Issues.md issue 3 — the app
 * previously hardcoded URLs and never verified integrity, despite both
 * repos' docs claiming otherwise). Cached in memory for the process
 * lifetime: the manifest only changes on a quarterly release, and every
 * caller already tolerates a stale/missing manifest by falling back to
 * constructed URLs, so there's no need to re-fetch per screen visit.
 */
public final class ManifestClient {

    private static final String MANIFEST_URL =
            "https://github.com/nynosy/adiresy-tiles/releases/latest/download/manifest.json";

    private static volatile ManifestDto cached;

    private ManifestClient() {}

    /** Blocking — call from a background thread. Returns null on any
     *  failure (offline, malformed response, etc.); callers must fall back
     *  gracefully rather than treat a null manifest as an error. */
    public static ManifestDto fetchSync() {
        if (cached != null) return cached;
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(MANIFEST_URL).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            ManifestDto manifest = new Gson().fromJson(response.body().string(), ManifestDto.class);
            cached = manifest;
            return manifest;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves a download target for one (layer, scope, tier) combination.
     * @param layer manifest.files / manifest.buildings / manifest.poi (may be null)
     * @param scope "national" or a province key, e.g. "antananarivo"
     * @param tier  "z12" / "z13" / "z14"
     * @return the resolved target, or null if the manifest/layer/entry is unavailable
     */
    public static DownloadTarget resolve(ManifestDto.LayerDto layer, String scope, String tier) {
        if (layer == null) return null;
        Map<String, ManifestFileEntry> tiers = "national".equals(scope)
                ? layer.national
                : (layer.provinces != null ? layer.provinces.get(scope) : null);
        if (tiers == null) return null;
        ManifestFileEntry entry = tiers.get(tier);
        if (entry == null || entry.url == null) return null;
        return new DownloadTarget(entry.url, entry.sha256);
    }
}
