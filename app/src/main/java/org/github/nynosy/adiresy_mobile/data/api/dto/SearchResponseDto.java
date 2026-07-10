package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Matches the actual API response from /api/v1/search/autocomplete/ */
public class SearchResponseDto {
    @SerializedName("status") public String status;
    @SerializedName("data")   public Data   data;

    public static class Data {
        @SerializedName("regions")   public List<AdminResult>   regions;
        @SerializedName("districts") public List<AdminResult>   districts;
        @SerializedName("communes")  public List<AdminResult>   communes;
        @SerializedName("fokontany") public List<AdminResult>   fokontany;
        @SerializedName("addresses") public List<AddressResult> addresses;
        @SerializedName("others")    public List<OtherResult>   others;
    }

    public static class AdminResult {
        @SerializedName("type")       public String   type;       // "region"|"district"|"commune"|"fokontany"
        @SerializedName("name")       public String   name;
        @SerializedName("pcode")      public String   pcode;
        @SerializedName("short_code") public String   shortCode;
        @SerializedName("region")     public String   region;     // parent name for district
        @SerializedName("district")   public String   district;   // parent name for commune/fokontany
        @SerializedName("commune")    public String   commune;    // parent name for fokontany
        @SerializedName("bbox")       public double[] bbox;       // [west, south, east, north]
        @SerializedName("centroid")   public double[] centroid;   // [lng, lat]
        @SerializedName("score")      public double   score;
    }

    public static class AddressResult {
        @SerializedName("type")        public String  type;
        @SerializedName("code")        public String  code;
        @SerializedName("fokontany")   public String  fokontany;
        @SerializedName("commune")     public String  commune;
        @SerializedName("district")    public String  district;
        @SerializedName("lat")         public Double  lat;
        @SerializedName("lng")         public Double  lng;
        @SerializedName("is_verified") public boolean isVerified;
        @SerializedName("score")       public double  score;
    }

    public static class OtherResult {
        @SerializedName("type")         public String   type;
        @SerializedName("name")         public String   name;
        @SerializedName("display_name") public String   displayName;
        @SerializedName("sublabel")     public String   sublabel;
        @SerializedName("lat")          public Double   lat;
        @SerializedName("lng")          public Double   lng;
        @SerializedName("bbox")         public double[] bbox;
        @SerializedName("score")        public double   score;
    }
}
