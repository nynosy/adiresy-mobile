package org.github.nynosy.adiresy_mobile.data.api;

import org.github.nynosy.adiresy_mobile.data.api.dto.AddressCodeDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.AdminUnitDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ApiResponse;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.DeviceRegisterDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.SearchResponseDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.GeoJsonGeometryDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.PaginatedDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

// Base URL: https://adiresy.mg/
public interface AdiresyApi {

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** First-launch anonymous device registration — no account required.
     *  Returns a per-install token to send as X-Adiresy-Key on every
     *  subsequent request. See DeviceAuthManager for the retry/storage flow. */
    @POST("api/v1/auth/device/register/")
    Call<DeviceRegisterDto> registerDevice(@Body DeviceRegisterDto request);

    // ── Addresses ─────────────────────────────────────────────────────────────

    @GET("api/v1/addresses/{canonical_code}/")
    Call<ApiResponse<AddressCodeDto>> resolveCode(@Path("canonical_code") String code);

    @GET("api/v1/addresses/reverse/")
    Call<ApiResponse<AddressCodeDto>> reverseGeocode(@Query("lat") double lat,
                                                     @Query("lng") double lng,
                                                     @Query("limit") int limit,
                                                     @Query("radius") int radiusMetres);

    /** Pass exactly one of fokontanyUuid / fokontanyPcode — the API now
     *  distinguishes the two instead of accepting either under `fokontany`. */
    @GET("api/v1/addresses/")
    Call<PaginatedDto<AddressCodeDto>> addressesByFokontany(
            @Query("fokontany") String fokontanyUuid,
            @Query("fokontany_pcode") String fokontanyPcode,
            @Query("page") int page);

    // ── Geo — regions ─────────────────────────────────────────────────────────

    @GET("api/v1/geo/regions/")
    Call<PaginatedDto<AdminUnitDto>> listRegions(@Query("page") int page,
                                                 @Query("search") String search);

    @GET("api/v1/geo/regions/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> regionGeometry(@Path("pcode") String pcode);

    // ── Geo — districts ───────────────────────────────────────────────────────

    @GET("api/v1/geo/districts/")
    Call<PaginatedDto<AdminUnitDto>> listDistricts(@Query("region") String regionUuid,
                                                   @Query("page") int page);

    @GET("api/v1/geo/districts/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> districtGeometry(@Path("pcode") String pcode);

    // ── Geo — communes ────────────────────────────────────────────────────────

    @GET("api/v1/geo/communes/")
    Call<PaginatedDto<AdminUnitDto>> listCommunes(@Query("district") String districtUuid,
                                                  @Query("page") int page);

    @GET("api/v1/geo/communes/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> communeGeometry(@Path("pcode") String pcode);

    // ── Geo — fokontany ───────────────────────────────────────────────────────

    @GET("api/v1/geo/fokontany/")
    Call<PaginatedDto<AdminUnitDto>> listFokontany(@Query("commune") String communeUuid,
                                                   @Query("page") int page);

    @GET("api/v1/geo/fokontany/")
    Call<PaginatedDto<AdminUnitDto>> searchFokontany(@Query("search") String name);

    @GET("api/v1/geo/fokontany/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> fokontanyGeometry(@Path("pcode") String pcode);

    // ── Search ────────────────────────────────────────────────────────────────

    @GET("api/v1/search/autocomplete/")
    Call<SearchResponseDto> autocomplete(@Query("q") String query,
                                         @Query("limit") int limit);

    // ── Health ────────────────────────────────────────────────────────────────

    @GET("api/v1/health/")
    Call<Void> healthCheck();
}
