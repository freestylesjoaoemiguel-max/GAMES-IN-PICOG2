/* Browser integration for the supplied v45. No native SDK emulation.
 * WebXR local-space poses or legacy WebVR frame data only. Runtime owns stereo.
 */
'use strict';
const PICO=(()=>{
 const p={starting:false,mode:'flat',presenting:false,frame:null,session:null,display:null,head:new THREE.Quaternion(),rawHead:new THREE.Quaternion(),headPosition:V3(),yaw:0,source:null,menu:false,choice:0,assist:false,nearFloor:null,buttonMap:{padClick:0,padTrig:1,padApp:2},ctrl:{present:false,quat:new THREE.Quaternion(),raw:new THREE.Quaternion(),trigger:false},select:new WeakMap(),needsRelease:true,reason:'Entre em VR para ler o controle',stats:{frames:0,eyes:0},calibration:null};
 const surface=HAND_SURFACE.create(THREE,handTriangles);p.surface=surface;
 const motor=GMOTION_PORT.create(THREE);p.motor=motor;
 const makeHand=side=>({side,present:false,quat:new THREE.Quaternion(),raw:new THREE.Quaternion(),position:V3(),trigger:false,controls:null,source:null,key:null,tracking:'absent',needsRelease:true,calibration:null,radius:.36,arm:ARM_PHYSICS.create(THREE,surface.solve)});
 p.hands={left:makeHand('left'),right:makeHand('right')};const hands=Object.values(p.hands);
 let arm=p.hands.right.arm;p.arm=arm;p.ctrl=p.hands.right;p.handRadius=.36;
 const yawQ=new THREE.Quaternion(),lastPosition=V3(),eye=V3(),forward=V3(0,0,-1),right=V3(1,0,0);
 const rig=new THREE.Group();S.add(rig);rig.add(cam);const ecam=new THREE.PerspectiveCamera();ecam.matrixAutoUpdate=false;
 let lastText='',hudTime=0,ready=false;
 const c=document.createElement('canvas');c.width=768;c.height=448;const ctx=c.getContext('2d'),tex=new THREE.CanvasTexture(c);tex.minFilter=THREE.LinearFilter;tex.generateMipmaps=false;
 const panel=new THREE.Mesh(new THREE.PlaneGeometry(1.65,.96),new THREE.MeshBasicMaterial({map:tex,transparent:true,depthTest:false,depthWrite:false,fog:false}));panel.renderOrder=10000;panel.frustumCulled=false;panel.visible=false;S.add(panel);p.panel=panel;
 const ray=new THREE.Line(new THREE.BufferGeometry().setFromPoints([V3(),V3(0,0,-1)]),new THREE.LineBasicMaterial({color:0xffcc66,depthTest:false}));ray.renderOrder=10001;ray.visible=false;S.add(ray);
 const rows=['CONTINUAR','CALIBRAR: aponte para a frente','ANDAR PELO TOUCHPAD (OPCIONAL): ','VOLTAR AO INICIO DO MAPA','SAIR DO VR'];
 p.immersive=()=>p.presenting;
 p.reset=()=>{motor.reset();p.supported=false;for(const h of hands){h.arm.reset();h.needsRelease=true;h.swimPrev=null}p.needsRelease=true;vel.set(0,0,0);touch[0]=touch[1]=false;slot.forEach(s=>{s.p=null;s.t=0});hv.forEach(v=>v.set(0,0,0))};
 p.toggleMenu=()=>{p.menu=!p.menu;p.reset();lastText=''};
 p.runChoice=()=>{if(p.choice===0)p.toggleMenu();else if(p.choice===1){if(p.ctrl.present){for(const h of hands)if(h.present)h.calibration=h.raw.clone().invert().multiply(p.rawHead);p.reset();p.menu=false}}else if(p.choice===2){p.assist=!p.assist;lastText=''}else if(p.choice===3){P.copy(spawnPos());p.reset();p.menu=false}else p.stop()};
 p.configureButtons=()=>{for(const k of ['padClick','padTrig','padApp'])p.buttonMap[k]=+document.getElementById(k).value;try{localStorage.setItem('v45-buttons',JSON.stringify(p.buttonMap))}catch(e){}};
 p.beforeFrame=dt=>{
  if(!p.presenting)return;rig.position.set(0,0,0);rig.quaternion.identity();rig.scale.setScalar(1);yawQ.setFromAxisAngle(V3(0,1,0),p.yaw);
  let sources=[],ref=null,validHead=false;
  if(p.mode==='webxr'&&p.frame){
   ref=R.xr.getReferenceSpace();try{const viewer=p.frame.getViewerPose(ref),q=viewer&&TRACKING.orientation(viewer.transform.orientation);if(q){p.rawHead.fromArray(q);p.headPosition.fromArray(TRACKING.position(viewer.transform.position)||[0,0,0]);validHead=true}}catch(e){}
   sources=Array.from(p.session.inputSources||[]).filter(s=>s&&s.targetRayMode==='tracked-pointer'&&(s.gripSpace||s.targetRaySpace));
  }else if(p.mode==='webvr'&&p.display){p.display.getFrameData(p.fd);const pose=p.fd.pose,q=pose&&TRACKING.orientation(pose.orientation);if(q){p.rawHead.fromArray(q);p.headPosition.fromArray(TRACKING.position(pose.position)||[0,0,0]);validHead=true}
   sources=Array.from(navigator.getGamepads?navigator.getGamepads():[]).filter(g=>g&&g.connected&&g.pose).sort((a,b)=>a.index-b.index);
  }
  const key=s=>p.mode==='webvr'?s.index+':'+s.id:s;
  const previous={};for(const h of hands)previous[h.side]=sources.find(s=>key(s)===h.key);
  const routed=TRACKING.pair(sources,previous);p.head.copy(yawQ).multiply(p.rawHead);cam.quaternion.copy(p.head);
  let toggle=false,confirm=false,turn=0;
  for(const h of hands){
   const src=routed[h.side];let pose=null,g=null,q=null,position=null;
   if(src&&validHead)try{if(p.mode==='webxr'){pose=p.frame.getPose(src.gripSpace||src.targetRaySpace,ref);g=src.gamepad;q=pose&&TRACKING.orientation(pose.transform.orientation);position=TRACKING.measured(pose,false)}else{pose=src.pose;g=src;q=TRACKING.orientation(pose.orientation);position=TRACKING.measured(pose,true)}}catch(e){}
   const id=src&&key(src),tracking=q?(position?'measured':'estimated-3dof'):'absent';
   if(id!==h.key||tracking!==h.tracking){motor.reset();h.arm.reset();h.needsRelease=true;h.lastTrigger=h.lastClick=h.lastMenu=h.longClick=false;h.swimPrev=null}
   h.key=id;h.source=src;h.tracking=tracking;h.present=!!q;h.trigger=false;h.controls=null;
   if(!q)continue;
   h.raw.fromArray(q);h.quat.copy(yawQ).multiply(h.raw);
   if(position)h.position.fromArray(position).sub(p.headPosition).applyQuaternion(yawQ).multiplyScalar(PS).add(P).add(V3(0,EYEUP,0));
   const b=TRACKING.controls(g,p.buttonMap,p.mode==='webxr'&&p.select.get(src));h.controls=b;h.trigger=b.trigger;if(!b.trigger)h.needsRelease=false;
   if(b.menu&&!h.lastMenu)toggle=true;h.lastMenu=b.menu;
   const now=performance.now();if(b.click&&!h.lastClick){h.clickTime=now;h.clickX=b.x;h.longClick=false}
   if(b.click&&!h.longClick&&now-h.clickTime>600){h.longClick=true;toggle=true}
   if(!b.click&&h.lastClick&&!h.longClick){if(p.menu)confirm=true;else if(Math.abs(h.clickX)>.55)turn=(h.clickX<0?1:-1)*Math.PI/6;else jumpReq=true}
   if(p.menu&&b.trigger&&!h.lastTrigger)confirm=true;
   if(p.menu&&b.touch&&Math.abs(b.y)>.6&&!h.menuAxis){p.choice=(p.choice+(b.y>0?1:rows.length-1))%rows.length;lastText=''}h.menuAxis=b.touch&&Math.abs(b.y)>.6;
   h.lastTrigger=b.trigger;h.lastClick=b.click;
  }
  p.ctrl=p.hands.right.present?p.hands.right:p.hands.left.present?p.hands.left:p.hands.right;arm=p.ctrl.arm;p.arm=arm;p.source=p.ctrl.source;p.needsRelease=p.ctrl.needsRelease;
  p.reason=hands.filter(h=>h.present).map(h=>(h.side==='left'?'E':'D')+': '+(h.tracking==='measured'?'posicao medida':'3DoF estimado')).join(' | ')||'SEM POSE: reconecte o controle';
  if(toggle)p.toggleMenu();if(confirm)p.runChoice();if(turn){p.yaw+=turn;p.reset()}
  p.stats.frames++;
 };
 p.handFrame=(dt,fw,rt)=>{
  forward.copy(fw);right.copy(rt);eye.copy(P).add(V3(0,EYEUP,0));lastPosition.copy(P);
  for(let i=0;i<hands.length;i++){const h=hands[i],a=h.arm,was=a.contact,aq=h.calibration?h.quat.clone().multiply(h.calibration):h.quat;
   a.update({position:P,eye,quat:h.quat,armQuat:aq,forward:fw,right:rt,side:h.side,handPosition:h.tracking==='measured'?h.position:null,scale:PS,handRadius:h.radius,dt,present:h.present,paused:p.menu||ZIP.on,trigger:h.trigger,needsRelease:h.needsRelease});
   tgt[i].copy(a.target);touch[i]=a.contact;if(a.contact&&!was)tapSound(i,a.cls,.6);
  }
  p.reaction=ARM_PHYSICS.reaction(THREE,hands.map(h=>h.arm));
  const floor=hit(arm.target,V3(0,-1,0),2*PS);p.nearFloor=floor?floor.distance/PS:null;
 };
 p.physics=(dt,d,rt,now)=>{
  if(p.menu){vel.set(0,0,0);jumpReq=false;return}
  if(ZIP.on){motor.reset();for(const h of hands)h.arm.reset();zipRide(dt);return}
  // ZIP sections 9-11: follower error -> body velocity -> actual velocity history.
  // Reuse the existing single collision integrator; never run a second gravity step.
  const drive=p.reaction,contactCount=motor.apply(hands.map(h=>h.arm),vel,dt,PS);
  p.supported=contactCount>0;
  if(!p.supported)vel.y-=GMOTION_PORT.GRAVITY*PS*dt*(WTR.in?.1:1);
  for(const h of hands){const a=h.arm,local=a.desired.clone().sub(lastPosition);if(WTR.in&&h.present&&h.swimPrev){const delta=local.clone().sub(h.swimPrev);if(delta.length()<PS*.5)vel.addScaledVector(delta,-2/Math.max(dt,.001)/Math.max(1,hands.filter(x=>x.present).length))}h.swimPrev=local}
  if(WTR.in)vel.multiplyScalar(Math.exp(-dt*2));
  const b=p.ctrl.controls;if(p.assist&&b&&b.touch&&!b.click)vel.addScaledVector(forward,-b.moveY*PS*dt*10).addScaledVector(rt,b.moveX*PS*dt*10);
  const grounded=()=>{const g=hit(P.clone().add(V3(0,.08,0)),DOWN,2*PS+.08),gap=g?P.y-1.1*CSC-g.point.y:Infinity;return !!(g&&g.n.y>.55&&gap>=-.02*PS&&gap<.035*PS&&vel.y<=.01*PS)};
  if(jumpReq&&(grounded()||hands.some(h=>h.arm.active&&h.arm.contact&&h.arm.normal.y>.55))){vel.y=4.5*PS;motor.reset();for(const h of hands)h.arm.reset()}jumpReq=false;
  const speed=Math.hypot(vel.x,vel.z);if(speed>8*PS){vel.x*=8*PS/speed;vel.z*=8*PS/speed}vel.y=Math.max(-10*PS,Math.min(6*PS,vel.y));
  const y=P.y,n=Math.min(48,Math.max(1,Math.ceil(vel.length()*dt/(.04*PS))));for(let i=0;i<n;i++){stepMove(dt/n);wallFix()}sweepFloor(y);
  gPrev=grounded();
  // The supplied motor carries momentum after release; no old walking friction.
  motor.record(P.clone().sub(lastPosition),dt);
  p.grounded=gPrev;p.bodySpeed=vel.length()/PS;
  if(drive&&drive.count)for(const h of hands)if(h.arm.driving)h.arm.travel+=Math.hypot(P.x-lastPosition.x,P.z-lastPosition.z)/PS;
  if(P.y<floorMin){P.copy(spawnPos());p.reset()}
 };
 p.afterPose=(fw,rt)=>{
  if(!p.presenting)return;if(P.distanceTo(lastPosition)>PS*2){p.reset();return}
  const shift=P.clone().sub(lastPosition);
  RIGHT_ARM.visible(pl,p.hands.left.present,p.hands.right.present||!p.hands.left.present);
  for(let i=0;i<hands.length;i++){
   const h=hands[i],a=h.arm,k=i?'R':'L',wrist=LM['h'+k];a.afterMove(shift);tgt[i].copy(a.target);touch[i]=a.contact;
   if(!h.wristRest)h.wristRest=wrist.position.clone();wrist.position.copy(h.wristRest);wrist.updateMatrixWorld(true);
   arm2(LM,k,tgt[i],fw,rt,PS*1.32);wrist.position.copy(wrist.parent.worldToLocal(tgt[i].clone()));wrist.updateMatrixWorld(true);
   const q=h.present?h.quat:p.head;handPose(LM,k,V3(0,0,-1).applyQuaternion(q),V3(1,0,0).applyQuaternion(q),h.trigger?[.65,.9,.9]:[.1,.1,.1]);
   HO[i].d=V3(0,0,-1).applyQuaternion(q);HO[i].A=V3(1,0,0).applyQuaternion(q);
   if(!h.radiusMeasured){h.radius=Math.max(.36,RIGHT_ARM.radius(pl,wrist)/PS*1.08);h.radiusMeasured=true}
  }
  p.handRadius=p.ctrl.radius;HUD3.m.visible=false;p.drawHUD();
 };
 p.drawHUD=()=>{
  const now=performance.now(),b=p.ctrl.controls;
  const status=!p.ctrl.present?'SEM POSE':hands.some(h=>h.arm.contact)?'CONTATO - EMPURRE / LEVANTE PARA SOLTAR':p.ctrl.tracking==='measured'?'NO AR - APROXIME A MAO DA SUPERFICIE':'NO AR - GIRE O CONTROLE PARA BAIXO';
  const diag=[p.mode+' | '+status,p.reason+(p.assist?' | TOUCHPAD LIGADO':' | GMOTION / SO MAOS'),'Vel: '+(p.bodySpeed||0).toFixed(2)+' | reto: '+Math.round(-(arm.neutral||0)*57.3)+' graus | piso: '+(p.nearFloor==null?'longe':p.nearFloor.toFixed(2))];
  const text=JSON.stringify([p.menu,p.choice,p.assist,diag,p.reason,b&&b.pressed]);
  if(text!==lastText&&now-hudTime>100){lastText=text;hudTime=now;ctx.clearRect(0,0,768,448);ctx.fillStyle='rgba(9,22,16,.93)';ctx.fillRect(0,0,768,p.menu?448:150);ctx.textBaseline='middle';ctx.font='bold 25px sans-serif';ctx.fillStyle='#e6ffe9';ctx.fillText('GORILLA RUSH V45 - PICO G2 / FISICA 1.0.8',20,25);
   ctx.font='20px sans-serif';if(p.menu){rows.forEach((r,i)=>{ctx.fillStyle=i===p.choice?'#ffcc66':'#e6ffe9';ctx.fillText((i===p.choice?'> ':'  ')+r+(i===2?(p.assist?'LIGADA':'DESLIGADA'):''),20,80+i*45)});ctx.font='16px sans-serif';ctx.fillStyle='#b9e4c4';ctx.fillText(p.reason.slice(0,86),20,330);ctx.fillText('Botoes: '+(b?b.pressed.join(','):'sem pose')+' | Touchpad cima/baixo: escolher',20,360);ctx.fillText('Gatilho: confirmar | Segure clique do touchpad: menu',20,390)}else{diag.forEach((t,i)=>{ctx.fillStyle=i===0?'#ffcc66':'#d4eadb';ctx.fillText(t,20,62+i*29)})}tex.needsUpdate=true;
  }
  const e=P.clone().add(V3(0,EYEUP,0)),f=V3(0,0,-1).applyQuaternion(p.head),u=V3(0,1,0).applyQuaternion(p.head);
  panel.position.copy(e).addScaledVector(f,1.8*PS).addScaledVector(u,p.menu?.1*PS:.65*PS);panel.quaternion.copy(p.head);panel.scale.setScalar(PS);panel.visible=true;
  ray.visible=p.menu&&p.ctrl.present;if(ray.visible){ray.position.copy(LM[p.ctrl.side==='left'?'hL':'hR'].getWorldPosition(V3()));ray.quaternion.copy(p.ctrl.quat);ray.scale.setScalar(1.5*PS);
   const rc=new THREE.Raycaster(ray.position,V3(0,0,-1).applyQuaternion(p.ctrl.quat));panel.updateMatrixWorld();const h=rc.intersectObject(panel)[0];if(h&&h.uv){const y=(1-h.uv.y)*448,i=Math.round((y-80)/45);if(i>=0&&i<rows.length&&p.choice!==i){p.choice=i;lastText=''}}
  }
 };
 function exit(){for(const h of hands){if(h.wristRest){const w=LM[h.side==='left'?'hL':'hR'];w.position.copy(h.wristRest);w.updateMatrixWorld(true);h.wristRest=null}h.present=false}if(p.armScales){LM.uL.scale.copy(p.armScales.left);LM.uR.scale.copy(p.armScales.right)}ready=false;RIGHT_ARM.visible(pl,true,true);p.presenting=false;p.mode='flat';p.frame=null;p.session=null;p.display=null;p.source=null;p.ctrl.present=false;p.menu=false;p.reset();panel.visible=ray.visible=false;rig.position.set(0,0,0);rig.quaternion.identity();rig.scale.setScalar(1);R.xr.enabled=false;vrView=false;rs();R.setAnimationLoop(frame);document.body.classList.remove('vr-presenting');p.refreshStart();document.getElementById('pico-status').textContent='VR encerrado. Clique em INICIAR VR para voltar.'}
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
   vrView=false;vrOn=false;p.yaw=yw;p.reset();p.menu=false;document.body.classList.add('vr-presenting');label.textContent='VR ativo: '+p.mode+' | fontes reais de controle';
   ['joy','fup','fdn','selb','xh'].forEach(id=>$(id).style.display='none');
   if(!ready){p.armScales={left:LM.uL.scale.clone(),right:LM.uR.scale.clone()};LM.uL.scale.multiplyScalar(1.32);LM.uR.scale.multiplyScalar(1.32);ready=true}
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
 document.getElementById('pico-diagnostic').onclick=()=>{const data={version:'v45-gmotion-9',touchpadAssist:p.assist,motor:'gmotion-port',supported:p.supported,releaseSpeed:motor.throwSpeed,velocityAverage:motor.average.toArray(),hands:hands.map(h=>({side:h.side,present:h.present,tracking:h.tracking,source:h.source&&(h.source.id||(h.source.profiles||[]).join(',')),position:h.tracking==='measured'?h.position.toArray():null,rotation:h.raw.toArray(),buttons:h.controls,contact:h.arm.contact,velocity:h.arm.handVelocity.toArray(),reaction:h.arm.reaction.toArray(),anchor:h.arm.plant&&h.arm.plant.toArray()})),bodyVelocity:vel.toArray(),grounded:p.grounded,userAgent:navigator.userAgent,api:p.api,mode:p.mode,source:p.reason,present:p.ctrl.present,buttons:p.ctrl.controls,contact:arm.contact,anchor:arm.plant&&arm.plant.toArray(),handRadius:arm.radius,target:arm.target.toArray(),desired:arm.desired.toArray(),floor:p.nearFloor,travel:arm.travel,position:P.toArray(),scale:PS,head:p.head.toArray(),controller:p.ctrl.quat.toArray(),renderer:R.info.render};const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));a.download='Gorilla-v45-diagnostico.json';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)};
 try{Object.assign(p.buttonMap,JSON.parse(localStorage.getItem('v45-buttons')||'{}'))}catch(e){}
 for(const k of ['padClick','padTrig','padApp']){const s=document.getElementById(k);s.value=p.buttonMap[k];s.onchange=p.configureButtons}
 p.detect();return p;
})();
