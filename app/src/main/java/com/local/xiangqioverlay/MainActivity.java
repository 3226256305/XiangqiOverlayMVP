package com.local.xiangqioverlay;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_NOTIFY = 1002;
    private TextView status;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());
        updateStatus();
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("象棋离线悬浮分析 · MVP");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("流程：授权悬浮窗 → 授权录屏 → 回到象棋 App → 点悬浮窗“校准” → 在初始局面截图上点棋盘左上/右下交叉点 → 返回游戏。\n\n第一版只给分析和箭头，不自动点击棋子。完全离线运行。\n");
        desc.setTextSize(16);
        desc.setTextColor(0xFF333333);
        root.addView(desc);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF444444);
        root.addView(status);

        Button overlay = button("1. 授权悬浮窗");
        overlay.setOnClickListener(v -> requestOverlay());
        root.addView(overlay);

        Button start = button("2. 授权录屏并启动");
        start.setOnClickListener(v -> requestCapture());
        root.addView(start);

        Button stop = button("停止服务");
        stop.setOnClickListener(v -> stopService(new Intent(this, CaptureService.class)));
        root.addView(stop);

        TextView tip = new TextView(this);
        tip.setText("提示：校准必须尽量在标准初始局面完成。若换了棋盘皮肤、缩放比例或横竖屏，重新校准。\n\n若悬浮窗显示“缺 Pikafish”，先在电脑运行 scripts/prepare_assets.ps1，再重新构建 APK。");
        tip.setTextSize(14);
        tip.setTextColor(0xFF666666);
        root.addView(tip);
        return root;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }
    }

    private void requestCapture() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlay();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
            startForegroundService(service);
            status.setText("服务已启动。现在切回象棋 App。第一次请在初始局面点悬浮窗“校准”。");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean calibrated = AppPrefs.templateVersion(this) > 0;
        status.setText("悬浮窗权限：" + (overlay ? "已授权" : "未授权") +
                "\n棋盘模板：" + (calibrated ? "已校准" : "未校准") +
                "\n当前行棋方：" + (AppPrefs.side(this).equals("w") ? "红方" : "黑方"));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
