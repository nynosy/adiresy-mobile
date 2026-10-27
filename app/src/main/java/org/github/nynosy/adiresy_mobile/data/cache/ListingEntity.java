package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Offline cache of a directory listing ("fiche"), synced via
 * {@code GET /api/v1/listings/?updated_since=} (see
 * {@code AdiresyRepository.syncListingsBlocking}). A listing that's deactivated
 * or removed server-side is deleted from this table outright, not soft-flagged
 * — the dev's delta-sync tombstones are a drop signal, not a status to carry
 * around locally. See docs/api-reply-2026-10-27-tasks.md §1.
 */
@Entity(
    tableName = "listings",
    indices = {
        @Index("categoryUuid"),
        @Index("communeName")
    }
)
public class ListingEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String name;
    public String slug;

    public String categoryUuid;
    public String categoryName;
    public String categoryIcon;
    public String categorySlug;

    public String addressCode;
    public Double latitude;
    public Double longitude;

    public String communeName;
    public String communeShortCode;
    public String districtName;
    public String districtShortCode;

    public String phone;
    public String website;
    public String legacyAddress;

    public boolean isVerified;

    public String coverImage;
    public String logo;
    public Double averageRating;

    /** This row's own server {@code updated_at} (ISO-8601) — not the sync
     *  cursor, which is tracked separately as the max seen across a full
     *  sync pass (see {@code AppPrefs.getListingsSyncCursor}). */
    public String updatedAt;

    public long cachedAt;
}
