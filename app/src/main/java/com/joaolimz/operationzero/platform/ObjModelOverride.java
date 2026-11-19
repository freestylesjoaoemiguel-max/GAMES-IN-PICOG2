package com.joaolimz.operationzero.platform;

import android.content.res.AssetManager;

import com.joaolimz.operationzero.game.mesh.Assets;
import com.joaolimz.operationzero.game.mesh.MeshData;
import com.joaolimz.operationzero.game.mesh.ObjLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * Loads assets/models/NAME.obj (+ NAME.mtl) to replace a procedural model with a properly licensed
 * external low-poly model. Missing files simply keep the built-in model.
 */
public final class ObjModelOverride implements Assets.Override {
    private final AssetManager am;
    private final HashMap<String, Boolean> present = new HashMap<String, Boolean>();

    public ObjModelOverride(AssetManager am) {
        this.am = am;
        try {
            String[] list = am.list("models");
            if (list != null) for (String f : list) present.put(f, Boolean.TRUE);
        } catch (IOException ignored) {
            // no models folder
        }
    }

    @Override public MeshData load(String name) {
        if (!present.containsKey(name + ".obj")) return null;
        try {
            HashMap<String, float[]> mats = null;
            if (present.containsKey(name + ".mtl")) {
                InputStreamReader mr = new InputStreamReader(am.open("models/" + name + ".mtl"), "UTF-8");
                mats = ObjLoader.parseMtl(mr);
                mr.close();
            }
            InputStreamReader r = new InputStreamReader(am.open("models/" + name + ".obj"), "UTF-8");
            MeshData m = ObjLoader.parse(name, r, mats, 1f, 3000);
            r.close();
            return m.vertexCount > 0 ? m : null;
        } catch (IOException e) {
            return null;
        }
    }
}
