'use strict';
/* PicoVerso VR — núcleo: renderização (WebXR / tela dividida / tela), entrada do controle, braços, locomoção, interação */
const EYE = 1.6, V3 = THREE.Vector3, UP = new V3(0, 1, 0);
const $ = id => document.getElementById(id);

/* ---------- Configurações ---------- */
const DEFAULTS = { music: true, sfx: true, voice: true, vol: 0.8, dark: false, snap: true, invert: false, walkDir: 'head', speed: 2.4, dwell: false, clickBtns: null, menuBtns: [1], walkAxis: -1 };
function loadSettings() { try { return Object.assign({}, DEFAULTS, JSON.parse(localStorage.getItem('pico_settings') || '{}')); } catch (e) { return Object.assign({}, DEFAULTS); } }
function saveSettings() { try { localStorage.setItem('pico_settings', JSON.stringify(Game.settings)); } catch (e) {} }
function loadProgress() { try { return JSON.parse(localStorage.getItem('pico_progress') || '{}'); } catch (e) { return {}; } }

/* ---------- Cena ---------- */
const canvas = $('view');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.xr.enabled = true; try { renderer.xr.setReferenceSpaceType('local'); } catch (e) {}
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(72, 1, 0.05, 700);
const rig = new THREE.Group(); rig.add(camera); scene.add(rig); rig.position.y = EYE;
const hemi = new THREE.HemisphereLight(0xffffff, 0x888888, 2); scene.add(hemi);
const sun = new THREE.DirectionalLight(0xffffff, 2); scene.add(sun);
const skyMesh = new THREE.Mesh(new THREE.SphereGeometry(450, 40, 20), new THREE.MeshBasicMaterial({ side: THREE.BackSide, fog: false, depthWrite: false }));
skyMesh.renderOrder = -1000; skyMesh.frustumCulled = false; scene.add(skyMesh);
const skyCache = {};
const stereo = new THREE.StereoCamera();

/* ---------- Estado do jogo ---------- */
const Game = {
  settings: loadSettings(), progress: loadProgress(),
  screen: 'home', history: [], mode: 'lobby', map: null, menuOpen: false,
  text: '', focus: null, shift: false, kbOpen: false, chat: null, chatInMain: false,
  starsFound: 0, hint: '', toastText: '', inputMode: 'flat', started: false,
  playerName() { return Brain.getName() || ''; }
};
let npcs = [], mainYaw = 0, chatYaw = 0, menuAnchor = new V3(), time = 0;
let mode = 'flat', xrSession = null;

const mainP = makeMainPanel(), chatP = makeChatPanel(), kbP = makeKeyboard(), hudP = makeHud(), toastP = makeToast();
scene.add(mainP.root, chatP.root, kbP.root);
camera.add(hudP.root, toastP.root);
hudP.root.rotation.set(-0.45, 0, 0); toastP.root.rotation.set(0.3, 0, 0);
hudP.show(false); toastP.show(false);
const panels = [mainP, chatP, kbP, hudP, toastP];

/* ---------- Corpo em primeira pessoa (braços!) ---------- */
const AV = { group: new THREE.Group(), arms: [], bodyYaw: 0, walkPh: 0 };
(function () {
  const U = new THREE.CylinderGeometry(1, 1, 1, 10), shirt = lam(0x3b82f6), skin = lam(0xf1c27d), glove = lam(0xffffff), dark = lam(0x283046);
  const mk = (g, m) => { const o = new THREE.Mesh(g, m); AV.group.add(o); return o; };
  AV.torso = mk(CG.cyl, shirt); AV.torso.scale.set(0.19, 0.5, 0.11);
  AV.hips = mk(CG.box, dark); AV.hips.scale.set(0.36, 0.14, 0.22);
  AV.legs = [mk(U, dark), mk(U, dark)];
  for (let i = 0; i < 2; i++) AV.arms.push({ up: mk(U, shirt), fo: mk(U, shirt), cuff: mk(U, glove), hand: mk(CG.sph, skin), finger: mk(U, skin) });
  scene.add(AV.group);
})();
const _s1 = new V3(), _s2 = new V3(), _s3 = new V3(), _s4 = new V3();
function setSeg(m, a, b, r) { const d = _s1.subVectors(b, a), L = d.length() || 0.001; m.position.copy(a).addScaledVector(d, 0.5); m.quaternion.setFromUnitVectors(UP, d.multiplyScalar(1 / L)); m.scale.set(r, L, r); }

/* ---------- Cursor e laser ---------- */
const cursor = new THREE.Group();
const cMat = new THREE.MeshBasicMaterial({ color: 0xffffff, depthTest: false, transparent: true, opacity: 0.95 });
cursor.add(new THREE.Mesh(new THREE.RingGeometry(0.018, 0.027, 28), cMat)); cursor.add(new THREE.Mesh(new THREE.CircleGeometry(0.006, 12), cMat));
cursor.children.forEach(m => m.renderOrder = 200); scene.add(cursor);
const laserMat = new THREE.MeshBasicMaterial({ color: 0x7fd3ff, transparent: true, opacity: 0.75, depthTest: false });
const laser = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 6, 1, true), laserMat); laser.renderOrder = 199; scene.add(laser);
const fadeMesh = new THREE.Mesh(new THREE.PlaneGeometry(3, 3), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0, depthTest: false })); fadeMesh.position.z = -0.25; fadeMesh.renderOrder = 999; fadeMesh.visible = false; camera.add(fadeMesh);
let fade = null;

/* ---------- Controles XR ---------- */
const ctrls = [0, 1].map(i => {
  const c = renderer.xr.getController(i); rig.add(c);
  c.addEventListener('connected', e => { c.userData.connected = true; c.userData.hand = e.data.handedness; });
  c.addEventListener('disconnected', () => { c.userData.connected = false; });
  c.addEventListener('selectstart', () => { Input.xrSelect = true; }); c.addEventListener('selectend', () => { Input.xrSelect = false; });
  c.addEventListener('squeezestart', () => { Input.xrMenu = true; });
  return c;
});
function activeCtrl() { const c = ctrls.filter(c => c.userData.connected); return c.find(x => x.userData.hand === 'right') || c[0] || null; }

/* ---------- Entrada ---------- */
const Input = {
  raw: { id: '', axes: [], buttons: [] }, walk: 0, turn: 0, clickHeld: false, clickDown: false, clickUp: false, menuDown: false, pose: null,
  prevClick: false, prevMenu: false, xrSelect: false, xrMenu: false, keys: {}, mouse: { x: 0, y: 0, has: false, down: false, moved: 0, sx: 0, sy: 0 }, mouseClick: false, calib: null, prevBtn: [], pointerClick: false,
  startCalib(kind) { this.calib = { kind, msg: kind === 'walk' ? 'deslize o touch para FRENTE' : 'aperte o botão de ' + (kind === 'click' ? 'CLIQUE' : 'MENU') }; Sfx.init(); Sfx.play('calib'); mainP.dirty = true; },
  resetCalib() { Object.assign(Game.settings, { clickBtns: null, menuBtns: [1], walkAxis: -1, invert: false }); saveSettings(); this.calib = null; Game.toast('Controle restaurado'); mainP.dirty = true; },
  poll() {
    const S = Game.settings; let list = [];
    try { const g = navigator.getGamepads ? navigator.getGamepads() : []; for (const p of g) if (p && p.connected) list.push(p); } catch (e) {}
    if (xrSession) try { for (const s of xrSession.inputSources) if (s.gamepad && !list.includes(s.gamepad)) list.push(s.gamepad); } catch (e) {}
    let held = false, menuHeld = false, wy = 0, wx = 0; this.pose = null;
    for (const p of list) {
      this.raw.id = p.id; this.raw.axes = Array.from(p.axes); this.raw.buttons = p.buttons.map(b => (b.pressed ? 1 : b.touched ? 0.25 : 0));
      if (p.pose && p.pose.orientation) this.pose = p.pose.orientation;
      // eixos: touch/pad (0,1) ou analógico (2,3) — usa o par com mais movimento (ou o calibrado)
      const pairs = S.walkAxis >= 0 ? [[p.axes[S.walkAxis - 1] || 0, p.axes[S.walkAxis] || 0]] : [[p.axes[0] || 0, p.axes[1] || 0], [p.axes[2] || 0, p.axes[3] || 0]];
      for (const pr of pairs) if (Math.hypot(pr[0], pr[1]) > Math.hypot(wx, wy)) { wx = pr[0]; wy = pr[1]; }
      p.buttons.forEach((b, i) => {
        const down = b.pressed, prev = this.prevBtn[i];
        if (down && !prev && this.calib && this.calib.kind !== 'walk') {
          if (this.calib.kind === 'click') S.clickBtns = [i]; else S.menuBtns = [i];
          saveSettings(); Game.toast('Botão ' + i + ' definido!'); Sfx.play('calib'); this.calib = null; mainP.dirty = true;
        }
        this.prevBtn[i] = down;
        if (!down) return;
        if (S.menuBtns.includes(i)) menuHeld = true; else if (!S.clickBtns || S.clickBtns.includes(i)) held = true;
      });
      if (this.calib && this.calib.kind === 'walk') {
        p.axes.forEach((a, i) => { if (Math.abs(a) > 0.7 && i % 2 === 1) { S.walkAxis = i; S.invert = a > 0; saveSettings(); Game.toast('Andar: eixo ' + i + (a > 0 ? ' (invertido)' : '')); Sfx.play('calib'); this.calib = null; mainP.dirty = true; } });
      }
    }
    const dz = 0.3; wy = Math.abs(wy) > dz ? wy : 0; wx = Math.abs(wx) > 0.7 ? wx : 0;
    let walk = -wy * (S.invert ? -1 : 1), turn = Math.abs(wx) > Math.abs(wy) * 1.2 ? Math.sign(wx) : 0; if (turn) walk = 0;
    const K = this.keys, typing = Game.kbOpen && Game.focus;
    if (!typing) { walk += (K.w || K.arrowup ? 1 : 0) - (K.s || K.arrowdown ? 1 : 0); turn += (K.d || K.arrowright || K.e ? 1 : 0) - (K.a || K.arrowleft || K.q ? 1 : 0); }
    this.walk = clamp(walk, -1, 1); this.turn = clamp(turn, -1, 1);
    held = held || this.xrSelect || (!typing && (K[' '] || K.enter)); menuHeld = menuHeld || this.xrMenu;
    this.clickDown = (held && !this.prevClick) || this.pointerClick; this.clickUp = !held && this.prevClick; this.clickHeld = held; this.prevClick = held; this.pointerClick = false;
    this.menuDown = menuHeld && !this.prevMenu; this.prevMenu = menuHeld; this.xrMenu = false;
  }
};

/* ---------- API do jogo usada pela UI ---------- */
Game.toast = function (msg, ms) { this.toastText = msg; toastP.show(true); toastP.dirty = true; clearTimeout(Game._tt); Game._tt = setTimeout(() => toastP.show(false), ms || 2400); };
Game.dirtyAll = function () { panels.forEach(p => p.dirty = true); };
Game.setSetting = function (k, v) {
  this.settings[k] = v; saveSettings();
  if (['music', 'sfx', 'voice', 'vol'].includes(k)) Sfx.set(k, v);
  if (k === 'music') { if (v) Sfx.startMusic(musicKind()); else Sfx.stopMusic(); }
  Sfx.play('click'); this.dirtyAll();
};
function musicKind() { return Game.map ? (Game.map.id === 'lobby' ? 'lobby' : MAPS[Game.map.id].music) : 'lobby'; }
Game.resetProgress = function () { this.progress = {}; try { localStorage.removeItem('pico_progress'); } catch (e) {} this.toast('Progresso zerado'); this.dirtyAll(); };
Game.recenter = function () { alpha0 = null; poseYaw = null; this.toast('Visão recentralizada'); };

const headW = new V3(), headQ = new THREE.Quaternion(), _a = new V3(), _b = new V3(), _q = new THREE.Quaternion();
function headYaw() { camera.getWorldDirection(_a); return Math.atan2(-_a.x, -_a.z); }
function placeAtHead(p, dyaw, pitch, R) {
  camera.getWorldPosition(headW); const f = camera.getWorldDirection(_a); const y = Math.atan2(-f.x, -f.z) + (dyaw || 0);
  p.place(headW, y, pitch); return y;
}
Game.openMenu = function (screen) {
  Sfx.init(); if (screen) this.screen = screen;
  mainYaw = placeAtHead(mainP, 0, 0.04); mainP.show(true); mainP.dirty = true; this.menuOpen = true; menuAnchor.copy(rig.position); Sfx.play('open');
  chatP.show(false); updateHudVis();
};
Game.closeMenu = function () { mainP.show(false); this.menuOpen = false; if (this.chatInMain) this.closeChat(true); this.hideKeyboard(); Sfx.play('close'); updateHudVis(); };
Game.closeMenuBtn = function () { if (this.chat && !this.chatInMain) this.closeChat(); else this.closeMenu(); };
Game.toggleMenu = function () { if (this.chat && !this.chatInMain) { this.closeChat(); return; } this.menuOpen ? this.closeMenu() : this.openMenu('home'); };
Game.go = function (s) {
  if (this.screen === 'chat' && s !== 'chat' && this.chatInMain) this.closeChat(true);
  if (s !== this.screen) this.history.push(this.screen); this.screen = s;
  if (!this.menuOpen) this.openMenu(s); mainP.dirty = true; mainP.show(true); Sfx.play('open');
  if (s !== 'set' && this.focus === 'name') { this.focus = null; this.text = ''; this.hideKeyboard(); }
  if (s === 'ctrl') mainP.live = true;
};
Game.back = function () { const s = this.history.pop() || 'home'; if (this.screen === 'chat' && this.chatInMain) this.closeChat(true); this.screen = s; mainP.dirty = true; Sfx.play('back'); };

/* ---------- Teclado flutuante e digitação ---------- */
function placeKeyboard() {
  camera.getWorldPosition(headW);
  const yaw = Game.chat && !Game.chatInMain ? chatYaw : mainYaw;
  kbP.place(headW, yaw, Game.chat && !Game.chatInMain ? -0.98 : -0.72);
}
Game.showKeyboard = function () { placeKeyboard(); kbP.show(true); this.kbOpen = true; kbP.dirty = true; Sfx.play('pop'); };
Game.hideKeyboard = function () { if (this.kbOpen) Sfx.play('close'); kbP.show(false); this.kbOpen = false; };
Game.focusText = function (f) {
  if (this.focus !== f) { this.focus = f; this.text = f === 'name' ? (Brain.getName() || '') : ''; }
  this.showKeyboard(); this.dirtyAll();
};
Game.typeChar = function (ch) {
  if (!this.focus) this.focus = this.screen === 'home' ? 'search' : 'chat';
  if (this.text.length >= 120) return; this.text += ch;
  if (this.shift && /\S/.test(ch)) this.shift = false;
  this.dirtyAll();
};
Game.backspace = function () { this.text = this.text.slice(0, -1); this.dirtyAll(); };
Game.toggleShift = function () { this.shift = !this.shift; kbP.dirty = true; };
Game.submit = function () {
  const txt = this.text.trim();
  if (this.focus === 'name') { if (txt) { Brain.setName(Brain.cap(txt.split(' ')[0])); this.toast('Olá, ' + Brain.getName() + '!'); Sfx.play('star'); } this.text = ''; this.focus = null; this.hideKeyboard(); this.dirtyAll(); return; }
  if (!txt) { Sfx.play('error'); return; }
  if (this.focus === 'search' || !this.chat) { this.openChat('pico'); this.text = txt; }
  this.sendChat(this.text.trim());
};

/* ---------- Conversa com IA ---------- */
const CHIPS = ['Oi!', 'Quem é você?', 'Conte uma piada', 'Onde estão as estrelas?', 'Como eu jogo?', 'Como está o clima?', 'Qual sua comida favorita?', 'Obrigado!'];
Game.chips = function () { const ch = this.chat; if (!ch) return []; return CHIPS.filter(c => !ch.used[c]).slice(0, 4); };
function chatCtx() {
  return {
    mapId: Game.map ? Game.map.id : 'lobby', mapName: Game.map ? MAPINFO[Game.map.id].name : MAPINFO.lobby.name, starsFound: Game.starsFound, starsTotal: 8,
    starHint() {
      if (!Game.map || !Game.map.stars) return ''; let best = null, bd = 1e9;
      Game.map.stars.forEach(s => { if (s.taken) return; const d = Math.hypot(s.pos.x - rig.position.x, s.pos.z - rig.position.z); if (d < bd) { bd = d; best = s; } });
      if (!best) return ''; const f = camera.getWorldDirection(new V3()); const dx = best.pos.x - rig.position.x, dz = best.pos.z - rig.position.z;
      const ang = Math.atan2(dx * -f.z + dz * f.x, dx * f.x + dz * f.z) * 180 / PI;
      const side = Math.abs(ang) < 35 ? 'bem à sua frente' : Math.abs(ang) > 145 ? 'atrás de você' : (ang > 0 ? 'à sua direita' : 'à sua esquerda');
      return `A mais próxima está a uns ${Math.round(bd / 0.8)} passos, ${side}.`;
    }
  };
}
function pushMsg(ch, me, text) { const m = { me, text, shown: me ? text.length : 0, age: 0 }; ch.msgs.push(m); if (ch.msgs.length > 30) ch.msgs.shift(); return m; }
Game.openChat = function (t) {
  Sfx.init(); let inst = null, def = null;
  if (typeof t === 'string') { inst = npcs.find(n => n.def.id === t) || null; def = inst ? inst.def : ROSTER.lobby[0]; } else { inst = t; def = t.def; }
  if (this.chat) this.closeChat(true);
  const ch = this.chat = { npc: def, inst, msgs: [], thinking: false, typing: false, used: {} };
  this.text = ''; this.focus = 'chat';
  const useMain = !(this.mode === 'play' && inst);
  this.chatInMain = useMain;
  if (useMain) { if (this.screen !== 'chat') this.history.push(this.screen); this.screen = 'chat'; if (!this.menuOpen) this.openMenu('chat'); else { mainP.dirty = true; Sfx.play('open'); } }
  else {
    camera.getWorldPosition(headW); const dx = inst.pos.x - headW.x, dz = inst.pos.z - headW.z; chatYaw = Math.atan2(-dx, -dz) - 0.62; // painel ao lado da pessoa, para ela continuar visível
    chatP.place(headW, chatYaw, -0.22); chatP.show(true); chatP.dirty = true; mainP.show(false); this.menuOpen = false; Sfx.play('open');
  }
  if (inst) { inst.focus = true; inst.waveT = 2.5; }
  updateHudVis();
  const g = def.greet ? def.greet[Math.floor(Math.random() * def.greet.length)] : 'Olá!';
  this.say(ch, g.replace('amigo', Brain.getName() || 'amigo'));
};
Game.say = function (ch, text) {
  const m = pushMsg(ch, false, text); ch.typing = true; if (ch.inst) ch.inst.talking = true;
  Sfx.play('recv'); Sfx.speak(text, ch.npc.pitch || 1); this.dirtyAll(); return m;
};
Game.sendChat = function (text) {
  const ch = this.chat; if (!ch || ch.thinking) return; text = String(text).trim(); if (!text) return;
  ch.used[text] = true; pushMsg(ch, true, text); this.text = ''; ch.thinking = true; Sfx.play('send'); this.dirtyAll();
  Brain.ask(ch.npc, text, chatCtx()).then(r => { if (this.chat !== ch) return; ch.thinking = false; this.say(ch, r); });
};
Game.closeChat = function (silent) {
  const ch = this.chat; if (!ch) return; if (ch.inst) { ch.inst.focus = false; ch.inst.talking = false; }
  this.chat = null; this.focus = null; this.text = ''; Sfx.shutUp(); this.hideKeyboard();
  const wasMain = this.chatInMain; this.chatInMain = false; chatP.show(false);
  if (wasMain && this.screen === 'chat' && !silent) { this.screen = this.history.pop() || 'home'; mainP.dirty = true; }
  if (!silent) Sfx.play('close'); updateHudVis(); this.dirtyAll();
};
Game.openChatNearest = function () {
  let best = null, bd = 12; npcs.forEach(n => { const d = Math.hypot(n.pos.x - rig.position.x, n.pos.z - rig.position.z); if (d < bd) { bd = d; best = n; } });
  if (best) this.openChat(best); else { this.toast('Ninguém por perto — falando com o Pico'); this.openChat('pico'); }
};
function updateHudVis() { hudP.show(!Game.menuOpen && !Game.chat && started); }

/* ---------- Troca de cenário ---------- */
function disposeGroup(g) { g.traverse(o => { if (o.geometry && !o.userData.keep) o.geometry.dispose(); }); }
function loadScene(id) {
  if (Game.map) { scene.remove(Game.map.group); disposeGroup(Game.map.group); }
  npcs.forEach(n => scene.remove(n.root)); npcs = [];
  const M = id === 'lobby' ? buildLobby() : buildMap(id);
  Game.map = M; scene.add(M.group); M.doors.forEach(d => { d.baseRy = M.houses[d.idx].ry; });
  const kind = id === 'lobby' ? 'lobby' : MAPS[id].sky;
  if (!skyCache[kind]) skyCache[kind] = skyTexture(kind);
  skyMesh.material.map = skyCache[kind]; skyMesh.material.needsUpdate = true;
  const cfg = M.cfg; scene.fog = new THREE.Fog(cfg.fog[0], 12, cfg.fog[1]); scene.background = new THREE.Color(cfg.fog[0]);
  hemi.color.set(cfg.hemi[0]); hemi.groundColor.set(cfg.hemi[1]); hemi.intensity = cfg.hemi[2];
  sun.color.set(cfg.sun[0]); sun.intensity = cfg.sun[1]; sun.position.set(...cfg.sun[2]);
  (ROSTER[id] || []).forEach(def => { const n = new NPC(def, M); scene.add(n.root); npcs.push(n); });
  const sp = id === 'lobby' ? [0, 3.2, 0] : cfg.spawn; rig.position.set(sp[0], EYE, sp[1]); rig.rotation.y = sp[2]; AV.bodyYaw = sp[2];
  Game.mode = id === 'lobby' ? 'lobby' : 'play'; Game.starsFound = 0;
  Sfx.startMusic(Game.settings.music ? musicKind() : musicKind()); Sfx.startAmbience(id);
  Game.hint = id === 'lobby' ? 'Aponte e clique nos botões' : 'Deslize o touch de trás para andar';
  hudP.dirty = true;
}
function transition(cb) {
  fade = { t: 0, phase: 'out', cb }; fadeMesh.visible = true;
}
Game.play = function (id) {
  Sfx.init(); Sfx.play('whoosh'); if (this.chat) this.closeChat(true); this.hideKeyboard();
  mainP.show(false); this.menuOpen = false; this.history = []; this.screen = 'home';
  transition(() => { loadScene(id); this.toast('Bem-vindo à ' + MAPS[id].name + '! Encontre as 8 estrelas ⭐', 3800); updateHudVis(); });
};
Game.toLobby = function () {
  if (this.chat) this.closeChat(true); mainP.show(false); this.menuOpen = false;
  transition(() => { loadScene('lobby'); this.screen = 'home'; this.openMenu('home'); });
};

/* ---------- Estrelas, portas, confete ---------- */
const confetti = makePoints(180, 0.14, 0xffffff, false); confetti.material.vertexColors = true; confetti.visible = false; scene.add(confetti);
{ const col = new Float32Array(180 * 3), pal = [[1, .3, .3], [.3, .6, 1], [1, .85, .2], [.3, .9, .5], [.8, .4, 1]]; for (let i = 0; i < 180; i++) { const c = pal[i % 5]; col.set(c, i * 3); } confetti.geometry.setAttribute('color', new THREE.Float32BufferAttribute(col, 3)); }
const cf = { t: 99, v: [] };
function burst(pos) {
  cf.t = 0; cf.v = []; const p = confetti.geometry.attributes.position; for (let i = 0; i < 180; i++) { p.setXYZ(i, pos.x, pos.y + 1, pos.z); cf.v.push(new V3((Math.random() - 0.5) * 6, 3 + Math.random() * 5, (Math.random() - 0.5) * 6)); } confetti.visible = true;
}
function collectStar(s) {
  if (s.taken) return; s.taken = true; s.g.visible = false; Game.starsFound++; Sfx.play('star');
  const id = Game.map.id; Game.progress[id] = Math.max(Game.progress[id] || 0, Game.starsFound);
  try { localStorage.setItem('pico_progress', JSON.stringify(Game.progress)); } catch (e) {}
  hudP.dirty = true; burst(s.pos);
  if (Game.starsFound >= 8) { setTimeout(() => Sfx.play('fanfare'), 400); Game.toast('🎉 Parabéns! Mundo completo! Visite outro mapa no menu.', 5000); burst(rig.position); }
  else Game.toast('⭐ Estrela ' + Game.starsFound + ' de 8!');
}
function toggleDoor(d) { d.open = !d.open; Sfx.play(d.open ? 'door' : 'doorClose'); if (d.open) d.collider.active = false; }

/* ---------- Mira / ponteiro ---------- */
const rc = new THREE.Raycaster(); rc.far = 40;
const aim = { origin: new V3(), dir: new V3(0, 0, -1), type: 'gaze', point: new V3(), side: 1, target: null };
let poseYaw = null, alpha0 = null, orient = null, pitch = 0;
const mouseNdc = new THREE.Vector2();
function shoulderPos(side, out) {
  camera.getWorldPosition(headW); const y = AV.bodyYaw, fx = -Math.sin(y), fz = -Math.cos(y), rx = Math.cos(y), rz = -Math.sin(y);
  return out.set(headW.x + rx * side * 0.24 + fx * 0.0, headW.y - 0.3, headW.z + rz * side * 0.2 + fz * 0.0);
}
function computeAim() {
  camera.getWorldPosition(headW); camera.getWorldQuaternion(headQ); camera.getWorldDirection(_a); const fwd = _a;
  const c = mode === 'xr' ? activeCtrl() : null;
  if (c) { c.getWorldQuaternion(_q); aim.dir.set(0, 0, -1).applyQuaternion(_q); aim.side = c.userData.hand === 'left' ? -1 : 1; shoulderPos(aim.side, _b); aim.origin.copy(_b).addScaledVector(aim.dir, 0.45); aim.type = 'ctrl'; }
  else if (Input.pose && mode !== 'xr') {
    _q.fromArray(Input.pose); aim.dir.set(0, 0, -1).applyQuaternion(_q); const yawN = Math.atan2(-fwd.x, -fwd.z);
    if (poseYaw === null) poseYaw = yawN - Math.atan2(-aim.dir.x, -aim.dir.z); aim.dir.applyAxisAngle(UP, poseYaw); aim.side = 1; shoulderPos(1, _b); aim.origin.copy(_b).addScaledVector(aim.dir, 0.45); aim.type = 'ctrl';
  } else if (mode === 'flat' && Input.mouse.has) { rc.setFromCamera(mouseNdc, camera); aim.origin.copy(rc.ray.origin); aim.dir.copy(rc.ray.direction); aim.type = 'mouse'; aim.side = 1; }
  else { aim.origin.copy(headW); aim.dir.copy(fwd); aim.type = 'gaze'; aim.side = 1; }
}
let dwell = { id: null, t: 0 }, clickBlock = 0;
const _sph = new THREE.Sphere(), _hit = new V3();
function pickTarget() {
  rc.set(aim.origin, aim.dir); rc.far = 40; let best = { t: 1e9, type: null };
  for (const p of panels) {
    if (!p.open || p.a < 0.6) continue; const h = rc.intersectObject(p.mesh, false)[0];
    if (h && h.distance < best.t) best = { t: h.distance, type: 'ui', panel: p, px: h.uv.x * p.pw, py: (1 - h.uv.y) * p.ph, point: h.point.clone() };
  }
  const uiHit = best.type === 'ui'; const M = Game.map;
  if (!uiHit || best.panel === hudP) {
    npcs.forEach(n => { const s = n.def.scale || 1; _sph.center.set(n.pos.x, n.root.position.y + (n.def.robot ? 0.95 : 0.95 * s), n.pos.z); _sph.radius = (n.def.robot ? 0.7 : 0.62) * Math.max(s, 0.8); if (rc.ray.intersectSphere(_sph, _hit)) { const d = _hit.distanceTo(aim.origin); if (d < best.t && d < 14) best = { t: d, type: 'npc', npc: n, point: _hit.clone() }; } });
    if (M) {
      M.doors.forEach(d => { const h = rc.intersectObject(d.leaf, false)[0]; if (h && h.distance < best.t && h.distance < 14) best = { t: h.distance, type: 'door', door: d, point: h.point.clone() }; });
      (M.stars || []).forEach(s => { if (s.taken) return; _sph.center.copy(s.pos); _sph.radius = 0.8; if (rc.ray.intersectSphere(_sph, _hit)) { const d = _hit.distanceTo(aim.origin); if (d < best.t && d < 30) best = { t: d, type: 'star', star: s, point: _hit.clone() }; } });
    }
  }
  return best;
}
const hintFor = t => !t.type ? (Game.mode === 'lobby' ? 'Aponte e clique nos botões' : 'Deslize o touch para andar') : t.type === 'npc' ? 'Clique para falar com ' + t.npc.def.name : t.type === 'door' ? 'Clique para ' + (t.door.open ? 'fechar' : 'abrir') + ' a porta' : t.type === 'star' ? 'Clique para pegar a estrela!' : '';

function doInteract(dt) {
  const t = aim.target = pickTarget();
  panels.forEach(p => p.setPointer(t.type === 'ui' && t.panel === p ? t.px : null, t.type === 'ui' && t.panel === p ? t.py : null));
  if (t.type === 'ui' && t.panel === hudP && !hudP.hoverId) { /* HUD sem botão: ignora */ }
  if (t.type === 'npc') t.npc.hover = 1;
  const hint = t.type === 'ui' ? Game.hint : hintFor(t); if (t.type !== 'ui' && hint !== Game.hint) { Game.hint = hint; hudP.dirty = true; }
  // clique
  let clicked = Input.clickDown && clickBlock <= 0;
  // olhar para selecionar
  const dId = t.type === 'ui' ? (t.panel.hoverId ? t.panel.name + ':' + t.panel.hoverId : null) : (t.type === 'npc' ? 'n' + t.npc.def.id : t.type === 'door' ? 'd' + t.door.idx : null);
  if (Game.settings.dwell && dId) { if (dwell.id === dId) { dwell.t += dt; if (dwell.t > 2) { clicked = true; dwell.t = -1.2; } } else { dwell.id = dId; dwell.t = 0; } } else { dwell.id = null; dwell.t = 0; }
  if (clicked) {
    if (t.type === 'ui') { const ok = t.panel.click(); if (ok) clickBlock = 0.3; }
    else if (t.type === 'npc') { if (t.npc.pos.distanceTo(rig.position) < 14) Game.openChat(t.npc); }
    else if (t.type === 'door') { toggleDoor(t.door); }
    else if (t.type === 'star') { if (t.star.pos.distanceTo(rig.position) < 9) collectStar(t.star); else Game.toast('Chegue mais perto da estrela!', 1500); }
  }
  // repetir apagar ao segurar
  if (Input.clickHeld && t.type === 'ui' && t.panel === kbP && kbP.hoverId === 'bk') { holdT += dt; if (holdT > 0.5) { holdT = 0.42; Game.backspace(); Sfx.play('key'); } } else holdT = 0;
  // cursor e laser
  const pt = t.type ? t.point : _b.copy(aim.origin).addScaledVector(aim.dir, 6); aim.point.copy(pt);
  const hov = (t.type === 'ui' && t.panel.hoverId) || t.type === 'npc' || t.type === 'door' || t.type === 'star';
  cursor.position.copy(pt); cursor.lookAt(camera.getWorldPosition(_a)); const dist = pt.distanceTo(camera.getWorldPosition(_a)); cursor.scale.setScalar(clamp(dist * 0.5, 0.8, 5) * (hov ? 1.25 : 1) * (1 + Math.max(0, dwell.t) * 0.3));
  cMat.color.set(hov ? 0x4be08a : 0xffffff); cursor.visible = mode !== 'xr' || aim.type === 'ctrl' || true;
}
let holdT = 0;

/* ---------- Braços ---------- */
const handPos = [new V3(), new V3()];
function updateArms(dt, walking) {
  camera.getWorldPosition(headW); const hy = headYaw(); let d = hy - AV.bodyYaw; while (d > PI) d -= TAU; while (d < -PI) d += TAU; AV.bodyYaw += d * Math.min(1, dt * (Math.abs(d) > 0.7 ? 8 : 2));
  const y = AV.bodyYaw, fx = -Math.sin(y), fz = -Math.cos(y), fwd = new V3(fx, 0, fz), right = new V3(Math.cos(y), 0, -Math.sin(y));
  AV.walkPh += walking ? dt * 7 : 0;
  AV.torso.position.set(headW.x - fx * 0.02, headW.y - 0.62, headW.z - fz * 0.02); AV.torso.rotation.y = y;
  AV.hips.position.set(headW.x, headW.y - 1.0, headW.z); AV.hips.rotation.y = y;
  for (let i = 0; i < 2; i++) {
    const s = i ? 1 : -1, hip = _s2.set(headW.x + right.x * s * 0.1, headW.y - 1.05, headW.z + right.z * s * 0.1);
    const sw = Math.sin(AV.walkPh + (i ? PI : 0)) * 0.3 * (walking ? 1 : 0);
    const foot = _s3.set(hip.x + fwd.x * sw, 0.05, hip.z + fwd.z * sw); setSeg(AV.legs[i], hip, foot, 0.075);
  }
  const mainSide = aim.side, mainIdx = mainSide > 0 ? 1 : 0;
  for (let i = 0; i < 2; i++) {
    const A = AV.arms[i], side = i ? 1 : -1, S = shoulderPos(side, new V3()); let D;
    if (i === mainIdx) {
      if (aim.type === 'ctrl') D = aim.dir.clone(); else { D = new V3().subVectors(aim.point, S); if (D.length() < 0.8) D = aim.dir.clone(); D.normalize(); }
      if (aim.dir.y < -0.9) D.y = -0.9;
    } else {
      D = new V3(fwd.x * 0.28, -1, fwd.z * 0.28).addScaledVector(right, side * 0.22); D.addScaledVector(fwd, Math.sin(AV.walkPh + (i ? 0 : PI)) * 0.5 * (walking ? 1 : 0) + Math.sin(time * 1.4) * 0.03); D.normalize();
    }
    const reach = i === mainIdx ? 0.54 : 0.5, H = new V3().copy(S).addScaledVector(D, reach), mid = new V3().addVectors(S, H).multiplyScalar(0.5);
    const dd = Math.min(S.distanceTo(H), 0.61), hh = Math.sqrt(Math.max(0, 0.31 * 0.31 - (dd / 2) * (dd / 2)));
    const bend = new V3(0, -1, 0).addScaledVector(right, side * 0.6); bend.addScaledVector(D, -bend.dot(D)).normalize();
    const E = mid.addScaledVector(bend, hh);
    setSeg(A.up, S, E, 0.036); setSeg(A.fo, E, H, 0.031);
    const wrist = new V3().lerpVectors(E, H, 0.82); setSeg(A.cuff, wrist, H, 0.036);
    A.hand.position.copy(H).addScaledVector(D, 0.045); A.hand.scale.set(0.04, 0.036, 0.055); A.hand.quaternion.setFromUnitVectors(new V3(0, 0, 1), D);
    const tip = new V3().copy(H).addScaledVector(D, 0.14); if (i === mainIdx) { A.finger.visible = true; setSeg(A.finger, new V3().copy(H).addScaledVector(D, 0.04), tip, 0.01); } else A.finger.visible = false;
    handPos[i].copy(i === mainIdx ? tip : H);
  }
  // laser sai da ponta do dedo
  const from = handPos[mainIdx], to = aim.point, L = from.distanceTo(to);
  laser.visible = aim.target && aim.target.type !== null || aim.type === 'ctrl'; _s1.subVectors(to, from);
  laser.position.copy(from).addScaledVector(_s1, 0.5); laser.quaternion.setFromUnitVectors(UP, _s1.normalize()); laser.scale.set(0.0035, L, 0.0035);
  laserMat.color.set(aim.target && (aim.target.type === 'npc' || aim.target.type === 'door' || aim.target.type === 'star' || (aim.target.type === 'ui' && aim.target.panel.hoverId)) ? 0x4be08a : 0x7fd3ff);
  laser.visible = mode === 'xr' || aim.type === 'ctrl' || aim.type === 'mouse';
}

/* ---------- Locomoção ---------- */
let stepAcc = 0, turnCool = 0, moving = false;
function locomotion(dt) {
  const S = Game.settings; moving = false; clickBlock -= dt; turnCool -= dt;
  if (Game.kbOpen && Game.focus === 'name' && false) return;
  if (Input.walk && clickBlock <= 0) {
    let dir;
    if (S.walkDir === 'hand' && aim.type === 'ctrl') dir = _b.copy(aim.dir); else dir = camera.getWorldDirection(_b);
    dir.y = 0; if (dir.lengthSq() < 1e-4) return; dir.normalize();
    const before = rig.position.clone(), sp = S.speed * (Input.walk < 0 ? 0.6 : 1);
    rig.position.addScaledVector(dir, Input.walk * sp * dt); collide(Game.map, rig.position, 0.32);
    const moved = rig.position.distanceTo(before); moving = moved > 0.0005; stepAcc += moved;
    if (stepAcc > 0.85) { stepAcc = 0; Sfx.play('step', Game.map.surface); }
  }
  if (Input.turn && S.snap && turnCool <= 0) { rig.rotation.y -= Input.turn * 0.5236; turnCool = 0.35; Sfx.play('hover'); }
  else if (Input.turn && !S.snap) rig.rotation.y -= Input.turn * 1.6 * dt;
}

/* ---------- Orientação (giroscópio / mouse) ---------- */
const euler = new THREE.Euler(), q0 = new THREE.Quaternion(), q1 = new THREE.Quaternion(-Math.sqrt(0.5), 0, 0, Math.sqrt(0.5)), zee = new V3(0, 0, 1);
function updateOrientation() {
  if (mode === 'xr') return;
  if (mode === 'split' && orient && orient.alpha !== null && orient.alpha !== undefined) {
    if (alpha0 === null) alpha0 = orient.alpha;
    const a = (orient.alpha - alpha0) * PI / 180, b = (orient.beta || 0) * PI / 180, g = (orient.gamma || 0) * PI / 180, o = ((screen.orientation && screen.orientation.angle) || window.orientation || 90) * PI / 180;
    euler.set(b, a, -g, 'YXZ'); camera.quaternion.setFromEuler(euler); camera.quaternion.multiply(q1); camera.quaternion.multiply(q0.setFromAxisAngle(zee, -o));
  } else { camera.rotation.set(pitch, 0, 0, 'YXZ'); }
}

/* ---------- Loop principal ---------- */
let started = false, last = 0;
function frame(ms) {
  const dt = Math.min(0.05, Math.max(0.001, (ms - last) / 1000)); last = ms; time += dt;
  Input.poll();
  if (fade) {
    fade.t += dt * (fade.phase === 'out' ? 3.2 : 1.8); const a = clamp(fade.t, 0, 1);
    fadeMesh.material.opacity = fade.phase === 'out' ? a : 1 - a;
    if (fade.t >= 1) { if (fade.phase === 'out') { fade.cb(); fade = { t: 0, phase: 'in' }; } else { fade = null; fadeMesh.visible = false; } }
  }
  if (started) {
    if (Input.menuDown) Game.toggleMenu();
    updateOrientation(); locomotion(dt);
    rig.updateMatrixWorld(true); scene.updateMatrixWorld(true);
    updateHudVis();
  } else { updateOrientation(); rig.updateMatrixWorld(true); }
  computeAim(); doInteract(dt); updateArms(dt, moving);
  const M = Game.map;
  if (M) {
    M.updaters.forEach(u => u(dt, time, rig.position));
    M.doors.forEach(d => { const target = d.open ? -1.75 : 0; d.a += (target - d.a) * Math.min(1, dt * 6); d.pivot.rotation.y = d.baseRy + d.a; if (!d.open && Math.abs(d.a) < 0.05) d.collider.active = true; });
    (M.stars || []).forEach(s => {
      if (s.taken) return; s.t += dt; s.g.rotation.y = s.t * 1.6; s.g.position.y = s.base + Math.sin(s.t * 2) * 0.12;
      if (started && Math.hypot(s.pos.x - rig.position.x, s.pos.z - rig.position.z) < 1.3) collectStar(s);
    });
    if (Game.mode === 'play') {
      if (Game.menuOpen && rig.position.distanceTo(menuAnchor) > 5 && !Game.focus) Game.closeMenu();
      if (Game.chat && !Game.chatInMain && Game.chat.inst && Game.chat.inst.pos.distanceTo(rig.position) > 13) { Game.toast('A conversa terminou'); Game.closeChat(); }
    }
  }
  npcs.forEach(n => n.update(dt, rig.position));
  const ch = Game.chat; if (ch) {
    ch.msgs.forEach(m => { if (m.age < 1) m.age += dt; });
    const last = ch.msgs[ch.msgs.length - 1];
    if (last && !last.me && last.shown < last.text.length) {
      const before = Math.floor(last.shown); last.shown = Math.min(last.text.length, last.shown + dt * 34); if (Math.floor(last.shown) !== before) { if (Math.floor(last.shown) % 2 === 0) Sfx.play('blip', 160 * (ch.npc.pitch || 1)); }
      ch.typing = true; if (ch.inst) ch.inst.talking = true; mainP.dirty = chatP.dirty = true;
    } else if (ch.typing) { ch.typing = false; if (ch.inst) ch.inst.talking = false; mainP.dirty = chatP.dirty = true; }
    if (ch.thinking) mainP.dirty = chatP.dirty = true;
  }
  if (Game.focus && Math.floor(time * 2) !== Math.floor((time - dt) * 2)) { mainP.dirty = true; chatP.dirty = true; }
  if (cf.t < 4) { cf.t += dt; const p = confetti.geometry.attributes.position; cf.v.forEach((v, i) => { v.y -= 9 * dt; p.setXYZ(i, p.getX(i) + v.x * dt, Math.max(0, p.getY(i) + v.y * dt), p.getZ(i) + v.z * dt); }); p.needsUpdate = true; if (cf.t >= 4) confetti.visible = false; }
  skyMesh.position.set(rig.position.x, 0, rig.position.z);
  panels.forEach(p => p.update(dt, time));
  render();
}
function render() {
  if (mode === 'split') {
    const s = renderer.getSize(new THREE.Vector2()), hw = Math.floor(s.x / 2);
    camera.aspect = hw / s.y; camera.updateProjectionMatrix(); camera.updateMatrixWorld(true); stereo.update(camera);
    renderer.setScissorTest(true);
    renderer.setViewport(0, 0, hw, s.y); renderer.setScissor(0, 0, hw, s.y); renderer.render(scene, stereo.cameraL);
    renderer.setViewport(hw, 0, hw, s.y); renderer.setScissor(hw, 0, hw, s.y); renderer.render(scene, stereo.cameraR);
    renderer.setScissorTest(false);
  } else renderer.render(scene, camera);
}
function resize() {
  if (renderer.xr.isPresenting) return;
  const w = window.innerWidth, h = window.innerHeight; renderer.setSize(w, h, false);
  if (mode !== 'split') { camera.aspect = w / h; camera.updateProjectionMatrix(); }
}
window.addEventListener('resize', resize); resize();

/* ---------- Entradas do navegador (mouse, toque, teclado, giroscópio) ---------- */
function setMouse(e) { const r = canvas.getBoundingClientRect(); Input.mouse.x = e.clientX - r.left; Input.mouse.y = e.clientY - r.top; mouseNdc.set(Input.mouse.x / r.width * 2 - 1, -(Input.mouse.y / r.height) * 2 + 1); Input.mouse.has = true; }
canvas.addEventListener('pointermove', e => {
  if (mode === 'xr') return; setMouse(e);
  if (Input.mouse.down) {
    const dx = e.clientX - Input.mouse.sx, dy = e.clientY - Input.mouse.sy; Input.mouse.moved += Math.abs(e.movementX || 0) + Math.abs(e.movementY || 0);
    if (Input.mouse.moved > 8 && mode === 'flat') { rig.rotation.y -= (e.movementX || 0) * 0.005; pitch = clamp(pitch - (e.movementY || 0) * 0.004, -1.3, 1.3); } void dx; void dy;
  }
});
canvas.addEventListener('pointerdown', e => { setMouse(e); Input.mouse.down = true; Input.mouse.moved = 0; Input.mouse.sx = e.clientX; Input.mouse.sy = e.clientY; if (e.pointerType === 'touch') { /* toque: mira no ponto */ } try { canvas.setPointerCapture(e.pointerId); } catch (er) {} });
canvas.addEventListener('pointerup', e => { if (mode === 'xr') return; setMouse(e); if (Input.mouse.down && Input.mouse.moved < 10) Input.pointerClick = true; Input.mouse.down = false; if (e.pointerType === 'touch') Input.mouse.has = true; });
canvas.addEventListener('contextmenu', e => e.preventDefault());
window.addEventListener('keydown', e => {
  const k = e.key.toLowerCase(); Input.keys[k] = true;
  if (!started) return;
  if (Game.kbOpen && Game.focus) {
    if (k === 'enter') { Game.submit(); e.preventDefault(); } else if (k === 'backspace') { Game.backspace(); e.preventDefault(); } else if (k === 'escape') { Game.hideKeyboard(); } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) { Game.typeChar(e.key); Sfx.play('key'); e.preventDefault(); }
    return;
  }
  if (k === 'm' || k === 'escape') Game.toggleMenu();
  if (k === 'f') { try { document.documentElement.requestFullscreen(); } catch (er) {} }
  if (k === ' ' || k === 'arrowup' || k === 'arrowdown') e.preventDefault();
});
window.addEventListener('keyup', e => { Input.keys[e.key.toLowerCase()] = false; });
window.addEventListener('deviceorientation', e => { orient = e; });
window.addEventListener('gamepadconnected', e => { if (started) Game.toast('🎮 Controle conectado'); });

/* ---------- Início / modos ---------- */
function showOverlay(msg) { $('overlay').classList.remove('hide'); if (msg) $('msg').textContent = msg; }
function begin(m) {
  Sfx.init(); Sfx.set('music', Game.settings.music); Sfx.set('sfx', Game.settings.sfx); Sfx.set('voice', Game.settings.voice); Sfx.set('vol', Game.settings.vol);
  mode = m; Game.inputMode = m; $('overlay').classList.add('hide'); started = true; camera.fov = m === 'split' ? 90 : 72; resize(); camera.updateProjectionMatrix();
  if (!Game.map || Game.map.id !== 'lobby') loadScene('lobby');
  Sfx.startMusic('lobby'); Sfx.startAmbience('lobby');
  setTimeout(() => { Game.openMenu('home'); const n = Brain.getName(); Game.toast(n ? 'Bem-vindo de volta, ' + n + '!' : 'Bem-vindo ao PicoVerso!', 2600); }, 250);
}
async function startXR() {
  try {
    const session = await navigator.xr.requestSession('immersive-vr', { optionalFeatures: ['local', 'local-floor', 'bounded-floor'] });
    renderer.xr.setReferenceSpaceType('local'); await renderer.xr.setSession(session); xrSession = session;
    session.addEventListener('end', () => { xrSession = null; mode = 'flat'; resize(); showOverlay('Sessão VR encerrada.'); });
    begin('xr');
  } catch (e) { $('msg').textContent = 'Não foi possível iniciar o VR: ' + (e && e.message ? e.message : e) + ' — tente o modo óculos (tela dividida).'; }
}
async function startSplit() {
  try { if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) await DeviceOrientationEvent.requestPermission(); } catch (e) {}
  try { await document.documentElement.requestFullscreen(); } catch (e) {}
  try { await screen.orientation.lock('landscape'); } catch (e) {}
  alpha0 = null; begin('split');
}
$('btnFlat').onclick = () => begin('flat');
$('btnSplit').onclick = startSplit;
$('btnXR').onclick = startXR;
if (navigator.xr && navigator.xr.isSessionSupported) {
  navigator.xr.isSessionSupported('immersive-vr').then(ok => {
    $('btnXR').disabled = !ok; $('xrNote').textContent = ok ? 'Recomendado · braços e laser seguem o controle' : 'Seu navegador não liberou WebXR — use o modo óculos';
  }).catch(() => { $('xrNote').textContent = 'WebXR indisponível — use o modo óculos'; });
} else { $('xrNote').textContent = 'WebXR não encontrado neste navegador — use o modo óculos (tela dividida)'; }

loadScene('lobby'); renderer.setAnimationLoop(frame);
window.__game = { renderer, Game, Input, scene, camera, rig, panels, loadScene, npcs: () => npcs, aim, begin, setMode: m => { mode = m; }, mouseNdc };
