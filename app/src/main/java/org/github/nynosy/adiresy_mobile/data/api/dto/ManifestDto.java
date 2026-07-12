package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * adiresy-tiles' manifest.json, fetched from the GitHub Release's
 * releases/latest/download/ URL. See ManifestClient for fetching/caching
 * and DownloadTarget resolution.
 */
public class ManifestDto {
    @SerializedName("version")    public String version;
    @SerializedName("generated")  public String generated;
    @SerializedName("files")      public LayerDto files;
    @SerializedName("buildings")  public LayerDto buildings;
    @SerializedName("poi")        public LayerDto poi;
    @SerializedName("boundaries") public ManifestFileEntry boundaries;

    /**
     * Shape shared by files/buildings/poi: a "national" entry per tier, plus
     * an optional per-province map of the same. Keyed by tier string ("z12",
     * "z13", "z14") so new tiers don't need a schema change.
     */
    public static class LayerDto {
        @SerializedName("national")  public Map<String, ManifestFileEntry> national;
        @SerializedName("provinces") public Map<String, Map<String, ManifestFileEntry>> provinces;
    }
}
