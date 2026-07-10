package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookmarkListDao {

    @Query("SELECT bl.*, COUNT(b.id) AS bookmarkCount " +
           "FROM bookmark_lists bl " +
           "LEFT JOIN bookmarks b ON b.list_id = bl.id " +
           "GROUP BY bl.id ORDER BY bl.created_at ASC")
    LiveData<List<BookmarkListWithCount>> getAllListsWithCount();

    @Query("SELECT * FROM bookmark_lists ORDER BY created_at ASC")
    List<BookmarkListEntity> getAllListsSync();

    @Query("SELECT * FROM bookmark_lists WHERE id = :id LIMIT 1")
    BookmarkListEntity getListByIdSync(long id);

    @Insert
    long insertList(BookmarkListEntity list);

    @Update
    void updateList(BookmarkListEntity list);

    @Delete
    void deleteList(BookmarkListEntity list);
}
