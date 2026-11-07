package local.doja.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Captura JPEG a 640x480 y 5 fps para la camara de vigilancia. No escribe en
 * disco: cada fotograma se entrega al espectador y se descarta.
 */
final class CameraStream {
    interface Sink {
        void onJpeg(byte[] jpeg) throws Exception;
    }

    interface Failure {
        void onError();
    }

    private static final int WIDTH = 640, HEIGHT = 480;
    private static final long FRAME_MS = 200L;

    private final Object lock = new Object();
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Sink sink;
    private Failure failure;
    private volatile boolean active;
    private long lastFrame;

    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    boolean start(Context context, Sink sink, Failure failure) {
        if (!permitted(context)) return false;
        CameraManager manager = context.getSystemService(CameraManager.class);
        String id = frontCamera(manager);
        if (manager == null || id == null) return false;
        synchronized (lock) {
            if (active) return false;
            active = true;
            this.sink = sink;
            this.failure = failure;
            thread = new HandlerThread("andromeda-camera");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    if (!active) {
                        camera.close();
                        return;
                    }
                    device = camera;
                    openSession();
                }

                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    fail();
                }

                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    fail();
                }
            }, handler);
            return true;
        } catch (Exception error) {
            stop();
            return false;
        }
    }

    void stop() {
        synchronized (lock) {
            active = false;
            failure = null;
            sink = null;
        }
        closeAll();
    }

    private void openSession() {
        try {
            reader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(source -> {
                Image image = null;
                try {
                    image = source.acquireNextImage();
                    long now = SystemClock.elapsedRealtime();
                    // 5 fps is enough for a live view; extra frames are dropped.
                    if (now - lastFrame < FRAME_MS) return;
                    lastFrame = now;
                    byte[] jpeg = jpeg(image);
                    Sink target = sink;
                    if (jpeg != null && target != null && active) target.onJpeg(jpeg);
                } catch (Exception failure) {
                    fail();
                } finally {
                    if (image != null) image.close();
                }
            }, handler);
            List<android.view.Surface> targets = new ArrayList<>();
            targets.add(reader.getSurface());
            device.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (!active) {
                        configured.close();
                        return;
                    }
                    session = configured;
                    try {
                        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(reader.getSurface());
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        session.setRepeatingRequest(builder.build(), null, handler);
                    } catch (Exception error) {
                        fail();
                    }
                }

                @Override public void onConfigureFailed(CameraCaptureSession configured) {
                    configured.close();
                    fail();
                }
            }, handler);
        } catch (Exception error) {
            fail();
        }
    }

    /**
     * YUV_420_888 to JPEG. A repeating JPEG request saturates some HALs after a
     * few seconds; a YUV stream is a normal preview and stays alive, and the
     * conversion is cheap at 5 fps and 640x480.
     */
    private static byte[] jpeg(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) return null;
        Image.Plane[] planes = image.getPlanes();
        int width = image.getWidth(), height = image.getHeight();
        int yRow = planes[0].getRowStride(), yStep = planes[0].getPixelStride();
        int uRow = planes[1].getRowStride(), uStep = planes[1].getPixelStride();
        int vRow = planes[2].getRowStride(), vStep = planes[2].getPixelStride();
        java.nio.ByteBuffer y = planes[0].getBuffer();
        java.nio.ByteBuffer u = planes[1].getBuffer();
        java.nio.ByteBuffer v = planes[2].getBuffer();
        byte[] nv21 = new byte[width * height * 3 / 2];
        int offset = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                nv21[offset++] = y.get(row * yRow + column * yStep);
            }
        }
        for (int row = 0; row < height / 2; row++) {
            for (int column = 0; column < width / 2; column++) {
                nv21[offset++] = v.get(row * vRow + column * vStep);
                nv21[offset++] = u.get(row * uRow + column * uStep);
            }
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        android.graphics.YuvImage frame = new android.graphics.YuvImage(
            nv21, ImageFormat.NV21, width, height, null);
        if (!frame.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 70, output)) return null;
        return output.toByteArray();
    }

    private void fail() {
        Failure callback;
        synchronized (lock) {
            if (!active) return;
            active = false;
            callback = failure;
            failure = null;
            sink = null;
        }
        closeAll();
        if (callback != null) callback.onError();
    }

    private void closeAll() {
        CameraCaptureSession currentSession = session;
        session = null;
        if (currentSession != null) {
            try {
                currentSession.close();
            } catch (RuntimeException ignored) { }
        }
        CameraDevice currentDevice = device;
        device = null;
        if (currentDevice != null) {
            try {
                currentDevice.close();
            } catch (RuntimeException ignored) { }
        }
        ImageReader currentReader = reader;
        reader = null;
        if (currentReader != null) {
            try {
                currentReader.close();
            } catch (RuntimeException ignored) { }
        }
        HandlerThread currentThread = thread;
        thread = null;
        handler = null;
        if (currentThread != null) currentThread.quitSafely();
    }

    /** The Moto stands facing the room, so the front lens is the security view. */
    private static String frontCamera(CameraManager manager) {
        if (manager == null) return null;
        try {
            for (String id : manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
            }
        } catch (Exception ignored) { }
        return null;
    }
}
