package org.github.nynosy.adiresy_mobile.data.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PaginatedDto<T> {
    @SerializedName("count")    public int count;
    @SerializedName("next")     public String next;
    @SerializedName("previous") public String previous;
    @SerializedName("results")  public List<T> results;
}
