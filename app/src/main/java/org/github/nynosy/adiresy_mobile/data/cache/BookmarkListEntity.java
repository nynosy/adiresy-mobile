package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmark_lists")
public class BookmarkListEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "description")
    public String description;

    @NonNull
    @ColumnInfo(name = "emoji")
    public String emoji = "📍";

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
