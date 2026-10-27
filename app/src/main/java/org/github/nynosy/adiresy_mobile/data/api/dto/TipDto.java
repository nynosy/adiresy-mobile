package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** A short visitor tip on a {@link ListingDto}'s detail response. Read-only —
 *  no endpoint exists yet for the app to submit one. */
public class TipDto {
    @SerializedName("id")          public String id;
    @SerializedName("author_name") public String authorName;
    @SerializedName("text")        public String text;
    @SerializedName("likes")       public int likes;
    @SerializedName("created_at")  public String createdAt;
}
