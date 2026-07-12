package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** Request/response body for POST /api/v1/auth/device/register/. */
public class DeviceRegisterDto {
    @SerializedName("platform")    public String platform;
    @SerializedName("app_version") public String appVersion;
    @SerializedName("token")       public String token;
}
