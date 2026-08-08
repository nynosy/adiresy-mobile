package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** Shared shape for Region, District, Commune, and Fokontany list items.
 *  The parent's pcode is returned under a level-specific key (a district's
 *  parent region is "region_pcode", a commune's parent district is
 *  "district_pcode", a fokontany's parent commune is "commune_pcode") —
 *  there is no generic "parent" field. */
public class AdminUnitDto {
    @SerializedName("id")             public String id;
    @SerializedName("pcode")          public String pcode;
    @SerializedName("name")           public String name;
    @SerializedName("region_pcode")   public String regionPcode;
    @SerializedName("district_pcode") public String districtPcode;
    @SerializedName("commune_pcode")  public String communePcode;
    // bbox: [west, south, east, north] — may be null for some levels
    @SerializedName("bbox")           public double[] bbox;
    @SerializedName("centroid")       public double[] centroid;
}
