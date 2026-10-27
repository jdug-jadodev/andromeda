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
    private FaceReaction reaction = FaceReaction.NONE;
    private long reactionUntil;
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
    public void setReaction(FaceReaction value, long durationMs) {
        reaction = value == null ? FaceReaction.NONE : value;
        reactionUntil = durationMs <= 0 ? 0 : SystemClock.uptimeMillis() + durationMs;
        invalidate();
    }
    FaceReaction getReaction() { return reaction; }
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
        if (reactionUntil > 0 && now > reactionUntil) { reaction = FaceReaction.NONE; reactionUntil = 0; }
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
        switch (reaction) {
            case SAD: targetOpen = .55f; targetSmile = -.45f; color = Color.rgb(151, 174, 220); break;
            case CRY: targetOpen = .5f; targetSmile = -.7f; color = Color.rgb(132, 174, 230); break;
            case COLD: targetOpen = .7f; targetSmile = -.1f; color = Color.rgb(139, 211, 255); break;
            case HOT: targetOpen = .75f; targetSmile = .2f; color = Color.rgb(255, 157, 102); break;
            case FOG: targetOpen = .6f; targetSmile = 0f; color = Color.rgb(176, 190, 203); break;
            case CELEBRATE: targetOpen = .95f; targetSmile = .95f; color = Color.rgb(255, 214, 112); break;
            case LOVE: targetOpen = .95f; targetSmile = .8f; color = Color.rgb(255, 143, 198); break;
            case SILLY: targetOpen = .9f; targetSmile = .9f; color = Color.rgb(255, 219, 104); break;
            case DISGUST: targetOpen = .5f; targetSmile = -.2f; color = Color.rgb(144, 204, 122); break;
            case COOL: targetOpen = .8f; targetSmile = .65f; color = Color.rgb(189, 166, 255); break;
            default: break;
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
        drawReaction(canvas, cx, cy, size, gap, eyeWidth, eyeHeight, y);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(saved);
    }
    private void drawReaction(Canvas canvas, float cx, float cy, float size,
            float gap, float eyeWidth, float eyeHeight, float mouthY) {
        switch (reaction) {
            case SAD:
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2, size * .035f));
                paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(Color.rgb(130, 155, 205));
                canvas.drawLine(cx - gap - eyeWidth * .3f, cy - eyeHeight * .65f,
                    cx - gap + eyeWidth * .25f, cy - eyeHeight * .5f, paint);
                canvas.drawLine(cx + gap - eyeWidth * .25f, cy - eyeHeight * .5f,
                    cx + gap + eyeWidth * .3f, cy - eyeHeight * .65f, paint);
                break;
            case CRY:
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(91, 165, 235));
                for (int side = -1; side <= 1; side += 2) {
                    float x = cx + side * gap;
                    canvas.drawOval(new RectF(x - size * .035f, cy + eyeHeight * .45f,
                        x + size * .035f, cy + eyeHeight * .8f), paint);
                    canvas.drawCircle(x, cy + eyeHeight * .92f, size * .04f, paint);
                }
                break;
            case COLD:
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2, size * .03f));
                paint.setColor(Color.rgb(111, 179, 245));
                for (int side = -1; side <= 1; side += 2) {
                    float x = cx + side * size * 1.05f;
                    for (int i = 0; i < 3; i++) {
                        float y = cy + (i - 1) * size * .18f;
                        canvas.drawLine(x - size * .08f, y, x + size * .08f, y, paint);
                    }
                }
                break;
            case HOT:
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(255, 111, 87));
                float sweatX = cx + size * .62f, sweatY = cy - size * .2f;
                Path drop = new Path(); drop.moveTo(sweatX, sweatY - size * .1f);
                drop.quadTo(sweatX + size * .09f, sweatY + size * .06f, sweatX, sweatY + size * .13f);
                drop.quadTo(sweatX - size * .09f, sweatY + size * .06f, sweatX, sweatY - size * .1f);
                canvas.drawPath(drop, paint);
                break;
            case FOG:
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(225, 232, 240)); paint.setAlpha(95);
                for (int i = 0; i < 3; i++)
                    canvas.drawCircle(cx + (i - 1) * size * .48f, cy + size * .95f, size * .2f, paint);
                paint.setAlpha(255);
                break;
            case CELEBRATE:
                int[] colors = {Color.rgb(255, 120, 120), Color.rgb(120, 205, 255), Color.rgb(255, 214, 100)};
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 9; i++) {
                    paint.setColor(colors[i % colors.length]);
                    float angle = (float)(Math.PI * 2 * i / 9), radius = size * (1.05f + (i % 2) * .18f);
                    canvas.drawCircle(cx + (float)Math.cos(angle) * radius,
                        cy + (float)Math.sin(angle) * radius * .8f, size * .045f, paint);
                }
                break;
            case LOVE:
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(255, 105, 170));
                drawHeart(canvas, cx - size * .95f, cy - size * .35f, size * .14f);
                drawHeart(canvas, cx + size * .95f, cy - size * .45f, size * .11f);
                break;
            case SILLY:
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(242, 123, 153));
                canvas.drawOval(new RectF(cx - size * .12f, mouthY + size * .02f,
                    cx + size * .12f, mouthY + size * .26f), paint);
                break;
            case DISGUST:
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2, size * .035f));
                paint.setColor(Color.rgb(83, 145, 83));
                Path wave = new Path(); wave.moveTo(cx - size * .3f, mouthY);
                for (int i = 1; i <= 4; i++) wave.lineTo(cx - size * .3f + i * size * .15f,
                    mouthY + (i % 2 == 0 ? -1 : 1) * size * .08f);
                canvas.drawPath(wave, paint);
                break;
            case COOL:
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(24, 27, 38)); paint.setAlpha(235);
                bounds.set(cx - gap - eyeWidth * .65f, cy - eyeHeight * .38f,
                    cx + gap + eyeWidth * .65f, cy + eyeHeight * .38f);
                canvas.drawRoundRect(bounds, size * .08f, size * .08f, paint);
                paint.setColor(Color.rgb(90, 102, 130)); paint.setAlpha(255);
                canvas.drawRect(cx - size * .04f, cy - eyeHeight * .3f, cx + size * .04f, cy + eyeHeight * .3f, paint);
                break;
            default: break;
        }
    }

    private void drawHeart(Canvas canvas, float x, float y, float size) {
        Path heart = new Path();
        heart.moveTo(x, y + size * .35f);
        heart.cubicTo(x - size * 1.1f, y - size * .4f, x - size * .4f, y - size * 1.1f, x, y - size * .45f);
        heart.cubicTo(x + size * .4f, y - size * 1.1f, x + size * 1.1f, y - size * .4f, x, y + size * .35f);
        canvas.drawPath(heart, paint);
    }

    private void eye(Canvas canvas, float x, float y, float width, float height, float radius) {
        bounds.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }
}
