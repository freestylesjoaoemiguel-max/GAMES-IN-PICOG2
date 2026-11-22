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

 const results=await page.evaluate(()=>{
  __test.manual=true;__test.time=performance.now();last=__test.time;
  // Keep the full game frame, input, scene, bones and triangle/body collisions.
  // Only the headset compositor is a fixture; stereo rendering is tested separately.
  PICO.render=()=>true;PICO.mode='webxr';R.xr.getReferenceSpace=()=>({});
  const sources={},poses=new Map(),report={assistDefault:PICO.assist};let minSkinY=Infinity,maxHorizontal=0;
  for(const side of ['left','right']){const space={},s={handedness:side,targetRayMode:'tracked-pointer',targetRaySpace:{},gripSpace:space,profiles:['generic-trigger'],gamepad:{mapping:'xr-standard',axes:[0,0],buttons:[{pressed:false}]}};sources[side]=s;poses.set(space,{x:side==='left'?-.25:.25,y:-.7,z:-.55})}
  const xr={getViewerPose:()=>({transform:{orientation:{x:0,y:0,z:0,w:1},position:{x:0,y:0,z:0}}}),getPose:space=>{const p=poses.get(space);return p?{emulatedPosition:!!p.emulated,transform:{position:p,orientation:p.q||{x:0,y:0,z:0,w:1}}}:null}};
  const material=new THREE.MeshBasicMaterial();material.userData.cls=0;
  const floor=new THREE.Mesh(new THREE.PlaneGeometry(30,30).rotateX(-Math.PI/2),material);
  const wall=new THREE.Mesh(new THREE.PlaneGeometry(10,10).rotateY(Math.PI/2).translate(.8*PS,0,0),material);
  const tick=(n=1)=>{for(let i=0;i<n;i++){PICO.frame=xr;frame(__test.time+=1000/72);PICO.frame=null;maxHorizontal=Math.max(maxHorizontal,Math.hypot(vel.x,vel.z)/PS);for(const h of Object.values(PICO.hands))if(h.present)for(const v of RIGHT_ARM.points(pl,LM[h.side==='left'?'hL':'hR']))minSkinY=Math.min(minSkinY,v.y/PS)}};
  const reset=(sides=['right'],walls=false)=>{buildGrid(walls?[floor,wall]:[floor]);P.set(0,1.1*CSC,0);vel.set(0,0,0);PICO.reset();PICO.menu=false;PICO.yaw=0;PICO.assist=false;ZIP.on=false;WTR.in=false;solo=true;keys.KeyW=0;ptrs.clear();PICO.session={inputSources:sides.map(s=>sources[s])};for(const side of ['left','right']){poses.set(sources[side].gripSpace,{x:side==='left'?-.25:.25,y:-.7,z:-.55});sources[side].gamepad.buttons=[{pressed:false}];sources[side].gamepad.axes=[0,0]}tick(12)};
  const ground=()=>{for(let i=1;i<=8;i++){for(const s of PICO.session.inputSources)poses.get(s.gripSpace).y=-.7-.08*i/8;tick()}tick(24)};
  const stroke=(sides,axis,amount,steps=16)=>{reset(sides);ground();const start=P.clone();let peak=0,contact=0;for(let i=0;i<steps;i++){for(const side of sides)poses.get(sources[side].gripSpace)[axis]+=amount/steps;tick();peak=Math.max(peak,vel.length()/PS);contact+=sides.filter(s=>PICO.hands[s].arm.contact).length}return {delta:P.clone().sub(start).divideScalar(PS).toArray(),peak,contact,tracking:sides.map(s=>PICO.hands[s].tracking)}};
  report.left=stroke(['left'],'z',.45);report.right=stroke(['right'],'z',.45);report.both=stroke(['left','right'],'z',.45);
  report.back=stroke(['right'],'z',-.4);report.sideLeft=stroke(['left'],'x',.4);report.sideRight=stroke(['right'],'x',-.4);
  report.fast=stroke(['right'],'z',.4,8);report.slow=stroke(['right'],'z',.4,40);report.limits=stroke(['right'],'z',2.4,8);
  stroke(['right'],'z',.4,8);poses.get(sources.right.gripSpace).y=.1;const release=P.clone();tick(12);report.momentum=(release.z-P.z)/PS;
  reset();ground();const idle=P.clone();tick(180);report.idle={drift:P.distanceTo(idle)/PS,feet:(P.y-1.1*CSC)/PS,vy:vel.y/PS,grounded:PICO.grounded};
  jumpReq=true;let peakY=P.y;for(let i=0;i<140;i++){tick();peakY=Math.max(peakY,P.y)}report.jump={height:(peakY-1.1*CSC)/PS,feet:(P.y-1.1*CSC)/PS,grounded:PICO.grounded};
  reset();P.y=3*PS;PICO.reset();let minimum=P.y,down=false;for(let i=0;i<150;i++){tick();down=down||vel.y<0;minimum=Math.min(minimum,P.y)}report.fall={down,minFeet:(minimum-1.1*CSC)/PS,feet:(P.y-1.1*CSC)/PS,grounded:PICO.grounded};
  reset(['right'],true);const hand=poses.get(sources.right.gripSpace);hand.y=-.2;hand.x=.4;tick(20);const start=P.clone();let peak=0;for(let i=0;i<20;i++){hand.x+=.025;tick();peak=Math.max(peak,vel.length()/PS)}report.wall={delta:P.clone().sub(start).divideScalar(PS).toArray(),peak};
  reset(['right'],true);poses.get(sources.right.gripSpace).y=-.2;P.y=2*PS;PICO.reset();poses.get(sources.right.gripSpace).x=.45;tick(150);report.wallRest={feet:(P.y-1.1*CSC)/PS,grounded:PICO.grounded};
  reset(['right'],true);vel.set(8*PS,0,0);tick(25);report.bodyWall={x:P.x/PS,maxX:.8-WR/PS};
  reset();poses.get(sources.right.gripSpace).y=.1;tick(20);const airStart=P.clone();for(let i=0;i<25;i++){poses.get(sources.right.gripSpace).z+=.01;tick()}report.freeAir={drift:P.distanceTo(airStart)/PS,contact:PICO.arm.contact};
  reset(['left','right']);ground();const left=PICO.hands.left.source,right=PICO.hands.right.source;PICO.session.inputSources.reverse();tick();report.routing={left:PICO.hands.left.source===left,right:PICO.hands.right.source===right,bothVisible:[]};pl.traverse(m=>{if(m.isSkinnedMesh)report.routing.bothVisible.push(m.userData.handMask)});
  PICO.session.inputSources=[sources.left];tick();report.routing.leftOnly=PICO.hands.left.present&&!PICO.hands.right.present;report.routing.leftMask=[];pl.traverse(m=>{if(m.isSkinnedMesh)report.routing.leftMask.push(m.userData.handMask)});
  const oldPos=poses.get(sources.left.gripSpace);poses.delete(sources.left.gripSpace);tick();report.poseLoss=!PICO.hands.left.present&&!PICO.hands.left.arm.contact;poses.set(sources.left.gripSpace,oldPos);tick();report.reconnectReaction=PICO.hands.left.arm.reaction.length()/PS;
  reset();ground();PICO.toggleMenu();tick();report.menu=PICO.menu&&!PICO.arm.contact&&vel.length()===0;PICO.toggleMenu();
  report.estimated={};for(const side of ['left','right']){reset([side]);const pose=poses.get(sources[side].gripSpace);pose.emulated=true;tick(12);const start=P.clone();for(let i=1;i<=40;i++){const q=new THREE.Quaternion().setFromAxisAngle(V3(1,0,0),-1.6*i/40);pose.q={x:q.x,y:q.y,z:q.z,w:q.w};tick()}report.estimated[side]={forward:(start.z-P.z)/PS,tracking:PICO.hands[side].tracking,otherAbsent:!PICO.hands[side==='left'?'right':'left'].present}}
  report.calibration=[];for(const offset of [-.35,.44]){reset(['right']);PICO.hands.right.arm=ARM_PHYSICS.create(THREE,PICO.surface.solve);const pose=poses.get(sources.right.gripSpace);pose.emulated=true;const set=q=>{const t=new THREE.Quaternion().setFromAxisAngle(V3(1,0,0),q);pose.q={x:t.x,y:t.y,z:t.z,w:t.w}};set(offset);tick(300);const before=P.clone();for(let i=0;i<144;i++){if(i%3===0)set(offset+.035*Math.sin(2*Math.PI*9*i/72));tick()}report.calibration.push({neutralError:Math.abs(PICO.arm.neutral+offset),drift:P.distanceTo(before)/PS,feet:(P.y-1.1*CSC)/PS})}
  reset();poses.get(sources.right.gripSpace).y=.2;PICO.assist=true;sources.right.gamepad.axes=[0,-1];const padStart=P.clone();tick(60);report.touchpad=(padStart.z-P.z)/PS;sources.right.gamepad.axes=[0,0];
  // Regression: trigger must not replace hand velocity with an accumulated anchor spring.
  report.freeHands=[];for(const held of [false,true]){reset();ground();sources.right.gamepad.buttons[0].pressed=held;const pose=poses.get(sources.right.gripSpace),start=P.clone();for(let i=0;i<16;i++){pose.z+=.45/16;tick()}const afterStroke=P.clone();pose.y=.1;tick(12);const released=!PICO.arm.contact;tick(180);report.freeHands.push({held,forward:(start.z-afterStroke.z)/PS,momentum:(afterStroke.z-P.z)/PS,released,feet:(P.y-1.1*CSC)/PS,speed:vel.length()/PS,grounded:PICO.grounded})}
  reset(['right'],true);poses.get(sources.right.gripSpace).y=-.2;poses.get(sources.right.gripSpace).x=.45;P.y=2*PS;PICO.reset();tick(12);sources.right.gamepad.buttons[0].pressed=true;tick(150);report.heldWallRest={feet:(P.y-1.1*CSC)/PS,grounded:PICO.grounded};
  report.freeWrist=[];for(const held of [false,true]){reset();PICO.hands.right.arm=ARM_PHYSICS.create(THREE,PICO.surface.solve);const pose=poses.get(sources.right.gripSpace);pose.emulated=true;sources.right.gamepad.buttons[0].pressed=held;const set=angle=>{const q=new THREE.Quaternion().setFromAxisAngle(V3(1,0,0),angle);pose.q={x:q.x,y:q.y,z:q.z,w:q.w};tick()};for(let i=0;i<300;i++)set(0);const strokes=[];for(let n=0;n<3;n++){const start=P.z;for(let i=1;i<=40;i++)set(-.7*i/40);for(let i=1;i<=25;i++)set(-.7+.8*i/25);for(let i=0;i<80;i++)set(.1);strokes.push({forward:(start-P.z)/PS,released:!PICO.arm.contact})}report.freeWrist.push({held,strokes,feet:(P.y-1.1*CSC)/PS,speed:vel.length()/PS})}
  report.padIsolation=[];for(const assist of [false,true])for(const touched of [false,true]){reset();PICO.assist=assist;poses.get(sources.right.gripSpace).y=.2;tick(20);sources.right.gamepad.buttons=[{pressed:false},{pressed:false},{pressed:false,touched}];sources.right.gamepad.axes= touched?[.08,-.1]:[.8,-.9];const start=P.clone();tick(180);report.padIsolation.push({assist,touched,drift:P.distanceTo(start)/PS})}
  reset();PICO.assist=false;poses.get(sources.right.gripSpace).y=.2;tick(20);sources.right.gamepad.buttons=[{pressed:false},{pressed:false},{pressed:false,touched:true}];sources.right.gamepad.axes=[0,-1];const noPadStart=P.clone();tick(120);report.padDisabledDrift=P.distanceTo(noPadStart)/PS;
  reset(['left']);PICO.mode='webvr';navigator.getGamepads=()=>[{id:'legacy-left',index:2,hand:'left',connected:true,pose:{hasPosition:true,position:[-.25,-.7,-.55],orientation:[0,0,0,1]},buttons:[],axes:[]}];tick();report.legacyPosition=PICO.hands.left.tracking==='measured'&&!PICO.hands.right.present;
  report.minSkinY=minSkinY;report.maxHorizontal=maxHorizontal;return report;
 });
 console.log(JSON.stringify(results,null,2));
 for(const name of ['left','right','both']){assert(results[name].delta[2]<-.12,name);assert(results[name].contact>3,name);assert(results[name].tracking.every(t=>t==='measured'))}
 assert(results.back.delta[2]>.12);assert(results.sideLeft.delta[0]<-.12);assert(results.sideRight.delta[0]>.12);
 assert(results.fast.peak>results.slow.peak*1.5);assert(results.fast.peak<=10);assert(results.both.peak<=results.right.peak*1.05);
 assert(results.wall.delta[0]<-.1);assert(results.wallRest.grounded&&Math.abs(results.wallRest.feet)<.01);assert(results.bodyWall.x<=results.bodyWall.maxX+.05);
 assert(results.idle.drift<.015&&Math.abs(results.idle.feet)<.01&&results.idle.grounded&&Math.abs(results.idle.vy)<.01);
 assert(results.jump.height>.3&&results.jump.grounded&&Math.abs(results.jump.feet)<.01);
 assert(results.fall.down&&results.fall.minFeet>=-.01&&results.fall.grounded);
 assert(results.freeAir.drift<.015&&!results.freeAir.contact);assert(results.routing.left&&results.routing.right&&results.routing.leftOnly);
 assert(results.routing.bothVisible.every(v=>v==='LR'));assert(results.routing.leftMask.every(v=>v==='L'));assert(results.poseLoss&&results.reconnectReaction===0&&results.menu);
 for(const side of ['left','right'])assert(results.estimated[side].forward>.2&&results.estimated[side].tracking==='estimated-3dof'&&results.estimated[side].otherAbsent);assert(results.legacyPosition);
 assert(results.minSkinY>=-.001&&results.maxHorizontal<=8.001&&results.momentum>.05&&results.limits.peak<8.01);
 assert(!results.assistDefault&&results.touchpad>.2);assert(results.calibration.every(c=>c.neutralError<.03&&c.drift<.1&&Math.abs(c.feet)<.05));
 assert(results.freeHands.every(h=>h.forward>.2&&h.momentum>.05&&h.released&&h.grounded&&Math.abs(h.feet)<.01&&h.speed<.01));
 assert(Math.abs(results.freeHands[0].forward-results.freeHands[1].forward)<1e-6);
 assert(results.heldWallRest.grounded&&Math.abs(results.heldWallRest.feet)<.01);
 assert(results.freeWrist.every(h=>h.strokes.every(s=>s.forward>.1&&s.released)&&Math.abs(h.feet)<.02&&h.speed<.05));
 assert.deepEqual(results.freeWrist[0].strokes,results.freeWrist[1].strokes);
 assert(results.padIsolation.every(p=>p.drift<.001)&&results.padDisabledDrift<.001);
 assert.deepEqual(errors,[]);
 const report={checks:['Left hand floor push','Right hand floor push','Both hands floor push','Hand wall push','Fast stroke','Slow stroke','Stationary grounded body','Jump and land','Fall without tunneling','Body wall collision','Forward motion','Backward motion','Left/right lateral motion'],results,errors,limitations:'Full game frames and real model/triangle/body collision. Two positional WebXR sources are fixtures, not proof that stock PICO G2 supplies two positional controllers. Actual rendering and legacy 3DoF are exercised by check_browser.mjs. No physical headset/FPS/latency test.'};
 fs.writeFileSync(path.join(root,'docs/LOCOMOTION_TESTS.json'),JSON.stringify(report,null,2)+'\n');
}finally{await browser.close()}
