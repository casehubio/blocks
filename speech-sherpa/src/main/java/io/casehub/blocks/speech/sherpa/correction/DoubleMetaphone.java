package io.casehub.blocks.speech.sherpa.correction;

public final class DoubleMetaphone {

    private static final int MAX_LENGTH = 4;

    public String[] compute(String input) {
        if (input == null || input.isEmpty()) {
            return new String[]{"", ""};
        }

        String word = input.toUpperCase();
        var primary = new StringBuilder();
        var alternate = new StringBuilder();
        int length = word.length();
        int last = length - 1;
        int current = 0;

        if (isSlavo(word)) {
            // simplified: no Slavic-specific rules
        }

        if (startsWith(word, "GN", "KN", "PN", "AE", "WR")) {
            current++;
        }

        if (word.charAt(0) == 'X') {
            primary.append('S');
            alternate.append('S');
            current++;
        }

        while (current < length && (primary.length() < MAX_LENGTH || alternate.length() < MAX_LENGTH)) {
            char c = word.charAt(current);
            switch (c) {
                case 'A', 'E', 'I', 'O', 'U' -> {
                    if (current == 0) {
                        primary.append('A');
                        alternate.append('A');
                    }
                    current++;
                }
                case 'B' -> {
                    primary.append('P');
                    alternate.append('P');
                    current += (current + 1 < length && word.charAt(current + 1) == 'B') ? 2 : 1;
                }
                case 'C' -> current = handleC(word, current, length, primary, alternate);
                case 'D' -> current = handleD(word, current, length, primary, alternate);
                case 'F' -> {
                    primary.append('F');
                    alternate.append('F');
                    current += (current + 1 < length && word.charAt(current + 1) == 'F') ? 2 : 1;
                }
                case 'G' -> current = handleG(word, current, length, last, primary, alternate);
                case 'H' -> {
                    if (current == 0 || !isVowel(word, current - 1)) {
                        if (current + 1 < length && isVowel(word, current + 1)) {
                            primary.append('H');
                            alternate.append('H');
                        }
                    }
                    current++;
                }
                case 'J' -> {
                    primary.append('J');
                    alternate.append('J');
                    current += (current + 1 < length && word.charAt(current + 1) == 'J') ? 2 : 1;
                }
                case 'K' -> {
                    primary.append('K');
                    alternate.append('K');
                    current += (current + 1 < length && word.charAt(current + 1) == 'K') ? 2 : 1;
                }
                case 'L' -> {
                    primary.append('L');
                    alternate.append('L');
                    current += (current + 1 < length && word.charAt(current + 1) == 'L') ? 2 : 1;
                }
                case 'M' -> {
                    primary.append('M');
                    alternate.append('M');
                    current += (current + 1 < length && word.charAt(current + 1) == 'M') ? 2 : 1;
                }
                case 'N' -> {
                    primary.append('N');
                    alternate.append('N');
                    current += (current + 1 < length && word.charAt(current + 1) == 'N') ? 2 : 1;
                }
                case 'P' -> {
                    if (current + 1 < length && word.charAt(current + 1) == 'H') {
                        primary.append('F');
                        alternate.append('F');
                        current += 2;
                    } else {
                        primary.append('P');
                        alternate.append('P');
                        current += (current + 1 < length && word.charAt(current + 1) == 'P') ? 2 : 1;
                    }
                }
                case 'Q' -> {
                    primary.append('K');
                    alternate.append('K');
                    current += (current + 1 < length && word.charAt(current + 1) == 'Q') ? 2 : 1;
                }
                case 'R' -> {
                    primary.append('R');
                    alternate.append('R');
                    current += (current + 1 < length && word.charAt(current + 1) == 'R') ? 2 : 1;
                }
                case 'S' -> current = handleS(word, current, length, primary, alternate);
                case 'T' -> current = handleT(word, current, length, primary, alternate);
                case 'V' -> {
                    primary.append('F');
                    alternate.append('F');
                    current += (current + 1 < length && word.charAt(current + 1) == 'V') ? 2 : 1;
                }
                case 'W' -> {
                    if (current + 1 < length && isVowel(word, current + 1)) {
                        primary.append('A');
                        alternate.append('A');
                    }
                    current++;
                }
                case 'X' -> {
                    primary.append("KS");
                    alternate.append("KS");
                    current += (current + 1 < length && word.charAt(current + 1) == 'X') ? 2 : 1;
                }
                case 'Z' -> {
                    primary.append('S');
                    alternate.append('S');
                    current += (current + 1 < length && word.charAt(current + 1) == 'Z') ? 2 : 1;
                }
                default -> current++;
            }
        }

        String p = primary.length() > MAX_LENGTH ? primary.substring(0, MAX_LENGTH) : primary.toString();
        String a = alternate.length() > MAX_LENGTH ? alternate.substring(0, MAX_LENGTH) : alternate.toString();
        return new String[]{p, a.isEmpty() ? p : a};
    }

    private int handleC(String word, int current, int length, StringBuilder primary, StringBuilder alternate) {
        if (current + 1 < length && regionMatch(word, current, "CH")) {
            primary.append('X');
            alternate.append('X');
            return current + 2;
        }
        if (current + 1 < length && regionMatch(word, current, "CK")) {
            primary.append('K');
            alternate.append('K');
            return current + 2;
        }
        if (current + 1 < length && (word.charAt(current + 1) == 'I' || word.charAt(current + 1) == 'E' || word.charAt(current + 1) == 'Y')) {
            primary.append('S');
            alternate.append('S');
            return current + 2;
        }
        primary.append('K');
        alternate.append('K');
        return current + (current + 1 < length && word.charAt(current + 1) == 'C' && word.charAt(current + 1) != 'E' ? 2 : 1);
    }

    private int handleD(String word, int current, int length, StringBuilder primary, StringBuilder alternate) {
        if (current + 1 < length && word.charAt(current + 1) == 'G') {
            if (current + 2 < length && (word.charAt(current + 2) == 'I' || word.charAt(current + 2) == 'E' || word.charAt(current + 2) == 'Y')) {
                primary.append('J');
                alternate.append('J');
                return current + 3;
            }
            primary.append("TK");
            alternate.append("TK");
            return current + 2;
        }
        primary.append('T');
        alternate.append('T');
        return current + (current + 1 < length && word.charAt(current + 1) == 'D' ? 2 : 1);
    }

    private int handleG(String word, int current, int length, int last, StringBuilder primary, StringBuilder alternate) {
        if (current + 1 < length && word.charAt(current + 1) == 'H') {
            if (current > 0 && !isVowel(word, current - 1)) {
                primary.append('K');
                alternate.append('K');
                return current + 2;
            }
            if (current == 0) {
                primary.append('K');
                alternate.append('K');
                return current + 2;
            }
            return current + 2;
        }
        if (current + 1 < length && word.charAt(current + 1) == 'N') {
            return current + (current + 2 < length && word.charAt(current + 2) == 'N' ? 3 : 2);
        }
        if (current + 1 < length && (word.charAt(current + 1) == 'I' || word.charAt(current + 1) == 'E' || word.charAt(current + 1) == 'Y')) {
            primary.append('J');
            alternate.append('K');
            return current + 2;
        }
        if (current + 1 < length && word.charAt(current + 1) == 'G') {
            primary.append('K');
            alternate.append('K');
            return current + 2;
        }
        primary.append('K');
        alternate.append('K');
        return current + 1;
    }

    private int handleS(String word, int current, int length, StringBuilder primary, StringBuilder alternate) {
        if (regionMatch(word, current, "SH")) {
            primary.append('X');
            alternate.append('X');
            return current + 2;
        }
        if (current + 1 < length && (word.charAt(current + 1) == 'I' || word.charAt(current + 1) == 'E' || word.charAt(current + 1) == 'Y')
                && current + 2 < length && (word.charAt(current + 2) == 'O' || word.charAt(current + 2) == 'A')) {
            // SION, SIAN
            primary.append('X');
            alternate.append('S');
            return current + 3;
        }
        if (regionMatch(word, current, "SC")) {
            if (current + 2 < length && (word.charAt(current + 2) == 'I' || word.charAt(current + 2) == 'E' || word.charAt(current + 2) == 'Y')) {
                primary.append('S');
                alternate.append('S');
                return current + 3;
            }
            primary.append("SK");
            alternate.append("SK");
            return current + 3;
        }
        primary.append('S');
        alternate.append('S');
        return current + (current + 1 < length && word.charAt(current + 1) == 'S' ? 2 : 1);
    }

    private int handleT(String word, int current, int length, StringBuilder primary, StringBuilder alternate) {
        if (regionMatch(word, current, "TH")) {
            primary.append('0');
            alternate.append('T');
            return current + 2;
        }
        if (regionMatch(word, current, "TION") || regionMatch(word, current, "TIA")) {
            primary.append('X');
            alternate.append('X');
            return current + 3;
        }
        if (regionMatch(word, current, "TCH")) {
            return current + 3;
        }
        primary.append('T');
        alternate.append('T');
        return current + (current + 1 < length && word.charAt(current + 1) == 'T' ? 2 : 1);
    }

    private static boolean isVowel(String word, int index) {
        if (index < 0 || index >= word.length()) return false;
        char c = word.charAt(index);
        return c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U' || c == 'Y';
    }

    private static boolean regionMatch(String word, int start, String region) {
        return start + region.length() <= word.length()
                && word.substring(start, start + region.length()).equals(region);
    }

    private static boolean startsWith(String word, String... prefixes) {
        for (String prefix : prefixes) {
            if (word.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean isSlavo(String word) {
        return false;
    }
}
