package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** Whether an OSM feature is already backed by a directory listing.
 *  Returned by {@code GET /api/v1/osm/claim-status/?osm_type=&osm_id=}.
 *
 *  Not used on the app's primary path — fiche markers are driven off
 *  {@code /listings/geo/} instead (see docs/directory-api-integration.md §5 Q3).
 *  Kept for a possible future "badge OSM POIs that lack a fiche" feature. */
public class ClaimStatusDto {
    public static final String CLAIMED   = "claimed";
    public static final String PENDING   = "pending";
    public static final String UNCLAIMED = "unclaimed";

    @SerializedName("state")        public String state;
    @SerializedName("listing_slug") public String listingSlug;
    @SerializedName("listing_id")   public String listingId;
}
