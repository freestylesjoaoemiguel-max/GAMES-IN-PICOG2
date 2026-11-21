/* Browser integration for the supplied v45. No native SDK emulation.
 * WebXR local-space poses or legacy WebVR frame data only. Runtime owns stereo.
 */
'use strict';
const PICO=(()=>{
 const p={starting:false,mode:'flat',presenting:false,frame:null,session:null,display:null,head:new THREE.Quaternion(),rawHead:new THREE.Quaternion(),headPosition:V3(),yaw:0,source:null,menu:false,choice:0,assist:false,nearFloor:null,buttonMap:{padClick:0,padTrig:1,padApp:2},ctrl:{present:false,quat:new THREE.Quaternion(),raw:new THREE.Quaternion(),trigger:false},select:new WeakMap(),needsRelease:true,reason:'Entre em VR para ler o controle',stats:{frames:0,eyes:0},calibration:null};
 const arm=ARM_PHYSICS.create(THREE,HAND_SURFACE.solve);p.arm=arm;
 const yawQ=new THREE.Quaternion(),lastPosition=V3(),eye=V3(),forward=V3(0,0,-1),right=V3(1,0,0);
 const rig=new THREE.Group();S.add(rig);rig.add(cam);const ecam=new THREE.PerspectiveCamera();ecam.matrixAutoUpdate=false;
 let lastTrigger=false,lastClick=false,clickTime=0,clickX=0,longClick=false,lastMenu=false,menuAxis=false,lastText='',hudTime=0,sourceKey=null,ready=false;
 const c=document.createElement('canvas');c.width=768;c.height=448;const ctx=c.getContext('2d'),tex=new THREE.CanvasTexture(c);tex.minFilter=THREE.LinearFilter;tex.generateMipmaps=false;
 const panel=new THREE.Mesh(new THREE.PlaneGeometry(1.65,.96),new THREE.MeshBasicMaterial({map:tex,transparent:true,depthTest:false,depthWrite:false,fog:false}));panel.renderOrder=10000;panel.frustumCulled=false;panel.visible=false;S.add(panel);p.panel=panel;
 const ray=new THREE.Line(new THREE.BufferGeometry().setFromPoints([V3(),V3(0,0,-1)]),new THREE.LineBasicMaterial({color:0xffcc66,depthTest:false}));ray.renderOrder=10001;ray.visible=false;S.add(ray);
 const rows=['CONTINUAR','CALIBRAR: aponte para a frente','AJUDA NO TOUCHPAD: ','VOLTAR AO INICIO DO MAPA','SAIR DO VR'];
 p.immersive=()=>p.presenting;
 p.reset=()=>{arm.reset();p.needsRelease=true;vel.set(0,0,0);touch[0]=touch[1]=false;slot.forEach(s=>{s.p=null;s.t=0});hv.forEach(v=>v.set(0,0,0));};
 p.toggleMenu=()=>{p.menu=!p.menu;p.reset();lastText=''};
 p.runChoice=()=>{if(p.choice===0)p.toggleMenu();else if(p.choice===1){if(p.ctrl.present){p.calibration=p.ctrl.raw.clone().invert().multiply(p.rawHead);p.reset();p.menu=false}}else if(p.choice===2){p.assist=!p.assist;lastText=''}else if(p.choice===3){P.copy(spawnPos());p.reset();p.menu=false}else p.stop()};
 p.configureButtons=()=>{for(const k of ['padClick','padTrig','padApp'])p.buttonMap[k]=+document.getElementById(k).value;try{localStorage.setItem('v45-buttons',JSON.stringify(p.buttonMap))}catch(e){}};
 p.beforeFrame=dt=>{
  if(!p.presenting)return;rig.position.set(0,0,0);rig.quaternion.identity();rig.scale.setScalar(1);
  yawQ.setFromAxisAngle(V3(0,1,0),p.yaw);
  let h=null,src=null,q=null,g=null;
  if(p.mode==='webxr'&&p.frame){
   const ref=R.xr.getReferenceSpace();try{h=p.frame.getViewerPose(ref);const a=h&&TRACKING.orientation(h.transform.orientation);if(a){p.rawHead.fromArray(a);const z=h.transform.position;p.headPosition.set(z.x,z.y,z.z)}}catch(e){p.reason='Falha ao ler pose da cabeca'}
   src=TRACKING.choose(p.session.inputSources,'right',p.source);
   if(src)try{const t=p.frame.getPose(src.targetRaySpace,ref);q=t&&TRACKING.orientation(t.transform.orientation);g=src.gamepad}catch(e){}
  }else if(p.mode==='webvr'&&p.display){
   p.display.getFrameData(p.fd);h=p.fd.pose;const a=h&&TRACKING.orientation(h.orientation);if(a)p.rawHead.fromArray(a);if(h&&h.position)p.headPosition.fromArray(h.position);else p.headPosition.set(0,0,0);
   const list=Array.from(navigator.getGamepads?navigator.getGamepads():[]).filter(g=>g&&g.connected&&g.pose&&TRACKING.orientation(g.pose.orientation));g=list.find(g=>g.hand==='right')||list[0];if(g){src=g.index+':'+g.id;q=TRACKING.orientation(g.pose.orientation)}
  }
  p.head.copy(yawQ).multiply(p.rawHead);cam.quaternion.copy(p.head);
  if(src!==sourceKey||(!q&&p.ctrl.present)){p.reset();sourceKey=src;lastTrigger=lastClick=lastMenu=false;longClick=false}
  p.source=typeof src==='object'?src:null;p.ctrl.present=!!q;
  p.ctrl.trigger=false;p.ctrl.controls=null;
  if(q){p.ctrl.raw.fromArray(q);p.ctrl.quat.copy(yawQ).multiply(p.ctrl.raw);const b=TRACKING.controls(g,p.buttonMap,p.source&&p.select.get(p.source));p.ctrl.controls=b;p.ctrl.trigger=b.trigger;p.reason=(p.mode==='webxr'?(src.profiles||[]).join(','):g.id)||p.mode;
   if(!b.trigger)p.needsRelease=false;
   if(b.menu&&!lastMenu)p.toggleMenu();lastMenu=b.menu;
   const now=performance.now();if(b.click&&!lastClick){clickTime=now;clickX=b.x;longClick=false}
   if(b.click&&!longClick&&now-clickTime>600){longClick=true;p.toggleMenu()}
   if(!b.click&&lastClick&&!longClick){if(p.menu)p.runChoice();else if(Math.abs(clickX)>.55){p.yaw+=(clickX<0?1:-1)*Math.PI/6;p.reset()}else jumpReq=true}
   if(p.menu&&b.trigger&&!lastTrigger)p.runChoice();
   if(p.menu&&Math.abs(b.y)>.6&&!menuAxis){p.choice=(p.choice+(b.y>0?1:rows.length-1))%rows.length;lastText=''}menuAxis=Math.abs(b.y)>.6;
   lastTrigger=b.trigger;lastClick=b.click;
  }else p.reason='SEM POSE: reconecte o controle. Nao ha rastreamento sintetico.';
  p.stats.frames++;
 };
 p.handFrame=(dt,fw,rt)=>{
  forward.copy(fw);right.copy(rt);eye.copy(P).add(V3(0,EYEUP,0));lastPosition.copy(P);
  // The source map uses a miniature player: all distances must use PS, not metres.
  let aq=p.ctrl.quat;if(p.calibration)aq=p.ctrl.quat.clone().multiply(p.calibration);
  const was=arm.contact;
  const touching=arm.update({position:P,eye,quat:p.ctrl.quat,armQuat:aq,forward:fw,right:rt,scale:PS,dt,present:p.ctrl.present,paused:p.menu,trigger:p.ctrl.trigger,needsRelease:p.needsRelease,cast:hit,
   move(delta,stepDt){const n=Math.max(1,Math.ceil(delta.length()/(.04*PS)));const saved=vel.clone();vel.copy(delta).divideScalar(stepDt);const y=P.y;for(let i=0;i<n;i++){stepMove(stepDt/n);wallFix()}sweepFloor(y);vel.copy(saved)}
  });
  tgt[1].copy(arm.target);tgt[0].copy(P);touch[0]=false;touch[1]=touching;
  if(touching&&!was){tapSound(1,lastCls,.6)}
  if(touching)vel.copy(arm.velocity);else if(was)vel.copy(arm.velocity);
  const floor=hit(arm.target,V3(0,-1,0),2*PS);p.nearFloor=floor?floor.distance/PS:null;
 };
 p.physics=(dt,d,rt,now)=>{
  if(p.menu){vel.set(0,0,0);jumpReq=false;return}
  if(ZIP.on){arm.reset();zipRide(dt);return}
  if(arm.contact){jumpReq=false;return}
  vel.y-=9.8*PS*dt*(WTR.in?.1:1);
  if(WTR.in&&p.ctrl.present){ // Rotational paddle: same estimated hand, not camera landmarks.
   const delta=arm.desired.clone().sub(p.swimPrev||arm.desired);if(delta.length()<PS*.5)vel.addScaledVector(delta,-2/Math.max(dt,.001));vel.multiplyScalar(Math.exp(-dt*2));
  }p.swimPrev=arm.desired.clone();
  const b=p.ctrl.controls;if(p.assist&&b&&b.touch&&!b.click){vel.addScaledVector(forward,-b.y*PS*dt*10).addScaledVector(rt,b.x*PS*dt*10)}
  const y=P.y,n=Math.max(1,Math.ceil(vel.length()*dt/(.04*PS)));for(let i=0;i<Math.min(n,48);i++){stepMove(dt/Math.min(n,48));wallFix()}sweepFloor(y);
  const g=hit(P.clone().add(V3(0,.08,0)),DOWN,2*PS+.08);gPrev=!!(g&&P.y-1.1*CSC<=g.point.y+.03*PS);
  if(jumpReq&&gPrev)vel.y=3.8*PS;jumpReq=false;const friction=gPrev?(SNOW(P)?.18:5.5):.25;vel.x*=Math.exp(-dt*friction);vel.z*=Math.exp(-dt*friction);vel.clampLength(0,6*PS);
  if(P.y<floorMin){P.copy(spawnPos());p.reset()}
 };
 p.afterPose=(fw,rt)=>{
  if(!p.presenting)return;
  if(!arm.contact){const shift=P.clone().sub(lastPosition);tgt[1].add(shift);arm.target.copy(tgt[1]);arm.last=arm.target.clone()}
  arm2(LM,'R',tgt[1],fw,rt,PS*1.32);
  const q=p.ctrl.present?p.ctrl.quat:p.head;
  handPose(LM,'R',V3(0,0,-1).applyQuaternion(q),V3(1,0,0).applyQuaternion(q),p.ctrl.trigger?[.65,.9,.9]:[.1,.1,.1]);
  HO[1].d=V3(0,0,-1).applyQuaternion(q);HO[1].A=V3(1,0,0).applyQuaternion(q);
  HUD3.m.visible=false;
  if(P.distanceTo(lastPosition)>PS*2)p.reset();
  p.drawHUD();
 };
 p.drawHUD=()=>{
  const now=performance.now(),b=p.ctrl.controls;
  const status=!p.ctrl.present?'SEM POSE':arm.contact?'APOIADA - EMPURRE / LEVANTE PARA SOLTAR':'NO AR - APONTE MAIS PARA BAIXO';
  const diag=[p.mode+' | '+status,'Piso: '+(p.nearFloor==null?'fora de alcance':p.nearFloor.toFixed(2)+' m virtuais')+' | Caminho: '+arm.travel.toFixed(2)+' m virtuais','3DoF: gire o controle; deslocar sem girar nao e medido.'];
  const text=JSON.stringify([p.menu,p.choice,p.assist,diag,p.reason,b&&b.pressed]);
  if(text!==lastText&&now-hudTime>100){lastText=text;hudTime=now;ctx.clearRect(0,0,768,448);ctx.fillStyle='rgba(9,22,16,.93)';ctx.fillRect(0,0,768,p.menu?448:150);ctx.textBaseline='middle';ctx.font='bold 25px sans-serif';ctx.fillStyle='#e6ffe9';ctx.fillText('GORILLA RUSH V45 - PICO G2 / TESTE 1',20,25);
   ctx.font='20px sans-serif';if(p.menu){rows.forEach((r,i)=>{ctx.fillStyle=i===p.choice?'#ffcc66':'#e6ffe9';ctx.fillText((i===p.choice?'> ':'  ')+r+(i===2?(p.assist?'LIGADA':'DESLIGADA'):''),20,80+i*45)});ctx.font='16px sans-serif';ctx.fillStyle='#b9e4c4';ctx.fillText(p.reason.slice(0,86),20,330);ctx.fillText('Botoes: '+(b?b.pressed.join(','):'sem pose')+' | Touchpad cima/baixo: escolher',20,360);ctx.fillText('Gatilho: confirmar | Segure clique do touchpad: menu',20,390)}else{diag.forEach((t,i)=>{ctx.fillStyle=i===0?'#ffcc66':'#d4eadb';ctx.fillText(t,20,62+i*29)})}tex.needsUpdate=true;
  }
  const e=P.clone().add(V3(0,EYEUP,0)),f=V3(0,0,-1).applyQuaternion(p.head),u=V3(0,1,0).applyQuaternion(p.head);
  panel.position.copy(e).addScaledVector(f,1.8*PS).addScaledVector(u,p.menu?.1*PS:.65*PS);panel.quaternion.copy(p.head);panel.scale.setScalar(PS);panel.visible=true;
  ray.visible=p.menu&&p.ctrl.present;if(ray.visible){ray.position.copy(LM.hR.getWorldPosition(V3()));ray.quaternion.copy(p.ctrl.quat);ray.scale.setScalar(1.5*PS);
   const rc=new THREE.Raycaster(ray.position,V3(0,0,-1).applyQuaternion(p.ctrl.quat));panel.updateMatrixWorld();const h=rc.intersectObject(panel)[0];if(h&&h.uv){const y=(1-h.uv.y)*448,i=Math.round((y-80)/45);if(i>=0&&i<rows.length&&p.choice!==i){p.choice=i;lastText=''}}
  }
 };
 function exit(){p.presenting=false;p.mode='flat';p.frame=null;p.session=null;p.display=null;p.source=null;p.ctrl.present=false;p.menu=false;p.reset();panel.visible=ray.visible=false;rig.position.set(0,0,0);rig.quaternion.identity();rig.scale.setScalar(1);R.xr.enabled=false;vrView=false;rs();R.setAnimationLoop(frame);document.body.classList.remove('vr-presenting');p.refreshStart();document.getElementById('pico-status').textContent='VR encerrado. Clique em INICIAR VR para voltar.'}
 p.stop=()=>{if(p.session)p.session.end();else if(p.display)p.display.exitPresent()};
 p.render=()=>{
  if(!p.presenting)return false;
  const e=P.clone().add(V3(0,EYEUP,0));
  if(p.mode==='webxr'){
   rig.scale.setScalar(PS);rig.quaternion.copy(yawQ);rig.position.copy(e).sub(p.headPosition.clone().applyQuaternion(yawQ).multiplyScalar(PS));cam.position.copy(p.headPosition);cam.quaternion.copy(p.rawHead);rig.updateMatrixWorld(true);R.render(S,cam);
   // Restore world camera for source systems and the next frame.
   rig.scale.setScalar(1);rig.quaternion.identity();rig.position.set(0,0,0);cam.position.copy(e);cam.quaternion.copy(p.head);rig.updateMatrixWorld(true);p.stats.eyes+=2;return true;
  }
  const fd=p.fd;p.display.getFrameData(fd);R.setScissorTest(true);
  for(let i=0;i<2;i++){
   const mat=new THREE.Matrix4().fromArray(i?fd.rightViewMatrix:fd.leftViewMatrix).invert(),pos=V3().setFromMatrixPosition(mat).sub(p.headPosition).multiplyScalar(PS).applyQuaternion(yawQ).add(e),q=new THREE.Quaternion().setFromRotationMatrix(mat).premultiply(yawQ);
   ecam.matrix.compose(pos,q,V3(1,1,1));ecam.matrixWorldNeedsUpdate=true;ecam.projectionMatrix.fromArray(i?fd.rightProjectionMatrix:fd.leftProjectionMatrix);ecam.projectionMatrixInverse.copy(ecam.projectionMatrix).invert();R.setViewport(i*p.eyeW,0,p.eyeW,p.eyeH);R.setScissor(i*p.eyeW,0,p.eyeW,p.eyeH);R.render(S,ecam);p.stats.eyes++;
  }R.setScissorTest(false);p.display.submitFrame();return true;
 };
 p.refreshStart=()=>{const button=document.getElementById('pico-vr');button.disabled=!(p.api&&pl&&mapDone)||p.starting||p.presenting;button.setAttribute('aria-busy',String(p.starting))};
 p.start=async()=>{
  if(!pl||!mapDone||p.presenting||p.starting)return;
  p.starting=true;p.refreshStart();
  const label=document.getElementById('pico-status');label.textContent='Solicitando entrada em VR…';
  try{
   if(p.api==='webxr'){
    // requestSession remains directly in the user gesture; no unrelated async work first.
    const session=await navigator.xr.requestSession('immersive-vr',{optionalFeatures:['local-floor']});
    p.session=session;R.xr.enabled=true;R.xr.setReferenceSpaceType('local');R.xr.setFramebufferScaleFactor(.55);
    try{await R.xr.setSession(session)}catch(e){await session.end();p.session=null;R.xr.enabled=false;throw e}
    p.mode='webxr';p.presenting=true;session.addEventListener('end',exit);session.addEventListener('selectstart',e=>p.select.set(e.inputSource,true));session.addEventListener('selectend',e=>p.select.set(e.inputSource,false));
    R.setAnimationLoop((t,f)=>{p.frame=f;try{frame(t)}finally{p.frame=null}});
   }else if(p.api==='webvr'){
    p.display=p.availableDisplay;const l=p.display.getEyeParameters('left'),r=p.display.getEyeParameters('right'),scale=Math.min(1,Math.sqrt(1e6/(Math.max(l.renderWidth,r.renderWidth)*Math.max(l.renderHeight,r.renderHeight))));
    p.eyeW=Math.round(Math.max(l.renderWidth,r.renderWidth)*scale);p.eyeH=Math.round(Math.max(l.renderHeight,r.renderHeight)*scale);R.setPixelRatio(1);R.setSize(p.eyeW*2,p.eyeH,false);p.display.depthNear=.01;p.display.depthFar=120;
    await p.display.requestPresent([{source:R.domElement}]);p.fd=new VRFrameData();p.mode='webvr';p.presenting=true;R.setAnimationLoop(null);
    const loop=t=>{if(!p.presenting||!p.display||!p.display.isPresenting)return;p.display.requestAnimationFrame(loop);frame(t)};p.display.requestAnimationFrame(loop);
   }else throw Error('Este navegador nao expoe WebXR nem WebVR imersivo. Nao sera usado VR Box como substituto.');
   if(state!=='play'){solo=true;NET.mode='cas';NET.code='SOLO';NET.host=true;startGame();initAudio()}
   vrView=false;vrOn=false;p.yaw=yw;p.reset();p.menu=false;document.body.classList.add('vr-presenting');label.textContent='VR ativo: '+p.mode+' | somente mao direita';
   ['joy','fup','fdn','selb','xh'].forEach(id=>$(id).style.display='none');
   if(!ready){RIGHT_ARM.strip(pl);LM.uR.scale.multiplyScalar(1.32);ready=true}
  }catch(e){label.textContent='Nao foi possivel entrar: '+(e.message||e);console.warn('VR entry',e);if(!p.presenting){p.display=null;R.xr.enabled=false;rs()}}finally{p.starting=false;p.refreshStart()}
 };
 addEventListener('vrdisplaypresentchange',()=>{if(p.mode==='webvr'&&p.display&&!p.display.isPresenting)exit()});
 addEventListener('keydown',e=>{if(state==='play'&&(e.code==='KeyM'||e.code==='Escape'))p.toggleMenu();if(p.menu){if(e.code==='ArrowDown')p.choice=(p.choice+1)%rows.length;if(e.code==='ArrowUp')p.choice=(p.choice+rows.length-1)%rows.length;if(e.code==='Enter')p.runChoice()}});
 addEventListener('visibilitychange',()=>{if(document.hidden)p.reset()});
 p.resize=()=>p.presenting;
 p.detect=async()=>{
  p.api=null;
  if(!/GorillaV45Android/.test(navigator.userAgent)){
   try{if(navigator.xr&&await navigator.xr.isSessionSupported('immersive-vr'))p.api='webxr'}catch(e){}
   if(!p.api)try{if(navigator.getVRDisplays&&typeof VRFrameData!=='undefined'){const displays=await navigator.getVRDisplays();p.availableDisplay=displays.find(d=>d.capabilities&&d.capabilities.canPresent);if(p.availableDisplay)p.api='webvr'}}catch(e){}
  }
  document.getElementById('pico-status').textContent=p.api?'API disponivel: '+p.api+' - teste no headset necessario.':'Sem API VR imersiva. Modo tela apenas; nao e VR nativo.';
  p.refreshStart();
 };
 document.getElementById('pico-vr').onclick=p.start;document.getElementById('pico-solo').onclick=()=>enter('solo');
 document.getElementById('pico-diagnostic').onclick=()=>{const data={version:'v45-start-vr-2',userAgent:navigator.userAgent,api:p.api,mode:p.mode,source:p.reason,present:p.ctrl.present,buttons:p.ctrl.controls,contact:arm.contact,floor:p.nearFloor,travel:arm.travel,position:P.toArray(),scale:PS,head:p.head.toArray(),controller:p.ctrl.quat.toArray(),renderer:R.info.render};const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));a.download='Gorilla-v45-diagnostico.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)};
 try{Object.assign(p.buttonMap,JSON.parse(localStorage.getItem('v45-buttons')||'{}'))}catch(e){}
 for(const k of ['padClick','padTrig','padApp']){const s=document.getElementById(k);s.value=p.buttonMap[k];s.onchange=p.configureButtons}
 p.detect();return p;
})();
