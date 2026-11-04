package local.doja.voice;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.View;

/** Christmas scene behind the face. Only exists while navidad is on; only the snow moves. */
final class ChristmasBackdropView extends View implements Choreographer.FrameCallback {
    private final ChristmasArt art = new ChristmasArt();
    private Bitmap sky, scene;
    private boolean attached, foreground, scheduled, reducedMotion;
    private long lastFrame;

    ChristmasBackdropView(Context context) {
        super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setVisibility(GONE);
    }

    void show(long fadeMs) {
        animate().cancel();
        if (getVisibility() != VISIBLE) { setAlpha(fadeMs > 0 ? 0 : 1); setVisibility(VISIBLE); }
        if (fadeMs > 0) animate().alpha(1).setDuration(fadeMs).start(); else setAlpha(1);
    }

    void hide(long fadeMs) {
        animate().cancel();
        if (getVisibility() != VISIBLE) return;
        if (fadeMs > 0) animate().alpha(0).setDuration(fadeMs).withEndAction(() -> setVisibility(GONE)).start();
        else setVisibility(GONE);
    }

    boolean isShowing() { return getVisibility() == VISIBLE; }

    void setForeground(boolean value) {
        foreground = value;
        if (value) reducedMotion = Settings.Global.getFloat(getContext().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0;
        updateScheduling();
    }

    protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; updateScheduling(); }
    protected void onDetachedFromWindow() { attached = false; updateScheduling(); super.onDetachedFromWindow(); }
    protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateScheduling(); }
    protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); updateScheduling(); }
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight); sky = scene = null;
    }

    private void updateScheduling() {
        boolean animate = attached && foreground && isShown() && getWindowVisibility() == VISIBLE && !reducedMotion;
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

    private void paintLayers(int width, int height) {
        sky = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        scene = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        art.paintSky(new Canvas(sky), width, height);
        art.paintScene(new Canvas(scene), width, height);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (sky == null) paintLayers(width, height);
        double t = reducedMotion ? 8 : SystemClock.uptimeMillis() / 1000.0;
        canvas.drawBitmap(sky, 0, 0, null);
        art.snowBehind(canvas, t, width, height);
        canvas.drawBitmap(scene, 0, 0, null);
        art.snowInFront(canvas, t, width, height);
    }
}
