package forgeweb.shim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** FibHash.place must equal libGDX's slot formula for every key and every table size. */
class FibHashTest {
    private static int libgdx(int item, int shift) {
        return (int) (item * 0x9E3779B97F4A7C15L >>> shift);
    }

    @Test
    void matchesLibGdxFormula() {
        Random r = new Random(7);
        int[] edge = {0, 1, -1, 2, 65535, 65536, 0x7FFFFFFF, 0x80000000, 0x7F4A7C15, 0x9E3779B9, 0xFFFF0000, 0x0000FFFF};
        for (int shift = 33; shift <= 63; shift++) {
            for (int k : edge) assertEquals(libgdx(k, shift), FibHash.place(k, shift), "key " + k + " shift " + shift);
            for (int i = 0; i < 20000; i++) {
                int k = r.nextInt();
                assertEquals(libgdx(k, shift), FibHash.place(k, shift), "key " + k + " shift " + shift);
            }
        }
    }

    @Test
    void matchesForCharsAndSmallInts() {
        for (int shift = 33; shift <= 63; shift++)
            for (int k = -70000; k < 70000; k++) assertEquals(libgdx(k, shift), FibHash.place(k, shift));
    }
}
