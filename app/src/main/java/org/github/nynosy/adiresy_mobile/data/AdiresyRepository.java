package org.github.nynosy.adiresy_mobile.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.data.api.AdiresyApi;
import org.github.nynosy.adiresy_mobile.data.api.ApiClient;
import org.github.nynosy.adiresy_mobile.data.api.dto.AddressCodeDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ApiResponse;
import org.github.nynosy.adiresy_mobile.data.api.dto.AdminUnitDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.SearchResponseDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.GeoJsonGeometryDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.PaginatedDto;
import org.github.nynosy.adiresy_mobile.data.cache.AddressDao;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitDao;
import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitEntity;
import org.github.nynosy.adiresy_mobile.data.cache.AppDatabase;
import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryDao;
import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryEntity;
import retrofit2.Response;

public class AdiresyRepository {

    /** Cache TTLs */
    private static final long TTL_ADDRESS_MS    = 7L  * 24 * 60 * 60 * 1000;
    private static final long TTL_ADMIN_MS      = 30L * 24 * 60 * 60 * 1000;

    private static volatile AdiresyRepository instance;

    private final AdiresyApi api;
    private final AddressDao addressDao;
    private final AdminUnitDao adminUnitDao;
    private final SearchHistoryDao searchHistoryDao;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Gson gson = new Gson();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private AdiresyRepository(Context context) {
        Context appCtx = context.getApplicationContext();
        api = ApiClient.get(appCtx);
        AppDatabase db = AppDatabase.getInstance(appCtx);
        addressDao = db.addressDao();
        adminUnitDao = db.adminUnitDao();
        searchHistoryDao = db.searchHistoryDao();
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
                Response<ApiResponse<AddressCodeDto>> resp = api.resolveCode(canonicalCode).execute();
                ApiResponse<AddressCodeDto> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    AddressEntity entity = toEntity(body.data);
                    addressDao.insert(entity);
                    out.postValue(Result.success(entity));
                } else {
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
                Response<ApiResponse<AddressCodeDto>> resp =
                        api.reverseGeocode(lat, lng, 1, TAP_RADIUS).execute();
                ApiResponse<AddressCodeDto> body = resp.body();
                if (resp.isSuccessful() && body != null && body.isOk()) {
                    AddressEntity entity = toEntity(body.data);
                    addressDao.insert(entity);
                    out.postValue(Result.success(entity));
                } else {
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
                } else {
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
                Response<GeoJsonGeometryDto> resp = geometryCall(type, pcode).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String json = gson.toJson(resp.body());
                    adminUnitDao.updateGeometry(pcode, json);
                    out.postValue(json);
                }
            } catch (Exception ignored) {
                out.postValue(null);
            }
        });
        return out;
    }

    // ── Search autocomplete ───────────────────────────────────────────────────

    public LiveData<Result<List<AutocompleteDto.Item>>> autocomplete(String query) {
        MutableLiveData<Result<List<AutocompleteDto.Item>>> out = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                retrofit2.Response<SearchResponseDto> resp = api.autocomplete(query, 20).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().data != null) {
                    out.postValue(Result.success(flattenSearchResponse(resp.body().data)));
                } else {
                    out.postValue(Result.error(resp.code(), resp.message()));
                }
            } catch (Exception e) {
                out.postValue(Result.networkError(e));
            }
        });
        return out;
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

    private AdminUnitEntity toAdminEntity(AdminUnitDto dto, String type) {
        AdminUnitEntity e = new AdminUnitEntity();
        e.pcode         = dto.pcode != null ? dto.pcode : dto.id;
        e.type          = type;
        e.uuid          = dto.id;
        e.name          = dto.name;
        e.parentUuid    = dto.parentUuid;
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
            Response<PaginatedDto<AdminUnitDto>> resp;
            switch (type) {
                case "region":   resp = api.listRegions(page, null).execute();        break;
                case "district": resp = api.listDistricts(parentUuid, page).execute(); break;
                case "commune":  resp = api.listCommunes(parentUuid, page).execute();  break;
                default:         resp = api.listFokontany(parentUuid, page).execute(); break;
            }
            if (!resp.isSuccessful() || resp.body() == null) {
                return gotFirstPage ? syntheticPage(all) : null;
            }
            gotFirstPage = true;
            PaginatedDto<AdminUnitDto> body = resp.body();
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
        switch (type) {
            case "region":    return api.regionGeometry(pcode);
            case "district":  return api.districtGeometry(pcode);
            case "commune":   return api.communeGeometry(pcode);
            default:          return api.fokontanyGeometry(pcode);
        }
    }
}
