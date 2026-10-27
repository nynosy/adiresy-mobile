package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ListingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ListingEntity> listings);

    /** No-op if the id was never cached — exactly what "ignore unknown ids
     *  on a tombstone" (docs/api-reply-2026-10-27-tasks.md §1) needs. */
    @Query("DELETE FROM listings WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM listings ORDER BY name")
    List<ListingEntity> getAllSync();

    @Query("SELECT * FROM listings WHERE categoryUuid = :categoryUuid ORDER BY name")
    List<ListingEntity> getByCategorySync(String categoryUuid);

    @Query("SELECT COUNT(*) FROM listings")
    int countSync();
}
