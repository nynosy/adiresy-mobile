package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "addresses")
public class AddressEntity {
    @PrimaryKey
    @NonNull
    public String canonicalCode = "";

    public String fokontanyUuid;
    public String fokontanyName;
    public String communeName;
    public String districtName;
    public String regionName;
    public String districtCode;
    public String communeShort;
    public double latitude;
    public double longitude;
    public long cachedAt;
}
