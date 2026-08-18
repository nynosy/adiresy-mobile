package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;

/**
 * Shows a single green pin at the most recently tapped building — a distinct
 * color from BookmarkPinController's markers so a fresh selection is never
 * confused with an existing favourite at the same spot.
 */
public class SelectionPinController {

    private static final String SOURCE = "selection-source";
    private static final String LAYER  = "selection-layer";
    private static final String ICON   = "selection-pin-icon";

    private static final int PIN_COLOR   = 0xFF2E7D32; // green, distinct from bookmark red / location blue
    private static final int PIN_SIZE_DP = 36;

    private MapLibreMap mapRef;

    public void setMap(MapLibreMap map) {
        mapRef = map;
    }

    public void onStyleReady(Style style, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = Math.round(PIN_SIZE_DP * density);
        style.addImage(ICON, pinBitmap(sizePx));
        style.addSource(new GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new SymbolLayer(LAYER, SOURCE)
                .withProperties(
                        PropertyFactory.iconImage(ICON),
                        PropertyFactory.iconAnchor("bottom"),
                        PropertyFactory.iconAllowOverlap(true)));
    }

    /** Shows the pin at the given location, replacing any previous one. */
    public void showAt(double lat, double lng) {
        GeoJsonSource src = source();
        if (src != null) src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lng, lat)));
    }

    public void clear() {
        GeoJsonSource src = source();
        if (src != null) src.setGeoJson(FeatureCollection.fromFeatures(new ArrayList<>()));
    }

    private GeoJsonSource source() {
        if (mapRef == null) return null;
        Style style = mapRef.getStyle();
        return style != null ? style.getSourceAs(SOURCE) : null;
    }

    /** Classic map-pin silhouette: circle head, pointed tail, white outline + centre dot. */
    private static Bitmap pinBitmap(int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        float cx = sizePx / 2f;
        float r  = sizePx * 0.30f;
        float cy = sizePx * 0.34f;
        float tipY = sizePx * 0.94f;
        float tailHalfWidth = r * 0.55f;

        Path tail = new Path();
        tail.moveTo(cx - tailHalfWidth, cy + r * 0.75f);
        tail.lineTo(cx, tipY);
        tail.lineTo(cx + tailHalfWidth, cy + r * 0.75f);
        tail.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(PIN_COLOR);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(tail, fill);
        canvas.drawCircle(cx, cy, r, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(sizePx * 0.045f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(tail, stroke);
        canvas.drawCircle(cx, cy, r, stroke);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, r * 0.4f, dot);

        return bmp;
    }
}
