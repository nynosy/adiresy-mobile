package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListEntity;

public class BookmarkPinController {

    public static final String BOOKMARK_LAYER  = "bookmark-layer";
    static final        String BOOKMARK_SOURCE = "bookmark-source";

    private static final float ZOOM_BOOKMARK_MIN = 11f;
    private static final int   EMOJI_SIZE_DP     = 36;

    private final BookmarkRepository bookmarkRepository;
    private final ExecutorService    bgExecutor  = Executors.newSingleThreadExecutor();
    private final Handler            mainHandler = new Handler(Looper.getMainLooper());
    private final float              density;

    private MapLibreMap       mapRef;
    private final Set<String> addedIconKeys = new HashSet<>();

    public BookmarkPinController(BookmarkRepository repo, Context context) {
        bookmarkRepository = repo;
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setMap(MapLibreMap map) {
        mapRef = map;
    }

    public void onStyleReady(Style style) {
        addedIconKeys.clear();
        style.addSource(new GeoJsonSource(BOOKMARK_SOURCE,
                FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new SymbolLayer(BOOKMARK_LAYER, BOOKMARK_SOURCE)
                .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconAnchor("center")));
    }

    public void onCameraIdle() {
        if (mapRef == null || mapRef.getStyle() == null) return;
        double zoom = mapRef.getCameraPosition().zoom;
        if (zoom < ZOOM_BOOKMARK_MIN) {
            clearPins();
            return;
        }
        LatLngBounds bounds = mapRef.getProjection().getVisibleRegion().latLngBounds;
        if (bounds == null) return;
        refreshPins(bounds);
    }

    private void clearPins() {
        if (mapRef == null) return;
        Style style = mapRef.getStyle();
        GeoJsonSource src = style != null ? style.getSourceAs(BOOKMARK_SOURCE) : null;
        if (src != null) src.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<>()));
    }

    private void refreshPins(LatLngBounds bounds) {
        int emojiSizePx = Math.round(EMOJI_SIZE_DP * density);
        bgExecutor.execute(() -> {
            List<BookmarkEntity> bookmarks = bookmarkRepository.getBookmarksInBoundsSync(
                    bounds.getLatSouth(), bounds.getLatNorth(),
                    bounds.getLonWest(), bounds.getLonEast());

            Map<Long, String> listEmoji = new HashMap<>();
            for (BookmarkListEntity list : bookmarkRepository.getAllListsSync()) {
                listEmoji.put(list.id, list.emoji != null && !list.emoji.isEmpty()
                        ? list.emoji : "📍");
            }

            List<Feature> features = new ArrayList<>();
            Map<String, String> toRegister = new HashMap<>();
            for (BookmarkEntity b : bookmarks) {
                String emoji   = listEmoji.getOrDefault(b.listId, "📍");
                String iconKey = "bm_" + emoji;
                if (!addedIconKeys.contains(iconKey)) toRegister.put(iconKey, emoji);
                Feature f = Feature.fromGeometry(Point.fromLngLat(b.longitude, b.latitude));
                f.addNumberProperty("lat", b.latitude);
                f.addNumberProperty("lng", b.longitude);
                f.addStringProperty("icon", iconKey);
                features.add(f);
            }

            Map<String, Bitmap> bitmaps = new HashMap<>();
            for (Map.Entry<String, String> entry : toRegister.entrySet()) {
                bitmaps.put(entry.getKey(), emojiBitmap(entry.getValue(), emojiSizePx));
            }

            FeatureCollection fc = FeatureCollection.fromFeatures(features);
            mainHandler.post(() -> {
                if (mapRef == null) return;
                Style style = mapRef.getStyle();
                if (style == null) return;
                for (Map.Entry<String, Bitmap> entry : bitmaps.entrySet()) {
                    style.addImage(entry.getKey(), entry.getValue());
                    addedIconKeys.add(entry.getKey());
                }
                GeoJsonSource src = style.getSourceAs(BOOKMARK_SOURCE);
                if (src != null) src.setGeoJson(fc);
            });
        });
    }

    private static Bitmap emojiBitmap(String emoji, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(sizePx * 0.75f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(emoji, sizePx / 2f, sizePx * 0.82f, paint);
        return bmp;
    }

    public void shutdown() {
        bgExecutor.shutdownNow();
        mapRef = null;
    }
}
