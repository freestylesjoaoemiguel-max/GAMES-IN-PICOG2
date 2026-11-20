package com.picoverso.cam;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Celular: transmite a câmera traseira (zoom 0,5x se existir ultra-wide) + giroscópio para o Pico G2 pela rede local. */
public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final int PORT = 8080;
    private StreamServer server;
    private ProcessCameraProvider provider;
    private Camera camera;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView ipView, statusView, zoomView;
    private ImageView preview;
    private volatile float minZoom = 1f, maxZoom = 1f, zoom = 1f;
    private volatile int jpegW = 1280, jpegH = 720;
    private long lastPreview = 0;
    private SensorManager sm;
    private int frames = 0;
    private long fpsT = System.currentTimeMillis();
    private int fps = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        try {
            server = new StreamServer(PORT, new StreamServer.Host() {
                @Override public String infoJson() {
                    return String.format(Locale.US, "{\"name\":\"PicoVerso Camera\",\"w\":%d,\"h\":%d,\"zoom\":%.2f,\"min\":%.2f,\"max\":%.2f}", jpegW, jpegH, zoom, minZoom, maxZoom);
                }
                @Override public float setZoom(float r) { return applyZoom(r); }
            });
            server.start();
        } catch (Exception e) {
            statusView.setText("Erro ao abrir o servidor: " + e.getMessage());
        }
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        refreshIp();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        ui.postDelayed(tick, 1000);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            fps = Math.round(frames * 1000f / Math.max(1, now - fpsT)); frames = 0; fpsT = now;
            int c = server == null ? 0 : server.clients.get();
            zoomView.setText(String.format(Locale.US, "Zoom %.1fx  (mín %.1fx · máx %.1fx)", zoom, minZoom, maxZoom));
            if (camera != null) statusView.setText("● Câmera ligada · " + fps + " fps · " + c + " óculos conectado(s)");
            refreshIp();
            ui.postDelayed(this, 1000);
        }
    };

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else statusView.setText("Sem permissão da câmera. Libere em Configurações do app.");
    }

    @Override protected void onResume() {
        super.onResume();
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
    }
    @Override protected void onPause() { super.onPause(); sm.unregisterListener(this); }
    @Override protected void onDestroy() { super.onDestroy(); ui.removeCallbacksAndMessages(null); if (server != null) server.stop(); exec.shutdownNow(); }

    @Override public void onSensorChanged(SensorEvent e) {
        float[] q = new float[4];
        SensorManager.getQuaternionFromVector(q, e.values); // w,x,y,z
        int r = getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_270 ? 3 : 1;
        if (server != null) server.setOrientation(q[1], q[2], q[3], q[0], r);
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    /* ---------- Câmera ---------- */
    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try { provider = f.get(); bind(); }
            catch (Exception e) { statusView.setText("Erro na câmera: " + e.getMessage()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bind() {
        provider.unbindAll();
        ImageAnalysis ia = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();
        ia.setAnalyzer(exec, this::analyze);
        camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, ia);
        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
        if (zs != null) { minZoom = zs.getMinZoomRatio(); maxZoom = zs.getMaxZoomRatio(); }
        applyZoom(0.5f); // padrão: 0,5x (ultra-wide), ou o menor zoom que o aparelho tiver
    }

    private float applyZoom(float r) {
        final float z = Math.max(minZoom, Math.min(maxZoom, r));
        zoom = z;
        ui.post(() -> { if (camera != null) camera.getCameraControl().setZoomRatio(z); });
        return z;
    }

    private void analyze(ImageProxy image) {
        try {
            int w = image.getWidth(), h = image.getHeight();
            byte[] nv21 = toNv21(image, w, h);
            YuvImage yuv = new YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(w * h / 4);
            yuv.compressToJpeg(new Rect(0, 0, w, h), 72, bos);
            byte[] jpg = bos.toByteArray();
            int rot = image.getImageInfo().getRotationDegrees();
            if (rot != 0) {
                Bitmap bm = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                Matrix m = new Matrix(); m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                bos.reset(); r.compress(Bitmap.CompressFormat.JPEG, 72, bos); jpg = bos.toByteArray();
                w = r.getWidth(); h = r.getHeight();
            }
            jpegW = w; jpegH = h; frames++;
            if (server != null) server.pushFrame(jpg);
            long now = System.currentTimeMillis();
            if (now - lastPreview > 250) {
                lastPreview = now;
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 4;
                final Bitmap pv = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
                ui.post(() -> preview.setImageBitmap(pv));
            }
        } catch (Exception ignored) {
        } finally { image.close(); }
    }

    private static byte[] toNv21(ImageProxy image, int w, int h) {
        ImageProxy.PlaneProxy[] p = image.getPlanes();
        ByteBuffer y = p[0].getBuffer(), u = p[1].getBuffer(), v = p[2].getBuffer();
        byte[] out = new byte[w * h * 3 / 2];
        int ys = p[0].getRowStride(), pos = 0;
        for (int r = 0; r < h; r++) { y.position(r * ys); y.get(out, pos, w); pos += w; }
        int rs = p[1].getRowStride(), ps = p[1].getPixelStride();
        for (int r = 0; r < h / 2; r++)
            for (int c = 0; c < w / 2; c++) { int i = r * rs + c * ps; out[pos++] = v.get(i); out[pos++] = u.get(i); }
        return out;
    }

    /* ---------- Interface ---------- */
    private void refreshIp() {
        StringBuilder sb = new StringBuilder();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) { if (sb.length() > 0) sb.append("\n"); sb.append(a.getHostAddress()); }
            }
        } catch (Exception ignored) {}
        ipView.setText(sb.length() == 0 ? "Sem Wi-Fi!\nConecte à mesma rede do Pico" : sb.toString());
    }

    private Button btn(String t, View.OnClickListener l) {
        Button x = new Button(this); x.setText(t); x.setAllCaps(false); x.setTextSize(18); x.setOnClickListener(l); return x;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL); root.setBackgroundColor(Color.parseColor("#0d1020")); root.setPadding(40, 30, 40, 30);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        TextView t = new TextView(this); t.setText("PicoVerso Câmera"); t.setTextColor(Color.WHITE); t.setTextSize(26);
        TextView h = new TextView(this); h.setText("Digite este IP no Pico (tela 📷 Câmera):"); h.setTextColor(Color.parseColor("#a9c7ff")); h.setTextSize(16);
        ipView = new TextView(this); ipView.setTextColor(Color.parseColor("#4be08a")); ipView.setTextSize(40); ipView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); ipView.setPadding(0, 12, 0, 12);
        statusView = new TextView(this); statusView.setTextColor(Color.parseColor("#ffd23f")); statusView.setTextSize(16); statusView.setText("Iniciando câmera…");
        zoomView = new TextView(this); zoomView.setTextColor(Color.WHITE); zoomView.setTextSize(16);
        LinearLayout row = new LinearLayout(this);
        row.addView(btn("0.5x", v -> applyZoom(0.5f))); row.addView(btn("1x", v -> applyZoom(1f))); row.addView(btn("2x", v -> applyZoom(2f)));
        left.addView(t); left.addView(h); left.addView(ipView); left.addView(statusView); left.addView(zoomView); left.addView(row);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f); pl.setMargins(30, 0, 0, 0);
        preview.setLayoutParams(pl);
        root.addView(left); root.addView(preview);
        setContentView(root);
    }
}
