// Teste de sessão VR real (immersive-vr) num emulador WebXR (IWER) configurado como UM controle estilo Pico G2:
// gatilho + touchpad (2 eixos) + 2 botões. Verifica: sessão, detecção de entrada, UI por raio, andar, braço virtual.
const { serve, launch, ROOT } = require('./harness.cjs');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const OUT = process.env.SHOTS || '/tmp';
const IWER = fs.readFileSync(path.join(ROOT, 'node_modules/iwer/build/iwer.min.js'), 'utf8');
const INIT = `${IWER}
(() => {
  const cfg = JSON.parse(JSON.stringify(IWER.metaQuest3));
  cfg.name = 'Pico G2 (emulado)';
  const mk = (hand) => ({ gamepad: { mapping: '', buttons: [{ id: 'trigger', type: 'analog', eventTrigger: 'select' }, null, { id: 'thumbstick', type: 'binary' }, { id: 'a-button', type: 'binary' }], axes: [{ id: 'thumbstick', type: 'x-axis' }, { id: 'thumbstick', type: 'y-axis' }] }, gripOffsetMatrix: [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1], numHapticActuators: 0 });
  cfg.controllerConfig = { profileId: 'pico-g2-emu', fallbackProfileIds: ['generic-trigger-squeeze-touchpad'], layout: { right: mk('right') } };
  const dev = new IWER.XRDevice(cfg);
  dev.installRuntime({ globalObject: window, forceInstall: true });
  window.__dev = dev;
})();`;
(async () => {
  const { server, url } = await serve(); const browser = await launch();
  const ctx = await browser.newContext({ viewport: { width: 900, height: 600 } }); await ctx.addInitScript(INIT);
  const page = await ctx.newPage();
  const errors = []; page.on('pageerror', (e) => errors.push('pageerror: ' + e.message)); page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto(url); await page.waitForFunction(() => window.app && window.app.support, null, { timeout: 20000 });
  assert.equal(await page.evaluate(() => window.app.support.vr), true, 'immersive-vr suportado no emulador');
  await page.evaluate(() => { const c = window.__dev.controllers.right; c.connected = true; });
  await page.check('input[value=pegapega]');
  await page.click('#btn-vr');
  await page.waitForFunction(() => window.app.inSession, null, { timeout: 15000 });
  await page.waitForFunction(() => window.app.hands[0].connected || window.app.hands[1].connected, null, { timeout: 8000 });
  await page.waitForTimeout(500);
  const diag = await page.evaluate(() => window.app.diagnostics());
  console.log(diag); assert.match(diag, /pico-g2-emu/); assert.match(diag, /botões\(4\)/); assert.match(diag, /eixos\(2\)/);
  assert.equal(await page.evaluate(() => window.app.modeName), 'pegapega');

  // --- UI por raio: abre o menu e clica "Operation Zero" apontando o controle ---
  const aimAt = (sel) => page.evaluate((sel) => {
    const app = window.app, ui = app.ui, p = ui.panel; const it = p.items.find((x) => x.b.label.includes(sel)); if (!it) return 'sem botão ' + sel;
    const u = (it.x + it.w / 2) / 1024, v = (it.y + it.h / 2) / 1024;   // fração da textura 1024x1024
    const g = p.mesh.geometry, w = p.widthM, h = w * p.usedH / 1024;
    const local = new p.mesh.position.constructor((u - 0.5) * w, (h / 2) - (it.y + it.h / 2) / 1024 * w, 0);
    p.mesh.updateMatrixWorld(true); const world = local.applyMatrix4(p.mesh.matrixWorld);
    const dev = window.__dev, o = dev.controllers.right.position; const origin = [0.2, dev.position.y - 0.4, -0.2];
    const d = [world.x - origin[0], world.y - origin[1], world.z - origin[2]]; const L = Math.hypot(...d);
    const f = [d[0] / L, d[1] / L, d[2] / L];
    // quaternion que leva (0,0,-1) a f
    const from = [0, 0, -1], dot = from[0] * f[0] + from[1] * f[1] + from[2] * f[2]; const ax = [from[1] * f[2] - from[2] * f[1], from[2] * f[0] - from[0] * f[2], from[0] * f[1] - from[1] * f[0]];
    const q = [ax[0], ax[1], ax[2], 1 + dot]; const ql = Math.hypot(...q); const qn = q.map((x) => x / ql);
    dev.controllers.right.position.set(...origin); dev.controllers.right.quaternion.set(...qn);
    return 'ok';
  }, sel);
  await page.evaluate(() => window.app.openMenu()); await page.waitForTimeout(400);
  assert.equal(await aimAt('Operation Zero'), 'ok'); await page.waitForTimeout(400);
  const hover = await page.evaluate(() => window.app.ui.panel.hover); assert.ok(hover >= 0, 'raio do controle acerta o botão (hover=' + hover + ')');
  await page.screenshot({ path: OUT + '/xr-menu.png' });
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('trigger', 1)); await page.waitForTimeout(300);
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('trigger', 0)); await page.waitForTimeout(800);
  assert.equal(await page.evaluate(() => window.app.modeName), 'fps', 'gatilho clicou no botão do menu');
  await page.evaluate(() => window.app.ui.close());

  // --- andar com o touchpad (eixo Y negativo = frente) ---
  await page.evaluate(() => { const c = window.__dev.controllers.right; c.position.set(0.2, 1.2, -0.3); c.quaternion.set(0, 0, 0, 1); });
  await page.waitForTimeout(300);
  const p0 = await page.evaluate(() => window.app.root.position.toArray());
  await page.evaluate(() => window.__dev.controllers.right.updateAxes('thumbstick', 0, -1)); await page.waitForTimeout(500);
  console.log('axes lidos', JSON.stringify(await page.evaluate(() => { const h = window.app.hands.find((x) => x.connected); return { axes: h.axes, pair: h.pair, stick: h.stick, ui: window.app.ui.isOpen }; })));
  await page.waitForTimeout(500);
  await page.evaluate(() => window.__dev.controllers.right.updateAxes('thumbstick', 0, 0));
  const p1 = await page.evaluate(() => window.app.root.position.toArray());
  console.log('andar', p0.map((v) => v.toFixed(2)), '->', p1.map((v) => v.toFixed(2)));
  assert.ok(p0[2] - p1[2] > 1.0, 'andou para frente (-z) com o touchpad: ' + (p0[2] - p1[2]));
  // atirar: gatilho gasta munição
  const m0 = await page.evaluate(() => window.app.mode.mag);
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('trigger', 1)); await page.waitForTimeout(500);
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('trigger', 0));
  const m1 = await page.evaluate(() => window.app.mode.mag); assert.ok(m1 < m0, 'atirou (munição ' + m0 + '->' + m1 + ')');
  await page.screenshot({ path: OUT + '/xr-fps.png' });

  // botão extra (índice 3) recarrega; vira "alt"
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('a-button', 1)); await page.waitForTimeout(200);
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('a-button', 0));
  assert.ok(await page.evaluate(() => window.app.mode.reload > 0 || window.app.mode.mag === 30), 'botão extra recarrega');

  // --- Pega-Pega com braço virtual (3DoF): agacha e empurra o chão para trás ---
  await page.evaluate(() => window.app.setMode('pegapega')); await page.evaluate(() => { window.app.ui.close(); window.app.settings.forceArm = true; });
  await page.evaluate(() => { window.__dev.position.set(0, 0.9, 0); });
  await page.waitForTimeout(1500);
  const q0 = await page.evaluate(() => window.app.root.position.toArray());
  // varre o braço de "à frente-baixo" até "baixo-atrás" repetidamente
  for (let k = 0; k < 4; k++) {
    for (let s = 0; s <= 10; s++) {
      const pitch = -0.3 - s * 0.12;   // radianos para baixo
      await page.evaluate((pitch) => { const c = window.__dev.controllers.right; c.quaternion.set(Math.sin(pitch / 2), 0, 0, Math.cos(pitch / 2)); }, pitch);
      await page.waitForTimeout(40);
    }
    await page.evaluate(() => { const c = window.__dev.controllers.right; c.quaternion.set(Math.sin(-0.15), 0, 0, Math.cos(-0.15)); }); await page.waitForTimeout(150);
  }
  const q1 = await page.evaluate(() => ({ p: window.app.root.position.toArray(), touching: window.app.mode.loco.touching, g: window.app.mode.loco.grounded }));
  console.log('pega-pega braço', q0.map((v) => v.toFixed(2)), '->', q1.p.map((v) => v.toFixed(2)), JSON.stringify(q1.touching));
  assert.ok(Math.hypot(q1.p[0] - q0[0], q1.p[2] - q0[2]) > 0.2, 'o corpo se moveu empurrando o chão com o braço');
  await page.screenshot({ path: OUT + '/xr-pega.png' });

  // sair
  await page.evaluate(() => window.app.exitVR()); await page.waitForFunction(() => !window.app.inSession, null, { timeout: 5000 });
  await browser.close(); server.close();
  assert.deepEqual(errors, [], errors.join('\n')); console.log('OK xr');
})().catch((e) => { console.error(e); process.exit(1); });
