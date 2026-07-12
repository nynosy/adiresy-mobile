package org.github.nynosy.adiresy_mobile.data.api;

import android.content.Context;

import java.io.IOException;

import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches the per-install device token (obtained via anonymous device
 * registration — see DeviceAuthManager) to every request. Read fresh from
 * AppPrefs on each call, rather than fixed at construction time, so a token
 * obtained after ApiClient was built — or rotated after a 401 — is picked
 * up immediately without rebuilding the client.
 */
public class ApiKeyInterceptor implements Interceptor {

    private final Context context;

    public ApiKeyInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String key = AppPrefs.get(context).getApiKey();
        Request.Builder builder = chain.request().newBuilder();
        if (!key.isEmpty()) {
            builder.addHeader("X-Adiresy-Key", key);
        }
        return chain.proceed(builder.build());
    }
}
