import {createRequire} from 'node:module';
import fs from 'node:fs';import path from 'node:path';import zlib from 'node:zlib';
import {execFileSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(path.join(root,'../gorilla-rush-v45/package.json'));
const {chromium:pw}=require('@playwright/test'),chromium=require('@sparticuz/chromium');
const libs=path.resolve(root,'../.cache/chromium-libs');fs.mkdirSync(libs,{recursive:true});
fs.writeFileSync(libs+'/libs.tar',zlib.brotliDecompressSync(fs.readFileSync(path.join(path.dirname(require.resolve('@sparticuz/chromium/package.json')),'bin/al2023.tar.br'))));
execFileSync('tar',['xf',libs+'/libs.tar','-C',libs]);process.env.LD_LIBRARY_PATH=libs+'/lib'+(process.env.LD_LIBRARY_PATH?':'+process.env.LD_LIBRARY_PATH:'');
const browser=await pw.launch({executablePath:await chromium.executablePath(),args:chromium.args.filter(a=>!a.includes('disable-web-security')&&!a.includes('allow-running-insecure-content')&&a!=='--single-process'),headless:true});
const passed=[],errors=[],knownWarnings=[];const fixture=process.env.CONTROLLER_FIXTURE==='1';
try {
 const page=await browser.newPage({viewport:{width:480,height:360}});
 page.on('console',m=>{
   console.log(m.type(),m.text());
   if(m.type()==='error') {
     // Existing binary scene resources create five unbound viewport textures
     // before their _ready hooks bind them. Track, don't suppress the warnings.
     if(m.text().includes('Viewport Texture must be set to use it.') || /at: get_(width|height) \(scene\/main\/viewport\.cpp:/.test(m.text())) knownWarnings.push(m.text());
     else errors.push(m.text());
   }
 });
 page.on('pageerror',e=>errors.push(e.message));
 page.on('requestfailed',r=>errors.push(r.url()+': '+r.failure()?.errorText));
 await page.goto(process.env.PREVIEW_BASE_URL||'http://127.0.0.1:5190/opensaber/');
 await page.waitForFunction(()=>window.opensaber?.ready && !document.querySelector('#status'),null,{timeout:120000});
 passed.push('Supplied Godot 4.3 engine/PCK boot from a subdirectory with web security enabled');
 if(fixture) {
   await page.waitForFunction(()=>window.opensaber.fixtureResult,null,{timeout:30000});
   const result=await page.evaluate(()=>JSON.parse(window.opensaber.fixtureResult));
   assert.deepEqual(result.failures,[]);passed.push(...result.passed);
 } else {
   assert.equal(await page.evaluate(()=>window.opensaber.vrSupported),false);
   assert(await page.locator('#enter-vr').isDisabled());
   passed.push('No immersive support: honest screen-only message and disabled VR entry');
   await page.locator('[data-command="right"]').click();
   await page.waitForFunction(()=>window.opensaber.state==='playing' && window.opensaber.audioTime>5,null,{timeout:90000});
   let state=await page.evaluate(()=>({...window.opensaber,command:undefined}));
   assert(state.audioPlaying && state.notesRemaining<37);
   passed.push('Right-hand chart loads original synthesized Ogg and spawns notes in the supplied game');
   await page.locator('[data-command="pause"]').click();
   await page.waitForFunction(()=>window.opensaber.state==='paused'&&!window.opensaber.audioPlaying);
   passed.push('Pause stops music and opens the original pause state');
   await page.locator('[data-command="resume"]').click();
   await page.waitForFunction(()=>window.opensaber.state==='playing'&&window.opensaber.audioTime>5);
   passed.push('Resume continues the same track');
   // Software GPU + single-thread audio can run much slower than wall time.
   try { await page.waitForFunction(()=>window.opensaber.state==='complete',null,{timeout:300000}); }
   catch(e) { console.error('Last game state',await page.evaluate(()=>({...window.opensaber,command:undefined})));throw e; }
   state=await page.evaluate(()=>({...window.opensaber,command:undefined}));
   assert.equal(state.notesRemaining,0);assert.equal(state.audioPlaying,false);
   passed.push('Full 52-second training reaches original score/end menu with all 37 notes consumed');
   assert(state.points>0);passed.push('Real saber/note collisions award points (desktop fixture, not physical controller)');
   for(const mode of ['left','both']) {
     await page.locator(`[data-command="${mode}"]`).click();
     await page.waitForFunction(()=>window.opensaber.state==='playing'&&window.opensaber.audioTime>1,null,{timeout:30000});
     await page.locator('[data-command="menu"]').click();
     await page.waitForFunction(()=>window.opensaber.state==='menu'&&!window.opensaber.audioPlaying);
     passed.push(`${mode} chart starts; return to menu stops playback`);
   }
   await page.reload();
   await page.waitForFunction(()=>window.opensaber?.ready&&!document.querySelector('#status'),null,{timeout:90000});
   passed.push('Reload with persisted browser files boots and keeps training available');
 }
 assert.deepEqual(errors,[]);assert(knownWarnings.length<=40);
 const report={publishedPackSha256:JSON.parse(fs.readFileSync(path.join(root,'docs/BUILD.json'),'utf8')).pck_sha256,passed,errors,knownWarnings,limitations:['Headless software-GPU Chromium; not a Pico G2 performance measurement.','No physical headset, native compositor, controller hardware, audio quality or installation validation.','Controller fixture uses Godot XRServer sources, not an actual browser WebXR session.','Binary supplied scenes still emit unbound ViewportTexture startup warnings before binding.']};
 fs.writeFileSync(path.join(root,'docs',fixture?'CONTROLLER_TESTS.json':'BROWSER_TESTS.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify(report,null,2));
} finally {await browser.close();}
