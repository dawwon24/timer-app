package com.hs.timer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 남은 시간 비율만큼 줄어드는 원형 진행 링. */
public class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction = 1f;

    public RingView(Context c, AttributeSet a) {
        super(c, a);
        float w = 8 * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(c.getColor(R.color.track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(c.getColor(R.color.accent));
    }

    public void setFraction(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (f != fraction) {
            fraction = f;
            invalidate();
        }
    }

    public void setArcColor(int color) {
        arc.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        int s = (MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED) ? w : Math.min(w, h);
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float half = track.getStrokeWidth() / 2f;
        rect.set(half, half, getWidth() - half, getHeight() - half);
        canvas.drawOval(rect, track);
        if (fraction > 0f) {
            // 12시 방향에서 시작, 남은 만큼 시계 방향으로 그림
            canvas.drawArc(rect, -90f, 360f * fraction, false, arc);
        }
    }
}
