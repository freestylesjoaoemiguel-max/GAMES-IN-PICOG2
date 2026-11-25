// Ambientes dentro de uma sessão VR emulada (1 controle estilo G2), com o APK real escolhido na página 2D.
const { serve, launch, ROOT } = require('./harness.cjs');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const APK = process.env.METULAS_APK, OUT = process.env.SHOTS || '/tmp';
if (!APK || !fs.existsSync(APK)) { console.log('SKIP xr-viewer: defina METULAS_APK'); process.exit(0); }
const IWER = fs.readFileSync(path.join(ROOT, 'node_modules/iwer/build/iwer.min.js'), 'utf8');
const INIT = `${IWER}
(() => { const cfg = JSON.parse(JSON.stringify(IWER.metaQuest3));
  cfg.controllerConfig = { profileId: 'pico-g2-emu', fallbackProfileIds: [], layout: { right: { gamepad: { mapping: '', buttons: [{ id: 'trigger', type: 'analog', eventTrigger: 'select' }, null, { id: 'thumbstick', type: 'binary' }], axes: [{ id: 'thumbstick', type: 'x-axis' }, { id: 'thumbstick', type: 'y-axis' }] }, gripOffsetMatrix: [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1], numHapticActuators: 0 } } };
  const dev = new IWER.XRDevice(cfg); dev.installRuntime({ globalObject: window, forceInstall: true }); window.__dev = dev; })();`;
(async () => {
  const { server, url } = await serve(); const browser = await launch();
  const ctx = await browser.newContext({ viewport: { width: 900, height: 600 } }); await ctx.addInitScript(INIT);
  const page = await ctx.newPage(); const errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message)); page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto(url); await page.waitForFunction(() => window.app && window.app.support);
  await page.evaluate(() => { window.__dev.controllers.right.connected = true; });
  await page.check('input[value=viewer]'); await page.setInputFiles('#apk-file', APK);
  await page.waitForFunction(() => window.app.apk, null, { timeout: 60000 });
  await page.click('#btn-vr'); await page.waitForFunction(() => window.app.inSession, null, { timeout: 15000 });
  await page.waitForFunction(() => window.app.modeName === 'viewer' && window.app.mode.info, null, { timeout: 60000 });
  const info = await page.evaluate(() => window.app.mode.info); console.log('em VR carregou', JSON.stringify(info));
  assert.ok(info.tris > 1000);
  // painel do ambiente visível; apontar para "Próximo" e clicar
  await page.waitForTimeout(500);
  await page.evaluate(() => { const app = window.app, p = app.ui.panel, it = p.items.find((x) => x.b.label.includes('Próximo')); const w = p.widthM, h = w * p.usedH / 1024;
    const local = new p.mesh.position.constructor((it.x + it.w / 2) / 1024 * w - w / 2, h / 2 - (it.y + it.h / 2) / 1024 * w, 0); p.mesh.updateMatrixWorld(true); const world = local.applyMatrix4(p.mesh.matrixWorld);
    const dev = window.__dev, o = [0.2, 1.2, -0.2], r = app.root.position, d = [world.x - (o[0] + r.x), world.y - (o[1] + r.y), world.z - (o[2] + r.z)], L = Math.hypot(...d), f = d.map((x) => x / L), dot = -f[2];
    const ax = [0 * f[2] - -1 * f[1], -1 * f[0] - 0 * f[2], 0], q = [ax[0], ax[1], ax[2], 1 + dot], ql = Math.hypot(...q);
    dev.controllers.right.position.set(...o); dev.controllers.right.quaternion.set(...q.map((x) => x / ql)); });
  await page.waitForTimeout(300);
  console.log('hover', await page.evaluate(() => JSON.stringify({ h: window.app.ui.panel.hover, loading: window.app.mode.loading, pos: window.app.ui.panel.mesh.position.toArray(), cam: window.app.camera.getWorldPosition(new window.app.root.position.constructor()).toArray() })));
  const before = await page.evaluate(() => window.app.mode.info.name);
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('trigger', 1)); await page.waitForTimeout(300);
  await page.evaluate(() => window.__dev.controllers.right.updateButtonValue('trigger', 0));
  await page.waitForFunction((b) => window.app.mode.info && window.app.mode.info.name !== b, before, { timeout: 30000 });
  const after = await page.evaluate(() => window.app.mode.info.name); console.log('trocou de ambiente:', before, '->', after); assert.notEqual(before, after);
  // andar com touchpad dentro do ambiente
  await page.evaluate(() => { window.app.ui.close(); const c = window.__dev.controllers.right; c.position.set(0.2, 1.2, -0.3); c.quaternion.set(0, 0, 0, 1); });
  await page.waitForTimeout(300); const p0 = await page.evaluate(() => window.app.root.position.toArray());
  await page.evaluate(() => window.__dev.controllers.right.updateAxes('thumbstick', 0, -1)); await page.waitForTimeout(1000);
  await page.evaluate(() => window.__dev.controllers.right.updateAxes('thumbstick', 0, 0));
  const p1 = await page.evaluate(() => window.app.root.position.toArray()); console.log('andar', p0.map((v) => v.toFixed(2)), '->', p1.map((v) => v.toFixed(2)));
  assert.ok(Math.hypot(p1[0] - p0[0], p1[2] - p0[2]) > 1.5, 'andou no ambiente');
  await page.screenshot({ path: OUT + '/xr-viewer.png' });
  await page.evaluate(() => window.app.exitVR()); await page.waitForFunction(() => !window.app.inSession, null, { timeout: 5000 });
  await browser.close(); server.close(); assert.deepEqual(errors, [], errors.join('\n')); console.log('OK xr-viewer');
})().catch((e) => { console.error(e); process.exit(1); });
