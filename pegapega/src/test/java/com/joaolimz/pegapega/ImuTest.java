package com.joaolimz.pegapega;

import com.joaolimz.pegapega.game.input.ImuHand;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.mesh.MeshLibrary;
import com.joaolimz.pegapega.game.player.ArmRig;
import com.joaolimz.pegapega.game.world.Arena;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Controller accelerometer: axis calibration, motion integration and its effect on arm locomotion. */
public class ImuTest {
    private static final float DT = 1f / 72f;
    /** Hidden vendor accelerometer axes (signed permutation, row major). */
    private static final int[] HIDDEN = {0, -1, 0, 0, 0, 1, -1, 0, 0};

    /** Raw sample the vendor sensor would report for a world specific force f (in units of 'scale'). */
    private static float[] rawFor(Quat rot, Vec3 worldForce, float scale) {
        Vec3 b = new Vec3();
        new Quat(rot).conjugate().rotate(worldForce, b);      // game body axes
        float[] r = new float[3];
        // raw = HIDDEN^T * body
        for (int i = 0; i < 3; i++) r[i] = (HIDDEN[i] * b.x + HIDDEN[3 + i] * b.y + HIDDEN[6 + i] * b.z) / scale;
        return r;
    }

    @Test
    public void calibrationFindsAccelerometerAxesAndUnits() {
        Quat[] rots = {new Quat().setYaw(0.4f), new Quat().setEuler(0.4f, 1.5f, 0f), new Quat().setYaw(0.4f - 1.57f)};
        float[][] acc = new float[3][];
        for (int i = 0; i < 3; i++) acc[i] = rawFor(rots[i], new Vec3(0, 9.81f, 0), 9.81f); // sensor in "g" units
        ImuHand imu = new ImuHand();
        // the orientation calibration found the body axes; the accelerometer shares them (HIDDEN = Pb^T)
        int[] pb = new int[9];
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) pb[r * 3 + c] = HIDDEN[c * 3 + r];
        imu.setDefaultFromBody(pb);
        // perturb the default so the solver has to work (flip the up axis)
        for (int j = 3; j < 6; j++) imu.pa[j] = -imu.pa[j];
        for (int j = 6; j < 9; j++) imu.pa[j] = -imu.pa[j];
        float err = imu.solve(rots, acc);
        assertTrue("residual " + err, err < 0.05f);
        assertTrue(imu.gravityIncluded);
        // still controller in a new pose -> no motion
        Quat pose = new Quat().setEuler(-1f, -0.6f, 0.3f);
        float[] still = rawFor(pose, new Vec3(0, 9.81f, 0), 9.81f);
        for (int i = 0; i < 72; i++) imu.update(still[0], still[1], still[2], pose, 0f, DT);
        assertTrue("no drift when still: " + imu.offset.length(), imu.offset.length() < 0.01f);
        // a quick downward thrust (2 g down for 0.15 s, then decelerate) moves the hand down
        float minY = 0f;
        for (int i = 0; i < 72; i++) {
            float down = i < 11 ? -20f : i < 22 ? 20f : 0f;
            float[] s = rawFor(pose, new Vec3(0, 9.81f + down, 0), 9.81f);
            imu.update(s[0], s[1], s[2], pose, 0f, DT);
            minY = Math.min(minY, imu.offset.y);
        }
        assertTrue("hand moved down with the arm: " + minY, minY < -0.08f);
        assertTrue("and returns to the arm model afterwards: " + imu.offset.length(), imu.offset.length() < 0.05f);
    }

    @Test
    public void realDownwardPushOnTheFloorJumps() {
        Arena ar = new Arena().build(new MeshLibrary(), -1);
        ArmRig rig = new ArmRig();
        rig.spawn(new Vec3(0f, 1f, 14f));
        Quat c = new Quat();
        Vec3 off = new Vec3();
        rig.setImuOffsets(off, off);
        // lower the controller slowly until the hands rest on the floor in front
        for (int i = 0; i <= 36; i++) { c.setEuler(0f, -0.5f - 0.45f * i / 36f, 0f); rig.update(ar.physics, c, true, 1, 0f, DT); }
        for (int i = 0; i < 72; i++) rig.update(ar.physics, c, true, 1, 0f, DT);
        assertTrue("hands resting on the floor", rig.anyAnchored());
        // the accelerometer reports a fast push down (hand offset goes 35 cm down in 0.1 s)
        float maxY = 0f;
        for (int i = 0; i < 8; i++) {
            off.set(0, -0.35f * (i + 1) / 8f, 0);
            rig.setImuOffsets(off, off);
            rig.update(ar.physics, c, true, 1, 0f, DT);
        }
        // arm comes back up: hands leave the floor and the body keeps the velocity
        Quat up = new Quat().setEuler(0f, 0.4f, 0f);
        off.set(0, 0, 0);
        rig.setImuOffsets(off, off);
        for (int i = 0; i < 50; i++) { rig.update(ar.physics, up, true, 1, 0f, DT); maxY = Math.max(maxY, rig.head.y); }
        assertTrue("jumped with a real arm push: " + maxY, maxY > 1.3f);
    }
}
