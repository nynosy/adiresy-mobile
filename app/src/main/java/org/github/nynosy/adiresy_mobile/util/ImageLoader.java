package org.github.nynosy.adiresy_mobile.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal bitmap downloader for {@code cover_image}/{@code logo} URIs, deliberately
 * not using Glide/Coil — no image-loading library exists in the app today (≤25 MB
 * base install is a design constraint; see README "Design constraints"), and adding
 * one is a dependency decision that shouldn't be made silently as a side effect of
 * one feature.
 *
 * <p>Uses a plain {@link HttpURLConnection}, not the API's shared {@code OkHttpClient}
 * — that client's {@code ApiKeyInterceptor} attaches {@code X-Adiresy-Key} to every
 * request, and these image URLs may be served from a different host (CDN/object
 * storage) than the API itself. Sending the device key to a third-party host would
 * be a credential leak.
 */
public final class ImageLoader {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;
    /** A handful of small thumbnails per session — not a general-purpose cache. */
    private static final int CACHE_ENTRIES      = 24;

    private static final LruCache<String, Bitmap> cache = new LruCache<>(CACHE_ENTRIES);
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onLoaded(@Nullable Bitmap bitmap);
    }

    private ImageLoader() {}

    /** Always calls {@code callback} on the main thread, synchronously if cached. */
    public static void load(@Nullable String url, Callback callback) {
        if (url == null || url.isEmpty()) {
            callback.onLoaded(null);
            return;
        }
        Bitmap cached = cache.get(url);
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }
        executor.execute(() -> {
            Bitmap bitmap = fetch(url);
            if (bitmap != null) cache.put(url, bitmap);
            mainHandler.post(() -> callback.onLoaded(bitmap));
        });
    }

    @Nullable
    private static Bitmap fetch(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "image/*");
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
