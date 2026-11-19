package com.joaolimz.pegapega.game.physics;

import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;

/**
 * Simplified rigid body: axis-aligned box collision shape (half extents), free visual rotation.
 * Optimised for mobile: sleeping, velocity clamping and global active-body budget.
 */
public final class RigidBody {
    public final Vec3 pos = new Vec3();
    public final Vec3 vel = new Vec3();
    public final Quat rot = new Quat();
    public final Vec3 angVel = new Vec3();
    public float hx = 0.1f, hy = 0.1f, hz = 0.1f;
    public float mass = 1f;
    public float restitution = 0.25f;
    public float friction = 0.6f;
    public int layer = Layers.PROP;
    public int collideMask = Layers.STATIC | Layers.DOOR | Layers.PROP | Layers.ITEM;
    /** Held by a hand or attached to a slot: moved by gameplay, pushes others, ignores gravity. */
    public boolean kinematic;
    public boolean enabled = true;
    public boolean sleeping;
    public boolean grounded;
    /** Bodies that should keep simulating (e.g. just thrown) are not culled by the budget for a while. */
    public float protectTimer;
    public float sleepTimer;
    public float lastImpactSpeed;
    /** Set by the world when an impact strong enough for audio occurs. */
    public boolean impactFlag;
    public int sector;
    public Object owner;

    public float invMass() { return kinematic || mass <= 0f ? 0f : 1f / mass; }

    public void wake() {
        sleeping = false;
        sleepTimer = 0f;
    }

    public void setSize(float hx, float hy, float hz) { this.hx = hx; this.hy = hy; this.hz = hz; }

    public float radiusXZ() { return Math.max(hx, hz); }
}
