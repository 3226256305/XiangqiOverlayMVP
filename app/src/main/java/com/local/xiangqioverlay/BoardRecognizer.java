package com.local.xiangqioverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;

import java.io.IOException;

public class BoardRecognizer {
    private final Context context;
    private TemplateModel model;
    private long loadedVersion = -1;

    public BoardRecognizer(Context context) { this.context = context.getApplicationContext(); }

    public RecognitionResult recognize(Bitmap frame) throws IOException {
        long version = AppPrefs.templateVersion(context);
        if (version <= 0) throw new IOException("尚未校准棋盘");
        if (model == null || loadedVersion != version) {
            model = TemplateModel.load(context);
            loadedVersion = version;
        }
        SharedPreferences p = AppPrefs.get(context);
        RectF board = new RectF(
                p.getFloat("board_l", 0) * frame.getWidth(),
                p.getFloat("board_t", 0) * frame.getHeight(),
                p.getFloat("board_r", 1) * frame.getWidth(),
                p.getFloat("board_b", 1) * frame.getHeight());
        boolean blackBottom = p.getBoolean("black_bottom", false);
        float dx = board.width() / 8f, dy = board.height() / 9f;
        float radius = Math.min(dx, dy) * 0.43f;
        char[][] visual = new char[10][9];
        float sum = 0f, min = 1f, marginSum = 0f;

        for (int vr = 0; vr < 10; vr++) {
            for (int vc = 0; vc < 9; vc++) {
                float cx = board.left + vc * dx;
                float cy = board.top + vr * dy;
                TemplateModel.Match m = model.classify(TemplateModel.extractFeature(frame, cx, cy, radius));
                visual[vr][vc] = m.piece;
                sum += m.score;
                min = Math.min(min, m.score);
                marginSum += m.margin;
            }
        }

        char[][] canonical = new char[10][9];
        for (int vr = 0; vr < 10; vr++) {
            for (int vc = 0; vc < 9; vc++) {
                int cr = blackBottom ? 9 - vr : vr;
                int cc = blackBottom ? 8 - vc : vc;
                canonical[cr][cc] = visual[vr][vc];
            }
        }
        float avg = sum / 90f;
        float avgMargin = marginSum / 90f;
        String boardFen = FenBuilder.boardToFen(canonical, "w").split(" ")[0];
        return new RecognitionResult(canonical, boardFen, avg, min, avgMargin, FenBuilder.plausible(canonical));
    }

    public static final class RecognitionResult {
        public final char[][] board;
        public final String boardFen;
        public final float avgScore, minScore, avgMargin;
        public final boolean plausible;
        public RecognitionResult(char[][] board, String boardFen, float avgScore, float minScore, float avgMargin, boolean plausible) {
            this.board = board; this.boardFen = boardFen; this.avgScore = avgScore; this.minScore = minScore;
            this.avgMargin = avgMargin; this.plausible = plausible;
        }
        public String fullFen(String side) { return boardFen + " " + side + " - - 0 1"; }
    }
}
