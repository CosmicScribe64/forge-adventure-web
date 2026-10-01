package forgeweb.compat;

import org.teavm.jso.JSBody;

/** Web-side hooks into Forge game code (see CallRedirector). */
public final class GameCompat {
    private GameCompat() {
    }

    /**
     * World.generateNew: a {@code ?seed=N} page parameter replaces a random seed (0), so tests and
     * measurements get the same world every run.
     */
    public static boolean generateNew(forge.adventure.world.World world, long seed) {
        if (seed == 0) {
            String param = seedParam();
            if (param != null) {
                try {
                    seed = Long.parseLong(param);
                    System.out.println("World seed from page: " + seed);
                } catch (NumberFormatException e) {
                    System.err.println("Ignoring bad seed parameter: " + param);
                }
            }
        }
        Progress.mark("world generation");
        Progress.show("", 0);
        boolean ok;
        try {
            ok = world.generateNew(seed);
        } finally {
            Progress.hide();
        }
        if (ok) {
            System.out.println("World hash: " + Long.toHexString(worldHash(world)));
        }
        return ok;
    }

    /**
     * A hash of the generated world (terrain, biomes, structures, collision, points of interest),
     * logged so changes to world generation can be checked to give the same world for a seed.
     */
    static long worldHash(forge.adventure.world.World world) {
        long h = 1125899906842597L;
        for (int x = 0; x < world.getWidthInTiles(); x++) {
            for (int y = 0; y < world.getHeightInTiles(); y++) {
                h = h * 31 + world.getTerrainIndex(x, y);
                h = h * 31 + world.getBiomeMapXY(x, y);
                h = h * 31 + (world.isStructure(x, y) ? 1 : 0);
                h = h * 31 + (world.isColliding(x, y) ? 1 : 0);
            }
        }
        for (forge.adventure.pointofintrest.PointOfInterest poi : world.getAllPointOfInterest()) {
            h = h * 31 + poi.getID().hashCode();
            h = h * 31 + Float.floatToIntBits(poi.getPosition().x);
            h = h * 31 + Float.floatToIntBits(poi.getPosition().y);
        }
        return h;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('seed');")
    private static native String seedParam();
}
