package com.cleanyourtv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * White line icons for the grid tiles, so every app matches the glass style.
 * Known packages get a drawn icon; anything else gets its own icon as a white
 * silhouette.
 */
final class LineIcons extends Drawable {
    static final int AIRPLAY = 0, GEAR = 1, SHARE = 2, FOLDER = 3, MEDIA = 4, GLOBE = 5, PLAY = 6;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private LineIcons(int type) {
        this.type = type;
        p.setColor(0xFFFFFFFF);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    static Drawable forApp(Context c, App app) {
        switch (app.pkg) {
            case "com.ecloud.eairplay":
                return new LineIcons(AIRPLAY);
            case "com.cvte.tv.androidsetting":
            case "com.android.settings":
                return new LineIcons(GEAR);
            case "com.ecloud.eshare.server":
                return new LineIcons(SHARE);
            case "com.cvte.filebrowser.activity":
            case "com.android.documentsui":
                return new LineIcons(FOLDER);
            case "com.cvte.tv.media":
            case "com.cvte.tv.player":
                return new LineIcons(MEDIA);
            case "com.android.browser":
                return new LineIcons(GLOBE);
            case "com.android.vending":
                return new LineIcons(PLAY);
            default:
                Drawable d = app.icon.getConstantState() != null
                        ? app.icon.getConstantState().newDrawable().mutate() : app.icon.mutate();
                d.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
                return d;
        }
    }

    // Coordinates below are fractions of the icon box.
    private float X(float f) {
        return getBounds().left + f * getBounds().width();
    }

    private float Y(float f) {
        return getBounds().top + f * getBounds().height();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.06f);
        path.reset();
        switch (type) {
            case AIRPLAY:
                // screen open at the bottom, with a solid triangle rising into it
                path.moveTo(X(0.30f), Y(0.68f));
                path.lineTo(X(0.12f), Y(0.68f));
                path.lineTo(X(0.12f), Y(0.18f));
                path.lineTo(X(0.88f), Y(0.18f));
                path.lineTo(X(0.88f), Y(0.68f));
                path.lineTo(X(0.70f), Y(0.68f));
                c.drawPath(path, p);
                path.reset();
                path.moveTo(X(0.50f), Y(0.55f));
                path.lineTo(X(0.74f), Y(0.86f));
                path.lineTo(X(0.26f), Y(0.86f));
                path.close();
                p.setStyle(Paint.Style.FILL);
                c.drawPath(path, p);
                break;
            case GEAR: {
                float cx = X(0.5f), cy = Y(0.5f);
                c.drawCircle(cx, cy, w * 0.12f, p);
                c.drawCircle(cx, cy, w * 0.27f, p);
                p.setStrokeWidth(w * 0.12f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(cx + (float) Math.cos(a) * w * 0.31f, cy + (float) Math.sin(a) * w * 0.31f,
                            cx + (float) Math.cos(a) * w * 0.42f, cy + (float) Math.sin(a) * w * 0.42f, p);
                }
                break;
            }
            case SHARE:
                // monitor, a phone in front of it, and a signal over the phone
                c.save();
                c.clipRect(X(0.58f), Y(0.38f), X(0.90f), Y(0.92f), android.graphics.Region.Op.DIFFERENCE);
                r.set(X(0.06f), Y(0.28f), X(0.66f), Y(0.68f));
                c.drawRoundRect(r, w * 0.04f, w * 0.04f, p);
                c.restore();
                c.drawLine(X(0.26f), Y(0.82f), X(0.46f), Y(0.82f), p);
                c.drawLine(X(0.36f), Y(0.68f), X(0.36f), Y(0.82f), p);
                r.set(X(0.62f), Y(0.42f), X(0.86f), Y(0.88f));
                c.drawRoundRect(r, w * 0.04f, w * 0.04f, p);
                for (float rad : new float[] {0.10f, 0.20f}) {
                    r.set(X(0.80f) - w * rad, Y(0.26f) - w * rad, X(0.80f) + w * rad, Y(0.26f) + w * rad);
                    c.drawArc(r, 270, 90, false, p);
                }
                break;
            case FOLDER:
                path.moveTo(X(0.10f), Y(0.80f));
                path.lineTo(X(0.10f), Y(0.22f));
                path.lineTo(X(0.38f), Y(0.22f));
                path.lineTo(X(0.47f), Y(0.32f));
                path.lineTo(X(0.90f), Y(0.32f));
                path.lineTo(X(0.90f), Y(0.80f));
                path.close();
                c.drawPath(path, p);
                c.drawLine(X(0.10f), Y(0.43f), X(0.90f), Y(0.43f), p);
                break;
            case MEDIA:
                // a screen with a play button, and a music note at its corner
                r.set(X(0.06f), Y(0.16f), X(0.70f), Y(0.70f));
                c.drawRoundRect(r, w * 0.05f, w * 0.05f, p);
                path.moveTo(X(0.30f), Y(0.30f));
                path.lineTo(X(0.50f), Y(0.43f));
                path.lineTo(X(0.30f), Y(0.56f));
                path.close();
                p.setStyle(Paint.Style.FILL);
                c.drawPath(path, p);
                c.drawCircle(X(0.74f), Y(0.82f), w * 0.07f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(X(0.80f), Y(0.82f), X(0.80f), Y(0.52f), p);
                c.drawLine(X(0.80f), Y(0.52f), X(0.93f), Y(0.60f), p);
                break;
            case GLOBE: {
                float cx = X(0.5f), cy = Y(0.5f), rad = w * 0.38f;
                c.drawCircle(cx, cy, rad, p);
                r.set(cx - rad * 0.45f, cy - rad, cx + rad * 0.45f, cy + rad);
                c.drawOval(r, p);
                c.drawLine(cx - rad, cy, cx + rad, cy, p);
                c.drawLine(cx, cy - rad, cx, cy + rad, p);
                float dy = rad * 0.5f, dx = (float) Math.sqrt(rad * rad - dy * dy);
                c.drawLine(cx - dx, cy - dy, cx + dx, cy - dy, p);
                c.drawLine(cx - dx, cy + dy, cx + dx, cy + dy, p);
                break;
            }
            default: // PLAY: outlined play triangle with the store's inner folds
                path.moveTo(X(0.24f), Y(0.12f));
                path.lineTo(X(0.84f), Y(0.50f));
                path.lineTo(X(0.24f), Y(0.88f));
                path.close();
                c.drawPath(path, p);
                c.drawLine(X(0.24f), Y(0.12f), X(0.60f), Y(0.58f), p);
                c.drawLine(X(0.24f), Y(0.88f), X(0.60f), Y(0.42f), p);
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
