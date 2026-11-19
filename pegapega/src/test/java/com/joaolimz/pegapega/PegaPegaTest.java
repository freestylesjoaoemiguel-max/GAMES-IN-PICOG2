package com.joaolimz.pegapega;

import com.joaolimz.pegapega.game.Game;
import com.joaolimz.pegapega.game.RenderQueue;
import com.joaolimz.pegapega.game.audio.Sfx;
import com.joaolimz.pegapega.game.audio.SoundSynth;
import com.joaolimz.pegapega.game.input.InputFrame;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.player.ArmRig;
import com.joaolimz.pegapega.game.save.Settings;
import com.joaolimz.pegapega.game.tag.Avatar;
import com.joaolimz.pegapega.game.tag.TagGame;
import com.joaolimz.pegapega.game.world.Arena;
import com.joaolimz.pegapega.game.mesh.MeshLibrary;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Simulation tests of the real game code: arm locomotion, bots and tag rules. */
public class PegaPegaTest {
    private static final float DT = 1f / 72f;

    private static Arena arena() { return new Arena().build(new MeshLibrary(), -1); }

    /** One arm stroke: reach forward-down to the ground, sweep back under the body, lift. */
    private static void stroke(ArmRig rig, Arena ar, float yaw, float from, float to, float seconds) {
        Quat c = new Quat();
        int n = (int) (seconds / DT);
        for (int i = 0; i <= n; i++) {
            float p = from + (to - from) * i / n;
            c.setEuler(yaw, p, 0f);
            rig.update(ar.physics, c, true, 1, 0f, DT);
        }
        // lift the arms back up (controller forward) for a moment
        c.setEuler(yaw, 0.1f, 0f);
        for (int i = 0; i < 10; i++) rig.update(ar.physics, c, true, 1, 0f, DT);
    }

    private static ArmRig rigAt(float x, float z) {
        ArmRig rig = new ArmRig();
        rig.spawn(new Vec3(x, 1.0f, z));
        rig.headRot.identity();
        return rig;
    }

    @Test
    public void standsStillWithArmsUp() {
        Arena ar = arena();
        ArmRig rig = rigAt(0f, 14f);
        Quat c = new Quat().setEuler(0f, 0.1f, 0f);
        for (int i = 0; i < 144; i++) rig.update(ar.physics, c, true, 1, 0f, DT);
        assertEquals(1.0f, rig.head.y, 0.05f);
        assertEquals(14f, rig.head.z, 0.01f);
        assertTrue(!rig.anyAnchored());
    }

    @Test
    public void pushingTheGroundBackwardsMovesForward() {
        Arena ar = arena();
        ArmRig rig = rigAt(0f, 14f);
        boolean anchoredSeen = false;
        for (int s = 0; s < 4; s++) {
            Quat c = new Quat();
            int n = 30;
            for (int i = 0; i <= n; i++) {
                c.setEuler(0f, -0.55f - 1.0f * i / n, 0f);
                rig.update(ar.physics, c, true, 1, 0f, DT);
                anchoredSeen |= rig.anyAnchored();
            }
            c.setEuler(0f, 0.1f, 0f);
            for (int i = 0; i < 12; i++) rig.update(ar.physics, c, true, 1, 0f, DT);
        }
        for (int i = 0; i < 72; i++) rig.update(ar.physics, new Quat().setEuler(0f, 0.1f, 0f), true, 1, 0f, DT);
        assertTrue("hands grabbed the ground", anchoredSeen);
        assertTrue("moved forward: " + (14f - rig.head.z), 14f - rig.head.z > 2.5f);
        assertEquals("back to standing height", 1.0f, rig.head.y, 0.1f);
    }

    @Test
    public void fastDownwardPushJumps() {
        Arena ar = arena();
        ArmRig rig = rigAt(0f, 14f);
        Quat c = new Quat();
        // place hands on the ground in front, then slam straight down quickly
        for (int i = 0; i < 6; i++) { c.setEuler(0f, -0.6f, 0f); rig.update(ar.physics, c, true, 1, 0f, DT); }
        for (int i = 0; i <= 8; i++) { c.setEuler(0f, -0.6f - 1.0f * i / 8, 0f); rig.update(ar.physics, c, true, 1, 0f, DT); }
        float maxY = rig.head.y;
        c.setEuler(0f, 0.6f, 0f); // let go
        for (int i = 0; i < 60; i++) { rig.update(ar.physics, c, true, 1, 0f, DT); maxY = Math.max(maxY, rig.head.y); }
        assertTrue("jumped: max head height " + maxY, maxY > 1.45f);
    }

    @Test
    public void climbsAWallByPullingDown() {
        Arena ar = arena();
        // in front of the steel pole at (18,18) (6 m high, 0.6 wide): face +Z? pole is at z=18, stand at z=16.9 facing +Z
        float yaw = (float) Math.PI; // face +Z
        ArmRig rig = rigAt(18f, 17.25f);
        rig.headRot.setYaw(yaw);
        float startY = rig.head.y;
        Quat c = new Quat();
        boolean wallGrab = false;
        for (int s = 0; s < 6; s++) {
            for (int i = 0; i <= 25; i++) { // pull down
                c.setEuler(yaw, 0.6f - 1.2f * i / 25f, 0f);
                rig.update(ar.physics, c, true, 1, 0f, DT);
                for (ArmRig.Hand h : rig.hands) if (h.anchored && Math.abs(h.normal.y) < 0.6f) wallGrab = true;
            }
            for (int i = 0; i <= 25; i++) { // reach up again (hand slides up the pole)
                c.setEuler(yaw, -0.6f + 1.2f * i / 25f, 0f);
                rig.update(ar.physics, c, true, 1, 0f, DT);
            }
        }
        assertTrue("hands grabbed the pole", wallGrab);
        assertTrue("climbed up the pole: " + rig.head.y, rig.head.y > startY + 1.0f);
    }

    @Test
    public void botsInfectEveryoneEventually() {
        Settings s = new Settings();
        s.botCount = 5;
        s.difficulty = 2;
        Game g = new Game(s, new TestPlatform());
        g.startGame(TagGame.INFECTION);
        InputFrame in = new InputFrame();
        // keep the player out of the way on top of the tall pole so only bots play
        int start = -1;
        for (int i = 0; i < 72 * 120; i++) {
            g.rig.spawn(new Vec3(-19f, 8.5f, -18f));
            g.update(DT, in);
            if (g.tag.phase == TagGame.PLAYING && start < 0) start = g.tag.countIt();
            if (g.tag.phase == TagGame.OVER) break;
        }
        int it = g.tag.countIt();
        assertTrue("infection spread among bots: " + start + " -> " + it, it >= start + 2);
    }

    @Test
    public void playerCanTagABotWithHands() {
        Settings s = new Settings();
        s.botCount = 1;
        Game g = new Game(s, new TestPlatform());
        g.startGame(TagGame.CLASSIC);
        InputFrame in = new InputFrame();
        for (int i = 0; i < 72 * 4; i++) g.update(DT, in); // countdown
        Avatar bot = g.tag.avatars.get(1);
        g.me.it = true;
        bot.it = false;
        g.me.immunity = 0f;
        bot.immunity = 0f;
        // put the bot right in front of the player's hand
        g.update(DT, in);
        Vec3 hand = g.rig.hands[1].pos;
        bot.head.set(hand.x, hand.y + 0.3f, hand.z);
        g.tag.checkContacts(0.72f, 0.42f);
        assertTrue("bot got tagged", bot.it);
        assertTrue("tag passed on", !g.me.it);
    }

    @Test
    public void gameRendersAndSoundsSynthesize() {
        Game g = new Game(new Settings(), new TestPlatform());
        RenderQueue q = new RenderQueue();
        g.update(DT, new InputFrame());
        g.render(q);
        assertTrue(q.count > 30);
        assertEquals(1, q.staticCount);
        for (int i = 0; i < Sfx.COUNT; i++) assertTrue(Sfx.NAMES[i], SoundSynth.generate(i).length > 100);
        assertTrue(g.arena.spawns.size() >= 8);
        assertTrue(g.arena.perches.size() >= 6);
    }
}
