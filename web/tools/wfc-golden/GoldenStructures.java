import com.badlogic.gdx.graphics.Color;
import forge.adventure.data.BiomeStructureData;
import forge.adventure.world.BiomeStructure;
import forge.adventure.world.ColorMap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * BiomeStructure.initialize over the Shandalar world's structure definitions (structures.py TSV)
 * for a few seeds; prints a hash of every structure's object ids, collision and image.
 */
public class GoldenStructures {
    static ColorMap load(String path) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        ColorMap m = new ColorMap(img.getWidth(), img.getHeight());
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                m.setColor(x, y, new Color((argb << 8) | (argb >>> 24)));
            }
        return m;
    }

    public static void main(String[] args) throws Exception {
        long hash = 1125899906842597L;
        long start = System.nanoTime();
        for (long seed : new long[] {1, 42, -7336514189187458432L}) {
            for (String line : Files.readAllLines(Paths.get(args[0]))) {
                String[] f = line.split("\t");
                BiomeStructureData d = new BiomeStructureData();
                d.N = Integer.parseInt(f[2]);
                d.symmetry = Integer.parseInt(f[3]);
                d.periodicInput = Boolean.parseBoolean(f[4]);
                d.periodicOutput = Boolean.parseBoolean(f[5]);
                d.ground = Integer.parseInt(f[6]);
                d.width = Float.parseFloat(f[7]);
                d.height = Float.parseFloat(f[8]);
                String[] colors = f[11].equals("-") ? new String[0] : f[11].split(",");
                String[] collisions = f[12].equals("-") ? new String[0] : f[12].split(",");
                d.mappingInfo = new BiomeStructureData.BiomeStructureDataMapping[colors.length];
                for (int i = 0; i < colors.length; i++) {
                    d.mappingInfo[i] = new BiomeStructureData.BiomeStructureDataMapping();
                    d.mappingInfo[i].color = colors[i];
                    d.mappingInfo[i].collision = Boolean.parseBoolean(collisions[i]);
                }
                int bw = Integer.parseInt(f[9]), bh = Integer.parseInt(f[10]);
                BiomeStructure s = new BiomeStructure(d, seed, bw, bh);
                s.initialize(load(f[0]), f[1].equals("-") ? null : load(f[1]));
                int tw = (int) (d.width * bw), th = (int) (d.height * bh);
                for (int x = 0; x < tw; x++)
                    for (int y = 0; y < th; y++) {
                        hash = hash * 31 + s.objectID(x, y);
                        hash = hash * 31 + (s.collision(x, y) ? 1 : 0);
                        hash = hash * 31 + (s.image == null ? 0 : Color.rgba8888(s.image.getColor(Math.min(x, s.image.getWidth() - 1), Math.min(y, s.image.getHeight() - 1))));
                    }
            }
        }
        System.out.printf("structures hash %016x  %.0f ms%n", hash, (System.nanoTime() - start) / 1e6);
    }
}
