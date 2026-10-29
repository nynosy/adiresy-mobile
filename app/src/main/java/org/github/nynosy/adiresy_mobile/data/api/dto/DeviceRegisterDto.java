package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Request/response body for POST /api/v1/auth/device/register/.
 *
 *  {@code challengeId}/{@code attestationChain} are set on the request when
 *  registering with Android Key Attestation (see GitHub issue #5 and
 *  DeviceAuthManager); both are null on an unattested registration, which the
 *  server still accepts — see that class's doc for when each path is taken. */
public class DeviceRegisterDto {
    @SerializedName("platform")           public String platform;
    @SerializedName("app_version")        public String appVersion;
    @SerializedName("challenge_id")       public String challengeId;
    /** Base64 DER certs from {@code KeyStore.getCertificateChain(...)}, leaf first. */
    @SerializedName("attestation_chain")  public List<String> attestationChain;

    @SerializedName("token")              public String token;
    /** True if the server accepted the attestation chain as genuine. Absent/false
     *  on an unattested registration — not itself an error (report mode). */
    @SerializedName("attested")           public boolean attested;
}
