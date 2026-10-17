package local.doja.voice;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.View;

/** Lightweight native face. No audio, network, worker thread or per-frame bitmap. */
public final class FaceView extends View implements Choreographer.FrameCallback {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path mouth = new Path();
    private FaceState state = FaceState.IDLE;
    private boolean attached, foreground, scheduled, reducedMotion;
    private long lastFrame, lastDraw, drawnFrames;
    private long entranceStarted;
    private float openness = .72f, separation = 1, smile = .4f;
    private float red = 157, green = 167, blue = 255;

    public FaceView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_NONE, null);
    }

    public void setState(FaceState value) {
        state = value == null ? FaceState.ERROR : value;
        invalidate();
    }
    FaceState getState() { return state; }
    long getDrawnFrames() { return drawnFrames; }
    boolean isAnimating() { return scheduled; }
    void enterRunning() {
        entranceStarted = reducedMotion ? 0 : SystemClock.uptimeMillis(); invalidate();
    }

    public void setForeground(boolean value) {
        foreground = value;
        if (value) {
            setReducedMotion(Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0);
        }
        updateScheduling();
    }

    void setReducedMotion(boolean value) {
        reducedMotion = value;
        lastDraw = lastFrame = 0;
        updateScheduling(); invalidate();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true; updateScheduling();
    }
    protected void onDetachedFromWindow() {
        attached = false; updateScheduling(); super.onDetachedFromWindow();
    }
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updateScheduling();
    }
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility); updateScheduling();
    }
    private void updateScheduling() {
        boolean animate = attached && foreground && isShown() && getWindowVisibility() == VISIBLE && !reducedMotion;
        if (animate && !scheduled) {
            scheduled = true; lastDraw = lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
        } else if (!animate && scheduled) {
            Choreographer.getInstance().removeFrameCallback(this); scheduled = false;
        }
    }
    public void doFrame(long nanos) {
        if (!scheduled) return;
        // Active face capped at 30 fps; ambient face at 15 fps to spare CPU/battery.
        long interval = state == FaceState.IDLE || state == FaceState.MUTED ? 65000000L : 32000000L;
        if (lastFrame == 0 || nanos - lastFrame >= interval) {
            lastFrame = nanos; invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); drawnFrames++;
        long now = SystemClock.uptimeMillis();
        float blend = reducedMotion || lastDraw == 0 ? 1 : 1 - (float)Math.exp(-Math.min(100, now - lastDraw) / 110f);
        lastDraw = now;
        float targetOpen = .8f, targetSeparation = 1, targetSmile = .45f;
        int color = Color.rgb(164, 178, 255);
        switch (state) {
            case IDLE: targetOpen = .62f; break;
            case MUTED: targetOpen = .22f; targetSmile = .15f; color = Color.rgb(148, 158, 185); break;
            case PREPARING: targetOpen = .6f; targetSmile = .15f; break;
            case READY: color = Color.rgb(111, 215, 234); break;
            case LISTENING: targetOpen = 1; targetSeparation = 1.05f; color = Color.rgb(111, 231, 248); break;
            case THINKING: targetOpen = .65f; targetSmile = .1f; color = Color.rgb(191, 163, 255); break;
            case SPEAKING: targetOpen = .9f; targetSmile = .8f; color = Color.rgb(124, 235, 205); break;
            case ERROR: targetOpen = .55f; targetSmile = -.3f; color = Color.rgb(247, 193, 119); break;
        }
        openness += (targetOpen - openness) * blend;
        separation += (targetSeparation - separation) * blend;
        smile += (targetSmile - smile) * blend;
        red += (Color.red(color) - red) * blend;
        green += (Color.green(color) - green) * blend;
        blue += (Color.blue(color) - blue) * blend;
        int faceColor = Color.rgb(Math.round(red), Math.round(green), Math.round(blue));
        float size = Math.min(getWidth() / 4.6f, getHeight() / 2.6f);
        if (size <= 0) return;
        float time = reducedMotion ? 0 : (now % 120000) / 1000f;
        float breath = reducedMotion ? 0 : (float)Math.sin(time * 1.7f);
        float gaze = reducedMotion ? 0 : (float)Math.sin(time * .48f) * size * .055f;
        if (state == FaceState.THINKING) gaze += size * .09f;
        float cx = getWidth() / 2f + gaze, cy = getHeight() * .44f + breath * size * .025f;
        int saved = canvas.save();
        if (!reducedMotion && entranceStarted > 0) {
            float progress = Math.min(1, (now - entranceStarted) / 680f);
            float remaining = 1 - progress;
            float overshoot = (float)Math.sin(progress * Math.PI) * size * .25f;
            canvas.translate(-getWidth() * remaining * remaining * remaining + overshoot,
                -Math.abs((float)Math.sin(progress * Math.PI * 4)) * size * .18f * remaining);
            canvas.rotate((float)Math.sin(progress * Math.PI * 6) * 8 * remaining, cx, cy);
            if (progress >= 1) entranceStarted = 0;
        }
        float blink = 1;
        if (!reducedMotion) {
            long phase = now % 4700;
            if (phase < 150) blink = .06f + .94f * Math.abs(phase - 75) / 75f;
        }
        float eyeWidth = size * .62f, eyeHeight = Math.max(size * .045f, size * .93f * openness * blink);
        float gap = size * .9f * separation;
        paint.setStyle(Paint.Style.FILL);
        // A few translucent outlines give depth without blur/shadow buffers.
        for (int layer = 3; layer >= 1; layer--) {
            paint.setColor(faceColor); paint.setAlpha(5 + (3 - layer) * 3);
            float spread = size * .055f * layer;
            eye(canvas, cx - gap, cy, eyeWidth + spread, eyeHeight + spread, size * .22f);
            eye(canvas, cx + gap, cy, eyeWidth + spread, eyeHeight + spread, size * .22f);
        }
        paint.setColor(faceColor); paint.setAlpha(255);
        eye(canvas, cx - gap, cy, eyeWidth, eyeHeight, size * .22f);
        eye(canvas, cx + gap, cy, eyeWidth, eyeHeight, size * .22f);
        if (state == FaceState.THINKING) {
            // Asymmetric brows and a sideways glance, rather than hiding the face.
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2, size * .045f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx - gap - eyeWidth * .45f, cy - eyeHeight * .7f,
                cx - gap + eyeWidth * .4f, cy - eyeHeight * .9f, paint);
            canvas.drawLine(cx + gap - eyeWidth * .4f, cy - eyeHeight * .85f,
                cx + gap + eyeWidth * .4f, cy - eyeHeight * .6f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float y = cy + size * .82f;
        if (state == FaceState.SPEAKING) {
            float opening = reducedMotion ? .12f : .08f + .16f * Math.abs((float)Math.sin(time * 11));
            bounds.set(cx - size * .25f, y - size * opening, cx + size * .25f, y + size * opening);
            canvas.drawRoundRect(bounds, size * .12f, size * .12f, paint);
        } else {
            mouth.reset(); mouth.moveTo(cx - size * .32f, y);
            mouth.quadTo(cx, y + size * .34f * smile, cx + size * .32f, y);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(2, size * .045f)); canvas.drawPath(mouth, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (state == FaceState.THINKING || state == FaceState.PREPARING) {
            for (int dot = 0; dot < 3; dot++) {
                float wave = reducedMotion ? .5f : .5f + .5f * (float)Math.sin(time * 4 - dot * .9f);
                paint.setAlpha(70 + Math.round(150 * wave));
                canvas.drawCircle(cx + (dot - 1) * size * .24f, y + size * .42f, size * .035f, paint);
            }
            paint.setAlpha(255);
        }
        canvas.restoreToCount(saved);
    }
    private void eye(Canvas canvas, float x, float y, float width, float height, float radius) {
        bounds.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }
}
