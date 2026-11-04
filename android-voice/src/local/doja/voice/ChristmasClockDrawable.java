package local.doja.voice;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * Wreath and reindeer on the clock screen. It is the background of the clock layer, so it never changes that
 * layout, stays behind its text and falls with the clock. Only the bells, the eyes and the nose are redrawn.
 */
final class ChristmasClockDrawable extends Drawable {
    private final ChristmasArt art = new ChristmasArt();
    private final Runnable tick = () -> { invalidateSelf(); schedule(); };
    private Bitmap still;
    private boolean running;

    void setRunning(boolean value) { running = value; schedule(); invalidateSelf(); }

    private void schedule() {
        unscheduleSelf(tick);
        if (!running || !isVisible()) return;
        long now = SystemClock.uptimeMillis();
        scheduleSelf(tick, now + ChristmasArt.clockFrameDelayMs(now / 1000.0));
    }

    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        schedule();
        return changed;
    }

    protected void onBoundsChange(Rect bounds) { still = null; }

    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) return;
        if (still == null) {
            still = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            art.paintClockStill(new Canvas(still), width, height);
        }
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.drawBitmap(still, 0, 0, null);
        art.drawClockLive(canvas, width, height, running ? SystemClock.uptimeMillis() / 1000.0 : 8, !running);
        canvas.restoreToCount(saved);
    }

    public void setAlpha(int alpha) {}
    public void setColorFilter(ColorFilter filter) {}
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
