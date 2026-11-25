package com.joaolimz.operationzero.game.mesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

/**
 * Minimal Wavefront OBJ importer for replacing procedural models with external, properly licensed
 * low-poly assets (e.g. Kenney CC0 kits exported to OBJ). Supports v / vn / f (polygons are fan
 * triangulated), usemtl with colors from an MTL map (Kd). Textures are intentionally ignored: the
 * renderer uses vertex colors to stay cheap on the Pico G2.
 */
public final class ObjLoader {
    private ObjLoader() { }

    /** Parses an MTL file into material name -> rgb. */
    public static HashMap<String, float[]> parseMtl(Reader reader) throws IOException {
        HashMap<String, float[]> out = new HashMap<String, float[]>();
        BufferedReader br = new BufferedReader(reader);
        String line, current = null;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("newmtl ")) {
                current = line.substring(7).trim();
                out.put(current, new float[]{0.8f, 0.8f, 0.8f});
            } else if (line.startsWith("Kd ") && current != null) {
                String[] p = line.split("\\s+");
                if (p.length >= 4) out.put(current, new float[]{parse(p[1]), parse(p[2]), parse(p[3])});
            }
        }
        return out;
    }

    /**
     * @param scale uniform scale applied to positions (OBJ units -> meters)
     * @param maxTriangles safety cap for mobile; extra faces are skipped
     */
    public static MeshData parse(String name, Reader reader, HashMap<String, float[]> materials, float scale, int maxTriangles)
            throws IOException {
        FloatList pos = new FloatList(), nrm = new FloatList();
        MeshBuilder mb = new MeshBuilder();
        BufferedReader br = new BufferedReader(reader);
        String line;
        float[] color = {0.8f, 0.8f, 0.8f};
        int tris = 0;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.length() < 2 || line.charAt(0) == '#') continue;
            if (line.startsWith("v ")) {
                String[] p = line.split("\\s+");
                pos.add(parse(p[1]) * scale); pos.add(parse(p[2]) * scale); pos.add(parse(p[3]) * scale);
            } else if (line.startsWith("vn ")) {
                String[] p = line.split("\\s+");
                nrm.add(parse(p[1])); nrm.add(parse(p[2])); nrm.add(parse(p[3]));
            } else if (line.startsWith("usemtl ")) {
                float[] c = materials != null ? materials.get(line.substring(7).trim()) : null;
                color = c != null ? c : new float[]{0.8f, 0.8f, 0.8f};
            } else if (line.startsWith("f ")) {
                String[] p = line.split("\\s+");
                int n = p.length - 1;
                if (n < 3) continue;
                int[] vi = new int[n];
                for (int i = 0; i < n; i++) {
                    String tok = p[i + 1];
                    int slash = tok.indexOf('/');
                    int idx = Integer.parseInt(slash >= 0 ? tok.substring(0, slash) : tok);
                    vi[i] = idx < 0 ? pos.size / 3 + idx : idx - 1;
                }
                for (int i = 1; i + 1 < n; i++) {
                    if (tris++ >= maxTriangles) break;
                    int a = vi[0], b = vi[i], c = vi[i + 1];
                    mb.color(color[0], color[1], color[2]);
                    // degenerate quad = triangle (p2 == p3)
                    mb.quad(pos.get(a * 3), pos.get(a * 3 + 1), pos.get(a * 3 + 2),
                            pos.get(b * 3), pos.get(b * 3 + 1), pos.get(b * 3 + 2),
                            pos.get(c * 3), pos.get(c * 3 + 1), pos.get(c * 3 + 2),
                            pos.get(c * 3), pos.get(c * 3 + 1), pos.get(c * 3 + 2));
                }
            }
        }
        return mb.build(name);
    }

    private static float parse(String s) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return 0f; }
    }

    private static final class FloatList {
        float[] a = new float[1024];
        int size;
        void add(float v) {
            if (size == a.length) { float[] n = new float[a.length * 2]; System.arraycopy(a, 0, n, 0, size); a = n; }
            a[size++] = v;
        }
        float get(int i) { return i >= 0 && i < size ? a[i] : 0f; }
    }
}
