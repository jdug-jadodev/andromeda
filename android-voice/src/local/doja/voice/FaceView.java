package local.doja.voice;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.View;

/**
 * Lightweight native face, eyes only. No audio, network, worker thread or per-frame bitmap.
 * Every visible quantity eases toward the target of the current state/reaction, so changes are never abrupt.
 * The same maths drive artifacts/andromeda-face-preview.html, which is the visual reference.
 */
public final class FaceView extends View implements Choreographer.FrameCallback {
    // Smoothed parameters (indexes into current[]/target[]/TAU[]).
    private static final int SCALE = 0, GAP = 1, LOOK_Y = 2, HEIGHT = 3, DROOP = 4, CUT_DOWN = 5, CUT_UP = 6, LID = 7,
        TEAR = 8, HEART = 9, ZZ = 10, THINK = 11, SPEAK = 12, BREATH = 13, COUNT = 14;
    private static final float[] TAU = {.22f, .3f, .3f, .25f, .3f, .22f, .25f, .14f, .35f, .3f, .7f, .55f, .4f, .9f};
    // Only a failure changes the eye colour.
    private static final int[] BASE = {164, 178, 255}, AMBER = {247, 193, 119}, SAD_BLUE = {151, 174, 220};
    private static final float DROWSY_RAMP_MS = 10000f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), cut = new Path(), clip = new Path();
    private final RectF bounds = new RectF();
    private final float[] current = newParameters(), target = new float[COUNT];
    private final float[] rgb = {BASE[0], BASE[1], BASE[2]};
    private final int[] colorTarget = new int[3];
    private FaceState state = FaceState.IDLE;
    private FaceReaction reaction = FaceReaction.NONE;
    private long reactionUntil, stateSince, startleAt;
    private boolean attached, foreground, scheduled, reducedMotion;
    private long lastFrame, lastDraw, drawnFrames;
    private long entranceStarted;

    public FaceView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_NONE, null);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
    }

    private static float[] newParameters() {
        float[] values = new float[COUNT];
        values[SCALE] = values[GAP] = values[HEIGHT] = values[BREATH] = 1;
        return values;
    }

    public void setState(FaceState value) {
        FaceState next = value == null ? FaceState.ERROR : value;
        if (next != state) stateSince = SystemClock.uptimeMillis();
        state = next;
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
    /** Woken up by a voice: the eyes pop wide, jump and shake, then settle. */
    void startle() {
        startleAt = reducedMotion ? 0 : SystemClock.uptimeMillis(); invalidate();
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
        boolean ambient = state == FaceState.IDLE || state == FaceState.MUTED || state == FaceState.ASLEEP;
        long interval = ambient ? 65000000L : 32000000L;
        if (lastFrame == 0 || nanos - lastFrame >= interval) {
            lastFrame = nanos; invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** Targets for the current state and reaction; the drawn values ease toward them. */
    private void computeTargets(float time, long now, int[] color) {
        float[] t = target;
        java.util.Arrays.fill(t, 0);
        t[SCALE] = t[GAP] = t[HEIGHT] = t[BREATH] = 1;
        color[0] = BASE[0]; color[1] = BASE[1]; color[2] = BASE[2];
        switch (state) {
            case MUTED: t[SCALE] = .72f; break;
            case PREPARING: t[SCALE] = .88f; break;
            case READY: t[SCALE] = .94f; break;
            case LISTENING: t[GAP] = 1.05f; break;
            case THINKING: t[GAP] = .94f; t[THINK] = 1; break;
            case SPEAKING: t[SCALE] = 1.04f; t[SPEAK] = 1; break;
            case ERROR: System.arraycopy(AMBER, 0, color, 0, 3); break;
            case DROWSY: {
                // Slow nods: the lids sink gradually, then the head jerks up and the eyes snap open.
                float progress = Math.min(1, Math.max(0, (now - stateSince) / DROWSY_RAMP_MS));
                float phase = reducedMotion ? 0 : (time % 3.4f) / 3.4f;
                float lid = phase < .72f ? phase / .72f : phase < .82f ? 1 - (phase - .72f) / .1f : 0;
                t[LID] = Math.min(.92f, .22f + .25f * progress + lid * (.15f + .4f * progress));
                t[LOOK_Y] = .03f * progress + lid * .05f * progress;
                t[SCALE] = .97f; t[BREATH] = 1.6f;
                break;
            }
            case ASLEEP: t[CUT_UP] = 1; t[LOOK_Y] = .1f; t[SCALE] = .97f; t[ZZ] = 1; t[BREATH] = 3; break;
            default: break;
        }
        switch (reaction) {
            case SAD: case CRY:
                t[DROOP] = 1; t[LOOK_Y] = .1f; t[HEIGHT] = .86f; t[THINK] = t[SPEAK] = t[CUT_UP] = t[ZZ] = t[LID] = 0;
                if (reaction == FaceReaction.CRY) t[TEAR] = 1; else System.arraycopy(SAD_BLUE, 0, color, 0, 3);
                break;
            case LOVE: t[HEART] = 1; break;
            case SILLY:
                t[CUT_DOWN] = 1; t[LOOK_Y] = -.03f; t[THINK] = t[SPEAK] = t[CUT_UP] = t[ZZ] = t[LID] = 0;
                break;
            default: break; // The other reactions are intentionally not drawn for now.
        }
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); drawnFrames++;
        long now = SystemClock.uptimeMillis();
        if (reactionUntil > 0 && now > reactionUntil) { reaction = FaceReaction.NONE; reactionUntil = 0; }
        boolean snap = reducedMotion || lastDraw == 0;
        float dt = snap ? 0 : Math.min(100, now - lastDraw) / 1000f;
        lastDraw = now;
        float size = Math.min(getWidth() / 4.6f, getHeight() / 2.6f);
        if (size <= 0) return;
        float time = reducedMotion ? 0 : (now % 120000) / 1000f;

        computeTargets(time, now, colorTarget);
        float startle = (!reducedMotion && startleAt > 0) ? (float)Math.exp(-(now - startleAt) / 450f) : 0;
        if (startle > .05f) {
            target[CUT_UP] = target[LID] = 0; target[HEIGHT] = 1;
        }
        target[SCALE] *= 1 + .2f * startle; target[GAP] += .12f * startle; target[LOOK_Y] -= .06f * startle;
        for (int i = 0; i < COUNT; i++) {
            float tau = startle > .05f && (i == CUT_UP || i == LID || i == HEIGHT) ? .05f : TAU[i];
            current[i] += snap ? target[i] - current[i] : (target[i] - current[i]) * (1 - (float)Math.exp(-dt / tau));
        }
        for (int i = 0; i < 3; i++)
            rgb[i] += snap ? colorTarget[i] - rgb[i] : (colorTarget[i] - rgb[i]) * (1 - (float)Math.exp(-dt / .3f));
        int faceColor = Color.rgb(Math.round(rgb[0]), Math.round(rgb[1]), Math.round(rgb[2]));

        float breath = reducedMotion ? 0
            : (float)Math.sin(time * (1.7f - .5f * Math.min(1, current[BREATH] - 1))) * size * .025f * current[BREATH];
        float gaze = reducedMotion ? 0 : (float)Math.sin(time * .48f) * size * .055f;
        float jitter = startle > .02f ? (float)Math.sin(time * 60) * size * .018f * startle : 0;
        float cx = getWidth() / 2f + gaze + jitter, cy = getHeight() * .44f + breath;
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
        // No blinking once the lids are already coming down.
        blink = 1 - (1 - blink) * (1 - Math.min(1, Math.max(current[CUT_UP], current[LID]) * 3));

        float radius = size * .6f * current[SCALE] * (1 - .05f * current[CUT_DOWN]);
        float gap = size * .66f * current[GAP];
        // Thinking: looks diagonally up to one side, holds, then to the other. Eases in and out with THINK.
        float think = current[THINK];
        float pose = reducedMotion ? .8f : Math.max(-1, Math.min(1, (float)Math.sin(time * 1.1f) * 2.2f));
        float turn = Math.abs(pose);
        float x0 = cx + pose * size * .32f * think, thinkUp = -(.04f + .2f * turn) * size * think;
        float narrowLeft = pose < 0 ? 1 - .4f * turn * think : 1, narrowRight = pose > 0 ? 1 - .4f * turn * think : 1;
        // Speaking: small nods, side glances and a soft rhythm. Eases in and out with SPEAK.
        float speak = current[SPEAK];
        float bob = reducedMotion ? 0
            : ((float)Math.sin(time * 5.2f) * size * .035f + (float)Math.sin(time * 8.7f + 1) * size * .015f) * speak;
        float sway = reducedMotion ? 0
            : ((float)Math.sin(time * 1.7f) * size * .1f + (float)Math.sin(time * 3.1f + .7f) * size * .05f) * speak;
        float pulse = reducedMotion ? 1 : 1 - .07f * (.5f + .5f * (float)Math.sin(time * 9.5f)) * speak;
        float hop = reducedMotion ? 0 : -Math.abs((float)Math.sin(time * 6.5f)) * size * .07f * current[CUT_DOWN];
        float y = cy + current[LOOK_Y] * size + thinkUp + bob + hop;
        float scaleY = Math.max(.05f, blink * current[HEIGHT] * pulse);
        float leftX = x0 + sway - gap + radius * (1 - narrowLeft), rightX = x0 + sway + gap - radius * (1 - narrowRight);

        paint.setStyle(Paint.Style.FILL); paint.setColor(faceColor); paint.setAlpha(255);
        drawEye(canvas, leftX, y, radius, narrowLeft, scaleY, -1);
        drawEye(canvas, rightX, y, radius, narrowRight, scaleY, 1);

        if (current[HEART] > .01f) {
            float s = size * .14f * (.6f + .4f * current[HEART]);
            float floatY = reducedMotion ? 0 : (float)Math.sin(time * 2) * size * .02f;
            paint.setColor(faceColor); paint.setAlpha(Math.round(255 * current[HEART]));
            drawHeart(canvas, cx - size * 1.7f, cy - size * .35f + floatY, s);
            drawHeart(canvas, cx + size * 1.7f, cy - size * .45f - floatY, s * .8f);
        }
        if (current[TEAR] > .01f) {
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < 2; k++) {
                    float phase = reducedMotion ? .35f : ((time + k * .8f + (side > 0 ? .4f : 0)) % 1.6f) / 1.6f;
                    paint.setColor(faceColor);
                    paint.setAlpha(Math.round(255 * Math.max(0, 1 - phase * phase) * .95f * current[TEAR]));
                    drawTear(canvas, cx + side * (gap + radius * .44f), y + radius * scaleY * .7f + phase * size * 1.1f, size * .075f);
                }
            }
        }
        if (current[ZZ] > .01f) {
            // Z's float up from the right side while asleep.
            paint.setColor(faceColor);
            for (int k = 0; k < 3; k++) {
                float phase = reducedMotion ? (k + .5f) / 3 : (time * .32f + k / 3f) % 1;
                paint.setAlpha(Math.round(255 * current[ZZ] * (float)Math.sin(phase * Math.PI) * .95f));
                paint.setTextSize(size * (.2f + .3f * phase));
                int mark = canvas.save();
                canvas.translate(cx + gap + radius + size * (.25f + phase * .5f), y - size * (.05f + phase * .6f));
                canvas.rotate(-10.3f);
                canvas.drawText("Z", 0, 0, paint);
                canvas.restoreToCount(mark);
            }
        }
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(saved);
    }

    /**
     * One eye that morphs: round, droopy (sad), crescent opening down (laugh), lidded (drowsy) or a lying
     * parenthesis (asleep). Shapes are built with Path.op so the edges stay anti-aliased.
     */
    private void drawEye(Canvas canvas, float x, float y, float radius, float scaleX, float scaleY, int side) {
        float cutDown = current[CUT_DOWN], cutUp = current[CUT_UP], droop = current[DROOP], lid = current[LID];
        int saved = canvas.save();
        canvas.translate(x, y); canvas.scale(scaleX, scaleY);
        if (cutUp > .002f) {
            // Closed eye: a round stroke that starts as a dot as wide as the eye and stretches into "‿".
            float thickness = radius * (1 - .88f * cutUp), half = radius - thickness, sag = .4f * radius * cutUp;
            if (lid > .002f) canvas.clipRect(-radius * 20, radius * (-1 + 1.5f * lid), radius * 20, radius * 20);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(thickness * 2);
            path.reset(); path.moveTo(-half, -sag / 2); path.quadTo(0, -sag / 2 + 2 * sag, half, -sag / 2);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            path.reset(); path.addCircle(0, 0, radius, Path.Direction.CW);
            if (cutDown > .002f) {
                cut.reset(); cut.addCircle(0, radius * (2 - 1.45f * cutDown), radius * .92f, Path.Direction.CW);
                path.op(cut, Path.Op.DIFFERENCE);
            }
            if (droop > .002f) {
                float outer = side * radius, inner = -side * radius;
                float outerY = radius * (-1.3f + .84f * droop), innerY = radius * (-1.3f + .4f * droop);
                clip.reset();
                clip.moveTo(outer + side * 2, outerY); clip.lineTo(inner - side * 2, innerY);
                clip.lineTo(inner - side * 2, radius * 2); clip.lineTo(outer + side * 2, radius * 2); clip.close();
                path.op(clip, Path.Op.INTERSECT);
            }
            if (lid > .002f) {
                clip.reset(); clip.addRect(-radius * 20, radius * (-1 + 1.5f * lid), radius * 20, radius * 20, Path.Direction.CW);
                path.op(clip, Path.Op.INTERSECT);
            }
            canvas.drawPath(path, paint);
        }
        canvas.restoreToCount(saved);
    }

    private void drawHeart(Canvas canvas, float x, float y, float size) {
        path.reset();
        path.moveTo(x, y + size * .35f);
        path.cubicTo(x - size * 1.1f, y - size * .4f, x - size * .4f, y - size * 1.1f, x, y - size * .45f);
        path.cubicTo(x + size * .4f, y - size * 1.1f, x + size * 1.1f, y - size * .4f, x, y + size * .35f);
        canvas.drawPath(path, paint);
    }

    /** Teardrop: pointed top, round bottom. */
    private void drawTear(Canvas canvas, float x, float y, float s) {
        path.reset();
        path.moveTo(x, y - s * 2);
        path.cubicTo(x + s * .3f, y - s * 1.1f, x + s, y - s * .5f, x + s, y + s * .1f);
        bounds.set(x - s, y + s * .1f - s, x + s, y + s * .1f + s);
        path.arcTo(bounds, 0, 180);
        path.cubicTo(x - s, y - s * .5f, x - s * .3f, y - s * 1.1f, x, y - s * 2);
        canvas.drawPath(path, paint);
    }
}
