package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AdminUnitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AdminUnitEntity> units);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AdminUnitEntity unit);

    @Query("SELECT * FROM admin_units WHERE type = :type ORDER BY name")
    List<AdminUnitEntity> findByType(String type);

    @Query("SELECT * FROM admin_units WHERE parentPcode = :parentUuid ORDER BY name")
    List<AdminUnitEntity> findByParent(String parentUuid);

    @Query("SELECT * FROM admin_units WHERE pcode = :pcode LIMIT 1")
    AdminUnitEntity findByPcode(String pcode);

    @Query("SELECT * FROM admin_units WHERE uuid = :uuid LIMIT 1")
    AdminUnitEntity findByUuid(String uuid);

    /** Update only the geometry field for an existing record. */
    @Query("UPDATE admin_units SET geometryJson = :geometryJson WHERE pcode = :pcode")
    void updateGeometry(String pcode, String geometryJson);

    @Query("DELETE FROM admin_units WHERE cachedAt < :cutoff")
    void evictExpired(long cutoff);
}
