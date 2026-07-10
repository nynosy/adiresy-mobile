package org.github.nynosy.adiresy_mobile.data.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "admin_units")
public class AdminUnitEntity {

    /** p-code (e.g. "MG.AN") — unique across all levels. */
    @PrimaryKey
    @NonNull
    public String pcode = "";

    /** "region" | "district" | "commune" | "fokontany" */
    public String type;
    public String uuid;
    public String name;
    @ColumnInfo(name = "parentPcode")
    public String parentUuid;

    /** Serialized double[4] bounding box as JSON string, nullable. */
    public String bboxJson;

    /** Serialized double[2] centroid as JSON string, nullable. */
    public String centroidJson;

    /** Full GeoJSON geometry string, null until geometry endpoint is called. */
    public String geometryJson;

    public long cachedAt;
}
