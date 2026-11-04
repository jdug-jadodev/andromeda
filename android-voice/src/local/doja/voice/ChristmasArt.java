package local.doja.voice;

import android.graphics.*;

/**
 * Christmas drawings: same shapes and numbers as artifacts/andromeda-face-preview.html (navidad).
 * Sizes are in u = 1% of the height and positions in fractions of the width, so they fit any landscape ratio.
 * Presentation only. No audio, network, worker thread or per-frame bitmap.
 */
final class ChristmasArt {
    private interface Drawing { void draw(Canvas canvas); }
    private static final class Flake {
        final float x, y, r, fall, phase, alpha;
        Flake(float x, float y, float r, float fall, float phase, float alpha) {
            this.x = x; this.y = y; this.r = r; this.fall = fall; this.phase = phase; this.alpha = alpha;
        }
    }
    private static final Flake[] FAR = flakes(120, 11, .22f, .5f, .055f, .6f), NEAR = flakes(70, 907, .5f, 1.05f, .1f, .92f);

    private static final class Gift {
        final float x, at, size, tall, spin, rest, tilt;
        final int color, dark, ribbon;
        Gift(int i) {
            x = (float)hash(i + .12); at = 5.5f + (float)hash(i + .22) * 8.2f; size = 5.5f + (float)hash(i + .32) * 6;
            tall = .72f + .45f * (float)hash(i + .42); spin = ((float)hash(i + .52) - .5f) * 6;
            rest = .84f + .13f * (float)hash(i + .62); tilt = ((float)hash(i + .72) - .5f) * .4f;
            int[] colors = GIFT_COLORS[i % GIFT_COLORS.length]; color = colors[0]; dark = colors[1]; ribbon = colors[2];
        }
    }
    private static final int[][] GIFT_COLORS = {{0xffd8303f, 0xff9c1a2b, 0xfff2c552}, {0xff2c9a5b, 0xff1a6a3e, 0xfff4f8ff},
        {0xff3f7fd8, 0xff2553a0, 0xfff4f8ff}, {0xfff2c552, 0xffc8942a, 0xffc8283b}, {0xff9a5bd8, 0xff6a3aa0, 0xfff2c552}};
    // From the far ones to the near ones, so the pile on the ground stacks by itself.
    private static final Gift[] RAIN = gifts();
    private static final Xfermode ADD = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private static final Typeface BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    // Live parts redraw often, so their gradients (in local units) are built once.
    private final Shader gold = new LinearGradient(-.56f, 0, .56f, 0, new int[]{0xff8a5e12, 0xffffe9a6, 0xffe8b53a, 0xff7c5210},
        new float[]{0, .32f, .6f, 1}, Shader.TileMode.CLAMP);
    private final Shader bowRed = new LinearGradient(0, -14, 0, 16, 0xffe8384a, 0xffa01526, Shader.TileMode.CLAMP);
    private final Shader nose = new RadialGradient(-3, 5, 13, new int[]{0xffff9a9a, 0xffe4202f, 0xff8e0f1c},
        new float[]{0, .4f, 1}, Shader.TileMode.CLAMP);
    private final Shader noseGlow = new RadialGradient(0, 8, 21, 0xffff4646, 0x00ff4646, Shader.TileMode.CLAMP);

    private static double hash(double n) {
        double value = Math.sin(n * 12.9898) * 43758.5453;
        return value - Math.floor(value);
    }

    private static Flake[] flakes(int count, int seed, float r0, float r1, float fall, float alpha) {
        Flake[] list = new Flake[count];
        for (int i = 0; i < count; i++) {
            list[i] = new Flake((float)hash(seed + i), (float)hash(seed + i + .37), r0 + (r1 - r0) * (float)hash(seed + i + .71),
                fall * (.75f + .5f * (float)hash(seed + i + .13)), (float)hash(seed + i + .53) * 6.283f, alpha);
        }
        return list;
    }

    private static Gift[] gifts() {
        Gift[] list = new Gift[60];
        for (int i = 0; i < list.length; i++) list[i] = new Gift(i);
        java.util.Arrays.sort(list, (a, b) -> Float.compare(a.rest, b.rest));
        return list;
    }

    private static float degrees(float radians) { return (float)Math.toDegrees(radians); }
    private static float sin(double value) { return (float)Math.sin(value); }
    private static float cos(double value) { return (float)Math.cos(value); }

    private void fill(int color) { paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color); }
    private void shade(Shader shader) { paint.setColor(0xff000000); paint.setStyle(Paint.Style.FILL); paint.setShader(shader); }
    private void stroke(int color, float width) {
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }
    private void ellipse(Canvas c, float x, float y, float rx, float ry, float radians) {
        c.save(); c.translate(x, y); c.rotate(degrees(radians));
        oval.set(-rx, -ry, rx, ry); c.drawOval(oval, paint); c.restore();
    }
    private void place(Canvas c, float x, float y, float scale, Drawing drawing) {
        c.save(); c.translate(x, y); c.scale(scale, scale); drawing.draw(c); c.restore();
    }
    /** Soft light from the upper left, the way a lit sphere looks. */
    private static Shader sphere(float x, float y, float r, int[] colors, float[] stops) {
        return new RadialGradient(x - r * .35f, y - r * .4f, r * 1.45f, colors, stops, Shader.TileMode.CLAMP);
    }

    // --- The scene behind her: painted once into two layers; only the snow moves. ---
    private static float ground(float x, float W, float H) {
        return H * (.8f + .03f * sin(x / W * 5 + .6) + .012f * sin(x / W * 13));
    }

    private void hill(Canvas c, float W, float H, float base, float amp, float freq, float phase, int color) {
        fill(color); path.reset(); path.moveTo(0, H);
        for (int i = 0; i <= 80; i++) { float x = W * i / 80; path.lineTo(x, H * (base + amp * sin(x / W * freq + phase))); }
        path.lineTo(W, H); path.close(); c.drawPath(path, paint);
    }

    void paintSky(Canvas c, float W, float H) {
        shade(new LinearGradient(0, 0, 0, H * .8f, new int[]{Color.rgb(7, 12, 28), Color.rgb(17, 29, 62), Color.rgb(38, 58, 104)},
            new float[]{0, .6f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        fill(Color.WHITE);
        for (int i = 0; i < 90; i++) {
            paint.setAlpha(Math.round(255 * (.25f + .65f * (float)hash(i + .2))));
            c.drawCircle((float)hash(i + .4) * W, (float)hash(i + .6) * H * .58f, H * (.0012f + .0028f * (float)hash(i + .8)), paint);
        }
        hill(c, W, H, .68f, .035f, 7, 1.2f, Color.rgb(40, 60, 108));
        hill(c, W, H, .73f, .03f, 9, 3.4f, Color.rgb(74, 98, 152));
    }

    private void ball(Canvas c, float x, float y, float r) {
        shade(sphere(x, y, r, new int[]{0xffffffff, 0xffe6eefc, 0xffb4c7e6}, new float[]{0, .45f, 1}));
        c.drawCircle(x, y, r, paint);
    }

    private void stick(Canvas c, float x0, float y0, float x1, float y1) {
        stroke(0xff5b3a24, 1.1f); c.drawLine(x0, y0, x1, y1, paint);
    }

    /** Origin at the ground under the snowman. */
    private void snowman(Canvas c) {
        stick(c, -9, -36, -21, -44); stick(c, -17.5f, -41.5f, -20, -38); stick(c, -17.5f, -41.5f, -18, -46);
        stick(c, 9, -37, 20, -50); stick(c, 16.5f, -46, 20.5f, -44.5f); stick(c, 16.5f, -46, 15.5f, -51);
        ball(c, 0, -13, 15); ball(c, 0, -34.5f, 11); ball(c, 0, -51, 8);
        fill(0xff20262f);
        for (float y : new float[]{-39, -33.5f, -28}) c.drawCircle(0, y, 1.1f, paint);
        c.drawCircle(-2.8f, -53, .95f, paint); c.drawCircle(2.8f, -53, .95f, paint);
        for (int i = -2; i <= 2; i++) c.drawCircle(i * 1.5f, -47.6f + (1 - i * i / 4f) * 1.2f, .45f, paint);
        shade(new LinearGradient(0, 0, 9, 0, 0xfff08a24, 0xffd4600f, Shader.TileMode.CLAMP));
        path.reset(); path.moveTo(.4f, -52.2f); path.lineTo(.4f, -49.8f); path.lineTo(9.5f, -50.6f); path.close(); c.drawPath(path, paint);
        // Scarf: a band round the neck and one tail.
        fill(0xffc8283b);
        c.save(); c.translate(4.5f, -41); c.rotate(degrees(.14f));
        c.drawRoundRect(-2.6f, 0, 2.6f, 15, .8f, .8f, paint);
        fill(0xfff3f6ff); c.drawRect(-2.6f, 8, 2.6f, 9.4f, paint); c.drawRect(-2.6f, 11, 2.6f, 12.4f, paint);
        c.restore();
        fill(0xffc8283b); path.reset(); path.moveTo(-8.6f, -45.5f);
        path.quadTo(0, -42.4f, 8.6f, -45.5f); path.lineTo(8.8f, -41); path.quadTo(0, -37.8f, -8.8f, -41); path.close(); c.drawPath(path, paint);
        fill(0xffa81f30); path.reset(); path.moveTo(-8.8f, -42); path.quadTo(0, -38.8f, 8.8f, -42);
        path.lineTo(8.8f, -41); path.quadTo(0, -37.8f, -8.8f, -41); path.close(); c.drawPath(path, paint);
        // Hat, slightly tilted.
        c.save(); c.translate(0, -57.5f); c.rotate(degrees(-.1f));
        fill(0xff1b2029); c.drawRoundRect(-6.6f, -14.5f, 6.6f, 0, 1.2f, 1.2f, paint);
        fill(0xffc8283b); c.drawRect(-6.6f, -3.6f, 6.6f, -.8f, paint);
        fill(0xff1b2029); ellipse(c, 0, 0, 10.6f, 1.9f, 0);
        fill(0xfff4f8ff); ellipse(c, .4f, -14.6f, 6.4f, 1.3f, 0);
        c.restore();
    }

    /** x is the centre of the box and y the floor it stands on. */
    private void gift(Canvas c, float x, float y, float w, float h, int color, int dark, int ribbon, boolean withBow) {
        shade(new LinearGradient(x - w / 2, 0, x + w / 2, 0, color, dark, Shader.TileMode.CLAMP));
        c.drawRoundRect(x - w / 2, y - h, x + w / 2, y, .5f, .5f, paint);
        fill(ribbon); c.drawRect(x - w * .08f, y - h, x + w * .08f, y, paint);
        fill(dark); c.drawRoundRect(x - w * .54f, y - h - h * .1f, x + w * .54f, y - h + h * .14f, .4f, .4f, paint);
        fill(ribbon); c.drawRect(x - w * .08f, y - h - h * .1f, x + w * .08f, y - h + h * .14f, paint);
        if (!withBow) return;
        float by = y - h - h * .1f, s = Math.min(w, h) * .2f;
        for (int side = -1; side <= 1; side += 2) ellipse(c, x + side * s * .85f, by - s * .35f, s, s * .55f, side * .45f);
        c.drawCircle(x, by - s * .1f, s * .34f, paint);
    }

    private void gifts(Canvas c) {
        gift(c, -7, 0, 18, 15, 0xffd8303f, 0xff9c1a2b, 0xfff2c552, false);
        gift(c, -8, -16.5f, 10, 8, 0xff3f7fd8, 0xff2553a0, 0xfff4f8ff, true);
        gift(c, 10, 0, 14, 11, 0xff2c9a5b, 0xff1a6a3e, 0xfff4f8ff, true);
        gift(c, 2, 2.5f, 9, 7, 0xfff2c552, 0xffc8942a, 0xffc8283b, true);
    }

    private void runner(Canvas c, int color, float width) {
        stroke(color, width);
        path.reset(); path.moveTo(30, -1.5f); path.lineTo(-20, -1.5f);
        path.cubicTo(-28, -1.5f, -33, -5, -33, -10); path.cubicTo(-33, -14, -27, -15, -26, -11);
        c.drawPath(path, paint);
        c.drawLine(-12, -1.5f, -12, -9, paint); c.drawLine(18, -1.5f, 18, -9, paint);
    }

    private void sleighBody() {
        path.reset(); path.moveTo(27, -8);
        path.cubicTo(31, -12, 33, -22, 34, -31); path.cubicTo(34.5f, -35, 29, -36, 27.5f, -32);
        path.cubicTo(25, -24, 18, -19.5f, 10, -19); path.lineTo(-12, -19);
        path.cubicTo(-20, -19, -24, -23, -28, -27); path.cubicTo(-31, -30, -36, -27, -34, -23);
        path.cubicTo(-31, -15, -24, -8, -14, -8); path.close();
    }

    /** Origin at the ground under the sleigh, which points left, toward the middle of the screen. */
    private void sleigh(Canvas c) {
        c.save(); c.translate(4, -1.2f); runner(c, 0xffa87c28, 1.4f); c.restore();
        gift(c, -8, -16, 12, 10, 0xff2c9a5b, 0xff1a6a3e, 0xfff2c552, true);
        gift(c, 15, -16, 11, 13, 0xff3f7fd8, 0xff2553a0, 0xfff4f8ff, true);
        gift(c, 4, -16, 11, 7, 0xfff2c552, 0xffc8942a, 0xffc8283b, true);
        runner(c, 0xffe3b54a, 1.8f);
        shade(new LinearGradient(0, -34, 0, -8, 0xffe23a4a, 0xff8e1526, Shader.TileMode.CLAMP));
        sleighBody(); c.drawPath(path, paint);
        stroke(0xffe3b54a, .9f); sleighBody(); c.drawPath(path, paint);
        stroke(0xffe3b54a, .7f);
        path.reset(); path.moveTo(-19, -12.5f); path.cubicTo(-8, -17, 4, -9, 14, -13); path.cubicTo(19, -15, 20, -11, 17, -11);
        c.drawPath(path, paint);
        fill(0xfff4f8ff);
        ellipse(c, 31, -33.5f, 3.6f, 1.5f, -.1f); ellipse(c, -31.5f, -27.5f, 3, 1.2f, .4f);
    }

    void paintScene(Canvas c, float W, float H) {
        float u = H / 100;
        shade(new LinearGradient(0, H * .74f, 0, H, 0xffe4edfc, 0xffa9bde0, Shader.TileMode.CLAMP));
        path.reset(); path.moveTo(0, H);
        for (int i = 0; i <= 120; i++) { float x = W * i / 120; path.lineTo(x, ground(x, W, H)); }
        path.lineTo(W, H); path.close(); c.drawPath(path, paint);
        float manX = W * .13f, giftX = W * .315f, sleighX = W * .77f;
        float manY = ground(manX, W, H), giftY = ground(giftX, W, H) + u * 3, sleighY = ground(sleighX, W, H) + u * 4;
        fill(0x735c78b0);
        ellipse(c, manX, manY + u * 1.5f, u * 19, u * 2.8f, 0);
        ellipse(c, giftX, giftY + u * 3, u * 21, u * 2.2f, 0);
        ellipse(c, sleighX, sleighY, u * 36, u * 2.2f, 0);
        place(c, sleighX, sleighY, u, this::sleigh);
        place(c, giftX, giftY, u, this::gifts);
        place(c, manX, manY, u, this::snowman);
    }

    private void snowfall(Canvas c, Flake[] list, double t, float W, float H) {
        float u = H / 100;
        fill(Color.WHITE);
        for (Flake f : list) {
            float y = (float)((f.y + t * f.fall) % 1) * (H + 4 * u) - 2 * u;
            float x = (float)((f.x + t * .006 + 1) % 1) * W + sin(t * .8 + f.phase) * u * 1.6f;
            paint.setAlpha(Math.round(255 * f.alpha));
            c.drawCircle(x, y, f.r * u, paint);
        }
    }
    void snowBehind(Canvas c, double t, float W, float H) { snowfall(c, FAR, t, W, H); }
    void snowInFront(Canvas c, double t, float W, float H) { snowfall(c, NEAR, t, W, H); }

    // --- Clock screen: wreath on the left, reindeer on the right; the time and everything else stay clear. ---
    private void holly(Canvas c, float x, float y, float angle, float s) {
        c.save(); c.translate(x, y); c.rotate(degrees(angle)); c.scale(s, s);
        path.reset(); path.moveTo(0, 0);
        path.quadTo(.1f, -.2f, .22f, -.22f); path.quadTo(.3f, -.11f, .4f, -.24f); path.quadTo(.52f, -.13f, .62f, -.22f);
        path.quadTo(.74f, -.1f, 1, 0);
        path.quadTo(.74f, .1f, .62f, .22f); path.quadTo(.52f, .13f, .4f, .24f); path.quadTo(.3f, .11f, .22f, .22f);
        path.quadTo(.1f, .2f, 0, 0); path.close();
        fill(0xff1f8a4c); c.drawPath(path, paint);
        stroke(0x8cbef0c8, .025f); c.drawLine(.05f, 0, .92f, 0, paint);
        c.restore();
    }

    private void berry(Canvas c, float x, float y, float r) {
        shade(sphere(x, y, r, new int[]{0xffff8a8a, 0xffe0202f, 0xff8e0f1c}, new float[]{0, .4f, 1}));
        c.drawCircle(x, y, r, paint);
    }

    private void ornament(Canvas c, float x, float y, float r) {
        shade(sphere(x, y, r, new int[]{0xfffff3c4, 0xfff0bf45, 0xff9a6a14}, new float[]{0, .4f, 1}));
        c.drawCircle(x, y, r, paint);
    }

    private void wreath(Canvas c, float x, float y, float R, float H) {
        float inner = R * .58f, mid = (R + inner) / 2;
        // Ribbon it hangs from.
        fill(0xffb81d2e); c.drawRect(x - H * .012f, 0, x + H * .012f, y - R * .8f, paint);
        fill(0xffe3b54a); c.drawRect(x - H * .012f, 0, x - H * .009f, y - R * .8f, paint);
        c.drawRect(x + H * .009f, 0, x + H * .012f, y - R * .8f, paint);
        stroke(0xff123d28, R - inner); c.drawCircle(x, y, mid, paint);
        int[] greens = {0xff14503a, 0xff1b6b45, 0xff268a55, 0xff3aa76a};
        for (int layer = 0; layer < 4; layer++) {
            stroke(greens[layer], H * (.0052f - layer * .0006f));
            for (int i = 0; i < 170; i++) {
                int k = layer * 170 + i;
                float a = (float)hash(k + .11) * 6.283f, r = inner + (R - inner) * (.05f + .95f * (float)hash(k + .31));
                float dir = a + 1.57f + ((float)hash(k + .51) - .5f) * 1.5f, length = H * (.024f + .022f * (float)hash(k + .71));
                float px = x + cos(a) * r, py = y + sin(a) * r;
                c.drawLine(px, py, px + cos(dir) * length, py + sin(dir) * length, paint);
            }
        }
        // Frost on the tips.
        fill(0xbfebf5ff);
        for (int i = 0; i < 70; i++) {
            float a = (float)hash(i + .17) * 6.283f, r = inner + (R - inner) * (float)hash(i + .37);
            c.drawCircle(x + cos(a) * r, y + sin(a) * r, H * (.0012f + .0016f * (float)hash(i + .57)), paint);
        }
        for (int i = 0; i < 7; i++) {
            float a = -1.57f + i * .9f, px = x + cos(a) * mid, py = y + sin(a) * mid;
            if (sin(a) > .75f) continue;   // the bow owns the bottom
            holly(c, px, py, a + .9f, H * .06f); holly(c, px, py, a + 2.5f, H * .06f); holly(c, px, py, a - .7f, H * .052f);
            berry(c, px - H * .007f, py - H * .004f, H * .0115f); berry(c, px + H * .009f, py - H * .003f, H * .0105f);
            berry(c, px + H * .001f, py + H * .009f, H * .0105f);
        }
        for (int i = 0; i < 6; i++) {
            float a = -1.12f + i * .9f, px = x + cos(a) * (mid + R * .06f), py = y + sin(a) * (mid + R * .06f);
            if (sin(a) > .75f) continue;
            ornament(c, px, py, H * .0135f);
        }
    }

    private void bow(Canvas c, float x, float y, float s) {
        c.save(); c.translate(x, y); c.scale(s, s);
        for (int side = -1; side <= 1; side += 2) {
            path.reset(); path.moveTo(side * 2, 2); path.lineTo(side * 7, 24); path.lineTo(side * 11, 19);
            path.lineTo(side * 15, 25); path.lineTo(side * 7, 1); path.close();
            shade(bowRed); c.drawPath(path, paint); stroke(0xffe3b54a, .7f); c.drawPath(path, paint);
            path.reset(); path.moveTo(0, 0);
            path.cubicTo(side * 6, -13, side * 21, -13, side * 20, -2); path.cubicTo(side * 20, 8, side * 7, 7, 0, 0); path.close();
            shade(bowRed); c.drawPath(path, paint); stroke(0xffe3b54a, .7f); c.drawPath(path, paint);
            path.reset(); path.moveTo(side * 3, 0);
            path.cubicTo(side * 7, -6, side * 14, -6, side * 14, -2); path.cubicTo(side * 14, 2, side * 7, 3, side * 3, 0); path.close();
            fill(0x735a0610); c.drawPath(path, paint);
        }
        shade(bowRed); c.drawRoundRect(-4.2f, -4.6f, 4.2f, 4.6f, 2.4f, 2.4f, paint);
        stroke(0xffe3b54a, .7f); c.drawRoundRect(-4.2f, -4.6f, 4.2f, 4.6f, 2.4f, 2.4f, paint);
        c.restore();
    }

    /** Pivot at x,y; swings by angle. The bell hangs a cord below the pivot. */
    private void bell(Canvas c, float x, float y, float s, float cord, float angle) {
        c.save(); c.translate(x, y); c.rotate(degrees(angle));
        stroke(0xffd9a93a, s * .05f); c.drawLine(0, 0, 0, cord, paint);
        c.translate(0, cord); c.scale(s, s);
        stroke(0xffd9a93a, .07f); c.drawCircle(0, -.03f, .1f, paint);
        c.save(); c.translate(0, 1.05f); c.rotate(degrees(-angle * 1.8f));
        fill(0xff7a5210); c.drawCircle(0, 0, .1f, paint); c.restore();
        path.reset(); path.moveTo(0, .04f);
        path.cubicTo(.28f, .04f, .34f, .28f, .36f, .52f); path.cubicTo(.38f, .72f, .5f, .82f, .56f, .93f);
        path.quadTo(0, 1.06f, -.56f, .93f); path.cubicTo(-.5f, .82f, -.38f, .72f, -.36f, .52f); path.cubicTo(-.34f, .28f, -.28f, .04f, 0, .04f);
        path.close(); shade(gold); c.drawPath(path, paint);
        stroke(0xff7c5210, .05f); path.reset(); path.moveTo(-.5f, .84f); path.quadTo(0, .96f, .5f, .84f); c.drawPath(path, paint);
        fill(0xa6ffffff); ellipse(c, -.15f, .4f, .05f, .2f, .12f);
        c.restore();
    }

    private void antler(Canvas c, int side) {
        stroke(0xffc99a63, 4.2f);
        path.reset(); path.moveTo(side * 13, -30); path.cubicTo(side * 16, -44, side * 26, -50, side * 36, -66); c.drawPath(path, paint);
        path.reset(); path.moveTo(side * 21, -47); path.quadTo(side * 19, -58, side * 21, -68); c.drawPath(path, paint);
        path.reset(); path.moveTo(side * 28, -54); path.quadTo(side * 37, -54, side * 46, -56); c.drawPath(path, paint);
    }

    /** Origin at the middle of the head. */
    private void reindeer(Canvas c) {
        antler(c, -1); antler(c, 1);
        for (int side = -1; side <= 1; side += 2) {
            c.save(); c.translate(side * 33, -19); c.rotate(degrees(side * .42f));
            fill(0xff9a6336); ellipse(c, 0, 0, 14, 7.5f, 0);
            fill(0xffeab7a0); ellipse(c, side * 1.5f, .4f, 8.6f, 4, 0);
            c.restore();
        }
        shade(new RadialGradient(-6, -14, 50, new int[]{0xffc58a55, 0xffa56a3a, 0xff7f4c28}, new float[]{0, .62f, 1}, Shader.TileMode.CLAMP));
        path.reset(); path.moveTo(0, -37);
        path.cubicTo(23, -37, 32, -16, 27, 6); path.cubicTo(23, 26, 12, 37, 0, 37);
        path.cubicTo(-12, 37, -23, 26, -27, 6); path.cubicTo(-32, -16, -23, -37, 0, -37); path.close();
        c.drawPath(path, paint);
        // Tuft of hair between the antlers.
        fill(0xff7f4c28); path.reset(); path.moveTo(-9, -35); path.quadTo(-3, -44, 0, -33); path.quadTo(3, -44, 9, -35);
        path.quadTo(3, -27, 0, -28); path.quadTo(-3, -27, -9, -35); path.close(); c.drawPath(path, paint);
        fill(0x47f0786e); ellipse(c, -19, 6, 6, 4, 0); ellipse(c, 19, 6, 6, 4, 0);
        shade(new RadialGradient(-4, 14, 26, 0xfff6e2c3, 0xffddb98e, Shader.TileMode.CLAMP));
        ellipse(c, 0, 19, 19, 15.5f, 0);
        stroke(0xff6b3f22, 1.5f); c.drawLine(0, 15, 0, 23, paint);
        path.reset(); path.moveTo(-9, 22); path.quadTo(-4.5f, 28, 0, 23); path.quadTo(4.5f, 28, 9, 22); c.drawPath(path, paint);
        // Collar and a sprig of holly on the head.
        fill(0xffc8283b); path.reset(); path.moveTo(-23, 33); path.quadTo(0, 49, 23, 33);
        path.lineTo(25, 39); path.quadTo(0, 56, -25, 39); path.close(); c.drawPath(path, paint);
        stroke(0xffe3b54a, 1);
        path.reset(); path.moveTo(-23, 33); path.quadTo(0, 49, 23, 33); c.drawPath(path, paint);
        path.reset(); path.moveTo(-25, 39); path.quadTo(0, 56, 25, 39); c.drawPath(path, paint);
        holly(c, 17, -28, -.25f, 15); holly(c, 17, -28, 1.05f, 14);
        berry(c, 15.5f, -28.5f, 3); berry(c, 20, -27, 2.8f); berry(c, 17, -24.5f, 2.8f);
    }

    private void reindeerLive(Canvas c, double t, boolean reduced) {
        double phase = t % 5.2;
        float open = !reduced && phase < .18 ? (float)(Math.abs(phase - .09) / .09) : 1;
        for (int side = -1; side <= 1; side += 2) {
            c.save(); c.translate(side * 12.5f, -8); c.scale(1, Math.max(.07f, open));
            fill(0xff20140c); ellipse(c, 0, 0, 6.6f, 7.4f, 0);
            fill(Color.WHITE); c.drawCircle(-2, -2.8f, 2.3f, paint); c.drawCircle(2, 2.2f, 1.1f, paint);
            c.restore();
        }
        float pulse = reduced ? .5f : .5f + .5f * sin(t * 1.7);
        shade(noseGlow); paint.setAlpha(Math.round(255 * (.3f + .28f * pulse))); c.drawCircle(0, 8, 21, paint);
        shade(nose); ellipse(c, 0, 8, 9.6f, 7.6f, 0);
    }

    void paintClockStill(Canvas c, float W, float H) {
        wreath(c, W * .148f, H * .39f, H * .205f, H);
        place(c, W * .852f, H * .55f, Math.min(H * .0068f, W * .0028f), this::reindeer);
    }

    void drawClockLive(Canvas c, float W, float H, double t, boolean reduced) {
        float u = H / 100, x = W * .148f, y = H * .39f, ring = H * .205f;
        // The bells stay still, then ring for a moment.
        double beat = t % 7;
        float swing = !reduced && beat < 2.6 ? sin(beat / 2.6 * Math.PI) * .24f * sin(t * 7) : 0;
        float top = y + ring * .98f, side = y + ring * .72f, s = u * 9.4f;
        bell(c, x - u * 15.5f, side, s * .86f, u * 10, swing * .8f);
        bell(c, x + u * 15.5f, side, s * .86f, u * 10, -swing * .8f);
        bell(c, x, top, s, u * 9.5f, swing * .6f);
        bow(c, x, top - u * .5f, u * .62f);
        final float collar = swing * .5f;
        place(c, W * .852f, H * .55f, Math.min(H * .0068f, W * .0028f), canvas -> {
            reindeerLive(canvas, t, reduced);
            bell(canvas, 0, 46, 8.5f, 1, collar);
        });
    }

    // --- "¿Cuántos días faltan?": Santa walks in, opens his bag and the count rises out of it ---
    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }
    private static float ease(float v) { return v * v * (3 - 2 * v); }
    private static float mix(float a, float b, float v) { return a + (b - a) * v; }

    private void santaArm(Canvas c, float x, float y, float angle) {
        c.save(); c.translate(x, y); c.rotate(degrees(angle));
        fill(0xffc8283b); c.drawRoundRect(-3.7f, -1, 3.7f, 14, 3.6f, 3.6f, paint);
        fill(0xfff4f8ff); c.drawRoundRect(-4.5f, 12.6f, 4.5f, 16.2f, 1.8f, 1.8f, paint);
        fill(0xff1f7a48); ellipse(c, 0, 18.6f, 4.5f, 4.7f, 0);
        c.restore();
    }

    /** Origin at the ground between his boots. step -1..1 walks, reach 0..1 goes to the bag, wave 0..1 raises the other arm. */
    private void santaFigure(Canvas c, float step, float reach, float wave, float time) {
        for (int side = -1; side <= 1; side += 2) {
            float lift = Math.max(0, side * step) * 2.4f;
            fill(0xffa81f30); c.drawRoundRect(side * 7 - 4.4f, -15, side * 7 + 4.4f, -2 - lift, 2.5f, 2.5f, paint);
            fill(0xff1b2029); ellipse(c, side * 8, -3.4f - lift, 7, 3.8f, 0);
            fill(0x47ffffff); ellipse(c, side * 8 - 2, -4.6f - lift, 2.6f, .9f, 0);
        }
        santaArm(c, -13, -36, mix(.2f, 2.55f + .22f * sin(time * 9), wave));
        shade(new LinearGradient(-16, 0, 16, 0, new int[]{0xffd8303f, 0xffd42a3a, 0xff9c1a2b}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        ellipse(c, 0, -26, 16.5f, 18.5f, 0);
        fill(0xfff4f8ff); c.drawRect(-2.6f, -43, 2.6f, -11, paint);
        c.drawRoundRect(-16, -13.5f, 16, -7.9f, 2.8f, 2.8f, paint);
        fill(0xff1b2029); c.drawRect(-16.2f, -26.5f, 16.2f, -21.9f, paint);
        fill(0xfff2c552); c.drawRoundRect(-4.2f, -28, 4.2f, -20.4f, 1.2f, 1.2f, paint);
        fill(0xff1b2029); c.drawRoundRect(-2.2f, -26, 2.2f, -22.4f, .6f, .6f, paint);
        santaArm(c, 13, -36, mix(-.2f, -1.25f, reach));
        // Head: face, beard, moustache, hat.
        fill(0xfff6c9a4); ellipse(c, 0, -50, 8.6f, 8.8f, 0);
        fill(0xfff4f8ff);
        for (int i = 0; i <= 8; i++) {
            float a = .15f + i * .355f;
            ellipse(c, cos(a) * 8.4f, -46 + sin(a) * 7.4f, 4.1f, 4.1f, 0);
        }
        ellipse(c, 0, -42, 8.6f, 7.6f, 0);
        fill(0x73ee786e); ellipse(c, -5.4f, -48.6f, 2.1f, 1.5f, 0); ellipse(c, 5.4f, -48.6f, 2.1f, 1.5f, 0);
        fill(0xff20262f); ellipse(c, -3.1f, -51.5f, 1, 1.15f, 0); ellipse(c, 3.1f, -51.5f, 1, 1.15f, 0);
        fill(Color.WHITE); c.drawCircle(-3.4f, -51.9f, .32f, paint); c.drawCircle(2.8f, -51.9f, .32f, paint);
        ellipse(c, -3.5f, -46, 4, 1.8f, .22f); ellipse(c, 3.5f, -46, 4, 1.8f, -.22f);
        fill(0xffe8947e); ellipse(c, 0, -48.2f, 1.8f, 1.6f, 0);
        fill(0xffc8283b); path.reset(); path.moveTo(-8.8f, -56.5f);
        path.cubicTo(-7, -68, 3, -72, 9, -69); path.cubicTo(14, -66.5f, 16.5f, -62, 17, -56.5f);
        path.quadTo(13, -60, 8.8f, -56.5f); path.close(); c.drawPath(path, paint);
        fill(0xffa81f30); path.reset(); path.moveTo(9, -69); path.cubicTo(14, -66.5f, 16.5f, -62, 17, -56.5f);
        path.quadTo(13, -60, 8.8f, -56.5f); path.quadTo(8, -63, 9, -69); path.close(); c.drawPath(path, paint);
        fill(0xfff4f8ff); c.drawRoundRect(-10.2f, -59.4f, 10.2f, -54.4f, 2.5f, 2.5f, paint);
        c.drawCircle(17.2f, -55, 3.2f, paint);
    }

    /** Origin at the ground under the bag; open 0..1 unties it and widens the mouth. */
    private void santaBag(Canvas c, float open) {
        float neck = 3.2f + 7.4f * open;
        shade(new RadialGradient(-3, -14, 24, new int[]{0xffb98350, 0xff8d5c33, 0xff5f3d20}, new float[]{0, .65f, 1}, Shader.TileMode.CLAMP));
        path.reset(); path.moveTo(-neck, -26);
        path.cubicTo(-neck - 1, -21, -15, -19, -15.5f, -10); path.cubicTo(-16, -2, -10, 0, 0, 0);
        path.cubicTo(10, 0, 16, -2, 15.5f, -10); path.cubicTo(15, -19, neck + 1, -21, neck, -26); path.close();
        c.drawPath(path, paint);
        stroke(0x803c220e, .5f);
        path.reset(); path.moveTo(-neck * .55f, -24); path.quadTo(-9, -15, -10, -6); c.drawPath(path, paint);
        path.reset(); path.moveTo(neck * .5f, -24); path.quadTo(7, -14, 9, -5); c.drawPath(path, paint);
        // A stitched patch.
        fill(0xffc8283b); c.drawRoundRect(3.5f, -11, 10.5f, -4.6f, .8f, .8f, paint);
        stroke(0xfff2c552, .3f); paint.setPathEffect(new DashPathEffect(new float[]{.8f, .6f}, 0));
        path.reset(); path.addRoundRect(4.2f, -10.3f, 9.8f, -5.3f, .6f, .6f, Path.Direction.CW); c.drawPath(path, paint);
        paint.setPathEffect(null);
        if (open < 1) {
            int alpha = Math.round(255 * (1 - open));
            fill(0xff8d5c33); paint.setAlpha(alpha);
            ellipse(c, -2.6f, -29, 2, 3.8f, -.5f); ellipse(c, 0, -29, 2, 3.8f, 0); ellipse(c, 2.6f, -29, 2, 3.8f, .5f);
            fill(0xffe3b54a); paint.setAlpha(alpha); c.drawRoundRect(-4.2f, -27, 4.2f, -25, 1, 1, paint);
        }
        if (open > 0) {
            float ry = .8f + 2.8f * open;
            fill(0xff2a1608); ellipse(c, 0, -26, neck, ry, 0);
            shade(new RadialGradient(0, -26, neck, Color.argb(Math.round(255 * open), 255, 236, 160), 0x00ffbe46, Shader.TileMode.CLAMP));
            ellipse(c, 0, -26, neck, ry, 0);
            stroke(0xffc99a63, .9f); ellipse(c, 0, -26, neck, ry, 0);
        }
    }

    // Slow turning beams of light from the bag; only on the day itself.
    private void lightRays(Canvas c, float x, float y, float length, float t, float open) {
        float strength = ease(clamp01((t - 4.4f) / .6f)) * open;
        if (strength <= 0) return;
        shade(new RadialGradient(x, y, length, Color.argb(Math.round(127 * strength), 255, 226, 140), 0x00ffbe50, Shader.TileMode.CLAMP));
        paint.setXfermode(ADD);
        oval.set(x - length, y - length, x + length, y + length);
        for (int i = 0; i < 14; i++) {
            float a = i / 14f * 360 + degrees(t * .22f);
            c.drawArc(oval, a - 6.3f, 12.6f, true, paint);
        }
        paint.setXfermode(null);
    }

    /** One frame of the scene. Camera, sky, snow and scene follow the same order as the HTML preview. */
    void drawSanta(Canvas c, Bitmap sky, Bitmap scene, float t, int days, double snowTime, float W, float H) {
        ChristmasSanta.Steps T = ChristmasSanta.steps(days);
        float k = H / 100 * 1.12f, floor = H * .95f;
        float arrive = clamp01(t / T.arrive), leave = clamp01((t - T.stay - .4f) / (T.leave - T.stay - .4f));
        float x = t < T.stay ? mix(W * 1.14f, W * .34f, 1 - (1 - arrive) * (1 - arrive)) : mix(W * .34f, -W * .4f, leave * leave);
        float step = arrive < 1 || leave > 0 ? sin(t * 9) : 0;
        float open = ease(clamp01((t - T.arrive) / (T.open - T.arrive))) * (1 - ease(clamp01((t - T.close) / .8f)));
        // The hand unties the bag and goes back before the camera arrives, so nothing covers the mouth.
        float reach = ease(clamp01((t - T.arrive + .2f) / .6f)) * (1 - ease(clamp01((t - T.open + .2f) / .5f)));
        float wave = ease(clamp01((t - T.back + .1f) / .4f)) * (1 - ease(clamp01((t - T.stay - .7f) / .4f)));
        float bagX = x + 36 * k, mouthY = floor - 26 * k;
        float zoom = ease(clamp01((t - T.open) / (T.zoom - T.open))) * (1 - ease(clamp01((t - T.hold) / (T.back - T.hold))));
        // The camera moves to the mouth of the bag: it ends low in the middle, leaving the sky for the count.
        float scale = mix(1, 2.7f, zoom);
        c.save();
        c.translate(mix(bagX, W / 2, zoom), mix(mouthY, H * .84f, zoom));
        c.scale(scale, scale); c.translate(-bagX, -mouthY);
        c.drawBitmap(sky, 0, 0, smooth);
        snowBehind(c, snowTime, W, H);
        if (days == 0) lightRays(c, bagX, mouthY, 150 * k, t, open);   // in the sky, behind everything on the snow
        c.drawBitmap(scene, 0, 0, smooth);
        fill(Color.argb(Math.round(173 * zoom), 7, 12, 28)); c.drawRect(-W, -H, W * 2, H * 2, paint);   // the bag and the count take over
        fill(0x735c78b0); ellipse(c, x, floor, 19 * k, 3 * k, 0); ellipse(c, bagX, floor, 17 * k, 2.6f * k, 0);
        // The light stays behind him and the bag, so it never washes out their colours.
        if (open > 0) {
            shade(new RadialGradient(bagX, mouthY, 40 * k, Color.argb(Math.round(178 * open), 255, 214, 120), 0x00ffaa3c, Shader.TileMode.CLAMP));
            paint.setXfermode(ADD); c.drawCircle(bagX, mouthY, 40 * k, paint); paint.setXfermode(null);
        }
        float bagOpen = open;
        place(c, bagX, floor, k, canvas -> santaBag(canvas, bagOpen));
        c.save(); c.translate(x, floor - Math.abs(step) * 1.2f * k); c.rotate(degrees(step * .04f)); c.scale(k, k);
        santaFigure(c, step, reach, wave, t);
        c.restore();
        if (open > 0) {
            fill(0xffffe9a6); paint.setXfermode(ADD);
            for (int i = 0; i < 46; i++) {
                float phase = (t * .32f + (float)hash(i + .31)) % 1;
                paint.setAlpha(Math.round(255 * sin(phase * Math.PI) * open));
                c.drawCircle(bagX + ((float)hash(i + .73) - .5f) * 19 * k * (1 + phase), mouthY - phase * 30 * k, (.22f + .3f * (float)hash(i + .91)) * k, paint);
            }
            paint.setXfermode(null);
        }
        c.restore();
        snowInFront(c, snowTime, W, H);
        if (days == 0) drawToday(c, t, zoom, W, H); else drawCount(c, T, t, days, W, H);
    }

    private void bigText(Canvas c, String text, float x, float y, float size, int alpha, float H) {
        paint.setTypeface(BOLD); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size);
        float middle = y - (paint.descent() + paint.ascent()) / 2;
        shade(new LinearGradient(0, y - size / 2, 0, y + size / 2, 0xfffff6c8, 0xffffc23a, Shader.TileMode.CLAMP));
        paint.setAlpha(alpha); paint.setShadowLayer(H * .05f, 0, 0, 0xe6ffbe46);
        c.drawText(text, x, middle, paint);
        paint.clearShadowLayer();
    }

    private void drawCount(Canvas c, ChristmasSanta.Steps T, float t, int days, float W, float H) {
        float rise = ease(clamp01((t - T.zoom + .5f) / 1.1f)), fade = 1 - clamp01((t - T.hold) / .5f);
        if (rise <= 0 || fade <= 0) return;
        int shown = Math.round(days * ease(clamp01((t - T.zoom) / (T.count - T.zoom))));
        float y = mix(H * .84f, H * .39f, rise), size = H * .42f * (.3f + .7f * rise);
        bigText(c, String.valueOf(shown), W / 2, y, size, Math.round(255 * fade), H);
        fill(Color.WHITE); paint.setAlpha(Math.round(255 * fade * clamp01((rise - .6f) / .4f)));
        paint.setTextSize(H * .07f); paint.setShadowLayer(H * .02f, 0, 0, 0xe6281004);
        float middle = -(paint.descent() + paint.ascent()) / 2;
        c.drawText(days == 1 ? "Falta" : "Faltan", W / 2, y - H * .27f + middle, paint);
        c.drawText(days == 1 ? "día para Navidad" : "días para Navidad", W / 2, y + H * .26f + middle, paint);
        paint.clearShadowLayer();
    }

    private void giftRain(Canvas c, float t, float W, float H) {
        float u = H / 100, gravity = H * .62f, fade = 1 - clamp01((t - 16.4f) / .9f);
        if (fade <= 0) return;
        // A transparent layer fades the pile as one; without it each gift would show through the others.
        int layer = fade < 1 ? c.saveLayerAlpha(0, 0, W, H, Math.round(255 * fade)) : -1;
        for (Gift g : RAIN) {
            float fall = t - g.at;
            if (fall < 0) continue;
            float rest = g.rest * H, landed = (float)Math.sqrt(2 * (rest + H * .12f) / gravity);
            float y = -H * .12f + gravity * fall * fall / 2, angle = g.spin * fall;
            if (fall >= landed) {
                float since = fall - landed;
                y = rest - Math.abs(sin(since * 7)) * (float)Math.exp(-since * 5) * u * 5; angle = g.tilt;
            }
            c.save(); c.translate(g.x * W, y); c.rotate(degrees(angle)); c.scale(u, u);
            gift(c, 0, g.size * g.tall / 2, g.size, g.size * g.tall, g.color, g.dark, g.ribbon, true);
            c.restore();
        }
        if (layer >= 0) c.restoreToCount(layer);
    }

    // "¡Es hoy!": a flash from the bag, the words jump out and it starts raining gifts.
    private void drawToday(Canvas c, float t, float zoom, float W, float H) {
        float flash = clamp01((t - 4.5f) / .35f) * (1 - clamp01((t - 4.9f) / 1.3f));
        giftRain(c, t, W, H);
        float pop = clamp01((t - 4.9f) / .55f), fade = 1 - clamp01((t - 15) / .7f);
        if (pop > 0 && fade > 0) {
            // Grows past its size, then settles; afterwards it breathes softly.
            float grow = (1 - (1 - pop) * (1 - pop) * (1 - pop)) * (1 + .22f * sin(pop * Math.PI)) * (1 + .025f * sin(t * 3));
            // Once the camera backs out it moves to the free sky, so Santa's face stays in view.
            float x = mix(W * .655f, W / 2, zoom), y = mix(H * .23f, H * .32f, zoom), size = H * mix(.25f, .3f, zoom) * grow;
            paint.setTypeface(BOLD); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size);
            stroke(0xd9781e14, H * .012f); paint.setAlpha(Math.round(255 * fade));
            c.drawText("¡Es hoy!", x, y - (paint.descent() + paint.ascent()) / 2, paint);
            bigText(c, "¡Es hoy!", x, y, size, Math.round(255 * fade), H);
        }
        if (flash > 0) { fill(Color.argb(Math.round(235 * flash), 255, 246, 214)); c.drawRect(0, 0, W, H, paint); }
    }

    /** The clock screen is on all day: full speed only while something visibly moves. */
    static long clockFrameDelayMs(double t) {
        return t % 7 < 2.6 || t % 5.2 < .3 ? 50 : 333;
    }
}
