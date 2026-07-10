package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** Shared shape for Region, District, Commune, and Fokontany list items. */
public class AdminUnitDto {
    @SerializedName("id")        public String id;
    @SerializedName("pcode")     public String pcode;
    @SerializedName("name")      public String name;
    @SerializedName("parent")    public String parentUuid;
    // bbox: [west, south, east, north] — may be null for some levels
    @SerializedName("bbox")      public double[] bbox;
    @SerializedName("centroid")  public double[] centroid;
}
