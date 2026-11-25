package com.joaolimz.operationzero.game;

import com.joaolimz.operationzero.game.ai.Npc;
import com.joaolimz.operationzero.game.ai.NpcSystem;
import com.joaolimz.operationzero.game.audio.AudioSystem;
import com.joaolimz.operationzero.game.audio.Sfx;
import com.joaolimz.operationzero.game.fx.Effects;
import com.joaolimz.operationzero.game.input.ControllerMapping;
import com.joaolimz.operationzero.game.input.InputFrame;
import com.joaolimz.operationzero.game.input.Platform;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Assets;
import com.joaolimz.operationzero.game.mesh.MeshLibrary;
import com.joaolimz.operationzero.game.mission.MissionSystem;
import com.joaolimz.operationzero.game.perf.PerfMonitor;
import com.joaolimz.operationzero.game.player.Hand;
import com.joaolimz.operationzero.game.player.Inventory;
import com.joaolimz.operationzero.game.player.Player;
import com.joaolimz.operationzero.game.save.SaveData;
import com.joaolimz.operationzero.game.save.Settings;
import com.joaolimz.operationzero.game.ui.Menu;
import com.joaolimz.operationzero.game.ui.Screens;
import com.joaolimz.operationzero.game.weapons.Weapon;
import com.joaolimz.operationzero.game.weapons.WeaponType;
import com.joaolimz.operationzero.game.world.Door;
import com.joaolimz.operationzero.game.world.Interactive;
import com.joaolimz.operationzero.game.world.ItemKind;
import com.joaolimz.operationzero.game.world.Level;
import com.joaolimz.operationzero.game.world.LevelBuilder;
import com.joaolimz.operationzero.game.world.Prop;
import com.joaolimz.operationzero.game.world.PropFactory;
import com.joaolimz.operationzero.game.world.SectorManager;
import com.joaolimz.operationzero.game.world.TriggerZone;

import java.util.HashSet;

/**
 * Top level game: state machine (menu, playing, paused, dead, victory), campaign scripting, save /
 * load, and orchestration of all subsystems. Completely platform independent (unit tested on the JVM).
 */
public final class Game implements NpcSystem.Listener {
    public enum State { MENU, PLAYING, PAUSED, DEAD, VICTORY }

    public final Settings settings;
    public final Platform platform;
    public final AudioSystem audio = new AudioSystem();
    public final MeshLibrary meshes = new MeshLibrary();
    public final Assets assets;
    public final Effects fx = new Effects();
    public final MissionSystem missions = new MissionSystem();
    public final Player player = new Player();
    public final Menu menu = new Menu();
    public final PerfMonitor perf = new PerfMonitor();
    public final Interaction interaction;
    public final SceneBuilder scene;
    public final Screens screens;

    public Level level;
    public PropFactory factory;
    public SectorManager sectors;
    public NpcSystem npcs;
    private int[] sectorMeshIds;

    public State state = State.MENU;
    public float playTime;
    public float time;
    public SaveData save;
    public final HashSet<String> flags = new HashSet<String>();
    private final HashSet<String> groupsQueued = new HashSet<String>();
    private boolean alarmWaveStarted;

    // input edges
    public final InputFrame in = new InputFrame();
    public final InputFrame prev = new InputFrame();
    public float switchHeld, actionHeld, menuHeld;
    private boolean switchConsumed, actionConsumed;
    /** Remapping: action index waiting for a key, or -1. */
    public int remapAction = -1;
    private float remapDelay;

    // ---- Pico G2 controller
    public final ControllerMapping ctrlMap = new ControllerMapping();
    private boolean ctrlAligned, calibOffered, ctrlWasConnected;
    private float realignTimer = -1f, homeHeld;
    /** Calibration wizard step (-1 = inactive). */
    public int calibStep = -1;
    public String calibMessage = "";
    private final float[][] calibRaw = new float[3][4];
    private float calibHeadYaw;
    private final float[][] calibTouch = new float[3][2];
    private String calibReturn = "main";

    public float fade;
    public float vignette;
    public float hintTimer;
    public String hint;
    private final Vec3 t0 = new Vec3();

    public Game(Settings settings, Platform platform, Assets.Override modelOverride) {
        this.settings = settings;
        this.platform = platform;
        this.assets = new Assets(meshes, modelOverride);
        this.interaction = new Interaction(this);
        this.scene = new SceneBuilder(this);
        this.screens = new Screens(this);
        this.save = SaveData.parse(platform.readFile(SaveData.FILE));
        loadMappingFromSettings();
        loadLevel(0);
        applySettings();
        screens.open("main");
    }

    // ===================================================================== settings / persistence
    public void applySettings() {
        if (level != null) level.physics.maxActiveBodies = settings.maxBodies();
        if (npcs != null) {
            npcs.maxActive = settings.maxNpcs();
            npcs.maxRagdolls = settings.maxRagdolls();
            npcs.corpseTime = settings.corpseTime();
        }
        fx.setLimit(settings.maxParticles());
        audio.master = settings.master;
        audio.sfxVolume = settings.sfx;
        audio.ambientVolume = settings.ambient;
        audio.refreshVolumes();
        player.height = settings.playerHeight;
        player.active = settings.rightHanded ? 1 : 0;
    }

    public void saveSettings() { platform.writeFile(Settings.FILE, settings.serialize()); }

    private void loadMappingFromSettings() {
        System.arraycopy(settings.ctrlPw, 0, ctrlMap.pw, 0, 9);
        System.arraycopy(settings.ctrlPb, 0, ctrlMap.pb, 0, 9);
        ctrlMap.inverse = settings.ctrlInverse;
    }

    /** Recenters head tracking and re-aligns the controller with the head a moment later. */
    public void recenterAll() {
        platform.recenter();
        realignTimer = 0.15f;
        showHint("Visão e controle recentralizados");
    }

    /**
     * Converts the raw Pico controller data into game input: orientation (aim), trigger/click/app buttons,
     * touchpad to walk (up/down) and turn (left/right), Home long press to recenter.
     */
    private void processController(float dt) {
        if (!in.ctrlConnected) {
            ctrlWasConnected = false;
            if (settings.aimSource == 1) in.hasControllerPose = false;
            return;
        }
        if (!ctrlWasConnected) {
            ctrlWasConnected = true;
            ctrlAligned = false;
            if (!settings.ctrlCalibrated && !calibOffered && state == State.MENU) {
                calibOffered = true;
                startCalibration();
            } else if (!settings.ctrlCalibrated) {
                showHint("Controle conectado. Calibre em CONTROLES > CALIBRAR CONTROLE");
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
        in.hasControllerPose = settings.aimSource == 0;
        // buttons (OR with the key events the system may also send)
        in.trigger |= in.ctrlTrigger;
        in.action |= in.ctrlClick;
        in.switchHand |= in.ctrlApp;
        // Home long press = recenter
        if (in.ctrlHome) {
            homeHeld += dt;
            if (homeHeld > 0.9f && homeHeld - dt <= 0.9f) recenterAll();
        } else {
            homeHeld = 0f;
        }
        // touchpad: thumb on the pad (without clicking) walks / turns
        if (in.ctrlTouching && !in.ctrlClick && calibStep < 0) {
            float dx = in.ctrlTouchX - settings.touchCX, dy = in.ctrlTouchY - settings.touchCY;
            float fx = settings.touchFX - settings.touchCX, fy = settings.touchFY - settings.touchCY;
            float rx = settings.touchRX - settings.touchCX, ry = settings.touchRY - settings.touchCY;
            float fl = Math.max(1f, fx * fx + fy * fy), rl = Math.max(1f, rx * rx + ry * ry);
            float fwd = MathUtil.clamp((dx * fx + dy * fy) / fl, -1f, 1f);
            float rt = MathUtil.clamp((dx * rx + dy * ry) / rl, -1f, 1f);
            if (Math.abs(rt) > 0.55f && Math.abs(rt) > Math.abs(fwd) * 1.2f) {
                in.turnX = Math.signum(rt) * Math.max(0.65f, Math.abs(rt));
            } else if (Math.abs(fwd) > 0.22f) {
                float k = (Math.abs(fwd) - 0.22f) / 0.78f;
                in.moveY = Math.signum(fwd) * Math.min(1f, k * 1.3f);
            }
        }
    }

    // ---------------------------------------------------------------- calibration wizard
    public void startCalibration() {
        calibReturn = state == State.PAUSED ? "pause" : "main";
        calibStep = 0;
        calibMessage = "";
        screens.open("calib");
    }

    public void cancelCalibration() {
        calibStep = -1;
        screens.open(calibReturn);
    }

    public static final String[] CALIB_TEXT = {
            "1/6  Aponte o controle para FRENTE (para onde você olha) e aperte o GATILHO",
            "2/6  Aponte o controle para CIMA (para o teto) e aperte o GATILHO",
            "3/6  Aponte o controle para a sua DIREITA e aperte o GATILHO",
            "4/6  Encoste o polegar no CENTRO do touchpad (sem clicar) e aperte o GATILHO",
            "5/6  Encoste o polegar na parte de CIMA do touchpad e aperte o GATILHO",
            "6/6  Encoste o polegar na parte DIREITA do touchpad e aperte o GATILHO"
    };

    private void updateCalibration() {
        boolean press = in.ctrlTrigger && !prev.ctrlTrigger;
        if (!in.ctrlConnected) {
            calibMessage = "Controle não conectado: " + in.ctrlStatus;
            return;
        }
        if (!press) return;
        audio.play2D(Sfx.UI_CLICK, 0.6f);
        if (calibStep <= 2) {
            float[] r = calibRaw[calibStep];
            r[0] = in.ctrlX; r[1] = in.ctrlY; r[2] = in.ctrlZ; r[3] = in.ctrlW;
            if (calibStep == 0) calibHeadYaw = in.head.yaw();
            if (calibStep == 2) {
                float err = ctrlMap.solve(calibRaw[0], calibRaw[1], calibRaw[2]);
                if (err > 1.4f) {
                    loadMappingFromSettings();
                    calibStep = 0;
                    calibMessage = "Não reconheci os movimentos (erro " + String.format("%.2f", err) + "). Vamos repetir.";
                    return;
                }
                ctrlMap.alignYaw(calibRaw[0][0], calibRaw[0][1], calibRaw[0][2], calibRaw[0][3], calibHeadYaw);
                ctrlAligned = true;
                System.arraycopy(ctrlMap.pw, 0, settings.ctrlPw, 0, 9);
                System.arraycopy(ctrlMap.pb, 0, settings.ctrlPb, 0, 9);
                settings.ctrlInverse = ctrlMap.inverse;
                settings.ctrlCalibrated = true;
                saveSettings();
                calibMessage = "Orientação calibrada! Agora o touchpad.";
            }
            calibStep++;
            return;
        }
        if (!in.ctrlTouching) {
            calibMessage = "Encoste o polegar no touchpad (sem clicar) enquanto aperta o gatilho.";
            return;
        }
        float[] t = calibTouch[calibStep - 3];
        t[0] = in.ctrlTouchX; t[1] = in.ctrlTouchY;
        calibStep++;
        calibMessage = "";
        if (calibStep == 6) {
            float cx = calibTouch[0][0], cy = calibTouch[0][1];
            float fx = calibTouch[1][0], fy = calibTouch[1][1];
            float rx = calibTouch[2][0], ry = calibTouch[2][1];
            boolean ok = (fx - cx) * (fx - cx) + (fy - cy) * (fy - cy) > 100f && (rx - cx) * (rx - cx) + (ry - cy) * (ry - cy) > 100f;
            if (ok) {
                settings.touchCX = cx; settings.touchCY = cy;
                settings.touchFX = fx; settings.touchFY = fy;
                settings.touchRX = rx; settings.touchRY = ry;
                saveSettings();
                showHint("Controle calibrado! Polegar em cima do touchpad = andar.");
                calibStep = -1;
                screens.open(calibReturn);
            } else {
                calibStep = 3;
                calibMessage = "Os toques ficaram muito próximos. Repita o touchpad.";
            }
        }
    }

    // ===================================================================== level lifecycle
    /** Builds a fresh copy of the facility and prepares it for the given mission index. */
    public void loadLevel(int mission) {
        level = new Level();
        LevelBuilder b = new LevelBuilder(level, meshes, assets, sectorMeshIds);
        b.build();
        sectorMeshIds = b.sectorMeshIds();
        factory = b.factory();
        sectors = new SectorManager(level.sectors, meshes);
        npcs = new NpcSystem(level);
        npcs.setListener(this);
        fx.clear();
        flags.clear();
        groupsQueued.clear();
        alarmWaveStarted = false;
        audio.setAlarm(false);
        missions.reset(mission);
        int cp = Math.min(mission, level.checkpoints.length - 1);
        player.spawn(level.checkpoints[cp], level.checkpointYaw[cp]);
        player.updateHead(in.head, 0f);
        player.inventory.clear();
        player.kills = 0;
        applySettings();
        applyProgress(mission);
        sectors.update(player.feet.x, player.feet.y + 0.5f, player.feet.z, level.physics);
        npcs.playerFeet.set(player.feet);
    }

    /** Re-creates the world state implied by having completed the missions before {@code mission}. */
    private void applyProgress(int mission) {
        if (mission >= 1) {
            openDoor("door_shutter");
            setLever("lever_power");
            flags.add("power_on");
            groupsQueued.add("ext");
        }
        if (mission >= 2) {
            Prop card = findProp(ItemKind.KEYCARD);
            if (card != null) store(card, Inventory.CHEST_L);
        }
        if (mission >= 3) {
            openDoor("door_corridor");
            openDoor("door_lab");
            Interactive t = level.interactive("terminal_security");
            if (t != null) { t.on = true; t.enabled = false; }
            groupsQueued.add("admin");
        }
        if (mission >= 4) {
            openDoor("door_secret");
            setLever("lever_secret");
            Prop dev = findProp(ItemKind.DEVICE);
            if (dev != null) { dev.pickedOnce = true; store(dev, Inventory.CHEST_R); }
            groupsQueued.add("core"); groupsQueued.add("lab"); groupsQueued.add("under"); groupsQueued.add("secret");
        }
        if (mission == 4) startAlarmWave();
        if (mission >= 5) {
            unlockEscape();
        }
        if (save != null && mission > 0 && mission == save.mission) {
            // restore inventory from the checkpoint
            for (int i = 0; i < 4; i++) player.inventory.reserve[i] = save.reserve[i];
            for (int s = 0; s < 2; s++) {
                WeaponType t = WeaponType.get(save.slotWeapon[s]);
                if (t == null) continue;
                Weapon w = factory.weapon(t, 0, player.feet.x, player.feet.y + 1, player.feet.z,
                        save.slotRounds[s], save.slotChambered[s]);
                store(w, s == 0 ? Inventory.HIP : Inventory.BACK);
            }
            player.health = Math.max(40, save.health);
            playTime = save.playTime;
            player.kills = save.kills;
        } else if (mission > 0) {
            // replaying a mission without a matching save: give a standard loadout
            Weapon w = factory.weapon(WeaponType.VX9, 0, player.feet.x, player.feet.y + 1, player.feet.z, 12, true);
            store(w, Inventory.HIP);
            player.inventory.reserve[WeaponType.AMMO_9MM] = 48;
            if (mission >= 3) {
                Weapon smg = factory.weapon(WeaponType.RAPTOR, 0, player.feet.x, player.feet.y + 1, player.feet.z, 30, true);
                store(smg, Inventory.BACK);
                player.inventory.reserve[WeaponType.AMMO_SMG] = 60;
            }
        }
    }

    private void openDoor(String id) {
        Door d = level.door(id);
        if (d != null) d.forceOpen();
    }

    private void setLever(String id) {
        Interactive it = level.interactive(id);
        if (it != null) { it.on = true; it.leverAngle = 0.7f; it.enabled = false; }
    }

    private Prop findProp(int kind) {
        for (int i = 0; i < level.props.size(); i++) if (level.props.get(i).kind == kind && level.props.get(i).active) return level.props.get(i);
        return null;
    }

    /** Puts a prop into an inventory slot (no checks). */
    public void store(Prop p, int slot) {
        p.slot = slot;
        p.heldBy = null;
        p.body.kinematic = true;
        p.body.enabled = true;
        player.inventory.items[slot] = p;
        p.pickedOnce = true;
    }

    public void newGame() {
        save = null;
        platform.deleteFile(SaveData.FILE);
        playTime = 0f;
        loadLevel(0);
        startPlaying();
    }

    public void continueGame() {
        save = SaveData.parse(platform.readFile(SaveData.FILE));
        int m = save != null ? Math.min(save.mission, MissionSystem.COUNT - 1) : 0;
        loadLevel(m);
        startPlaying();
    }

    public void startMission(int m) {
        if (save == null || save.mission != m) {
            // keep the stored save intact, just replay from that mission with a standard loadout
            SaveData keep = save;
            save = null;
            loadLevel(m);
            save = keep;
        } else {
            loadLevel(m);
        }
        startPlaying();
    }

    public void restartCheckpoint() {
        int m = save != null ? Math.min(save.mission, MissionSystem.COUNT - 1) : 0;
        loadLevel(m);
        startPlaying();
    }

    private void startPlaying() {
        state = State.PLAYING;
        fade = 1f;
        audio.setAmbient(sectors.currentSector().ambientSound);
        player.hands[0].resetTracking();
        player.hands[1].resetTracking();
    }

    public void pause() {
        if (state != State.PLAYING) return;
        state = State.PAUSED;
        screens.open("pause");
    }

    public void resume() {
        if (state == State.PAUSED) state = State.PLAYING;
    }

    public void toMainMenu() {
        state = State.MENU;
        audio.setAlarm(false);
        save = SaveData.parse(platform.readFile(SaveData.FILE));
        screens.open("main");
    }

    /** Writes the checkpoint (called on mission completion). */
    public void writeCheckpoint() {
        SaveData d = new SaveData();
        d.mission = missions.current;
        d.health = (int) player.health;
        System.arraycopy(player.inventory.reserve, 0, d.reserve, 0, 4);
        d.completedMask = missions.completedMask | (save != null ? save.completedMask : 0);
        d.playTime = playTime;
        d.kills = player.kills;
        // weapons: holstered first, then held ones into free slots
        Weapon hip = null, back = null;
        Prop ph = player.inventory.items[Inventory.HIP], pb = player.inventory.items[Inventory.BACK];
        if (ph instanceof Weapon) hip = (Weapon) ph;
        if (pb instanceof Weapon) back = (Weapon) pb;
        for (int h = 0; h < 2; h++) {
            Prop held = player.hands[h].held;
            if (held instanceof Weapon) {
                Weapon w = (Weapon) held;
                boolean small = w.type == WeaponType.VX9 || w.type == WeaponType.RAPTOR;
                if (hip == null && small) hip = w; else if (back == null) back = w; else if (hip == null && small) hip = w;
            }
        }
        Weapon[] ws = {hip, back};
        for (int s = 0; s < 2; s++) {
            Weapon w = ws[s];
            if (w == null) continue;
            d.slotWeapon[s] = w.type.id;
            d.slotRounds[s] = w.roundsInMagazine();
            d.slotChambered[s] = w.chambered;
            if (w.type.usesMagazine() && w.mag == null) d.slotRounds[s] = -1;
        }
        d.hasKeycard = player.inventory.hasItemKind(ItemKind.KEYCARD) || heldKind(ItemKind.KEYCARD);
        d.hasDevice = player.inventory.hasItemKind(ItemKind.DEVICE) || heldKind(ItemKind.DEVICE);
        save = d;
        platform.writeFile(SaveData.FILE, d.serialize());
    }

    private boolean heldKind(int kind) {
        for (int h = 0; h < 2; h++) if (player.hands[h].held != null && player.hands[h].held.kind == kind) return true;
        return false;
    }

    // ===================================================================== scripting
    public void onEvent(String e) {
        if (e == null) return;
        if (missions.onEvent(e)) {
            audio.play2D(Sfx.OBJECTIVE, 0.8f);
            onMissionStarted(missions.current);
            if (missions.finished()) {
                writeCheckpoint();
                victory();
                return;
            }
            writeCheckpoint();
        }
        if (e.equals("power_on")) {
            flags.add("power_on");
            Door d = level.door("door_shutter");
            d.locked = false;
            d.openDoor();
            audio.play(Sfx.DOOR_SLIDE, t0.set(0, 1.5f, 0), 1f, 0.8f);
            showHint("Energia restaurada. O portão está abrindo.");
        } else if (e.equals("unlock_corridor")) {
            Door d = level.door("door_corridor");
            d.locked = false;
            d.openDoor();
            showHint("Corredor liberado.");
        } else if (e.equals("lab_card")) {
            Door d = level.door("door_lab");
            d.locked = false;
            d.openDoor();
            audio.play2D(Sfx.DOOR_SLIDE, 0.8f);
            onEvent("lab_door_opened");
        } else if (e.equals("lab_terminal")) {
            showHint("Registro: o dispositivo está no laboratório secreto do subsolo.");
        } else if (e.equals("secret_power")) {
            Door d = level.door("door_secret");
            d.locked = false;
            d.openDoor();
            showHint("Energia do laboratório secreto restaurada.");
        } else if (e.equals("garage_open")) {
            Door d = level.door("door_garage_out");
            d.locked = false;
            d.openDoor();
            showHint("Portão abrindo. Vá para o ponto de extração!");
        } else if (e.equals("enter_garage")) {
            if (missions.current == 5 && !groupsQueued.contains("garage")) {
                groupsQueued.add("garage");
                npcs.queue("garage", Npc.SOLDIER, "garage", "garage");
                npcs.queue("garage", Npc.GUARD, "garage", "garage");
                npcs.queue("garage", Npc.MARKSMAN, "garage", "garage");
            }
        }
    }

    private void onMissionStarted(int m) {
        if (m == 4) startAlarmWave();
        if (m == 5) unlockEscape();
    }

    private void startAlarmWave() {
        if (alarmWaveStarted) return;
        alarmWaveStarted = true;
        flags.add("alarm");
        audio.setAlarm(true);
        showHint("ALARME! Reforços a caminho.");
        // every enemy already alive in the underground joins the hunt and counts for the objective
        npcs.huntPlayer("alarm", player.feet);
        int[] types = {Npc.SOLDIER, Npc.GUARD, Npc.SOLDIER, Npc.HEAVY, Npc.GUARD, Npc.MARKSMAN};
        for (int t : types) npcs.queue("alarm", t, "under", "alarm");
    }

    private void unlockEscape() {
        flags.remove("alarm");
        audio.setAlarm(false);
        Door tunnel = level.door("door_tunnel");
        if (tunnel != null) { tunnel.locked = false; tunnel.openDoor(); }
        Door gin = level.door("door_garage_in");
        if (gin != null) gin.locked = false;
        Interactive btn = level.interactive("btn_garage");
        if (btn != null) btn.enabled = true;
        Door gout = level.door("door_garage_out");
        if (gout != null) gout.lockedHint = "Aperte o botão verde do portão";
    }

    private void victory() {
        state = State.VICTORY;
        audio.setAlarm(false);
        audio.play2D(Sfx.VICTORY, 1f);
        screens.open("victory");
    }

    public void showHint(String text) {
        hint = text;
        hintTimer = 3.5f;
    }

    /** Populates sectors with their enemy groups the first time they become active. */
    private void populate() {
        for (int i = 0; i < level.sectors.size(); i++) {
            if (!level.sectors.get(i).active) continue;
            switch (i) {
                case LevelBuilder.S_ADMIN:
                    group("admin", "admin", Npc.GUARD, Npc.GUARD, Npc.SOLDIER);
                    break;
                case LevelBuilder.S_CORE:
                    group("core", "core", Npc.SOLDIER, Npc.GUARD, Npc.SOLDIER, Npc.MARKSMAN);
                    break;
                case LevelBuilder.S_LAB:
                    group("lab", "lab", Npc.GUARD, Npc.SOLDIER, Npc.GUARD);
                    break;
                case LevelBuilder.S_UNDER:
                    group("under", "under", Npc.SOLDIER, Npc.SOLDIER);
                    if (missions.current >= 3) group("secret", "secret", Npc.HEAVY, Npc.SOLDIER);
                    break;
                default:
                    break;
            }
        }
        // exterior guards appear once the player is armed or leaves the start area
        if (!groupsQueued.contains("ext") && (flags.contains("armed") || player.feet.z < 20f)) {
            group("ext", "ext", Npc.GUARD, Npc.GUARD);
        }
    }

    private void group(String name, String patrol, int... types) {
        if (groupsQueued.contains(name)) return;
        groupsQueued.add(name);
        for (int t : types) npcs.queue(name, t, patrol, name);
    }

    // ===================================================================== NPC callbacks
    @Override public void onNpcShoot(Npc n, Vec3 muzzle, Vec3 impact, boolean hitPlayer, float damage) {
        fx.flash(muzzle, player.head, 0.8f);
        fx.tracer(muzzle, impact, 1f, 0.75f, 0.4f);
        audio.play(Sfx.NPC_SHOT, muzzle, 0.9f, 1f, 45f);
        if (hitPlayer) {
            player.damage(damage, muzzle);
            audio.play2D(Sfx.PLAYER_HURT, 0.8f);
            platform.vibrate(60, 0.6f);
        } else {
            fx.sparks(impact, t0.set(muzzle).sub(impact).normalize(), 3, 1f, 0.8f, 0.4f);
            if (impact.distance(player.eye) < 2.5f) audio.play(Sfx.WHIZ, impact, 0.8f, 1f);
        }
    }

    @Override public void onNpcKilled(Npc n, Vec3 at) {
        player.kills++;
        audio.play(Sfx.NPC_DEATH, at, 0.9f, 0.9f);
        // drop ammo matching its weapon
        int ammo = n.type == Npc.GUARD ? WeaponType.AMMO_9MM : n.type == Npc.SOLDIER ? WeaponType.AMMO_SMG
                : n.type == Npc.HEAVY ? WeaponType.AMMO_SHELL : WeaponType.AMMO_RIFLE;
        int amount = ammo == WeaponType.AMMO_SHELL ? 4 : ammo == WeaponType.AMMO_RIFLE ? 8 : 15;
        int mesh = ammo == WeaponType.AMMO_9MM ? assets.ammoPistol : ammo == WeaponType.AMMO_SMG ? assets.ammoSmg
                : ammo == WeaponType.AMMO_SHELL ? assets.ammoShells : assets.ammoRifle;
        Prop p = factory.prop(ItemKind.AMMO_BOX, mesh, 0.09f, 0.055f, 0.05f, 1f, n.sector, at.x, at.y + 0.9f, at.z);
        p.value = ammo;
        p.value2 = amount;
        p.name = "Municao " + WeaponType.AMMO_NAMES[ammo];
        p.body.sleeping = false;
        p.body.vel.set(0, 1.5f, 0);
    }

    @Override public void onNpcAlert(Npc n) { audio.play(Sfx.NPC_ALERT, n.pos, 0.9f, 1f); }

    @Override public void onNpcHurt(Npc n) { audio.play(Sfx.NPC_PAIN, n.pos, 0.7f, 1f + (n.type == Npc.HEAVY ? -0.2f : 0.1f)); }

    // ===================================================================== update
    /** Main tick. {@code input} is the latest platform input snapshot. */
    public void update(float dt, InputFrame input) {
        dt = MathUtil.clamp(dt, 0f, 0.05f);
        time += dt;
        prev.copyFrom(in);
        in.copyFrom(input);
        processController(dt);
        if (in.recenter && !prev.recenter) recenterAll();
        player.updateHead(in.head, settings.cameraRecoil ? player.cameraPunch : 0f);
        if (player.cameraPunch > 0f) player.cameraPunch -= player.cameraPunch * MathUtil.damp(12f, dt);
        audio.setListener(player.eye, player.head);
        if (hintTimer > 0f) hintTimer -= dt;
        if (fade > 0f) fade = Math.max(0f, fade - dt * 2.5f);

        // long press tracking for switch/menu/action
        trackButtons(dt);

        if (state == State.PLAYING) {
            updatePlaying(dt);
        } else {
            updateMenu(dt);
            // keep the world alive behind the pause/death panels (no simulation in main menu)
            if (state == State.DEAD) {
                fx.update(dt);
            }
        }
        perf.activeBodies = level.physics.activeBodyCount;
        perf.activeNpcs = npcs.activeCount;
        perf.aliveNpcs = npcs.aliveCount;
        perf.activeSectors = sectors.activeCount;
        perf.effects = fx.activeCount;
        perf.ragdolls = npcs.activeRagdolls();
        perf.props = level.props.size();
    }

    private void trackButtons(float dt) {
        if (in.switchHand) {
            switchHeld += dt;
            if (switchHeld > 0.8f && !switchConsumed) {
                switchConsumed = true;
                if (state == State.PLAYING) pause();
            }
        } else {
            if (prev.switchHand && !switchConsumed && state == State.PLAYING) interaction.switchHands();
            switchHeld = 0f;
            switchConsumed = false;
        }
        if (in.menu && !prev.menu) {
            if (state == State.PLAYING) pause();
            else if (state == State.PAUSED && menu.screen.equals("pause")) resume();
        }
        if (in.action) {
            actionHeld += dt;
            if (actionHeld > 0.7f && !actionConsumed && state == State.PLAYING) {
                actionConsumed = true;
                interaction.dropHeld(player.activeHand());
            }
        } else {
            if (prev.action && !actionConsumed && state == State.PLAYING) interaction.contextAction();
            actionHeld = 0f;
            actionConsumed = false;
        }
    }

    private void updatePlaying(float dt) {
        playTime += dt;
        interaction.update(dt);
        level.physics.step(dt, player.feet);
        interaction.postPhysics(dt);
        // doors
        Hand ah = player.activeHand();
        for (int i = 0; i < level.doors.size(); i++) {
            Door d = level.doors.get(i);
            if (!sectors.isActive(d.sector)) continue;
            Vec3 hp = d.grabbedBy != null ? ((Hand) d.grabbedBy).pos : null;
            boolean wasOpen = d.openedOnce;
            if (d.update(dt, hp)) audio.play(Sfx.DOOR_OPEN, t0.set(d.hinge.centerX(), d.hinge.y0 + 1f, d.hinge.centerZ()), 0.6f, 1f);
            if (!wasOpen && d.openedOnce && d.openEvent != null) onEvent(d.openEvent);
        }
        // triggers
        for (int i = 0; i < level.triggers.size(); i++) {
            TriggerZone z = level.triggers.get(i);
            if (!z.enabled) continue;
            boolean inside = z.box.contains(player.feet.x, player.feet.y + 0.5f, player.feet.z);
            if (inside && !z.inside) {
                if (z.once) z.enabled = false;
                onEvent(z.event);
            }
            z.inside = inside;
        }
        // sectors & streaming
        if (sectors.update(player.feet.x, player.feet.y + 0.5f, player.feet.z, level.physics)) {
            audio.setAmbient(sectors.currentSector().ambientSound);
        }
        populate();
        // AI
        npcs.playerEye.set(player.eye);
        npcs.playerFeet.set(player.feet);
        player.head.forward(npcs.playerForward);
        npcs.playerSpeed = player.moveSpeedNow;
        npcs.playerAlive = player.alive;
        npcs.update(dt);
        // alarm wave completion
        if (missions.current == 4 && alarmWaveStarted && npcs.alive("alarm") == 0 && npcs.pending("alarm") == 0) {
            onEvent("alarm_cleared");
        }
        fx.update(dt);
        missions.update(dt);
        if (player.hurtTimer > 0f) player.hurtTimer -= dt;
        if (!player.alive) {
            state = State.DEAD;
            audio.setAlarm(false);
            interaction.releaseAll();
            screens.open("dead");
        }
    }

    private void updateMenu(float dt) {
        Hand h = player.activeHand();
        if (in.hasControllerPose) player.driveHand(h, true, in.controller, dt, settings.sensitivity);
        Vec3 origin, dir = t0;
        if (in.hasControllerPose) {
            origin = h.pos;
            h.rot.forward(dir);
        } else {
            origin = player.eye;
            player.head.forward(dir);
        }
        if (calibStep >= 0 && menu.screen.equals("calib")) {
            updateCalibration();
            screens.live();
            // the buttons of the wizard are selected with the head (the controller is not calibrated yet)
            origin = player.eye;
            player.head.forward(dir);
            String c = menu.pointer(origin, dir, dt, true);
            if (c != null) { audio.play2D(Sfx.UI_CLICK, 0.6f); screens.click(c); }
            return;
        }
        if (menu.screen.equals("diag")) screens.live();
        if (remapAction >= 0) {
            remapDelay -= dt;
            if (remapDelay <= 0f && in.lastKeyCode != 0) {
                settings.keys[remapAction] = new int[]{in.lastKeyCode};
                saveSettings();
                showHint("Tecla " + in.lastKeyCode + " atribuída a " + Settings.ACTION_NAMES[remapAction]);
                remapAction = -1;
                screens.open("controls");
            }
            return;
        }
        if (menu.center.distance(player.eye) > 3f) screens.place();
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

    public void beginRemap(int action) {
        remapAction = action;
        remapDelay = 0.4f;
    }

    /** Build the render queue for this frame (head pose, scene, UI). */
    public void render(RenderQueue q) {
        scene.build(q);
    }

    public boolean isPlaying() { return state == State.PLAYING; }
}
