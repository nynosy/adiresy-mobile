package org.github.nynosy.adiresy_mobile.data.api;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import org.github.nynosy.adiresy_mobile.BuildConfig;
import org.github.nynosy.adiresy_mobile.data.api.dto.ApiResponse;
import org.github.nynosy.adiresy_mobile.data.api.dto.DeviceChallengeDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.DeviceRegisterDto;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;

import retrofit2.Response;
import timber.log.Timber;

/**
 * Ensures the app holds a valid per-install API token, obtained via
 * anonymous device registration (POST /api/v1/auth/device/register/)
 * instead of a single key baked into the build. No account or sign-in is
 * required, and registration counts against its own rate limit (10
 * registrations/hour/IP), separate from the per-token request limit.
 *
 * <p>Registration is lazy — it happens the first time a caller is about to
 * make an authenticated request, never at app startup, so a fresh install
 * stays fully usable offline (NFR-1/NFR-7). It self-heals on a 401 by
 * discarding the stored token so the next call re-registers.
 *
 * <p><b>Android Key Attestation</b> (github.com/nynosy/adiresy-mobile/issues/5):
 * registration first tries an attested flow — fetch a single-use challenge,
 * generate an {@code AndroidKeyStore} key over it, send the certificate chain
 * back with the register call. The server runs in <i>report</i> mode: both an
 * attested and an unattested registration succeed today; {@code attested}
 * just records which kind it was. Enforcement (refusing unattested
 * registrations) switches on later, on the dev's own timeline. If anything in
 * the attestation path fails locally — keystore unavailable, generation
 * error, challenge fetch fails — this falls back to a plain unattested
 * registration rather than leaving the app without a token: attestation
 * strengthens the key, it was never meant to gate whether the app works.
 */
public class DeviceAuthManager {

    private static final String KEY_ALIAS = "adiresy_device";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

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
        DeviceRegisterDto body = attestedRegistrationBody();
        if (body == null) {
            body = plainRegistrationBody();
        }
        submitRegistration(prefs, body, /* alreadyRetried= */ false);
    }

    private static DeviceRegisterDto plainRegistrationBody() {
        DeviceRegisterDto body = new DeviceRegisterDto();
        body.platform = "android";
        body.appVersion = BuildConfig.VERSION_NAME;
        return body;
    }

    /** Fetches a challenge and generates an attested key over it. Returns
     *  null (never throws) if any step fails, so the caller can fall back to
     *  an unattested registration instead of registering nothing at all. */
    @Nullable
    private DeviceRegisterDto attestedRegistrationBody() {
        try {
            Response<ApiResponse<DeviceChallengeDto>> resp = api.deviceChallenge().execute();
            ApiResponse<DeviceChallengeDto> envelope = resp.body();
            if (!resp.isSuccessful() || envelope == null || !envelope.isOk()) {
                return null;
            }
            DeviceChallengeDto challenge = envelope.data;
            List<String> chain = generateAttestedChain(challenge.challenge);
            if (chain == null) return null;

            DeviceRegisterDto body = plainRegistrationBody();
            body.challengeId = challenge.challengeId;
            body.attestationChain = chain;
            return body;
        } catch (Exception e) {
            Timber.w(e, "Attested registration setup failed, falling back to unattested");
            return null;
        }
    }

    private void submitRegistration(AppPrefs prefs, DeviceRegisterDto body, boolean alreadyRetried) {
        try {
            Response<ApiResponse<DeviceRegisterDto>> resp = api.registerDevice(body).execute();
            ApiResponse<DeviceRegisterDto> envelope = resp.body();
            if (resp.isSuccessful() && envelope != null && envelope.isOk()
                    && envelope.data.token != null) {
                prefs.setApiKey(envelope.data.token);
                if (BuildConfig.DEBUG) {
                    Timber.w("Registered — attested=" + envelope.data.attested
                            + (body.challengeId != null ? " (attestation attempted)" : " (no challenge)"));
                }
                return;
            }
            // 400 on an attested attempt means the challenge expired or was already
            // used — fetch a fresh one and retry exactly once (see issue #5's
            // response-handling table). Any other outcome (including 403 — "device
            // can't prove it's the genuine app") is left to fail: the token stays
            // empty, the caller's own request fails the way it would for any other
            // auth error, and the next trigger starts over from a fresh attempt.
            if (resp.code() == 400 && body.challengeId != null && !alreadyRetried) {
                DeviceRegisterDto retryBody = attestedRegistrationBody();
                if (retryBody != null) {
                    submitRegistration(prefs, retryBody, true);
                }
            }
        } catch (Exception ignored) {
            // Offline, or the registration endpoint/its own rate limit was
            // hit — leave the token empty. The caller's request will fail
            // with a network/auth error like any other offline call, and
            // the next attempt retries registration.
        }
    }

    /** Base64 DER certs from {@code KeyStore.getCertificateChain}, leaf first;
     *  null on any failure. A fresh key is generated for every attempt — the
     *  challenge is single-use, so there's no key to reuse between calls. */
    @Nullable
    private List<String> generateAttestedChain(String challengeBase64) {
        try {
            byte[] challengeBytes = Base64.decode(challengeBase64, Base64.DEFAULT);
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            ks.deleteEntry(KEY_ALIAS);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                generateStrongBoxPreferredKeyPair(challengeBytes);
            } else {
                generateKeyPair(challengeBytes, false);
            }

            Certificate[] chain = ks.getCertificateChain(KEY_ALIAS);
            if (chain == null || chain.length == 0) return null;
            List<String> encoded = new ArrayList<>();
            for (Certificate c : chain) {
                encoded.add(Base64.encodeToString(c.getEncoded(), Base64.NO_WRAP));
            }
            return encoded;
        } catch (Exception e) {
            Timber.w(e, "Attested key generation failed");
            return null;
        }
    }

    /** Kept in its own {@code @RequiresApi} method, only ever called from
     *  behind the SDK_INT check above, rather than inlined into
     *  {@link #generateAttestedChain}: a {@code catch} clause naming an
     *  exception type newer than minSdk (StrongBoxUnavailableException is
     *  API 28; minSdk is 26) can fail class verification on an older device
     *  even if that branch is never reached at runtime — unlike a plain
     *  SDK-gated method *call*, which Android resolves lazily. Isolating it
     *  in its own method keeps that verification from ever happening below
     *  API 28. */
    @RequiresApi(Build.VERSION_CODES.P)
    private void generateStrongBoxPreferredKeyPair(byte[] challengeBytes) throws Exception {
        try {
            generateKeyPair(challengeBytes, true);
        } catch (StrongBoxUnavailableException e) {
            generateKeyPair(challengeBytes, false);
        }
    }

    private void generateKeyPair(byte[] challengeBytes, boolean strongBox) throws Exception {
        KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challengeBytes);
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            spec.setIsStrongBoxBacked(true);
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE);
        kpg.initialize(spec.build());
        kpg.generateKeyPair();
    }
}
