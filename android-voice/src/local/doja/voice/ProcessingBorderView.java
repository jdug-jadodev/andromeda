package local.doja.voice;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;

/** Clockwise perimeter loader with a fading tail. Presentation only, not progress. */
final class ProcessingBorderView extends View implements Choreographer.FrameCallback {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path perimeter = new Path(), segment = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] position = new float[2];
    private boolean processing, foreground, attached, scheduled, reducedMotion;
    private long lastFrame, started;
    private float length;
    private int color = Color.rgb(191, 163, 255);

    ProcessingBorderView(Context context) {
        super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE); paint.setStrokeCap(Paint.Cap.ROUND);
    }
    void setColor(int value) { color = value | 0xff000000; invalidate(); }
    int getColor() { return color; }
    boolean isProcessing() { return processing; }
    boolean isAnimating() { return scheduled; }
    void setProcessing(boolean value) {
        if (value && !processing) started = SystemClock.uptimeMillis();
        processing = value; setVisibility(value ? VISIBLE : GONE); updateScheduling(); invalidate();
    }
    void setForeground(boolean value) {
        foreground = value;
        if (value) reducedMotion = Settings.Global.getFloat(getContext().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0;
        updateScheduling(); invalidate();
    }
    void setReducedMotion(boolean value) { reducedMotion = value; updateScheduling(); invalidate(); }
    protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; updateScheduling(); }
    protected void onDetachedFromWindow() { attached = false; updateScheduling(); super.onDetachedFromWindow(); }
    protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateScheduling(); }
    private void updateScheduling() {
        boolean animate = attached && foreground && processing && getWindowVisibility() == VISIBLE && !reducedMotion;
        if (animate && !scheduled) {
            scheduled = true; lastFrame = 0; Choreographer.getInstance().postFrameCallback(this);
        } else if (!animate && scheduled) {
            scheduled = false; Choreographer.getInstance().removeFrameCallback(this);
        }
    }
    public void doFrame(long nanos) {
        if (!scheduled) return;
        if (lastFrame == 0 || nanos - lastFrame >= 32000000L) { lastFrame = nanos; invalidate(); }
        Choreographer.getInstance().postFrameCallback(this);
    }
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(7), radius = dp(25);
        perimeter.reset(); perimeter.addRoundRect(inset, inset, width - inset, height - inset,
            radius, radius, Path.Direction.CW);
        measure.setPath(perimeter, true); length = measure.getLength();
    }
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!processing || length <= 0) return;
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
        if (reducedMotion) {
            paint.setAlpha(180); paint.setStrokeWidth(dp(3)); canvas.drawPath(perimeter, paint); return;
        }
        float head = ((SystemClock.uptimeMillis() - started) % 3800) / 3800f * length;
        float step = length * .25f / 36;
        for (int index = 35; index >= 0; index--) {
            float end = wrap(head - index * step), start = wrap(end - step);
            segment.reset();
            if (start <= end) measure.getSegment(start, end, segment, true);
            else { measure.getSegment(start, length, segment, true); measure.getSegment(0, end, segment, true); }
            float intensity = (36 - index) / 36f;
            paint.setStrokeWidth(dp(11)); paint.setAlpha(Math.round(38 * intensity * intensity));
            canvas.drawPath(segment, paint);
            paint.setStrokeWidth(dp(3)); paint.setAlpha(Math.round(240 * intensity * intensity));
            canvas.drawPath(segment, paint);
        }
        measure.getPosTan(head, position, null);
        paint.setStyle(Paint.Style.FILL); paint.setAlpha(255);
        canvas.drawCircle(position[0], position[1], dp(2.2f), paint);
    }
    private float wrap(float value) { return (value % length + length) % length; }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
