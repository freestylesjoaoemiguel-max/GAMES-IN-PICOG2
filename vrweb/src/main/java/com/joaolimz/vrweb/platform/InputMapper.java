package com.joaolimz.vrweb.platform;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.joaolimz.vrweb.game.input.InputFrame;
import com.joaolimz.vrweb.game.save.Settings;

/**
 * Maps Android input to game actions using the (remappable) key bindings in {@link Settings}.
 * Sources: Pico G2 controller key events (trigger, touchpad click/swipes, app/back), Bluetooth gamepads
 * (sticks, triggers, buttons), keyboards and the touch screen (for testing on a phone in a viewer).
 */
public final class InputMapper {
    private final Settings settings;
    private final boolean[] action = new boolean[Settings.ACTIONS];
    private float stickX, stickY, turn;
    private float axisTrigger, axisGrip;
    private boolean touchTrigger, touchMove;
    private int lastKey;
    private long lastKeyTime;

    public InputMapper(Settings settings) { this.settings = settings; }

    /** Returns true if the key was consumed. */
    public synchronized boolean onKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (down && event.getRepeatCount() == 0) {
            lastKey = keyCode;
            lastKeyTime = System.nanoTime();
        }
        boolean any = false;
        for (int a = 0; a < Settings.ACTIONS; a++) {
            if (settings.isBound(a, keyCode)) {
                action[a] = down;
                any = true;
            }
        }
        // the back key must never close the app directly (it is the Pico "app" button)
        return any || keyCode == KeyEvent.KEYCODE_BACK;
    }

    public synchronized boolean onGenericMotion(MotionEvent e) {
        int src = e.getSource();
        if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK || (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            stickX = dead(e.getAxisValue(MotionEvent.AXIS_X));
            stickY = dead(-e.getAxisValue(MotionEvent.AXIS_Y));
            turn = dead(e.getAxisValue(MotionEvent.AXIS_Z));
            if (turn == 0f) turn = dead(e.getAxisValue(MotionEvent.AXIS_RX));
            axisTrigger = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
            axisGrip = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (hx != 0f || hy != 0f) {
                if (Math.abs(hx) > 0.5f) turn = hx;
                if (Math.abs(hy) > 0.5f) stickY = -hy;
            }
            return true;
        }
        return false;
    }

    /** Touch screen: tap = trigger, hold with two fingers = walk forward (phone testing). */
    public synchronized boolean onTouch(MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            touchTrigger = true;
            touchMove = e.getPointerCount() >= 2;
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            touchTrigger = false;
            touchMove = false;
        } else if (act == MotionEvent.ACTION_POINTER_UP) {
            touchMove = e.getPointerCount() - 1 >= 2;
        }
        return true;
    }

    private static float dead(float v) { return Math.abs(v) < 0.18f ? 0f : v; }

    public synchronized void fill(InputFrame f) {
        f.trigger = action[Settings.A_TRIGGER] || axisTrigger > 0.5f || touchTrigger;
        f.grip = action[Settings.A_GRIP] || axisGrip > 0.5f;
        f.action = action[Settings.A_ACTION];
        f.switchHand = action[Settings.A_SWITCH];
        f.menu = action[Settings.A_MENU];
        f.up = action[Settings.A_UP];
        f.down = action[Settings.A_DOWN];
        f.left = action[Settings.A_LEFT];
        f.right = action[Settings.A_RIGHT];
        f.moveX = stickX;
        f.moveY = touchMove ? 1f : stickY;
        f.turnX = turn;
        // report the last key press only once (used by the remapping screen)
        if (lastKey != 0 && System.nanoTime() - lastKeyTime < 400_000_000L) {
            f.lastKeyCode = lastKey;
            lastKey = 0;
        } else {
            f.lastKeyCode = 0;
        }
    }
}
