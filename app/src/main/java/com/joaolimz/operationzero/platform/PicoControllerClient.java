package com.joaolimz.operationzero.platform;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.joaolimz.operationzero.game.input.InputFrame;

import java.util.List;

/**
 * Reads the Pico G2 / Goblin ("Hummingbird") 3DoF controller directly from the Pico system service
 * (package com.picovr.hummingbirdsvc) over Binder. The interface descriptor, transaction codes and data
 * layout are the ones used by Pico's official Native SDK (HbClientActivity / HummingBirdAIDLService):
 *   1 getHBSensorState -> float[4] {w, x, y, z}
 *   2 getHBKeyEvent    -> int[8]   {touchX, touchY, home, app, click, volUp, volDown, battery}
 *   3 getConnectionState -> int (2 = connected)
 *   4 resetHBSensor
 *  28 getTriggerKeyEvent -> int (1 = pressed)
 * No vendor library is bundled; on devices without the service this class simply stays disconnected.
 */
public final class PicoControllerClient {
    private static final String TAG = "OperationZero";
    private static final String DESCRIPTOR = "com.picovr.picovrlib.hummingbird.HummingBirdAIDLService";
    private static final String SERVICE_PACKAGE = "com.picovr.hummingbirdsvc";
    private static final int TX_SENSOR = 1, TX_KEYS = 2, TX_STATE = 3, TX_RESET = 4, TX_TRIGGER = 28;

    private final Context ctx;
    private volatile IBinder binder;
    private boolean bound;
    private Thread thread;
    private volatile boolean running;

    // latest snapshot (guarded by this)
    private boolean connected;
    private float qx, qy, qz, qw = 1f;
    private int touchX, touchY, battery = -1;
    private boolean home, app, click, trigger;
    private String status = "serviço do controle Pico não encontrado";
    private int errors;
    private volatile boolean resetRequested;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            setStatus("serviço conectado, aguardando controle");
            Log.i(TAG, "Pico controller service bound: " + name);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null;
            setStatus("serviço desconectado");
        }
    };

    public PicoControllerClient(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    private synchronized void setStatus(String s) { status = s; }

    /** True if the Pico controller service is installed on this device. */
    public boolean isServiceInstalled() {
        try {
            Intent i = new Intent(DESCRIPTOR).setPackage(SERVICE_PACKAGE);
            List<ResolveInfo> l = ctx.getPackageManager().queryIntentServices(i, 0);
            return l != null && !l.isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void start() {
        if (!bound) {
            try {
                Intent i = new Intent(DESCRIPTOR).setPackage(SERVICE_PACKAGE);
                bound = ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
                setStatus(bound ? "conectando ao serviço do controle..." : "serviço do controle Pico não encontrado (sem G2?)");
            } catch (RuntimeException e) {
                bound = false;
                setStatus("falha ao conectar: " + e.getClass().getSimpleName());
                Log.w(TAG, "bind Pico controller service failed", e);
            }
        }
        if (thread == null) {
            running = true;
            thread = new Thread(new Runnable() {
                @Override public void run() { loop(); }
            }, "PicoController");
            thread.setPriority(Thread.MAX_PRIORITY - 1);
            thread.start();
        }
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(200); } catch (InterruptedException ignored) { }
            thread = null;
        }
        if (bound) {
            try { ctx.unbindService(conn); } catch (RuntimeException ignored) { }
            bound = false;
        }
        binder = null;
        synchronized (this) { connected = false; }
    }

    /** Asks the service to reset the controller orientation (recenter). */
    public void requestReset() { resetRequested = true; }

    private void loop() {
        while (running) {
            IBinder b = binder;
            if (b != null) {
                try {
                    poll(b);
                    errors = 0;
                } catch (RemoteException | RuntimeException e) {
                    if (++errors == 1) Log.w(TAG, "Pico controller read failed", e);
                    setStatus("erro lendo controle: " + e.getClass().getSimpleName());
                }
            }
            try { Thread.sleep(b != null ? 8 : 250); } catch (InterruptedException e) { return; }
        }
    }

    private void poll(IBinder b) throws RemoteException {
        if (resetRequested) {
            resetRequested = false;
            transactVoid(b, TX_RESET);
        }
        int state = transactInt(b, TX_STATE);
        if (state != 2) {
            synchronized (this) {
                connected = false;
                status = state == 1 ? "controle conectando..." : "controle desligado/desconectado (estado " + state + ")";
            }
            return;
        }
        float[] s = transactFloats(b, TX_SENSOR);
        int[] k = transactInts(b, TX_KEYS);
        int trig = transactInt(b, TX_TRIGGER);
        synchronized (this) {
            connected = true;
            if (s != null && s.length >= 4) {
                // vendor Orientation(x=s1, y=s2, z=s3, w=s0)
                qx = s[1]; qy = s[2]; qz = s[3]; qw = s[0];
            }
            if (k != null && k.length >= 8) {
                touchX = k[0]; touchY = k[1];
                home = k[2] == 1; app = k[3] == 1; click = k[4] == 1;
                battery = k[7];
            }
            trigger = trig == 1;
            status = "controle G2 conectado";
        }
    }

    private int transactInt(IBinder b, int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private float[] transactFloats(IBinder b, int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.createFloatArray();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private int[] transactInts(IBinder b, int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.createIntArray();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void transactVoid(IBinder b, int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /** Copies the latest controller state into the input frame. */
    public synchronized void fill(InputFrame f) {
        f.ctrlConnected = connected;
        f.ctrlStatus = status;
        if (!connected) {
            f.ctrlTrigger = f.ctrlClick = f.ctrlApp = f.ctrlHome = false;
            f.ctrlTouching = false;
            return;
        }
        f.ctrlX = qx; f.ctrlY = qy; f.ctrlZ = qz; f.ctrlW = qw;
        f.ctrlTrigger = trigger;
        f.ctrlClick = click;
        f.ctrlApp = app;
        f.ctrlHome = home;
        f.ctrlTouching = touchX != 0 || touchY != 0;
        f.ctrlTouchX = touchX;
        f.ctrlTouchY = touchY;
        f.ctrlBattery = battery;
    }
}
