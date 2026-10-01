import forge.adventure.world.ColorMap;
import forge.adventure.world.OverlappingModel;
import com.badlogic.gdx.graphics.Color;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Runs Forge's WFC on real structure models for many seeds and prints a hash of all outputs. */
public class Golden {
    public static void main(String[] args) throws Exception {
        long hash = 1125899906842597L;
        long start = System.nanoTime();
        int ok = 0, runs = 0;
        for (String path : args) {
            BufferedImage img = ImageIO.read(new File(path));
            ColorMap src = new ColorMap(img.getWidth(), img.getHeight());
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    // libGDX Pixmap.getPixel is RGBA8888
                    src.setColor(x, y, new Color(((argb << 8) | (argb >>> 24))));
                }
            for (int n = 2; n <= 3; n++) {
                for (int size : new int[] {10, 7}) {
                    OverlappingModel m = new OverlappingModel(src, n, size, size, true, true, 8, 0);
                    for (int s = 0; s < 25; s++) {
                        runs++;
                        boolean r = m.run(1234 + s * 5355, 0);
                        hash = hash * 31 + (r ? 1 : 2);
                        if (!r) continue;
                        ok++;
                        ColorMap g = m.graphics();
                        for (int y = 0; y < size; y++)
                            for (int x = 0; x < size; x++) hash = hash * 31 + Color.rgba8888(g.getColor(x, y));
                    }
                }
            }
        }
        System.out.printf("hash %016x  runs %d ok %d  %.0f ms%n", hash, runs, ok, (System.nanoTime() - start) / 1e6);
    }
}
