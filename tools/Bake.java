// Darkens a wallpaper top and bottom (the launcher no longer draws a scrim) and saves JPEG.
//   java tools/Bake.java launcher/wallpaper-master.png launcher/app/src/main/assets/wallpaper.jpg
import javax.imageio.*; import javax.imageio.stream.*; import java.awt.image.BufferedImage; import java.io.File;
/** Multiplies the launcher's top/bottom scrim into the wallpaper, saves JPEG. */
public class Bake { public static void main(String[] a) throws Exception {
  BufferedImage s = ImageIO.read(new File(a[0])); int w = s.getWidth(), h = s.getHeight();
  BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
  for (int y = 0; y < h; y++) { double v = (double) y / (h - 1); double al = v < 0.5 ? 0.45 + (0.15 - 0.45) * v * 2 : 0.15 + (0.85 - 0.15) * (v - 0.5) * 2; double k = 1 - al;
    for (int x = 0; x < w; x++) { int p = s.getRGB(x, y); int r = (int) Math.round(((p >> 16) & 255) * k), g = (int) Math.round(((p >> 8) & 255) * k), b = (int) Math.round((p & 255) * k); o.setRGB(x, y, (r << 16) | (g << 8) | b); } }
  ImageWriter wr = ImageIO.getImageWritersByFormatName("jpg").next(); ImageWriteParam p = wr.getDefaultWriteParam(); p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(0.93f);
  try (ImageOutputStream out = ImageIO.createImageOutputStream(new File(a[1]))) { wr.setOutput(out); wr.write(null, new IIOImage(o, null, null), p); } } }
