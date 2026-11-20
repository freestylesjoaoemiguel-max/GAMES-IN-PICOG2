'use strict';
/* PicoCam (Pico): recebe MJPEG + giroscópio do app do celular e mostra em VR (tela dividida) */
const $ = s => document.querySelector(s);
const S = Object.assign({ ip: '', zoom: 0.5, mode: 'head', disp: 'split', fov: 100 }, (() => { try { return JSON.parse(localStorage.getItem('picocam') || '{}'); } catch (e) { return {}; } })());
const save = () => { try { localStorage.setItem('picocam', JSON.stringify(S)); } catch (e) {} };
let active = false, base = '', info = null, es = null, img = null, tex = null, q = null, rot = 1, needCal = true, tAcc = 0, orient = null, alpha0 = null, lastTap = 0;
const say = (m, bad) => { $('#st').textContent = m; $('#st').style.color = bad ? '#ff8a80' : '#ffd23f'; };

/* ---------- tela do IP ---------- */
function drawIp() { $('#ip').textContent = S.ip || 'digite o IP…'; $('#ip').style.opacity = S.ip ? 1 : .5; }
['1','2','3','⌫','4','5','6','.','7','8','9','C','0',':'].forEach(k => {
  const b = document.createElement('button'); b.textContent = k;
  b.onclick = () => { if (k === '⌫') S.ip = S.ip.slice(0, -1); else if (k === 'C') S.ip = ''; else if (S.ip.length < 21) S.ip += k; save(); drawIp(); };
  $('#keys').appendChild(b);
});
window.addEventListener('keydown', e => { if (active) return; if (/^[0-9.:]$/.test(e.key)) { S.ip += e.key; save(); drawIp(); } else if (e.key === 'Backspace') { S.ip = S.ip.slice(0, -1); save(); drawIp(); } else if (e.key === 'Enter') connect(); });
function mark() {
  document.querySelectorAll('#zoom button').forEach(b => b.classList.toggle('on', +b.dataset.z === S.zoom));
  document.querySelectorAll('#mode button').forEach(b => b.classList.toggle('on', b.dataset.m === S.mode));
  document.querySelectorAll('#disp button').forEach(b => b.classList.toggle('on', b.dataset.d === S.disp));
}
document.querySelectorAll('#zoom button').forEach(b => b.onclick = () => { S.zoom = +b.dataset.z; S.fov = S.zoom <= .6 ? 100 : S.zoom <= 1.2 ? 70 : 40; save(); mark(); if (active) { rebuildGeo(); getJ('/zoom?r=' + S.zoom).catch(() => {}); } });
document.querySelectorAll('#mode button').forEach(b => b.onclick = () => { S.mode = b.dataset.m; needCal = true; save(); mark(); });
document.querySelectorAll('#disp button').forEach(b => b.onclick = () => { S.disp = b.dataset.d; save(); mark(); });
$('#go').onclick = connect; drawIp(); mark();

async function getJ(path) {
  const c = new AbortController(), t = setTimeout(() => c.abort(), 4500);
  try { const r = await fetch(base + path, { signal: c.signal }); return await r.json(); } finally { clearTimeout(t); }
}
async function connect() {
  const raw = S.ip.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
  if (!/^\d{1,3}(\.\d{1,3}){3}(:\d+)?$/.test(raw)) { say('IP inválido. Exemplo: 192.168.0.12', true); return; }
  base = 'http://' + (raw.includes(':') ? raw : raw + ':8080'); say('Conectando em ' + base + '…');
  try { info = await getJ('/info'); } catch (e) { say('Não achei o celular. Confira se está na mesma rede Wi-Fi, o IP e se o app do celular está aberto.', true); return; }
  try { const z = await getJ('/zoom?r=' + S.zoom); if (z && z.zoom) S.zoom = z.zoom; } catch (e) {}
  save(); start();
}

/* ---------- visualização ---------- */
const canvas = $('#view'), renderer = new THREE.WebGLRenderer({ canvas, antialias: false });
const scene = new THREE.Scene(), camera = new THREE.PerspectiveCamera(90, 1, 0.05, 50), stereo = new THREE.StereoCamera(); scene.add(camera);
const group = new THREE.Group(); scene.add(group); let mesh = null;
function patchGeo() {
  const R = 3, w = R * S.fov * Math.PI / 180, aspect = info && info.w && info.h ? info.w / info.h : 16 / 9, h = w / aspect, span = w / R, N = Math.max(2, Math.ceil(span * 28)), pos = [], uv = [], idx = [];
  for (let i = 0; i <= N; i++) { const a = (i / N - .5) * span, x = R * Math.sin(a), z = -R * Math.cos(a); pos.push(x, h / 2, z, x, -h / 2, z); uv.push(i / N, 1, i / N, 0); if (i < N) { const k = i * 2; idx.push(k, k + 1, k + 2, k + 1, k + 3, k + 2); } }
  const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); g.setIndex(idx); return g;
}
function rebuildGeo() { if (mesh) { mesh.geometry.dispose(); mesh.geometry = patchGeo(); } }
function start() {
  img = new Image(); img.crossOrigin = 'anonymous'; img.src = base + '/stream?t=' + Date.now();
  img.onerror = () => { if (active) stop('Conexão com o celular perdida.'); };
  tex = new THREE.Texture(img); tex.generateMipmaps = false; tex.minFilter = THREE.LinearFilter;
  mesh = new THREE.Mesh(patchGeo(), new THREE.MeshBasicMaterial({ map: tex, side: THREE.DoubleSide, depthTest: false })); mesh.frustumCulled = false; group.add(mesh);
  try { es = new EventSource(base + '/sensor'); es.onmessage = e => { try { const j = JSON.parse(e.data); q = j.q; rot = j.r || 1; } catch (er) {} }; } catch (e) {}
  needCal = true; alpha0 = null; active = true;
  $('#setup').style.display = 'none'; canvas.style.display = 'block'; $('#hud').style.display = 'block'; setTimeout(() => { $('#hud').style.display = 'none'; }, 5000);
  try { document.documentElement.requestFullscreen(); } catch (e) {}
  try { if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) DeviceOrientationEvent.requestPermission(); } catch (e) {}
  resize(); renderer.setAnimationLoop(frame);
}
function stop(msg) {
  active = false; renderer.setAnimationLoop(null);
  if (es) { es.close(); es = null; } if (img) { img.onerror = null; img.src = ''; img = null; }
  if (mesh) { group.remove(mesh); mesh.geometry.dispose(); mesh.material.dispose(); mesh = null; } if (tex) { tex.dispose(); tex = null; }
  canvas.style.display = 'none'; $('#hud').style.display = 'none'; $('#setup').style.display = 'flex'; say(msg || 'Câmera desligada.', !!msg);
}
window.picoBack = () => { if (active) { stop(); return true; } return false; };
canvas.addEventListener('click', () => { const n = Date.now(); if (n - lastTap < 400) { needCal = true; alpha0 = null; } lastTap = n; });
window.addEventListener('deviceorientation', e => { orient = e; });
function resize() { renderer.setSize(innerWidth, innerHeight, false); }
addEventListener('resize', resize);

const eu = new THREE.Euler(), q1 = new THREE.Quaternion(-Math.SQRT1_2, 0, 0, Math.SQRT1_2), q0 = new THREE.Quaternion(), zee = new THREE.Vector3(0, 0, 1), qCur = new THREE.Quaternion(), V = THREE.Vector3;
function headYaw() { const d = new V(); camera.getWorldDirection(d); return Math.atan2(-d.x, -d.z); }
function frame() {
  // orientação do óculos (giroscópio do próprio aparelho)
  if (orient && orient.alpha != null) {
    if (alpha0 === null) alpha0 = orient.alpha;
    const a = (orient.alpha - alpha0) * Math.PI / 180, b = (orient.beta || 0) * Math.PI / 180, g = (orient.gamma || 0) * Math.PI / 180, o = ((screen.orientation && screen.orientation.angle) || window.orientation || 90) * Math.PI / 180;
    eu.set(b, a, -g, 'YXZ'); camera.quaternion.setFromEuler(eu); camera.quaternion.multiply(q1); camera.quaternion.multiply(q0.setFromAxisAngle(zee, -o));
  }
  camera.updateMatrixWorld(true);
  tAcc += 1 / 60; if (tAcc > 1 / 30 && img && img.complete && img.naturalWidth) { tAcc = 0; tex.needsUpdate = true; }
  if (S.mode === 'head' || !q) group.quaternion.copy(camera.quaternion);
  else { // modo janela: orientação do celular (Android rotation vector, ENU) -> three.js
    const qd = new THREE.Quaternion(q[0], q[1], q[2], q[3]), dev = (a, b, c) => new V(a, b, c).applyQuaternion(qd), cv = v => new V(v.x, v.z, -v.y);
    const right = cv(rot === 3 ? dev(0, 1, 0) : dev(0, -1, 0)), up = cv(rot === 3 ? dev(-1, 0, 0) : dev(1, 0, 0)), back = cv(dev(0, 0, 1));
    const qc = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(right, up, back)), fw = new V(0, 0, -1).applyQuaternion(qc);
    if (needCal) { group.userData.yaw = headYaw() - Math.atan2(-fw.x, -fw.z); needCal = false; qCur.copy(qc); }
    const target = new THREE.Quaternion().setFromAxisAngle(new V(0, 1, 0), group.userData.yaw || 0).multiply(qc);
    qCur.slerp(target, 0.35); group.quaternion.copy(qCur);
  }
  const w = renderer.domElement.width, h = renderer.domElement.height;
  if (S.disp === 'split') {
    const hw = w >> 1; camera.fov = 90; camera.aspect = hw / h; camera.updateProjectionMatrix(); stereo.update(camera);
    renderer.setScissorTest(true);
    renderer.setViewport(0, 0, hw, h); renderer.setScissor(0, 0, hw, h); renderer.render(scene, stereo.cameraL);
    renderer.setViewport(hw, 0, hw, h); renderer.setScissor(hw, 0, hw, h); renderer.render(scene, stereo.cameraR);
    renderer.setScissorTest(false);
  } else { camera.fov = 90; camera.aspect = w / h; camera.updateProjectionMatrix(); renderer.setViewport(0, 0, w, h); renderer.render(scene, camera); }
}
