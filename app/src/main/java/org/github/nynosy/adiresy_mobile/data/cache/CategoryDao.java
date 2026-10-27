package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CategoryEntity> categories);

    @Query("SELECT * FROM categories ORDER BY name")
    List<CategoryEntity> getAllSync();
}
