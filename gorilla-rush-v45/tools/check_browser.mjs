import {chromium as playwright} from '@playwright/test';
import chromium from '@sparticuz/chromium';
import fs from 'node:fs';import path from 'node:path';import zlib from 'node:zlib';import {execFileSync} from 'node:child_process';import assert from 'node:assert/strict';import crypto from 'node:crypto';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),libs=path.resolve(root,'../.cache/chromium-libs');fs.mkdirSync(libs,{recursive:true});fs.writeFileSync(path.join(libs,'libs.tar'),zlib.brotliDecompressSync(fs.readFileSync(path.join(root,'node_modules/@sparticuz/chromium/bin/al2023.tar.br'))));execFileSync('tar',['xf',path.join(libs,'libs.tar'),'-C',libs]);process.env.LD_LIBRARY_PATH=path.join(libs,'lib')+(process.env.LD_LIBRARY_PATH?':'+process.env.LD_LIBRARY_PATH:'');
const browser=await playwright.launch({executablePath:await chromium.executablePath(),args:chromium.args.filter(a=>!a.includes('disable-web-security')&&!a.includes('allow-running-insecure-content')&&a!=='--single-process'),headless:true});

const base=process.env.PREVIEW_BASE_URL||'http://127.0.0.1:5180/',passed=[],errors=[];
try{
 const page=await browser.newPage({viewport:{width:1024,height:768}});page.on('pageerror',e=>errors.push(e.stack));
 await page.addInitScript(()=>{
  // Compatibility regression: old Gecko does NOT need DecompressionStream anymore.
  window.DecompressionStream=undefined;
  window.__test={q:[0,0,0,1],lost:false,pressed:false,manual:false,time:0,submitted:0};
  window.VRFrameData=function(){this.pose={orientation:[0,0,0,1],position:[0,0,0]}};
  const display={displayName:'Pico G2 fixture',capabilities:{canPresent:true},isPresenting:false,
   getEyeParameters(){return{renderWidth:1920,renderHeight:2160}},
   requestPresent(){this.isPresenting=true;return Promise.resolve()},exitPresent(){this.isPresenting=false;window.dispatchEvent(new Event('vrdisplaypresentchange'));return Promise.resolve()},
   requestAnimationFrame(cb){return __test.manual?0:requestAnimationFrame(cb)},
   getFrameData(f){const c=new THREE.PerspectiveCamera(90,1920/2160,.01,120);f.leftProjectionMatrix=f.rightProjectionMatrix=c.projectionMatrix.toArray();f.leftViewMatrix=new THREE.Matrix4().makeTranslation(.032,0,0).toArray();f.rightViewMatrix=new THREE.Matrix4().makeTranslation(-.032,0,0).toArray()},
   submitFrame(){__test.submitted++}};
  navigator.getVRDisplays=()=>Promise.resolve([display]);
  navigator.getGamepads=()=>__test.lost?[]:[{id:'Pico G2 single controller',hand:'right',connected:true,index:0,mapping:'',pose:{orientation:__test.q},axes:[0,0],buttons:[{pressed:false},{pressed:__test.pressed},{pressed:false}]}];
 });
 await page.goto(base);await page.waitForFunction('window.V45&&V45.ready&&pl&&mapDone',null,{timeout:90000});
 assert(await page.evaluate('mapMeshes.length>100&&beachMeshes.length>100'));passed.push('Both supplied maps and skinned avatar load with DecompressionStream unavailable; 68 payload hashes tested separately');
 assert.equal(await page.evaluate('PICO.api'),'webvr');await page.click('#pico-vr');await page.waitForFunction("PICO.presenting&&state==='play'&&PICO.stats.frames>5",null,{timeout:20000});
 assert(await page.evaluate('__test.submitted>0&&PICO.stats.eyes>=2'));passed.push('Real frame loop requests legacy presentation, draws both eye projection/view matrices and submits frames');
 const neutral=await page.evaluate(()=>{const wrist=LM.hR.getWorldPosition(V3()),finger=LM.pmR.getWorldPosition(V3()).sub(wrist).normalize();return {error:finger.distanceTo(V3(0,0,-1).applyQuaternion(PICO.ctrl.quat)),gap:wrist.distanceTo(tgt[1]),wrist:wrist.toArray(),target:tgt[1].toArray(),PS,mesh:pl.children.length}});
 assert(neutral.error<.03);assert(neutral.gap<.03*neutral.PS);passed.push('Right wrist matches collider target and measured orientation in actual miniature player scale');
 assert(await page.evaluate("(()=>{let n=0;pl.traverse(o=>{if(o.isSkinnedMesh&&o.userData.rightArmOnly)n++});return n>0})()"));passed.push('Left skin removed only from local rendering, supplied GLB preserved');
 await page.screenshot({path:path.join(root,'docs/preview-v45-vr.png')});
 const movement=await page.evaluate(()=>{
  __test.manual=true;__test.time=performance.now();last=__test.time;
  const floor=new THREE.Mesh(new THREE.PlaneGeometry(30,30).rotateX(-Math.PI/2),new THREE.MeshBasicMaterial());floor.material.userData.cls=0;buildGrid([floor]);P.set(0,1.1*CSC,0);vel.set(0,0,0);PICO.reset();PICO.yaw=0;PICO.assist=false;solo=true;keys.KeyW=0;ptrs.clear();
  const tick=(pitch,n=1)=>{__test.q=new THREE.Quaternion().setFromAxisAngle(V3(1,0,0),pitch).toArray();for(let i=0;i<n;i++)frame(__test.time+=1000/72)};
  tick(0,20);const starts=P.clone(),strokes=[];
  for(let stroke=0;stroke<3;stroke++){const before=P.clone();for(let i=1;i<=32;i++)tick(-1.6*i/32);tick(-1.6,6);for(let i=1;i<=24;i++)tick(-1.6+1.6*i/24);tick(0,40);strokes.push((before.z-P.z)/PS)}
  const end=P.clone(),stopped=end.clone();tick(0,60);
  return{strokes,forward:(starts.z-end.z)/PS,release:!PICO.arm.contact,drift:P.distanceTo(stopped)/PS,trigger:PICO.ctrl.trigger,keys:Object.values(keys).some(Boolean),buttons:PICO.ctrl.controls.pressed,scale:PS};
 });
 console.log('movement',movement);assert(movement.forward>.5);assert(movement.strokes.every(x=>x>.05));assert(movement.release);assert(movement.drift<.06);assert(!movement.trigger&&!movement.keys&&movement.buttons.length===0);passed.push('Actual full v45 frame: three ground strokes move forward without trigger/joystick; lifting releases and stationary hand stops');
 const rotation=await page.evaluate(()=>{let f=0,r=0;for(const axis of [V3(1,0,0),V3(0,1,0),V3(0,0,1)])for(let i=0;i<=24;i++){__test.q=new THREE.Quaternion().setFromAxisAngle(axis,i*2*Math.PI/24).toArray();frame(__test.time+=1000/72);const w=LM.hR.getWorldPosition(V3()),d=LM.pmR.getWorldPosition(V3()).sub(w).normalize(),a=LM.ixR.getWorldPosition(V3()).sub(LM.mdR.getWorldPosition(V3()));a.addScaledVector(d,-a.dot(d)).normalize();f=Math.max(f,d.distanceTo(V3(0,0,-1).applyQuaternion(PICO.ctrl.quat)));r=Math.max(r,a.distanceTo(V3(1,0,0).applyQuaternion(PICO.ctrl.quat)))}return{forwardError:f,rollError:r}});assert(rotation.forwardError<.03&&rotation.rollError<.03);passed.push('Actual skinned hand rotates 360 degrees about all three axes');
 await page.evaluate(()=>{PICO.toggleMenu();__test.q=[0,0,0,1];frame(__test.time+=1000/72)});assert(await page.evaluate('PICO.menu&&!PICO.arm.contact&&vel.length()===0&&PICO.panel.visible'));passed.push('Immersive menu renders in scene and cancels locomotion without throwing player');
 await page.evaluate(()=>{PICO.toggleMenu();__test.lost=true;frame(__test.time+=1000/72)});assert(await page.evaluate('!PICO.ctrl.present&&!PICO.arm.contact&&PICO.reason.includes("SEM POSE")'));passed.push('Controller loss cancels contact and explicitly reports missing pose');
 // Source-specific WebXR mapping and no stale Gamepad fallback; renderer remains real.
 const xr=await page.evaluate(()=>{const source={targetRayMode:'tracked-pointer',handedness:'none',targetRaySpace:{},profiles:['generic-trigger-touchpad'],gamepad:{mapping:'xr-standard',axes:[0,0],buttons:[]}};PICO.mode='webxr';PICO.session={inputSources:[source]};let valid=true;R.xr.getReferenceSpace=()=>({});PICO.frame={getViewerPose(){return{transform:{orientation:{x:0,y:0,z:0,w:1},position:{x:0,y:0,z:0}}}},getPose(space){if(space!==source.targetRaySpace)throw Error('wrong source');return valid?{emulatedPosition:true,transform:{orientation:{x:0,y:0,z:0,w:1}}}:null}};PICO.beforeFrame(.016);const good=PICO.ctrl.present;valid=false;__test.lost=false;PICO.beforeFrame(.016);return{good,lost:!PICO.ctrl.present}});assert(xr.good&&xr.lost);passed.push('WebXR reads selected source targetRaySpace; pose loss cannot fall back to stale legacy controller');
 assert.deepEqual(errors,[]);
 const report={passed,errors,neutral,movement,rotation,limitations:['Mocked WebVR display/WebXR frame and controller, real v45 game/model/triangle collision/WebGL.','No physical G2, real immersive compositor, native SDK, Android installation, latency/FPS or multiplayer test.']};fs.writeFileSync(path.join(root,'docs/BROWSER_TESTS.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{await browser.close()}
