package com.cleanyourtv.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The custom views of the home screen. */
final class Views {
    private Views() {}

    static final float FOCUS_SCALE = 1.08f;

    /**
     * One app tile: a translucent navy glass card with a thin blue edge, holding
     * either a white logo (dock banners) or a white line icon with the app's name.
     */
    static final class Tile extends FrameLayout {
        final App app;
        final float x, y, w, h;
        TextView label;

        Tile(Context c, App app, float x, float y, float w, float h, boolean banner) {
            super(c);
            this.app = app;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            setOutlineProvider(new RoundOutline(Ui.px(12)));
            setClipToOutline(true);
            setBackground(Ui.rounded(0xA60C1830, 0x995AA8FF, Ui.px(12)));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (banner) {
                img.setImageDrawable(app.art);
                LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                lp.leftMargin = lp.rightMargin = Math.round(w * 0.06f);
                lp.topMargin = lp.bottomMargin = Math.round(h * 0.06f);
                addView(img, lp);
                return;
            }
            img.setImageDrawable(LineIcons.forApp(c, app));
            int icon = Math.round(h * 0.40f);
            LayoutParams lp = new LayoutParams(icon, icon, Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Math.round(h * 0.16f);
            addView(img, lp);
            TextView name = Ui.text(c, Ui.regular, 15, 0xFFF2F5FA);
            name.setText(app.label);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            tl.bottomMargin = Math.round(h * 0.11f);
            tl.leftMargin = tl.rightMargin = Ui.ipx(8);
            addView(name, tl);
        }

        void setFocusedState(boolean f) {
            float sc = f ? FOCUS_SCALE : 1f;
            animate().scaleX(sc).scaleY(sc).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            if (label != null) label.animate().alpha(f ? 1f : 0f).setDuration(200).start();
        }
    }

    /** Rounded clip for tiles (a named class: see the note in InputActivity). */
    static final class RoundOutline extends ViewOutlineProvider {
        private final float r;

        RoundOutline(float r) {
            this.r = r;
        }

        @Override
        public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
        }
    }

    /** Frosted pill button in the top bar. */
    static final class Pill extends LinearLayout {
        final TextView text;
        final Icon icon;
        float x, y, w, h;

        Pill(Context c, int iconType, String label) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.ipx(16), 0, Ui.ipx(20), 0);
            setBackground(Ui.rounded(0x24FFFFFF, 0x2EFFFFFF, Ui.px(22)));
            icon = new Icon(iconType, 0xFFD1D1D6);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            addView(iv, new LayoutParams(Ui.ipx(20), Ui.ipx(20)));
            text = Ui.text(c, Ui.regular, 17, 0xFFD1D1D6);
            text.setMaxWidth(Ui.ipx(220));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.ipx(9);
            addView(text, lp);
            setText(label);
        }

        void setText(String s) {
            text.setText(s);
        }

        float measureWidth() {
            float tw = Math.min(text.getPaint().measureText(String.valueOf(text.getText())), Ui.px(220));
            return Ui.ipx(16) + Ui.ipx(20) + Ui.ipx(9) + tw + Ui.ipx(20) + 2;
        }

        void setFocusedState(boolean f) {
            int c = f ? Color.WHITE : 0xFFD1D1D6;
            text.setTextColor(c);
            icon.setColor(c);
        }
    }

    /** Simple stroked icons for the pills. */
    static final class Icon extends Drawable {
        static final int INPUTS = 0, WIFI = 1, SETTINGS = 2;
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Icon(int type, int color) {
            this.type = type;
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int c) {
            p.setColor(c);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), x = b.left, y = b.top, cx = x + w / 2;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.09f);
            switch (type) {
                case INPUTS:
                    r.set(x + w * 0.08f, y + h * 0.14f, x + w * 0.92f, y + h * 0.70f);
                    c.drawRoundRect(r, w * 0.08f, w * 0.08f, p);
                    c.drawLine(x + w * 0.32f, y + h * 0.88f, x + w * 0.68f, y + h * 0.88f, p);
                    break;
                case WIFI: {
                    float cy = y + h * 0.84f;
                    for (float rad : new float[] {0.20f, 0.42f, 0.64f}) {
                        r.set(cx - w * rad, cy - w * rad, cx + w * rad, cy + w * rad);
                        c.drawArc(r, 225, 90, false, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy - w * 0.02f, w * 0.07f, p);
                    break;
                }
                default: {
                    float cy = y + h / 2;
                    c.drawCircle(cx, cy, w * 0.13f, p);
                    c.drawCircle(cx, cy, w * 0.30f, p);
                    p.setStrokeWidth(w * 0.13f);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        c.drawLine(cx + (float) Math.cos(a) * w * 0.33f, cy + (float) Math.sin(a) * w * 0.33f,
                                cx + (float) Math.cos(a) * w * 0.44f, cy + (float) Math.sin(a) * w * 0.44f, p);
                    }
                }
            }
        }

        @Override
        public void setAlpha(int a) {
            p.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** The single translucent focus highlight that springs between targets. */
    static final class Glass extends View {
        private final RectF cur = new RectF(), from = new RectF(), to = new RectF();
        private float rad, fromRad, toRad;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LinearGradient sheenShader;
        private final Matrix m = new Matrix();
        private ValueAnimator anim;
        private boolean shown;

        Glass(Context c) {
            super(c);
            fill.setColor(0x1A5AB4FF);
            sheenShader = new LinearGradient(0, 0, 0, 1, 0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            sheen.setShader(sheenShader);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1f, Ui.px(1.6f)));
            halo.setStyle(Paint.Style.STROKE);
            halo.setStrokeWidth(Math.max(1f, Ui.px(2.2f)));
            halo.setColor(0xFF4AA8FF);
            stroke.setColor(0xF27CC4FF);
        }

        void moveTo(RectF target, float radius, boolean animate) {
            if (anim != null) anim.cancel();
            if (!animate || !shown) {
                cur.set(target);
                rad = radius;
                shown = true;
                invalidate();
                return;
            }
            from.set(cur);
            fromRad = rad;
            to.set(target);
            toRad = radius;
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(340);
            anim.setInterpolator(new OvershootInterpolator(1.3f));
            anim.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                cur.set(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f,
                        from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f);
                rad = fromRad + (toRad - fromRad) * f;
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!shown) return;
            c.drawRoundRect(cur, rad, rad, fill);
            m.setScale(1f, Math.max(1f, cur.height() * 0.6f));
            m.postTranslate(0, cur.top);
            sheenShader.setLocalMatrix(m);
            c.drawRoundRect(cur, rad, rad, sheen);
            // outer blue glow: a few widening, fading rings (no blur)
            for (int k = 3; k >= 1; k--) {
                float o = Ui.px(2.2f) * k;
                halo.setAlpha(0x26 * (4 - k) / 2);
                c.drawRoundRect(cur.left - o, cur.top - o, cur.right + o, cur.bottom + o, rad + o, rad + o, halo);
            }
            float i = stroke.getStrokeWidth() / 2;
            c.drawRoundRect(cur.left + i, cur.top + i, cur.right - i, cur.bottom - i, rad, rad, stroke);
        }
    }

    /** Ambient glow behind the dock title, tinted from the focused app's banner. */
    static final class Glow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = 0;
        private float cx, cy, r;
        private ValueAnimator anim;

        Glow(Context c) {
            super(c);
        }

        void setCenter(float cx, float cy, float r) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
            update(color);
        }

        void setColor(int target, boolean animate) {
            if (anim != null) anim.cancel();
            if (!animate || color == 0) {
                update(target);
                return;
            }
            anim = ValueAnimator.ofArgb(color, target);
            anim.setDuration(450);
            anim.addUpdateListener(a -> update((int) a.getAnimatedValue()));
            anim.start();
        }

        private void update(int c) {
            color = c;
            if (r > 0 && c != 0) {
                p.setShader(new RadialGradient(cx, cy, r,
                        new int[] {(c & 0x00FFFFFF) | 0x6E000000, (c & 0x00FFFFFF) | 0x24000000, c & 0x00FFFFFF},
                        new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (color == 0 || r <= 0) return;
            c.save();
            c.scale(1f, 0.42f, cx, cy);
            c.drawCircle(cx, cy, r, p);
            c.restore();
        }
    }
}
