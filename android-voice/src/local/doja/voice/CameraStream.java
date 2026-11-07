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
        String id = backCamera(manager);
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
            reader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(source -> {
                Image image = null;
                try {
                    image = source.acquireNextImage();
                    long now = SystemClock.elapsedRealtime();
                    // 5 fps is enough for a live view; extra frames are dropped.
                    if (now - lastFrame < FRAME_MS) return;
                    lastFrame = now;
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] jpeg = new byte[buffer.remaining()];
                    buffer.get(jpeg);
                    Sink target = sink;
                    if (target != null && active) target.onJpeg(jpeg);
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

    private static String backCamera(CameraManager manager) {
        if (manager == null) return null;
        try {
            for (String id : manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            }
        } catch (Exception ignored) { }
        return null;
    }
}
