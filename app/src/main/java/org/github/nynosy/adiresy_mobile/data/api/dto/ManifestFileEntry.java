package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** One downloadable file entry from adiresy-tiles' manifest.json. */
public class ManifestFileEntry {
    @SerializedName("filename")   public String filename;
    @SerializedName("url")        public String url;
    @SerializedName("size_bytes") public long sizeBytes;
    @SerializedName("sha256")     public String sha256;
}
