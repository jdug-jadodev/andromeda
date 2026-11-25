package local.doja.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/** Plays the trip-to-Huila scene over everything for its 15 s, then hides. About 30 frames a second. */
final class HuilaView extends View implements Choreographer.FrameCallback {
    private final HuilaArt art = new HuilaArt();
    private long start, lastFrame, drawnFrames;
    private int days;
    private Runnable done;
    private boolean scheduled;

    HuilaView(Context context) {
        super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setVisibility(GONE);
    }

    void play(int days, Runnable done) {
        this.days = days; this.done = done; start = SystemClock.uptimeMillis();
        setVisibility(VISIBLE); invalidate();
        if (!scheduled) { scheduled = true; lastFrame = 0; Choreographer.getInstance().postFrameCallback(this); }
    }

    void stop() {
        start = 0; done = null; setVisibility(GONE);
        scheduled = false; Choreographer.getInstance().removeFrameCallback(this);
        art.release();
    }

    boolean isPlaying() { return start != 0; }
    long getDrawnFrames() { return drawnFrames; }

    protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    public void doFrame(long nanos) {
        if (!scheduled) return;
        if (lastFrame == 0 || nanos - lastFrame >= 32000000L) { lastFrame = nanos; invalidate(); }
        Choreographer.getInstance().postFrameCallback(this);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (start == 0 || getWidth() <= 0 || getHeight() <= 0) return;
        drawnFrames++;
        float seconds = (SystemClock.uptimeMillis() - start) / 1000f;
        art.draw(canvas, Math.min(seconds, HuilaArt.END), days, getWidth(), getHeight());
        if (seconds >= HuilaArt.END) {
            Runnable finished = done; stop();
            if (finished != null) post(finished);
        }
    }
}
