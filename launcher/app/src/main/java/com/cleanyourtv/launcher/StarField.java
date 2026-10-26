package com.cleanyourtv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A light sky animation over the wallpaper: the picture's own stars twinkle, and
 * the constellation Libra lights up one star at a time, then its lines glow.
 * Only this small patch of sky is redrawn, at ~15 fps, and only while running.
 * Positions are in 1280x720 layout units and match the Poseidon wallpaper.
 */
final class StarField extends View {
    private static final long FRAME_MS = 66;
    private static final float CYCLE = 11f;          // seconds per Libra sequence
    private static final float STEP = 0.55f;         // delay between Libra stars

    // Region of the screen this view covers (layout units).
    private static final float LEFT = 380, TOP = 20, RIGHT = 790, BOTTOM = 360;

    // The wallpaper's own stars: x, y, size, twinkle period (s), phase.
    private static final float[][] TWINKLE = {
        {594, 124, 1.6f, 4.3f, 0.0f},
        {641, 137, 1.6f, 5.7f, 1.9f},
        {747, 51, 0.8f, 3.6f, 0.7f},
        {705, 125, 0.7f, 6.2f, 2.8f},
        {658, 160, 0.7f, 4.9f, 4.1f},
    };

    // Libra from its real positions (north up, east left), brightness-scaled sizes,
    // in the order they light up: Brachium, Zubenelgenubi, Zubeneschamali,
    // Zubenelakrab, upsilon, tau.
    private static final float[][] LIBRA = {
        {494, 289, 0.85f},  // sigma Lib (Brachium)
        {525, 199, 1.0f},   // alpha Lib (Zubenelgenubi)
        {464, 133, 1.05f},  // beta Lib (Zubeneschamali)
        {421, 187, 0.8f},   // gamma Lib (Zubenelakrab)
        {418, 317, 0.8f},   // upsilon Lib
        {414, 333, 0.75f},  // tau Lib
    };
    private static final int[][] LINES = {{1, 2}, {2, 3}, {3, 1}, {1, 0}, {3, 4}, {4, 5}};

    private final boolean libra;
    private final Bitmap glow;
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final long start = SystemClock.uptimeMillis();
    private final Tick tick = new Tick(this);
    private boolean running;

    StarField(Context c, boolean libra) {
        super(c);
        this.libra = libra;
        // one soft white glow, scaled per star (cheaper than a shader per frame)
        glow = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(32, 32, 32, new int[] {0xFFFFFFFF, 0x66DDE8FF, 0x00DDE8FF},
                new float[] {0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(glow).drawCircle(32, 32, 32, p);
        dot.setColor(0xFFF4F7FF);
        line.setColor(0xFFCFDcFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, Ui.px(0.8f)));
    }

    void place(FrameLayout parent) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.round(Ui.px(RIGHT - LEFT)), Math.round(Ui.px(BOTTOM - TOP)));
        lp.leftMargin = Math.round(Ui.px(LEFT));
        lp.topMargin = Math.round(Ui.px(TOP));
        parent.addView(this, lp);
    }

    void setRunning(boolean on) {
        if (on == running) return;
        running = on;
        removeCallbacks(tick);
        if (on) post(tick);
    }

    private float x(float u) {
        return Ui.px(u - LEFT);
    }

    private float y(float u) {
        return Ui.px(u - TOP);
    }

    private void drawGlow(Canvas c, float cx, float cy, float radius, float alpha) {
        if (alpha <= 0.01f) return;
        glowPaint.setAlpha(Math.round(Math.min(1f, alpha) * 255));
        r.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawBitmap(glow, null, r, glowPaint);
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - start) / 1000f;

        for (float[] s : TWINKLE) {
            // mostly calm, with a brief soft flare now and then
            double ph = 2 * Math.PI * (t / s[3]) + s[4];
            float flare = (float) Math.pow(Math.max(0, Math.sin(ph)), 6);
            drawGlow(c, x(s[0]), y(s[1]), Ui.px(5 + 5 * flare) * s[2], 0.2f + 0.4f * flare);
        }
        if (!libra) return;

        float ct = t % CYCLE;
        // lines: faint at rest, brighter once every star has lit
        float allLit = LIBRA.length * STEP;
        float lineBoost = ct < allLit ? 0 : (float) Math.max(0, Math.sin(Math.PI * Math.min(1, (ct - allLit) / 2.4f)));
        line.setAlpha(Math.round(255 * (0.035f + 0.08f * lineBoost)));
        for (int[] l : LINES) {
            float[] a = LIBRA[l[0]], b = LIBRA[l[1]];
            c.drawLine(x(a[0]), y(a[1]), x(b[0]), y(b[1]), line);
        }
        for (int i = 0; i < LIBRA.length; i++) {
            float[] s = LIBRA[i];
            float dt = ct - i * STEP;                       // seconds since this star lit
            float lit = dt < 0 ? 0 : (float) Math.exp(-dt / 0.9f);
            float glowAll = 0.2f * lineBoost;
            float cx = x(s[0]), cy = y(s[1]);
            drawGlow(c, cx, cy, Ui.px(4 + 8 * lit) * s[2], 0.18f + 0.42f * Math.max(lit, glowAll));
            dot.setAlpha(Math.round(255 * (0.45f + 0.3f * lit)));
            c.drawCircle(cx, cy, Ui.px(1.0f) * s[2], dot);
        }
    }

    // A named class rather than an anonymous one: see the note in InputActivity.
    private static final class Tick implements Runnable {
        private final StarField v;

        Tick(StarField v) {
            this.v = v;
        }

        @Override
        public void run() {
            if (!v.running) return;
            v.invalidate();
            v.postDelayed(this, FRAME_MS);
        }
    }
}
