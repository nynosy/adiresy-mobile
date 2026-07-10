package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SearchHistoryEntity entry);

    /** Returns the 20 most recent searches, observed as LiveData for the Search screen. */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    LiveData<List<SearchHistoryEntity>> getRecent();

    @Query("DELETE FROM search_history")
    void clearAll();
}
