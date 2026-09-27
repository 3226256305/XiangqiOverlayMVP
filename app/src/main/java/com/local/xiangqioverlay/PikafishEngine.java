package com.local.xiangqioverlay;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PikafishEngine implements AutoCloseable {
    private final Context context;
    private Process process;
    private BufferedReader reader;
    private PrintWriter writer;
    private static final Pattern INFO = Pattern.compile(".*depth\\s+(\\d+).*multipv\\s+(\\d+).*score\\s+(cp|mate)\\s+(-?\\d+).*pv\\s+(.+)");

    public PikafishEngine(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void start() throws IOException {
        if (process != null && process.isAlive()) return;
        File bin = new File(context.getApplicationInfo().nativeLibraryDir, "libpikafish.so");
        if (!bin.exists() || bin.length() < 100_000) throw new IOException("缺 Pikafish Android ARM64 引擎。运行 scripts/prepare_assets.ps1 后重新构建 APK");
        File engineDir = new File(context.getFilesDir(), "engine");
        if (!engineDir.exists() && !engineDir.mkdirs()) throw new IOException("无法创建引擎目录");
        File nnue = new File(engineDir, "pikafish.nnue");
        if (!nnue.exists()) copyAsset("pikafish.nnue", nnue);
        if (!nnue.exists() || nnue.length() < 1_000_000) throw new IOException("缺 pikafish.nnue");

        bin.setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
        pb.directory(engineDir);
        pb.redirectErrorStream(true);
        process = pb.start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new PrintWriter(process.getOutputStream(), true);

        send("uci");
        readUntil("uciok", 5000);
        send("setoption name Threads value 2");
        send("setoption name Hash value 64");
        send("setoption name EvalFile value " + nnue.getAbsolutePath());
        send("isready");
        readUntil("readyok", 8000);
    }

    public synchronized AnalysisResult analyze(String fen, int movetimeMs, int multiPv) throws IOException {
        start();
        send("stop");
        send("setoption name MultiPV value " + multiPv);
        send("position fen " + fen);
        send("go movetime " + movetimeMs);
        Map<Integer, AnalysisResult.PvLine> latest = new HashMap<>();
        String best = null;
        long deadline = System.currentTimeMillis() + movetimeMs + 5000L;
        while (System.currentTimeMillis() < deadline) {
            String line = reader.readLine();
            if (line == null) throw new IOException("Pikafish 已退出");
            if (line.startsWith("bestmove ")) {
                String[] s = line.split("\\s+");
                if (s.length >= 2) best = s[1];
                break;
            }
            Matcher m = INFO.matcher(line);
            if (m.matches()) {
                int depth = Integer.parseInt(m.group(1));
                int idx = Integer.parseInt(m.group(2));
                String scoreType = m.group(3);
                int raw = Integer.parseInt(m.group(4));
                String score = scoreType.equals("cp") ? String.format("%+.2f", raw / 100.0) : "M" + raw;
                String pv = m.group(5);
                AnalysisResult.PvLine old = latest.get(idx);
                if (old == null || depth >= old.depth) latest.put(idx, new AnalysisResult.PvLine(idx, depth, score, pv));
            }
        }
        if (best == null) throw new IOException("Pikafish 分析超时");
        AnalysisResult result = new AnalysisResult();
        result.bestMove = best;
        for (int i = 1; i <= multiPv; i++) if (latest.containsKey(i)) result.lines.add(latest.get(i));
        return result;
    }

    private void copyAsset(String name, File out) throws IOException {
        try (InputStream in = context.getAssets().open(name); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        } catch (IOException e) {
            if (out.exists()) out.delete();
            throw new IOException("APK 中没有 " + name + "。先运行 scripts/prepare_assets.ps1", e);
        }
    }

    private void send(String command) { writer.println(command); writer.flush(); }

    private void readUntil(String needle, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String line = reader.readLine();
            if (line == null) throw new IOException("Pikafish 启动失败");
            if (line.contains(needle)) return;
        }
        throw new IOException("等待 Pikafish " + needle + " 超时");
    }

    @Override public synchronized void close() {
        try { if (writer != null) send("quit"); } catch (Exception ignored) {}
        if (process != null) process.destroy();
        process = null;
    }
}
