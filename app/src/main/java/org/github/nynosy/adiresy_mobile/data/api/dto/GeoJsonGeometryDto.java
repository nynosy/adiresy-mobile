package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/** Raw GeoJSON geometry returned by /geo/{level}/{pcode}/geometry/ endpoints. */
public class GeoJsonGeometryDto {
    @SerializedName("type")        public String type;
    @SerializedName("coordinates") public JsonElement coordinates;
}
