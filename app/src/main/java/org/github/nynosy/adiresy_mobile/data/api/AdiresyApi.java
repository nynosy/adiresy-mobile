package org.github.nynosy.adiresy_mobile.data.api;

import java.util.List;

import org.github.nynosy.adiresy_mobile.data.api.dto.AddressCodeDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.AdminUnitDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ApiResponse;
import org.github.nynosy.adiresy_mobile.data.api.dto.CategoryDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ClaimStatusDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.DeviceRegisterDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ListingDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.SearchResponseDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.GeoJsonGeometryDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.PaginatedDto;
import okhttp3.ResponseBody;
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
     *  subsequent request. See DeviceAuthManager for the retry/storage flow.
     *  Like every other endpoint, the response body is wrapped in the
     *  {"status":"ok","data":{...}} envelope — despite the live OpenAPI
     *  schema showing DeviceRegister unwrapped, the server always wraps it. */
    @POST("api/v1/auth/device/register/")
    Call<ApiResponse<DeviceRegisterDto>> registerDevice(@Body DeviceRegisterDto request);

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
    Call<ApiResponse<PaginatedDto<AdminUnitDto>>> listRegions(@Query("page") int page,
                                                 @Query("search") String search);

    @GET("api/v1/geo/regions/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> regionGeometry(@Path("pcode") String pcode);

    // ── Geo — districts ───────────────────────────────────────────────────────

    @GET("api/v1/geo/districts/")
    Call<ApiResponse<PaginatedDto<AdminUnitDto>>> listDistricts(@Query("region") String regionUuid,
                                                   @Query("page") int page);

    @GET("api/v1/geo/districts/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> districtGeometry(@Path("pcode") String pcode);

    // ── Geo — communes ────────────────────────────────────────────────────────

    @GET("api/v1/geo/communes/")
    Call<ApiResponse<PaginatedDto<AdminUnitDto>>> listCommunes(@Query("district") String districtUuid,
                                                  @Query("page") int page);

    @GET("api/v1/geo/communes/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> communeGeometry(@Path("pcode") String pcode);

    // ── Geo — fokontany ───────────────────────────────────────────────────────

    @GET("api/v1/geo/fokontany/")
    Call<ApiResponse<PaginatedDto<AdminUnitDto>>> listFokontany(@Query("commune") String communeUuid,
                                                   @Query("page") int page);

    @GET("api/v1/geo/fokontany/")
    Call<ApiResponse<PaginatedDto<AdminUnitDto>>> searchFokontany(@Query("search") String name);

    @GET("api/v1/geo/fokontany/{pcode}/geometry/")
    Call<GeoJsonGeometryDto> fokontanyGeometry(@Path("pcode") String pcode);

    // ── Directory (establishments / fiches) ────────────────────────────────────

    /** Full category set, unpaginated. A listing's {@code categorySlug} refers
     *  to one of these. */
    @GET("api/v1/categories/")
    Call<ApiResponse<List<CategoryDto>>> listCategories();

    /** Active, non-deleted directory listings. Every query param is optional —
     *  pass null to omit it (Retrofit drops null query params).
     *
     *  For offline sync pass {@code updatedSince} (ISO-8601, the last successful
     *  pull) with {@code ordering="updated_at"} for a stable cursor, and
     *  {@code pageSize} up to 200. For a proximity search pass {@code lat}+{@code lng}
     *  (both required together) with {@code radius} in metres. */
    @GET("api/v1/listings/")
    Call<ApiResponse<PaginatedDto<ListingDto>>> listListings(
            @Query("category")      String categoryUuid,
            @Query("commune")       String communeUuid,
            @Query("district")      String districtUuid,
            @Query("fokontany")     String fokontanyUuid,
            @Query("region")        String regionUuid,
            @Query("is_verified")   Boolean isVerified,
            @Query("search")        String search,
            @Query("lat")           Double lat,
            @Query("lng")           Double lng,
            @Query("radius")        Integer radiusMetres,
            @Query("ordering")      String ordering,
            @Query("updated_since") String updatedSince,
            @Query("page")          Integer page,
            @Query("page_size")     Integer pageSize);

    /** One listing, looked up by UUID or by slug. */
    @GET("api/v1/listings/{id}/")
    Call<ApiResponse<ListingDto>> getListing(@Path("id") String idOrSlug);

    /** Every active listing that has a location, as a single GeoJSON
     *  FeatureCollection (capped at 5000 features) — minimal per feature
     *  (slug, name, category icon, logo). Returned raw rather than decoded so
     *  the repository can hand it straight to MapLibre; the envelope shape is
     *  unconfirmed, so the repo unwraps a {@code data} wrapper if present. */
    @GET("api/v1/listings/geo/")
    Call<ResponseBody> listingsGeo();

    /** Whether an OSM feature already has a fiche. Not on the primary path —
     *  see ClaimStatusDto. */
    @GET("api/v1/osm/claim-status/")
    Call<ApiResponse<ClaimStatusDto>> claimStatus(@Query("osm_type") String osmType,
                                                  @Query("osm_id") long osmId);

    // ── Search ────────────────────────────────────────────────────────────────

    @GET("api/v1/search/autocomplete/")
    Call<SearchResponseDto> autocomplete(@Query("q") String query,
                                         @Query("limit") int limit);

    // ── Health ────────────────────────────────────────────────────────────────

    @GET("api/v1/health/")
    Call<Void> healthCheck();
}
