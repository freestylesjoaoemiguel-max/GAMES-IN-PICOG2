import {chromium as playwright} from '@playwright/test';
import chromium from '@sparticuz/chromium';
import fs from 'node:fs';import path from 'node:path';import zlib from 'node:zlib';import {execFileSync} from 'node:child_process';import assert from 'node:assert/strict';import crypto from 'node:crypto';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),libs=path.resolve(root,'../.cache/chromium-libs');fs.mkdirSync(libs,{recursive:true});fs.writeFileSync(path.join(libs,'libs.tar'),zlib.brotliDecompressSync(fs.readFileSync(path.join(root,'node_modules/@sparticuz/chromium/bin/al2023.tar.br'))));execFileSync('tar',['xf',path.join(libs,'libs.tar'),'-C',libs]);process.env.LD_LIBRARY_PATH=path.join(libs,'lib')+(process.env.LD_LIBRARY_PATH?':'+process.env.LD_LIBRARY_PATH:'');
const browser=await playwright.launch({executablePath:await chromium.executablePath(),args:chromium.args.filter(a=>!a.includes('disable-web-security')&&!a.includes('allow-running-insecure-content')&&a!=='--single-process'),headless:true});

const base=process.env.PREVIEW_BASE_URL||'http://127.0.0.1:5180/',passed=[],errors=[];
try{
 const page=await browser.newPage({viewport:{width:1024,height:768}});page.on('pageerror',e=>errors.push(e.stack));
 await page.addInitScript(()=>{
  window.__entry={requests:0,deny:true,activated:false};
  Object.defineProperty(navigator,'xr',{configurable:true,value:{
   isSessionSupported:async type=>type==='immersive-vr',
   requestSession(type){__entry.requests++;__entry.type=type;__entry.activated=navigator.userActivation.isActive;
    if(__entry.deny)return Promise.reject(new Error('Permissao negada para teste'));
    const session=new EventTarget();session.inputSources=[];session.end=async()=>session.dispatchEvent(new Event('end'));return Promise.resolve(session);
   }
  }});
  navigator.getVRDisplays=async()=>{throw Error('WebVR must not be selected when WebXR is available')};
 });
 await page.goto(base);await page.waitForFunction('window.V45&&V45.ready&&PICO.api=== "webxr"',null,{timeout:90000});
 assert.equal(await page.locator('#pico-vr').innerText(),'INICIAR VR');assert(await page.locator('#pico-vr').isVisible());
 assert.equal(await page.locator('#pico-bar').isVisible(),false);assert.equal(await page.locator('#vr').count(),0);
 assert.equal(await page.evaluate('__entry.requests'),0);assert.equal(await page.evaluate('state'),'lobby');
 passed.push('Single primary INICIAR VR button; no floating top mode selector, no session requested before the click');
 await page.screenshot({path:path.join(root,'docs/preview-v45-start.png')});
 await page.click('#pico-vr');await page.waitForFunction('!PICO.starting&&__entry.requests===1');
 assert(await page.evaluate('__entry.activated&&__entry.type==="immersive-vr"'));assert.equal(await page.evaluate('state'),'lobby');
 assert(await page.locator('#pico-status').innerText().then(s=>s.includes('Permissao negada')));assert.equal(await page.locator('#pico-vr').isEnabled(),true);
 passed.push('Click directly requests immersive-vr with user activation; denial leaves lobby intact and permits retry');
 // Stub only the XR manager/compositor boundary. Game start and button handlers are real.
 await page.evaluate(()=>{__entry.deny=false;R.xr.setSession=async()=>{};R.setAnimationLoop=()=>{};});
 await page.click('#pico-vr');await page.waitForFunction('PICO.presenting&&!PICO.starting');
 assert(await page.evaluate('PICO.mode==="webxr"&&state==="play"&&!vrView&&!vrOn&&__entry.requests===2&&__entry.activated'));
 assert.equal(await page.locator('#vr-entry').isVisible(),false);passed.push('One main click starts game in WebXR after acceptance, never flat or VR Box, and hides launch overlay');
 await page.evaluate(()=>PICO.stop());await page.waitForFunction('!PICO.presenting');assert(await page.locator('#pico-vr').isVisible());assert(await page.locator('#pico-vr').isEnabled());
 await page.evaluate(()=>{let resolve;__entry.pending=new Promise(r=>resolve=r);__entry.resolve=resolve;navigator.xr.requestSession=()=>{__entry.requests++;return __entry.pending};PICO.start();PICO.start()});
 assert.equal(await page.evaluate('__entry.requests'),3);assert(await page.evaluate('PICO.starting'));assert.equal(await page.locator('#pico-vr').isEnabled(),false);
 await page.evaluate(()=>{const s=new EventTarget();s.inputSources=[];s.end=async()=>s.dispatchEvent(new Event('end'));__entry.resolve(s)});await page.waitForFunction('PICO.presenting&&!PICO.starting');
 passed.push('Exiting restores INICIAR VR; repeated clicks during permission prompt cannot create duplicate sessions');
 await page.evaluate(()=>PICO.stop());
 await page.evaluate(async()=>{navigator.xr.isSessionSupported=async()=>false;navigator.getVRDisplays=async()=>[];await PICO.detect()});assert.equal(await page.locator('#pico-vr').isEnabled(),false);assert(await page.locator('#pico-status').innerText().then(s=>s.includes('Sem API VR')));
 passed.push('Unsupported browsers show clear status and cannot silently start fake VR');
 await page.locator('.tab[data-t="set"]').evaluate(b=>{document.getElementById('lobby').style.display='';document.body.classList.add('inlobby');b.click()});await page.locator('[data-g="ctl"]').first().click();
 assert(await page.locator('#pico-bar').isVisible());assert(await page.locator('#qj').isVisible());assert(await page.locator('#pico-solo').isVisible());
 passed.push('Screen play, online rooms and controller diagnostics remain in Settings > Controls, not a top overlay');
 await page.setViewportSize({width:390,height:844});
 await page.locator('.tab[data-t="play"]').evaluate(b=>b.click());
 const box=await page.locator('#pico-vr').boundingBox();assert(box&&box.x>=0&&box.y>=0&&box.x+box.width<=390&&box.y+box.height<=844);
 passed.push('Primary launch control stays inside mobile viewport');
 assert.deepEqual(errors,[]);const report={passed,errors,limitations:'Chromium with mocked WebXR session/manager boundary; real UI, boot and game start. No physical headset or real compositor test.'};fs.writeFileSync(path.join(root,'docs/ENTRY_TESTS.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{await browser.close()}
