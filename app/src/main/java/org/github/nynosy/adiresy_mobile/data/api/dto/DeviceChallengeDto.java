package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** Response body for POST /api/v1/auth/device/challenge/ — step 1 of an
 *  attested device registration (see GitHub issue #5). Single-use; pass
 *  {@code challenge} (base64) to {@code KeyGenParameterSpec.Builder
 *  .setAttestationChallenge()}, then send the resulting key's certificate
 *  chain to {@code /device/register/} with {@code challengeId} before
 *  {@code expiresIn} seconds. */
public class DeviceChallengeDto {
    @SerializedName("challenge_id") public String challengeId;
    /** Base64, 32 bytes. */
    @SerializedName("challenge")    public String challenge;
    /** Seconds. */
    @SerializedName("expires_in")   public int expiresIn;
}
