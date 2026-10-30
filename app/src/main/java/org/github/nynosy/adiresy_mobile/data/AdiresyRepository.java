package org.github.nynosy.adiresy_mobile.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.BuildConfig;
import org.github.nynosy.adiresy_mobile.data.api.AdiresyApi;
import org.github.nynosy.adiresy_mobile.data.api.ApiClient;
import org.github.nynosy.adiresy_mobile.data.api.DeviceAuthManager;
import org.github.nynosy.adiresy_mobile.data.api.dto.AddressCodeDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ApiResponse;
import org.github.nynosy.adiresy_mobile.data.api.dto.AdminUnitDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.CategoryDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ListingDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.SearchResponseDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.GeoJsonGeometryDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.PaginatedDto;
import org.github.nynosy.adiresy_mobile.data.cache.AddressDao;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitDao;
import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitEntity;
import org.github.nynosy.adiresy_mobile.data.cache.AppDatabase;
import org.github.nynosy.adiresy_mobile.data.cache.CategoryDao;
import org.github.nynosy.adiresy_mobile.data.cache.CategoryEntity;
import org.github.nynosy.adiresy_mobile.data.cache.ListingDao;
import org.github.nynosy.adiresy_mobile.data.cache.ListingEntity;
import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryDao;
import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryEntity;
import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class AdiresyRepository {

    /** Cache TTLs */
    private static final long TTL_ADDRESS_MS    = 7L  * 24 * 60 * 60 * 1000;
    private static final long TTL_ADMIN_MS      = 30L * 24 * 60 * 60 * 1000;

    private static volatile AdiresyRepository instance;

    private final AdiresyApi api;
    private final DeviceAuthManager deviceAuth;
    private final AddressDao addressDao;
    private final AdminUnitDao adminUnitDao;
    private final SearchHistoryDao searchHistoryDao;
    private final ListingDao listingDao;
    private final CategoryDao categoryDao;
    private final AppPrefs appPrefs;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Gson gson = new Gson();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private AdiresyRepository(Context context) {
        Context appCtx = context.getApplicationContext();
        api = ApiClient.get(appCtx);
        deviceAuth = new DeviceAuthManager(appCtx, api);
        AppDatabase db = AppDatabase.getInstance(appCtx);
        addressDao = db.addressDao();
        adminUnitDao = db.adminUnitDao();
        searchHistoryDao = db.searchHistoryDao();
        listingDao = db.listingDao();
        categoryDao = db.categoryDao();
        appPrefs = AppPrefs.get(appCtx);
    }

    public static AdiresyRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AdiresyRepository.class) {
                if (instance == null) instance = new AdiresyRepository(context);
            }
        }
        return instance;
    }

    // ── Address resolve ───────────────────────────────────────────────────────

    public LiveData<Result<AddressEntity>> resolveCode(String canonicalCode) {
        MutableLiveData<Result<AddressEntity>> out = new MutableLiveData<>();
        executor.execute(() -> {
            // 1. Check cache
            AddressEntity cached = addressDao.findByCode(canonicalCode);
            if (cached != null && !isExpired(cached.cachedAt, TTL_ADDRESS_MS)) {
                out.postValue(Result.success(cached));
                return;
            }
            // 2. Network
            try {
                deviceAuth.ensureToken();
                Response<ApiResponse<AddressCodeDto>> resp = api.resolveCode(canonicalCode).execute();
                ApiResponse<AddressCodeDto> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    AddressEntity entity = toEntity(body.data);
                    addressDao.insert(entity);
                    out.postValue(Result.success(entity));
                } else {
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    // Serve stale cache rather than an empty error if available
                    out.postValue(cached != null
                            ? Result.stale(cached)
                            : Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                out.postValue(cached != null
                        ? Result.stale(cached)
                        : Result.networkError(e));
            }
        });
        return out;
    }

    // ── Reverse geocode ───────────────────────────────────────────────────────

    private static final int TAP_RADIUS    = 100;
    private static final int NEARBY_LIMIT  = 20;
    private static final int NEARBY_RADIUS = 200;

    /** Single closest address within TAP_RADIUS metres — for building tap.
     *  Falls back to the nearest cached address if the network is unavailable. */
    public LiveData<Result<AddressEntity>> reverseGeocode(double lat, double lng) {
        // ~100 m expressed as degrees latitude (1° ≈ 111 km)
        final double CACHE_DELTA = TAP_RADIUS / 111_000.0;
        MutableLiveData<Result<AddressEntity>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                deviceAuth.ensureToken();
                Response<ApiResponse<AddressCodeDto>> resp =
                        api.reverseGeocode(lat, lng, 1, TAP_RADIUS).execute();
                ApiResponse<AddressCodeDto> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    AddressEntity entity = toEntity(body.data);
                    addressDao.insert(entity);
                    out.postValue(Result.success(entity));
                } else {
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    out.postValue(Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                // Network unavailable — try nearest cached address
                AddressEntity cached = addressDao.findNearest(lat, lng, CACHE_DELTA);
                if (cached != null && !isExpired(cached.cachedAt, TTL_ADDRESS_MS)) {
                    out.postValue(Result.stale(cached));
                } else {
                    out.postValue(Result.networkError(e));
                }
            }
        });
        return out;
    }

    /** Up to NEARBY_LIMIT addresses within NEARBY_RADIUS metres — for locate-me. */
    public LiveData<Result<List<AddressEntity>>> nearbyBuildings(double lat, double lng) {
        MutableLiveData<Result<List<AddressEntity>>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                deviceAuth.ensureToken();
                Response<ApiResponse<AddressCodeDto>> resp =
                        api.reverseGeocode(lat, lng, NEARBY_LIMIT, NEARBY_RADIUS).execute();
                ApiResponse<AddressCodeDto> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()
                        && body.data.candidates != null) {
                    List<AddressEntity> entities = new ArrayList<>();
                    for (AddressCodeDto dto : body.data.candidates) {
                        AddressEntity e = toEntity(dto);
                        addressDao.insert(e);
                        entities.add(e);
                    }
                    out.postValue(Result.success(entities));
                } else if (resp.code() == 404) {
                    out.postValue(Result.success(new ArrayList<>()));
                } else {
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    out.postValue(Result.error(
                            resp.code(), resp.message() != null ? resp.message() : "no candidates"));
                }
            } catch (Exception e) {
                out.postValue(Result.networkError(e));
            }
        });
        return out;
    }

    // ── Admin units ───────────────────────────────────────────────────────────

    public LiveData<Result<List<AdminUnitEntity>>> listRegions() {
        return listAdminUnits("region", null);
    }

    public LiveData<Result<List<AdminUnitEntity>>> listDistricts(String regionUuid) {
        return listAdminUnits("district", regionUuid);
    }

    public LiveData<Result<List<AdminUnitEntity>>> listCommunes(String districtUuid) {
        return listAdminUnits("commune", districtUuid);
    }

    public LiveData<Result<List<AdminUnitEntity>>> listFokontany(String communeUuid) {
        return listAdminUnits("fokontany", communeUuid);
    }

    private LiveData<Result<List<AdminUnitEntity>>> listAdminUnits(String type, String parentUuid) {
        MutableLiveData<Result<List<AdminUnitEntity>>> out = new MutableLiveData<>();
        executor.execute(() -> {
            List<AdminUnitEntity> cached = (parentUuid == null)
                    ? adminUnitDao.findByType(type)
                    : adminUnitDao.findByParent(parentUuid);

            boolean cacheValid = !cached.isEmpty()
                    && !isExpired(cached.get(0).cachedAt, TTL_ADMIN_MS);
            if (cacheValid) {
                out.postValue(Result.success(cached));
                return;
            }

            try {
                deviceAuth.ensureToken();
                PaginatedDto<AdminUnitDto> page = fetchAllAdminPages(type, parentUuid);
                if (page != null && page.results != null) {
                    List<AdminUnitEntity> entities = new ArrayList<>();
                    for (AdminUnitDto dto : page.results) {
                        entities.add(toAdminEntity(dto, type));
                    }
                    adminUnitDao.insertAll(entities);
                    out.postValue(Result.success(entities));
                } else {
                    out.postValue(!cached.isEmpty()
                            ? Result.stale(cached)
                            : Result.error(0, "empty response"));
                }
            } catch (Exception e) {
                out.postValue(!cached.isEmpty()
                        ? Result.stale(cached)
                        : Result.networkError(e));
            }
        });
        return out;
    }

    public LiveData<String> getGeometry(String type, String pcode) {
        MutableLiveData<String> out = new MutableLiveData<>();
        executor.execute(() -> {
            AdminUnitEntity cached = adminUnitDao.findByPcode(pcode);
            if (cached != null && cached.geometryJson != null) {
                out.postValue(cached.geometryJson);
                return;
            }
            try {
                deviceAuth.ensureToken();
                Response<GeoJsonGeometryDto> resp = geometryCall(type, pcode).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String json = gson.toJson(resp.body());
                    adminUnitDao.updateGeometry(pcode, json);
                    out.postValue(json);
                } else if (resp.code() == 401) {
                    deviceAuth.invalidateToken();
                }
            } catch (Exception ignored) {
                out.postValue(null);
            }
        });
        return out;
    }

    // ── Search autocomplete ───────────────────────────────────────────────────

    /** Autocomplete, with directory listings merged in client-side (the server
     *  endpoint doesn't carry them — see {@link #searchListingsForAutocomplete}).
     *  Fiches are placed first: they're the app's own content (establishments
     *  the user can actually visit/interact with), ahead of raw admin units
     *  and OSM places which exist mostly to geolocate a search, not to be
     *  destinations themselves. A listings-search failure (e.g. a
     *  key-permission gap — see docs/directory-api-integration.md §5 Q1)
     *  never fails the whole search; it just means no fiche results get
     *  merged in for that query. */
    public LiveData<Result<List<AutocompleteDto.Item>>> autocomplete(String query) {
        MutableLiveData<Result<List<AutocompleteDto.Item>>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                deviceAuth.ensureToken();
                retrofit2.Response<SearchResponseDto> resp = api.autocomplete(query, 20).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().data != null) {
                    List<AutocompleteDto.Item> items = searchListingsForAutocomplete(query);
                    items.addAll(flattenSearchResponse(resp.body().data));
                    out.postValue(Result.success(items));
                } else {
                    logDirectoryFailure("GET /search/ (autocomplete)", resp, null);
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    out.postValue(Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                logDirectoryFailure("GET /search/ (autocomplete)", null, e);
                out.postValue(Result.networkError(e));
            }
        });
        return out;
    }

    /** Up to this many fiches appended to a text search, after the address/admin
     *  results — directory search is a secondary result type here, not the
     *  primary one. */
    private static final int AUTOCOMPLETE_LISTINGS_LIMIT = 5;

    private List<AutocompleteDto.Item> searchListingsForAutocomplete(String query) {
        try {
            Response<ApiResponse<PaginatedDto<ListingDto>>> resp = api.listListings(
                    null, null, null, null, null, null, query,
                    null, null, null, null, null, 1, AUTOCOMPLETE_LISTINGS_LIMIT).execute();
            ApiResponse<PaginatedDto<ListingDto>> body = resp.body();
            if (!resp.isSuccessful() || body == null || !body.isOk() || body.data.results == null) {
                logDirectoryFailure("GET /listings/?search= (autocomplete)", resp, null);
                return new ArrayList<>();
            }
            List<AutocompleteDto.Item> items = new ArrayList<>();
            for (ListingDto listing : body.data.results) {
                AutocompleteDto.Item item = new AutocompleteDto.Item();
                item.type = "fiche";
                item.slug = listing.slug;
                item.label = listing.communeName != null && !listing.communeName.isEmpty()
                        ? listing.name + " · " + listing.communeName
                        : listing.name;
                item.lat = listing.latitude;
                item.lng = listing.longitude;
                items.add(item);
            }
            logDirectoryOk("GET /listings/?search= (autocomplete)", resp.code(),
                    items.size() + " fiche results for \"" + query + "\"");
            return items;
        } catch (Exception e) {
            logDirectoryFailure("GET /listings/?search= (autocomplete)", null, e);
            return new ArrayList<>(); // Soft-fail — the address/admin results still stand.
        }
    }

    private List<AutocompleteDto.Item> flattenSearchResponse(SearchResponseDto.Data data) {
        List<AutocompleteDto.Item> items = new ArrayList<>();
        addAdminResults(items, data.districts);
        addAdminResults(items, data.communes);
        addAdminResults(items, data.fokontany);
        addAdminResults(items, data.regions);
        if (data.addresses != null) {
            for (SearchResponseDto.AddressResult a : data.addresses) {
                AutocompleteDto.Item item = new AutocompleteDto.Item();
                item.type = "code";
                item.code = a.code;
                StringBuilder label = new StringBuilder(a.code);
                if (a.fokontany != null) label.append(" · ").append(a.fokontany);
                if (a.district != null) label.append(", ").append(a.district);
                item.label = label.toString();
                item.lat = a.lat;
                item.lng = a.lng;
                items.add(item);
            }
        }
        if (data.others != null) {
            for (SearchResponseDto.OtherResult o : data.others) {
                AutocompleteDto.Item item = new AutocompleteDto.Item();
                item.type = "place";
                item.label = o.name;
                if (o.sublabel != null) {
                    int comma = o.sublabel.indexOf(',');
                    item.label += " · " + (comma > 0 ? o.sublabel.substring(0, comma).trim() : o.sublabel);
                }
                item.lat = o.lat;
                item.lng = o.lng;
                item.bbox = o.bbox;
                items.add(item);
            }
        }
        return items;
    }

    private void addAdminResults(List<AutocompleteDto.Item> out,
                                 List<SearchResponseDto.AdminResult> src) {
        if (src == null) return;
        for (SearchResponseDto.AdminResult a : src) {
            AutocompleteDto.Item item = new AutocompleteDto.Item();
            item.type = a.type;   // "district", "commune", "fokontany", "region"
            item.level = a.type;
            item.uuid = a.pcode;  // pcode used as identifier for now
            item.label = a.name;
            String parent = parentOf(a);
            if (parent != null) item.label += " · " + parent;
            if (a.centroid != null && a.centroid.length == 2) {
                item.lng = a.centroid[0];
                item.lat = a.centroid[1];
            }
            item.bbox = a.bbox;
            out.add(item);
        }
    }

    private String parentOf(SearchResponseDto.AdminResult a) {
        if (a.commune  != null) return a.commune;
        if (a.district != null) return a.district;
        if (a.region   != null) return a.region;
        return null;
    }

    // ── Directory (establishments / fiches) ─────────────────────────────────────
    //
    // Phase A: online browse only (no Room cache yet — offline sync is Phase C,
    // gated on the key-permission answer; see docs/directory-api-integration.md).
    //
    // Every call logs its outcome under TAG_DIRECTORY (debug builds only) with
    // the real HTTP code and a body/error snippet. Added because OkHttp's own
    // HttpLoggingInterceptor (see ApiClient), which logs at INFO, never showed up
    // on a real low-end test device: `getprop persist.log.tag` was `W` there —
    // a vendor policy (seen on Transsion/ZTE-style Android Go firmware) that
    // silently drops INFO/DEBUG/VERBOSE logs system-wide, not an app bug. Exactly
    // this app's target hardware (see README "Design constraints"), so both log
    // calls below use WARN — the only level reliably visible on that class of
    // device — even for the success case, which Android log-level purists will
    // wince at; diagnostic visibility on real target devices wins here.
    // Filter logcat with `-s DirectoryApi` to see only these.

    private static final String TAG_DIRECTORY = "DirectoryApi";

    private static void logDirectoryOk(String endpoint, int httpCode, String detail) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG_DIRECTORY, endpoint + " -> HTTP " + httpCode + " OK, " + detail);
        }
    }

    /** Pass {@code resp} for an HTTP-level failure (non-2xx or an unexpected
     *  envelope), or {@code error} for a network/parse exception — exactly one
     *  should be non-null. Reads {@code errorBody} defensively: it's only
     *  available once and may be null even on a non-2xx response. */
    private static void logDirectoryFailure(String endpoint, Response<?> resp, Exception error) {
        if (!BuildConfig.DEBUG) return;
        if (error != null) {
            Log.w(TAG_DIRECTORY, endpoint + " -> network/parse error: " + error);
            return;
        }
        String snippet = null;
        try {
            if (resp != null && resp.errorBody() != null) {
                String raw = resp.errorBody().string();
                snippet = raw.length() > 300 ? raw.substring(0, 300) + "…" : raw;
            }
        } catch (Exception ignored) {
            // errorBody is a one-shot stream — a failure reading it isn't itself
            // worth logging over the HTTP code we already have.
        }
        Log.w(TAG_DIRECTORY, endpoint + " -> HTTP " + (resp != null ? resp.code() : -1)
                + (snippet != null ? ": " + snippet : " (no error body)"));
    }

    /** Max listings per page the API allows — used for the one-shot browse fetch. */
    private static final int LISTINGS_PAGE_SIZE = 200;

    /** The full, unpaginated category set. */
    public LiveData<Result<List<CategoryDto>>> listCategories() {
        MutableLiveData<Result<List<CategoryDto>>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                deviceAuth.ensureToken();
                Response<ApiResponse<List<CategoryDto>>> resp = api.listCategories().execute();
                ApiResponse<List<CategoryDto>> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    logDirectoryOk("GET /categories/", resp.code(), body.data.size() + " categories");
                    out.postValue(Result.success(body.data));
                } else {
                    logDirectoryFailure("GET /categories/", resp, null);
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    out.postValue(Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                logDirectoryFailure("GET /categories/", null, e);
                out.postValue(Result.networkError(e));
            }
        });
        return out;
    }

    /** Directory search / proximity browse — first page only (Phase A).
     *  Pass null for any unused filter; {@code lat}+{@code lng} must be supplied
     *  together for a proximity query, with {@code radius} in metres. */
    public LiveData<Result<List<ListingDto>>> listings(String search, String categoryUuid,
                                                       Double lat, Double lng, Integer radius) {
        MutableLiveData<Result<List<ListingDto>>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                deviceAuth.ensureToken();
                Response<ApiResponse<PaginatedDto<ListingDto>>> resp = api.listListings(
                        categoryUuid, null, null, null, null, null, search,
                        lat, lng, radius, null, null, 1, LISTINGS_PAGE_SIZE).execute();
                ApiResponse<PaginatedDto<ListingDto>> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    List<ListingDto> results = body.data.results != null
                            ? body.data.results : new ArrayList<>();
                    logDirectoryOk("GET /listings/", resp.code(),
                            results.size() + " results (search=" + search + ")");
                    out.postValue(Result.success(results));
                } else {
                    logDirectoryFailure("GET /listings/", resp, null);
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    out.postValue(Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                logDirectoryFailure("GET /listings/", null, e);
                out.postValue(Result.networkError(e));
            }
        });
        return out;
    }

    /** One listing's detail, by UUID or slug. */
    public LiveData<Result<ListingDto>> getListing(String idOrSlug) {
        MutableLiveData<Result<ListingDto>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                deviceAuth.ensureToken();
                Response<ApiResponse<ListingDto>> resp = api.getListing(idOrSlug).execute();
                ApiResponse<ListingDto> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    logDirectoryOk("GET /listings/" + idOrSlug + "/", resp.code(), body.data.name);
                    out.postValue(Result.success(body.data));
                } else {
                    logDirectoryFailure("GET /listings/" + idOrSlug + "/", resp, null);
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    out.postValue(Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                logDirectoryFailure("GET /listings/" + idOrSlug + "/", null, e);
                out.postValue(Result.networkError(e));
            }
        });
        return out;
    }

    /** Blocking fetch of all located listings as a GeoJSON FeatureCollection
     *  string, ready for a MapLibre {@code GeoJsonSource}. Returns null on any
     *  failure. Called by FichePinController on its own executor, mirroring how
     *  the other map-layer controllers pull from the repository. */
    public String listingsGeoJsonBlocking() {
        try {
            deviceAuth.ensureToken();
            Response<ResponseBody> resp = api.listingsGeo().execute();
            if (resp.isSuccessful() && resp.body() != null) {
                String json = unwrapGeoJson(resp.body().string());
                // Whitespace-agnostic (server may or may not pretty-print) — a rough
                // count, not a real parse; FichePinController does the real parsing.
                int featureCount = countOccurrences(json, "\"Feature\"");
                logDirectoryOk("GET /listings/geo/", resp.code(),
                        json.length() + " chars, ~" + featureCount + " features");
                return json;
            }
            logDirectoryFailure("GET /listings/geo/", resp, null);
            if (resp.code() == 401) deviceAuth.invalidateToken();
        } catch (Exception e) {
            logDirectoryFailure("GET /listings/geo/", null, e);
        }
        return null;
    }

    /** Cheap substring count — avoids a second full JSON parse just for a log line. */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0, from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            count++;
            from += needle.length();
        }
        return count;
    }

    /** The geo endpoint's envelope shape is unconfirmed. If the payload carries a
     *  {@code data} wrapper (like the other directory endpoints) rather than a bare
     *  FeatureCollection, unwrap it so MapLibre receives {@code {type:"FeatureCollection",…}}. */
    private static String unwrapGeoJson(String raw) {
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (!obj.has("features") && obj.has("data") && obj.get("data").isJsonObject()) {
                return obj.getAsJsonObject("data").toString();
            }
        } catch (Exception ignored) {
            // Not a recognised object — hand the raw payload through unchanged.
        }
        return raw;
    }

    // ── Directory offline sync (Phase C) ────────────────────────────────────────
    //
    // Blocking — called from ListingSyncWorker's background thread, mirroring
    // listingsGeoJsonBlocking(). Pulls every page of /listings/?updated_since=,
    // upserting active rows and deleting rows the API flags is_active=false or
    // is_deleted=true (see docs/api-reply-2026-10-27-tasks.md §1 — a delta row
    // naming an id we never cached is a correctly-silent no-op, since DELETE
    // WHERE id = ? on a missing row just does nothing). Refreshes the full
    // category set in the same pass (small, unpaginated, no delta semantics).
    //
    // The cursor only advances after every page of a run completes — a run that
    // fails partway through leaves the old cursor in place, so the next attempt
    // retries the same window rather than silently skipping whatever the failed
    // run didn't reach.

    private static final int SYNC_PAGE_SIZE = 200;

    /** Pulls every page of pending listing updates and applies them locally.
     *  @return true if the sync completed (even if there was nothing new);
     *  false on any network/HTTP failure, in which case the cursor was not
     *  advanced. */
    public boolean syncListingsBlocking() {
        try {
            deviceAuth.ensureToken();
            syncCategoriesBlocking();

            String cursor = appPrefs.getListingsSyncCursor();
            String sinceParam = cursor.isEmpty() ? null : cursor;
            String maxUpdatedAt = cursor;

            List<ListingEntity> toUpsert = new ArrayList<>();
            List<String> toDelete = new ArrayList<>();
            int page = 1;
            int totalSeen = 0;

            while (true) {
                Response<ApiResponse<PaginatedDto<ListingDto>>> resp = api.listListings(
                        null, null, null, null, null, null, null,
                        null, null, null, "updated_at", sinceParam, page, SYNC_PAGE_SIZE)
                        .execute();
                ApiResponse<PaginatedDto<ListingDto>> body = resp.body();
                if (!resp.isSuccessful() || body == null || !body.isOk()) {
                    logDirectoryFailure("SYNC /listings/ (page " + page + ")", resp, null);
                    if (resp.code() == 401) deviceAuth.invalidateToken();
                    return false;
                }

                List<ListingDto> results = body.data.results != null
                        ? body.data.results : new ArrayList<>();
                for (ListingDto dto : results) {
                    totalSeen++;
                    if (dto.updatedAt != null
                            && dto.updatedAt.compareTo(maxUpdatedAt) > 0) {
                        maxUpdatedAt = dto.updatedAt;
                    }
                    if (!dto.isActive || dto.isDeleted) {
                        toDelete.add(dto.id);
                    } else {
                        toUpsert.add(toEntity(dto));
                    }
                }

                if (body.data.next == null) break;
                page++;
            }

            for (String id : toDelete) listingDao.deleteById(id);
            if (!toUpsert.isEmpty()) listingDao.upsertAll(toUpsert);
            appPrefs.setListingsSyncCursor(maxUpdatedAt);

            logDirectoryOk("SYNC /listings/", 200, totalSeen + " rows across " + page
                    + " page(s), " + toUpsert.size() + " upserted, " + toDelete.size()
                    + " deleted, cursor=" + maxUpdatedAt);
            return true;
        } catch (Exception e) {
            logDirectoryFailure("SYNC /listings/", null, e);
            return false;
        }
    }

    private void syncCategoriesBlocking() throws Exception {
        Response<ApiResponse<List<CategoryDto>>> resp = api.listCategories().execute();
        ApiResponse<List<CategoryDto>> body = resp.body();
        if (!resp.isSuccessful() || body == null || !body.isOk()) {
            logDirectoryFailure("SYNC /categories/", resp, null);
            return;
        }
        List<CategoryEntity> entities = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (CategoryDto dto : body.data) {
            CategoryEntity e = new CategoryEntity();
            e.id          = dto.id != null ? dto.id : "";
            e.name        = dto.name;
            e.slug        = dto.slug;
            e.icon        = dto.icon;
            e.color       = dto.color;
            e.description = dto.description;
            e.cachedAt    = now;
            entities.add(e);
        }
        categoryDao.upsertAll(entities);
        logDirectoryOk("SYNC /categories/", resp.code(), entities.size() + " categories");
    }

    private static ListingEntity toEntity(ListingDto dto) {
        ListingEntity e = new ListingEntity();
        e.id                = dto.id != null ? dto.id : "";
        e.name              = dto.name;
        e.slug              = dto.slug;
        e.categoryUuid      = dto.categoryUuid;
        e.categoryName      = dto.categoryName;
        e.categoryIcon      = dto.categoryIcon;
        e.categorySlug      = dto.categorySlug;
        e.addressCode       = dto.addressCode;
        e.latitude          = dto.latitude;
        e.longitude         = dto.longitude;
        e.communeName       = dto.communeName;
        e.communeShortCode  = dto.communeShortCode;
        e.districtName      = dto.districtName;
        e.districtShortCode = dto.districtShortCode;
        e.phone             = dto.phone;
        e.website           = dto.website;
        e.legacyAddress     = dto.legacyAddress;
        e.isVerified        = dto.isVerified;
        e.coverImage        = dto.coverImage;
        e.logo              = dto.logo;
        e.averageRating     = dto.averageRating;
        e.updatedAt         = dto.updatedAt;
        e.cachedAt          = System.currentTimeMillis();
        return e;
    }

    // ── Search history ────────────────────────────────────────────────────────

    public LiveData<List<SearchHistoryEntity>> getSearchHistory() {
        return searchHistoryDao.getRecent();
    }

    public void recordSearch(String query) {
        executor.execute(() -> {
            SearchHistoryEntity entry = new SearchHistoryEntity();
            entry.query = query;
            entry.timestamp = System.currentTimeMillis();
            searchHistoryDao.upsert(entry);
        });
    }

    public void clearSearchHistory() {
        executor.execute(searchHistoryDao::clearAll);
    }

    // ── Cache maintenance ─────────────────────────────────────────────────────

    public void evictExpiredCache() {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            addressDao.evictExpired(now - TTL_ADDRESS_MS);
            adminUnitDao.evictExpired(now - TTL_ADMIN_MS);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean isExpired(long cachedAt, long ttl) {
        return System.currentTimeMillis() - cachedAt > ttl;
    }

    private static AddressEntity toEntity(AddressCodeDto dto) {
        AddressEntity e = new AddressEntity();
        e.canonicalCode  = dto.canonicalCode != null ? dto.canonicalCode : "";
        e.fokontanyUuid  = dto.fokontanyUuid;
        e.fokontanyName  = dto.fokontanyName;
        e.communeName    = dto.communeName;
        e.districtName   = dto.districtName;
        e.regionName     = dto.regionName;
        e.districtCode   = dto.districtCode;
        e.communeShort   = dto.communeShort;
        e.latitude       = dto.latitude  != null ? dto.latitude  : 0.0;
        e.longitude      = dto.longitude != null ? dto.longitude : 0.0;
        e.cachedAt       = System.currentTimeMillis();
        return e;
    }

    /** Regions have no parent; every other level's parent pcode is returned
     *  under a level-specific key on the DTO (see AdminUnitDto). */
    private static String parentPcodeFor(String type, AdminUnitDto dto) {
        return switch (type) {
            case "district"  -> dto.regionPcode;
            case "commune"   -> dto.districtPcode;
            case "fokontany" -> dto.communePcode;
            default          -> null;
        };
    }

    private AdminUnitEntity toAdminEntity(AdminUnitDto dto, String type) {
        AdminUnitEntity e = new AdminUnitEntity();
        e.pcode         = dto.pcode != null ? dto.pcode : dto.id;
        e.type          = type;
        e.uuid          = dto.id;
        e.name          = dto.name;
        e.parentUuid    = parentPcodeFor(type, dto);
        e.bboxJson      = dto.bbox      != null ? gson.toJson(dto.bbox)     : null;
        e.centroidJson  = dto.centroid  != null ? gson.toJson(dto.centroid) : null;
        e.cachedAt      = System.currentTimeMillis();
        return e;
    }

    private PaginatedDto<AdminUnitDto> fetchAllAdminPages(String type, String parentUuid)
            throws Exception {
        List<AdminUnitDto> all = new ArrayList<>();
        int page = 1;
        boolean gotFirstPage = false;
        while (true) {
            Response<ApiResponse<PaginatedDto<AdminUnitDto>>> resp = switch (type) {
                case "region"   -> api.listRegions(page, null).execute();
                case "district" -> api.listDistricts(parentUuid, page).execute();
                case "commune"  -> api.listCommunes(parentUuid, page).execute();
                default         -> api.listFokontany(parentUuid, page).execute();
            };
            ApiResponse<PaginatedDto<AdminUnitDto>> env = resp.body();
            if (!resp.isSuccessful() || env == null || env.data == null) {
                if (resp.code() == 401) deviceAuth.invalidateToken();
                return gotFirstPage ? syntheticPage(all) : null;
            }
            gotFirstPage = true;
            PaginatedDto<AdminUnitDto> body = env.data;
            if (body.results != null) all.addAll(body.results);
            if (body.next == null) break;
            page++;
        }
        return syntheticPage(all);
    }

    private static PaginatedDto<AdminUnitDto> syntheticPage(List<AdminUnitDto> results) {
        PaginatedDto<AdminUnitDto> p = new PaginatedDto<>();
        p.results = results;
        return p;
    }

    private retrofit2.Call<GeoJsonGeometryDto> geometryCall(String type, String pcode) {
        return switch (type) {
            case "region"   -> api.regionGeometry(pcode);
            case "district" -> api.districtGeometry(pcode);
            case "commune"  -> api.communeGeometry(pcode);
            default         -> api.fokontanyGeometry(pcode);
        };
    }
}
