package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AddressCodeDto {
    @SerializedName("id")              public String id;
    @SerializedName("canonical_code")  public String canonicalCode;
    @SerializedName("district_code")   public String districtCode;
    @SerializedName("commune_short")   public String communeShort;
    @SerializedName("serial")          public int serial;
    @SerializedName("fokontany")       public String fokontanyUuid;
    @SerializedName("fokontany_name")  public String fokontanyName;
    @SerializedName("commune_name")    public String communeName;
    @SerializedName("district_name")   public String districtName;
    @SerializedName("region_name")     public String regionName;
    @SerializedName("latitude")        public Double latitude;
    @SerializedName("longitude")       public Double longitude;
    @SerializedName("match")           public String match;
    @SerializedName("distance_m")      public Double distanceM;
    // Present when limit > 1; ranked by containment then distance
    @SerializedName("candidates")      public List<AddressCodeDto> candidates;
}
