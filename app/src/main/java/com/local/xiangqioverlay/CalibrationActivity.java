package com.local.xiangqioverlay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class CalibrationActivity extends Activity {
    private CalibrationView calibrationView;
    private Bitmap screenshot;
    private boolean blackBottom;
    private Button orientationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra("screenshot_path");
        screenshot = path == null ? null : BitmapFactory.decodeFile(path);
        if (screenshot == null) {
            Toast.makeText(this, "无法读取校准截图", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        blackBottom = AppPrefs.blackBottom(this);
        setContentView(buildUi());
        calibrationView.setBitmap(screenshot);
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView instructions = new TextView(this);
        instructions.setText("必须是标准初始局面。依次点击棋盘【左上角交叉点】和【右下角交叉点】。不是棋盘外框，而是最外侧棋子所在的两个交叉点。\n");
        instructions.setTextSize(15);
        root.addView(instructions);

        calibrationView = new CalibrationView(this);
        root.addView(calibrationView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        orientationButton = new Button(this);
        updateOrientationText();
        orientationButton.setOnClickListener(v -> { blackBottom = !blackBottom; updateOrientationText(); });
        bar.addView(orientationButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button reset = new Button(this);
        reset.setText("重选");
        reset.setOnClickListener(v -> calibrationView.reset());
        bar.addView(reset, new LinearLayout.LayoutParams(0, -2, 0.6f));

        Button confirm = new Button(this);
        confirm.setText("保存校准");
        confirm.setOnClickListener(v -> saveCalibration());
        bar.addView(confirm, new LinearLayout.LayoutParams(0, -2, 0.9f));

        root.addView(bar);
        return root;
    }

    private void updateOrientationText() {
        if (orientationButton != null) orientationButton.setText(blackBottom ? "视角：黑方在下" : "视角：红方在下");
    }

    private void saveCalibration() {
        RectF rect = calibrationView.getBitmapSelection();
        if (rect == null || rect.width() < screenshot.getWidth() * 0.35f || rect.height() < screenshot.getHeight() * 0.25f) {
            Toast.makeText(this, "请正确点击棋盘左上、右下交叉点", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            TemplateModel.calibrateAndSave(this, screenshot, rect, blackBottom);
            AppPrefs.get(this).edit()
                    .putFloat("board_l", rect.left / screenshot.getWidth())
                    .putFloat("board_t", rect.top / screenshot.getHeight())
                    .putFloat("board_r", rect.right / screenshot.getWidth())
                    .putFloat("board_b", rect.bottom / screenshot.getHeight())
                    .putBoolean("black_bottom", blackBottom)
                    .putString("side", "w")
                    .putLong("template_version", System.currentTimeMillis())
                    .apply();
            Toast.makeText(this, "校准完成。切回象棋 App 即可。", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "校准失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenshot != null && !screenshot.isRecycled()) screenshot.recycle();
        String path = getIntent().getStringExtra("screenshot_path");
        if (path != null) new File(path).delete();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
