// Teste do visualizador de ambientes com o APK REAL (caminho em METULAS_APK) e texturas decodificadas (QA_DIR/<env>.json).
const { serve, launch } = require('./harness.cjs');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const APK = process.env.METULAS_APK, QA = process.env.QA_DIR, OUT = process.env.SHOTS || '/tmp';
if (!APK || !fs.existsSync(APK)) { console.log('SKIP viewer: defina METULAS_APK'); process.exit(0); }
(async () => {
  const { server, url } = await serve(); const browser = await launch();
  const page = await browser.newPage({ viewport: { width: 900, height: 560 } });
  const errors = []; page.on('pageerror', (e) => errors.push('pageerror: ' + e.message)); page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto(url); await page.waitForFunction(() => window.app);
  await page.check('input[value=viewer]');
  await page.setInputFiles('#apk-file', APK);
  await page.waitForFunction(() => window.app.apk, null, { timeout: 60000 });
  const info = await page.evaluate(() => window.app.apkInfo);
  assert.equal(info.envs.length, 7, 'ambientes: ' + info.envs); console.log('APK ok:', info.envs.join(','));
  await page.click('#btn-2d'); await page.waitForFunction(() => window.app.modeName === 'viewer');
  const only = (process.env.ENVS || info.envs.join(',')).split(',');
  for (const name of only) {
    const i = info.envs.indexOf(name);
    if (QA && fs.existsSync(path.join(QA, name + '.json'))) await page.evaluate((t) => { window.__QA_TEXTURES = JSON.parse(t); }, fs.readFileSync(path.join(QA, name + '.json'), 'utf8'));
    const t0 = Date.now();
    await page.evaluate((i) => window.app.mode.load(i), i);
    const r = await page.evaluate(() => { const m = window.app.mode; window.app.ui.close(); return { loading: m.loading, info: m.info, y: window.app.root.position.y, floor: m.floorY }; });
    console.log(name, 'carregou em', Date.now() - t0, 'ms', JSON.stringify(r.info), 'rootY', r.y.toFixed(2));
    assert.ok(r.info && r.info.draws > 0, 'sem objetos em ' + name);
    for (const [k, yaw] of [['a', 0], ['b', Math.PI / 2], ['c', Math.PI], ['d', -Math.PI / 2]]) {
      await page.evaluate((y) => { window.app.deskYaw = y; window.app.deskPitch = 0.05; }, yaw); await page.waitForTimeout(250);
      await page.screenshot({ path: `${OUT}/env-${name}-${k}.png` });
    }
  }
  await browser.close(); server.close();
  assert.deepEqual(errors, [], errors.join('\n')); console.log('OK viewer');
})().catch((e) => { console.error(e); process.exit(1); });
