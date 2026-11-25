package local.doja.voice;

import android.graphics.*;

/**
 * The trip-to-Huila scene, a line-by-line port of artifacts/andromeda-huila-preview.html (approved there first).
 * Everything is drawn in a 2280x1080 design space and scaled to the view; the times match the prototype and the
 * baked sound in assets/huila-viaje.wav. Cartoon only: the car is empty and the suitcases land intact.
 */
final class HuilaArt {
    static final float W = 2280, H = 1080;
    // timeline (seconds)
    static final float CAR_IN = .3f, CAR_STOP = 2.7f, COUNT = 3.0f, SOLDIER = 4.4f, BOOM = 8.5f, DUCK = 11.2f, END = 15f;
    static final float[][] BURSTS = {{5.2f, 6}, {6.3f, 7}, {7.35f, 8}};
    static final float GAP = .085f;
    static final float[] SHOTS;
    static {
        int n = 0; for (float[] b : BURSTS) n += (int) b[1];
        SHOTS = new float[n]; int k = 0;
        for (float[] b : BURSTS) for (int i = 0; i < (int) b[1]; i++) SHOTS[k++] = b[0] + i * GAP;
    }
    // layout
    static final float HORIZON = 560, VERGE = 690, ROAD_TOP = 800, ROAD_BOT = 955;
    static final float CAR_X = 760, CAR_Y = 888, SOLDIER_X = 1790, SOLDIER_Y = 800;
    static final float[] MUZZLE = {-212, -224};
    static final int CAR_COLOR = c("#e2553b");

    private Canvas g;
    private final Path path = new Path(), oval = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Typeface heavy = Typeface.create(Typeface.DEFAULT, 900, false), bold = Typeface.create(Typeface.DEFAULT, 800, false),
        semi = Typeface.create(Typeface.DEFAULT, 600, false);
    private Bitmap background, soldier;
    private float scaleX = 1, scaleY = 1;

    HuilaArt() {
        line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ---------- helpers ----------
    static int c(String hex) { return Color.parseColor(hex); }
    static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    static float lerp(float a, float b, float p) { return a + (b - a) * p; }
    static float easeOut(float p) { return 1 - (float) Math.pow(1 - p, 3); }
    static float easeBack(float p) { float k = 1.9f; return 1 + (k + 1) * (float) Math.pow(p - 1, 3) + k * (float) Math.pow(p - 1, 2); }
    static float rnd(double i, double k) { double v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453; return (float) (v - Math.floor(v)); }
    static int argb(float a, int r, int gg, int b) { return Color.argb(Math.round(clamp(a) * 255), r, gg, b); }

    private void begin() { path.reset(); }
    private void rr(float x, float y, float w, float h, float r) { path.addRoundRect(x, y, x + w, y + h, r, r, Path.Direction.CW); }
    private void circle(float x, float y, float r) { path.addCircle(x, y, r, Path.Direction.CW); }
    private void ellipse(float x, float y, float rx, float ry, float rot, Path into) {
        box.set(x - rx, y - ry, x + rx, y + ry);
        if (rot == 0) { into.addOval(box, Path.Direction.CW); return; }
        oval.reset(); oval.addOval(box, Path.Direction.CW); matrix.setRotate((float) Math.toDegrees(rot), x, y); oval.transform(matrix); into.addPath(oval);
    }
    private void poly(float... p) { begin(); path.moveTo(p[0], p[1]); for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]); path.close(); }
    private void fill(int color) { fill.setShader(null); fill.setColor(color); g.drawPath(path, fill); }
    private void fill(Shader shader) { fill.setColor(Color.BLACK); fill.setShader(shader); g.drawPath(path, fill); fill.setShader(null); }
    private void stroke(int color, float width) { line.setShader(null); line.setColor(color); line.setStrokeWidth(width); g.drawPath(path, line); }
    private void rect(float x, float y, float w, float h, int color) { fill.setShader(null); fill.setColor(color); g.drawRect(x, y, x + w, y + h, fill); }
    private void seg(float x1, float y1, float x2, float y2, int color, float width) {
        line.setShader(null); line.setColor(color); line.setStrokeWidth(width); g.drawLine(x1, y1, x2, y2, line);
    }
    private void star(float x, float y, float r1, float r2, int n, float rot) {
        begin();
        for (int i = 0; i < n * 2; i++) {
            float r = i % 2 == 1 ? r2 : r1; double a = rot + i * Math.PI / n;
            float px = x + (float) Math.cos(a) * r, py = y + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
    }

    // ---------- static background ----------
    private void paintBackground() {
        // sky
        begin(); path.addRect(0, 0, W, H, Path.Direction.CW);
        fill(new LinearGradient(0, 0, 0, HORIZON + 120, new int[]{c("#6fb9e6"), c("#b9def0"), c("#f7d9a6")}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP));
        // sun
        begin(); path.addRect(1600, 0, 2300, 520, Path.Direction.CW);
        fill(new RadialGradient(1960, 210, 260, new int[]{Color.argb(255, 255, 246, 200), Color.argb(230, 255, 226, 140), Color.argb(0, 255, 226, 140)},
            new float[]{0, .25f, 1}, Shader.TileMode.CLAMP));
        begin(); circle(1960, 210, 70); fill(c("#fff6d0"));
        // clouds
        float[][] clouds = {{260, 120, 1}, {1500, 90, .8f}, {2150, 380, .6f}, {880, 210, .55f}};
        for (float[] cl : clouds) {
            float x = cl[0], y = cl[1], k = cl[2]; begin();
            ellipse(x, y, 120 * k, 38 * k, 0, path); ellipse(x + 70 * k, y - 22 * k, 80 * k, 40 * k, 0, path); ellipse(x - 80 * k, y + 5 * k, 70 * k, 28 * k, 0, path);
            fill(Color.argb(217, 255, 255, 255));
        }
        // Nevado del Huila
        poly(-60, 560, 180, 360, 300, 262, 380, 236, 450, 248, 520, 280, 640, 360, 900, 560);
        fill(new LinearGradient(0, 230, 0, HORIZON, c("#8fa3bb"), c("#6f8499"), Shader.TileMode.CLAMP));
        poly(216, 330, 300, 262, 380, 236, 450, 248, 520, 280, 588, 326, 540, 318, 505, 300, 470, 326, 430, 296, 392, 330, 350, 300, 300, 332, 262, 312);
        fill(c("#f4f8fc"));
        // far range
        begin(); path.moveTo(0, 560);
        for (int x = 0; x <= W; x += 40) path.lineTo(x, 470 + (float) (Math.sin(x / 170.0) * 34 + Math.sin(x / 63.0) * 12) + (x < 700 ? 40 : 0));
        path.lineTo(W, 600); path.lineTo(0, 600); path.close(); fill(c("#86a892"));
        // green hills
        begin(); path.moveTo(0, 640);
        for (int x = 0; x <= W; x += 30) path.lineTo(x, 545 + (float) (Math.sin(x / 210.0 + 1) * 26 + Math.sin(x / 90.0) * 8));
        path.lineTo(W, 700); path.lineTo(0, 700); path.close(); fill(c("#5f9a55"));
        // river Magdalena
        begin(); path.moveTo(-10, 628);
        for (int x = 0; x <= 1500; x += 30) path.lineTo(x, 628 + (float) Math.sin(x / 260.0) * 18);
        for (int x = 1500; x >= 0; x -= 30) path.lineTo(x, 662 + (float) Math.sin(x / 260.0) * 18 + x / 120f);
        path.close(); fill(c("#58a6cc"));
        for (int i = 0; i < 14; i++) { float x = 60 + i * 100, y = 645 + (float) Math.sin(x / 260.0) * 18 + x / 240;
            seg(x, y, x + 40, y, Color.argb(115, 255, 255, 255), 3); }
        // Tatacoa
        Object[][] bands = {{c("#d98a52"), 585f, 0}, {c("#c9673c"), 615f, 2}, {c("#b3542f"), 650f, 4}};
        for (Object[] band : bands) {
            int col = (int) band[0]; float base = (float) band[1]; int k = (int) band[2];
            begin(); path.moveTo(1180, 720);
            for (int x = 1180; x <= W + 20; x += 22) {
                float h = 40 + 55 * (float) Math.abs(Math.sin(x / 75.0 + k)) + 25 * rnd(x, k);
                path.lineTo(x, base - h * (x < 1300 ? (x - 1180) / 120f : 1));
            }
            path.lineTo(W, 720); path.close(); fill(col);
            for (int x = 1220; x < W; x += 34) seg(x, base - 20 - 40 * rnd(x, k + 9), x + 6 * (rnd(x, k) - .5f), base + 40, Color.argb(71, 80, 30, 15), 3);
        }
        // verge
        begin(); path.addRect(0, VERGE, W, ROAD_TOP, Path.Direction.CW);
        fill(new LinearGradient(0, VERGE, 0, ROAD_TOP, c("#b9b866"), c("#949c47"), Shader.TileMode.CLAMP));
        for (int i = 0; i < 160; i++) { float x = rnd(i, 3) * W, y = VERGE + 15 + rnd(i, 4) * (ROAD_TOP - VERGE - 20);
            seg(x, y, x - 6, y - 16, c("#7d8a3a"), 3); seg(x, y, x + 5, y - 14, c("#7d8a3a"), 3); }
        // road
        rect(0, ROAD_TOP, W, ROAD_BOT - ROAD_TOP, c("#4b4b51"));
        rect(0, ROAD_TOP + 6, W, 6, c("#e9e9e9")); rect(0, ROAD_BOT - 12, W, 6, c("#e9e9e9"));
        for (int x = 0; x < W; x += 150) rect(x, (ROAD_TOP + ROAD_BOT) / 2 - 4, 85, 8, c("#f2c230"));
        // foreground
        rect(0, ROAD_BOT, W, H - ROAD_BOT, c("#a2a752"));
        for (int i = 0; i < 120; i++) { float x = rnd(i, 7) * W, y = ROAD_BOT + 25 + rnd(i, 8) * 110; int col = c("#7f8a3c");
            seg(x, y, x - 8, y - 24, col, 4); seg(x, y, x + 7, y - 20, col, 4); seg(x, y, x, y - 28, col, 4); }
        float[][] rocks = {{300, 1010, 40}, {1650, 1040, 30}, {2000, 995, 46}};
        for (float[] r : rocks) { begin(); ellipse(r[0], r[1], r[2], r[2] * .55f, 0, path); fill(c("#8f8676")); }
        cactus(1480, 770, 230); cactus(2150, 790, 270); cactus(230, 745, 150);
        // welcome sign
        rect(1222, 600, 14, 195, c("#6b6b6b")); rect(1322, 600, 14, 195, c("#6b6b6b"));
        begin(); rr(1140, 545, 280, 125, 14); fill(c("#1f7a45"));
        begin(); rr(1150, 555, 260, 105, 9); stroke(Color.WHITE, 6);
        text.setShadowLayer(0, 0, 0, 0); text.setColor(Color.WHITE);
        text.setTypeface(semi); text.setTextSize(30); g.drawText("Bienvenidos al", 1280, 598, text);
        text.setTypeface(heavy); text.setTextSize(52); g.drawText("HUILA", 1280, 648, text);
    }
    private void cactus(float x, float base, float h) {
        int body = c("#4f7d3b"), edge = c("#3c6330");
        begin(); rr(x - 24, base - h, 48, h, 24); fill(body); stroke(edge, 4);
        arm(x - 74, base - h * .45f, -1, h * .35f, body, edge); arm(x + 44, base - h * .6f, 1, h * .3f, body, edge);
        seg(x - 6, base - h + 20, x - 6, base - 10, Color.argb(64, 255, 255, 255), 2);
    }
    private void arm(float ax, float ay, int dir, float len, int body, int edge) {
        begin(); rr(ax, ay - len, 30, len, 15); fill(body); stroke(edge, 4);
        begin(); rr(dir < 0 ? ax : ax - 40, ay - 10, 70, 30, 15); fill(body);
    }

    // ---------- suitcases and car ----------
    static final float[][] ROOF_BAGS = {{-60, -232, 130, 70}, {62, -226, 96, 58}, {-40, -298, 86, 56}};
    static final int[] BAG_COLORS = {c("#2f7fd1"), c("#f2b632"), c("#9b59b6")};
    private void suitcase(float cx, float cy, float w, float h, int col, float ang) {
        g.save(); g.translate(cx, cy); g.rotate((float) Math.toDegrees(ang));
        begin(); path.moveTo(-w * .18f, -h / 2); path.lineTo(-w * .18f, -h / 2 - 16); path.lineTo(w * .18f, -h / 2 - 16); path.lineTo(w * .18f, -h / 2);
        line.setStrokeCap(Paint.Cap.BUTT); stroke(c("#3a2a1a"), 6); line.setStrokeCap(Paint.Cap.ROUND);
        begin(); rr(-w / 2, -h / 2, w, h, 10); fill(col); stroke(Color.argb(64, 0, 0, 0), 4);
        rect(-w * .3f, -h / 2, 9, h, Color.argb(191, 80, 50, 25)); rect(w * .3f - 9, -h / 2, 9, h, Color.argb(191, 80, 50, 25));
        begin(); circle(w * .05f, h * .05f, Math.min(w, h) * .12f); fill(Color.argb(204, 255, 255, 255));
        g.restore();
    }
    private void car(float x, float y, float roll, boolean burnt, float[][] holes, int holeCount, boolean bags) {
        g.save(); g.translate(x, y);
        begin(); ellipse(0, 44, 250, 16, 0, path); fill(Color.argb(71, 0, 0, 0));
        if (burnt) { g.translate(135, 0); g.rotate((float) Math.toDegrees(-0.06)); g.translate(-135, 14); }
        int body = burnt ? c("#2c2826") : CAR_COLOR;
        if (!burnt) {
            rect(-125, -196, 210, 9, c("#2b2b2b")); rect(-110, -196, 8, 14, c("#2b2b2b")); rect(60, -196, 8, 14, c("#2b2b2b"));
            if (bags) for (int i = 0; i < 3; i++) { float[] b = ROOF_BAGS[i]; suitcase(b[0], b[1], b[2], b[3], BAG_COLORS[i], 0); }
        }
        begin(); path.moveTo(-178, -92); path.lineTo(-140, -172); path.quadTo(-130, -186, -110, -186);
        path.lineTo(70, -186); path.quadTo(92, -186, 106, -170); path.lineTo(170, -92); path.close(); fill(body);
        begin(); rr(-228, -100, 456, 92, 28); fill(body);
        int glass = burnt ? c("#141212") : c("#a6d8ee");
        poly(-156, -100, -126, -164, -36, -164, -36, -100); fill(glass);
        poly(-20, -100, -20, -164, 66, -164, 84, -158, 134, -100); fill(glass);
        if (!burnt) { poly(-110, -160, -90, -160, -120, -104, -140, -104); fill(Color.argb(140, 255, 255, 255)); }
        seg(-28, -100, -28, -18, Color.argb(64, 0, 0, 0), 3);
        begin(); ellipse(214, -66, 14, 10, 0, path); fill(burnt ? c("#222222") : c("#ffe9a8"));
        rect(-230, -82, 14, 22, burnt ? c("#222222") : c("#b3202a"));
        begin(); rr(-232, -32, 464, 20, 9); fill(c("#2a2a2a"));
        if (!burnt) rect(-200, -90, 380, 8, Color.argb(46, 255, 255, 255));
        for (int i = 0; i < holeCount; i++) {
            begin(); circle(holes[i][0], holes[i][1], 6); fill(c("#1a1a1a"));
            begin(); circle(holes[i][0], holes[i][1], 9); stroke(Color.argb(128, 255, 255, 255), 2);
        }
        float[] wheels = burnt ? new float[]{135} : new float[]{-135, 135};
        for (float wx : wheels) {
            begin(); circle(wx, 0, 50); fill(c("#151515"));
            begin(); circle(wx, 0, 24); fill(burnt ? c("#3a3533") : c("#c9ccd1"));
            for (int k = 0; k < 5; k++) { double a = roll + k * 1.2566;
                seg(wx, 0, wx + (float) Math.cos(a) * 24, (float) Math.sin(a) * 24, burnt ? c("#222222") : c("#7b7f86"), 5); }
        }
        if (burnt) { begin(); ellipse(-150, 20, 40, 12, 0, path); fill(c("#151515")); }
        g.restore();
    }

    // ---------- the man in camouflage (cached) ----------
    private interface Shape { void add(); }
    private static final int[] CAMO = {c("#4a5926"), c("#8c7b4b"), c("#2f3a1e"), c("#a39a68")};
    private final Path blob = new Path();
    private void camo(Shape shape, int seed) {
        begin(); shape.add();
        g.save(); g.clipPath(path);
        rect(-400, -500, 800, 600, c("#6b7a3a"));
        for (int i = 0; i < 70; i++) {
            blob.reset(); ellipse(-260 + rnd(i, seed) * 520, -360 + rnd(i, seed + 1) * 380, 10 + rnd(i, seed + 2) * 22, 7 + rnd(i, seed + 3) * 12, rnd(i, seed + 4) * 3, blob);
            fill.setShader(null); fill.setColor(CAMO[i % 4]); g.drawPath(blob, fill);
        }
        g.restore();
    }
    private void limb(float x1, float y1, float x2, float y2, float w) {
        double a = Math.atan2(y2 - y1, x2 - x1); float dx = (float) Math.sin(a) * w / 2, dy = (float) -Math.cos(a) * w / 2;
        path.moveTo(x1 + dx, y1 + dy); path.lineTo(x2 + dx, y2 + dy); path.lineTo(x2 - dx, y2 - dy); path.lineTo(x1 - dx, y1 - dy); path.close();
    }
    private void soldierAt(float x, float y) {
        int skin = c("#c98b5e");
        g.save(); g.translate(x, y);
        begin(); rr(-46, -24, 42, 24, 6); rr(4, -24, 42, 24, 6); fill(c("#3b2a1d"));
        camo(() -> { rr(-36, -150, 32, 130, 8); rr(8, -150, 32, 130, 8); }, 11);
        camo(() -> rr(-50, -282, 100, 148, 22), 21);
        rect(-50, -150, 100, 14, c("#2b2b22"));
        rect(-38, -225, 30, 30, Color.argb(115, 30, 35, 15)); rect(4, -225, 30, 30, Color.argb(115, 30, 35, 15));
        camo(() -> limb(18, -255, -40, -214, 24), 31);
        begin(); rr(0, -236, 74, 26, 8); fill(c("#5a3a22"));
        rect(-96, -238, 104, 28, c("#2d2d2d")); rect(-68, -252, 34, 12, c("#2d2d2d"));
        g.save(); g.translate(-64, -212); g.rotate((float) Math.toDegrees(-0.18)); rect(0, 0, 22, 46, c("#262626")); g.restore();
        rect(-158, -234, 64, 20, c("#3d3d3d")); rect(-208, -228, 52, 8, c("#1b1b1b"));
        begin(); circle(-42, -214, 13); fill(skin);
        camo(() -> limb(-12, -258, -126, -228, 26), 41);
        begin(); circle(-128, -226, 13); fill(skin);
        begin(); circle(-6, -312, 33); fill(skin);
        begin(); rr(-44, -322, 36, 12, 5); fill(c("#1a1a1a"));
        seg(-40, -294, -18, -296, c("#3b2413"), 6);
        camo(() -> { box.set(-46, -360, 38, -276); path.arcTo(box, 180, 180, true); path.lineTo(46, -316); path.lineTo(-54, -316); path.close(); }, 51);
        rect(-56, -320, 104, 7, c("#3c4721"));
        g.restore();
    }
    private void bush(float x, float y) {
        float[][] blobs = {{-120, -40, 70}, {-50, -80, 85}, {40, -70, 90}, {120, -40, 70}, {0, -30, 90}, {-150, -10, 50}, {150, -10, 55}};
        begin(); for (float[] b : blobs) circle(x + b[0], y + b[1], b[2]); fill(c("#3f6b2f"));
        begin(); for (float[] b : blobs) circle(x + b[0] - b[2] * .2f, y + b[1] - b[2] * .25f, b[2] * .6f); fill(c("#4f8139"));
    }

    // ---------- explosion pieces ----------
    private static final int DEBRIS = 46;
    private static final float[][] DEB = new float[DEBRIS][6]; // vx, vy, size, rot, colour index, ground
    private static final int[] DEB_COLORS = {CAR_COLOR, c("#262626"), c("#8a8a8a"), c("#a6d8ee"), c("#f2b632")};
    private static final float[][] FLY = {{-420, -1150, -6, 905}, {330, -1350, 7, 930}, {120, -1700, -9, 880}}; // vx, vy, spin, ground
    static {
        for (int i = 0; i < DEBRIS; i++) {
            double a = -Math.PI / 2 + (rnd(i, 1) - .5) * 2.6, sp = 700 + rnd(i, 2) * 1100;
            DEB[i] = new float[]{(float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp), 8 + rnd(i, 3) * 26, rnd(i, 4) * 12 - 6, i % 5, 850 + rnd(i, 5) * 150};
        }
    }
    private final float[] bal = new float[4]; // x, y, landed, tl
    private float[] ballistic(float x0, float y0, float vx, float vy, float dt, float ground) {
        float grav = 1900, tl = (float) ((-vy + Math.sqrt(vy * vy + 2 * grav * (ground - y0))) / grav), d = Math.min(dt, tl);
        bal[0] = x0 + vx * d; bal[1] = y0 + vy * d + .5f * grav * d * d; bal[2] = dt >= tl ? 1 : 0; bal[3] = tl; return bal;
    }

    // ---------- frame ----------
    private final float[][] holes = new float[SHOTS.length][2];

    void draw(Canvas canvas, float t, int days, int width, int height) {
        g = canvas;
        float sx = width / W, sy = height / H;
        if (background == null || sx != scaleX || sy != scaleY) prepare(width, height, sx, sy);
        // camera shake
        float amp = 0;
        for (float s : SHOTS) if (t >= s && t < s + .06f) amp = Math.max(amp, 5);
        float db = t - BOOM;
        if (db >= 0 && db < 2) amp = Math.max(amp, 36 * (float) Math.exp(-db * 2.4));
        int fr = (int) Math.floor(t * 60); float ox = (rnd(fr, 1) - .5f) * 2 * amp, oy = (rnd(fr, 2) - .5f) * 2 * amp;
        if (amp > 0) g.drawBitmap(background, 0, 0, null);   // fills the edge the shake uncovers
        g.save(); g.translate(ox * sx, oy * sy); g.drawBitmap(background, 0, 0, null); g.scale(sx, sy);

        // car
        float pIn = clamp((t - CAR_IN) / (CAR_STOP - CAR_IN)), carX = lerp(-420, CAR_X, easeOut(pIn)), ds = t - CAR_STOP;
        float bob = t < CAR_STOP ? (float) Math.sin(t * 30) * 1.5f : 5 * (float) (Math.exp(-ds * 6) * Math.sin(ds * 26));
        int holeCount = 0;
        for (int i = 0; i < SHOTS.length; i++) if (t > SHOTS[i] + .13f && t < BOOM) { holes[holeCount][0] = -180 + rnd(i, 21) * 360; holes[holeCount][1] = -150 + rnd(i, 22) * 120; holeCount++; }
        if (t < BOOM) car(carX, CAR_Y + bob, (carX + 420) / 50, false, holes, holeCount, true);
        else if (db > .12f) car(CAR_X, CAR_Y, 0, true, null, 0, false);

        // man in camouflage
        float up = clamp((t - SOLDIER) / .5f), down = clamp((t - DUCK) / .5f), rise = easeOut(up) * (1 - easeOut(down));
        float kick = 0; for (float s : SHOTS) if (t >= s && t < s + .05f) kick = 7;
        if (rise > 0) {
            g.save(); g.clipRect(-100, -100, W + 100, SOLDIER_Y - 2);
            float x = SOLDIER_X + kick, y = SOLDIER_Y + (1 - rise) * 340;
            box.set(x - 230, y - 370, x + 110, y + 10); g.drawBitmap(soldier, null, box, bitmapPaint);
            g.restore();
        }
        bush(SOLDIER_X + 10, SOLDIER_Y + 8);

        // shots
        float mx = SOLDIER_X + MUZZLE[0] + kick, my = SOLDIER_Y + MUZZLE[1];
        for (int i = 0; i < SHOTS.length; i++) {
            float s = SHOTS[i], a = t - s; if (a < 0) continue;
            float tx = CAR_X - 180 + rnd(i, 21) * 360, ty = CAR_Y - 150 + rnd(i, 22) * 120;
            if (a < .045f) {
                begin(); path.addRect(mx - 120, my - 120, mx + 120, my + 120, Path.Direction.CW);
                fill(new RadialGradient(mx, my, 120, Color.argb(204, 255, 240, 180), Color.argb(0, 255, 200, 80), Shader.TileMode.CLAMP));
                star(mx - 20, my, 46 + rnd(i, 9) * 30, 14, 7, rnd(i, 10) * 3); fill(c("#fff6c8"));
                star(mx - 14, my, 26, 9, 7, rnd(i, 11) * 3); fill(c("#ffb02e"));
            }
            float p = a / .13f;
            if (p <= 1) {
                float b = Math.max(0, p - .18f);
                seg(lerp(mx, tx, b), lerp(my, ty, b), lerp(mx, tx, p), lerp(my, ty, p), Color.argb(242, 255, 225, 130), 5);
            } else if (p < 1.5f && s + .13f < BOOM) { star(tx, ty, 22 * (1.5f - p) * 2, 6, 6, i); fill(c("#fff3b0")); }
            if (a < .7f) {
                float cx = SOLDIER_X + 20 + 170 * a, cy = SOLDIER_Y - 240 - 380 * a + 1200 * a * a;
                if (cy < SOLDIER_Y + 30) { g.save(); g.translate(cx, cy); g.rotate((float) Math.toDegrees(a * 25)); rect(-6, -3, 12, 6, c("#d9a72e")); g.restore(); }
            }
        }

        if (db >= 0) explosion(t, db);
        drawCount(t, days);
        if (db > .08f && db < 2.8f) boom(db);
        g.restore();
        if (db >= 0 && db < .36f) g.drawColor(argb(.95f - db * 2.6f, 255, 252, 235));
    }

    private void explosion(float t, float db) {
        float cx = CAR_X, cy = CAR_Y - 70;
        for (int i = 0; i < 90; i++) {
            float born = .12f + i * .055f, age = db - born; if (age < 0 || age > 4.5f) continue;
            float spread = i < 25 ? 220 : 70, px = cx + (rnd(i, 31) - .5f) * spread + age * 45, py = cy - 20 - age * (120 + rnd(i, 32) * 80);
            float r = 50 + age * 55 + (i < 25 ? 60 : 0), al = .55f * (1 - age / 4.5f);
            begin(); circle(px, py, r); fill(i < 25 ? argb(al, 52, 48, 46) : argb(al, 96, 90, 86));
        }
        if (db > .3f) for (int k = 0; k < 6; k++) {
            float fx = cx - 120 + k * 45, fh = 50 + 35 * (float) Math.abs(Math.sin(t * 9 + k * 1.7)) + 20 * rnd(Math.floor(t * 14), k);
            begin(); path.moveTo(fx - 22, cy - 60); path.quadTo(fx - 18, cy - 70 - fh * .6f, fx, cy - 70 - fh);
            path.quadTo(fx + 18, cy - 70 - fh * .6f, fx + 22, cy - 60); path.close();
            fill(new LinearGradient(0, cy - 90 - fh, 0, cy - 60, new int[]{Color.argb(0, 255, 220, 90), Color.argb(242, 255, 170, 40), Color.argb(242, 230, 70, 20)},
                new float[]{0, .4f, 1}, Shader.TileMode.CLAMP));
        }
        for (float[] d : DEB) {
            float[] p = ballistic(cx, cy, d[0], d[1], db, d[5]);
            g.save(); g.translate(p[0], p[1]); g.rotate((float) Math.toDegrees(d[3] * Math.min(db, p[3])));
            rect(-d[2] / 2, -d[2] / 3, d[2], d[2] * .66f, DEB_COLORS[(int) d[4]]); g.restore();
        }
        { float[] w = ballistic(cx - 135, CAR_Y, -520, -950, db, CAR_Y + 10); float wx = w[0], wy = w[1];
          if (w[2] == 1) { float r = db - w[3]; wx = w[0] - 520 * (1 - (float) Math.exp(-r * 1.2)) / 1.2f; }
          g.save(); g.translate(wx, wy); g.rotate((float) Math.toDegrees(-db * 9));
          begin(); circle(0, 0, 50); fill(c("#151515")); begin(); circle(0, 0, 24); fill(c("#9a9da3")); rect(-4, -24, 8, 48, c("#151515")); g.restore(); }
        for (int i = 0; i < 3; i++) {
            float[] b = ROOF_BAGS[i], f = FLY[i], p = ballistic(cx + b[0], CAR_Y + b[1], f[0], f[1], db, f[3]);
            float ang = f[2] * Math.min(db, p[3]); boolean landed = p[2] == 1;
            if (landed) ang = (float) (Math.round(ang / (Math.PI / 2)) * (Math.PI / 2));
            suitcase(p[0], p[1] - (landed ? b[3] / 2 - 20 : 0), b[2], b[3], BAG_COLORS[i], ang);
        }
        if (db < 1.8f) {
            float R = 400 * (1 - (float) Math.exp(-db * 5)), al = db < .5f ? 1 : Math.max(0, 1 - (db - .5f) / 1.3f);
            for (int k = 0; k < 7; k++) {
                float ox = (rnd(k, 41) - .5f) * R * .9f, oy = (rnd(k, 42) - .5f) * R * .6f - db * 90, r = R * (.45f + rnd(k, 43) * .45f);
                if (r < 1) continue;
                begin(); circle(cx + ox, cy + oy, r);
                fill(new RadialGradient(cx + ox, cy + oy, r, new int[]{argb(al, 255, 250, 215), argb(al, 255, 190, 60), argb(al * .85f, 225, 70, 25), Color.argb(0, 120, 30, 10)},
                    new float[]{0, .35f, .7f, 1}, Shader.TileMode.CLAMP));
            }
        }
        if (db < .5f) { begin(); circle(cx, cy, 120 + db * 1700); stroke(argb(.6f * (1 - db / .5f), 255, 255, 255), 26); }
    }

    private void boom(float db) {
        float s = db < .33f ? easeBack(clamp(db / .33f)) : 1, al = db > 2.3f ? 1 - (db - 2.3f) / .5f : 1;
        int layer = al < 1 ? g.saveLayerAlpha(null, Math.round(clamp(al) * 255)) : g.save();
        g.translate(610, 680); g.rotate((float) Math.toDegrees(-.12)); g.scale(s, s);
        star(0, 0, 230, 150, 14, .2f); fill(c("#ffd23f")); stroke(c("#c0392b"), 12);
        text.setShadowLayer(0, 0, 0, 0); text.setTypeface(heavy); text.setTextSize(120);
        float base = 6 - (text.descent() + text.ascent()) / 2;
        text.setStyle(Paint.Style.STROKE); text.setStrokeWidth(14); text.setStrokeJoin(Paint.Join.ROUND); text.setColor(Color.WHITE);
        g.drawText("¡BUUM!", 0, base, text);
        text.setStyle(Paint.Style.FILL); text.setColor(c("#e63b1f")); g.drawText("¡BUUM!", 0, base, text);
        g.restoreToCount(layer);
    }

    private void drawCount(float t, int days) {
        float a = t - COUNT; if (a < 0) return;
        float appear = clamp(a / .4f);
        int layer = appear < 1 ? g.saveLayerAlpha(null, Math.round(appear * 255)) : g.save();
        text.setStyle(Paint.Style.FILL); text.setShadowLayer(22, 0, 4, Color.argb(140, 0, 0, 0)); text.setColor(Color.WHITE);
        if (days == 0) {
            text.setTypeface(heavy); text.setTextSize(150); g.drawText("¡Es hoy!", 1140, 260, text);
            text.setTypeface(bold); text.setTextSize(72); g.drawText("¡Nos vamos para el Huila!", 1140, 380, text);
            g.restoreToCount(layer); return;
        }
        text.setTypeface(bold); text.setTextSize(72); g.drawText(days == 1 ? "Falta" : "Faltan", 1140, 120, text);
        float roll = clamp(a / .9f); int shown = Math.max(days, Math.round(days + 15 * (1 - easeOut(roll))));
        float pop = a < .9f ? 1 : 1 + .12f * (float) (Math.exp(-(a - .9f) * 7) * Math.sin((a - .9f) * 18));
        g.save(); g.translate(1140, 340); g.scale(pop, pop);
        text.setTypeface(heavy); text.setTextSize(240);
        text.setShader(new LinearGradient(0, -200, 0, 0, c("#fff1b8"), c("#ffb52e"), Shader.TileMode.CLAMP));
        g.drawText(String.valueOf(shown), 0, 0, text); text.setShader(null); g.restore();
        text.setTypeface(bold); text.setTextSize(66);
        g.drawText((days == 1 ? "día" : "días") + " para el viaje al Huila", 1140, 445, text);
        text.setShadowLayer(0, 0, 0, 0);
        g.restoreToCount(layer);
    }

    private void prepare(int width, int height, float sx, float sy) {
        scaleX = sx; scaleY = sy; Canvas keep = g;
        background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        g = new Canvas(background); g.scale(sx, sy); paintBackground();
        soldier = Bitmap.createBitmap(Math.max(1, Math.round(340 * sx)), Math.max(1, Math.round(380 * sy)), Bitmap.Config.ARGB_8888);
        g = new Canvas(soldier); g.scale(sx, sy); soldierAt(230, 370);
        g = keep;
    }

    void release() { background = soldier = null; }
}
