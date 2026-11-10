package com.reiko.signaltower.radio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Morse code (v0.62.0): the table, turning text into key timings (a telegraph keying a message),
 * and turning key timings back into text (a Morse Key, keyed by hand or redstone).
 *
 * <p>Decoding adapts to the speed: a press is a dash if it's at least twice the length of the
 * shortest press, and a dot is how long the others are on average. If every press is about the same
 * length: keyed by hand, they're dots if under {@link #HAND_DASH_TICKS} and dashes otherwise (a
 * click is never shorter than a dot, however fast the clicking, so the gaps don't tell); keyed by
 * redstone or a telegraph, they're dashes if they're at least twice the shortest gap (the gap
 * inside a letter is a dot long), dots if no more than half of it, else by that same rule; so a
 * message of only one-element letters keyed fast (T T) can come out as dots (E E). Between presses,
 * a gap of 2 dots or more ends a letter, and 5 or more a word. A code that isn't in the table comes
 * out as {@link #UNKNOWN}.
 */
public final class Morse {
    /** A key timing: down ({@code on}) or up, for {@code ticks}. */
    public record Element(boolean on, int ticks) {
    }

    /** One dot, in ticks, when a telegraph keys a message (12 words a minute). */
    public static final int TELEGRAPH_UNIT = 2;
    /** With nothing shorter to compare it with, a press this long or longer is a dash (hand keying). */
    public static final int HAND_DASH_TICKS = 13;
    /** What a code that isn't in the table decodes to. */
    public static final char UNKNOWN = '*';

    private static final Map<Character, String> CODES = new HashMap<>();
    private static final Map<String, Character> LETTERS = new HashMap<>();

    static {
        String[][] table = {
                {"A", ".-"}, {"B", "-..."}, {"C", "-.-."}, {"D", "-.."}, {"E", "."}, {"F", "..-."}, {"G", "--."},
                {"H", "...."}, {"I", ".."}, {"J", ".---"}, {"K", "-.-"}, {"L", ".-.."}, {"M", "--"}, {"N", "-."},
                {"O", "---"}, {"P", ".--."}, {"Q", "--.-"}, {"R", ".-."}, {"S", "..."}, {"T", "-"}, {"U", "..-"},
                {"V", "...-"}, {"W", ".--"}, {"X", "-..-"}, {"Y", "-.--"}, {"Z", "--.."},
                {"0", "-----"}, {"1", ".----"}, {"2", "..---"}, {"3", "...--"}, {"4", "....-"}, {"5", "....."},
                {"6", "-...."}, {"7", "--..."}, {"8", "---.."}, {"9", "----."},
                {".", ".-.-.-"}, {",", "--..--"}, {"?", "..--.."}, {"'", ".----."}, {"!", "-.-.--"}, {"/", "-..-."},
                {"(", "-.--."}, {")", "-.--.-"}, {"&", ".-..."}, {":", "---..."}, {";", "-.-.-."}, {"=", "-...-"},
                {"+", ".-.-."}, {"-", "-....-"}, {"\"", ".-..-."}, {"@", ".--.-."},
        };
        for (String[] row : table) {
            CODES.put(row[0].charAt(0), row[1]);
            LETTERS.put(row[1], row[0].charAt(0));
        }
    }

    private Morse() {
    }

    /** The code for {@code c} (".-" for A), or null if it has none. Letters in either case. */
    public static String codeOf(char c) {
        return CODES.get(Character.toUpperCase(c));
    }

    /** The character for {@code code}, or {@link #UNKNOWN}. */
    public static char letterOf(String code) {
        Character c = LETTERS.get(code);
        return c == null ? UNKNOWN : c;
    }

    /** {@code text} as it goes out in Morse: upper case, runs of spaces as one, characters with no code left out. */
    public static String sendable(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c)) {
                if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') out.append(' ');
            } else if (CODES.containsKey(c)) {
                out.append(c);
            }
        }
        return out.toString().strip();
    }

    /** ".-- .... ..-" for "WHO" (letters by a space, words by " / "). */
    public static String codes(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : sendable(text).toCharArray()) {
            if (c == ' ') {
                out.append("/ ");
            } else {
                out.append(CODES.get(c)).append(' ');
            }
        }
        return out.toString().strip();
    }

    /**
     * The key timings for {@code text} at {@code unit} ticks a dot: a dash is 3, the gap inside a
     * letter 1, between letters 3, between words 7. Starts and ends with the key down.
     */
    public static List<Element> encode(String text, int unit) {
        List<Element> out = new ArrayList<>();
        String clean = sendable(text);
        boolean wordGap = false;
        for (char c : clean.toCharArray()) {
            if (c == ' ') {
                wordGap = true;
                continue;
            }
            if (!out.isEmpty()) {
                out.add(new Element(false, (wordGap ? 7 : 3) * unit));
            }
            wordGap = false;
            String code = CODES.get(c);
            for (int i = 0; i < code.length(); i++) {
                if (i > 0) out.add(new Element(false, unit));
                out.add(new Element(true, (code.charAt(i) == '-' ? 3 : 1) * unit));
            }
        }
        return out;
    }

    /** How many ticks {@code elements} take. */
    public static int length(List<Element> elements) {
        int total = 0;
        for (Element element : elements) total += element.ticks();
        return total;
    }

    /** How long a dot is in {@code presses} (ticks each), by the rule in the class comment (no gaps to go by). */
    public static double dotLength(List<Integer> presses) {
        return dotLength(presses, List.of(), false);
    }

    /** How long a dot is in {@code presses}, with the {@code gaps} between them to go by unless keyed by {@code hand}. */
    public static double dotLength(List<Integer> presses, List<Integer> gaps, boolean hand) {
        return timing(presses, gaps, hand)[0];
    }

    /**
     * How long a dot is, and how long a press has to be to be a dash: if some presses are at least
     * twice as long as the shortest, a dash is anything that long (however much the dashes vary)
     * and a dot is the mean of the rest; otherwise all are dots or all are dashes, by the rule in
     * the class comment.
     */
    private static double[] timing(List<Integer> presses, List<Integer> gaps, boolean hand) {
        if (presses.isEmpty()) {
            return new double[] {HAND_DASH_TICKS / 2.0, HAND_DASH_TICKS};
        }
        int min = Integer.MAX_VALUE, max = 0;
        for (int p : presses) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        if (max >= 2 * min) {
            double sum = 0;
            int n = 0;
            for (int p : presses) {
                if (p < 2 * min) {
                    sum += p;
                    n++;
                }
            }
            return new double[] {sum / n, 2 * min};
        }
        double mean = 0;
        for (int p : presses) mean += p;
        mean /= presses.size();
        int minGap = Integer.MAX_VALUE;
        if (!hand) {
            for (int g : gaps) minGap = Math.min(minGap, g);
        }
        boolean dashes;
        if (minGap != Integer.MAX_VALUE && mean >= 2 * minGap) {
            dashes = true; // each longer than twice a gap inside a letter
        } else if (minGap != Integer.MAX_VALUE && mean * 2 <= minGap) {
            dashes = false; // well under every gap
        } else {
            dashes = mean >= HAND_DASH_TICKS;
        }
        return dashes ? new double[] {mean / 3.0, 0} : new double[] {mean, Integer.MAX_VALUE};
    }

    /**
     * What was keyed: {@code presses} (ticks down each) and the {@code gaps} after all but the
     * last (ticks up). The letter being keyed is included only if {@code finished}; otherwise it's
     * left for {@link #pendingCode}.
     */
    public static String decode(List<Integer> presses, List<Integer> gaps, boolean finished) {
        return decode(presses, gaps, finished, false);
    }

    /** {@link #decode(List, List, boolean)}, keyed by {@code hand} (clicks) or not (redstone, a telegraph). */
    public static String decode(List<Integer> presses, List<Integer> gaps, boolean finished, boolean hand) {
        double[] timing = timing(presses, gaps, hand);
        double dot = timing[0];
        StringBuilder text = new StringBuilder();
        StringBuilder letter = new StringBuilder();
        for (int i = 0; i < presses.size(); i++) {
            letter.append(presses.get(i) >= timing[1] ? '-' : '.');
            if (i < gaps.size()) {
                int gap = gaps.get(i);
                if (gap >= 2 * dot) {
                    text.append(letterOf(letter.toString()));
                    letter.setLength(0);
                    if (gap >= 5 * dot) text.append(' ');
                }
            }
        }
        if (finished && !letter.isEmpty()) {
            text.append(letterOf(letter.toString()));
        }
        return text.toString().strip();
    }

    /** The dots and dashes of the letter being keyed (after the last letter gap), e.g. ".-". */
    public static String pendingCode(List<Integer> presses, List<Integer> gaps) {
        return pendingCode(presses, gaps, false);
    }

    /** {@link #pendingCode(List, List)}, keyed by {@code hand} or not. */
    public static String pendingCode(List<Integer> presses, List<Integer> gaps, boolean hand) {
        double[] timing = timing(presses, gaps, hand);
        double dot = timing[0];
        StringBuilder letter = new StringBuilder();
        for (int i = 0; i < presses.size(); i++) {
            letter.append(presses.get(i) >= timing[1] ? '-' : '.');
            if (i < gaps.size() && gaps.get(i) >= 2 * dot) {
                letter.setLength(0);
            }
        }
        return letter.toString();
    }
}
