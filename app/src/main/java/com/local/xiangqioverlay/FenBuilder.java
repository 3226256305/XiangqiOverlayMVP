package com.local.xiangqioverlay;

public final class FenBuilder {
    private FenBuilder() {}

    public static String boardToFen(char[][] board, String side) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 10; r++) {
            int empty = 0;
            for (int c = 0; c < 9; c++) {
                char p = board[r][c];
                if (p == '.') empty++;
                else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(p);
                }
            }
            if (empty > 0) sb.append(empty);
            if (r != 9) sb.append('/');
        }
        sb.append(' ').append(side).append(" - - 0 1");
        return sb.toString();
    }

    public static boolean plausible(char[][] b) {
        int redKing = 0, blackKing = 0, redCount = 0, blackCount = 0;
        for (char[] row : b) for (char p : row) {
            if (p == 'K') redKing++;
            if (p == 'k') blackKing++;
            if (p >= 'A' && p <= 'Z') redCount++;
            if (p >= 'a' && p <= 'z') blackCount++;
        }
        return redKing == 1 && blackKing == 1 && redCount <= 16 && blackCount <= 16;
    }
}
