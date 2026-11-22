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
  const sources={},poses=new Map(),report={};let minSkinY=Infinity,maxHorizontal=0;
  for(const side of ['left','right']){const space={},s={handedness:side,targetRayMode:'tracked-pointer',targetRaySpace:{},gripSpace:space,profiles:['generic-trigger'],gamepad:{mapping:'xr-standard',axes:[0,0],buttons:[{pressed:false}]}};sources[side]=s;poses.set(space,{x:side==='left'?-.25:.25,y:-.7,z:-.55})}
  const xr={getViewerPose:()=>({transform:{orientation:{x:0,y:0,z:0,w:1},position:{x:0,y:0,z:0}}}),getPose:space=>{const p=poses.get(space);return p?{emulatedPosition:!!p.emulated,transform:{position:p,orientation:p.q||{x:0,y:0,z:0,w:1}}}:null}};
  const material=new THREE.MeshBasicMaterial();material.userData.cls=0;
  const floor=new THREE.Mesh(new THREE.PlaneGeometry(30,30).rotateX(-Math.PI/2),material);
  const wall=new THREE.Mesh(new THREE.PlaneGeometry(10,10).rotateY(Math.PI/2).translate(.8*PS,0,0),material);
  const tick=(n=1)=>{for(let i=0;i<n;i++){PICO.frame=xr;frame(__test.time+=1000/72);PICO.frame=null;maxHorizontal=Math.max(maxHorizontal,Math.hypot(vel.x,vel.z)/PS);for(const h of Object.values(PICO.hands))if(h.present)for(const v of RIGHT_ARM.points(pl,LM[h.side==='left'?'hL':'hR']))minSkinY=Math.min(minSkinY,v.y/PS)}};
  const reset=(sides=['right'],walls=false)=>{buildGrid(walls?[floor,wall]:[floor]);P.set(0,1.1*CSC,0);vel.set(0,0,0);PICO.reset();PICO.menu=false;PICO.yaw=0;PICO.assist=false;ZIP.on=false;WTR.in=false;solo=true;keys.KeyW=0;ptrs.clear();PICO.session={inputSources:sides.map(s=>sources[s])};for(const side of ['left','right']){poses.set(sources[side].gripSpace,{x:side==='left'?-.25:.25,y:-.7,z:-.55});sources[side].gamepad.buttons[0].pressed=false}tick(12)};
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
 assert.deepEqual(errors,[]);
 const report={checks:['Left hand floor push','Right hand floor push','Both hands floor push','Hand wall push','Fast stroke','Slow stroke','Stationary grounded body','Jump and land','Fall without tunneling','Body wall collision','Forward motion','Backward motion','Left/right lateral motion'],results,errors,limitations:'Full game frames and real model/triangle/body collision. Two positional WebXR sources are fixtures, not proof that stock PICO G2 supplies two positional controllers. Actual rendering and legacy 3DoF are exercised by check_browser.mjs. No physical headset/FPS/latency test.'};
 fs.writeFileSync(path.join(root,'docs/LOCOMOTION_TESTS.json'),JSON.stringify(report,null,2)+'\n');
}finally{await browser.close()}
