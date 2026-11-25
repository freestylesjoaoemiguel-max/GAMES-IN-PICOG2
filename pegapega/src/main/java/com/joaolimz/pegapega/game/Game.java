package com.joaolimz.pegapega.game;

import com.joaolimz.pegapega.game.audio.AudioSystem;
import com.joaolimz.pegapega.game.audio.Sfx;
import com.joaolimz.pegapega.game.input.ControllerMapping;
import com.joaolimz.pegapega.game.input.ImuHand;
import com.joaolimz.pegapega.game.input.InputFrame;
import com.joaolimz.pegapega.game.input.Platform;
import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.mesh.Assets;
import com.joaolimz.pegapega.game.mesh.MeshLibrary;
import com.joaolimz.pegapega.game.perf.PerfMonitor;
import com.joaolimz.pegapega.game.physics.StaticCollider;
import com.joaolimz.pegapega.game.player.ArmRig;
import com.joaolimz.pegapega.game.save.Settings;
import com.joaolimz.pegapega.game.tag.Avatar;
import com.joaolimz.pegapega.game.tag.BotSystem;
import com.joaolimz.pegapega.game.tag.TagGame;
import com.joaolimz.pegapega.game.ui.Menu;
import com.joaolimz.pegapega.game.ui.Screens;
import com.joaolimz.pegapega.game.world.Arena;

/** PICO VR: PEGA-PEGA - arm locomotion tag game against bots, fully offline. */
public final class Game implements TagGame.Listener {
    public enum State { MENU, PLAYING, PAUSED }

    public final Settings settings;
    public final Platform platform;
    public final AudioSystem audio = new AudioSystem();
    public final MeshLibrary meshes = new MeshLibrary();
    public final Assets assets;
    public final PerfMonitor perf = new PerfMonitor();
    public final Menu menu = new Menu();
    public final Screens screens;
    public final SceneBuilder scene;
    public final Arena arena;
    public final ArmRig rig = new ArmRig();
    public final TagGame tag = new TagGame();
    public BotSystem bots;
    public Avatar me;
    public final Particles particles = new Particles();

    public State state = State.MENU;
    public float time;
    public float yaw;
    public final Quat headWorld = new Quat();
    public final Quat ctrlWorld = new Quat();
    public int activeArm = 1;
    public String hint;
    public float hintTimer;
    public String banner;
    public float bannerTimer;
    public float fade = 1f;
    public float vignette;
    public float hurt;

    // input
    public final InputFrame in = new InputFrame();
    public final InputFrame prev = new InputFrame();
    private float switchHeld;
    private boolean switchConsumed;
    private float snapCooldown;
    private boolean turnLatch;

    // controller (shared design with Operation Zero)
    public final ControllerMapping ctrlMap = new ControllerMapping();
    private boolean ctrlAligned, calibOffered, ctrlWasConnected;
    private float realignTimer = -1f, homeHeld;
    public int calibStep = -1;
    public String calibMessage = "";
    private final float[][] calibRaw = new float[3][4];
    private float calibHeadYaw;
    private final float[][] calibTouch = new float[3][2];
    private String calibReturn = "main";

    // controller accelerometer (real arm motion)
    public final ImuHand imu = new ImuHand();
    private final float[] accEma = new float[3];
    private final float[][] calibAcc = new float[3][3];
    private final Vec3 zero = new Vec3();

    private int windStream;
    private float windVol;
    private final Vec3 t0 = new Vec3(), t1 = new Vec3();
    private final Quat q0 = new Quat();

    public Game(Settings settings, Platform platform) {
        this.settings = settings;
        this.platform = platform;
        this.assets = new Assets(meshes);
        this.arena = new Arena().build(meshes, -1);
        this.screens = new Screens(this);
        this.scene = new SceneBuilder(this);
        tag.listener = this;
        loadMapping();
        setupAvatars();
        attract();
        applySettings();
        rig.spawn(new Vec3(0f, settings.standHeight, 14f));
        yaw = 0f;
        updateHead();
        screens.open("main");
    }

    // ====================================================================== setup
    public void applySettings() {
        rig.armLength = settings.armLength;
        rig.standHeight = settings.standHeight;
        rig.jumpMultiplier = settings.flingBoost;
        rig.climbAssist = settings.climbAssist;
        imu.gain = settings.imuGain;
        if (settings.accelCalibrated) {
            System.arraycopy(settings.accelPerm, 0, imu.pa, 0, 9);
            imu.gravityIncluded = settings.accelGravity;
            imu.gravity = settings.accelG;
        } else {
            imu.setDefaultFromBody(settings.ctrlPb);
        }
        audio.master = settings.master;
        audio.sfxVolume = settings.sfx;
        audio.ambientVolume = settings.ambient;
        audio.refreshVolumes();
        particles.limit = settings.maxParticles();
        if (bots != null) bots.setDifficulty(settings.difficulty);
    }

    public void saveSettings() { platform.writeFile(Settings.FILE, settings.serialize()); }

    private void loadMapping() {
        System.arraycopy(settings.ctrlPw, 0, ctrlMap.pw, 0, 9);
        System.arraycopy(settings.ctrlPb, 0, ctrlMap.pb, 0, 9);
        ctrlMap.inverse = settings.ctrlInverse;
    }

    /** (Re)creates the player + bots. */
    public void setupAvatars() {
        tag.avatars.clear();
        bots = new BotSystem(arena);
        bots.setDifficulty(settings.difficulty);
        me = new Avatar(0, true);
        me.name = settings.playerName;
        me.color = settings.playerColor;
        tag.avatars.add(me);
        int n = MathUtil.clamp(settings.botCount, 1, 9);
        int c = 0;
        for (int i = 0; i < n; i++) {
            Avatar a = new Avatar(i + 1, false);
            if (c == settings.playerColor) c++;
            a.color = c++ % Avatar.COLORS.length;
            a.name = Avatar.BOT_NAMES[i % Avatar.BOT_NAMES.length];
            tag.avatars.add(a);
            bots.add(a);
        }
    }

    /** Bots wander around in the main menu (attract mode). */
    private void attract() {
        tag.mode = TagGame.FREE;
        tag.startRound();
        tag.phase = TagGame.PLAYING;
        spawnAll();
    }

    private void spawnAll() {
        for (int i = 0; i < tag.avatars.size(); i++) {
            Avatar a = tag.avatars.get(i);
            Vec3 sp = arena.spawns.get(i % arena.spawns.size());
            a.head.set(sp.x, settings.standHeight, sp.z);
            a.vel.set(0, 0, 0);
            a.yaw = (float) Math.atan2(sp.x, sp.z); // face the center
        }
        Vec3 sp = arena.spawns.get(0);
        rig.spawn(new Vec3(sp.x, settings.standHeight, sp.z));
        yaw = (float) Math.atan2(sp.x, sp.z);
    }

    public void startGame(int mode) {
        settings.mode = mode;
        saveSettings();
        setupAvatars();
        tag.mode = mode;
        tag.round = 0;
        tag.startRound();
        spawnAll();
        state = State.PLAYING;
        fade = 1f;
        audio.setAmbient(Sfx.AMBIENT);
        showBanner(TagGame.MODE_NAMES[mode], 2.5f);
    }

    public void restartRound() {
        tag.startRound();
        spawnAll();
        state = State.PLAYING;
        fade = 1f;
    }

    public void pause() {
        if (state != State.PLAYING) return;
        state = State.PAUSED;
        screens.open("pause");
    }

    public void resume() { if (state == State.PAUSED) state = State.PLAYING; }

    public void toMenu() {
        state = State.MENU;
        setupAvatars();
        attract();
        screens.open("main");
    }

    public void showHint(String t) { hint = t; hintTimer = 3.5f; }

    public void showBanner(String t, float s) { banner = t; bannerTimer = s; }

    // ====================================================================== tag callbacks
    @Override public void onTag(Avatar chaser, Avatar victim) {
        audio.play(Sfx.TAG, victim.head, 1f, 1f, 40f);
        for (int i = 0; i < 10; i++) particles.burst(victim.head, 0.75f, 0.35f, 1f);
        if (victim == me) {
            hurt = 1f;
            platform.vibrate(120, 1f);
            audio.play2D(Sfx.INFECTED, 0.9f);
            showBanner(tag.mode == TagGame.INFECTION ? "VOCÊ FOI INFECTADO! Pegue os outros!" : "VOCÊ É O PEGADOR!", 3f);
        } else if (chaser == me) {
            platform.vibrate(60, 0.8f);
            showBanner("Você pegou " + victim.name + "!", 2f);
        }
    }

    @Override public void onRoundStart() {
        audio.play2D(Sfx.WHISTLE, 0.9f);
        showBanner(me.it ? (tag.mode == TagGame.INFECTION ? "VOCÊ COMEÇA INFECTADO! Pegue todo mundo!" : "VOCÊ É O PEGADOR!")
                : "CORRA! Fuja dos pegadores!", 3f);
    }

    @Override public void onRoundOver(String result) {
        boolean won = tag.mode == TagGame.INFECTION ? !me.it || tag.countIt() == tag.avatars.size() && me.it && !me.taggedThisRound
                : bestClassic() == me;
        audio.play2D(won ? Sfx.WIN : Sfx.ROUND_END, 0.9f);
        showBanner(result, 7f);
    }

    private Avatar bestClassic() {
        Avatar best = null;
        for (Avatar v : tag.avatars) if (best == null || v.timeIt < best.timeIt) best = v;
        return best;
    }

    // ====================================================================== controller
    public void recenterAll() {
        platform.recenter();
        realignTimer = 0.15f;
        showHint("Visão e controle recentralizados");
    }

    private void processController(float dt) {
        if (!in.ctrlConnected) {
            ctrlWasConnected = false;
            return;
        }
        if (!ctrlWasConnected) {
            ctrlWasConnected = true;
            ctrlAligned = false;
            if (!settings.ctrlCalibrated && !calibOffered && state == State.MENU) {
                calibOffered = true;
                startCalibration();
            }
        }
        if (realignTimer >= 0f) {
            realignTimer -= dt;
            if (realignTimer < 0f) ctrlAligned = false;
        }
        if (!ctrlAligned) {
            ctrlMap.alignYaw(in.ctrlX, in.ctrlY, in.ctrlZ, in.ctrlW, in.head.yaw());
            ctrlAligned = true;
        }
        ctrlMap.apply(in.ctrlX, in.ctrlY, in.ctrlZ, in.ctrlW, in.controller);
        in.hasControllerPose = true;
        in.trigger |= in.ctrlTrigger;
        in.action |= in.ctrlClick;
        in.switchHand |= in.ctrlApp;
        if (in.ctrlHome) {
            homeHeld += dt;
            if (homeHeld > 0.9f && homeHeld - dt <= 0.9f) recenterAll();
        } else {
            homeHeld = 0f;
        }
        if (in.ctrlTouching && !in.ctrlClick && calibStep < 0) {
            float dx = in.ctrlTouchX - settings.touchCX, dy = in.ctrlTouchY - settings.touchCY;
            float fx = settings.touchFX - settings.touchCX, fy = settings.touchFY - settings.touchCY;
            float rx = settings.touchRX - settings.touchCX, ry = settings.touchRY - settings.touchCY;
            float fwd = MathUtil.clamp((dx * fx + dy * fy) / Math.max(1f, fx * fx + fy * fy), -1f, 1f);
            float rt = MathUtil.clamp((dx * rx + dy * ry) / Math.max(1f, rx * rx + ry * ry), -1f, 1f);
            if (Math.abs(rt) > 0.55f && Math.abs(rt) > Math.abs(fwd) * 1.2f) in.turnX = Math.signum(rt) * Math.max(0.65f, Math.abs(rt));
            else if (Math.abs(fwd) > 0.22f) in.moveY = Math.signum(fwd) * Math.min(1f, (Math.abs(fwd) - 0.22f) / 0.78f * 1.3f);
        }
    }

    public static final String[] CALIB_TEXT = {
            "1/6  Aponte o controle para FRENTE (para onde você olha) e aperte o GATILHO",
            "2/6  Aponte o controle para CIMA (para o céu) e aperte o GATILHO",
            "3/6  Aponte o controle para a sua DIREITA e aperte o GATILHO",
            "4/6  Encoste o polegar no CENTRO do touchpad (sem clicar) e aperte o GATILHO",
            "5/6  Encoste o polegar na parte de CIMA do touchpad e aperte o GATILHO",
            "6/6  Encoste o polegar na parte DIREITA do touchpad e aperte o GATILHO"
    };

    public void startCalibration() {
        calibReturn = state == State.PAUSED ? "pause" : "main";
        calibStep = 0;
        calibMessage = "";
        screens.open("calib");
    }

    public void endCalibration() {
        calibStep = -1;
        screens.open(calibReturn);
    }

    private void updateCalibration() {
        boolean press = in.ctrlTrigger && !prev.ctrlTrigger;
        if (!in.ctrlConnected) { calibMessage = "Controle não conectado: " + in.ctrlStatus; return; }
        if (!press) return;
        audio.play2D(Sfx.UI_CLICK, 0.6f);
        if (calibStep <= 2) {
            float[] r = calibRaw[calibStep];
            r[0] = in.ctrlX; r[1] = in.ctrlY; r[2] = in.ctrlZ; r[3] = in.ctrlW;
            System.arraycopy(accEma, 0, calibAcc[calibStep], 0, 3);
            if (calibStep == 0) calibHeadYaw = in.head.yaw();
            if (calibStep == 2) {
                float err = ctrlMap.solve(calibRaw[0], calibRaw[1], calibRaw[2]);
                if (err > 1.4f) {
                    loadMapping();
                    calibStep = 0;
                    calibMessage = "Não reconheci os movimentos. Vamos repetir.";
                    return;
                }
                ctrlMap.alignYaw(calibRaw[0][0], calibRaw[0][1], calibRaw[0][2], calibRaw[0][3], calibHeadYaw);
                ctrlAligned = true;
                System.arraycopy(ctrlMap.pw, 0, settings.ctrlPw, 0, 9);
                System.arraycopy(ctrlMap.pb, 0, settings.ctrlPb, 0, 9);
                settings.ctrlInverse = ctrlMap.inverse;
                settings.ctrlCalibrated = true;
                if (in.ctrlHasImu) {
                    Quat[] rots = new Quat[3];
                    for (int i = 0; i < 3; i++) rots[i] = ctrlMap.convert(calibRaw[i][0], calibRaw[i][1], calibRaw[i][2], calibRaw[i][3], new Quat());
                    imu.solve(rots, calibAcc);
                    System.arraycopy(imu.pa, 0, settings.accelPerm, 0, 9);
                    settings.accelGravity = imu.gravityIncluded;
                    settings.accelG = imu.gravity;
                    settings.accelCalibrated = true;
                    imu.reset();
                }
                saveSettings();
                calibMessage = "Orientação e sensor de movimento calibrados! Agora o touchpad.";
            }
            calibStep++;
            return;
        }
        if (!in.ctrlTouching) { calibMessage = "Encoste o polegar no touchpad (sem clicar) enquanto aperta o gatilho."; return; }
        float[] t = calibTouch[calibStep - 3];
        t[0] = in.ctrlTouchX; t[1] = in.ctrlTouchY;
        calibStep++;
        calibMessage = "";
        if (calibStep == 6) {
            float cx = calibTouch[0][0], cy = calibTouch[0][1];
            boolean ok = dist2(calibTouch[1], cx, cy) > 100f && dist2(calibTouch[2], cx, cy) > 100f;
            if (ok) {
                settings.touchCX = cx; settings.touchCY = cy;
                settings.touchFX = calibTouch[1][0]; settings.touchFY = calibTouch[1][1];
                settings.touchRX = calibTouch[2][0]; settings.touchRY = calibTouch[2][1];
                saveSettings();
                showHint("Controle calibrado!");
                endCalibration();
            } else {
                calibStep = 3;
                calibMessage = "Os toques ficaram muito próximos. Repita o touchpad.";
            }
        }
    }

    private static float dist2(float[] p, float cx, float cy) { return (p[0] - cx) * (p[0] - cx) + (p[1] - cy) * (p[1] - cy); }

    // ====================================================================== update
    private void updateHead() {
        q0.setYaw(yaw);
        headWorld.setMul(q0, in.head);
        rig.headRot.set(headWorld);
        if (in.hasControllerPose) {
            ctrlWorld.setMul(q0, in.controller);
        } else {
            // no controller: arms follow the gaze, tilted down toward the ground
            Quat tilt = new Quat().setAxisAngle(1, 0, 0, -0.7f);
            ctrlWorld.setMul(headWorld, tilt);
        }
        audio.setListener(rig.head, headWorld);
    }

    public void update(float dt, InputFrame input) {
        dt = MathUtil.clamp(dt, 0f, 0.05f);
        time += dt;
        prev.copyFrom(in);
        in.copyFrom(input);
        processController(dt);
        if (in.recenter && !prev.recenter) recenterAll();
        if (hintTimer > 0f) hintTimer -= dt;
        if (bannerTimer > 0f) bannerTimer -= dt;
        if (fade > 0f) fade = Math.max(0f, fade - dt * 2f);
        if (hurt > 0f) hurt = Math.max(0f, hurt - dt * 1.5f);
        trackButtons(dt);
        turning(dt);
        updateHead();

        // accelerometer: smoothed raw value (for calibration) and the hand offset
        float k = MathUtil.damp(10f, dt);
        accEma[0] += (in.ctrlAccX - accEma[0]) * k;
        accEma[1] += (in.ctrlAccY - accEma[1]) * k;
        accEma[2] += (in.ctrlAccZ - accEma[2]) * k;
        if (in.ctrlConnected && in.ctrlHasImu && settings.imuEnabled && in.hasControllerPose) {
            float gyro = (float) Math.sqrt(in.ctrlGyroX * in.ctrlGyroX + in.ctrlGyroY * in.ctrlGyroY + in.ctrlGyroZ * in.ctrlGyroZ);
            imu.update(in.ctrlAccX, in.ctrlAccY, in.ctrlAccZ, ctrlWorld, gyro, dt);
            rig.setImuOffsets(imu.offset, imu.offset);
        } else {
            imu.reset();
            rig.setImuOffsets(zero, zero);
        }

        boolean playing = state == State.PLAYING;
        float walk = playing && settings.touchpadWalk ? (in.up ? 1f : in.down ? -1f : in.moveY) : 0f;
        if (playing) {
            rig.update(arena.physics, ctrlWorld, settings.armMode == 0, activeArm, walk, dt);
        }
        // player avatar mirrors the rig
        me.head.set(rig.head);
        me.vel.set(rig.vel);
        me.yaw = headWorld.yaw();
        me.handL.set(rig.hands[0].pos);
        me.handR.set(rig.hands[1].pos);
        me.shoulderL.set(rig.hands[0].shoulder);
        me.shoulderR.set(rig.hands[1].shoulder);
        if (playing) {
            for (ArmRig.Hand h : rig.hands) {
                if (!h.tapped) continue;
                int sfx = h.tapMaterial == StaticCollider.MAT_METAL ? Sfx.TAP_METAL : h.tapMaterial == StaticCollider.MAT_WOOD ? Sfx.TAP_WOOD : Sfx.TAP_SOFT;
                audio.play(sfx, h.pos, 0.35f + 0.6f * h.tapStrength, 0.9f + 0.2f * h.tapStrength, 15f);
                platform.vibrate(12 + (int) (25 * h.tapStrength), 0.3f + 0.6f * h.tapStrength);
            }
            if (me.head.y < -8f) { rig.spawn(new Vec3(0, settings.standHeight, 12f)); }
        }

        boolean frozen = tag.phase == TagGame.COUNTDOWN && tag.mode != TagGame.FREE;
        bots.update(tag.avatars, dt, frozen);
        for (int i = 0; i < bots.taps.size() && i < 3; i++) {
            Vec3 p = bots.taps.get(i);
            if (p.distance(rig.head) < 14f) audio.play(Sfx.TAP_SOFT, p, 0.3f, 1.1f, 14f);
        }
        if (playing) {
            int before = tag.phase;
            tag.update(dt);
            if (tag.phase == TagGame.COUNTDOWN && before == TagGame.OVER) spawnAll();
            if (tag.phase == TagGame.COUNTDOWN && tag.phaseTime > 0f && (int) (tag.phaseTime + dt) != (int) tag.phaseTime) {
                audio.play2D(Sfx.COUNTDOWN, 0.6f);
            }
            tag.checkContacts(0.72f, 0.42f);
        } else {
            updateMenu(dt);
        }
        // infected sparks
        for (Avatar a : tag.avatars) {
            if (a.it && a != me && particles.rng.nextFloat() < dt * 12f) particles.burst(a.head, 0.75f, 0.35f, 1f);
        }
        particles.update(dt);
        wind(dt);
        float speed = rig.vel.length();
        float strength = settings.vignette == 0 ? 0f : settings.vignette == 1 ? 0.5f : 0.85f;
        vignette += (strength * MathUtil.clamp((speed - 2.5f) / 5f, 0f, 1f) - vignette) * MathUtil.damp(5f, dt);
        perf.activeNpcs = bots.bots.size();
        perf.aliveNpcs = tag.countIt();
        perf.effects = particles.active;
    }

    private void wind(float dt) {
        float speed = state == State.PLAYING ? rig.vel.length() : 0f;
        float target = MathUtil.clamp((speed - 2f) / 6f, 0f, 1f) * settings.master * settings.ambient;
        windVol += (target - windVol) * MathUtil.damp(4f, dt);
        AudioSystem.Sink sink = audioSink;
        if (sink == null) return;
        if (windStream == 0 && windVol > 0.02f) windStream = sink.play(Sfx.WIND, windVol, windVol, 1f, true);
        else if (windStream != 0) {
            if (windVol < 0.01f) { sink.stop(windStream); windStream = 0; }
            else sink.setVolume(windStream, windVol, windVol);
        }
    }

    private AudioSystem.Sink audioSink;

    public void setAudioSink(AudioSystem.Sink s) {
        audioSink = s;
        audio.setSink(s);
        audio.setAmbient(Sfx.AMBIENT);
    }

    private void trackButtons(float dt) {
        if (in.switchHand) {
            switchHeld += dt;
            if (switchHeld > 0.8f && !switchConsumed) {
                switchConsumed = true;
                if (state == State.PLAYING) pause();
            }
        } else {
            if (prev.switchHand && !switchConsumed && state == State.PLAYING) {
                activeArm = 1 - activeArm;
                if (settings.armMode == 1) showHint(activeArm == 1 ? "Braço DIREITO" : "Braço ESQUERDO");
            }
            switchHeld = 0f;
            switchConsumed = false;
        }
        if (in.menu && !prev.menu) {
            if (state == State.PLAYING) pause();
            else if (state == State.PAUSED && menu.screen.equals("pause")) resume();
        }
    }

    private void turning(float dt) {
        if (state != State.PLAYING) return;
        float turn = in.turnX;
        if (in.left) turn = -1f;
        if (in.right) turn = 1f;
        if (settings.turnMode == 0) {
            snapCooldown -= dt;
            if (Math.abs(turn) > 0.6f) {
                if (!turnLatch && snapCooldown <= 0f) {
                    yaw -= Math.signum(turn) * settings.snapAngle * MathUtil.DEG;
                    fade = Math.max(fade, 0.3f);
                    snapCooldown = 0.25f;
                    turnLatch = true;
                }
            } else turnLatch = false;
        } else {
            yaw -= settings.smoothTurnSpeed * MathUtil.DEG * turn * dt;
        }
    }

    private void updateMenu(float dt) {
        if (menu.center.distance(rig.head) > 3.5f) screens.place();
        if (calibStep >= 0 && menu.screen.equals("calib")) {
            updateCalibration();
            screens.live();
            headWorld.forward(t0);
            String c = menu.pointer(rig.head, t0, dt, true);
            if (c != null) { audio.play2D(Sfx.UI_CLICK, 0.6f); screens.click(c); }
            return;
        }
        if (menu.screen.equals("diag")) screens.live();
        Vec3 origin = t1, dir = t0;
        if (in.hasControllerPose) {
            ctrlWorld.forward(dir);
            origin.set(rig.hands[activeArm].shoulder);
        } else {
            headWorld.forward(dir);
            origin.set(rig.head);
        }
        Menu.Button before = menu.hovered;
        String clicked = menu.pointer(origin, dir, dt, !in.hasControllerPose);
        if (menu.hovered != before && menu.hovered != null) audio.play2D(Sfx.UI_HOVER, 0.4f);
        boolean click = (in.trigger && !prev.trigger) || (in.action && !prev.action);
        if (clicked == null && click && menu.hovered != null) clicked = menu.hovered.id;
        if (clicked != null) {
            audio.play2D(Sfx.UI_CLICK, 0.6f);
            screens.click(clicked);
        }
    }

    public void render(RenderQueue q) { scene.build(q); }

    public boolean isPlaying() { return state == State.PLAYING; }
}
