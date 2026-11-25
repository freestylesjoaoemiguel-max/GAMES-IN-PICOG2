import { App } from './app.js';
import { ApkReader, listEnvironments } from './apk.js';
import { ViewerMode } from './modes/viewer.js';
import { PegaPegaMode } from './modes/pegapega.js';
import { FpsMode } from './modes/fps.js';

const $ = (id) => document.getElementById(id);
const store = (() => { try { localStorage.setItem('t', '1'); localStorage.removeItem('t'); return localStorage; } catch (e) { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }; } })();

const app = new App($('xr-canvas'), store);
window.app = app;   // para depuração/testes
app.register('viewer', new ViewerMode(app));
app.register('pegapega', new PegaPegaMode(app));
app.register('fps', new FpsMode(app));
app.selected = store.getItem('pico-webxr-mode') || 'pegapega';

const MODES = { viewer: 'Ambientes (Metulas)', pegapega: 'Pega-Pega (Gorilla)', fps: 'Operation Zero (FPS)' };

async function startMode(name) {
  app.selected = name; try { store.setItem('pico-webxr-mode', name); } catch (e) { /* ok */ }
  if (name === 'viewer' && !app.apk) { app.note('Escolha o APK na página primeiro.'); }
  await app.setMode(name);
  refreshHome();
}

// ----- painéis em VR -----
function settingsSpec() {
  const s = app.settings, b = app.bindings;
  const save = () => app.saveSettings();
  return {
    title: 'Configurações', cols: 2,
    lines: [`Velocidade ${s.speed.toFixed(1)} m/s · Alcance do braço ${s.armReach.toFixed(2)} m · Texturas ${s.tex}px`,
      `Resolução ${Math.round(s.scale * 100)}% (vale na próxima entrada em VR) · Direção: ${s.steer === 'head' ? 'cabeça' : 'controle'}`],
    buttons: [
      { label: 'Velocidade −', cb: () => { s.speed = Math.max(0.5, s.speed - 0.5); save(); } },
      { label: 'Velocidade +', cb: () => { s.speed = Math.min(30, s.speed + 0.5); save(); } },
      { label: 'Braço −', cb: () => { s.armReach = Math.max(0.5, s.armReach - 0.05); save(); } },
      { label: 'Braço +', cb: () => { s.armReach = Math.min(1.2, s.armReach + 0.05); save(); } },
      { label: s.steer === 'head' ? 'Andar: cabeça' : 'Andar: controle', cb: () => { s.steer = s.steer === 'head' ? 'controller' : 'head'; save(); } },
      { label: b.invertY ? 'Inverter frente: SIM' : 'Inverter frente: não', cb: () => { b.invertY = !b.invertY; b.save(); }, active: b.invertY },
      { label: b.invertX ? 'Inverter lados: SIM' : 'Inverter lados: não', cb: () => { b.invertX = !b.invertX; b.save(); }, active: b.invertX },
      { label: s.forceArm ? 'Braço virtual: sempre' : 'Braço virtual: auto', cb: () => { s.forceArm = !s.forceArm; save(); }, active: s.forceArm },
      { label: 'Textura máx ' + s.tex, cb: () => { s.tex = s.tex >= 4096 ? 512 : s.tex * 2; save(); } },
      { label: 'Resolução ' + Math.round(s.scale * 100) + '%', cb: () => { s.scale = s.scale >= 1 ? 0.5 : Math.round((s.scale + 0.125) * 1000) / 1000; save(); } },
      { label: 'Volume ' + Math.round(s.volume * 100) + '%', cb: () => { s.volume = s.volume >= 1 ? 0 : Math.round((s.volume + 0.2) * 10) / 10; app.sound.setVolume(s.volume); save(); } },
      { label: 'Voltar', cb: () => app.openMenu() },
    ],
  };
}

function mapSpec() {
  const b = app.bindings, ln = [];
  const lbl = (a) => (a === 'alt' && b.map.alt === null ? 'qualquer outro botão' : (b.map[a] || []).length ? 'botão ' + b.map[a].join(',') : 'nenhum');
  ln.push(`Atirar/selecionar: ${lbl('fire')} (gatilho)  ·  Ação extra: ${lbl('alt')}  ·  Menu: ${lbl('menu')}`);
  const conn = app.hands.filter((h) => h.connected);
  if (!conn.length) ln.push({ t: 'Nenhum controle detectado ainda — mexa/aperte o controle.', color: '#ffd166' });
  for (const h of conn) ln.push({ t: `Controle ${h.handedness}: ${h.buttons.map((x, i) => (x.pressed ? i : null)).filter((x) => x !== null).join(',') || '–'} apertado(s); eixos ${h.axes.map((a) => a.toFixed(1)).join(' ')}`, color: '#9fe3ff' });
  if (app.learn) ln.push({ t: `Aperte agora o botão para “${app.learn.action}”…`, color: '#ffd166' });
  else if (app.learnMsg) ln.push({ t: app.learnMsg, color: '#6ee7a0' });
  const learn = (action, name) => ({ label: 'Mapear: ' + name, cb: () => { app.startLearn(action, (i) => { app.learnMsg = `“${name}” = botão ${i}`; }); } });
  return { title: 'Mapear botões', lines: ln, buttons: [learn('alt', 'ação extra'), learn('menu', 'abrir menu'), learn('fire', 'atirar'), { label: 'Restaurar padrão', cb: () => { b.reset(); app.learnMsg = 'Padrão restaurado'; } }, { label: 'Voltar', cb: () => app.openMenu(), wide: true }] };
}

function diagSpec() {
  return { title: 'Diagnóstico', lines: app.diagnostics().split('\n').flatMap((l) => (l.length > 74 ? [l.slice(0, 74), '  ' + l.slice(74, 148)] : [l])).slice(0, 15), buttons: [{ label: 'Voltar', cb: () => app.openMenu(), wide: true }] };
}

app.menuBuilder = () => ({
  title: 'PICO G2 · WebXR',
  lines: [`Modo atual: ${MODES[app.modeName] || '—'}   ·   FPS ${app.fps.toFixed(0)}`, app.apk ? 'APK carregado ✔' : 'APK dos ambientes: não carregado (escolha na página)'],
  buttons: [
    { label: '🌍 Ambientes', cb: () => startMode('viewer'), active: app.modeName === 'viewer' },
    { label: '🦍 Pega-Pega', cb: () => startMode('pegapega'), active: app.modeName === 'pegapega' },
    { label: '🔫 Operation Zero', cb: () => startMode('fps'), active: app.modeName === 'fps' },
    { label: '⚙ Configurações', cb: () => app.ui.open(settingsSpec, 0.4) },
    { label: '🎮 Mapear botões', cb: () => { app.learnMsg = ''; app.ui.open(mapSpec, 0.2); } },
    { label: '🩺 Diagnóstico', cb: () => app.ui.open(diagSpec, 0.5) },
    { label: '▶ Continuar', cb: () => app.ui.close() },
    { label: '⏏ Sair do VR', cb: () => (app.inSession ? app.exitVR() : app.ui.close()) },
  ],
});

// ----- página 2D -----
function setStatus(el, text, cls) { el.textContent = text; el.className = 'badge ' + (cls || ''); }

async function refreshSupport() {
  const s = await app.checkSupport();
  setStatus($('st-https'), s.secure ? 'HTTPS ✔' : 'Sem HTTPS ✖ (WebXR exige HTTPS)', s.secure ? 'ok' : 'bad');
  setStatus($('st-xr'), s.xr ? (s.vr ? 'WebXR immersive-vr ✔' : 'WebXR presente, mas immersive-vr não suportado ✖') : 'Sem WebXR ✖', s.vr ? 'ok' : 'bad');
  setStatus($('st-astc'), s.astc ? 'Texturas ASTC ✔' : 'ASTC ✖ (cenas só com cores)', s.astc ? 'ok' : 'warn');
  const btn = $('btn-vr');
  btn.disabled = !s.vr;
  $('vr-hint').textContent = s.vr ? '' : s.xr ? 'Este navegador diz que não suporta VR imersiva. Use o Navegador do Pico (atualizado) e abra por HTTPS.' : 'Não há WebXR aqui. No Pico, abra esta página no Navegador do Pico (HTTPS). No PC, use o modo teste 2D.';
  $('diag').textContent = app.diagnostics();
}

function refreshHome() {
  document.querySelectorAll('input[name=mode]').forEach((r) => { r.checked = r.value === app.selected; });
  $('apk-box').style.display = app.selected === 'viewer' ? 'block' : 'none';
}

async function loadApk(file) {
  const info = $('apk-info'); info.textContent = 'Lendo ' + file.name + '…';
  try {
    const apk = await ApkReader.open(file);
    const envs = listEnvironments(apk);
    if (!envs.length) throw new Error('Este arquivo não contém ambientes (assets/env/*/scene.bin). Escolha o Metulas.apk.');
    app.apk = apk; app.apkInfo = { name: file.name, size: file.size, envs: envs.map((e) => e.name) };
    info.textContent = `✔ ${file.name} (${(file.size / 1048576).toFixed(0)} MB): ${envs.length} ambientes — ${envs.map((e) => e.name).join(', ')}`; info.className = 'ok-text';
    $('diag').textContent = app.diagnostics();
  } catch (e) { app.apk = null; info.textContent = '✖ ' + (e.message || e); info.className = 'bad-text'; }
}

async function go2D() {
  $('home').classList.add('hidden'); $('xr-canvas').classList.remove('hidden'); $('bar2d').classList.remove('hidden');
  app.resize();
  await startMode(app.selected);
}

function bind() {
  document.querySelectorAll('input[name=mode]').forEach((r) => r.addEventListener('change', () => { app.selected = r.value; refreshHome(); }));
  $('apk-file').addEventListener('change', (e) => { if (e.target.files[0]) loadApk(e.target.files[0]); });
  $('btn-vr').addEventListener('click', async () => {
    const err = $('vr-err'); err.textContent = '';
    try {
      app.sound.unlock();
      await app.enterVR();
      $('home').classList.add('hidden'); $('xr-canvas').classList.remove('hidden');
      await startMode(app.selected);
      if (!app.hands.some((h) => h.connected)) app.note('Aperte um botão do controle para ele aparecer.');
    } catch (e) { err.textContent = 'Não consegui entrar em VR: ' + (e.message || e); console.error(e); }
  });
  $('btn-2d').addEventListener('click', go2D);
  $('btn-back').addEventListener('click', () => { $('home').classList.remove('hidden'); $('xr-canvas').classList.add('hidden'); $('bar2d').classList.add('hidden'); });
  $('btn-menu2d').addEventListener('click', () => app.toggleMenu());
  $('btn-copy').addEventListener('click', async () => {
    const t = app.diagnostics();
    try { await navigator.clipboard.writeText(t); $('btn-copy').textContent = 'Copiado ✔'; } catch (e) { $('diag').focus(); window.getSelection().selectAllChildren($('diag')); $('btn-copy').textContent = 'Selecione e copie ↑'; }
    setTimeout(() => { $('btn-copy').textContent = 'Copiar diagnóstico'; }, 2500);
  });
  app.onSessionChange = (on) => {
    if (!on) { $('home').classList.remove('hidden'); $('xr-canvas').classList.add('hidden'); $('bar2d').classList.add('hidden'); refreshSupport(); }
  };
  setInterval(() => { if (!$('home').classList.contains('hidden')) $('diag').textContent = app.diagnostics(); }, 1000);
}

bind(); refreshHome(); refreshSupport();
