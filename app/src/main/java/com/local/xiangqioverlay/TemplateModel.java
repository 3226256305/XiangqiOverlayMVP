package com.local.xiangqioverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateModel {
    public static final int SIZE = 24;
    public static final char[] CLASSES = new char[]{'.','r','n','b','a','k','c','p','R','N','B','A','K','C','P'};
    private static final int MAGIC = 0x58495450; // XITP
    private final Map<Character, float[]> templates = new HashMap<>();

    public static File modelFile(Context c) { return new File(c.getFilesDir(), "templates_v1.bin"); }

    public static void calibrateAndSave(Context c, Bitmap screenshot, RectF board, boolean blackBottom) throws IOException {
        float dx = board.width() / 8f;
        float dy = board.height() / 9f;
        float radius = Math.min(dx, dy) * 0.43f;
        char[][] canonical = initialBoard();
        Map<Character, List<float[]>> samples = new HashMap<>();
        for (char ch : CLASSES) samples.put(ch, new ArrayList<>());

        for (int vr = 0; vr < 10; vr++) {
            for (int vc = 0; vc < 9; vc++) {
                int cr = blackBottom ? 9 - vr : vr;
                int cc = blackBottom ? 8 - vc : vc;
                char label = canonical[cr][cc];
                float cx = board.left + vc * dx;
                float cy = board.top + vr * dy;
                samples.get(label).add(extractFeature(screenshot, cx, cy, radius));
            }
        }

        TemplateModel model = new TemplateModel();
        for (char ch : CLASSES) {
            List<float[]> list = samples.get(ch);
            if (list == null || list.isEmpty()) throw new IOException("缺少模板类别 " + ch);
            model.templates.put(ch, mean(list));
        }
        model.save(c);
    }

    public static TemplateModel load(Context c) throws IOException {
        File f = modelFile(c);
        if (!f.exists()) throw new IOException("模板尚未校准");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC) throw new IOException("模板文件版本不兼容");
            int size = in.readInt();
            int count = in.readInt();
            if (size != SIZE || count != CLASSES.length) throw new IOException("模板尺寸不匹配");
            TemplateModel m = new TemplateModel();
            int len = SIZE * SIZE * 4;
            for (int i = 0; i < count; i++) {
                char ch = in.readChar();
                float[] feat = new float[len];
                for (int j = 0; j < len; j++) feat[j] = in.readFloat();
                m.templates.put(ch, feat);
            }
            return m;
        }
    }

    private void save(Context c) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(modelFile(c))))) {
            out.writeInt(MAGIC);
            out.writeInt(SIZE);
            out.writeInt(CLASSES.length);
            for (char ch : CLASSES) {
                out.writeChar(ch);
                float[] feat = templates.get(ch);
                for (float v : feat) out.writeFloat(v);
            }
        }
    }

    public Match classify(float[] feature) {
        char best = '.';
        float bestScore = -Float.MAX_VALUE;
        float second = -Float.MAX_VALUE;
        for (char ch : CLASSES) {
            float score = cosine(feature, templates.get(ch));
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = ch;
            } else if (score > second) {
                second = score;
            }
        }
        return new Match(best, bestScore, bestScore - second);
    }

    public static float[] extractFeature(Bitmap src, float cx, float cy, float radius) {
        int left = Math.max(0, Math.round(cx - radius));
        int top = Math.max(0, Math.round(cy - radius));
        int right = Math.min(src.getWidth(), Math.round(cx + radius));
        int bottom = Math.min(src.getHeight(), Math.round(cy + radius));
        int w = Math.max(2, right - left);
        int h = Math.max(2, bottom - top);
        Bitmap crop = Bitmap.createBitmap(src, left, top, w, h);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true);
        if (scaled != crop) crop.recycle();

        int[] px = new int[SIZE * SIZE];
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        float[] f = new float[SIZE * SIZE * 4];
        float[] lum = new float[SIZE * SIZE];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            float r = ((p >> 16) & 255) / 255f;
            float g = ((p >> 8) & 255) / 255f;
            float b = (p & 255) / 255f;
            float sum = r + g + b + 0.02f;
            f[i * 4] = r / sum;
            f[i * 4 + 1] = g / sum;
            f[i * 4 + 2] = b / sum;
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b;
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                float dx = x > 0 ? Math.abs(lum[i] - lum[i - 1]) : 0f;
                float dy = y > 0 ? Math.abs(lum[i] - lum[i - SIZE]) : 0f;
                f[i * 4 + 3] = Math.min(1f, (dx + dy) * 1.8f);
                float nx = (x + 0.5f) / SIZE - 0.5f;
                float ny = (y + 0.5f) / SIZE - 0.5f;
                if (nx * nx + ny * ny > 0.245f) {
                    f[i * 4] = f[i * 4 + 1] = f[i * 4 + 2] = f[i * 4 + 3] = 0f;
                }
            }
        }
        scaled.recycle();
        normalize(f);
        return f;
    }

    private static void normalize(float[] f) {
        double ss = 0;
        for (float v : f) ss += v * v;
        float norm = (float)Math.sqrt(ss) + 1e-6f;
        for (int i = 0; i < f.length; i++) f[i] /= norm;
    }

    private static float cosine(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static float[] mean(List<float[]> list) {
        int len = list.get(0).length;
        float[] out = new float[len];
        for (float[] f : list) for (int i = 0; i < len; i++) out[i] += f[i];
        for (int i = 0; i < len; i++) out[i] /= list.size();
        normalize(out);
        return out;
    }

    public static char[][] initialBoard() {
        String[] rows = {
                "rnbakabnr",
                ".........",
                ".c.....c.",
                "p.p.p.p.p",
                ".........",
                ".........",
                "P.P.P.P.P",
                ".C.....C.",
                ".........",
                "RNBAKABNR"
        };
        char[][] b = new char[10][9];
        for (int r = 0; r < 10; r++) b[r] = rows[r].toCharArray();
        return b;
    }

    public static final class Match {
        public final char piece;
        public final float score;
        public final float margin;
        public Match(char piece, float score, float margin) {
            this.piece = piece; this.score = score; this.margin = margin;
        }
    }
}
