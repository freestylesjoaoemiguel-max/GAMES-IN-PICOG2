package com.joaolimz.vrweb.platform;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Surface;

import com.joaolimz.vrweb.game.math.Quat;

/**
 * 3DoF head tracking from the Android sensor framework (game rotation vector, then rotation vector,
 * then accelerometer-only tilt). Converts the device frame to the VR camera frame (Y up, -Z forward)
 * taking the display rotation into account, with yaw recentering. If no sensor reports, the game still
 * runs (look direction then comes from turning only) and the status says so.
 */
public final class HeadTracker implements SensorEventListener {
    private final SensorManager sm;
    private Sensor sensor;
    private String sensorName = "nenhum";
    private final float[] rot = new float[9], remap = new float[9];
    private final Quat raw = new Quat();
    private final Quat recenter = new Quat();
    private final Quat out = new Quat();
    private final Quat tmp = new Quat();
    private volatile long lastEvent;
    private volatile boolean flip180;
    private int displayRotation = Surface.ROTATION_90;
    private boolean recenterRequested = true;
    private final float[] grav = new float[3];
    private boolean accelOnly;

    public HeadTracker(Context ctx) {
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (sensor == null) sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (sensor == null) { sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); accelOnly = sensor != null; }
            if (sensor != null) sensorName = sensor.getName();
        }
    }

    public void setDisplayRotation(int rotation) { displayRotation = rotation; }

    public void setFlip(boolean flip) { flip180 = flip; }

    public void start() {
        if (sm != null && sensor != null) sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stop() {
        if (sm != null) sm.unregisterListener(this);
    }

    public synchronized void requestRecenter() { recenterRequested = true; }

    public boolean isWorking() { return lastEvent != 0 && SystemClock.elapsedRealtime() - lastEvent < 1500; }

    public String status() {
        if (sensor == null) return "sem sensor de rotacao (use gamepad/touchpad para girar)";
        return (isWorking() ? "OK - " : "aguardando - ") + sensorName + (accelOnly ? " (somente inclinacao)" : "");
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (accelOnly) {
            for (int i = 0; i < 3; i++) grav[i] = grav[i] * 0.8f + e.values[i] * 0.2f;
            float[] geo = {0f, 1f, 0f};
            if (!SensorManager.getRotationMatrix(rot, null, grav, geo)) return;
        } else {
            SensorManager.getRotationMatrixFromVector(rot, e.values);
        }
        int ax, ay;
        switch (displayRotation) {
            case Surface.ROTATION_90: ax = SensorManager.AXIS_Y; ay = SensorManager.AXIS_MINUS_X; break;
            case Surface.ROTATION_180: ax = SensorManager.AXIS_MINUS_X; ay = SensorManager.AXIS_MINUS_Y; break;
            case Surface.ROTATION_270: ax = SensorManager.AXIS_MINUS_Y; ay = SensorManager.AXIS_X; break;
            default: ax = SensorManager.AXIS_X; ay = SensorManager.AXIS_Y; break;
        }
        SensorManager.remapCoordinateSystem(rot, ax, ay, remap);
        // remap: camera (screen) -> android world (X east, Y north, Z up). Our world: X east, Y up, Z south.
        // Column j of M is the world image of camera axis j: our = (w.x, w.z, -w.y)
        float m00 = remap[0], m01 = remap[1], m02 = remap[2];
        float m10 = remap[6], m11 = remap[7], m12 = remap[8];
        float m20 = -remap[3], m21 = -remap[4], m22 = -remap[5];
        if (flip180) { m00 = -m00; m10 = -m10; m20 = -m20; m01 = -m01; m11 = -m11; m21 = -m21; }
        synchronized (this) {
            raw.setFromBasis(m00, m10, m20, m01, m11, m21, m02, m12, m22);
            if (accelOnly) {
                // no yaw reference: keep pitch/roll only
                float yaw = raw.yaw();
                tmp.setYaw(-yaw);
                raw.preMul(tmp);
            }
            if (recenterRequested) {
                recenter.setYaw(-raw.yaw());
                recenterRequested = false;
            }
            lastEvent = SystemClock.elapsedRealtime();
        }
    }

    /** Latest head orientation relative to the recenter yaw. */
    public synchronized void read(Quat dst) {
        if (lastEvent == 0) { dst.identity(); return; }
        out.setMul(recenter, raw);
        dst.set(out);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
