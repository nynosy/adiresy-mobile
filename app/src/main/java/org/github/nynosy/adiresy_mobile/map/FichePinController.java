package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;

/**
 * Renders the Adiresy directory ("fiches") as a map layer, fed by the unpaginated
 * {@code /api/v1/listings/geo/} FeatureCollection (whole country, capped 5000).
 *
 * <p>The layer is loaded once per style and is always on above {@link #ZOOM_FICHE_MIN}
 * — MapLibre handles symbol culling, so no per-camera refresh is needed (unlike
 * {@link BookmarkPinController}, which rebuilds emoji bitmaps as bookmarks change).
 *
 * <p>Each feature carries a small payload (slug, name, category icon/logo); property
 * names are read defensively because the geo feature schema is not published. Pins
 * are drawn from the category emoji. Taps are handled by the host fragment, which
 * queries {@link #FICHE_LAYER} and reads the {@code slug}/{@code name} properties.
 *
 * <p><b>Category filtering:</b> the geo payload documents no stable per-feature
 * category identifier (just "category icon"), so filtering keys off the same
 * normalised {@code category_icon} emoji used for rendering, matched against the
 * confirmed {@code Category.icon} field from {@code /categories/}. Two categories
 * that happened to share an icon glyph would be filtered together — an accepted
 * approximation until the geo schema is confirmed (see docs/directory-api-integration.md §5 Q2).
 */
public final class FichePinController {

    public static final String FICHE_LAYER  = "fiche-layer";
    static final        String FICHE_SOURCE = "fiche-source";

    /** Match the dedicated POI overlay's z12 activation. */
    private static final float ZOOM_FICHE_MIN = 12f;
    private static final int   ICON_SIZE_DP   = 30;
    private static final String DEFAULT_ICON  = "📍";

    // Property keys we read from the geo features, most-specific first.
    private static final String[] ICON_KEYS = { "category_icon", "icon", "category" };
    private static final String[] NAME_KEYS = { "name", "title" };
    private static final String[] SLUG_KEYS = { "slug", "id" };

    private final AdiresyRepository repository;
    private final ExecutorService   bgExecutor  = Executors.newSingleThreadExecutor();
    private final Handler           mainHandler = new Handler(Looper.getMainLooper());
    private final int               iconSizePx;

    private MapLibreMap mapRef;
    /** All features from the last successful load, pre-filter. Re-filtered in
     *  place when the category selection changes — no re-fetch needed. */
    private List<Feature> allFeatures = new ArrayList<>();
    private Set<String>   hiddenCategoryIcons = Collections.emptySet();

    public FichePinController(AdiresyRepository repository, Context context) {
        this.repository = repository;
        float density = context.getResources().getDisplayMetrics().density;
        this.iconSizePx = Math.round(ICON_SIZE_DP * density);
    }

    public void setMap(MapLibreMap map) {
        mapRef = map;
    }

    /** Adds the (empty) source + layer, then kicks off the one-shot network load. */
    public void onStyleReady(Style style) {
        allFeatures = new ArrayList<>();
        style.addSource(new GeoJsonSource(FICHE_SOURCE,
                FeatureCollection.fromFeatures(new ArrayList<>())));
        SymbolLayer layer = new SymbolLayer(FICHE_LAYER, FICHE_SOURCE)
                .withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAllowOverlap(false),
                        PropertyFactory.iconAnchor("bottom"),
                        PropertyFactory.textField(Expression.get("name")),
                        PropertyFactory.textSize(11f),
                        PropertyFactory.textAnchor("top"),
                        PropertyFactory.textOptional(true),
                        PropertyFactory.textMaxWidth(7f),
                        PropertyFactory.textHaloWidth(1.2f),
                        PropertyFactory.textHaloColor("#ffffff"));
        layer.setMinZoom(ZOOM_FICHE_MIN);
        style.addLayer(layer);
        load();
    }

    private void load() {
        bgExecutor.execute(() -> {
            String geoJson = repository.listingsGeoJsonBlocking();
            if (geoJson == null) return;

            FeatureCollection fc;
            try {
                fc = FeatureCollection.fromJson(geoJson);
            } catch (Exception e) {
                return; // Unparseable payload — leave the layer empty.
            }
            List<Feature> src = fc.features();
            if (src == null || src.isEmpty()) return;

            // Give each feature a stable "icon" image key plus a normalised, unprefixed
            // "category_icon" property (used for filtering), collecting the distinct
            // emoji so we can register one bitmap per icon.
            Map<String, String> toRegister = new HashMap<>();
            for (Feature f : src) {
                String emoji   = firstProperty(f, ICON_KEYS, DEFAULT_ICON);
                String iconKey = "fiche_" + emoji;
                toRegister.putIfAbsent(iconKey, emoji);
                f.addStringProperty("icon", iconKey);
                f.addStringProperty("category_icon", emoji);
                // Normalise the label/slug under keys the layer + tap handler expect.
                f.addStringProperty("name", firstProperty(f, NAME_KEYS, ""));
                f.addStringProperty("slug", firstProperty(f, SLUG_KEYS, ""));
            }

            Map<String, Bitmap> bitmaps = new HashMap<>();
            for (Map.Entry<String, String> e : toRegister.entrySet()) {
                bitmaps.put(e.getKey(), emojiBitmap(e.getValue(), iconSizePx));
            }

            mainHandler.post(() -> {
                allFeatures = src;
                if (mapRef == null) return;
                Style style = mapRef.getStyle();
                if (style == null) return;
                for (Map.Entry<String, Bitmap> e : bitmaps.entrySet()) {
                    style.addImage(e.getKey(), e.getValue());
                }
                applyFilter();
            });
        });
    }

    /** Hides every pin whose normalised {@code category_icon} is in
     *  {@code hiddenIcons} (an empty set shows everything); must be called on
     *  the main thread. Cheap — filters the in-memory list, no re-fetch. */
    public void setHiddenCategoryIcons(Set<String> hiddenIcons) {
        hiddenCategoryIcons = hiddenIcons != null ? hiddenIcons : Collections.emptySet();
        applyFilter();
    }

    private void applyFilter() {
        if (mapRef == null) return;
        Style style = mapRef.getStyle();
        if (style == null) return;
        GeoJsonSource source = style.getSourceAs(FICHE_SOURCE);
        if (source == null) return;

        if (hiddenCategoryIcons.isEmpty()) {
            source.setGeoJson(FeatureCollection.fromFeatures(allFeatures));
            return;
        }
        List<Feature> visible = new ArrayList<>();
        for (Feature f : allFeatures) {
            String icon = f.getStringProperty("category_icon");
            if (icon == null || !hiddenCategoryIcons.contains(icon)) visible.add(f);
        }
        source.setGeoJson(FeatureCollection.fromFeatures(visible));
    }

    /** First non-empty string property among {@code keys}, else {@code fallback}. */
    private static String firstProperty(Feature f, String[] keys, String fallback) {
        for (String key : keys) {
            if (f.hasProperty(key)) {
                String v = f.getStringProperty(key);
                if (v != null && !v.isEmpty()) return v;
            }
        }
        return fallback;
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
