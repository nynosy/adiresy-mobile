package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** A single review on a {@link ListingDto}'s detail response. Read-only — no
 *  endpoint exists yet for the app to submit one. */
public class ReviewDto {
    @SerializedName("id")          public String id;
    @SerializedName("author_name") public String authorName;
    /** 1–5. */
    @SerializedName("rating")      public int rating;
    @SerializedName("comment")     public String comment;
    @SerializedName("created_at")  public String createdAt;
}
