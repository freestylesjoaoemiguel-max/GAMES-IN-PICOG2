import {chromium as playwright} from '@playwright/test';
import chromium from '@sparticuz/chromium';
import fs from 'node:fs';import path from 'node:path';import zlib from 'node:zlib';import {execFileSync} from 'node:child_process';import assert from 'node:assert/strict';import crypto from 'node:crypto';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),libs=path.resolve(root,'../.cache/chromium-libs');fs.mkdirSync(libs,{recursive:true});fs.writeFileSync(path.join(libs,'libs.tar'),zlib.brotliDecompressSync(fs.readFileSync(path.join(root,'node_modules/@sparticuz/chromium/bin/al2023.tar.br'))));execFileSync('tar',['xf',path.join(libs,'libs.tar'),'-C',libs]);process.env.LD_LIBRARY_PATH=path.join(libs,'lib')+(process.env.LD_LIBRARY_PATH?':'+process.env.LD_LIBRARY_PATH:'');
const browser=await playwright.launch({executablePath:await chromium.executablePath(),args:chromium.args.filter(a=>!a.includes('disable-web-security')&&!a.includes('allow-running-insecure-content')&&a!=='--single-process'),headless:true});

const base=process.env.PREVIEW_BASE_URL||'http://127.0.0.1:5177/',passed=[],errors=[];
try{
 // Preserve the 4:3 projection but reduce software-GPU work in CI. No runtime
 // resolution, physics thresholds, gesture assertions or pixel-readback checks change.
 const page=await browser.newPage({viewport:{width:640,height:480}});page.on('pageerror',e=>errors.push(e.stack||e.message));
 await page.goto(base);await page.waitForFunction('pl&&mapDone');await page.click('#solo');await page.waitForFunction("state==='play'");
 // Browser integration fixture: real game/render/skin, mocked XRFrame, not a physical immersive session.
 await page.evaluate(()=>{
  window.__xr={q:[0,0,0,1],pressed:false,lost:false};PLATFORM.isPico=false;SETTINGS.trackingMode='auto';
  const source={targetRayMode:'tracked-pointer',targetRaySpace:{},handedness:'none',profiles:['generic-trigger-touchpad'],gamepad:{mapping:'xr-standard',get buttons(){return [{pressed:__xr.pressed,value:__xr.pressed?1:0}]},axes:[0,0]}};
  INPUT.xrSources=[source];VRP.mode='webxr';VRP.presenting=true;R.xr.getReferenceSpace=()=>({});
  VRP.xrFrame={getPose(){return __xr.lost?null:{emulatedPosition:true,transform:{orientation:{x:__xr.q[0],y:__xr.q[1],z:__xr.q[2],w:__xr.q[3]},position:{x:0,y:0,z:0}}}},getViewerPose(){return {transform:{orientation:{x:0,y:0,z:0,w:1}}}}};
  P.set(0,100,0);vel.set(0,0,0);TUNE.grav=0;LOCO.reset();
 });
 await page.waitForFunction('INPUT.ctrl.present&&LOCO.ready');await page.waitForTimeout(250);
 assert.equal(await page.evaluate('VRP.threeDoF()'),true);passed.push('Generic touchpad XR source with zero/emulated position uses 3DoF arm, not a hidden wrist at XR origin');
 const settle=async()=>{const start=await page.evaluate('fr');await page.waitForFunction(n=>fr>=n+3,start,{timeout:10000})};
 const getPose=()=>page.evaluate(()=>{const wrist=LM.hR.getWorldPosition(V3()),finger=LM.pmR.getWorldPosition(V3()).sub(wrist).normalize(),across=LM.ixR.getWorldPosition(V3()).sub(LM.mdR.getWorldPosition(V3())).normalize(),forward=V3(0,0,-1).applyQuaternion(INPUT.ctrl.quat),right=V3(1,0,0).applyQuaternion(INPUT.ctrl.quat);across.addScaledVector(finger,-across.dot(finger)).normalize();
  return {wrist:wrist.toArray(),target:tgt[1].toArray(),projection:wrist.clone().project(cam).toArray(),forwardError:finger.distanceTo(forward),rollError:across.distanceTo(right),elbowSlack:L1+L2-LM.uR.getWorldPosition(V3()).distanceTo(wrist)};});
 const neutral=await getPose();assert(neutral.forwardError<.02);assert(Math.abs(neutral.projection[0])<1&&Math.abs(neutral.projection[1])<1&&neutral.projection[2]<1);assert(neutral.elbowSlack>.1);passed.push('Neutral gorilla wrist in first-person frustum with bent articulated elbow');
 for(const angles of [[.25,0,0],[0,.4,0],[0,0,.6]]){
  await page.evaluate(a=>{__xr.q=new THREE.Quaternion().setFromEuler(new THREE.Euler(...a,'YXZ')).toArray()},angles);await settle();const p=await getPose();assert(p.forwardError<.02);assert(p.rollError<.03);assert(Math.hypot(...p.wrist.map((v,i)=>v-p.target[i]))<.03);
 }
 passed.push('Actual skinned gorilla hand follows controller pitch, yaw AND roll, not just a moving pointer');
 const rotation360=await page.evaluate(()=>{const saved=INPUT.ctrl.quat.clone();let maxForwardError=0,maxRollError=0;
  for(const axis of [V3(1,0,0),V3(0,1,0),V3(0,0,1)])for(let step=0;step<=24;step++){
   INPUT.ctrl.quat.setFromAxisAngle(axis,step*Math.PI*2/24);HOOK.wristPose();
   const wrist=LM.hR.getWorldPosition(V3()),d=LM.pmR.getWorldPosition(V3()).sub(wrist).normalize(),a=LM.ixR.getWorldPosition(V3()).sub(LM.mdR.getWorldPosition(V3()));a.addScaledVector(d,-a.dot(d)).normalize();
   maxForwardError=Math.max(maxForwardError,d.distanceTo(V3(0,0,-1).applyQuaternion(INPUT.ctrl.quat)));maxRollError=Math.max(maxRollError,a.distanceTo(V3(1,0,0).applyQuaternion(INPUT.ctrl.quat)));
  }
  INPUT.ctrl.quat.copy(saved);HOOK.wristPose();return {maxForwardError,maxRollError};
 });assert(rotation360.maxForwardError<.03&&rotation360.maxRollError<.03);passed.push('Actual gorilla wrist completes 360 degrees on all three axes without an Euler-angle clamp or flipped hand');

 await page.evaluate(()=>{__xr.q=[0,0,0,1];VRMENU.open_()});await settle();await page.waitForFunction('VRMENU.open&&!!VRMENU.pointerOrigin',{},{timeout:10000});const menu=await getPose();assert(Math.abs(menu.projection[0])<1&&Math.abs(menu.projection[1])<1);assert(menu.forwardError<.02);assert(await page.evaluate('VRMENU.pointerOrigin.distanceTo(LM.hR.getWorldPosition(V3()))<.03'));passed.push('Opening VR menu keeps tracked hand visible; laser originates at gorilla wrist');
 await page.screenshot({path:path.join(root,'docs/preview-hand-menu.png')});
 await page.evaluate('VRMENU.close()');await settle();
 const pixelProof=await page.evaluate(()=>{
  const target=new THREE.WebGLRenderTarget(256,256),a=new Uint8Array(256*256*4),b=new Uint8Array(a.length),old=R.getRenderTarget();
  R.setRenderTarget(target);R.render(S,cam);R.readRenderTargetPixels(target,0,0,256,256,a);pg.visible=false;R.render(S,cam);R.readRenderTargetPixels(target,0,0,256,256,b);pg.visible=true;R.setRenderTarget(old);target.dispose();
  let changed=0;const p=LM.hR.getWorldPosition(V3()).project(cam),x=(p.x*.5+.5)*256,y=(p.y*.5+.5)*256;
  for(let yy=Math.max(0,Math.floor(y-32));yy<Math.min(256,y+32);yy++)for(let xx=Math.max(0,Math.floor(x-32));xx<Math.min(256,x+32);xx++){const i=(yy*256+xx)*4;if(Math.abs(a[i]-b[i])+Math.abs(a[i+1]-b[i+1])+Math.abs(a[i+2]-b[i+2])>15)changed++}
  return changed;
 });assert(pixelProof>30);passed.push('GPU pixel readback proves avatar renders around the visible wrist (not only bone transforms)');await page.screenshot({path:path.join(root,'docs/preview-hand-first-person.png')});
 // Controlled collision lab using the actual game's triangle grid and frame loop.
 await page.evaluate(()=>{VRMENU.close();const floor=new THREE.Mesh(new THREE.PlaneGeometry(30,30).rotateX(-Math.PI/2),new THREE.MeshBasicMaterial());floor.material.userData.cls=0;buildGrid([floor]);P.set(0,1.1,0);vel.set(0,0,0);__xr.q=[0,0,0,1];__xr.pressed=false;LOCO.reset()});await page.waitForTimeout(300);
 assert(await page.evaluate('vel.length()<.01&&!LOCO.pushing'));passed.push('Moving/resting hand in free air creates no false surface propulsion');
 for(let i=1;i<=12;i++){await page.evaluate(a=>{__xr.q=[Math.sin(a/2),0,0,Math.cos(a/2)]},-1.35*i/12);await page.waitForTimeout(40)}
 await page.waitForFunction('LOCO.pushing&&touch[1]',{},{timeout:5000});
 const push=await page.evaluate('({headY:P.y,handY:tgt[1].y,trigger:INPUT.ctrl.trigger,anchor:!!LOCO.anchor})');assert(push.headY>1.13);assert(push.handY>=.07);assert.equal(push.trigger,false);assert.equal(push.anchor,false);passed.push('Actual game pushes body upward from floor contact with open hand, no trigger and no double integration');
 await page.evaluate('__xr.pressed=true');await page.waitForFunction('!!LOCO.anchor');
 const anchored=await page.evaluate('LOCO.anchor.toArray()');await page.evaluate(()=>{__xr.q=new THREE.Quaternion().setFromEuler(new THREE.Euler(-1.35,.25,0,'YXZ')).toArray()});await page.waitForTimeout(250);
 assert(await page.evaluate(a=>tgt[1].distanceTo(new THREE.Vector3().fromArray(a))<.02,anchored));passed.push('Trigger holds only a genuinely contacting hand; articulated arm stays attached to anchor');
 await page.evaluate('__xr.pressed=false;__xr.q=[0,0,0,1]');await page.waitForFunction('!LOCO.anchor');assert(await page.evaluate('vel.length()<=TUNE.max+.01'));passed.push('Release detaches hand and keeps bounded measured launch velocity');
 // Regression: 1.2 passed vertical-push tests but produced no walking on a flat floor.
 await page.evaluate(()=>{P.set(0,1.1,0);vel.set(0,0,0);TUNE.grav=9.8;SETTINGS.assist=0;assist=0;ptrs.clear();for(const k in keys)keys[k]=0;__xr.pressed=false;__xr.q=[0,0,0,1];LOCO.reset()});await settle();
 const walkStart=await page.evaluate('P.toArray()'),strokes=[];
 const aim=async angle=>{await page.evaluate(a=>{__xr.q=[Math.sin(a/2),0,0,Math.cos(a/2)]},angle);await settle()};
 for(let stroke=0;stroke<3;stroke++){
  await page.waitForFunction('P.y<1.16&&!LOCO.pushing',{},{timeout:10000});const before=await page.evaluate('P.toArray()');
  for(let step=1;step<=8;step++)await aim(-.85*step/8);
  await page.waitForFunction('!!LOCO.groundPlant',{},{timeout:5000});
  for(let step=1;step<=12;step++)await aim(-.85-.75*step/12);
  for(let step=1;step<=8;step++)await aim(-1.6+1.3*step/8);
  await aim(0);const after=await page.evaluate('P.toArray()');strokes.push({before,after,forward:before[2]-after[2]});
 }
 const walkEnd=await page.evaluate('P.toArray()'),walkDistance=walkStart[2]-walkEnd[2];assert(walkDistance>.6);assert(strokes.every(s=>s.forward>.1));assert(await page.evaluate('!INPUT.ctrl.trigger&&INPUT.ctrl.x===0&&INPUT.ctrl.y===0'));passed.push('Three open-hand floor strokes advance the real player horizontally with gravity enabled and no buttons/joystick/keyboard');
 await page.evaluate('__xr.lost=true');await page.waitForFunction('!INPUT.ctrl.present');await page.waitForFunction("HUD3D.ch.tracking.includes('SEM POSE')",{},{timeout:5000});const rest=await getPose();assert(Math.abs(rest.projection[0])<1&&Math.abs(rest.projection[1])<1);assert(await page.evaluate("HUD3D.ch.tracking.includes('SEM POSE')"));passed.push('Pose loss shows resting hand and explicit warning rather than claiming live tracking');
 assert.deepEqual(errors,[]);const report={passed,errors,pixelProof,neutral,menu,rotation360,walkDistance,strokes,limitations:'Real Chromium/WebGL and supplied skinned model; XR pose/session is a fixture, no physical PICO G2 or actual immersive session validation.'};fs.writeFileSync(path.join(root,'docs/HAND_TESTS.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{await browser.close()}
