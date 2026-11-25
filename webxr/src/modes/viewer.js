import * as THREE from 'three';
import { listEnvironments } from '../apk.js';
import { parseScene, readMesh, worldMatrices } from '../scene.js';
import { parseKtx, limitMips } from '../ktx.js';

const NICE = { bubbles: 'Bolhas', cybercity: 'Cidade Cyber', desert: 'Deserto', dome: 'Domo', ryokan: 'Ryokan (Japão)', spacestation: 'Estação Espacial', winterlodge: 'Cabana de Inverno' };
const ALPHA_CUT = /leaves|leaf|plants|fence|bamboo|lilypad|balloons|fish/i;
const ADDITIVE = /glow|fog|mote|firefly|light\.ktx|stars/i;

/** Unidade do cenário: cenas com raio enorme foram exportadas em centímetros. */
export function sceneUnitScale(radius) { return radius > 5000 ? 0.01 : 1; }

export class ViewerMode {
  constructor(app) { this.app = app; this.group = new THREE.Group(); this.envs = []; this.idx = 0; this.loading = false; this.fly = false; this.userScale = 1; this.heightAdj = 0; this.info = null; this.pausable = false; this.texCache = new Map(); this.disposables = []; }

  get ready() { return !!this.app.apk; }
  envName(e) { return NICE[e.name] || e.name; }

  async enter() {
    const app = this.app;
    app.scene.add(this.group);
    app.scene.background = new THREE.Color(0x05070d);
    this.envs = this.app.apk ? listEnvironments(this.app.apk) : [];
    if (!this.envs.length) { this.showNeedApk(); return; }
    this.idx = Math.min(this.idx, this.envs.length - 1);
    await this.load(this.idx);
  }
  exit() { this.clear(); this.app.scene.remove(this.group); this.app.sound.stopAmbient(); }
  onSessionStart() { if (this.info) this.placeUser(); }

  showNeedApk() {
    this.app.ui.open(() => ({ title: 'Ambientes', lines: ['Nenhum APK carregado.', 'Saia do VR, escolha o arquivo Metulas.apk', 'na página e entre em VR de novo.'], buttons: [{ label: 'Menu', cb: () => this.app.openMenu(), wide: true }] }));
  }

  clear() {
    for (const o of this.disposables) { try { o.dispose(); } catch (e) { /* ok */ } }
    this.disposables = []; this.texCache.clear();
    while (this.group.children.length) this.group.remove(this.group.children[0]);
    this.info = null;
  }

  panel(extra = []) {
    const app = this.app, e = this.envs[this.idx];
    return () => ({
      title: e ? this.envName(e) : 'Ambientes',
      lines: this.loading ? [{ t: this.loadText || 'Carregando…', color: '#ffd166' }] : this.info
        ? [`${this.info.tris.toLocaleString('pt-BR')} triângulos · ${this.info.draws} objetos · escala ${(this.info.unit * this.userScale).toFixed(3)}`,
          `Texturas: máx ${app.settings.tex}px${this.info.noAstc ? ' (GPU sem ASTC: só cores)' : ''} · FPS ${app.fps.toFixed(0)}`, ...extra]
        : ['—'],
      buttons: [
        { label: '◀ Anterior', cb: () => this.step(-1), disabled: this.loading },
        { label: 'Próximo ▶', cb: () => this.step(1), disabled: this.loading },
        { label: this.fly ? 'Voo: LIGADO' : 'Voo: desligado', cb: () => { this.fly = !this.fly; }, active: this.fly },
        { label: 'Recentrar', cb: () => this.placeUser() },
        { label: 'Altura +10cm', cb: () => { this.heightAdj += 0.1; this.placeUser(); } },
        { label: 'Altura −10cm', cb: () => { this.heightAdj -= 0.1; this.placeUser(); } },
        { label: 'Escala +', cb: () => this.rescale(1.25) },
        { label: 'Escala −', cb: () => this.rescale(0.8) },
        { label: 'Velocidade +', cb: () => { app.settings.speed = Math.min(40, app.settings.speed * 1.4); app.saveSettings(); } },
        { label: 'Velocidade −', cb: () => { app.settings.speed = Math.max(0.5, app.settings.speed / 1.4); app.saveSettings(); } },
        { label: 'Menu principal', cb: () => app.openMenu(), wide: true },
      ],
    });
  }

  async step(d) { if (this.loading || !this.envs.length) return; this.idx = (this.idx + d + this.envs.length) % this.envs.length; await this.load(this.idx); }

  rescale(k) { this.userScale = Math.min(50, Math.max(0.02, this.userScale * k)); this.applyScale(); this.placeUser(); }
  applyScale() { if (this.info) this.group.scale.setScalar(this.info.unit * this.userScale); }

  async load(i) {
    const app = this.app, e = this.envs[i], base = `assets/env/${e.name}/`;
    this.loading = true; this.loadText = 'Lendo cena…';
    app.ui.open(this.panel(), 0.5);
    this.clear(); app.sound.stopAmbient();
    await new Promise((r) => setTimeout(r, 30));
    try {
      const scene = parseScene(await app.apk.arrayBuffer(base + 'scene.bin'));
      const unit = sceneUnitScale(scene.radius);
      // texturas realmente usadas
      const usedMat = new Set(scene.nodes.filter((n) => n.mesh >= 0 && scene.meshes[n.mesh]).map((n) => scene.meshes[n.mesh].material));
      const usedTex = new Set([...usedMat].map((m) => (scene.materials[m] ? scene.materials[m].texture : -1)).filter((t) => t >= 0));
      const noAstc = !app.renderer.extensions.has('WEBGL_compressed_texture_astc') && !(globalThis.__QA_TEXTURES);
      const textures = new Map(); let n = 0;
      for (const t of usedTex) {
        const name = scene.textureNames[t];
        this.loadText = `Texturas ${++n}/${usedTex.size}: ${name.slice(0, 32)}`; app.ui.panel.set(this.panel()());
        await new Promise((r) => setTimeout(r, 0));
        if (!this.texCache.has(name)) this.texCache.set(name, await this.makeTexture(base, name, noAstc));
        textures.set(t, this.texCache.get(name));
      }
      // materiais
      const mats = scene.materials.map((m, mi) => {
        const name = scene.textureNames[m.texture] || '';
        const mat = new THREE.MeshBasicMaterial({ map: textures.get(m.texture) || null, side: THREE.DoubleSide });
        mat.color.setRGB(m.color[0], m.color[1], m.color[2]);
        if (m.flags & 1) {
          mat.transparent = true; mat.depthWrite = false; mat.opacity = Math.min(1, m.color[3]);
          if (ADDITIVE.test(name)) mat.blending = THREE.AdditiveBlending;
        } else if (ALPHA_CUT.test(name)) { mat.alphaTest = 0.5; }
        if (m.color[3] < 0.99) { mat.transparent = true; mat.opacity = m.color[3]; }
        this.disposables.push(mat); return mat;
      });
      const geoms = new Map();
      const W = worldMatrices(scene.nodes);
      let tris = 0, draws = 0;
      const sky = [];
      scene.nodes.forEach((nd, ni) => {
        if (nd.mesh < 0 || !scene.meshes[nd.mesh]) return;
        const mesh = scene.meshes[nd.mesh];
        let g = geoms.get(nd.mesh);
        if (!g) {
          const d = readMesh(scene, mesh); g = new THREE.BufferGeometry();
          g.setAttribute('position', new THREE.BufferAttribute(d.pos, 3)); g.setAttribute('uv', new THREE.BufferAttribute(d.uv, 2));
          g.setIndex(new THREE.BufferAttribute(d.idx, 1)); g.computeBoundingSphere(); geoms.set(nd.mesh, g); this.disposables.push(g);
        }
        const o = new THREE.Mesh(g, mats[mesh.material] || mats[0]);
        o.matrixAutoUpdate = false; o.matrix.fromArray(W[ni]); o.matrixWorldNeedsUpdate = true;
        if (g.boundingSphere.radius * Math.max(...nd.scale) > scene.radius * 0.45) { o.renderOrder = -10; o.frustumCulled = false; sky.push(o); }
        else if (mats[mesh.material] && mats[mesh.material].transparent) o.renderOrder = 5;
        this.group.add(o); tris += mesh.ni / 3; draws++;
      });
      this.scene = scene; this.info = { unit, tris: Math.round(tris), draws, noAstc, name: e.name, radius: scene.radius };
      this.userScale = 1; this.heightAdj = 0; this.applyScale(); this.group.updateMatrixWorld(true);
      this.computeFloor();
      this.placeUser();
      if (e.files.includes('ambient.ogg')) {
        try { const b = await app.apk.blob_(base + 'ambient.ogg'); app.sound.playAmbient(URL.createObjectURL(new Blob([b], { type: 'audio/ogg' }))); } catch (x) { /* sem áudio */ }
      }
      this.loading = false;
      app.ui.open(this.panel(), 0.5);
      app.note('Ambiente carregado: ' + e.name);
    } catch (err) {
      this.loading = false;
      app.ui.open(() => ({ title: 'Erro ao carregar', lines: [{ t: String(err.message || err), color: '#ff6b6b' }], buttons: [{ label: 'Tentar de novo', cb: () => this.load(i) }, { label: 'Próximo ▶', cb: () => this.step(1) }, { label: 'Menu principal', cb: () => app.openMenu(), wide: true }] }));
      console.error(err);
    }
  }

  async makeTexture(base, name, noAstc) {
    const qa = globalThis.__QA_TEXTURES && globalThis.__QA_TEXTURES[name];
    if (qa) { // gancho de teste: textura já decodificada (RGBA) fornecida pelo harness
      const t = new THREE.DataTexture(Uint8Array.from(atob(qa.rgba), (c) => c.charCodeAt(0)), qa.w, qa.h, THREE.RGBAFormat);
      t.wrapS = t.wrapT = THREE.RepeatWrapping; t.minFilter = THREE.LinearFilter; t.magFilter = THREE.LinearFilter; t.generateMipmaps = false; t.needsUpdate = true; this.disposables.push(t); return t;
    }
    if (noAstc) return null;
    try {
      const k = parseKtx(await this.app.apk.arrayBuffer(base + name));
      const mips = limitMips(k, this.app.settings.tex);
      const t = new THREE.CompressedTexture(mips, mips[0].width, mips[0].height, k.glInternalFormat);
      t.wrapS = t.wrapT = THREE.RepeatWrapping; t.minFilter = mips.length > 1 ? THREE.LinearMipmapLinearFilter : THREE.LinearFilter; t.magFilter = THREE.LinearFilter;
      t.generateMipmaps = false; t.needsUpdate = true; this.disposables.push(t); return t;
    } catch (e) { console.warn('textura', name, e); return null; }
  }

  /** Nos ambientes originais a origem do cenário é o chão do ponto de vista (y=0). Confirma com um raio para baixo
   *  (qualquer malha, inclusive translúcida) e aceita o chão se estiver a no máx. 3 m abaixo / 0,5 m acima; senão usa y=0. */
  computeFloor() {
    const u = this.info.unit, ray = new THREE.Raycaster(new THREE.Vector3(0, 0.5 / u, 0), new THREE.Vector3(0, -1, 0), 0, 3.5 / u);
    this.group.updateMatrixWorld(true);
    const hit = ray.intersectObjects(this.group.children, false)[0];
    this.floorY = hit ? hit.point.y : 0;
    this.info.floorFound = !!hit;
  }

  placeUser() {
    if (!this.info) return;
    const s = this.info.unit * this.userScale;
    this.app.root.position.set(0, this.floorY * s + this.heightAdj, 0);
  }

  update(fi) {
    const app = this.app;
    if (this.loading) return;
    let ray = null, hand = null;
    const pi = app.primary(fi.rays); if (pi >= 0) { ray = fi.rays[pi]; hand = ray.hand; }
    if (fi.xr && hand) {
      if (app.bindings.justPressed(hand, 'alt')) { this.fly = !this.fly; app.note(this.fly ? 'Voo ligado' : 'Voo desligado'); }
      const m = app.bindings.move(hand);
      if (m.mag > 0.05) this.move(m, fi.dt, ray);
    } else if (!fi.xr) {
      const k = app.keys; const m = { x: (k.has('KeyD') ? 1 : 0) - (k.has('KeyA') ? 1 : 0), y: (k.has('KeyW') ? 1 : 0) - (k.has('KeyS') ? 1 : 0), mag: 1 };
      if (m.x || m.y) this.move(m, fi.dt, { quat: app.camera.getWorldQuaternion(new THREE.Quaternion()) });
    }
  }

  move(m, dt, ray) {
    const app = this.app;
    const q = app.settings.steer === 'head' ? app.camera.getWorldQuaternion(new THREE.Quaternion()) : ray.quat;
    const f = new THREE.Vector3(0, 0, -1).applyQuaternion(q);
    if (!this.fly) f.y = 0;
    if (f.lengthSq() < 1e-4) { f.set(0, 0, -1).applyQuaternion(app.camera.getWorldQuaternion(new THREE.Quaternion())); f.y = 0; }
    f.normalize();
    const r = new THREE.Vector3().crossVectors(f, new THREE.Vector3(0, 1, 0)).normalize();
    const speed = app.settings.speed * Math.max(1, this.userScale < 1 ? 1 : 1);
    app.root.position.addScaledVector(f, m.y * speed * dt).addScaledVector(r, m.x * speed * dt);
  }
}
