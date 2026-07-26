package com.osgworld.djbooth.mixer;

/**
 * A track's musical key, and the arithmetic for mixing two of them together.
 *
 * <p>A key is a root note (0 = C, 1 = C#, … 11 = B) plus whether it is major or minor. Two tracks
 * in keys that clash sound wrong together however well their beats line up, which is why DJs pick
 * records by key as well as by tempo.
 *
 * <p>Keys are also written in <b>Camelot</b> notation — {@code 8A}, {@code 5B} and so on — which
 * exists precisely because it makes compatibility obvious: neighbouring numbers and the A/B pair at
 * the same number all mix cleanly. This class can read and write both.
 */
public record MusicKey(int root, boolean minor) {
    /** Note names for each root, sharps rather than flats. */
    private static final String[] NOTES =
            {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /**
     * Camelot wheel position for each root, minor then major.
     *
     * <p>The wheel is laid out by the circle of fifths, so adjacent positions share all but one
     * note. 8A is A minor and 8B is C major, which is why both indices start there.
     */
    private static final int[] CAMELOT_MINOR = {5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10};
    private static final int[] CAMELOT_MAJOR = {8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1};

    public MusicKey {
        root = Math.floorMod(root, 12);
    }

    /** Camelot position, 1..12. */
    public int camelotNumber() {
        return minor ? CAMELOT_MINOR[root] : CAMELOT_MAJOR[root];
    }

    /** Camelot notation, e.g. {@code 8A}. */
    public String camelot() {
        return camelotNumber() + (minor ? "A" : "B");
    }

    /** Musician's notation, e.g. {@code Am} or {@code C}. */
    @Override
    public String toString() {
        return NOTES[root] + (minor ? "m" : "");
    }

    /**
     * Read a key from whatever a metadata source hands back.
     *
     * <p>Accepts Camelot ({@code 8A}), note names ({@code Am}, {@code F#m}, {@code Db}), and the
     * numeric root/mode pair that pitch-class APIs use. Returns null when it can't tell.
     */
    public static MusicKey parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Camelot: digits then A (minor) or B (major).
        if (s.length() <= 3 && Character.isDigit(s.charAt(0))) {
            char side = Character.toUpperCase(s.charAt(s.length() - 1));
            if (side == 'A' || side == 'B') {
                try {
                    int n = Integer.parseInt(s.substring(0, s.length() - 1));
                    boolean minor = side == 'A';
                    int[] table = minor ? CAMELOT_MINOR : CAMELOT_MAJOR;
                    for (int root = 0; root < 12; root++) {
                        if (table[root] == n) {
                            return new MusicKey(root, minor);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        // Note name, optionally sharp or flat, optionally followed by a minor marker.
        int i = 0;
        int root = switch (Character.toUpperCase(s.charAt(i++))) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> -1;
        };
        if (root < 0) {
            return null;
        }
        if (i < s.length() && (s.charAt(i) == '#' || s.charAt(i) == '♯')) {
            root++;
            i++;
        } else if (i < s.length() && (s.charAt(i) == 'b' || s.charAt(i) == '♭')) {
            // Only a flat if something follows it or the note can't stand alone as "Bb".
            root--;
            i++;
        }
        // Whatever follows the note has to be a mode marker and nothing else, or this wasn't a key
        // at all — "B" is a key, "banana" is a fruit, and both start with a valid note name.
        String rest = s.substring(Math.min(i, s.length())).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (rest) {
            case "", "maj", "major" -> new MusicKey(root, false);
            case "m", "min", "minor" -> new MusicKey(root, true);
            default -> null;
        };
    }

    /** Build from the root/mode pair numeric APIs return, or null when the root is unknown. */
    public static MusicKey of(int root, boolean minor) {
        return root < 0 ? null : new MusicKey(root, minor);
    }

    /**
     * How many semitones this key must move to land on {@code target}, taking the shortest way
     * round: at most six up or six down, so KEY SYNC never shifts a track further than it has to.
     */
    public int semitonesTo(MusicKey target) {
        int diff = Math.floorMod(target.root - this.root, 12);
        return diff > 6 ? diff - 12 : diff;
    }

    /**
     * Whether two keys already mix without shifting anything.
     *
     * <p>The Camelot rule: same position, one step around the wheel, or the relative major/minor
     * at the same number.
     */
    public boolean mixesWith(MusicKey other) {
        int a = camelotNumber();
        int b = other.camelotNumber();
        if (a == b) {
            return true; // same number: either identical, or relative major/minor
        }
        int step = Math.floorMod(a - b, 12);
        return (step == 1 || step == 11) && minor == other.minor;
    }
}
