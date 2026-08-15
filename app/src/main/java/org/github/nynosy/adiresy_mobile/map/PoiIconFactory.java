package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;

import org.maplibre.android.maps.Style;

import org.github.nynosy.adiresy_mobile.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates bitmap icons for each POI category and registers them with the map style.
 * Icons are 22dp colored circles with a white border and white symbol inside.
 */
public final class PoiIconFactory {

    private static final int SIZE_DP = 22;

    private static final int COL_HEALTHCARE = 0xFFf44336;
    private static final int COL_EDUCATION  = 0xFF2196f3;
    private static final int COL_FOOD       = 0xFFff9800;
    private static final int COL_FINANCE    = 0xFF9c27b0;
    private static final int COL_LODGING    = 0xFF009688;
    private static final int COL_SHOPPING   = 0xFF4caf50;
    private static final int COL_FUEL       = 0xFF795548;
    private static final int COL_DEFAULT    = 0xFF607d8b;

    /** One entry for the legend dialog and the map style. */
    public static class Entry {
        public final String key;
        public final Bitmap icon;
        public final int labelRes;
        Entry(String key, Bitmap icon, int labelRes) {
            this.key = key;
            this.icon = icon;
            this.labelRes = labelRes;
        }
    }

    /** Returns all POI entries (icon bitmap + label string resource). */
    public static List<Entry> allEntries(Context ctx) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        int sz = Math.round(SIZE_DP * dp);
        List<Entry> entries = new ArrayList<>(Arrays.asList(
            new Entry("poi_healthcare", healthcare(sz), R.string.poi_label_healthcare),
            new Entry("poi_education",  education(sz),  R.string.poi_label_education),
            new Entry("poi_food",       food(sz),       R.string.poi_label_food),
            new Entry("poi_finance",    finance(sz),    R.string.poi_label_finance),
            new Entry("poi_lodging",    lodging(sz),    R.string.poi_label_lodging),
            new Entry("poi_shopping",   shopping(sz),   R.string.poi_label_shopping),
            new Entry("poi_fuel",       fuel(sz),       R.string.poi_label_fuel)
        ));
        // Categories below reuse real Maki pictograms (CC0) instead of hand-drawn
        // glyphs -- see docs/THIRD_PARTY_LICENSES.md. Grouped by the poi-overlay.yml
        // OSM tag values they cover; StyleLoader's icon-image match must stay in sync.
        entries.addAll(Arrays.asList(
            new Entry("poi_veterinary", vectorIcon(ctx, sz, 0xFF6D4C41, R.drawable.ic_maki_veterinary), R.string.poi_label_veterinary),
            new Entry("poi_library", vectorIcon(ctx, sz, 0xFF3949AB, R.drawable.ic_maki_library), R.string.poi_label_library),
            new Entry("poi_police", vectorIcon(ctx, sz, 0xFF1A237E, R.drawable.ic_maki_police), R.string.poi_label_police),
            new Entry("poi_fire", vectorIcon(ctx, sz, 0xFFBF360C, R.drawable.ic_maki_fire_station), R.string.poi_label_fire),
            new Entry("poi_government", vectorIcon(ctx, sz, 0xFF5D4037, R.drawable.ic_maki_town_hall), R.string.poi_label_government),
            new Entry("poi_post", vectorIcon(ctx, sz, 0xFFF9A825, R.drawable.ic_maki_post), R.string.poi_label_post),
            new Entry("poi_embassy", vectorIcon(ctx, sz, 0xFF455A64, R.drawable.ic_maki_embassy), R.string.poi_label_embassy),
            new Entry("poi_worship", vectorIcon(ctx, sz, 0xFF6A1B9A, R.drawable.ic_maki_place_of_worship), R.string.poi_label_worship),
            new Entry("poi_cinema", vectorIcon(ctx, sz, 0xFF303F9F, R.drawable.ic_maki_cinema), R.string.poi_label_cinema),
            new Entry("poi_theatre", vectorIcon(ctx, sz, 0xFF4527A0, R.drawable.ic_maki_theatre), R.string.poi_label_theatre),
            new Entry("poi_parking", vectorIcon(ctx, sz, 0xFF0288D1, R.drawable.ic_maki_parking), R.string.poi_label_parking),
            new Entry("poi_bus", vectorIcon(ctx, sz, 0xFFF57C00, R.drawable.ic_maki_bus), R.string.poi_label_bus),
            new Entry("poi_ferry", vectorIcon(ctx, sz, 0xFF00838F, R.drawable.ic_maki_ferry), R.string.poi_label_ferry),
            new Entry("poi_rail", vectorIcon(ctx, sz, 0xFF37474F, R.drawable.ic_maki_rail), R.string.poi_label_rail),
            new Entry("poi_care", vectorIcon(ctx, sz, 0xFFC2185B, R.drawable.ic_maki_heart), R.string.poi_label_care),
            new Entry("poi_prison", vectorIcon(ctx, sz, 0xFF424242, R.drawable.ic_maki_prison), R.string.poi_label_prison),
            new Entry("poi_taxi", vectorIcon(ctx, sz, 0xFFFBC02D, R.drawable.ic_maki_taxi), R.string.poi_label_taxi),
            new Entry("poi_car", vectorIcon(ctx, sz, 0xFF0277BD, R.drawable.ic_maki_car), R.string.poi_label_car),
            new Entry("poi_bicycle_rental", vectorIcon(ctx, sz, 0xFF43A047, R.drawable.ic_maki_bicycle_share), R.string.poi_label_bicycle_rental),
            new Entry("poi_bicycle", vectorIcon(ctx, sz, 0xFF2E7D32, R.drawable.ic_maki_bicycle), R.string.poi_label_bicycle),
            new Entry("poi_toilets", vectorIcon(ctx, sz, 0xFF616161, R.drawable.ic_maki_toilet), R.string.poi_label_toilets),
            new Entry("poi_water", vectorIcon(ctx, sz, 0xFF039BE5, R.drawable.ic_maki_drinking_water), R.string.poi_label_water),
            new Entry("poi_shelter", vectorIcon(ctx, sz, 0xFF8D6E63, R.drawable.ic_maki_shelter), R.string.poi_label_shelter),
            new Entry("poi_charging", vectorIcon(ctx, sz, 0xFF558B2F, R.drawable.ic_maki_charging_station), R.string.poi_label_charging),
            new Entry("poi_pharmacy_chemist", vectorIcon(ctx, sz, 0xFF00897B, R.drawable.ic_maki_pharmacy), R.string.poi_label_pharmacy_chemist),
            new Entry("poi_bakery", vectorIcon(ctx, sz, 0xFFA1887F, R.drawable.ic_maki_bakery), R.string.poi_label_bakery),
            new Entry("poi_alcohol", vectorIcon(ctx, sz, 0xFF8D6E63, R.drawable.ic_maki_alcohol_shop), R.string.poi_label_alcohol),
            new Entry("poi_grocery_misc", vectorIcon(ctx, sz, 0xFF689F38, R.drawable.ic_maki_grocery), R.string.poi_label_grocery_misc),
            new Entry("poi_shoes", vectorIcon(ctx, sz, 0xFF6D4C41, R.drawable.ic_maki_shoe), R.string.poi_label_shoes),
            new Entry("poi_jewelry", vectorIcon(ctx, sz, 0xFFAD1457, R.drawable.ic_maki_jewelry_store), R.string.poi_label_jewelry),
            new Entry("poi_optician", vectorIcon(ctx, sz, 0xFF00ACC1, R.drawable.ic_maki_optician), R.string.poi_label_optician),
            new Entry("poi_mobile", vectorIcon(ctx, sz, 0xFF1E88E5, R.drawable.ic_maki_mobile_phone), R.string.poi_label_mobile),
            new Entry("poi_hardware", vectorIcon(ctx, sz, 0xFF757575, R.drawable.ic_maki_hardware), R.string.poi_label_hardware),
            new Entry("poi_garden_centre", vectorIcon(ctx, sz, 0xFF7CB342, R.drawable.ic_maki_garden_centre), R.string.poi_label_garden_centre),
            new Entry("poi_travel", vectorIcon(ctx, sz, 0xFF5E35B1, R.drawable.ic_maki_suitcase), R.string.poi_label_travel),
            new Entry("poi_office", vectorIcon(ctx, sz, 0xFF78909C, R.drawable.ic_maki_building), R.string.poi_label_office),
            new Entry("poi_museum", vectorIcon(ctx, sz, 0xFF7B1FA2, R.drawable.ic_maki_museum), R.string.poi_label_museum),
            new Entry("poi_information", vectorIcon(ctx, sz, 0xFF29B6F6, R.drawable.ic_maki_information), R.string.poi_label_information),
            new Entry("poi_attraction", vectorIcon(ctx, sz, 0xFFFB8C00, R.drawable.ic_maki_attraction), R.string.poi_label_attraction),
            new Entry("poi_park", vectorIcon(ctx, sz, 0xFF388E3C, R.drawable.ic_maki_park), R.string.poi_label_park),
            new Entry("poi_sports", vectorIcon(ctx, sz, 0xFF9CCC65, R.drawable.ic_maki_pitch), R.string.poi_label_sports),
            new Entry("poi_swimming", vectorIcon(ctx, sz, 0xFF26C6DA, R.drawable.ic_maki_swimming), R.string.poi_label_swimming),
            new Entry("poi_shop_generic", vectorIcon(ctx, sz, 0xFF9E9E9E, R.drawable.ic_maki_shop), R.string.poi_label_shop_generic)
        ));
        entries.add(new Entry("poi_default", defaultIcon(sz), R.string.poi_label_default));
        return entries;
    }

    /** Adds all POI icons to the given style. Call inside the setStyle() callback. */
    public static void addAllToStyle(Style style, Context ctx) {
        for (Entry e : allEntries(ctx)) {
            style.addImage(e.key, e.icon);
        }
    }

    // ── Icon drawing ────────────────────────────────────────────────────────

    /** Red circle with white medical cross. */
    private static Bitmap healthcare(int sz) {
        Canvas c = canvas(sz, COL_HEALTHCARE);
        Paint p = fill(sz);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;
        float arm = r * 0.28f, len = r * 0.62f;
        c.drawRect(cx - arm, cy - len, cx + arm, cy + len, p);
        c.drawRect(cx - len, cy - arm, cx + len, cy + arm, p);
        return bmp(c);
    }

    /** Blue circle with white open book (two pages + spine). */
    private static Bitmap education(int sz) {
        Canvas c = canvas(sz, COL_EDUCATION);
        Paint p = stroke(sz, 0.11f);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;

        Path left = new Path();
        left.moveTo(cx, cy - r * 0.5f);
        left.lineTo(cx - r * 0.62f, cy - r * 0.3f);
        left.lineTo(cx - r * 0.62f, cy + r * 0.52f);
        left.lineTo(cx, cy + r * 0.3f);
        left.close();

        Path right = new Path();
        right.moveTo(cx, cy - r * 0.5f);
        right.lineTo(cx + r * 0.62f, cy - r * 0.3f);
        right.lineTo(cx + r * 0.62f, cy + r * 0.52f);
        right.lineTo(cx, cy + r * 0.3f);
        right.close();

        c.drawPath(left, p);
        c.drawPath(right, p);
        c.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.3f, p);
        return bmp(c);
    }

    /** Orange circle with white fork (3 tines + handle). */
    private static Bitmap food(int sz) {
        Canvas c = canvas(sz, COL_FOOD);
        Paint p = stroke(sz, 0.11f);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;
        float top = cy - r * 0.6f, bot = cy - r * 0.05f, gap = r * 0.3f;
        c.drawLine(cx - gap, top, cx - gap, bot, p);
        c.drawLine(cx,       top, cx,       bot, p);
        c.drawLine(cx + gap, top, cx + gap, bot, p);
        c.drawLine(cx - gap, bot, cx + gap, bot, p);
        c.drawLine(cx, bot, cx, cy + r * 0.62f, p);
        return bmp(c);
    }

    /** Purple circle with white "$" character. */
    private static Bitmap finance(int sz) {
        Canvas c = canvas(sz, COL_FINANCE);
        Paint p = fill(sz);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sz * 0.58f);
        p.setFakeBoldText(true);
        float cx = sz / 2f, cy = sz / 2f;
        c.drawText("$", cx, cy - (p.ascent() + p.descent()) / 2f, p);
        return bmp(c);
    }

    /** Teal circle with white bed (headboard + mattress + pillow). */
    private static Bitmap lodging(int sz) {
        Canvas c = canvas(sz, COL_LODGING);
        Paint p = fill(sz);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;
        c.drawRect(cx - r * 0.65f, cy - r * 0.45f, cx - r * 0.35f, cy + r * 0.52f, p);
        c.drawRect(cx - r * 0.35f, cy + r * 0.1f,  cx + r * 0.65f, cy + r * 0.52f, p);
        c.drawRoundRect(new RectF(cx - r*0.28f, cy - r*0.35f, cx + r*0.6f, cy + r*0.12f),
                sz * 0.06f, sz * 0.06f, p);
        return bmp(c);
    }

    /** Green circle with white shopping bag (body + arc handle). */
    private static Bitmap shopping(int sz) {
        Canvas c = canvas(sz, COL_SHOPPING);
        Paint p = fill(sz);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;
        c.drawRoundRect(new RectF(cx - r*0.52f, cy - r*0.15f, cx + r*0.52f, cy + r*0.6f),
                sz * 0.07f, sz * 0.07f, p);
        Paint sp = stroke(sz, 0.11f);
        c.drawArc(new RectF(cx - r*0.32f, cy - r*0.68f, cx + r*0.32f, cy - r*0.1f),
                0, -180, false, sp);
        return bmp(c);
    }

    /** Brown circle with white fuel pump (body + nozzle arm). */
    private static Bitmap fuel(int sz) {
        Canvas c = canvas(sz, COL_FUEL);
        Paint p = fill(sz);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;
        c.drawRect(cx - r*0.52f, cy - r*0.45f, cx + r*0.15f, cy + r*0.55f, p);
        Paint sp = stroke(sz, 0.12f);
        float armY = cy - r * 0.18f;
        c.drawLine(cx + r*0.15f, armY,          cx + r*0.62f, armY,         sp);
        c.drawLine(cx + r*0.62f, armY,          cx + r*0.62f, cy + r*0.3f,  sp);
        return bmp(c);
    }

    /** Gray circle with white info "i" symbol. */
    private static Bitmap defaultIcon(int sz) {
        Canvas c = canvas(sz, COL_DEFAULT);
        Paint p = fill(sz);
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f;
        c.drawCircle(cx, cy - r * 0.32f, r * 0.13f, p);
        Paint sp = stroke(sz, 0.13f);
        c.drawLine(cx, cy - r*0.08f, cx, cy + r*0.52f, sp);
        return bmp(c);
    }

    /** Colored circle badge with a real Maki pictogram (already white-filled) centered inside. */
    private static Bitmap vectorIcon(Context ctx, int sz, int bgColor, int drawableRes) {
        Canvas c = canvas(sz, bgColor);
        Drawable d = AppCompatResources.getDrawable(ctx, drawableRes);
        int glyph = Math.round(sz * 0.62f);
        int off = (sz - glyph) / 2;
        d.setBounds(off, off, off + glyph, off + glyph);
        d.draw(c);
        return bmp(c);
    }

    // ── Canvas / Paint helpers ──────────────────────────────────────────────

    // Holds the active bitmap so bmp(canvas) can return it.
    private static Bitmap activeBitmap;

    private static Canvas canvas(int sz, int bgColor) {
        activeBitmap = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(activeBitmap);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(bgColor);
        c.drawCircle(sz / 2f, sz / 2f, sz / 2f - 0.5f, bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(sz * 0.08f);
        c.drawCircle(sz / 2f, sz / 2f, sz / 2f - sz * 0.04f - 0.5f, border);

        return c;
    }

    private static Paint fill(int sz) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Paint stroke(int sz, float widthFactor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sz * widthFactor);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }

    private static Bitmap bmp(Canvas ignored) {
        return activeBitmap;
    }

    private PoiIconFactory() {}
}
