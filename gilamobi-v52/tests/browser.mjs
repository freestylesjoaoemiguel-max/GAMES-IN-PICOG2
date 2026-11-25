import {createRequire} from 'node:module';
import fs from 'node:fs';import path from 'node:path';import zlib from 'node:zlib';import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url),{chromium:pw}=require('@playwright/test'),chromium=require('@sparticuz/chromium');
const ROOT=path.resolve(import.meta.dirname,'..'), CACHE=path.resolve(ROOT,'../.cache/gila-browser');fs.mkdirSync(CACHE,{recursive:true});
const libs=CACHE+'/libs';fs.mkdirSync(libs,{recursive:true});fs.writeFileSync(libs+'/libs.tar',zlib.brotliDecompressSync(fs.readFileSync(path.join(path.dirname(require.resolve('@sparticuz/chromium/package.json')),'bin/al2023.tar.br'))));execFileSync('tar',['xf',libs+'/libs.tar','-C',libs]);process.env.LD_LIBRARY_PATH=libs+'/lib';
const browser=await pw.launch({executablePath:await chromium.executablePath(),args:chromium.args.filter(a=>!a.includes('disable-web-security')&&!a.includes('allow-running-insecure-content')&&a!=='--single-process'),headless:true});
const base=process.env.PREVIEW_BASE_URL||'http://127.0.0.1:5192/';
const passed=[],failed=[],messages=[],warnings=[];
const check=(ok,label)=>{(ok?passed:failed).push(label);console.log(ok?'PASS':'FAIL',label);};
let page;
const wait=async(fn)=>page.waitForFunction(fn,null,{timeout:60000});
const diag=()=>page.evaluate(()=>JSON.parse(gila.diagnostics));
const cmd=async(action)=>page.evaluate(a=>gila.command(a),action);
try {
 page=await browser.newPage({viewport:{width:480,height:360}});
 page.on('pageerror',e=>failed.push('JavaScript exception: '+e.message));
 page.on('console',m=>{const s=m.text();messages.push(s);if(m.type()==='warning')warnings.push(s);if(/SCRIPT ERROR|Parse Error|Failed to load script|GL_INVALID_(OPERATION|FRAMEBUFFER)/.test(s))failed.push(s);});
 await page.addInitScript({path:path.join(path.dirname(require.resolve('iwer')),'../build/iwer.js')});
 await page.addInitScript(()=>{
  // TEST ONLY. Never shipped to web/ or Pages; this is not Pico hardware.
  window.device=new IWER.XRDevice(IWER.metaQuest3,{stereoEnabled:true});
  device.installRuntime({forceInstall:true,polyfillLayers:false});
  device.controllers.left.connected=false;device.position.set(0,1.6,0);
  const request=navigator.xr.requestSession.bind(navigator.xr);
  navigator.xr.requestSession=async(...args)=>{window.requestArgs=args;const session=await request(...args);window.session=session;return session;};
 });
 await page.goto(base);await wait(()=>window.gila?.ready);
 check(true,'Actual v52 PCK and WASM boot in WebGL2');
 await page.locator('#g2-name').fill('Pico verde');await page.locator('#g2-save-name').click();
 await wait(()=>gila.nickname==='Pico verde');
 check(true,'Lobby input saves custom player name through Godot profile settings');
 await page.getByText('INICIAR VR',{exact:true}).click();
 await wait(()=>gila.immersive&&gila.tracked===1);
 check(await page.evaluate(()=>requestArgs[0]==='immersive-vr'),'Button requests real immersive-vr API');
 check(await page.evaluate(()=>gila.layersPolyfilled),'Legacy base-layer path exercised through Layers polyfill');
 let d=await diag();check(!d.tracked[0]&&d.tracked[1]&&!d.arm_visible[0]&&d.arm_visible[1],'Only connected right hand visible and active');
 check(await page.evaluate(()=>JSON.parse(gila.presence).vr_pico_g2===true),'VR session publishes Pico identity');
 check(d.scale===1,'XR bypasses broken Godot 4.4 scaled multiview post pass');
 await page.waitForTimeout(1000);await page.screenshot({path:CACHE+'/stereo.png'});
 const beforeStroke=await page.evaluate(()=>JSON.parse(gila.position));let touched=false;
 for(const angle of [.25,.36,.44,.52,.60]){
  await page.evaluate(a=>device.controllers.right.quaternion.set(-Math.sin(a/2),0,0,Math.cos(a/2)),angle);
  await page.waitForTimeout(300);touched=touched||await page.evaluate(()=>gila.contacts>0);
 }
 const afterStroke=await page.evaluate(()=>JSON.parse(gila.position));
 check(touched&&Math.abs(afterStroke[2]-beforeStroke[2])>.03,'Estimated 3DoF stroke contacts actual floor and moves original player');
 await page.evaluate(()=>device.controllers.right.quaternion.set(0,0,0,1));await page.waitForTimeout(500);
 check(await page.evaluate(()=>gila.contacts===0),'Lifting estimated hand releases surface support');
 for(const height of [0,1.6,2.8]){
  await page.evaluate(h=>device.position.y=h,height);await page.waitForTimeout(450);
  check(await page.evaluate(()=>Math.abs(JSON.parse(gila.diagnostics).head[1]-JSON.parse(gila.position)[1]-1.36)<.03),'3DoF eye anchor handles runtime height '+height);
 }
 await page.evaluate(()=>device.position.y=1.6);
 await cmd('base');await page.waitForTimeout(2200);
 await page.evaluate(()=>device.quaternion.set(0,Math.sin(.25),0,Math.cos(.25)));
 await wait(()=>Math.abs(JSON.parse(gila.diagnostics).head_forward[0])>.35);
 check(true,'Head orientation follows runtime pose');
 await page.evaluate(()=>device.controllers.left.connected=true);await wait(()=>gila.tracked===2);
 check((await diag()).arm_visible.every(Boolean),'Two real fixture sources enable both skinned arms');
 await page.evaluate(()=>device.controllers.right.connected=false);await wait(()=>gila.tracked===1&&!JSON.parse(gila.diagnostics).tracked[1]);
 check(!(await diag()).arm_visible[1],'Controller disconnect hides its arm');
 await page.evaluate(()=>{device.quaternion.set(0,0,0,1);gila.force3dof=false;device.controllers.left.position.set(0,1.4,-.4);});
 await page.waitForTimeout(400);
 await page.evaluate(()=>device.controllers.left.updateButtonValue('trigger',1));
 await wait(()=>JSON.parse(gila.diagnostics).menu);
 await page.evaluate(()=>device.controllers.left.updateButtonValue('trigger',0));
 await page.waitForTimeout(400);check((await diag()).menu,'Long trigger opens menu, release does not close it');
 // Aim the actual input ray at the world-space Continuar button.
 await page.evaluate(()=>{
  const x=(85/1280-.5)*2.1,y=1.6+(.5-690/900)*1.4765625-1.4,z=-1.7+.4;
  const length=Math.hypot(x,y,z),yaw=Math.atan2(-x,-z),pitch=Math.asin(y/length);
  device.controllers.left.quaternion.set(Math.sin(pitch/2)*Math.cos(yaw/2),Math.cos(pitch/2)*Math.sin(yaw/2),-Math.sin(pitch/2)*Math.sin(yaw/2),Math.cos(pitch/2)*Math.cos(yaw/2));
 });
 await page.waitForTimeout(500);await page.evaluate(()=>device.controllers.left.updateButtonValue('trigger',1));
 await page.waitForTimeout(200);await page.evaluate(()=>device.controllers.left.updateButtonValue('trigger',0));
 await wait(()=>!JSON.parse(gila.diagnostics).menu);
 check(true,'Controller ray clicks actual world-space menu button');
 await page.evaluate(()=>{gila.force3dof=true;device.controllers.left.quaternion.set(0,0,0,1);});
 for(const name of ['backrooms','sandbox','base']){
  await cmd(name);await page.waitForTimeout(2200);await wait(()=>!JSON.parse(gila.diagnostics).menu&&JSON.parse(gila.diagnostics).running);
  check(await page.evaluate(n=>gila.map===n,name),'Original map switches inside immersive session: '+name);
 }
 await cmd('sandbox');await page.waitForTimeout(2200);await cmd('grinner');await wait(()=>JSON.parse(gila.diagnostics).role==='grinner');
 check((await diag()).tracked[0]&&(await diag()).arm_visible[0],'Grinner role rebuild retains XR and connected arm');
 await cmd('chainsaw');await cmd('grab');await cmd('normal');await wait(()=>JSON.parse(gila.diagnostics).role==='normal');
 check(true,'Original Grinner tool commands and normal role switch execute');
 await page.evaluate(()=>device.controllers.left.connected=false);await wait(()=>gila.tracked===0);
 check((await diag()).arm_visible.every(v=>!v),'No sources means no invented or supporting hands');
 await page.evaluate(()=>session.end());await wait(()=>!gila.immersive);await page.waitForTimeout(1800);
 check(true,'Ending session restores mono target without multiview framebuffer errors');
 await page.getByText('INICIAR VR',{exact:true}).click();await wait(()=>gila.immersive);
 await page.evaluate(()=>device.controllers.right.connected=true);await wait(()=>gila.tracked===1);
 check(true,'Session can be started again and controller reconnected');
 await page.evaluate(()=>session.end());await wait(()=>!gila.immersive);
 await page.screenshot({path:CACHE+'/after-exit.png'});
 await page.close();
 page=await browser.newPage({viewport:{width:480,height:360},acceptDownloads:true});
 page.on('pageerror',e=>failed.push('Flat page exception: '+e.message));
 page.on('console',m=>{messages.push(m.text());if(/SCRIPT ERROR|Parse Error/.test(m.text()))failed.push(m.text());});
 await page.goto(base);await wait(()=>window.gila?.ready);
 check(await page.locator('#g2-vr').isDisabled(),'Unsupported desktop runtime cannot enter fake VR');
 await cmd('sandbox');await page.waitForTimeout(2200);
 await cmd('camera');await cmd('record');await page.waitForTimeout(1600);
 const download=page.waitForEvent('download',{timeout:15000});await cmd('record');
 const file=await download;await file.saveAs(CACHE+'/camera.avi');
 const avi=fs.readFileSync(CACHE+'/camera.avi');
 check(avi.length>1000&&avi.toString('ascii',0,4)==='RIFF'&&avi.toString('ascii',8,12)==='AVI ','Original camera produces an actual browser-downloadable AVI');
} catch(e){failed.push(e.stack||String(e));}
finally {await browser.close();}
const result={runtime:'Godot export + Chromium software WebGL2 + IWER 2.8.0 stereo fixture (NOT a headset)',base,passed,failed,warnings:[...new Set(warnings)],physical_pico_g2_tested:false,performance_benchmark:false};
fs.writeFileSync(ROOT+'/docs/BROWSER_TESTS.json',JSON.stringify(result,null,2)+'\n');fs.writeFileSync(CACHE+'/console.log',messages.join('\n'));
console.log(JSON.stringify(result,null,2));if(failed.length)process.exitCode=1;
