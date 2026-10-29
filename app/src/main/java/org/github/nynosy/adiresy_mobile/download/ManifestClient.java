package org.github.nynosy.adiresy_mobile.download;

import com.google.gson.Gson;

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
 * repos' docs claiming otherwise).
 *
 * <p>Cached in memory with a short TTL, not indefinitely for the process
 * lifetime as a previous version of this class did — that meant a long-lived
 * (even just backgrounded, not force-stopped) process could never see a new
 * quarterly release without the user killing the app, which is exactly the
 * "I know there's a new release but the app still downloads the old one"
 * failure mode. A fetch failure (offline, etc.) falls back to returning
 * whatever's cached, even if expired — stale-but-checksummed beats nothing.
 */
public final class ManifestClient {

    private static final String MANIFEST_URL =
            "https://github.com/nynosy/adiresy-tiles/releases/latest/download/manifest.json";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

    private static volatile ManifestDto cached;
    private static volatile long cachedAt;

    private ManifestClient() {}

    /** Blocking — call from a background thread. Returns null only when
     *  there's no cached manifest at all and the fetch also failed; callers
     *  must fall back gracefully rather than treat a null manifest as an
     *  error. */
    public static ManifestDto fetchSync() {
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return cached;
        }
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(MANIFEST_URL).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return cached;
            ManifestDto manifest = new Gson().fromJson(response.body().string(), ManifestDto.class);
            cached = manifest;
            cachedAt = System.currentTimeMillis();
            return manifest;
        } catch (Exception e) {
            return cached;
        }
    }

    /**
     * Resolves a download target for one (layer, tier) combination. National-only
     * — no region scope, see docs/National-Only-Simplification-Implementation-Spec.md.
     * @param layer manifest.files / manifest.buildings / manifest.poi (may be null)
     * @param tier  "z12" / "z13"
     * @return the resolved target, or null if the manifest/layer/entry is unavailable
     */
    public static DownloadTarget resolve(ManifestDto.LayerDto layer, String tier) {
        if (layer == null || layer.national == null) return null;
        ManifestFileEntry entry = layer.national.get(tier);
        if (entry == null || entry.url == null) return null;
        return new DownloadTarget(entry.url, entry.sha256, entry.sizeBytes);
    }
}
