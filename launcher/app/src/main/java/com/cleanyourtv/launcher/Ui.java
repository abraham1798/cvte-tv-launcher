package com.cleanyourtv.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Layout units, fonts and small view helpers. Sizes are given for a 1280-wide screen. */
final class Ui {
    static float s = 1f;
    static Typeface thin, light, regular, medium;

    private Ui() {}

    static void init(Activity a) {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        s = Math.max(dm.widthPixels, dm.heightPixels) / 1280f;
        if (thin == null) {
            AssetManager am = a.getAssets();
            thin = font(am, "Outfit-Thin.ttf");
            light = font(am, "Outfit-Light.ttf");
            regular = font(am, "Outfit-Regular.ttf");
            medium = font(am, "Outfit-Medium.ttf");
        }
    }

    private static Typeface font(AssetManager am, String name) {
        try {
            return Typeface.createFromAsset(am, "fonts/" + name);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static float px(float v) {
        return v * s;
    }

    static int ipx(float v) {
        return Math.max(1, Math.round(v * s));
    }

    static TextView text(Context c, Typeface tf, float size, int color) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Absolute placement inside a FrameLayout; w or h < 0 means wrap_content. */
    static void place(View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                w < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(w),
                h < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(h));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(x);
        lp.topMargin = Math.round(y);
        v.setLayoutParams(lp);
    }

    static GradientDrawable rounded(int fill, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        if (stroke != 0) d.setStroke(ipx(1), stroke);
        d.setCornerRadius(radius);
        return d;
    }

    /** A bright, saturated accent colour taken from an app banner, for the dock glow. */
    static int sampleColor(Drawable src) {
        if (src == null) return 0xFF3D7BD9;
        Drawable d = src.getConstantState() != null ? src.getConstantState().newDrawable().mutate() : src;
        Bitmap b = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 24, 14);
        d.draw(new Canvas(b));
        float[] hsv = new float[3];
        double sx = 0, sy = 0, weight = 0, sat = 0;
        for (int y = 0; y < 14; y++) {
            for (int x = 0; x < 24; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double w = hsv[1] * hsv[2] + 0.02;
                double h = Math.toRadians(hsv[0]);
                sx += Math.cos(h) * w;
                sy += Math.sin(h) * w;
                sat += hsv[1] * w;
                weight += w;
            }
        }
        b.recycle();
        if (weight == 0 || sat / weight < 0.12) return 0xFF3D7BD9; // white logos: the theme blue
        float hue = (float) Math.toDegrees(Math.atan2(sy, sx));
        if (hue < 0) hue += 360;
        float sv = (float) Math.min(0.9, Math.max(0.5, sat / weight * 1.2));
        return Color.HSVToColor(new float[] {hue, sv, 0.95f});
    }
}
