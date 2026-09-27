package com.local.xiangqioverlay;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {
    public String bestMove;
    public final List<PvLine> lines = new ArrayList<>();

    public static class PvLine {
        public int index;
        public int depth;
        public String score;
        public String pv;
        public PvLine(int index, int depth, String score, String pv) {
            this.index = index; this.depth = depth; this.score = score; this.pv = pv;
        }
    }
}
