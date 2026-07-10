package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchHistoryEntity {
    /** The raw query string — used as PK so duplicates update rather than insert. */
    @PrimaryKey
    @NonNull
    public String query = "";

    public long timestamp;
}
