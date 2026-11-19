package com.joaolimz.operationzero;

import com.joaolimz.operationzero.game.Game;
import com.joaolimz.operationzero.game.RenderQueue;
import com.joaolimz.operationzero.game.ai.Npc;
import com.joaolimz.operationzero.game.ai.Ragdoll;
import com.joaolimz.operationzero.game.ai.SpawnPoint;
import com.joaolimz.operationzero.game.audio.Sfx;
import com.joaolimz.operationzero.game.audio.SoundSynth;
import com.joaolimz.operationzero.game.input.InputFrame;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.player.Hand;
import com.joaolimz.operationzero.game.player.Inventory;
import com.joaolimz.operationzero.game.save.SaveData;
import com.joaolimz.operationzero.game.save.Settings;
import com.joaolimz.operationzero.game.weapons.Magazine;
import com.joaolimz.operationzero.game.weapons.Weapon;
import com.joaolimz.operationzero.game.weapons.WeaponType;
import com.joaolimz.operationzero.game.world.Door;
import com.joaolimz.operationzero.game.world.ItemKind;
import com.joaolimz.operationzero.game.world.Prop;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless simulation tests of the real game code (same classes that run on the headset).
 * Inputs are injected through {@link InputFrame} exactly as the Android layer does.
 */
public class GameSimulationTest {
    private static final float DT = 1f / 72f;
    private TestPlatform platform;
    private Game game;
    private InputFrame input;

    @Before
    public void setUp() {
        platform = new TestPlatform();
        Settings s = new Settings();
        game = new Game(s, platform, null);
        input = new InputFrame();
        input.hasControllerPose = true;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) game.update(DT, input);
    }

    private void tap(Runnable press, Runnable release) {
        press.run();
        step(2);
        release.run();
        step(2);
    }

    /** Finds a controller orientation that brings the active hand (or a point on the held object) close to target. */
    private float aimHandAt(Vec3 target, boolean useHeldTop) {
        Hand h = game.player.activeHand();
        float bestD = Float.MAX_VALUE, bestYaw = 0, bestPitch = 0;
        Quat q = new Quat();
        for (int pass = 0; pass < 2; pass++) {
            float yc = pass == 0 ? 0f : bestYaw, pc = pass == 0 ? 0f : bestPitch;
            float range = pass == 0 ? 3.1f : 0.25f, n = pass == 0 ? 40 : 30;
            for (int iy = 0; iy <= n; iy++) {
                for (int ip = 0; ip <= n; ip++) {
                    float yaw = yc - range + 2 * range * iy / n;
                    float pitch = Math.max(-1.55f, Math.min(1.55f, pc - (pass == 0 ? 1.5f : range) + 2 * (pass == 0 ? 1.5f : range) * ip / n));
                    q.setEuler(yaw - game.player.yaw, pitch, 0f);
                    input.controller.set(q);
                    step(1);
                    Vec3 p = useHeldTop && h.held != null ? h.held.body.pos : h.pos;
                    float d = p.distance(target);
                    if (d < bestD) { bestD = d; bestYaw = yaw; bestPitch = pitch; }
                }
            }
        }
        q.setEuler(bestYaw - game.player.yaw, bestPitch, 0f);
        input.controller.set(q);
        step(1);
        return bestD;
    }

    private void lookAt(Vec3 target) {
        Hand h = game.player.activeHand();
        for (int i = 0; i < 4; i++) {
            Vec3 d = new Vec3().setSub(target, h.pos);
            Quat world = new Quat().setLookRotation(d.x, d.y, d.z, 0, 1, 0);
            Quat yawInv = new Quat().setYaw(-game.player.yaw);
            input.controller.setMul(yawInv, world);
            step(2);
        }
    }

    private Weapon findWeapon(WeaponType t) {
        for (Prop p : game.level.props) if (p instanceof Weapon && ((Weapon) p).type == t) return (Weapon) p;
        return null;
    }

    @Test
    public void levelIsBuiltWithSectorsNavigationAndCover() {
        assertEquals(7, game.level.sectors.size());
        assertTrue("nav nodes", game.level.nav.size() > 80);
        int linked = 0;
        for (int i = 0; i < game.level.nav.size(); i++) if (game.level.nav.neighbors(i).length > 0) linked++;
        assertTrue("linked nav nodes " + linked, linked > 70);
        assertTrue("cover points " + game.level.covers.size(), game.level.covers.size() > 40);
        assertTrue(game.level.spawns.size() >= 20);
        // path from the parking lot to the secret lab exists once doors are open
        for (Door d : game.level.doors) d.forceOpen();
        int a = game.level.nav.nearest(new Vec3(0, 0, 30), null, false, null);
        int b = game.level.nav.nearest(new Vec3(-13, -6, -68), null, false, null);
        int[] path = new int[64];
        int len = game.level.nav.findPath(a, b, path);
        assertTrue("path length " + len, len > 5);
        // streaming: only part of the map is active
        assertTrue(game.sectors.activeCount < 7);
    }

    @Test
    public void allSoundsSynthesize() {
        for (int i = 0; i < Sfx.COUNT; i++) {
            short[] pcm = SoundSynth.generate(i);
            assertTrue(Sfx.NAMES[i], pcm.length > 100);
            assertTrue(SoundSynth.toWav(pcm).length == 44 + pcm.length * 2);
        }
    }

    @Test
    public void menuDwellStartsGame() {
        assertEquals(Game.State.MENU, game.state);
        input.hasControllerPose = false;
        step(1);
        // look at the JOGAR button (top-left)
        game.menu.place(game.player.eye, 0f, 1.6f);
        Vec3 btn = new Vec3(game.menu.center).add(-0.36f, 0.24f, 0f);
        Vec3 d = new Vec3().setSub(btn, game.player.eye);
        input.head.setLookRotation(d.x, d.y, d.z, 0, 1, 0);
        step((int) (2.2f / DT));
        assertEquals(Game.State.PLAYING, game.state);
    }

    @Test
    public void pickUpShootEjectReloadWithSingle3DofController() {
        game.newGame();
        step(10);
        Weapon pistol = findWeapon(WeaponType.VX9);
        assertNotNull(pistol);
        // distance grab: point at the pistol and pull the trigger
        lookAt(pistol.body.pos);
        assertEquals("pointing at pistol", pistol, game.player.activeHand().pointProp);
        input.trigger = true;
        step(60);
        input.trigger = false;
        step(3);
        Hand right = game.player.activeHand();
        assertEquals(pistol, right.held);
        assertTrue(pistol.chambered);
        assertEquals(12, pistol.mag.rounds);
        // shoot all 13 rounds
        int shots = 0;
        for (int i = 0; i < 20; i++) {
            input.trigger = true; step(2);
            input.trigger = false; step(12);
        }
        shots = pistol.shotsFired;
        assertEquals(13, shots);
        assertTrue(pistol.slideLocked);
        assertEquals(0, pistol.totalLoaded());
        // eject the empty magazine with the touchpad click
        tap(new Runnable() { public void run() { input.action = true; } }, new Runnable() { public void run() { input.action = false; } });
        assertNull(pistol.mag);
        // reserve ammo: grab the ammo box from the table first
        Prop box = null;
        for (Prop p : game.level.props) if (p.kind == ItemKind.AMMO_BOX && p.active && p.body.pos.z > 26f && p.body.pos.z < 28f && Math.abs(p.body.pos.x) < 2) box = p;
        assertNotNull(box);
        // switch to the left hand (APP button) and use it
        tap(new Runnable() { public void run() { input.switchHand = true; } }, new Runnable() { public void run() { input.switchHand = false; } });
        Hand left = game.player.activeHand();
        assertFalse(left.right);
        lookAt(box.body.pos);
        input.trigger = true; step(40); input.trigger = false; step(3);
        assertEquals(24, game.player.inventory.reserve[WeaponType.AMMO_9MM]);
        // reach the ammo pouch on the hip and take a magazine
        Vec3 pouch = Inventory.zone(Inventory.POUCH, game.player.eye, game.player.torso, true, new Vec3());
        float d = aimHandAt(pouch, false);
        assertTrue("pouch reachable, d=" + d, d < Inventory.radius(Inventory.POUCH));
        input.trigger = true; step(2);
        assertTrue("magazine in hand", left.held instanceof Magazine);
        Magazine mag = (Magazine) left.held;
        assertEquals(12, mag.rounds);
        assertEquals(12, game.player.inventory.reserve[WeaponType.AMMO_9MM]);
        // bring the magazine to the magwell of the pistol held by the parked right hand
        Vec3 well = new Vec3();
        pistol.body.rot.rotate(0f, pistol.type.magY, pistol.type.magZ, well).add(pistol.body.pos);
        float dm = aimHandAt(well, true);
        assertTrue("magwell reachable, d=" + dm, dm < 0.14f || pistol.mag != null);
        step(2);
        assertEquals("magazine inserted", mag, pistol.mag);
        assertTrue("slide released and chambered", pistol.chambered);
        input.trigger = false; step(2);
        // back to the right hand and fire again
        tap(new Runnable() { public void run() { input.switchHand = true; } }, new Runnable() { public void run() { input.switchHand = false; } });
        assertTrue(game.player.activeHand().right);
        int before = pistol.shotsFired;
        input.trigger = true; step(2); input.trigger = false; step(10);
        assertEquals(before + 1, pistol.shotsFired);
        assertEquals(10, pistol.mag.rounds);
    }

    @Test
    public void holsterAndDrawFromHip() {
        game.newGame();
        Weapon pistol = findWeapon(WeaponType.VX9);
        lookAt(pistol.body.pos);
        input.trigger = true; step(60); input.trigger = false; step(3);
        assertEquals(pistol, game.player.activeHand().held);
        Vec3 hip = Inventory.zone(Inventory.HIP, game.player.eye, game.player.torso, true, new Vec3());
        float d = aimHandAt(hip, false);
        assertTrue("hip reachable d=" + d, d < Inventory.radius(Inventory.HIP));
        input.action = true; step(2); input.action = false; step(2);
        assertEquals(pistol, game.player.inventory.items[Inventory.HIP]);
        assertNull(game.player.activeHand().held);
        // draw it again
        input.trigger = true; step(2);
        assertEquals(pistol, game.player.activeHand().held);
        input.trigger = false; step(2);
    }

    @Test
    public void objectsCanBeGrabbedAndThrown() {
        game.newGame();
        Prop bottle = null;
        for (Prop p : game.level.props) if (p.kind == ItemKind.CAN && p.body.pos.z > 20) bottle = p;
        assertNotNull(bottle);
        game.player.feet.set(-2.3f, 0f, 25.3f);
        step(3);
        lookAt(bottle.body.pos);
        input.trigger = true;
        step(50);
        assertEquals(bottle, game.player.activeHand().held);
        assertTrue(bottle.body.kinematic);
        // swing the controller quickly upward and release
        Quat q = new Quat();
        for (int i = 0; i < 6; i++) {
            q.setEuler(0f, -0.6f + i * 0.3f, 0f);
            input.controller.set(q);
            step(1);
        }
        input.trigger = false;
        step(1);
        assertNull(game.player.activeHand().held);
        assertFalse(bottle.body.kinematic);
        assertTrue("thrown speed " + bottle.body.vel.length(), bottle.body.vel.length() > 1.5f);
        Vec3 start = new Vec3(bottle.body.pos);
        step(30);
        assertTrue(bottle.body.pos.distance(start) > 0.3f);
        // physics eventually sleeps the object
        step(600);
        assertTrue(!bottle.active || bottle.body.sleeping);
    }

    @Test
    public void leverOpensShutterAndHingedDoorsSwing() {
        game.newGame();
        Door shutter = game.level.door("door_shutter");
        assertTrue(shutter.locked);
        // walk near the lever and pull it by pointing (3DoF accessibility path)
        game.player.feet.set(3.6f, 0f, 1.8f);
        game.player.yaw = 0f;
        step(5);
        lookAt(game.level.interactive("lever_power").pos);
        input.trigger = true; step(3); input.trigger = false; step(3);
        assertTrue(game.flags.contains("power_on"));
        step(300);
        assertTrue(shutter.isOpen());
        // walking into the lobby completes mission 1
        game.player.feet.set(0f, 0f, -3f);
        step(5);
        assertEquals(1, game.missions.current);
        assertNotNull(platform.files.get(SaveData.FILE));
        // push the office door by walking through it
        Door office = game.level.door("door_office");
        assertFalse(office.locked);
        game.player.feet.set(-4.8f, 0f, -4.4f);
        input.moveX = 0f;
        input.moveY = 1f;
        Quat west = new Quat().setYaw((float) Math.PI * 0.5f);
        input.head.set(west);
        step(120);
        input.moveY = 0f;
        input.head.identity();
        assertTrue("door angle " + office.hinge.angle, Math.abs(office.hinge.angle) > 0.3f);
        assertTrue("player passed", game.player.feet.x < -6.2f);
        // the corridor door stays locked until the security terminal is used
        Door corr = game.level.door("door_corridor");
        assertTrue(corr.locked);
        game.onEvent("unlock_corridor");
        assertFalse(corr.locked);
    }

    @Test
    public void npcSeesAttacksTakesCoverAndDiesIntoRagdoll() {
        game.newGame();
        // move player to the warehouse with an enemy
        game.player.feet.set(3.5f, 0f, -25.8f);
        game.player.yaw = (float) Math.PI * -0.5f; // facing +X (into the warehouse)
        step(5);
        game.npcs.clearAll();
        SpawnPoint sp = new SpawnPoint("test", 15f, 0f, -26f, (float) Math.PI * 0.5f, 2);
        game.level.spawns.add(sp);
        game.npcs.queue("test", Npc.SOLDIER, null, "test");
        // spawning out of view is required: turn the player around first
        game.player.yaw = (float) Math.PI * 0.5f;
        step(60);
        Npc n = null;
        for (Npc c : game.npcs.npcs) if (c.alive && c.tag.equals("test")) n = c;
        assertNotNull("npc spawned", n);
        game.player.yaw = (float) Math.PI * -0.5f;
        boolean alerted = false, attacked = false, covered = false;
        float hp0 = game.player.health;
        for (int i = 0; i < 72 * 25 && game.player.alive; i++) {
            step(1);
            if (n.state == Npc.State.ALERT) alerted = true;
            if (n.state == Npc.State.ATTACK) attacked = true;
            if (n.state == Npc.State.COVER) covered = true;
            if (alerted && (attacked || covered) && game.player.health < hp0 && covered) break;
        }
        assertTrue("alerted", alerted);
        assertTrue("attack or cover", attacked || covered);
        assertTrue("player damaged " + game.player.health, game.player.health < hp0);
        assertTrue("used cover", covered);
        // kill it with a head shot worth of damage
        game.npcs.damage(n, 500f, 0, n.pos, new Vec3(1, 0, 0));
        assertFalse(n.alive);
        assertEquals(Npc.State.DEAD, n.state);
        Ragdoll r = null;
        for (Ragdoll c : game.npcs.ragdolls) if (c.active) r = c;
        assertNotNull("ragdoll active", r);
        float headY0 = r.p[Ragdoll.HEAD].y;
        step(72 * 2);
        assertTrue("ragdoll fell", r.p[Ragdoll.HEAD].y < headY0 - 0.5f);
        step((int) (72 * (game.settings.corpseTime() + 4)));
        assertFalse("ragdoll recycled", r.active);
        // an ammo box was dropped
        boolean drop = false;
        for (Prop p : game.level.props) if (p.kind == ItemKind.AMMO_BOX && p.active && p.body.pos.distance(n.pos) < 2f) drop = true;
        assertTrue(drop);
    }

    @Test
    public void playerDiesAndRestartsFromCheckpoint() {
        game.newGame();
        game.onEvent("enter_lobby");
        assertEquals(1, game.missions.current);
        game.player.damage(500f, null);
        step(2);
        assertEquals(Game.State.DEAD, game.state);
        game.restartCheckpoint();
        assertEquals(Game.State.PLAYING, game.state);
        assertEquals(1, game.missions.current);
        assertEquals(100f, game.player.health, 0.01f);
        assertTrue(game.level.door("door_shutter").isOpen());
    }

    @Test
    public void fullCampaignEventsReachVictoryAndSaveLoads() {
        game.newGame();
        game.onEvent("power_on");
        game.onEvent("enter_lobby");
        game.onEvent("pickup_keycard");
        game.onEvent("unlock_corridor");
        game.onEvent("lab_card");
        assertEquals(3, game.missions.current);
        assertTrue(game.level.door("door_lab").target >= 1f);
        game.onEvent("secret_power");
        game.onEvent("pickup_device");
        assertEquals(4, game.missions.current);
        assertTrue(game.flags.contains("alarm"));
        // the alarm wave must be eliminated
        game.player.feet.set(-13f, -6f, -66f);
        step(72 * 3);
        int guard = 0;
        while ((game.npcs.alive("alarm") > 0 || game.npcs.pending("alarm") > 0) && guard++ < 400) {
            for (Npc n : game.npcs.npcs) if (n.alive) game.npcs.damage(n, 999f, 0, n.pos, new Vec3(0, 0, 1));
            game.player.health = 100f;
            step(36);
        }
        step(2);
        assertEquals("alarm cleared", 5, game.missions.current);
        assertFalse(game.level.door("door_tunnel").locked);
        assertTrue(game.level.interactive("btn_garage").enabled);
        // checkpoint saved -> continue loads mission 5
        SaveData sd = SaveData.parse(platform.files.get(SaveData.FILE));
        assertEquals(5, sd.mission);
        game.toMainMenu();
        game.continueGame();
        assertEquals(5, game.missions.current);
        assertFalse(game.level.door("door_tunnel").locked);
        game.onEvent("garage_open");
        game.onEvent("exit_reached");
        assertEquals(Game.State.VICTORY, game.state);
        assertTrue(game.missions.finished());
    }

    @Test
    public void sectorStreamingAndRenderQueue() {
        game.newGame();
        RenderQueue q = new RenderQueue();
        game.render(q);
        assertTrue(q.count > 20);
        assertTrue(q.staticCount >= 1 && q.staticCount < 7);
        // the underground is not resident while outside
        int under = game.level.sectors.get(4).staticMeshes.get(0);
        assertFalse(game.meshes.isResident(under));
        game.player.feet.set(-10f, -6f, -40f);
        step(3);
        assertTrue(game.meshes.isResident(under));
        assertFalse(game.level.sectors.get(0).active);
    }

    @Test
    public void physicsBudgetAndSleep() {
        game.newGame();
        // wake every prop in the exterior and check the budget holds
        for (Prop p : game.level.props) {
            if (p.body.sector == 0 && p.active && !p.isHeld()) { p.body.wake(); p.body.vel.set(0, 0.1f, 0); }
        }
        step(3);
        assertTrue("awake " + game.level.physics.activeBodyCount, game.level.physics.activeBodyCount <= game.settings.maxBodies());
        step(72 * 4);
        assertTrue(game.level.physics.activeBodyCount <= 2);
    }

    @Test
    public void settingsPersist() {
        Settings s = new Settings();
        s.quality = Settings.HIGH;
        s.turnMode = 1;
        s.keys[Settings.A_TRIGGER] = new int[]{123};
        Settings r = new Settings();
        r.load(s.serialize());
        assertEquals(Settings.HIGH, r.quality);
        assertEquals(1, r.turnMode);
        assertEquals(123, r.keys[Settings.A_TRIGGER][0]);
        assertEquals(Settings.LOW, new Settings().quality);
    }
}
