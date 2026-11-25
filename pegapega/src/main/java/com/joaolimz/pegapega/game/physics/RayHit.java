package com.joaolimz.pegapega.game.physics;

import com.joaolimz.pegapega.game.math.Vec3;

/** Result of a raycast query. */
public final class RayHit {
    public float distance;
    public final Vec3 point = new Vec3();
    public final Vec3 normal = new Vec3();
    public int layer;
    public StaticCollider collider;
    public RigidBody body;
    public HingePanel hinge;
    /** Gameplay owner (NPC, prop, door) when relevant. */
    public Object owner;
    /** Extra info: e.g. NPC hit zone. */
    public int part;

    public void clear() {
        distance = Float.MAX_VALUE;
        collider = null; body = null; hinge = null; owner = null; layer = 0; part = 0;
    }

    public boolean hasHit() { return distance < Float.MAX_VALUE; }
}
