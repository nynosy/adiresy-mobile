package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;

import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;

import java.util.List;

public class MapController {

    private final MapLibreMap map;
    private final Context context;

    public MapController(MapLibreMap map, Context context) {
        this.map = map;
        this.context = context.getApplicationContext();
    }

    /** Animates the camera to the given position. */
    public void centreOn(LatLng latLng, float zoom) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
    }

    /** Moves the camera instantly (no animation) — use when restoring state. */
    public void jumpTo(LatLng latLng, float zoom) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
    }

    /**
     * Stub for Phase 2 routing — draws a polyline between the given points.
     * Not used in Phase 1.
     */
    public void addPolyline(List<LatLng> points, int color) {
        // Phase 2 implementation
    }

    public MapLibreMap getMap() {
        return map;
    }
}
