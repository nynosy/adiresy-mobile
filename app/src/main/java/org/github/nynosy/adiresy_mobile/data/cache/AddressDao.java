package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AddressEntity address);

    @Query("SELECT * FROM addresses WHERE canonicalCode = :code LIMIT 1")
    AddressEntity findByCode(String code);

    @Query("SELECT * FROM addresses WHERE fokontanyUuid = :uuid ORDER BY canonicalCode")
    List<AddressEntity> findByFokontany(String uuid);

    /** Nearest cached address within a bounding-box approximation (~100 m). */
    @Query("SELECT * FROM addresses WHERE ABS(latitude - :lat) < :delta AND ABS(longitude - :lng) < :delta ORDER BY (latitude - :lat)*(latitude - :lat)+(longitude - :lng)*(longitude - :lng) LIMIT 1")
    AddressEntity findNearest(double lat, double lng, double delta);

    /** Remove entries older than the given epoch-millis cutoff (7-day TTL). */
    @Query("DELETE FROM addresses WHERE cachedAt < :cutoff")
    void evictExpired(long cutoff);
}
