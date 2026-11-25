package com.joaolimz.operationzero;

import com.joaolimz.operationzero.game.Game;
import com.joaolimz.operationzero.game.input.ControllerMapping;
import com.joaolimz.operationzero.game.input.InputFrame;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.player.Hand;
import com.joaolimz.operationzero.game.save.Settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Simulates a Pico G2 controller whose raw quaternion uses an unknown axis convention and checks that the
 * calibration wizard recovers it, that the hand then follows the controller (not the head), and that the
 * touchpad walks / turns.
 */
public class PicoControllerTest {
    private static final float DT = 1f / 72f;

    /** Hidden vendor convention: raw = A^T * G * B^T (signed permutations). */
    private static final int[] A = {0, 0, 1, 1, 0, 0, 0, -1, 0};
    private static final int[] B = {0, 1, 0, 0, 0, 1, -1, 0, 0};

    /** Raw vendor quaternion {x,y,z,w} for a game-space rotation g. */
    static float[] raw(Quat g, float yawDrift) {
        Quat d = new Quat().setYaw(yawDrift).mul(g); // the controller's own yaw reference differs from the head
        float[] m = matrix(d);
        // raw = A^T * m * B^T
        float[] t = new float[9], r = new float[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            float s = 0;
            for (int k = 0; k < 3; k++) s += A[k * 3 + i] * m[k * 3 + j];
            t[i * 3 + j] = s;
        }
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            float s = 0;
            for (int k = 0; k < 3; k++) s += t[i * 3 + k] * B[j * 3 + k];
            r[i * 3 + j] = s;
        }
        Quat q = new Quat().setFromBasis(r[0], r[3], r[6], r[1], r[4], r[7], r[2], r[5], r[8]);
        return new float[]{q.x, q.y, q.z, q.w};
    }

    static float[] matrix(Quat q) {
        float x = q.x, y = q.y, z = q.z, w = q.w;
        return new float[]{
                1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
                2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
                2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)};
    }

    private static void set(InputFrame in, float[] r) { in.ctrlX = r[0]; in.ctrlY = r[1]; in.ctrlZ = r[2]; in.ctrlW = r[3]; }

    @Test
    public void solveRecoversHiddenAxisConvention() {
        float drift = 1.1f;
        ControllerMapping m = new ControllerMapping();
        Quat fwd = new Quat().setYaw(0.3f);
        Quat up = new Quat().setEuler(0.3f, MathUtil.PI * 0.5f - 0.05f, 0f);
        Quat right = new Quat().setYaw(0.3f - MathUtil.PI * 0.5f);
        float err = m.solve(raw(fwd, drift), raw(up, drift), raw(right, drift));
        assertTrue("fit error " + err, err < 0.2f);
        float[] r0 = raw(fwd, drift);
        m.alignYaw(r0[0], r0[1], r0[2], r0[3], 0.3f);
        MathUtil.Rng rng = new MathUtil.Rng(5);
        Quat g = new Quat(), out = new Quat();
        Vec3 a = new Vec3(), b = new Vec3();
        for (int i = 0; i < 50; i++) {
            g.setEuler(rng.range(-3f, 3f), rng.range(-1.4f, 1.4f), rng.range(-1f, 1f));
            float[] r = raw(g, drift);
            m.apply(r[0], r[1], r[2], r[3], out);
            g.forward(a); out.forward(b);
            assertTrue("forward mismatch " + a + " vs " + b, a.distance(b) < 0.02f);
            g.up(a); out.up(b);
            assertTrue("up mismatch", a.distance(b) < 0.02f);
        }
    }

    @Test
    public void wizardCalibratesAndHandFollowsControllerNotHead() {
        TestPlatform platform = new TestPlatform();
        Game game = new Game(new Settings(), platform, null);
        InputFrame in = new InputFrame();
        float drift = -2.0f;
        in.ctrlConnected = true;
        in.ctrlStatus = "teste";
        set(in, raw(new Quat(), drift));
        game.update(DT, in);
        game.update(DT, in);
        assertEquals("wizard opens automatically", "calib", game.menu.screen);
        Quat[] poses = {new Quat(), new Quat().setEuler(0f, MathUtil.PI * 0.5f - 0.1f, 0f), new Quat().setYaw(-MathUtil.PI * 0.5f)};
        for (int step = 0; step < 3; step++) {
            set(in, raw(poses[step], drift));
            in.ctrlTrigger = true; game.update(DT, in);
            in.ctrlTrigger = false; game.update(DT, in);
        }
        assertEquals(3, game.calibStep);
        assertTrue(game.settings.ctrlCalibrated);
        // touchpad: center, up, right (this pad reports y growing downward)
        int[][] touches = {{128, 128}, {128, 30}, {226, 128}};
        for (int[] t : touches) {
            in.ctrlTouching = true; in.ctrlTouchX = t[0]; in.ctrlTouchY = t[1];
            in.ctrlTrigger = true; game.update(DT, in);
            in.ctrlTrigger = false; game.update(DT, in);
        }
        in.ctrlTouching = false;
        game.update(DT, in);
        assertEquals(-1, game.calibStep);
        assertEquals("main", game.menu.screen);
        // start the game; head looks straight ahead, controller points 40 degrees left and 20 degrees down
        game.newGame();
        in.head.identity();
        Quat aim = new Quat().setEuler(0.7f, -0.35f, 0f);
        set(in, raw(aim, drift));
        for (int i = 0; i < 10; i++) game.update(DT, in);
        Hand h = game.player.activeHand();
        Vec3 f = new Vec3();
        h.rot.forward(f);
        Vec3 want = new Vec3();
        new Quat().setYaw(game.player.yaw).mul(aim).forward(want);
        assertTrue("hand follows controller: " + f + " want " + want, f.distance(want) < 0.05f);
        // touching the upper part of the pad walks forward
        float z0 = game.player.feet.z;
        in.ctrlTouching = true; in.ctrlTouchX = 128; in.ctrlTouchY = 40;
        for (int i = 0; i < 72; i++) game.update(DT, in);
        assertTrue("walked forward " + (z0 - game.player.feet.z), z0 - game.player.feet.z > 1.0f);
        // touching the right side turns (snap)
        float yaw0 = game.player.yaw;
        in.ctrlTouchX = 220; in.ctrlTouchY = 128;
        for (int i = 0; i < 5; i++) game.update(DT, in);
        assertTrue("turned right", game.player.yaw < yaw0 - 0.3f);
        in.ctrlTouching = false;
        // trigger from the controller service fires the pistol picked up with the controller
        game.update(DT, in);
    }
}
