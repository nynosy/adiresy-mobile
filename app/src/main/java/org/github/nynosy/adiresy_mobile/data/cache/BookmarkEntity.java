package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "bookmarks",
    foreignKeys = @ForeignKey(
        entity = BookmarkListEntity.class,
        parentColumns = "id",
        childColumns = "list_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("list_id"),
        @Index(value = "canonical_code", unique = true)
    }
)
public class BookmarkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "canonical_code")
    public String canonicalCode = "";

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @ColumnInfo(name = "fokontany_name")
    public String fokontanyName;

    @ColumnInfo(name = "commune_name")
    public String communeName;

    @ColumnInfo(name = "district_name")
    public String districtName;

    @ColumnInfo(name = "region_name")
    public String regionName;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "user_description")
    public String userDescription;

    @ColumnInfo(name = "list_id")
    public long listId;

    @ColumnInfo(name = "saved_at")
    public long savedAt;
}
