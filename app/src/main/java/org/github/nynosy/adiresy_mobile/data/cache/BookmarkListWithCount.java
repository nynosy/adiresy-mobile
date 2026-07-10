package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

public class BookmarkListWithCount {
    @Embedded
    public BookmarkListEntity list;

    @ColumnInfo(name = "bookmarkCount")
    public int bookmarkCount;
}
