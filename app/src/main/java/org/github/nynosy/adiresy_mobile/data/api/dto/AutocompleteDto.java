package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AutocompleteDto {

    @SerializedName("results") public List<Item> results;

    public static class Item {
        @SerializedName("type")    public String type;   // "code" | "place" | "admin"
        @SerializedName("level")   public String level;  // "region"|"district"|"commune"|"fokontany"
        @SerializedName("label")   public String label;
        @SerializedName("code")    public String code;   // present when type == "code"
        @SerializedName("uuid")    public String uuid;   // present when type == "admin"
        @SerializedName("bbox")    public double[] bbox; // present when type == "admin"
        @SerializedName("lat")     public Double lat;
        @SerializedName("lng")     public Double lng;
    }
}
