package local.doja.voice;

import android.graphics.*;

/**
 * The trip-to-Huila scene, a line-by-line port of artifacts/andromeda-huila-preview.html (approved there first).
 * Five chubby travellers walk in, men in camouflage pop up and fire into the sky, the travellers run off, then the
 * empty car arrives and blows up. Drawn in a 2280x1080 design space scaled to the view; times match the prototype
 * and the sound baked into assets/huila-viaje.wav. Cartoon only: nobody is hit, the suitcases land intact.
 */
final class HuilaArt {
    static final float W = 2280, H = 1080;
    // timeline (seconds)
    static final float WALK_IN = .2f, WALK_STOP = 2.8f, COUNT = 3.0f, SCARE = 3.95f, RUN = 4.4f,
        CAR_IN = 6.6f, CAR_STOP = 8.4f, BOOM = 9.2f, DUCK = 12.0f, END = 16f;
    static final float GAP = .085f;
    // layout
    static final float HORIZON = 560, VERGE = 690, ROAD_TOP = 800, ROAD_BOT = 955, CAR_X = 900, CAR_Y = 888;
    static final int CAR_COLOR = c("#e2553b");

    // men in camouflage: x, y (feet), scale, rise time, rifle tilt to the sky
    static final float[][] SOLDIERS = {
        {1690, 700, .6f, 3.62f, 1.2f}, {1960, 705, .6f, 3.42f, .92f}, {2200, 718, .64f, 3.7f, 1.15f},
        {1560, 742, .74f, 3.5f, 1.02f}, {1810, 800, 1, 3.4f, .95f}, {2090, 800, 1, 3.55f, 1.12f}};
    static final float[] SHOT_T; static final int[] SHOT_WHO, SHOT_K;
    static {
        int n = SOLDIERS.length * 15; float[] t = new float[n]; int[] who = new int[n], k = new int[n]; int m = 0;
        for (int s = 0; s < SOLDIERS.length; s++) for (int b = 0; b < 3; b++) for (int i = 0; i < 5; i++) {
            t[m] = SOLDIERS[s][3] + .45f + b * .95f + (s % 3) * .11f + i * GAP; who[m] = s; k[m] = s * 100 + b * 10 + i; m++;
        }
        Integer[] order = new Integer[n]; for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(t[a], t[b]));
        SHOT_T = new float[n]; SHOT_WHO = new int[n]; SHOT_K = new int[n];
        for (int i = 0; i < n; i++) { SHOT_T[i] = t[order[i]]; SHOT_WHO[i] = who[order[i]]; SHOT_K[i] = k[order[i]]; }
    }

    // the five travellers
    private static final class Person {
        final float stop, y; final int shirt, pants, skin, hair, dress, cap; final boolean female, mustache, bald, ponytail, vueltiao; final String number;
        Person(float stop, float y, int shirt, int pants, int skin, int hair, int dress, int cap, boolean female, boolean mustache,
               boolean bald, boolean ponytail, boolean vueltiao, String number) {
            this.stop = stop; this.y = y; this.shirt = shirt; this.pants = pants; this.skin = skin; this.hair = hair; this.dress = dress; this.cap = cap;
            this.female = female; this.mustache = mustache; this.bald = bald; this.ponytail = ponytail; this.vueltiao = vueltiao; this.number = number;
        }
    }
    private static final Person[] PEOPLE = {
        new Person(470, 912, c("#e74c3c"), c("#34495e"), c("#e0ac7e"), 0, 0, c("#2e6fd1"), false, true, false, false, false, null),
        new Person(610, 896, 0, 0, c("#c68863"), c("#1d1410"), c("#ff7eb6"), 0, true, false, false, false, false, null),
        new Person(750, 914, c("#f9d71c"), c("#1f4fa3"), c("#d39a6a"), 0, 0, 0, false, false, true, false, false, "10"),
        new Person(890, 898, 0, 0, c("#e8b590"), c("#6b3a1f"), c("#8e44ad"), 0, true, false, false, true, false, null),
        new Person(1030, 912, c("#3498db"), c("#c8b48a"), c("#b97a50"), 0, 0, 0, false, false, false, false, true, null),
    };

    private Canvas g;
    private final Path path = new Path(), oval = new Path(), blob = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final Matrix matrix = new Matrix();
    private final Typeface heavy = Typeface.create(Typeface.DEFAULT, 900, false), bold = Typeface.create(Typeface.DEFAULT, 800, false),
        semi = Typeface.create(Typeface.DEFAULT, 600, false);
    private Bitmap background;
    private final Bitmap[] soldiers = new Bitmap[SOLDIERS.length];
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
    static float deg(double rad) { return (float) Math.toDegrees(rad); }

    private void begin() { path.reset(); }
    private void rr(float x, float y, float w, float h, float r) { path.addRoundRect(x, y, x + w, y + h, r, r, Path.Direction.CW); }
    private void ellipse(float x, float y, float rx, float ry, float rot, Path into) {
        box.set(x - rx, y - ry, x + rx, y + ry);
        if (rot == 0) { into.addOval(box, Path.Direction.CW); return; }
        oval.reset(); oval.addOval(box, Path.Direction.CW); matrix.setRotate(deg(rot), x, y); oval.transform(matrix); into.addPath(oval);
    }
    private void ellipse(float x, float y, float rx, float ry, float rot, int color) { begin(); ellipse(x, y, rx, ry, rot, path); fill(color); }
    private void circle(float x, float y, float r, int color) { fill.setShader(null); fill.setColor(color); g.drawCircle(x, y, r, fill); }
    private void poly(float... p) { begin(); path.moveTo(p[0], p[1]); for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]); path.close(); }
    private void fill(int color) { fill.setShader(null); fill.setColor(color); g.drawPath(path, fill); }
    private void fill(Shader shader) { fill.setColor(Color.BLACK); fill.setShader(shader); g.drawPath(path, fill); fill.setShader(null); }
    private void stroke(int color, float width) { line.setShader(null); line.setColor(color); line.setStrokeWidth(width); g.drawPath(path, line); }
    private void rect(float x, float y, float w, float h, int color) { fill.setShader(null); fill.setColor(color); g.drawRect(x, y, x + w, y + h, fill); }
    private void seg(float x1, float y1, float x2, float y2, int color, float width) {
        line.setShader(null); line.setColor(color); line.setStrokeWidth(width); g.drawLine(x1, y1, x2, y2, line);
    }
    private void arc(float cx, float cy, float r, double from, double to) {
        box.set(cx - r, cy - r, cx + r, cy + r); path.arcTo(box, deg(from), deg(to - from), true);
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
    private void centered(String value, float x, float y) { g.drawText(value, x, y - (text.descent() + text.ascent()) / 2, text); }

    // ---------- static background ----------
    private void paintBackground() {
        begin(); path.addRect(0, 0, W, H, Path.Direction.CW);
        fill(new LinearGradient(0, 0, 0, HORIZON + 120, new int[]{c("#6fb9e6"), c("#b9def0"), c("#f7d9a6")}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP));
        begin(); path.addRect(1600, 0, 2300, 520, Path.Direction.CW);
        fill(new RadialGradient(1960, 210, 260, new int[]{Color.argb(255, 255, 246, 200), Color.argb(230, 255, 226, 140), Color.argb(0, 255, 226, 140)},
            new float[]{0, .25f, 1}, Shader.TileMode.CLAMP));
        circle(1960, 210, 70, c("#fff6d0"));
        float[][] clouds = {{260, 120, 1}, {1500, 90, .8f}, {2150, 380, .6f}, {880, 210, .55f}};
        for (float[] cl : clouds) {
            float x = cl[0], y = cl[1], k = cl[2]; begin();
            ellipse(x, y, 120 * k, 38 * k, 0, path); ellipse(x + 70 * k, y - 22 * k, 80 * k, 40 * k, 0, path); ellipse(x - 80 * k, y + 5 * k, 70 * k, 28 * k, 0, path);
            fill(Color.argb(217, 255, 255, 255));
        }
        poly(-60, 560, 180, 360, 300, 262, 380, 236, 450, 248, 520, 280, 640, 360, 900, 560);
        fill(new LinearGradient(0, 230, 0, HORIZON, c("#8fa3bb"), c("#6f8499"), Shader.TileMode.CLAMP));
        poly(216, 330, 300, 262, 380, 236, 450, 248, 520, 280, 588, 326, 540, 318, 505, 300, 470, 326, 430, 296, 392, 330, 350, 300, 300, 332, 262, 312);
        fill(c("#f4f8fc"));
        begin(); path.moveTo(0, 560);
        for (int x = 0; x <= W; x += 40) path.lineTo(x, 470 + (float) (Math.sin(x / 170.0) * 34 + Math.sin(x / 63.0) * 12) + (x < 700 ? 40 : 0));
        path.lineTo(W, 600); path.lineTo(0, 600); path.close(); fill(c("#86a892"));
        begin(); path.moveTo(0, 640);
        for (int x = 0; x <= W; x += 30) path.lineTo(x, 545 + (float) (Math.sin(x / 210.0 + 1) * 26 + Math.sin(x / 90.0) * 8));
        path.lineTo(W, 700); path.lineTo(0, 700); path.close(); fill(c("#5f9a55"));
        begin(); path.moveTo(-10, 628);
        for (int x = 0; x <= 1500; x += 30) path.lineTo(x, 628 + (float) Math.sin(x / 260.0) * 18);
        for (int x = 1500; x >= 0; x -= 30) path.lineTo(x, 662 + (float) Math.sin(x / 260.0) * 18 + x / 120f);
        path.close(); fill(c("#58a6cc"));
        for (int i = 0; i < 14; i++) { float x = 60 + i * 100, y = 645 + (float) Math.sin(x / 260.0) * 18 + x / 240;
            seg(x, y, x + 40, y, Color.argb(115, 255, 255, 255), 3); }
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
        begin(); path.addRect(0, VERGE, W, ROAD_TOP, Path.Direction.CW);
        fill(new LinearGradient(0, VERGE, 0, ROAD_TOP, c("#b9b866"), c("#949c47"), Shader.TileMode.CLAMP));
        for (int i = 0; i < 160; i++) { float x = rnd(i, 3) * W, y = VERGE + 15 + rnd(i, 4) * (ROAD_TOP - VERGE - 20);
            seg(x, y, x - 6, y - 16, c("#7d8a3a"), 3); seg(x, y, x + 5, y - 14, c("#7d8a3a"), 3); }
        rect(0, ROAD_TOP, W, ROAD_BOT - ROAD_TOP, c("#4b4b51"));
        rect(0, ROAD_TOP + 6, W, 6, c("#e9e9e9")); rect(0, ROAD_BOT - 12, W, 6, c("#e9e9e9"));
        for (int x = 0; x < W; x += 150) rect(x, (ROAD_TOP + ROAD_BOT) / 2 - 4, 85, 8, c("#f2c230"));
        rect(0, ROAD_BOT, W, H - ROAD_BOT, c("#a2a752"));
        for (int i = 0; i < 120; i++) { float x = rnd(i, 7) * W, y = ROAD_BOT + 25 + rnd(i, 8) * 110; int col = c("#7f8a3c");
            seg(x, y, x - 8, y - 24, col, 4); seg(x, y, x + 7, y - 20, col, 4); seg(x, y, x, y - 28, col, 4); }
        float[][] rocks = {{300, 1010, 40}, {1650, 1040, 30}, {2000, 995, 46}};
        for (float[] r : rocks) ellipse(r[0], r[1], r[2], r[2] * .55f, 0, c("#8f8676"));
        cactus(1420, 770, 230); cactus(2240, 780, 250); cactus(230, 745, 150);
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
        g.save(); g.translate(cx, cy); g.rotate(deg(ang));
        begin(); path.moveTo(-w * .18f, -h / 2); path.lineTo(-w * .18f, -h / 2 - 16); path.lineTo(w * .18f, -h / 2 - 16); path.lineTo(w * .18f, -h / 2);
        line.setStrokeCap(Paint.Cap.BUTT); stroke(c("#3a2a1a"), 6); line.setStrokeCap(Paint.Cap.ROUND);
        begin(); rr(-w / 2, -h / 2, w, h, 10); fill(col); stroke(Color.argb(64, 0, 0, 0), 4);
        rect(-w * .3f, -h / 2, 9, h, Color.argb(191, 80, 50, 25)); rect(w * .3f - 9, -h / 2, 9, h, Color.argb(191, 80, 50, 25));
        circle(w * .05f, h * .05f, Math.min(w, h) * .12f, Color.argb(204, 255, 255, 255));
        g.restore();
    }
    private void car(float x, float y, float roll, boolean burnt, boolean bags) {
        g.save(); g.translate(x, y);
        ellipse(0, 44, 250, 16, 0, Color.argb(71, 0, 0, 0));
        if (burnt) { g.translate(135, 0); g.rotate(deg(-0.06)); g.translate(-135, 14); }
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
        ellipse(214, -66, 14, 10, 0, burnt ? c("#222222") : c("#ffe9a8"));
        rect(-230, -82, 14, 22, burnt ? c("#222222") : c("#b3202a"));
        begin(); rr(-232, -32, 464, 20, 9); fill(c("#2a2a2a"));
        if (!burnt) rect(-200, -90, 380, 8, Color.argb(46, 255, 255, 255));
        float[] wheels = burnt ? new float[]{135} : new float[]{-135, 135};
        for (float wx : wheels) {
            circle(wx, 0, 50, c("#151515")); circle(wx, 0, 24, burnt ? c("#3a3533") : c("#c9ccd1"));
            for (int k = 0; k < 5; k++) { double a = roll + k * 1.2566;
                seg(wx, 0, wx + (float) Math.cos(a) * 24, (float) Math.sin(a) * 24, burnt ? c("#222222") : c("#7b7f86"), 5); }
        }
        if (burnt) ellipse(-150, 20, 40, 12, 0, c("#151515"));
        g.restore();
    }

    // ---------- the men in camouflage (one cached drawing each) ----------
    private interface Shape { void add(); }
    private static final int[] CAMO = {c("#4a5926"), c("#8c7b4b"), c("#2f3a1e"), c("#a39a68")};
    private static final float[] MUZZLE = {-212, -224}, PIVOT = {0, -240};
    /** Soldier bitmap bounds around his feet, in design units: wide enough for the rifle raised to the sky. */
    private static final float BMP_LEFT = 240, BMP_TOP = 460, BMP_RIGHT = 120, BMP_BOTTOM = 10;
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
    private void soldierAt(float x, float y, float aim) {
        int skin = c("#c98b5e");
        g.save(); g.translate(x, y);
        begin(); rr(-46, -24, 42, 24, 6); rr(4, -24, 42, 24, 6); fill(c("#3b2a1d"));
        camo(() -> { rr(-36, -150, 32, 130, 8); rr(8, -150, 32, 130, 8); }, 11);
        camo(() -> rr(-50, -282, 100, 148, 22), 21);
        rect(-50, -150, 100, 14, c("#2b2b22"));
        rect(-38, -225, 30, 30, Color.argb(115, 30, 35, 15)); rect(4, -225, 30, 30, Color.argb(115, 30, 35, 15));
        g.save(); g.translate(PIVOT[0], PIVOT[1]); g.rotate(deg(aim)); g.translate(-PIVOT[0], -PIVOT[1]);
        camo(() -> limb(18, -255, -40, -214, 24), 31);
        begin(); rr(0, -236, 74, 26, 8); fill(c("#5a3a22"));
        rect(-96, -238, 104, 28, c("#2d2d2d")); rect(-68, -252, 34, 12, c("#2d2d2d"));
        g.save(); g.translate(-64, -212); g.rotate(deg(-0.18)); rect(0, 0, 22, 46, c("#262626")); g.restore();
        rect(-158, -234, 64, 20, c("#3d3d3d")); rect(-208, -228, 52, 8, c("#1b1b1b"));
        circle(-42, -214, 13, skin);
        camo(() -> limb(-12, -258, -126, -228, 26), 41);
        circle(-128, -226, 13, skin);
        g.restore();
        circle(-6, -312, 33, skin);
        begin(); rr(-44, -322, 36, 12, 5); fill(c("#1a1a1a"));
        seg(-40, -294, -18, -296, c("#3b2413"), 6);
        camo(() -> { arc(-4, -318, 42, Math.PI, 2 * Math.PI); path.lineTo(46, -316); path.lineTo(-54, -316); path.close(); }, 51);
        rect(-56, -320, 104, 7, c("#3c4721"));
        g.restore();
    }
    private final float[] muzzle = new float[2];
    private float[] muzzleOf(float[] sd) {
        float vx = MUZZLE[0] - PIVOT[0], vy = MUZZLE[1] - PIVOT[1], co = (float) Math.cos(sd[4]), si = (float) Math.sin(sd[4]);
        muzzle[0] = sd[0] + sd[2] * (PIVOT[0] + vx * co - vy * si); muzzle[1] = sd[1] + sd[2] * (PIVOT[1] + vx * si + vy * co); return muzzle;
    }
    private void bush(float x, float y, float s) {
        float[][] blobs = {{-120, -40, 70}, {-50, -80, 85}, {40, -70, 90}, {120, -40, 70}, {0, -30, 90}, {-150, -10, 50}, {150, -10, 55}};
        g.save(); g.translate(x, y); g.scale(s, s);
        for (float[] b : blobs) circle(b[0], b[1], b[2], c("#3f6b2f"));
        for (float[] b : blobs) circle(b[0] - b[2] * .2f, b[1] - b[2] * .25f, b[2] * .6f, c("#4f8139"));
        g.restore();
    }

    // ---------- the travellers ----------
    private static float personX(Person p, int i, float t) {
        if (t < RUN + i * .07f) return p.stop - 1500 * (1 - clamp((t - WALK_IN) / (WALK_STOP - WALK_IN)));
        float r = t - (RUN + i * .07f);
        return p.stop - 260 * r * r - 700 * r;   // speeds up as they flee
    }
    private void person(Person p, int i, float t) {
        float x = personX(p, i, t); if (x < -200 || x > W + 200) return;
        boolean running = t >= RUN + i * .07f, walking = !running && t < WALK_STOP && t > WALK_IN, scared = t >= SCARE + i * .04f;
        float w = running ? t * 19 + i : t * 9 + i * 1.3f;
        float js = t - SCARE - i * .04f, jump = js >= 0 && js < .35f ? (float) Math.sin(js / .35f * Math.PI) * 55 : 0;
        float bob = walking ? Math.abs((float) Math.sin(w)) * 6 : running ? Math.abs((float) Math.sin(w)) * 14 : 0;
        g.save(); g.translate(x, p.y - jump - bob);
        ellipse(0, jump + bob, 62, 12, 0, Color.argb(56, 0, 0, 0));
        g.scale(running ? -1 : 1, 1); if (running) g.rotate(deg(.14));
        float swing = walking ? (float) Math.sin(w) * .5f : running ? (float) Math.sin(w) * .95f : 0;
        float[][] legs = {{-17, swing}, {17, -swing}};
        for (float[] l : legs) {
            g.save(); g.translate(l[0], -58); g.rotate(deg(l[1]));
            begin(); rr(-13, 0, 26, 56, 12); fill(p.female ? p.skin : p.pants);
            ellipse(8, 56, 20, 10, 0, c("#2b2b2b")); g.restore();
        }
        if (p.female) {
            begin(); path.moveTo(-58, -40); path.quadTo(-70, -120, -40, -150); path.lineTo(40, -150); path.quadTo(72, -120, 60, -40); path.close(); fill(p.dress);
            ellipse(0, -110, 54, 52, 0, p.dress);
        } else {
            ellipse(0, -108, 56, 60, 0, p.shirt);
            if (p.number != null) {   // the shirt number never reads backwards
                g.save(); if (running) g.scale(-1, 1);
                text.setShadowLayer(0, 0, 0, 0); text.setStyle(Paint.Style.FILL); text.setTypeface(heavy); text.setTextSize(34); text.setColor(c("#1f4fa3"));
                g.drawText(p.number, running ? -4 : 4, -98, text); g.restore();
            }
        }
        ellipse(-16, -128, 18, 26, -.4f, Color.argb(46, 255, 255, 255));
        float[][] arms = {{-46, 1}, {46, -1}};
        for (float[] a0 : arms) {
            float a = scared ? (a0[1] > 0 ? -2.6f : 2.6f) + (float) Math.sin(t * 30 + a0[0]) * .15f : a0[1] * swing * .8f;
            g.save(); g.translate(a0[0], -140); g.rotate(deg(a));
            begin(); rr(-11, 0, 22, 52, 11); fill(p.female ? p.skin : p.shirt);
            circle(0, 54, 11, p.skin); g.restore();
        }
        if (p.female) { ellipse(-6, -178, 36, 44, 0, p.hair); if (p.ponytail) ellipse(-36, -190, 14, 26, .5f, p.hair); }
        circle(4, -188, 31, p.skin);
        circle(24, -178, 8, Color.argb(89, 230, 90, 90));
        if (p.female) { begin(); arc(4, -194, 31, Math.PI * 1.05, Math.PI * 1.95); path.close(); fill(p.hair); }
        if (p.cap != 0) { begin(); arc(4, -200, 31, Math.PI, 2 * Math.PI); path.close(); fill(p.cap); rect(4, -204, 46, 9, p.cap); }
        if (p.bald) { ellipse(-6, -208, 10, 5, -.3f, Color.argb(89, 255, 255, 255)); rect(-26, -196, 8, 18, c("#2b1d14")); }
        if (p.vueltiao) {
            int straw = c("#f4ecd8"); ellipse(4, -210, 56, 12, 0, straw);
            begin(); rr(-20, -246, 48, 38, 12); fill(straw);
            rect(-20, -232, 48, 5, c("#1b1b1b")); rect(-20, -222, 48, 5, c("#1b1b1b")); rect(-44, -212, 96, 3, c("#1b1b1b"));
        }
        if (scared) {
            circle(14, -192, 9, Color.WHITE); circle(30, -192, 9, Color.WHITE); circle(16, -191, 4, c("#111111")); circle(32, -191, 4, c("#111111"));
            circle(24, -170, 7, c("#5a1f1f"));
        } else {
            circle(16, -192, 4, c("#111111")); circle(30, -192, 4, c("#111111"));
            begin(); arc(24, -178, 9, .2, Math.PI - .2); stroke(c("#5a1f1f"), 3);
        }
        if (p.mustache) ellipse(26, -176, 13, 5, 0, c("#2b1d14"));
        if (running) ellipse(-22, -214 - (t * 120 % 30), 5, 8, 0, Color.argb(230, 120, 190, 255));
        g.restore();
        float ex = t - (SCARE + i * .04f);
        if (ex > 0 && ex < .9f) {   // "!" when the shooting starts
            float s = ex < .15f ? easeBack(ex / .15f) : 1;
            g.save(); g.translate(x + 10, p.y - 290 - jump); g.scale(s, s);
            circle(0, 0, 30, Color.WHITE);
            line.setColor(c("#e63b1f")); line.setStrokeWidth(4); g.drawCircle(0, 0, 30, line);
            text.setShadowLayer(0, 0, 0, 0); text.setStyle(Paint.Style.FILL); text.setTypeface(heavy); text.setTextSize(44); text.setColor(c("#e63b1f"));
            centered("!", 0, 2); g.restore();
        }
        if (running) for (int k = 0; k < 4; k++) {   // dust behind their feet
            float age = (t * 3 + k * .25f) % 1;
            circle(x + 40 + age * 70, p.y - 6 - age * 20, 10 + age * 18, argb(.5f * (1 - age), 200, 190, 160));
        }
    }

    // ---------- explosion pieces ----------
    private static final int DEBRIS = 46;
    private static final float[][] DEB = new float[DEBRIS][6];
    private static final int[] DEB_COLORS = {CAR_COLOR, c("#262626"), c("#8a8a8a"), c("#a6d8ee"), c("#f2b632")};
    private static final float[][] FLY = {{-420, -1150, -6, 905}, {330, -1350, 7, 930}, {120, -1700, -9, 880}};
    static {
        for (int i = 0; i < DEBRIS; i++) {
            double a = -Math.PI / 2 + (rnd(i, 1) - .5) * 2.6, sp = 700 + rnd(i, 2) * 1100;
            DEB[i] = new float[]{(float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp), 8 + rnd(i, 3) * 26, rnd(i, 4) * 12 - 6, i % 5, 850 + rnd(i, 5) * 150};
        }
    }
    private final float[] bal = new float[4];
    private float[] ballistic(float x0, float y0, float vx, float vy, float dt, float ground) {
        float grav = 1900, tl = (float) ((-vy + Math.sqrt(vy * vy + 2 * grav * (ground - y0))) / grav), d = Math.min(dt, tl);
        bal[0] = x0 + vx * d; bal[1] = y0 + vy * d + .5f * grav * d * d; bal[2] = dt >= tl ? 1 : 0; bal[3] = tl; return bal;
    }

    // ---------- frame ----------
    void draw(Canvas canvas, float t, int days, int width, int height) {
        g = canvas;
        float sx = width / W, sy = height / H;
        if (background == null || sx != scaleX || sy != scaleY) prepare(width, height, sx, sy);
        float amp = 0, db = t - BOOM;
        for (float s : SHOT_T) if (t >= s && t < s + .05f) { amp = 3; break; }
        if (db >= 0 && db < 2) amp = Math.max(amp, 36 * (float) Math.exp(-db * 2.4));
        int fr = (int) Math.floor(t * 60); float ox = (rnd(fr, 1) - .5f) * 2 * amp, oy = (rnd(fr, 2) - .5f) * 2 * amp;
        if (amp > 0) g.drawBitmap(background, 0, 0, null);   // fills the edge the shake uncovers
        g.save(); g.translate(ox * sx, oy * sy); g.drawBitmap(background, 0, 0, null); g.scale(sx, sy);

        // the men in camouflage, back row first, each behind his own bush
        for (int who = 0; who < SOLDIERS.length; who++) {
            float[] sd = SOLDIERS[who]; float s = sd[2];
            float up = clamp((t - sd[3]) / .45f), down = clamp((t - DUCK - who * .06f) / .5f), rise = easeOut(up) * (1 - easeOut(down));
            float kick = 0;
            for (int i = 0; i < SHOT_T.length; i++) if (SHOT_WHO[i] == who && t >= SHOT_T[i] && t < SHOT_T[i] + .05f) { kick = 6 * s; break; }
            if (rise > 0) {
                float x = sd[0] + kick, y = sd[1] + (1 - rise) * 360 * s;
                g.save(); g.clipRect(-100, -100, W + 100, sd[1] - 2);
                box.set(x - BMP_LEFT * s, y - BMP_TOP * s, x + BMP_RIGHT * s, y + BMP_BOTTOM * s); g.drawBitmap(soldiers[who], null, box, bitmapPaint);
                g.restore();
            }
            bush(sd[0] + 10 * s, sd[1] + 8 * s, s);
        }

        // shots into the sky: flash at the barrel and a tracer going up
        for (int i = 0; i < SHOT_T.length; i++) {
            float a = t - SHOT_T[i]; if (a < 0 || a > .35f) continue;
            float[] sd = SOLDIERS[SHOT_WHO[i]]; float s = sd[2]; int k = SHOT_K[i];
            float[] m = muzzleOf(sd); float mx = m[0], my = m[1];
            double dir = Math.PI + sd[4] + (rnd(k, 7) - .5f) * .18f; float dx = (float) Math.cos(dir), dy = (float) Math.sin(dir);
            if (a < .045f) {
                float r = 110 * s;
                begin(); path.addRect(mx - r, my - r, mx + r, my + r, Path.Direction.CW);
                fill(new RadialGradient(mx, my, r, Color.argb(204, 255, 240, 180), Color.argb(0, 255, 200, 80), Shader.TileMode.CLAMP));
                star(mx + dx * 18 * s, my + dy * 18 * s, (40 + rnd(k, 9) * 26) * s, 12 * s, 7, rnd(k, 10) * 3); fill(c("#fff6c8"));
                star(mx + dx * 12 * s, my + dy * 12 * s, 22 * s, 8 * s, 7, rnd(k, 11) * 3); fill(c("#ffb02e"));
            }
            float p = a / .35f, L = 900, b0 = Math.max(0, p - .15f);
            seg(mx + dx * L * b0, my + dy * L * b0, mx + dx * L * p, my + dy * L * p, argb(.95f * (1 - p), 255, 225, 130), 5 * s);
        }
        for (int i = 0; i < SHOT_T.length; i++) {   // casings
            float a = t - SHOT_T[i]; if (a < 0 || a > .7f) continue;
            float[] sd = SOLDIERS[SHOT_WHO[i]]; float s = sd[2];
            float cx = sd[0] + (20 + 170 * a) * s, cy = sd[1] + (-240 - 380 * a + 1200 * a * a) * s;
            if (cy < sd[1] + 30) { g.save(); g.translate(cx, cy); g.rotate(deg(a * 25)); rect(-5 * s, -3 * s, 10 * s, 6 * s, c("#d9a72e")); g.restore(); }
        }

        // the travellers walk in, jump, and run away
        for (int i = 0; i < PEOPLE.length; i++) person(PEOPLE[i], i, t);

        // the car arrives once they have gone
        float pIn = clamp((t - CAR_IN) / (CAR_STOP - CAR_IN)), carX = lerp(-420, CAR_X, easeOut(pIn)), ds = t - CAR_STOP;
        float bob = t < CAR_STOP ? (float) Math.sin(t * 30) * 1.5f : 5 * (float) (Math.exp(-ds * 6) * Math.sin(ds * 26));
        if (t >= CAR_IN && t < BOOM) car(carX, CAR_Y + bob, (carX + 420) / 50, false, true);
        else if (db > .12f) car(CAR_X, CAR_Y, 0, true, false);

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
            circle(px, py, r, i < 25 ? argb(al, 52, 48, 46) : argb(al, 96, 90, 86));
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
            g.save(); g.translate(p[0], p[1]); g.rotate(deg(d[3] * Math.min(db, p[3])));
            rect(-d[2] / 2, -d[2] / 3, d[2], d[2] * .66f, DEB_COLORS[(int) d[4]]); g.restore();
        }
        { float[] w = ballistic(cx - 135, CAR_Y, -520, -950, db, CAR_Y + 10); float wx = w[0], wy = w[1];
          if (w[2] == 1) { float r = db - w[3]; wx = w[0] - 520 * (1 - (float) Math.exp(-r * 1.2)) / 1.2f; }
          g.save(); g.translate(wx, wy); g.rotate(deg(-db * 9));
          circle(0, 0, 50, c("#151515")); circle(0, 0, 24, c("#9a9da3")); rect(-4, -24, 8, 48, c("#151515")); g.restore(); }
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
                fill.setShader(new RadialGradient(cx + ox, cy + oy, r, new int[]{argb(al, 255, 250, 215), argb(al, 255, 190, 60), argb(al * .85f, 225, 70, 25), Color.argb(0, 120, 30, 10)},
                    new float[]{0, .35f, .7f, 1}, Shader.TileMode.CLAMP));
                fill.setColor(Color.BLACK); g.drawCircle(cx + ox, cy + oy, r, fill); fill.setShader(null);
            }
        }
        if (db < .5f) { line.setColor(argb(.6f * (1 - db / .5f), 255, 255, 255)); line.setStrokeWidth(26); g.drawCircle(cx, cy, 120 + db * 1700, line); }
    }

    private void boom(float db) {
        float s = db < .33f ? easeBack(clamp(db / .33f)) : 1, al = db > 2.3f ? 1 - (db - 2.3f) / .5f : 1;
        int layer = al < 1 ? g.saveLayerAlpha(null, Math.round(clamp(al) * 255)) : g.save();
        g.translate(CAR_X - 210, 700); g.rotate(deg(-.12)); g.scale(s, s);
        star(0, 0, 230, 150, 14, .2f); fill(c("#ffd23f")); stroke(c("#c0392b"), 12);
        text.setShadowLayer(0, 0, 0, 0); text.setTypeface(heavy); text.setTextSize(120);
        text.setStyle(Paint.Style.STROKE); text.setStrokeWidth(14); text.setStrokeJoin(Paint.Join.ROUND); text.setColor(Color.WHITE);
        centered("¡BUUM!", 0, 6);
        text.setStyle(Paint.Style.FILL); text.setColor(c("#e63b1f")); centered("¡BUUM!", 0, 6);
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
            text.setShadowLayer(0, 0, 0, 0); g.restoreToCount(layer); return;
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
        for (int i = 0; i < SOLDIERS.length; i++) {
            float s = SOLDIERS[i][2];
            soldiers[i] = Bitmap.createBitmap(Math.max(1, Math.round((BMP_LEFT + BMP_RIGHT) * s * sx)), Math.max(1, Math.round((BMP_TOP + BMP_BOTTOM) * s * sy)), Bitmap.Config.ARGB_8888);
            g = new Canvas(soldiers[i]); g.scale(s * sx, s * sy); soldierAt(BMP_LEFT, BMP_TOP, SOLDIERS[i][4]);
        }
        g = keep;
    }

    void release() { background = null; java.util.Arrays.fill(soldiers, null); }
}
