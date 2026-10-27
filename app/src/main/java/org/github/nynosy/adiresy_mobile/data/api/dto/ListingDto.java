package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A directory listing (establishment / "fiche").
 *
 *  Maps the {@code ListingList} schema returned by {@code GET /api/v1/listings/}.
 *  {@code GET /api/v1/listings/{id}/} ({@code ListingDetail}) returns every list
 *  field plus the detail-only block below — both schemas confirmed against the
 *  live OpenAPI spec 2026-10-27 (see docs/api-reply-2026-10-27-tasks.md §2). */
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
    // Always present on a delta/tombstone row (see updated_since); absent (so
    // Java-default false, correctly) on every normal row.
    @SerializedName("is_deleted")          public boolean isDeleted;

    @SerializedName("cover_image")         public String coverImage;
    @SerializedName("logo")                public String logo;
    @SerializedName("average_rating")      public Double averageRating;

    @SerializedName("updated_at")          public String updatedAt;

    // ── Detail-only fields (GET /listings/{id}/) ────────────────────────────────

    @SerializedName("description")         public String description;
    @SerializedName("email")               public String email;

    // Untyped/nullable in the schema (no `type`, no nested `$ref`) — genuinely
    // ambiguous shape (plain string? structured per-day object?). Deliberately
    // not mapped to a typed field yet; ask the dev before guessing a shape that
    // would need an incompatible migration later.
    // See docs/api-reply-2026-10-27-tasks.md §2 "opening_hours".

    /** Set once a listing has been claimed by an owner account. */
    @SerializedName("owner_id")            public String ownerId;
    /** "claimed" | "pending" | "unclaimed" — the *listing's* claim state (is it
     *  owner-claimed). Not to be confused with {@link ClaimStatusDto#state},
     *  which is the *OSM feature's* claim state (does it have a fiche at all) —
     *  same enum values, different subject. See
     *  docs/api-reply-2026-10-27-tasks.md §2. */
    @SerializedName("claim_state")         public String claimState;
    @SerializedName("pending_claim_count") public Integer pendingClaimCount;

    @SerializedName("reviews")             public List<ReviewDto> reviews;
    @SerializedName("tips")                public List<TipDto> tips;
}
