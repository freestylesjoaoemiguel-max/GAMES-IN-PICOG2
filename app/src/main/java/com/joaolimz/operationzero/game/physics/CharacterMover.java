package com.joaolimz.operationzero.game.physics;

import com.joaolimz.operationzero.game.math.Vec3;

/** Capsule-like character controller (vertical circle + step up + gravity) for player and NPCs. */
public final class CharacterMover {
    private CharacterMover() { }

    public static final float STEP = 0.42f;

    /**
     * Moves the character by (dx, dz), resolves walls, steps, gravity.
     * @param state float[1] holding vertical velocity (in/out)
     * @return true when grounded after the move
     */
    public static boolean move(PhysicsWorld w, Vec3 feet, float[] state, float dx, float dz, float radius, float height, float dt,
                               int mask, float doorPush) {
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        int sub = Math.max(1, (int) Math.ceil(len / (radius * 0.8f)));
        for (int i = 0; i < sub; i++) {
            feet.x += dx / sub;
            feet.z += dz / sub;
            w.resolveCircle(feet, radius, feet.y + STEP, feet.y + height, mask, doorPush);
        }
        float ground = w.groundHeight(feet.x, feet.z, radius * 0.55f, feet.y + STEP, mask);
        float vy = state[0];
        boolean grounded;
        if (ground > -999f && feet.y - ground <= 0.06f && vy <= 0f) {
            // walking on (or stepping up onto) a surface
            if (ground > feet.y) feet.y = Math.min(ground, feet.y + Math.max(STEP, 4f * dt)); else feet.y = ground;
            vy = 0f;
            grounded = true;
        } else {
            vy -= PhysicsWorld.GRAVITY * dt;
            if (vy < -25f) vy = -25f;
            feet.y += vy * dt;
            if (ground > -999f && feet.y <= ground) {
                feet.y = ground;
                vy = 0f;
                grounded = true;
            } else {
                grounded = false;
            }
        }
        state[0] = vy;
        return grounded;
    }
}
