package com.local.xiangqioverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final int NOTIF_ID = 77;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread imageThread;
    private final Object frameLock = new Object();
    private Bitmap latestFrame;
    private ScheduledExecutorService analyzer;
    private BoardRecognizer recognizer;
    private PikafishEngine engine;
    private WindowManager wm;
    private LinearLayout panel;
    private ArrowOverlayView arrow;
    private TextView statusText, linesText;
    private Handler mainHandler;
    private boolean paused = false;
    private String lastBoard;
    private String pendingBoard;
    private int pendingCount;

    @Override public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(getMainLooper());
        recognizer = new BoardRecognizer(this);
        engine = new PikafishEngine(this);
        createNotificationChannel();
        if (Settings.canDrawOverlays(this)) createOverlay();
        analyzer = Executors.newSingleThreadScheduledExecutor();
        analyzer.scheduleWithFixedDelay(this::analyzeTick, 1200, 1200, TimeUnit.MILLISECONDS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIF_ID, n);
        if (projection == null && intent != null && intent.hasExtra(EXTRA_RESULT_DATA)) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            startProjection(code, data);
        }
        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager m = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = m.getMediaProjection(resultCode, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, mainHandler);
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels, h = dm.heightPixels, density = dm.densityDpi;
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            imageThread = new HandlerThread("capture-images");
            imageThread.start();
            imageReader.setOnImageAvailableListener(this::onImage, new Handler(imageThread.getLooper()));
            virtualDisplay = projection.createVirtualDisplay("XiangqiOverlayCapture", w, h, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
            updateStatus("录屏已启动，等待校准/识别");
        } catch (Exception e) {
            updateStatus("录屏启动失败：" + e.getMessage());
        }
    }

    private void onImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();
            int paddedWidth = image.getWidth() + rowPadding / pixelStride;
            Bitmap padded = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap bmp = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
            if (bmp != padded) padded.recycle();
            synchronized (frameLock) {
                if (latestFrame != null && !latestFrame.isRecycled()) latestFrame.recycle();
                latestFrame = bmp;
            }
        } catch (Exception ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private void analyzeTick() {
        if (paused || projection == null || AppPrefs.templateVersion(this) <= 0) return;
        Bitmap frame = copyFrame();
        if (frame == null) return;
        try {
            BoardRecognizer.RecognitionResult rr = recognizer.recognize(frame);
            if (!rr.plausible) {
                updateStatus(String.format("识别不可信 avg %.3f / margin %.3f，建议重新校准", rr.avgScore, rr.avgMargin));
                return;
            }
            if (!rr.boardFen.equals(pendingBoard)) {
                pendingBoard = rr.boardFen;
                pendingCount = 1;
                return;
            }
            pendingCount++;
            if (pendingCount < 2) return;

            if (lastBoard == null) {
                lastBoard = rr.boardFen;
            } else if (!lastBoard.equals(rr.boardFen)) {
                lastBoard = rr.boardFen;
                String side = AppPrefs.side(this).equals("w") ? "b" : "w";
                AppPrefs.setSide(this, side);
            }
            String side = AppPrefs.side(this);
            String fen = rr.fullFen(side);
            updateStatus(String.format("已识别 · %s走 · avg %.3f", side.equals("w") ? "红" : "黑", rr.avgScore));
            AnalysisResult ar = engine.analyze(fen, 450, 3);
            showAnalysis(ar);
        } catch (Exception e) {
            updateStatus(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            frame.recycle();
        }
    }

    private Bitmap copyFrame() {
        synchronized (frameLock) {
            if (latestFrame == null || latestFrame.isRecycled()) return null;
            return latestFrame.copy(Bitmap.Config.ARGB_8888, false);
        }
    }

    private void createOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        arrow = new ArrowOverlayView(this);
        WindowManager.LayoutParams arrowLp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        arrowLp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(arrow, arrowLp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        panel.setBackgroundColor(0xDD111111);
        statusText = new TextView(this);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(12);
        panel.addView(statusText);
        linesText = new TextView(this);
        linesText.setTextColor(0xFFFFFFFF);
        linesText.setTextSize(13);
        panel.addView(linesText);

        LinearLayout buttons = new LinearLayout(this);
        Button calibrate = smallButton("校准");
        calibrate.setOnClickListener(v -> launchCalibration());
        buttons.addView(calibrate);
        Button side = smallButton("换边");
        side.setOnClickListener(v -> {
            AppPrefs.setSide(this, AppPrefs.side(this).equals("w") ? "b" : "w");
            updateStatus("手动切换：" + (AppPrefs.side(this).equals("w") ? "红方走" : "黑方走"));
        });
        buttons.addView(side);
        Button pause = smallButton("暂停");
        pause.setOnClickListener(v -> { paused = !paused; pause.setText(paused ? "继续" : "暂停"); });
        buttons.addView(pause);
        Button close = smallButton("停");
        close.setOnClickListener(v -> stopSelf());
        buttons.addView(close);
        panel.addView(buttons);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(300), -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(8); lp.y = dp(80);
        wm.addView(panel, lp);
    }

    private Button smallButton(String t) {
        Button b = new Button(this);
        b.setText(t); b.setTextSize(11); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0);
        return b;
    }

    private void launchCalibration() {
        Bitmap frame = copyFrame();
        if (frame == null) { updateStatus("还没有捕获到屏幕画面"); return; }
        try {
            File f = new File(getCacheDir(), "calibration.png");
            try (FileOutputStream out = new FileOutputStream(f)) { frame.compress(Bitmap.CompressFormat.PNG, 100, out); }
            Intent i = new Intent(this, CalibrationActivity.class);
            i.putExtra("screenshot_path", f.getAbsolutePath());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(i);
        } catch (Exception e) {
            updateStatus("保存校准截图失败：" + e.getMessage());
        } finally { frame.recycle(); }
    }

    private void showAnalysis(AnalysisResult ar) {
        mainHandler.post(() -> {
            if (arrow != null) arrow.setMove(ar.bestMove);
            if (linesText != null) {
                StringBuilder s = new StringBuilder();
                s.append("最佳：").append(ar.bestMove).append('\n');
                for (AnalysisResult.PvLine l : ar.lines) {
                    String pv = l.pv;
                    String[] moves = pv.split("\\s+");
                    StringBuilder shortPv = new StringBuilder();
                    for (int i = 0; i < Math.min(5, moves.length); i++) {
                        if (i > 0) shortPv.append(' ');
                        shortPv.append(moves[i]);
                    }
                    s.append(l.index).append("  ").append(l.score).append("  d").append(l.depth).append("  ").append(shortPv).append('\n');
                }
                linesText.setText(s.toString());
            }
        });
    }

    private void updateStatus(String s) { mainHandler.post(() -> { if (statusText != null) statusText.setText(s); }); }

    private Notification buildNotification() {
        return new Notification.Builder(this, "capture")
                .setContentTitle("象棋离线悬浮分析")
                .setContentText("正在读取屏幕并本地分析")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("capture", "屏幕分析", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (analyzer != null) analyzer.shutdownNow();
        engine.close();
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (projection != null) projection.stop();
        if (imageThread != null) imageThread.quitSafely();
        synchronized (frameLock) { if (latestFrame != null && !latestFrame.isRecycled()) latestFrame.recycle(); }
        try { if (wm != null && panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        try { if (wm != null && arrow != null) wm.removeView(arrow); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
