package com.local.xiangqioverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class ArrowOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String move;

    public ArrowOverlayView(Context c) {
        super(c);
        paint.setColor(0xD9E53935);
        paint.setStrokeWidth(dp(6));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setMove(String move) { this.move = move; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (move == null || move.length() < 4) return;
        SharedPreferences p = AppPrefs.get(getContext());
        float l = p.getFloat("board_l", 0) * getWidth();
        float t = p.getFloat("board_t", 0) * getHeight();
        float r = p.getFloat("board_r", 1) * getWidth();
        float b = p.getFloat("board_b", 1) * getHeight();
        boolean blackBottom = p.getBoolean("black_bottom", false);
        float[] a = coord(move.substring(0,2), l,t,r,b,blackBottom);
        float[] z = coord(move.substring(2,4), l,t,r,b,blackBottom);
        if (a == null || z == null) return;
        canvas.drawLine(a[0], a[1], z[0], z[1], paint);
        double ang = Math.atan2(z[1]-a[1], z[0]-a[0]);
        float len = dp(18);
        Path path = new Path();
        path.moveTo(z[0], z[1]);
        path.lineTo((float)(z[0]-len*Math.cos(ang-Math.PI/6)), (float)(z[1]-len*Math.sin(ang-Math.PI/6)));
        path.moveTo(z[0], z[1]);
        path.lineTo((float)(z[0]-len*Math.cos(ang+Math.PI/6)), (float)(z[1]-len*Math.sin(ang+Math.PI/6)));
        canvas.drawPath(path, paint);
    }

    private float[] coord(String s, float l, float t, float r, float b, boolean blackBottom) {
        int file = s.charAt(0) - 'a';
        int rank = s.charAt(1) - '0';
        if (file < 0 || file > 8 || rank < 0 || rank > 9) return null;
        int cr = 9 - rank;
        int cc = file;
        int vr = blackBottom ? 9 - cr : cr;
        int vc = blackBottom ? 8 - cc : cc;
        return new float[]{ l + (r-l) * vc / 8f, t + (b-t) * vr / 9f };
    }

    private float dp(int v) { return v * getResources().getDisplayMetrics().density; }
}
