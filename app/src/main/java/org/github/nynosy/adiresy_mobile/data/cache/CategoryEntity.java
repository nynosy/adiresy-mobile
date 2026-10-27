package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Offline cache of a directory category, refreshed in full on every
 *  listings sync (no delta semantics — the category set is small and
 *  unpaginated). */
@Entity(tableName = "categories")
public class CategoryEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String name;
    public String slug;
    public String icon;
    public String color;
    public String description;

    public long cachedAt;
}
