package com.joaolimz.operationzero.game.input;

import com.joaolimz.operationzero.game.math.Quat;

/**
 * Snapshot of all input for one frame. Filled by the platform layer (Android sensors, Pico controller
 * key events, Bluetooth gamepads, touch screen) and read by the game.
 */
public final class InputFrame {
    /** Head orientation from the IMU (yaw relative to recenter point). */
    public final Quat head = new Quat();
    /** True when a 3DoF/6DoF controller orientation is available (Pico SDK bridge, etc.). */
    public boolean hasControllerPose;
    public final Quat controller = new Quat();

    public boolean trigger;
    public boolean grip;
    /** Touchpad click / context action (eject magazine, rack slide, drop). */
    public boolean action;
    /** App button: switch which virtual hand the controller drives. */
    public boolean switchHand;
    public boolean menu;
    public boolean recenter;

    /** Analog locomotion (-1..1). Y positive = forward. */
    public float moveX, moveY;
    /** Analog turn (-1..1). */
    public float turnX;
    /** Digital directions (touchpad swipes / d-pad). */
    public boolean up, down, left, right;

    /** Last raw key code pressed this frame (for in-game remapping), or 0. */
    public int lastKeyCode;

    public void copyFrom(InputFrame o) {
        head.set(o.head);
        hasControllerPose = o.hasControllerPose;
        controller.set(o.controller);
        trigger = o.trigger; grip = o.grip; action = o.action; switchHand = o.switchHand; menu = o.menu; recenter = o.recenter;
        moveX = o.moveX; moveY = o.moveY; turnX = o.turnX;
        up = o.up; down = o.down; left = o.left; right = o.right;
        lastKeyCode = o.lastKeyCode;
    }
}
