package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE list_id = :listId ORDER BY saved_at DESC")
    LiveData<List<BookmarkEntity>> getBookmarksForList(long listId);

    @Query("SELECT * FROM bookmarks WHERE canonical_code = :code LIMIT 1")
    BookmarkEntity findByCodeSync(String code);

    @Query("SELECT COUNT(*) FROM bookmarks")
    int getTotalCountSync();

    @Query("SELECT * FROM bookmarks WHERE " +
           "canonical_code LIKE '%' || :q || '%' OR " +
           "name LIKE '%' || :q || '%' OR " +
           "user_description LIKE '%' || :q || '%' " +
           "ORDER BY saved_at DESC LIMIT 5")
    List<BookmarkEntity> searchBookmarksSync(String q);

    @Query("SELECT * FROM bookmarks WHERE " +
           "latitude BETWEEN :south AND :north AND " +
           "longitude BETWEEN :west AND :east")
    List<BookmarkEntity> getBookmarksInBoundsSync(double south, double north,
                                                   double west, double east);

    @Query("UPDATE bookmarks SET list_id = :newListId WHERE id = :bookmarkId")
    void moveToList(long bookmarkId, long newListId);

    @Query("UPDATE bookmarks SET user_description = :description WHERE id = :bookmarkId")
    void updateDescription(long bookmarkId, String description);

    @Insert
    long insertBookmark(BookmarkEntity bookmark);

    @Update
    void updateBookmark(BookmarkEntity bookmark);

    @Delete
    void deleteBookmark(BookmarkEntity bookmark);

    @Query("DELETE FROM bookmarks WHERE canonical_code = :code")
    void deleteByCode(String code);

    @Query("SELECT * FROM bookmarks ORDER BY saved_at DESC")
    List<BookmarkEntity> getAllBookmarksSync();
}
