package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.math.Aabb;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.HingePanel;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;
import com.joaolimz.operationzero.game.physics.StaticCollider;

/**
 * Physical doors. HINGED doors swing from collisions (player, props, NPCs) or when the hand pulls the
 * handle. SLIDING doors (lab, shutter, garage) are motorized and opened by buttons, levers or cards.
 */
public final class Door {
    public static final int HINGED = 0, SLIDING = 1;

    public final String id;
    public final int type;
    public final int sector;
    public int mesh;
    public boolean locked;
    /** Text shown when the door is locked (HUD hint). */
    public String lockedHint = "Trancada";
    public String openEvent;
    public boolean openedOnce;

    // hinged
    public HingePanel hinge;
    public float angVel;
    public float minAngle = -1.75f, maxAngle = 1.75f;
    public float width, height;
    /** Hand currently pulling the handle (player.Hand) or null. */
    public Object grabbedBy;
    private float creakCooldown;

    // sliding
    public StaticCollider slab;
    public final Aabb closedBox = new Aabb();
    public final Vec3 travel = new Vec3();
    public float open, target, speed = 0.7f;
    public boolean moving;

    private Door(String id, int type, int sector) { this.id = id; this.type = type; this.sector = sector; }

    public static Door hinged(String id, PhysicsWorld world, float hingeX, float hingeZ, float y, float baseYaw, float width, float height, int sector) {
        Door d = new Door(id, HINGED, sector);
        HingePanel h = new HingePanel();
        h.hingeX = hingeX; h.hingeZ = hingeZ; h.y0 = y; h.y1 = y + height; h.length = width; h.baseYaw = baseYaw;
        h.halfThickness = 0.04f; h.sector = sector; h.owner = d;
        world.addHinge(h);
        d.hinge = h; d.width = width; d.height = height;
        return d;
    }

    public static Door sliding(String id, PhysicsWorld world, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                               float tx, float ty, float tz, int sector) {
        Door d = new Door(id, SLIDING, sector);
        d.closedBox.set(minX, minY, minZ, maxX, maxY, maxZ);
        d.travel.set(tx, ty, tz);
        d.slab = world.addMovable(minX, minY, minZ, maxX, maxY, maxZ, sector);
        d.slab.layer = Layers.STATIC;
        d.slab.material = StaticCollider.MAT_METAL;
        d.slab.owner = d;
        return d;
    }

    public boolean isOpen() {
        if (type == SLIDING) return open > 0.95f;
        return Math.abs(hinge.angle) > 0.6f;
    }

    /** Opens a sliding door (motor) or unlocks + swings a hinged door. Returns true if it started opening. */
    public boolean openDoor() {
        if (locked) return false;
        if (type == SLIDING) {
            if (target >= 1f) return false;
            target = 1f;
            moving = true;
            return true;
        }
        angVel += 2.5f;
        return true;
    }

    public void closeDoor() {
        if (type == SLIDING) { target = 0f; moving = true; }
    }

    /** Snaps to fully open (used when loading a checkpoint). */
    public void forceOpen() {
        locked = false;
        if (type == SLIDING) { open = target = 1f; applySlide(); }
        else { hinge.angle = maxAngle * 0.8f; angVel = 0f; }
        openedOnce = true;
    }

    /** Returns true when the door made a noise this frame (for audio). */
    public boolean update(float dt, Vec3 handPos) {
        boolean noise = false;
        if (type == SLIDING) {
            if (moving) {
                open = MathUtil.approach(open, target, speed * dt);
                applySlide();
                if (open == target) moving = false;
            }
            return false;
        }
        HingePanel h = hinge;
        creakCooldown -= dt;
        if (locked) {
            h.torqueAccum = 0f; angVel = 0f; h.angle = 0f;
            return false;
        }
        if (grabbedBy != null && handPos != null) {
            // pull the door toward the hand position projected in the hinge frame
            float dx = handPos.x - h.hingeX, dz = handPos.z - h.hingeZ;
            float handYaw = (float) Math.atan2(-dz, dx);
            float targetAngle = MathUtil.clamp(MathUtil.wrapAngle(handYaw - h.baseYaw), minAngle, maxAngle);
            angVel = MathUtil.clamp((targetAngle - h.angle) * 12f, -6f, 6f);
        } else {
            angVel += h.torqueAccum * 25f;
            angVel *= 1f - MathUtil.clamp(2.2f * dt, 0f, 1f);
        }
        h.torqueAccum = 0f;
        float prev = h.angle;
        h.angle += angVel * dt;
        if (h.angle > maxAngle) { h.angle = maxAngle; angVel = -angVel * 0.3f; }
        if (h.angle < minAngle) { h.angle = minAngle; angVel = -angVel * 0.3f; }
        if (Math.abs(h.angle - prev) > 0.02f && creakCooldown <= 0f) { noise = true; creakCooldown = 1.2f; }
        if (!openedOnce && Math.abs(h.angle) > 0.5f) openedOnce = true;
        return noise;
    }

    private void applySlide() {
        Aabb b = slab.box;
        b.set(closedBox.minX + travel.x * open, closedBox.minY + travel.y * open, closedBox.minZ + travel.z * open,
                closedBox.maxX + travel.x * open, closedBox.maxY + travel.y * open, closedBox.maxZ + travel.z * open);
        slab.enabled = open < 0.97f;
    }

    /** Handle world position (side = +1 or -1 face). */
    public Vec3 handlePos(Vec3 out, float side) {
        HingePanel h = hinge;
        float l = h.length - 0.12f;
        float nx = -h.dirZ(), nz = h.dirX();
        return out.set(h.hingeX + h.dirX() * l + nx * 0.06f * side, h.y0 + 1.0f, h.hingeZ + h.dirZ() * l + nz * 0.06f * side);
    }
}
