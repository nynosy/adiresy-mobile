package org.github.nynosy.adiresy_mobile.data.api;

import android.content.Context;

import org.github.nynosy.adiresy_mobile.BuildConfig;
import org.github.nynosy.adiresy_mobile.data.api.dto.ApiResponse;
import org.github.nynosy.adiresy_mobile.data.api.dto.DeviceRegisterDto;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;

import retrofit2.Response;

/**
 * Ensures the app holds a valid per-install API token, obtained via
 * anonymous device registration (POST /api/v1/auth/device/register/)
 * instead of a single key baked into the build. No account or sign-in is
 * required, and registration counts against its own rate limit (10
 * registrations/hour/IP), separate from the per-token request limit.
 *
 * Registration is lazy — it happens the first time a caller is about to
 * make an authenticated request, never at app startup, so a fresh install
 * stays fully usable offline (NFR-1/NFR-7). It self-heals on a 401 by
 * discarding the stored token so the next call re-registers.
 */
public class DeviceAuthManager {

    private final Context context;
    private final AdiresyApi api;

    public DeviceAuthManager(Context context, AdiresyApi api) {
        this.context = context.getApplicationContext();
        this.api = api;
    }

    /** Blocking — call from a background thread before any authenticated
     *  request. No-op if a token is already stored. */
    public void ensureToken() {
        AppPrefs prefs = AppPrefs.get(context);
        if (prefs.getApiKey().isEmpty()) {
            register(prefs);
        }
    }

    /** Discards the current token. Call after a request comes back 401 so
     *  the next ensureToken() re-registers instead of resending a dead key. */
    public void invalidateToken() {
        AppPrefs.get(context).setApiKey("");
    }

    private void register(AppPrefs prefs) {
        try {
            DeviceRegisterDto body = new DeviceRegisterDto();
            body.platform = "android";
            body.appVersion = BuildConfig.VERSION_NAME;
            Response<ApiResponse<DeviceRegisterDto>> resp = api.registerDevice(body).execute();
            ApiResponse<DeviceRegisterDto> envelope = resp.body();
            if (resp.isSuccessful() && envelope != null && envelope.isOk()
                    && envelope.data.token != null) {
                prefs.setApiKey(envelope.data.token);
            }
        } catch (Exception ignored) {
            // Offline, or the registration endpoint/its own rate limit was
            // hit — leave the token empty. The caller's request will fail
            // with a network/auth error like any other offline call, and
            // the next attempt retries registration.
        }
    }
}
