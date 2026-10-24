package com.hs.timer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Scroller;

/**
 * 구분선 없는 순환 숫자 휠. 가운데 숫자는 크고 진하게, 멀어질수록 작고 흐리게 그린다.
 */
public class WheelPicker extends View {
    public interface OnValueChangeListener {
        void onValueChange(WheelPicker picker, int value);
    }

    private static final int VISIBLE_ROWS = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Scroller scroller;
    private final float itemH, textSize;
    private final int touchSlop, minFling, maxFling;
    private VelocityTracker velocity;
    private OnValueChangeListener listener;

    private int count = 60;
    private int value;
    private int pos; // 스크롤 위치(px). value * itemH 에 정렬되면 정지 상태
    private float downY, lastY;
    private boolean dragging;

    public WheelPicker(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        itemH = 64 * d;
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 46, getResources().getDisplayMetrics());
        scroller = new Scroller(c);
        scroller.setFriction(ViewConfiguration.getScrollFriction() * 2.5f);
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        paint.setFontFeatureSettings("tnum");
        paint.setColor(c.getColor(R.color.text));
        setFocusable(true);
        setClickable(true);
    }

    public void setMaxValue(int max) {
        count = max + 1;
        setValue(Math.min(value, max));
    }

    public int getValue() {
        return value;
    }

    public void setValue(int v) {
        scroller.forceFinished(true);
        value = Math.floorMod(v, count);
        pos = Math.round(value * itemH);
        invalidate();
    }

    public void setOnValueChangedListener(OnValueChangeListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int h = (int) (itemH * VISIBLE_ROWS) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        int first = (int) Math.floor(pos / itemH);
        float frac = pos - first * itemH;
        int base = paint.getColor() & 0x00FFFFFF;
        for (int k = -2; k <= 3; k++) {
            float y = cy + k * itemH - frac;
            float dist = Math.min(1.5f, Math.abs(y - cy) / itemH); // 가운데로부터 몇 칸 떨어졌는지
            float t = dist / 1.5f;
            float scale = 1f - 0.42f * Math.min(1f, dist);
            int alpha = (int) (255 * (1f - 0.8f * t) * (1f - 0.35f * t));
            if (alpha <= 0) continue;
            paint.setTextSize(textSize * scale);
            paint.setColor(base | (alpha << 24));
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label(first + k), cx, y - (fm.ascent + fm.descent) / 2f, paint);
        }
        paint.setColor(base | 0xFF000000);
    }

    private String label(int i) {
        int v = Math.floorMod(i, count);
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                downY = lastY = y;
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(y - downY) > touchSlop) {
                    dragging = true;
                    lastY = y;
                }
                if (dragging) {
                    pos -= Math.round(y - lastY);
                    lastY = y;
                    updateValue();
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    velocity.computeCurrentVelocity(1000, maxFling);
                    int vy = (int) velocity.getYVelocity();
                    if (Math.abs(vy) > minFling) {
                        scroller.fling(0, pos, 0, -vy, 0, 0, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
                        scroller.setFinalY(snapped(scroller.getFinalY()));
                        postInvalidateOnAnimation();
                    } else {
                        settle();
                    }
                } else {
                    // 위/아래 숫자를 탭하면 한 칸 이동
                    float cy = getHeight() / 2f;
                    if (y < cy - itemH / 2) scrollBy(-1);
                    else if (y > cy + itemH / 2) scrollBy(1);
                    else settle();
                    performClick();
                }
                recycleVelocity();
                break;
            case MotionEvent.ACTION_CANCEL:
                settle();
                recycleVelocity();
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void recycleVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    private int snapped(int y) {
        return Math.round(Math.round(y / itemH) * itemH);
    }

    private void settle() {
        animateTo(snapped(pos), 220);
    }

    private void scrollBy(int rows) {
        animateTo(snapped(pos) + Math.round(rows * itemH), 260);
    }

    private void animateTo(int target, int ms) {
        scroller.forceFinished(true);
        if (target == pos) return;
        scroller.startScroll(0, pos, 0, target - pos, ms);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            pos = scroller.getCurrY();
            updateValue();
            postInvalidateOnAnimation();
            if (scroller.isFinished()) {
                // 숫자가 커지지 않도록 위치를 한 바퀴 범위로 되돌림
                pos = Math.round(value * itemH);
            }
        }
    }

    private void updateValue() {
        int v = Math.floorMod(Math.round(pos / itemH), count);
        if (v != value) {
            value = v;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onValueChange(this, v);
        }
    }

    // ---------- 접근성 ----------

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.NumberPicker";
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(String.valueOf(value));
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            scrollBy(1);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            scrollBy(-1);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
