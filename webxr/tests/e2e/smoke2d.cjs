// Fumaça no modo teste 2D: abre cada modo, anda, atira e confere que não há erros de console.
const { serve, launch } = require('./harness.cjs');
const assert = require('node:assert/strict');
const OUT = process.env.SHOTS || '/tmp';
(async () => {
  const { server, url } = await serve(); const browser = await launch();
  const page = await browser.newPage({ viewport: { width: 1000, height: 600 } });
  const errors = []; page.on('pageerror', (e) => errors.push('pageerror: ' + e.message)); page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto(url); await page.waitForFunction(() => window.app);
  assert.ok(await page.textContent('#st-https'), 'status https');
  await page.screenshot({ path: OUT + '/home.png' });
  await page.click('#btn-2d');
  // Pega-Pega
  await page.waitForFunction(() => window.app.modeName === 'pegapega');
  await page.evaluate(() => window.app.ui.close()); await page.waitForTimeout(500);
  await page.keyboard.down('KeyW'); await page.waitForTimeout(1200); await page.keyboard.up('KeyW');
  const pp = await page.evaluate(() => ({ pos: window.app.root.position.toArray(), bots: window.app.mode.bots.length, grounded: window.app.mode.loco.grounded }));
  assert.equal(pp.bots, 5); console.log('pegapega', JSON.stringify(pp));
  // marcação: o pegador encosta -> jogador vira pegador; depois pega um bot -> passa adiante
  await page.evaluate(() => { const m = window.app.mode, it = m.bots.find((b) => b.it), P = m.loco.pos; m.immune = 0; it.x = P[0]; it.z = P[2]; it.y = P[1]; });
  await page.waitForFunction(() => window.app.mode.isIt === true, null, { timeout: 3000 });
  await page.evaluate(() => { const m = window.app.mode, P = m.loco.pos, b = m.bots.find((x) => !x.it && x.frozen <= 0); m.immune = 0; b.x = P[0]; b.z = P[2]; b.y = P[1]; });
  await page.waitForFunction(() => window.app.mode.isIt === false && window.app.mode.tagCount === 1, null, { timeout: 3000 });
  console.log('marcação ok');
  await page.evaluate(() => { window.app.deskYaw = 2.4; window.app.deskPitch = 0.1; }); await page.waitForTimeout(300);
  await page.screenshot({ path: OUT + '/pegapega.png' });
  // FPS
  await page.evaluate(() => window.app.setMode('fps')); await page.evaluate(() => window.app.ui.close());
  await page.waitForTimeout(4500);
  const fps0 = await page.evaluate(() => ({ wave: window.app.mode.wave, en: window.app.mode.enemies.length }));
  assert.ok(fps0.wave >= 1 && fps0.en > 0, 'onda começou ' + JSON.stringify(fps0));
  await page.mouse.move(500, 300); await page.mouse.down(); await page.waitForTimeout(600); await page.mouse.up();
  const fps1 = await page.evaluate(() => ({ mag: window.app.mode.mag, score: window.app.mode.score }));
  assert.ok(fps1.mag < 30, 'atirou ' + JSON.stringify(fps1)); console.log('fps', JSON.stringify(fps0), JSON.stringify(fps1));
  await page.screenshot({ path: OUT + '/fps.png' });
  // menu
  await page.evaluate(() => window.app.openMenu()); await page.waitForTimeout(300);
  await page.screenshot({ path: OUT + '/menu.png' });
  await browser.close(); server.close();
  assert.deepEqual(errors, [], errors.join('\n'));
  console.log('OK smoke2d');
})().catch((e) => { console.error(e); process.exit(1); });
