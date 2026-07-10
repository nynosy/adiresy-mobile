package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.content.res.Configuration;

import java.io.File;

import org.github.nynosy.adiresy_mobile.data.prefs.AppPrefs;

public class StyleLoader {

    private final AppPrefs prefs;

    public StyleLoader(Context context) {
        prefs = AppPrefs.get(context);
    }

    /**
     * Returns the style to pass to MapLibreMap.setStyle(). The return value is either:
     * - A file:// URL pointing to a companion style-{light|dark}.json beside the tiles, or
     * - An inline MapLibre style JSON string (starts with '{') generated from the tile path, or
     * - An asset:// URL for the dev stub (no tiles downloaded).
     */
    private String getStyleUri(boolean darkMode) {
        String styleName = darkMode ? "style-dark.json" : "style-light.json";

        String buildingsPath   = prefs.hasBuildings()   ? prefs.getBuildingsPath()   : null;
        String boundariesPath  = prefs.hasBoundaries()  ? prefs.getBoundariesPath()  : null;

        String nationalPath = prefs.getDataPath();
        if (!nationalPath.isEmpty()) {
            File nationalFile = new File(nationalPath);
            File styleFile = new File(nationalFile.getParentFile(), styleName);
            if (styleFile.exists()) return styleFile.toURI().toString();
            if (nationalFile.exists())
                return buildInlineStyle(nationalFile.getAbsolutePath(), darkMode,
                        buildingsPath, boundariesPath);
        }

        return "asset://map/" + styleName;
    }

    public String getStyleUri(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return getStyleUri(nightMode == Configuration.UI_MODE_NIGHT_YES);
    }

    public boolean hasOfflineTiles() {
        return prefs.hasOfflineData();
    }

    private static String jsonStr(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String buildInlineStyle(String absolutePath, boolean darkMode,
                                           String buildingsPath, String boundariesPath) {
        // MapLibre Native Android 11.7+: local PMTiles via pmtiles://file:// prefix
        String tilesUrl = "pmtiles://file://" + jsonStr(absolutePath);

        String bg          = darkMode ? "#13151a" : "#E8DDD3";
        String water       = darkMode ? "#1a3a5c" : "#a8d4f5";
        String waterway    = darkMode ? "#1a3a5c" : "#74b9e8";
        String landcover   = darkMode ? "#1e2d1e" : "#d4e8c2";
        String park        = darkMode ? "#1a2e1a" : "#c8e6c9";
        String building    = darkMode ? "#3a3a4a" : "#BEBBB5";
        String buildingOut = darkMode ? "#252530" : "#9E9B95";
        String roadMinor   = darkMode ? "#3a3a4a" : "#F8F5EE";
        String roadMajor   = darkMode ? "#4a4a6a" : "#ffd27f";
        String roadTrunk   = darkMode ? "#5a4a2a" : "#f6a623";
        String roadMotor   = darkMode ? "#5a2a2a" : "#e25c5c";
        String boundary    = darkMode ? "#4a3a2a" : "#c8a870";
        String labelFill   = darkMode ? "#d0d0d0" : "#222222";
        String labelHalo   = darkMode ? "#000000" : "#E8DDD3";

        String buildingsUrl  = buildingsPath  != null ? "pmtiles://file://" + jsonStr(buildingsPath)  : null;
        String boundariesUrl = boundariesPath != null ? "pmtiles://file://" + jsonStr(boundariesPath) : null;

        // Extra sources appended after the base source
        StringBuilder extraSources = new StringBuilder();
        if (buildingsUrl != null)
            extraSources.append(",\"buildings\":{\"type\":\"vector\",\"url\":\"")
                        .append(buildingsUrl).append("\"}");
        if (boundariesUrl != null)
            extraSources.append(",\"boundaries\":{\"type\":\"vector\",\"url\":\"")
                        .append(boundariesUrl).append("\"}");

        // Legacy filter syntax: ["in", "property", value1, value2, ...]
        // Light: upper-left source → shadow falls on right/bottom faces of extruded buildings
        String lightIntensity = darkMode ? "0.25" : "0.35";
        return "{"
            + "\"version\":8,"
            + "\"name\":\"Adiresy Dev\","
            + "\"glyphs\":\"https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf\","
            + "\"center\":[46.8691,-18.9100],"
            + "\"zoom\":5,"
            + "\"light\":{\"anchor\":\"viewport\",\"color\":\"white\","
            +  "\"intensity\":" + lightIntensity + ",\"position\":[1.5,210,35]},"
            + "\"sources\":{"
            +   "\"omtiles\":{\"type\":\"vector\",\"url\":\"" + tilesUrl + "\"}"
            +   extraSources
            + "},"
            + "\"layers\":["
            // Background
            + "{\"id\":\"bg\",\"type\":\"background\","
            +  "\"paint\":{\"background-color\":\"" + bg + "\"}},"
            // Landcover (forest, grass, etc.)
            + "{\"id\":\"landcover\",\"type\":\"fill\",\"source\":\"omtiles\",\"source-layer\":\"landcover\","
            +  "\"paint\":{\"fill-color\":\"" + landcover + "\",\"fill-opacity\":0.6}},"
            // Parks
            + "{\"id\":\"park\",\"type\":\"fill\",\"source\":\"omtiles\",\"source-layer\":\"park\","
            +  "\"paint\":{\"fill-color\":\"" + park + "\",\"fill-opacity\":0.7}},"
            // Water fill
            + "{\"id\":\"water\",\"type\":\"fill\",\"source\":\"omtiles\",\"source-layer\":\"water\","
            +  "\"paint\":{\"fill-color\":\"" + water + "\"}},"
            // Waterway lines
            + "{\"id\":\"waterway\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"waterway\","
            +  "\"paint\":{\"line-color\":\"" + waterway + "\",\"line-width\":1}},"
            // Minor roads
            + "{\"id\":\"road-minor\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"transportation\","
            +  "\"filter\":[\"in\",\"class\",\"residential\",\"service\",\"track\",\"path\",\"footway\",\"cycleway\"],"
            +  "\"paint\":{\"line-color\":\"" + roadMinor + "\","
            +   "\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],12,0.5,16,2]}},"
            // Secondary/tertiary
            + "{\"id\":\"road-secondary\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"transportation\","
            +  "\"filter\":[\"in\",\"class\",\"secondary\",\"tertiary\",\"minor\"],"
            +  "\"paint\":{\"line-color\":\"" + roadMinor + "\","
            +   "\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],8,0.5,16,4]}},"
            // Primary
            + "{\"id\":\"road-primary\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"transportation\","
            +  "\"filter\":[\"in\",\"class\",\"primary\"],"
            +  "\"paint\":{\"line-color\":\"" + roadMajor + "\","
            +   "\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],6,0.5,16,6]}},"
            // Trunk
            + "{\"id\":\"road-trunk\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"transportation\","
            +  "\"filter\":[\"in\",\"class\",\"trunk\"],"
            +  "\"paint\":{\"line-color\":\"" + roadTrunk + "\","
            +   "\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],5,0.5,16,7]}},"
            // Motorway
            + "{\"id\":\"road-motorway\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"transportation\","
            +  "\"filter\":[\"in\",\"class\",\"motorway\"],"
            +  "\"paint\":{\"line-color\":\"" + roadMotor + "\","
            +   "\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],5,0.5,16,8]}},"
            // Admin boundaries (OMTiles coarse)
            + "{\"id\":\"boundary\",\"type\":\"line\",\"source\":\"omtiles\",\"source-layer\":\"boundary\","
            +  "\"filter\":[\"<=\",\"admin_level\",4],"
            +  "\"paint\":{\"line-color\":\"" + boundary + "\",\"line-width\":1,\"line-dasharray\":[3,2]}},"
            // Buildings overlay — fill-extrusion for 3D effect (fixed 5m height; VIDA tiles have no height data)
            + (buildingsUrl != null
                ? "{\"id\":\"buildings-fill\",\"type\":\"fill-extrusion\",\"source\":\"buildings\","
                +  "\"source-layer\":\"MDG\",\"minzoom\":14,"
                +  "\"paint\":{\"fill-extrusion-color\":\"" + building + "\","
                +   "\"fill-extrusion-height\":5,"
                +   "\"fill-extrusion-base\":0,"
                +   "\"fill-extrusion-opacity\":0.95}},"
                : "")
            // Boundaries overlay (present only when boundaries PMTiles downloaded)
            // admLevel: 1=region, 2=district, 3=commune, 4=fokontany, 99=coastline
            + (boundariesUrl != null
                ? "{\"id\":\"boundaries-region\",\"type\":\"line\",\"source\":\"boundaries\","
                +  "\"source-layer\":\"boundaries\","
                +  "\"filter\":[\"==\",\"admLevel\",1],"
                +  "\"paint\":{\"line-color\":\"" + boundary + "\",\"line-width\":1.5}},"
                +  "{\"id\":\"boundaries-district\",\"type\":\"line\",\"source\":\"boundaries\","
                +  "\"source-layer\":\"boundaries\","
                +  "\"filter\":[\"==\",\"admLevel\",2],\"minzoom\":7,"
                +  "\"paint\":{\"line-color\":\"" + boundary + "\",\"line-width\":1,\"line-dasharray\":[4,2]}},"
                +  "{\"id\":\"boundaries-commune\",\"type\":\"line\",\"source\":\"boundaries\","
                +  "\"source-layer\":\"boundaries\","
                +  "\"filter\":[\"==\",\"admLevel\",3],\"minzoom\":10,"
                +  "\"paint\":{\"line-color\":\"" + boundary + "\",\"line-width\":0.7,\"line-dasharray\":[2,2]}},"
                +  "{\"id\":\"boundaries-fokontany\",\"type\":\"line\",\"source\":\"boundaries\","
                +  "\"source-layer\":\"boundaries\","
                +  "\"filter\":[\"==\",\"admLevel\",4],\"minzoom\":12,"
                +  "\"paint\":{\"line-color\":\"" + boundary + "\",\"line-width\":0.5,\"line-opacity\":0.6}},"
                : "")
            // --- Everything above this line is fill/line. Labels and POI are rendered on top. ---
            // Road name labels (above buildings so they stay readable)
            + "{\"id\":\"road-label\",\"type\":\"symbol\",\"source\":\"omtiles\",\"source-layer\":\"transportation_name\","
            +  "\"minzoom\":13,"
            +  "\"layout\":{"
            +   "\"text-field\":[\"get\",\"name\"],"
            +   "\"text-font\":[\"Noto Sans Regular\"],"
            +   "\"text-size\":10,"
            +   "\"symbol-placement\":\"line\","
            +   "\"text-max-angle\":30,"
            +   "\"text-optional\":true"
            +  "},"
            +  "\"paint\":{"
            +   "\"text-color\":\"" + labelFill + "\","
            +   "\"text-halo-color\":\"" + labelHalo + "\","
            +   "\"text-halo-width\":1"
            +  "}},"
            // POI icons + labels — single layer so icon and text are treated as one unit.
            // icon-optional:false keeps the icon visible; text-optional:true drops the label
            // if there is not enough space, preventing the label from hiding its own icon.
            + "{\"id\":\"poi-symbol\",\"type\":\"symbol\",\"source\":\"omtiles\",\"source-layer\":\"poi\","
            +  "\"minzoom\":14,"
            +  "\"filter\":[\"<=\",\"rank\",3],"
            +  "\"layout\":{"
            +   "\"icon-image\":[\"match\",[\"get\",\"class\"],"
            +    "[\"hospital\",\"doctor\",\"pharmacy\"],\"poi_healthcare\","
            +    "[\"school\",\"college\",\"university\",\"kindergarten\"],\"poi_education\","
            +    "[\"restaurant\",\"cafe\",\"fast_food\",\"bar\",\"pub\"],\"poi_food\","
            +    "[\"bank\",\"atm\"],\"poi_finance\","
            +    "[\"hotel\",\"hostel\",\"guest_house\",\"motel\"],\"poi_lodging\","
            +    "[\"supermarket\",\"convenience\",\"clothes\",\"department_store\"],\"poi_shopping\","
            +    "[\"fuel\"],\"poi_fuel\","
            +    "\"poi_default\"],"
            +   "\"icon-size\":1.0,"
            +   "\"icon-allow-overlap\":false,"
            +   "\"icon-optional\":false,"
            +   "\"text-field\":[\"step\",[\"zoom\"],\"\"," + 15 + ",[\"get\",\"name\"]],"
            +   "\"text-font\":[\"Noto Sans Regular\"],"
            +   "\"text-size\":11,"
            +   "\"text-anchor\":\"top\","
            +   "\"text-offset\":[0,0.8],"
            +   "\"text-max-width\":8,"
            +   "\"text-optional\":true"
            +  "},"
            +  "\"paint\":{"
            +   "\"text-color\":\"" + labelFill + "\","
            +   "\"text-halo-color\":\"" + labelHalo + "\","
            +   "\"text-halo-width\":1"
            +  "}},"
            // Place labels — on top of everything so they're never occluded by buildings or POI dots
            // Cities and capitals
            + "{\"id\":\"place-city\",\"type\":\"symbol\",\"source\":\"omtiles\",\"source-layer\":\"place\","
            +  "\"filter\":[\"in\",\"class\",\"city\",\"capital\"],"
            +  "\"layout\":{"
            +   "\"text-field\":[\"get\",\"name\"],"
            +   "\"text-font\":[\"Noto Sans Bold\"],"
            +   "\"text-size\":[\"interpolate\",[\"linear\"],[\"zoom\"],4,10,8,15],"
            +   "\"text-anchor\":\"center\",\"text-max-width\":8"
            +  "},"
            +  "\"paint\":{"
            +   "\"text-color\":\"" + labelFill + "\","
            +   "\"text-halo-color\":\"" + labelHalo + "\","
            +   "\"text-halo-width\":1.5"
            +  "}},"
            // Towns and villages
            + "{\"id\":\"place-town\",\"type\":\"symbol\",\"source\":\"omtiles\",\"source-layer\":\"place\","
            +  "\"filter\":[\"in\",\"class\",\"town\",\"village\"],"
            +  "\"minzoom\":7,"
            +  "\"layout\":{"
            +   "\"text-field\":[\"get\",\"name\"],"
            +   "\"text-font\":[\"Noto Sans Regular\"],"
            +   "\"text-size\":[\"interpolate\",[\"linear\"],[\"zoom\"],7,10,12,13],"
            +   "\"text-anchor\":\"center\",\"text-max-width\":8"
            +  "},"
            +  "\"paint\":{"
            +   "\"text-color\":\"" + labelFill + "\","
            +   "\"text-halo-color\":\"" + labelHalo + "\","
            +   "\"text-halo-width\":1"
            +  "}},"
            // Suburbs and neighbourhoods
            + "{\"id\":\"place-suburb\",\"type\":\"symbol\",\"source\":\"omtiles\",\"source-layer\":\"place\","
            +  "\"filter\":[\"in\",\"class\",\"suburb\",\"neighbourhood\",\"hamlet\"],"
            +  "\"minzoom\":11,"
            +  "\"layout\":{"
            +   "\"text-field\":[\"get\",\"name\"],"
            +   "\"text-font\":[\"Noto Sans Regular\"],"
            +   "\"text-size\":11,"
            +   "\"text-anchor\":\"center\",\"text-max-width\":6"
            +  "},"
            +  "\"paint\":{"
            +   "\"text-color\":\"" + labelFill + "\","
            +   "\"text-halo-color\":\"" + labelHalo + "\","
            +   "\"text-halo-width\":1"
            +  "}}"
            + "]"
            + "}";
    }
}
