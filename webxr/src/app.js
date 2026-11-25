import * as THREE from 'three';
import { HandState, Bindings, describeHand } from './inputmap.js';
import { UI } from './ui.js';
import { Sound } from './audio.js';

THREE.ColorManagement.enabled = false;   // texturas/cores passam "cruas" (sem conversão sRGB): igual ao app original

const DEFAULT_SETTINGS = { speed: 3.5, tex: 2048, scale: 0.75, armReach: 0.8, steer: 'controller', forceArm: false, volume: 0.7 };

export class App {
  constructor(canvas, store) {
    this.store = store;
    this.settings = { ...DEFAULT_SETTINGS };
    try { Object.assign(this.settings, JSON.parse(store.getItem('pico-webxr-settings') || '{}')); } catch (e) { /* ok */ }
    this.bindings = new Bindings(store);
    this.sound = new Sound(); this.sound.volume = this.settings.volume;

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance', alpha: false });
    this.renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.xr.enabled = true;
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(70, 1, 0.05, 4000);
    this.root = new THREE.Group();     // origem do corpo no mundo (locomoção move isto)
    this.offset = new THREE.Group();   // compensa o espaço 'local' (sem chão): +altura dos olhos
    this.root.add(this.offset); this.offset.add(this.camera); this.scene.add(this.root);
    this.hands = [new HandState(0), new HandState(1)];
    this.controllers = [0, 1].map((i) => {
      const c = this.renderer.xr.getController(i);
      c.addEventListener('connected', (e) => { this.sources[i] = e.data; this.hands[i].connected = true; this.hands[i].everPressed.clear(); });
      c.addEventListener('disconnected', () => { this.sources[i] = null; this.hands[i].connected = false; this.hands[i].buttons = []; this.hands[i].axes = []; });
      c.addEventListener('selectstart', () => { this.hands[i].selecting = true; this._selStart[i] = true; });
      c.addEventListener('selectend', () => { this.hands[i].selecting = false; });
      this.offset.add(c); return c;
    });
    this.sources = [null, null]; this._selStart = [false, false]; this._firePrev = [false, false]; this._uiClickT = -9;
    this.lasers = this.controllers.map((c) => {
      const g = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), new THREE.Vector3(0, 0, -1)]);
      const l = new THREE.Line(g, new THREE.LineBasicMaterial({ color: 0x34d1ff, depthTest: false }));
      l.renderOrder = 999; l.visible = false; l.frustumCulled = false; c.add(l); return l;
    });
    this.ui = new UI(this);
    this.session = null; this.refType = 'local-floor'; this.eyeOffset = 0;
    this.inSession = false; this.padAlways = false;
    this.mode = null; this.modes = {}; this.modeName = '';
    this.apk = null; this.apkInfo = null;
    this.learn = null; this.log = [];
    this.pointer = { x: 0, y: 0, down: false, justDown: false, active: false };   // modo teste 2D
    this.keys = new Set();
    this.dt = 0; this._t = 0; this.frameCount = 0; this.fps = 0; this._fpsAcc = 0; this._fpsN = 0;
    this.onSessionChange = () => {};
    this._tmpV = new THREE.Vector3(); this._tmpQ = new THREE.Quaternion();
    this._bindDesktop(canvas);
    window.addEventListener('resize', () => this.resize());
    this.resize();
    this.renderer.setAnimationLoop((t, frame) => this.frame(t, frame));
  }

  saveSettings() { try { this.store.setItem('pico-webxr-settings', JSON.stringify(this.settings)); } catch (e) { /* ok */ } }
  note(msg) { this.log.push(msg); if (this.log.length > 12) this.log.shift(); this.lastNote = msg; this.noteT = 4; }

  resize() {
    if (this.renderer.xr.isPresenting) return;
    const w = window.innerWidth, h = window.innerHeight;
    this.renderer.setSize(w, h, false); this.camera.aspect = w / h; this.camera.updateProjectionMatrix();
  }

  // ---------- suporte / sessão ----------
  async checkSupport() {
    const r = { secure: window.isSecureContext, xr: !!navigator.xr, vr: false, error: '', refs: {}, ua: navigator.userAgent };
    const gl = this.renderer.getContext();
    r.webgl2 = typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext;
    r.astc = this.renderer.extensions.has('WEBGL_compressed_texture_astc');
    r.maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    r.renderer = ''; try { const e = gl.getExtension('WEBGL_debug_renderer_info'); if (e) r.renderer = gl.getParameter(e.UNMASKED_RENDERER_WEBGL); } catch (e) { /* ok */ }
    if (navigator.xr) {
      try { r.vr = await navigator.xr.isSessionSupported('immersive-vr'); } catch (e) { r.error = String(e && e.message || e); }
    }
    this.support = r; return r;
  }

  async enterVR() {
    if (!navigator.xr) throw new Error('Este navegador não tem WebXR (navigator.xr). Abra a página no Navegador do Pico (HTTPS).');
    this.sound.unlock();
    this.renderer.xr.setFramebufferScaleFactor(this.settings.scale);
    const session = await navigator.xr.requestSession('immersive-vr', { optionalFeatures: ['local-floor', 'bounded-floor'] });
    // tenta chão real; se o aparelho (3DoF) não oferece, usa 'local' e soma a altura dos olhos
    let ref = 'local-floor';
    try { await session.requestReferenceSpace('local-floor'); } catch (e) { ref = 'local'; }
    this.refType = ref; this.renderer.xr.setReferenceSpaceType(ref);
    this.eyeOffset = ref === 'local' ? 1.6 : 0; this.offset.position.set(0, this.eyeOffset, 0);
    session.addEventListener('end', () => this._onEnd());
    await this.renderer.xr.setSession(session);
    this.session = session; this.inSession = true;
    this.note('Sessão VR iniciada (' + ref + ')');
    this.onSessionChange(true);
    if (this.mode && this.mode.onSessionStart) this.mode.onSessionStart();
    return session;
  }
  async exitVR() { if (this.session) { try { await this.session.end(); } catch (e) { /* já encerrada */ } } }
  _onEnd() {
    this.session = null; this.inSession = false; this.eyeOffset = 0; this.offset.position.set(0, 0, 0);
    this.sources = [null, null]; this.hands.forEach((h) => { h.connected = false; });
    this.lasers.forEach((l) => { l.visible = false; });
    this.resize(); this.onSessionChange(false);
  }

  // ---------- modos ----------
  register(name, mode) { this.modes[name] = mode; }
  async setMode(name) {
    if (this.mode && this.mode.exit) this.mode.exit();
    this.ui.close();
    this.modeName = name; this.mode = this.modes[name];
    this.root.position.set(0, 0, 0);
    this.scene.fog = null; this.scene.background = null;
    if (this.mode) await this.mode.enter();
  }

  toggleMenu() {
    if (this.ui.isOpen && this.ui.builder === this.menuBuilder) { this.ui.close(); return; }
    this.openMenu();
  }
  openMenu() { this.sound.click(); this.ui.open(this.menuBuilder, 0); }

  // ---------- entrada ----------
  _bindDesktop(canvas) {
    const rect = () => canvas.getBoundingClientRect();
    canvas.addEventListener('pointermove', (e) => {
      const r = rect(); this.pointer.x = ((e.clientX - r.left) / r.width) * 2 - 1; this.pointer.y = -(((e.clientY - r.top) / r.height) * 2 - 1);
      if (this.pointer.down && !this.ui.isOpen) { this.deskYaw -= e.movementX * 0.004; this.deskPitch = Math.max(-1.4, Math.min(1.4, this.deskPitch - e.movementY * 0.004)); }
      this.pointer.active = true;
    });
    canvas.addEventListener('pointerdown', (e) => { this.pointer.down = true; this.pointer.justDown = true; this.sound.unlock(); try { canvas.setPointerCapture(e.pointerId); } catch (x) { /* ok */ } });
    canvas.addEventListener('pointerup', () => { this.pointer.down = false; });
    window.addEventListener('keydown', (e) => { this.keys.add(e.code); if (e.code === 'Escape' || e.code === 'KeyM') this.toggleMenu(); });
    window.addEventListener('keyup', (e) => this.keys.delete(e.code));
    this.deskYaw = 0; this.deskPitch = 0;
  }

  /** atualiza HandState a partir das fontes XR e calcula raios mundiais */
  _readInput(frame) {
    const rays = [];
    const ref = this.renderer.xr.getReferenceSpace();
    for (let i = 0; i < 2; i++) {
      const src = this.sources[i], h = this.hands[i];
      if (!src) { h.connected = false; continue; }
      let emulated = true, hasPos = false;
      try {
        const pose = frame && ref ? frame.getPose(src.gripSpace || src.targetRaySpace, ref) : null;
        if (pose) { hasPos = !!pose.transform.position; emulated = pose.emulatedPosition !== false; }
      } catch (e) { /* ok */ }
      h.update({ handedness: src.handedness, profiles: Array.from(src.profiles || []), gamepad: src.gamepad || null, selecting: h.selecting, emulated, hasPosition: hasPos, targetRayMode: src.targetRayMode });
      const c = this.controllers[i];
      c.updateMatrixWorld(true);
      const o = new THREE.Vector3().setFromMatrixPosition(c.matrixWorld);
      const q = new THREE.Quaternion().setFromRotationMatrix(c.matrixWorld);
      rays[i] = { origin: o, dir: new THREE.Vector3(0, 0, -1).applyQuaternion(q), quat: q, hand: h };
    }
    return rays;
  }

  /** Mão "primária" (a que tem raio): preferência para a direita, senão qualquer conectada. */
  primary(rays) { return rays[1] ? 1 : rays[0] ? 0 : -1; }

  desktopRay() {
    this.camera.updateMatrixWorld(true);
    const o = new THREE.Vector3().setFromMatrixPosition(this.camera.matrixWorld);
    const d = this.ui.isOpen || this.ui.pad.mesh.visible ? new THREE.Vector3(this.pointer.x, this.pointer.y, 0.5).unproject(this.camera).sub(o).normalize()
      : new THREE.Vector3(0, 0, -1).applyQuaternion(this.camera.getWorldQuaternion(this._tmpQ));
    const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 0, -1), d);
    return { origin: o, dir: d, quat: q, hand: null };
  }

  frame(t, frame) {
    const now = t / 1000; const dt = Math.min(0.05, this._t ? now - this._t : 0.016); this._t = now; this.dt = dt;
    this.frameCount++; this._fpsAcc += dt; this._fpsN++;
    if (this._fpsAcc > 0.5) { this.fps = this._fpsN / this._fpsAcc; this._fpsAcc = 0; this._fpsN = 0; }
    if (this.noteT > 0) this.noteT -= dt;
    const xr = this.renderer.xr.isPresenting;
    let rays = [];
    if (xr) rays = this._readInput(frame);
    else {
      // modo teste 2D: câmera controlada por mouse/teclado
      this.camera.rotation.order = 'YXZ'; this.camera.rotation.set(this.deskPitch, this.deskYaw, 0);
      if (this.camera.position.y < 0.5) this.camera.position.set(0, 1.6, 0);
      rays[0] = this.desktopRay();
    }
    // aprender botão
    if (this.learn) this._learnStep();
    // UI
    const uiHits = this.ui.update(dt, rays.map((r) => r || { origin: new THREE.Vector3(0, -99, 0), dir: new THREE.Vector3(0, 1, 0) }));
    const uiActive = this.ui.isOpen || this.ui.pad.mesh.visible;
    let uiClicked = false;
    for (let i = 0; i < rays.length; i++) {
      if (!rays[i]) continue;
      // gatilho: borda de subida única (evento 'select' do WebXR OU botão do gamepad, o que vier primeiro) + anti-duplo-clique
      let fireNow = false;
      if (xr) {
        const down = !!(rays[i].hand && this.bindings.isDown(rays[i].hand, 'fire')) || this._selStart[i];
        fireNow = down && !this._firePrev[i] && now - this._uiClickT > 0.35; this._firePrev[i] = down || (rays[i].hand ? rays[i].hand.selecting : false);
      } else fireNow = this.pointer.justDown;
      const hit = uiHits[i];
      if (xr) {
        const l = this.lasers[i]; l.visible = uiActive || !!this.laserAlways;
        l.scale.z = hit ? Math.max(0.1, hit.dist) : 3;
      }
      if (fireNow && uiActive && hit) { this._uiClickT = now; this.sound.click(); this.ui.click(i); uiClicked = true; this._pulse(i); }
      if (xr && rays[i].hand && this.bindings.justPressed(rays[i].hand, 'menu') && !this.learn) this.toggleMenu();
    }
    this.frameInfo = { xr, rays, uiActive, uiClicked, dt, now };
    if (this.mode && this.mode.update && !(this.ui.isOpen && this.mode.pausable !== false)) this.mode.update(this.frameInfo);
    else if (this.mode && this.mode.idle) this.mode.idle(this.frameInfo);
    this._selStart[0] = this._selStart[1] = false; this.pointer.justDown = false;
    this.renderer.render(this.scene, this.camera);
  }

  _pulse(i) { try { const a = this.sources[i] && this.sources[i].gamepad && this.sources[i].gamepad.hapticActuators; if (a && a[0] && a[0].pulse) a[0].pulse(0.4, 30); } catch (e) { /* ok */ } }

  startLearn(action, done) { this.learn = { action, done, baseline: null }; }
  _learnStep() {
    for (const h of this.hands) {
      if (!h.connected) continue;
      for (let i = 0; i < h.buttons.length; i++) {
        if (h.justPressed(i)) {
          this.bindings.bind(this.learn.action, i);
          const d = this.learn.done; this.learn = null; if (d) d(i); return;
        }
      }
    }
  }

  /** texto de diagnóstico (2D e VR) */
  diagnostics() {
    const s = this.support || {};
    const L = [];
    L.push(`WebXR: ${s.xr ? 'sim' : 'NÃO'} | immersive-vr: ${s.vr ? 'suportado' : 'NÃO'} | HTTPS/seguro: ${s.secure ? 'sim' : 'NÃO'}`);
    L.push(`WebGL2: ${s.webgl2 ? 'sim' : 'NÃO'} | ASTC: ${s.astc ? 'sim' : 'NÃO (texturas viram cores)'} | tex máx: ${s.maxTex}`);
    L.push(`GPU: ${s.renderer || '(oculta)'} | FPS: ${this.fps.toFixed(0)} | ref: ${this.refType}`);
    L.push(`UA: ${(s.ua || navigator.userAgent).slice(0, 110)}`);
    if (s.error) L.push('Erro isSessionSupported: ' + s.error);
    const conn = this.hands.filter((h) => h.connected);
    if (!conn.length) L.push('Controles: nenhum detectado' + (this.inSession ? ' (aperte um botão / acorde o controle)' : ' (entre em VR)'));
    for (const h of conn) L.push(describeHand(h));
    return L.join('\n');
  }
}
