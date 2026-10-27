package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** A directory category. A listing's {@code category_slug} refers to one of these.
 *  Returned unpaginated by {@code GET /api/v1/categories/}. */
public class CategoryDto {
    @SerializedName("id")          public String id;
    @SerializedName("name")        public String name;
    @SerializedName("slug")        public String slug;
    // Emoji or short glyph shown in the category grid / on the map pin.
    @SerializedName("icon")        public String icon;
    // Hex accent colour (e.g. "#4caf50") — category border, pin outline, pin label.
    @SerializedName("color")       public String color;
    @SerializedName("description") public String description;
}
