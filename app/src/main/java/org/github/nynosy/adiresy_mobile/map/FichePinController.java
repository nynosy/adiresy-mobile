package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Property;
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

import org.github.nynosy.adiresy_mobile.BuildConfig;
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

    // See AdiresyRepository's DirectoryApi-tagged logging for why this is WARN,
    // not INFO/DEBUG: on at least one real low-end test device,
    // `persist.log.tag` was `W`, a vendor policy that silently drops anything
    // below WARN system-wide — the previous version of this class had no
    // logging at all here, so a parse failure against real (vs. near-empty
    // test) data was completely invisible.
    private static final String TAG = "FichePinController";

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
        // iconImage uses the classic "{property}" token syntax, not
        // Expression.get("icon") — on-device testing (2026-10-29, real device,
        // real 303-listing production data) found Expression-based data-driven
        // properties silently never resolved against this GeoJsonSource: layer,
        // source, and registered images were all confirmed present via
        // style.getLayer/getSource/getImage, the data was confirmed applied via
        // source.setGeoJson, no exception was thrown anywhere in the pipeline —
        // nothing painted regardless. A literal iconImage(String) with no
        // per-feature data (SelectionPinController) rendered correctly, and
        // switching this layer to the equivalent token-syntax string fixed it.
        //
        // TODO: no text label (business name) is rendered under each pin. A
        // textField("{name}") + supporting text-* properties block, otherwise
        // identical to this layer, reproduced the original failure (nothing
        // painted again) even with the token-syntax icon fix applied — unclear
        // whether text-field itself is incompatible with this GeoJsonSource on
        // this MapLibre build, or something more specific in that property
        // combination. Investigation was cut short by hitting the anonymous
        // device key's rate limit (60 req/hour) from repeated test-launch
        // cycles, not by running out of hypotheses — revisit with a fresh
        // token/hour budget before concluding text labels aren't feasible here.
        SymbolLayer layer = new SymbolLayer(FICHE_LAYER, FICHE_SOURCE)
                .withProperties(
                        PropertyFactory.iconImage("{icon}"),
                        // true, not false: MapLibre's collision index is shared
                        // across layers, and the base style's OSM POI layer
                        // (added to the style first) is dense enough in real
                        // commercial areas that every fiche symbol was silently
                        // losing the collision race and never rendering — no
                        // error, just invisible. BookmarkPinController and
                        // SelectionPinController already use true for exactly
                        // this reason; this layer was the one inconsistent spot.
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM));
        layer.setMinZoom(ZOOM_FICHE_MIN);
        style.addLayer(layer);
        load();
    }

    private void load() {
        bgExecutor.execute(() -> {
            String geoJson = repository.listingsGeoJsonBlocking();
            if (geoJson == null) {
                if (BuildConfig.DEBUG) Log.w(TAG, "listingsGeoJsonBlocking() returned null — no fiche pins this session");
                return;
            }

            FeatureCollection fc;
            try {
                fc = FeatureCollection.fromJson(geoJson);
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse /listings/geo/ payload (" + geoJson.length()
                        + " chars): " + e + " — first 300 chars: "
                        + geoJson.substring(0, Math.min(300, geoJson.length())));
                return; // Unparseable payload — leave the layer empty.
            }
            List<Feature> src = fc.features();
            if (src == null || src.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Parsed /listings/geo/ OK but got 0 features");
                return;
            }
            if (BuildConfig.DEBUG) {
                Feature sample = src.get(0);
                Log.w(TAG, src.size() + " features parsed; sample properties: "
                        + (sample.properties() != null ? sample.properties().toString() : "null")
                        + " geometry type: " + (sample.geometry() != null
                                ? sample.geometry().type() : "null"));
            }

            // Give each feature a stable "icon" image key plus a normalised, unprefixed
            // "category_icon" property (used for filtering), collecting the distinct
            // emoji so we can register one bitmap per icon.
            //
            // The image key is ASCII (fiche_icon_0, fiche_icon_1, …), not the emoji
            // character itself. This was the first fix attempted for the rendering
            // bug described in onStyleReady() (switched before the real cause —
            // Expression.get vs token syntax — was isolated), and every subsequent
            // test kept it rather than reverting, so it's untested in isolation:
            // it may or may not be load-bearing on its own. Kept as cheap, reasonable
            // hygiene for a string used as a style/sprite identifier either way.
            Map<String, String> toRegister  = new HashMap<>(); // iconKey -> emoji
            Map<String, String> keyForEmoji = new HashMap<>(); // emoji -> iconKey (dedup)
            for (Feature f : src) {
                String emoji   = firstProperty(f, ICON_KEYS, DEFAULT_ICON);
                String iconKey = keyForEmoji.get(emoji);
                if (iconKey == null) {
                    iconKey = "fiche_icon_" + keyForEmoji.size();
                    keyForEmoji.put(emoji, iconKey);
                    toRegister.put(iconKey, emoji);
                }
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
                try {
                    allFeatures = src;
                    if (mapRef == null) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "mapRef null at apply time");
                        return;
                    }
                    Style style = mapRef.getStyle();
                    if (style == null) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "style null at apply time");
                        return;
                    }
                    for (Map.Entry<String, Bitmap> e : bitmaps.entrySet()) {
                        style.addImage(e.getKey(), e.getValue());
                    }
                    if (BuildConfig.DEBUG) {
                        boolean layerPresent = style.getLayer(FICHE_LAYER) != null;
                        boolean sourcePresent = style.getSource(FICHE_SOURCE) != null;
                        boolean imagePresent = style.getImage(bitmaps.keySet().iterator().next()) != null;
                        Log.w(TAG, "Pre-apply check — layer present: " + layerPresent
                                + ", source present: " + sourcePresent
                                + ", first image registered: " + imagePresent
                                + ", bitmaps registered: " + bitmaps.size());
                    }
                    applyFilter();
                } catch (Exception e) {
                    Log.w(TAG, "Exception applying fiche features to style: " + e, e);
                }
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

        List<Feature> visible = hiddenCategoryIcons.isEmpty() ? allFeatures : new ArrayList<>();
        if (!hiddenCategoryIcons.isEmpty()) {
            for (Feature f : allFeatures) {
                String icon = f.getStringProperty("category_icon");
                if (icon == null || !hiddenCategoryIcons.contains(icon)) visible.add(f);
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(visible));
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Applied " + visible.size() + "/" + allFeatures.size()
                    + " fiche features to the map source");
        }
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
