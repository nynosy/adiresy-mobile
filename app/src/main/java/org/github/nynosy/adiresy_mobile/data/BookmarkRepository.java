package org.github.nynosy.adiresy_mobile.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.data.cache.AppDatabase;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkDao;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListDao;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListWithCount;

public class BookmarkRepository {

    public static final int MAX_BOOKMARKS   = 500;
    public static final int WARN_THRESHOLD  = 490;

    public enum SaveStatus { OK, WARN, LIMIT_REACHED, MOVED }

    public static class SaveResult {
        public final SaveStatus status;
        public final String listName;
        public final String oldListName;
        public final int total;

        public SaveResult(SaveStatus status, String listName, String oldListName, int total) {
            this.status      = status;
            this.listName    = listName;
            this.oldListName = oldListName;
            this.total       = total;
        }
    }

    public static class ImportResult {
        public final int imported;
        public final int truncated;
        public final boolean schemaError;

        public ImportResult(int imported, int truncated, boolean schemaError) {
            this.imported    = imported;
            this.truncated   = truncated;
            this.schemaError = schemaError;
        }
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    private static volatile BookmarkRepository instance;

    private final BookmarkListDao listDao;
    private final BookmarkDao     bookmarkDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context         appCtx;
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private BookmarkRepository(Context context) {
        appCtx      = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appCtx);
        listDao     = db.bookmarkListDao();
        bookmarkDao = db.bookmarkDao();
    }

    public static BookmarkRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (BookmarkRepository.class) {
                if (instance == null) instance = new BookmarkRepository(context);
            }
        }
        return instance;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public LiveData<List<BookmarkListWithCount>> getAllListsWithCount() {
        return listDao.getAllListsWithCount();
    }

    public LiveData<List<BookmarkEntity>> getBookmarksForList(long listId) {
        return bookmarkDao.getBookmarksForList(listId);
    }

    public BookmarkEntity findByCodeSync(String code) {
        return bookmarkDao.findByCodeSync(code);
    }

    public List<BookmarkEntity> searchBookmarksSync(String query) {
        return bookmarkDao.searchBookmarksSync(query);
    }

    public List<BookmarkEntity> getBookmarksInBoundsSync(double south, double north,
                                                         double west, double east) {
        return bookmarkDao.getBookmarksInBoundsSync(south, north, west, east);
    }

    public List<BookmarkListEntity> getAllListsSync() {
        return listDao.getAllListsSync();
    }

    // ── List operations ───────────────────────────────────────────────────────

    public void createList(String name, String description, String emoji, Callback<Long> cb) {
        executor.execute(() -> {
            BookmarkListEntity list = new BookmarkListEntity();
            list.name        = name;
            list.description = description;
            list.emoji       = (emoji != null && !emoji.isEmpty()) ? emoji : "📍";
            list.createdAt   = System.currentTimeMillis();
            long id = listDao.insertList(list);
            if (cb != null) mainHandler.post(() -> cb.onResult(id));
        });
    }

    public void updateList(BookmarkListEntity list, Runnable onDone) {
        executor.execute(() -> {
            listDao.updateList(list);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    public void deleteList(BookmarkListEntity list, Runnable onDone) {
        executor.execute(() -> {
            listDao.deleteList(list);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    // ── Bookmark operations ───────────────────────────────────────────────────

    public void saveBookmark(String canonicalCode, double lat, double lng,
                             String fokontanyName, String communeName,
                             String districtName, String regionName,
                             String name, String userDescription, long listId,
                             Callback<SaveResult> cb) {
        executor.execute(() -> {
            BookmarkListEntity target  = listDao.getListByIdSync(listId);
            String             tName  = target != null ? target.name : "";
            BookmarkEntity     existing = bookmarkDao.findByCodeSync(canonicalCode);
            SaveResult result;

            if (existing != null && existing.listId == listId) {
                // Already in this list — no-op
                result = new SaveResult(SaveStatus.OK, tName, null,
                        bookmarkDao.getTotalCountSync());
            } else if (existing != null) {
                // In a different list — move
                BookmarkListEntity old = listDao.getListByIdSync(existing.listId);
                existing.listId = listId;
                if (name != null) existing.name = name;
                if (userDescription != null) existing.userDescription = userDescription;
                bookmarkDao.updateBookmark(existing);
                result = new SaveResult(SaveStatus.MOVED, tName,
                        old != null ? old.name : "", bookmarkDao.getTotalCountSync());
            } else {
                int total = bookmarkDao.getTotalCountSync();
                if (total >= MAX_BOOKMARKS) {
                    result = new SaveResult(SaveStatus.LIMIT_REACHED, tName, null, total);
                } else {
                    BookmarkEntity entity = new BookmarkEntity();
                    entity.canonicalCode   = canonicalCode;
                    entity.latitude        = lat;
                    entity.longitude       = lng;
                    entity.fokontanyName   = fokontanyName;
                    entity.communeName     = communeName;
                    entity.districtName    = districtName;
                    entity.regionName      = regionName;
                    entity.name            = name;
                    entity.userDescription = userDescription;
                    entity.listId          = listId;
                    entity.savedAt         = System.currentTimeMillis();
                    bookmarkDao.insertBookmark(entity);
                    int newTotal = total + 1;
                    SaveStatus st = newTotal >= WARN_THRESHOLD ? SaveStatus.WARN : SaveStatus.OK;
                    result = new SaveResult(st, tName, null, newTotal);
                }
            }

            SaveResult r = result;
            if (cb != null) mainHandler.post(() -> cb.onResult(r));
        });
    }

    public void deleteBookmarkByCode(String code, Runnable onDone) {
        executor.execute(() -> {
            bookmarkDao.deleteByCode(code);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    public void insertBookmark(BookmarkEntity bookmark, Runnable onDone) {
        executor.execute(() -> {
            bookmarkDao.insertBookmark(bookmark);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    public void deleteBookmark(BookmarkEntity bookmark, Runnable onDone) {
        executor.execute(() -> {
            bookmarkDao.deleteBookmark(bookmark);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    public void moveBookmark(long bookmarkId, long newListId, Runnable onDone) {
        executor.execute(() -> {
            bookmarkDao.moveToList(bookmarkId, newListId);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    public void updateDescription(long bookmarkId, String description, Runnable onDone) {
        executor.execute(() -> {
            bookmarkDao.updateDescription(bookmarkId, description);
            if (onDone != null) mainHandler.post(onDone);
        });
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /** Synchronous — call off main thread. Returns a temp file ready for sharing. */
    public File exportJson() throws Exception {
        List<BookmarkListEntity> lists       = listDao.getAllListsSync();
        List<BookmarkEntity>     allBookmarks = bookmarkDao.getAllBookmarksSync();

        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("exported_at", System.currentTimeMillis());

        JsonArray listsArr = new JsonArray();
        for (BookmarkListEntity list : lists) {
            JsonObject lObj = new JsonObject();
            lObj.addProperty("name",        list.name);
            lObj.addProperty("description", list.description);
            lObj.addProperty("emoji",       list.emoji);
            lObj.addProperty("created_at",  list.createdAt);

            JsonArray bookmarksArr = new JsonArray();
            for (BookmarkEntity b : allBookmarks) {
                if (b.listId != list.id) continue;
                JsonObject bObj = new JsonObject();
                bObj.addProperty("canonical_code",  b.canonicalCode);
                bObj.addProperty("latitude",        b.latitude);
                bObj.addProperty("longitude",       b.longitude);
                bObj.addProperty("fokontany_name",  b.fokontanyName);
                bObj.addProperty("commune_name",    b.communeName);
                bObj.addProperty("district_name",   b.districtName);
                bObj.addProperty("region_name",     b.regionName);
                bObj.addProperty("user_description",b.userDescription);
                bObj.addProperty("saved_at",        b.savedAt);
                bookmarksArr.add(bObj);
            }
            lObj.add("bookmarks", bookmarksArr);
            listsArr.add(lObj);
        }
        root.add("lists", listsArr);

        File outFile = new File(appCtx.getCacheDir(), "adiresy_bookmarks.json");
        try (FileWriter w = new FileWriter(outFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        }
        return outFile;
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /** Synchronous — call off main thread. */
    public ImportResult importJson(InputStream inputStream) {
        try {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();

            if (!root.has("schema_version") || root.get("schema_version").getAsInt() != 1) {
                return new ImportResult(0, 0, true);
            }

            JsonArray lists    = root.getAsJsonArray("lists");
            int       imported  = 0;
            int       truncated = 0;

            for (int i = 0; i < lists.size(); i++) {
                JsonObject lObj = lists.get(i).getAsJsonObject();

                BookmarkListEntity list = new BookmarkListEntity();
                list.name        = str(lObj, "name", "Imported");
                list.description = strOrNull(lObj, "description");
                list.emoji       = strOrNull(lObj, "emoji") != null ? strOrNull(lObj, "emoji") : "📍";
                list.createdAt   = lObj.has("created_at")
                        ? lObj.get("created_at").getAsLong()
                        : System.currentTimeMillis();
                long newListId = listDao.insertList(list);

                if (!lObj.has("bookmarks")) continue;
                JsonArray bookmarks = lObj.getAsJsonArray("bookmarks");
                for (int j = 0; j < bookmarks.size(); j++) {
                    if (bookmarkDao.getTotalCountSync() >= MAX_BOOKMARKS) {
                        truncated += bookmarks.size() - j;
                        break;
                    }
                    JsonObject bObj = bookmarks.get(j).getAsJsonObject();
                    String code = str(bObj, "canonical_code", null);
                    if (code == null || code.isEmpty()) continue;
                    if (bookmarkDao.findByCodeSync(code) != null) continue;

                    BookmarkEntity entity = new BookmarkEntity();
                    entity.canonicalCode   = code;
                    entity.latitude        = bObj.has("latitude")  ? bObj.get("latitude").getAsDouble()  : 0;
                    entity.longitude       = bObj.has("longitude") ? bObj.get("longitude").getAsDouble() : 0;
                    entity.fokontanyName   = strOrNull(bObj, "fokontany_name");
                    entity.communeName     = strOrNull(bObj, "commune_name");
                    entity.districtName    = strOrNull(bObj, "district_name");
                    entity.regionName      = strOrNull(bObj, "region_name");
                    entity.userDescription = strOrNull(bObj, "user_description");
                    entity.listId          = newListId;
                    entity.savedAt         = bObj.has("saved_at")
                            ? bObj.get("saved_at").getAsLong()
                            : System.currentTimeMillis();
                    bookmarkDao.insertBookmark(entity);
                    imported++;
                }
            }
            return new ImportResult(imported, truncated, false);
        } catch (Exception e) {
            return new ImportResult(0, 0, true);
        }
    }

    private String str(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private String strOrNull(JsonObject obj, String key) {
        return str(obj, key, null);
    }
}
