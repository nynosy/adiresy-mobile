package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** A directory listing (establishment / "fiche").
 *
 *  Maps the {@code ListingList} schema returned by {@code GET /api/v1/listings/}.
 *  {@code GET /api/v1/listings/{id}/} returns a superset — any extra detail
 *  fields are simply ignored by Gson until we add them (the detail schema is
 *  still undocumented; see docs/directory-api-integration.md §5 Q2).
 *
 *  {@code description} below is wired up as a proof of concept for exactly one
 *  detail-only field — it's the most likely to exist (Category already has
 *  one) and nothing here breaks if it turns out to be absent or differently
 *  named, since FicheBottomSheet hides the row when it's blank.
 *
 *  TODO(directory-api #Q2): add the rest of the detail-only fields once the
 *  dev confirms them — at least opening_hours, a photo gallery beyond
 *  cover_image/logo, social_links, and reviews (see
 *  docs/directory-api-integration.md §5 Q2 for the question as sent). */
public class ListingDto {
    @SerializedName("id")                  public String id;
    @SerializedName("name")                public String name;
    @SerializedName("slug")                public String slug;

    @SerializedName("category")            public String categoryUuid;
    @SerializedName("category_name")       public String categoryName;
    @SerializedName("category_icon")       public String categoryIcon;
    @SerializedName("category_slug")       public String categorySlug;

    @SerializedName("address_code")        public String addressCode;
    @SerializedName("latitude")            public Double latitude;
    @SerializedName("longitude")           public Double longitude;

    @SerializedName("commune_name")        public String communeName;
    @SerializedName("commune_short_code")  public String communeShortCode;
    @SerializedName("district_name")       public String districtName;
    @SerializedName("district_short_code") public String districtShortCode;

    @SerializedName("phone")               public String phone;
    @SerializedName("website")             public String website;
    // Official Malagasy address ("Lot ...").
    @SerializedName("legacy_address")      public String legacyAddress;

    @SerializedName("is_verified")         public boolean isVerified;
    @SerializedName("is_active")           public boolean isActive;

    @SerializedName("cover_image")         public String coverImage;
    @SerializedName("logo")                public String logo;
    @SerializedName("average_rating")      public Double averageRating;

    @SerializedName("updated_at")          public String updatedAt;

    // ── Detail-only fields (GET /listings/{id}/), unconfirmed — see class doc ──

    /** Proof-of-concept detail field — null on the list response, present (if
     *  it exists at all) only on the detail response. */
    @SerializedName("description")         public String description;

    // TODO(directory-api #Q2): opening_hours
    // TODO(directory-api #Q2): photo gallery (beyond cover_image/logo)
    // TODO(directory-api #Q2): social_links
    // TODO(directory-api #Q2): reviews
}
