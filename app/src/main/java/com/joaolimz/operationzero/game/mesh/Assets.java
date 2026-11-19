package com.joaolimz.operationzero.game.mesh;

/**
 * Registers every runtime mesh and keeps their ids. Each model can be replaced at startup by an
 * external OBJ (see README "Modelos externos") through {@link Override}.
 */
public final class Assets {
    /** Optional hook that returns an imported mesh for a model name, or null to keep the procedural one. */
    public interface Override {
        MeshData load(String modelName);
    }

    public final MeshLibrary lib;
    public int[] hand = new int[6]; // [right? 0..2 : 3..5] by pose
    public int armUpper, armFore, playerTorso;
    public int pistol, pistolSlide, smg, shotgun, shotgunPump, rifle;
    public int magPistol, magSmg, magRifle, shell;
    public int crateSmall, crateLarge, crateBreakable, barrel, bottle, can, wrench, keycard, device, medkit;
    public int ammoPistol, ammoSmg, ammoShells, ammoRifle, debris, shard;
    public int doorWood, doorMetal, doorHandle, slabShutter, slabLab, slabGarage, buttonRed, buttonGreen, leverBase, leverArm;
    public int terminal, cardReader, target;
    public int[] npcHead = new int[4], npcTorso = new int[4], npcLimb = new int[4], npcLimbDark = new int[4], npcLod = new int[4];
    public int npcGun;
    public int flash, spark, tracer, blob, marker, vignette, sky, uiQuad, hitFlash, arcDot, laser;

    public static final int[] NPC_ARMOR = {0x46505C, 0x4E5638, 0x5A2E2E, 0x6E6450};
    public static final int[] NPC_VISOR = {0xFF8A1F, 0xFF8A1F, 0xFF3B2F, 0x7FD6FF};

    public Assets(MeshLibrary lib, Override override) {
        this.lib = lib;
        for (int p = 0; p < 3; p++) {
            hand[p] = reg(override, Models.hand(true, p));
            hand[3 + p] = reg(override, Models.hand(false, p));
        }
        armUpper = reg(override, Models.limb("arm_upper", 0x30363F, 0x3D4652));
        armFore = reg(override, Models.limb("arm_fore", 0x2B2F36, 0xE07A1F));
        playerTorso = reg(override, Models.playerTorso());
        pistol = reg(override, Models.pistol());
        pistolSlide = reg(override, Models.pistolSlide());
        smg = reg(override, Models.smg());
        shotgun = reg(override, Models.shotgun());
        shotgunPump = reg(override, Models.shotgunPump());
        rifle = reg(override, Models.rifle());
        magPistol = reg(override, Models.magazine("mag_pistol", 0.016f, 0.1f, 0x2A2D33));
        magSmg = reg(override, Models.magazine("mag_smg", 0.017f, 0.17f, 0x2A2D33));
        magRifle = reg(override, Models.magazine("mag_rifle", 0.022f, 0.15f, 0x3B3F33));
        shell = reg(override, Models.shell());
        crateSmall = reg(override, Models.crate("crate_small", 0.2f, 0.2f, 0.2f, 0x9C7A4D));
        crateLarge = reg(override, Models.crate("crate_large", 0.45f, 0.45f, 0.45f, 0x6F7A52));
        crateBreakable = reg(override, Models.crate("crate_breakable", 0.25f, 0.25f, 0.25f, 0xB08A55));
        barrel = reg(override, Models.barrel());
        bottle = reg(override, Models.bottle());
        can = reg(override, Models.can());
        wrench = reg(override, Models.wrench());
        keycard = reg(override, Models.keycard());
        device = reg(override, Models.device());
        medkit = reg(override, Models.medkit());
        ammoPistol = reg(override, Models.ammoBox(0xC9A13B));
        ammoSmg = reg(override, Models.ammoBox(0x2D6CDF));
        ammoShells = reg(override, Models.ammoBox(0xB8322B));
        ammoRifle = reg(override, Models.ammoBox(0x2FB36A));
        debris = reg(override, Models.debris());
        shard = reg(override, Models.glassShard());
        doorWood = reg(override, Models.doorPanel(0x7A5A3A));
        doorMetal = reg(override, Models.doorPanel(0x58606A));
        doorHandle = reg(override, Models.doorHandle());
        slabShutter = reg(override, Models.slab("shutter", 0x6A7078, 0xE0C21F));
        slabLab = reg(override, Models.slab("lab_door", 0xD8DCE0, 0x2D6CDF));
        slabGarage = reg(override, Models.slab("garage_door", 0x59606B, 0xE07A1F));
        buttonRed = reg(override, Models.button(0xC23B32));
        buttonGreen = reg(override, Models.button(0x2FB36A));
        leverBase = reg(override, Models.leverBase());
        leverArm = reg(override, Models.leverArm());
        terminal = reg(override, Models.terminal());
        cardReader = reg(override, Models.cardReader());
        target = reg(override, Models.target());
        for (int t = 0; t < 4; t++) {
            npcHead[t] = reg(override, Models.npcHead(NPC_ARMOR[t], NPC_VISOR[t]));
            npcTorso[t] = reg(override, Models.npcTorso(NPC_ARMOR[t], NPC_VISOR[t]));
            npcLimb[t] = reg(override, Models.limb("npc_limb_" + t, NPC_ARMOR[t], Models.darken(NPC_ARMOR[t], 0.8f)));
            npcLimbDark[t] = reg(override, Models.limb("npc_leg_" + t, Models.darken(NPC_ARMOR[t], 0.75f), Models.darken(NPC_ARMOR[t], 0.6f)));
            npcLod[t] = reg(override, Models.npcLowBody(NPC_ARMOR[t], NPC_VISOR[t]));
        }
        npcGun = reg(override, Models.npcGun());
        flash = reg(null, Models.flash());
        spark = reg(null, Models.spark());
        tracer = reg(null, Models.unitBox("tracer", 0xFFE6A0, 0.8f));
        blob = reg(null, Models.blobShadow());
        marker = reg(null, Models.marker());
        vignette = reg(null, Models.vignette());
        sky = reg(null, Models.skyDome());
        uiQuad = reg(null, Models.quad("ui_quad", 0xFFFFFF, 1f));
        hitFlash = reg(null, Models.unitBox("hit_flash", 0xFF3020, 0.5f));
        arcDot = reg(null, Models.unitBox("arc_dot", 0x4FD8FF, 0.9f));
        laser = reg(null, Models.unitBox("laser", 0xFFFFFF, 0.5f));
    }

    private int reg(Override override, MeshData procedural) {
        MeshData m = null;
        if (override != null) {
            try { m = override.load(procedural.name); } catch (RuntimeException ignored) { m = null; }
        }
        return lib.register(m != null ? m : procedural);
    }

    public int handMesh(boolean right, int pose) { return hand[(right ? 0 : 3) + pose]; }
}
