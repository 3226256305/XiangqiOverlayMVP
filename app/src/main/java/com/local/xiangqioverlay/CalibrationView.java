package com.local.xiangqioverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class CalibrationView extends View {
    private Bitmap bitmap;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private Float x1, y1, x2, y2;

    public CalibrationView(Context context) {
        super(context);
        markerPaint.setColor(0xFFE53935);
        markerPaint.setStrokeWidth(dp(3));
        markerPaint.setStyle(Paint.Style.STROKE);
        setBackgroundColor(Color.BLACK);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        reset();
        invalidate();
    }

    public void reset() {
        x1 = y1 = x2 = y2 = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        float scale = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        float left = (getWidth() - w) / 2f;
        float top = (getHeight() - h) / 2f;
        dst.set(left, top, left + w, top + h);
        canvas.drawBitmap(bitmap, null, dst, bitmapPaint);

        if (x1 != null) drawPoint(canvas, x1, y1);
        if (x2 != null) {
            drawPoint(canvas, x2, y2);
            canvas.drawRect(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2), markerPaint);
        }
    }

    private void drawPoint(Canvas c, float x, float y) {
        c.drawCircle(x, y, dp(10), markerPaint);
        c.drawLine(x - dp(16), y, x + dp(16), y, markerPaint);
        c.drawLine(x, y - dp(16), x, y + dp(16), markerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN || bitmap == null || !dst.contains(e.getX(), e.getY())) return true;
        if (x1 == null || x2 != null) {
            x1 = e.getX(); y1 = e.getY(); x2 = y2 = null;
        } else {
            x2 = e.getX(); y2 = e.getY();
        }
        invalidate();
        return true;
    }

    public RectF getBitmapSelection() {
        if (bitmap == null || x1 == null || x2 == null) return null;
        float sx = bitmap.getWidth() / dst.width();
        float sy = bitmap.getHeight() / dst.height();
        float l = (Math.min(x1, x2) - dst.left) * sx;
        float t = (Math.min(y1, y2) - dst.top) * sy;
        float r = (Math.max(x1, x2) - dst.left) * sx;
        float b = (Math.max(y1, y2) - dst.top) * sy;
        return new RectF(l, t, r, b);
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
