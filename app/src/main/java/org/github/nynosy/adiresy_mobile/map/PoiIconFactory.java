package org.github.nynosy.adiresy_mobile.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.maplibre.android.maps.Style;

import org.github.nynosy.adiresy_mobile.R;

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
        return Arrays.asList(
            new Entry("poi_healthcare", healthcare(sz), R.string.poi_label_healthcare),
            new Entry("poi_education",  education(sz),  R.string.poi_label_education),
            new Entry("poi_food",       food(sz),       R.string.poi_label_food),
            new Entry("poi_finance",    finance(sz),    R.string.poi_label_finance),
            new Entry("poi_lodging",    lodging(sz),    R.string.poi_label_lodging),
            new Entry("poi_shopping",   shopping(sz),   R.string.poi_label_shopping),
            new Entry("poi_fuel",       fuel(sz),       R.string.poi_label_fuel),
            new Entry("poi_default",    defaultIcon(sz),R.string.poi_label_default)
        );
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
