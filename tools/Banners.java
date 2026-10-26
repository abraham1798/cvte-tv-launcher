import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the 16:9 dock banners (640x360 PNG) for the launcher.
 *   java tools/Banners.java <app-icon.png> <symbol.png> <name> <outfit-regular.ttf> <out-dir>
 * Writes app-logo.png (the bright logo lifted out of a square app icon), symbol-name.png
 * (a black-on-transparent symbol, recoloured white, next to <name>) and livetv.png.
 * Use only artwork you are allowed to use.
 */
public class Banners {
    static final int W = 640, H = 360;

    public static void main(String[] a) throws Exception {
        File out = new File(a[4]);
        out.mkdirs();
        ImageIO.write(fromIcon(ImageIO.read(new File(a[0]))), "png", new File(out, "app-logo.png"));
        ImageIO.write(symbolAndName(ImageIO.read(new File(a[1])), a[2]), "png", new File(out, "symbol-name.png"));
        Font outfit = Font.createFont(Font.TRUETYPE_FONT, new File(a[3]));
        ImageIO.write(liveTv(outfit), "png", new File(out, "livetv.png"));
    }

    static Graphics2D g(BufferedImage b) {
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** An app's own logo, lifted out of its square icon onto a transparent wide banner. */
    static BufferedImage fromIcon(BufferedImage icon) {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(b);
        // Logo band of the icon (tune the fractions for your icon).
        int s = icon.getWidth();
        int cx = Math.round(s * 0.06f), cy = Math.round(s * 0.30f), cw = Math.round(s * 0.89f), ch = Math.round(s * 0.40f);
        BufferedImage crop = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int p = icon.getRGB(cx + x, cy + y);
                // keep only the bright logo strokes: alpha from luminance
                int r = (p >> 16) & 255, gg = (p >> 8) & 255, bl = p & 255;
                float lum = (0.3f * r + 0.59f * gg + 0.11f * bl) / 255f;
                float al = Math.max(0f, Math.min(1f, (lum - 0.28f) / 0.35f));
                crop.setRGB(x, y, (Math.round(al * 255) << 24) | (p & 0xFFFFFF));
            }
        }
        float scale = (W * 0.80f) / cw;
        int dw = Math.round(cw * scale), dh = Math.round(ch * scale);
        g.drawImage(crop, (W - dw) / 2, (H - dh) / 2, dw, dh, null);
        g.dispose();
        return b;
    }

    /** A symbol and a name, both white, on a transparent banner. */
    static BufferedImage symbolAndName(BufferedImage sym, String name) {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(b);
        Color blue = new Color(0xF2F4F8); // logo colour
        // tint: keep the symbol's alpha, colour it white; skip any mark in the bottom-right corner
        int sw = sym.getWidth(), sh = sym.getHeight();
        BufferedImage t = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                if (x > sw * 0.86 && y > sh * 0.80) continue;
                int al = (sym.getRGB(x, y) >>> 24);
                int lum = sym.getRGB(x, y) & 255;
                al = al * (255 - lum) / 255;
                t.setRGB(x, y, (al << 24) | (blue.getRGB() & 0xFFFFFF));
            }
        }
        int symH = 150, symW = Math.round(sw * (symH / (float) sh));
        Font f;
        try {
            f = Font.createFont(Font.TRUETYPE_FONT, new File("C:/Windows/Fonts/ARIALNB.TTF")).deriveFont(92f);
        } catch (Exception e) {
            f = new Font("SansSerif", Font.BOLD, 84);
        }
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int gap = 22, total = symW + gap + fm.stringWidth(name);
        int x0 = (W - total) / 2, symY = (H - symH) / 2;
        g.drawImage(t, x0, symY, symW, symH, null);
        g.setColor(blue);
        int baseline = H / 2 + (fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(name, x0 + symW + gap, baseline);
        g.dispose();
        return b;
    }

    /** A television with a broadcast signal, and "TV en vivo", white on frosted grey. */
    static BufferedImage liveTv(Font outfit) {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(b);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int tx = 255, ty = 92, tw = 118, th = 84;
        g.draw(new RoundRectangle2D.Float(tx, ty, tw, th, 18, 18));
        g.drawLine(tx + tw / 2 - 26, ty + th + 22, tx + tw / 2 + 26, ty + th + 22);
        // signal arcs from the top-right corner of the screen
        float ax = tx + tw + 8, ay = ty - 6;
        for (int i = 1; i <= 2; i++) {
            float r = 18f * i + 6;
            g.draw(new Arc2D.Float(ax - r, ay - r, 2 * r, 2 * r, 0, 90, Arc2D.OPEN));
        }
        g.fill(new Ellipse2D.Float(ax - 6, ay - 6, 12, 12));
        g.setFont(outfit.deriveFont(52f));
        FontMetrics fm = g.getFontMetrics();
        String s = "TV en vivo";
        g.drawString(s, (W - fm.stringWidth(s)) / 2, 290);
        g.dispose();
        return b;
    }
}
