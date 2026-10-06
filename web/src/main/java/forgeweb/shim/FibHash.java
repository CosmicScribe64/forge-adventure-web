package forgeweb.shim;

/**
 * Fibonacci hashing for the shadowed libGDX hash maps (IntMap, IntFloatMap, ObjectIntMap), without a 64-bit
 * multiplication. libGDX computes a slot as {@code (int)(item * 0x9E3779B97F4A7C15L >>> shift)}. TeaVM 0.15 turns
 * every {@code long} operation into JavaScript BigInt arithmetic, and each BigInt result is a heap allocation, so
 * every lookup in a TextraTypist font (one per glyph and frame) cost four of them: at the title screen these maps
 * were a third of the roughly 9000 BigInt operations a frame (wiki/analyses/webkit-memory.md). Only the high word
 * of the 64-bit product is needed (shift is 34 to 63), and it is computed here exactly with 32-bit arithmetic, so
 * the slots, and with them the iteration order of every map, are the same as libGDX's. FibHashTest checks it.
 * Upstream (libGDX) could do the same for targets without fast longs.
 */
public final class FibHash {
    private FibHash() {
    }

    /** The same value as {@code (int)(item * 0x9E3779B97F4A7C15L >>> shift)}, for shift from 33 to 63. */
    public static int place(int item, int shift) {
        // Low word of the constant is 0x7F4A7C15, the high word 0x9E3779B9; item is sign-extended to 64 bits.
        int a0 = item & 0xFFFF, a1 = item >>> 16;
        int t = a0 * 0x7C15;
        int mid = a1 * 0x7C15 + (t >>> 16);
        int mid2 = a0 * 0x7F4A + (mid & 0xFFFF);
        // High word of the unsigned 32x32 product item * 0x7F4A7C15, then the two cross terms (only their low words matter).
        int high = a1 * 0x7F4A + (mid >>> 16) + (mid2 >>> 16) + item * 0x9E3779B9 + (item >> 31) * 0x7F4A7C15;
        return high >>> (shift - 32);
    }
}
