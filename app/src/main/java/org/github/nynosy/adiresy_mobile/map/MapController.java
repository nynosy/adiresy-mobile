package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;

import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;

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

    public MapLibreMap getMap() {
        return map;
    }
}
