package com.fm1.midibridge;

/**
 * Names the chord made by a set of held keys, e.g. C4 E4 G4 → "C major",
 * E3 G3 C4 → "C major / E", A3 C4 E4 G4 → "Am7". Works on pitch classes, so
 * any voicing or octave doubling names the same; the lowest key decides the
 * slash bass. Returns null for anything else, and the screen shows the
 * single note as usual.
 */
final class Chord {

    private Chord() {
    }

    // Intervals above the root (semitones), and the suffix printed after the root.
    // Order matters only for ties, which these exact pitch-class sets don't have
    // except the symmetrical ones (aug, dim7), where the bass picks the root.
    private static final int[][] SHAPES = {
            {0, 4, 7}, {0, 3, 7}, {0, 3, 6}, {0, 4, 8}, {0, 2, 7}, {0, 5, 7},
            {0, 4, 7, 10}, {0, 4, 7, 11}, {0, 3, 7, 10}, {0, 3, 6, 10}, {0, 3, 6, 9},
            {0, 3, 7, 11}, {0, 4, 7, 9}, {0, 3, 7, 9}, {0, 5, 7, 10}, {0, 2, 4, 7}, {0, 2, 3, 7},
            {0, 2, 4, 7, 10}, {0, 2, 4, 7, 11}, {0, 2, 3, 7, 10},
            {0, 4, 10}, {0, 4, 11}, {0, 3, 10},  // sevenths with the fifth left out
    };
    private static final String[] SUFFIX = {
            " major", " minor", "dim", "aug", "sus2", "sus4",
            "7", "maj7", "m7", "m7b5", "dim7",
            "m(maj7)", "6", "m6", "7sus4", "add9", "m(add9)",
            "9", "maj9", "m9",
            "7", "maj7", "m7",
    };

    /**
     * Chord name for these MIDI notes, or null if they aren't a chord it knows.
     * Needs 3+ different notes: two keys down is usually just legato playing
     * (the next key pressed a moment before the last one is let go).
     */
    static String name(int[] notes) {
        if (notes.length < 3) return null;
        int bass = lowest(notes) % 12;
        int mask = 0;
        for (int n : notes) mask |= 1 << (n % 12);
        if (Integer.bitCount(mask) < 3) return null;

        String best = null;
        for (int root = 0; root < 12; root++) {
            if ((mask & (1 << root)) == 0) continue;
            for (int s = 0; s < SHAPES.length; s++) {
                if (shapeMask(SHAPES[s], root) != mask) continue;
                String name = Bridge.pitchClass(root) + SUFFIX[s];
                if (root == bass) return name;          // root position wins outright
                if (best == null) best = name + " / " + Bridge.pitchClass(bass);
            }
        }
        return best;
    }

    private static int shapeMask(int[] shape, int root) {
        int m = 0;
        for (int i : shape) m |= 1 << ((root + i) % 12);
        return m;
    }

    private static int lowest(int[] notes) {
        int lo = notes[0];
        for (int n : notes) lo = Math.min(lo, n);
        return lo;
    }
}
