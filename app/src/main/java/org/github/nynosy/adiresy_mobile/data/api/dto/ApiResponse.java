package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

public class ApiResponse<T> {
    @SerializedName("status") public String status;
    @SerializedName("data")   public T data;

    public boolean isOk() {
        return "ok".equals(status) && data != null;
    }
}
