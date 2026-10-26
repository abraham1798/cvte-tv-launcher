import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Makes a small, heavily compressed wallpaper look better on a 1080p TV:
 * Lanczos upscale, flat-area deblocking, edge-aware unsharp mask, fine grain.
 *   java -cp <twelvemonkeys jars> tools/Enhance.java in.webp out.png [width height]
 */
public class Enhance {
    public static void main(String[] a) throws Exception {
        BufferedImage src = ImageIO.read(new File(a[0]));
        if (src == null) throw new IllegalStateException("cannot decode " + a[0]);
        int W = a.length > 3 ? Integer.parseInt(a[2]) : 1920, H = a.length > 3 ? Integer.parseInt(a[3]) : 1080;
        float[][] img = toFloat(src);                         // [3][w*h], 0..255
        img = resize(img, src.getWidth(), src.getHeight(), W, H);
        img = deblock(img, W, H);
        img = sharpen(img, W, H);
        ImageIO.write(toImage(img, W, H, 1.1f), "png", new File(a[1]));
        System.out.println("wrote " + a[1] + " " + W + "x" + H);
    }

    static float[][] toFloat(BufferedImage b) {
        int w = b.getWidth(), h = b.getHeight();
        float[][] c = new float[3][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = b.getRGB(x, y), i = y * w + x;
                c[0][i] = (p >> 16) & 255;
                c[1][i] = (p >> 8) & 255;
                c[2][i] = p & 255;
            }
        }
        return c;
    }

    static double lanczos(double x) {
        if (x == 0) return 1;
        if (Math.abs(x) >= 3) return 0;
        double px = Math.PI * x;
        return 3 * Math.sin(px) * Math.sin(px / 3) / (px * px);
    }

    /** Separable Lanczos-3 resample. */
    static float[][] resize(float[][] in, int w, int h, int W, int H) {
        float[][] tmp = new float[3][W * h];
        double sx = (double) w / W;
        for (int x = 0; x < W; x++) {
            double cx = (x + 0.5) * sx - 0.5;
            int x0 = (int) Math.floor(cx) - 2;
            double[] k = new double[6];
            double sum = 0;
            for (int j = 0; j < 6; j++) sum += k[j] = lanczos(cx - (x0 + j));
            for (int y = 0; y < h; y++) {
                for (int c = 0; c < 3; c++) {
                    double v = 0;
                    for (int j = 0; j < 6; j++) {
                        int xx = Math.max(0, Math.min(w - 1, x0 + j));
                        v += in[c][y * w + xx] * k[j];
                    }
                    tmp[c][y * W + x] = (float) (v / sum);
                }
            }
        }
        float[][] out = new float[3][W * H];
        double sy = (double) h / H;
        for (int y = 0; y < H; y++) {
            double cy = (y + 0.5) * sy - 0.5;
            int y0 = (int) Math.floor(cy) - 2;
            double[] k = new double[6];
            double sum = 0;
            for (int j = 0; j < 6; j++) sum += k[j] = lanczos(cy - (y0 + j));
            for (int x = 0; x < W; x++) {
                for (int c = 0; c < 3; c++) {
                    double v = 0;
                    for (int j = 0; j < 6; j++) {
                        int yy = Math.max(0, Math.min(h - 1, y0 + j));
                        v += tmp[c][yy * W + x] * k[j];
                    }
                    out[c][y * W + x] = (float) (v / sum);
                }
            }
        }
        return out;
    }

    /** Box mean of one channel with radius r (integral image). */
    static float[] boxMean(float[] ch, int w, int h, int r) {
        double[] ii = new double[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            double row = 0;
            for (int x = 0; x < w; x++) {
                row += ch[y * w + x];
                ii[(y + 1) * (w + 1) + x + 1] = ii[y * (w + 1) + x + 1] + row;
            }
        }
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - r), y1 = Math.min(h, y + r + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - r), x1 = Math.min(w, x + r + 1);
                double s = ii[y1 * (w + 1) + x1] - ii[y0 * (w + 1) + x1] - ii[y1 * (w + 1) + x0] + ii[y0 * (w + 1) + x0];
                out[y * w + x] = (float) (s / ((y1 - y0) * (x1 - x0)));
            }
        }
        return out;
    }

    static float[] luma(float[][] c) {
        float[] l = new float[c[0].length];
        for (int i = 0; i < l.length; i++) l[i] = 0.299f * c[0][i] + 0.587f * c[1][i] + 0.114f * c[2][i];
        return l;
    }

    /** Smooths compression blotches only where the image is dark and locally flat. */
    static float[][] deblock(float[][] c, int w, int h) {
        float[] l = luma(c);
        float[] l2 = new float[l.length];
        for (int i = 0; i < l.length; i++) l2[i] = l[i] * l[i];
        int r = 4;
        float[] m = boxMean(l, w, h, r), m2 = boxMean(l2, w, h, r);
        float[][] mean = {boxMean(c[0], w, h, r), boxMean(c[1], w, h, r), boxMean(c[2], w, h, r)};
        float[][] out = new float[3][l.length];
        for (int i = 0; i < l.length; i++) {
            float var = Math.max(0, m2[i] - m[i] * m[i]);
            float flat = Math.max(0, 1 - var / 60f);                 // 1 on flat sky, 0 on detail
            float dark = Math.max(0, Math.min(1, (110 - l[i]) / 60f)); // only darker areas
            float wgt = 0.75f * flat * dark;
            for (int k = 0; k < 3; k++) out[k][i] = c[k][i] * (1 - wgt) + mean[k][i] * wgt;
        }
        return out;
    }

    /** Unsharp mask on detailed areas only, so flat sky noise isn't amplified. */
    static float[][] sharpen(float[][] c, int w, int h) {
        float[] l = luma(c);
        float[] l2 = new float[l.length];
        for (int i = 0; i < l.length; i++) l2[i] = l[i] * l[i];
        float[] m = boxMean(l, w, h, 2), m2 = boxMean(l2, w, h, 2);
        float[][] out = new float[3][l.length];
        for (int k = 0; k < 3; k++) {
            float[] blur = boxMean(boxMean(c[k], w, h, 1), w, h, 1); // ~gaussian radius 1.4
            for (int i = 0; i < l.length; i++) {
                float var = Math.max(0, m2[i] - m[i] * m[i]);
                float detail = Math.min(1, var / 150f);
                out[k][i] = c[k][i] + 0.7f * detail * (c[k][i] - blur[i]);
            }
        }
        return out;
    }

    static BufferedImage toImage(float[][] c, int w, int h, float grain) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(7);
        for (int i = 0; i < w * h; i++) {
            float n = (float) rnd.nextGaussian() * grain; // same grain on all channels: no colour noise
            int r = clamp(c[0][i] + n), g = clamp(c[1][i] + n), bl = clamp(c[2][i] + n);
            b.setRGB(i % w, i / w, (r << 16) | (g << 8) | bl);
        }
        return b;
    }

    static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
