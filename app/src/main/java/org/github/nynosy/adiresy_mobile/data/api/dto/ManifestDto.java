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
     * Shape shared by files/buildings/poi: a "national" entry per tier.
     * Keyed by tier string ("z12", "z13") so new tiers don't need a schema
     * change. National-only — no region packs, see
     * docs/National-Only-Simplification-Implementation-Spec.md.
     */
    public static class LayerDto {
        @SerializedName("national") public Map<String, ManifestFileEntry> national;
    }
}
