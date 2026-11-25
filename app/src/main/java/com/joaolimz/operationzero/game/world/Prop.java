package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.RigidBody;

/**
 * A physical, grabbable object: collider (box), rigid body, mass, grab point and interaction layer.
 */
public class Prop {
    public final RigidBody body = new RigidBody();
    public int kind;
    public int mesh = -1;
    public float meshScale = 1f;
    /** Pooled / removed props are inactive and neither simulated nor drawn. */
    public boolean active = true;
    /** Hand currently holding it (player.Hand) or null. */
    public Object heldBy;
    /** Inventory slot index when stored on the body, or -1. */
    public int slot = -1;
    /** Weapons / magazines snap to a predefined grip; other props keep their offset. */
    public boolean snapGrip;
    public final Vec3 holdPos = new Vec3();
    public final Quat holdRot = new Quat();
    /** Breakable props have health > 0. */
    public float health = -1f;
    /** Time until automatic removal (dropped magazines, debris). Negative = never. */
    public float despawnTimer = -1f;
    /** Mission event fired the first time the player grabs it. */
    public String pickupEvent;
    public boolean pickedOnce;
    /** Display name for HUD. */
    public String name = "";
    /** Gameplay value: ammo class for ammo boxes, heal for medkits... */
    public int value;
    public int value2;
    /** Remaining time this prop is highlighted (distance grab target). */
    public float highlight;

    public Prop init(int kind, int mesh, float hx, float hy, float hz, float mass, int sector) {
        this.kind = kind;
        this.mesh = mesh;
        body.setSize(hx, hy, hz);
        body.mass = mass;
        body.sector = sector;
        body.layer = kind == ItemKind.DEBRIS ? Layers.DEBRIS : (kind == ItemKind.CRATE || kind == ItemKind.BARREL ? Layers.PROP : Layers.ITEM);
        body.collideMask = Layers.STATIC | Layers.DOOR | Layers.PROP | Layers.ITEM;
        body.owner = this;
        name = ItemKind.NAMES[kind];
        return this;
    }

    public Prop at(float x, float y, float z) {
        body.pos.set(x, y, z);
        body.vel.set(0, 0, 0);
        body.sleeping = true;
        return this;
    }

    public boolean isHeld() { return heldBy != null; }

    public boolean isStored() { return slot >= 0; }

    /** World position of the grab point. */
    public Vec3 grabPoint(Vec3 out) { return out.set(body.pos); }
}
