// One controller, ONE RIGHT ARM. No swapping, mirrored hand or delayed second arm.
// G2 provides orientation (3DoF); hand position remains an estimated arm model.
'use strict';
const LOCO=(function(){
  const L={anchor:null,climbVel:new THREE.Vector3(),desiredW:[new THREE.Vector3(),new THREE.Vector3()],ready:false,grace:.08,needsRelease:true,surface:null,lastHand:null,pushing:false,groundPlant:null,groundNormal:new THREE.Vector3(0,1,0),local:new THREE.Vector3(.22,-.25,.65),pose:new THREE.Quaternion()};
  const _d=new THREE.Vector3(),_hq=new THREE.Quaternion(),_v=new THREE.Vector3(),UP=new THREE.Vector3(0,1,0);
  const fq=new THREE.Quaternion(),lastFw=new THREE.Vector3(),pointing=new THREE.Vector3(),lastPointing=new THREE.Vector3();let fqInit=false,L1b=0,L2b=0;
  L.ctrlHand=()=>1;
  HOOK.handEnabled=i=>i===1;
  L.speedMul=()=>({fast:1,medium:.7,slow:.45})[SETTINGS.speedMode]||1;
  L.applyTune=()=>{const k=L.speedMul();TUNE.imp=SETTINGS.impulse*k;TUNE.jmp=(1+(SETTINGS.impulse-1)*.32)*Math.sqrt(k);TUNE.grav=SETTINGS.gravity;TUNE.fric=SETTINGS.friction;TUNE.max=SETTINGS.maxSpeed*k;TUNE.spd=k;SHOW_NAMES=!!SETTINGS.names;window.MUSIC&&MUSIC.volume()};
  L.applyArms=()=>{if(!LM||!LM.uR)return;if(!L1b){L1b=L1;L2b=L2}const k=SETTINGS.arm;
    LM.uR.scale.setScalar(k);for(const id in RO){const M=RO[id];if(M.uR)M.uR.scale.setScalar(k)}
    L1=L1b*k;L2=L2b*k;ARM=(L1+L2)*.98;L.reset()};
  const active=()=>INPUT.ctrl.present&&VRP.threeDoF()&&state==='play';
  HOOK.bodyFw=(fw,d)=>{VRP.headWorldQuat(_hq);_d.set(0,0,-1).applyQuaternion(_hq);
    if(Math.abs(_d.y)>.97)_d.set(0,1,0).applyQuaternion(_hq).multiplyScalar(-Math.sign(_d.y));
    fw.set(_d.x,0,_d.z);if(fw.lengthSq()<1e-6)fw.set(0,0,-1);fw.normalize();d.set(0,0,-1).applyQuaternion(_hq)};
  // Reset does NOT throw the player. A held trigger must be released before grabbing again.
  L.reset=()=>{const climbing=!!L.anchor||L.pushing;L.anchor=null;L.climbVel.set(0,0,0);L.ready=false;L.grace=.08;L.needsRelease=true;fqInit=false;L.surface=null;L.lastHand=null;L.pushing=false;L.groundPlant=null;
    for(let i=0;i<2;i++){touch[i]=false;hv[i].set(0,0,0);slot[i].p=null;slot[i].t=0;}
    if(climbing)vel.set(0,0,0);
  };
  L.release=(throwPlayer=false)=>{L.groundPlant=null;if(!L.anchor&&!L.pushing)return;L.anchor=null;L.pushing=false;
    if(!throwPlayer){L.climbVel.set(0,0,0);vel.set(0,0,0);return}
    // Leaving a real surface keeps the measured push speed; no arbitrary jump multiplier.
    vel.copy(L.climbVel);vel.clampLength(0,TUNE.max);vel.y=Math.min(vel.y,5.2*TUNE.jmp);
  };
  HOOK.noPush=i=>i===0||INPUT.context==='menu'||VRP.threeDoF();
  HOOK.hand=(i,fw,rt,dt)=>{
    if(i===0)return null; // Never let the disabled arm reset the right-arm filter.
    if(!active()){if(L.ready||L.anchor)L.reset();return null}
    if(INPUT.context==='menu'&&(L.anchor||L.pushing))L.reset();
    dt=Math.max(.001,Math.min(.05,dt));
    const q=INPUT.ctrl.quat;
    // Only a jump of the POINTING direction or body yaw reseeds position physics.
    // Rolling the wrist through 180/360 degrees must never cancel a real floor contact.
    pointing.set(0,0,-1).applyQuaternion(q);
    if(fqInit&&(lastPointing.dot(pointing)<Math.cos(1.2)||lastFw.dot(fw)<.7))L.reset();
    if(!fqInit){fq.copy(q);fqInit=true}else{
      const angle=2*Math.acos(Math.min(1,Math.abs(fq.dot(q))));
      const alpha=1-Math.exp(-dt*(45-25*SETTINGS.smooth));
      fq.slerp(q,angle>1e-6?Math.min(alpha,8*dt/angle):1).normalize();
    }
    lastPointing.copy(pointing);lastFw.copy(fw);
    if(!INPUT.ctrl.trigger&&INPUT.context==='game')L.needsRelease=false;
    // Wrist rotation is the same measured quaternion as the pointer (including roll).
    // Only POSITION is estimated. Neutral wrist is in front of the player, elbow bent.
    L.pose.copy(q);_d.set(0,0,-1).applyQuaternion(fq);
    const down=Math.max(0,-_d.y),reach=Math.min(ARM*.96,(.55+.78*down)*SETTINGS.reach);
    const o=new THREE.Vector3(.22+_d.dot(rt)*reach,-.25+_d.y*reach,.10+_d.dot(fw)*reach);
    L.local.copy(o);L.desiredW[1].copy(P).addScaledVector(rt,o.x).addScaledVector(UP,o.y).addScaledVector(fw,o.z);
    if(!L.ready){hl[1].copy(o);hv[1].set(0,0,0);touch[1]=false;L.ready=true;}
    L.grace=Math.max(0,L.grace-dt);return o;
  };
  // One physical hand target shared by the collider, visible wrist and pointer.
  HOOK.handContact=(i,p,fw,rt)=>{
    if(i!==1||!active()||INPUT.context==='menu'){L.surface=null;L.groundPlant=null;return false}
    L.desiredW[1].copy(p);
    if(L.grace>0){L.lastHand=p.clone();L.surface=null;return false}
    if(L.anchor&&INPUT.ctrl.trigger){p.copy(L.anchor);L.surface={contact:true};return true}
    L.anchor=null;
    // On first valid frame seed from the shoulder: do not retain a stale world-space hand.
    const start=L.lastHand||P.clone().addScaledVector(rt,.22).addScaledVector(UP,-.25);
    L.surface=HAND_SURFACE.solve(start,p,.075,hit);p.copy(L.surface.position);
    // Static friction on walkable ground: the old solver erased ALL tangential force by
    // sliding the hand to the desired X/Z. Keep the actual contact point during a stroke.
    // Walls still slide with an open hand; the trigger is the explicit wall-grab option.
    if(L.surface.contact&&L.surface.normal.y>=.55){
      if(L.groundPlant){
        const separation=L.desiredW[1].clone().sub(L.groundPlant).dot(L.groundNormal);
        const planeChange=Math.abs(p.clone().sub(L.groundPlant).dot(L.groundNormal));
        if(separation>.045||planeChange>.06||L.desiredW[1].distanceTo(L.groundPlant)>ARM*.7)L.groundPlant=null;
      }
      if(!L.groundPlant){L.groundPlant=p.clone();L.groundNormal.copy(L.surface.normal)}
      p.copy(L.groundPlant);
    }else L.groundPlant=null;
    L.lastHand=p.clone();
    if(L.surface.contact)lastCls=L.surface.cls;return L.surface.contact;
  };
  HOOK.climb=(dt,fw,rt)=>{
    if(!active()||INPUT.context==='menu'){if(L.anchor||L.pushing)L.reset();return false}
    if(!INPUT.ctrl.trigger){L.needsRelease=false;L.anchor=null}
    if(!L.ready||L.grace>0)return false;
    if(!L.surface||!L.surface.contact){L.release(true);return false}
    if(INPUT.ctrl.trigger&&!L.needsRelease&&!L.anchor)L.anchor=tgt[1].clone();
    _v.copy(tgt[1]).sub(L.desiredW[1]);
    if(_v.length()>ARM*.8){L.reset();return false}
    const limit=TUNE.max*Math.max(.001,Math.min(.05,dt));if(_v.length()>limit)_v.setLength(limit);
    const before=P.clone(),steps=Math.max(1,Math.ceil(_v.length()/.08));_v.divideScalar(steps);
    for(let i=0;i<steps;i++){const y=P.y;P.add(_v);wallFix();groundSweep(y)}
    _v.copy(P).sub(before).divideScalar(Math.max(dt,.001));
    if(!L.pushing)L.climbVel.copy(_v);else L.climbVel.lerp(_v,1-Math.exp(-dt*22));
    L.climbVel.clampLength(0,TUNE.max);vel.copy(L.climbVel);L.pushing=true;return true;
  };
  HOOK.poseAfterMove=(fw,rt)=>{
    if(state!=='play'||!VRP.threeDoF())return;
    if(!INPUT.ctrl.present){tgt[1].copy(P).addScaledVector(rt,.24).addScaledVector(UP,-.32).addScaledVector(fw,.6);return}
    if(INPUT.context==='menu'||!L.surface||!L.surface.contact){tgt[1].copy(P).addScaledVector(rt,L.local.x).addScaledVector(UP,L.local.y).addScaledVector(fw,L.local.z);L.lastHand=tgt[1].clone()}
  };
  HOOK.wristPose=()=>{
    if(!INPUT.ctrl.present||state!=='play')return false;
    handPose(LM,'R',new THREE.Vector3(0,0,-1).applyQuaternion(INPUT.ctrl.quat),new THREE.Vector3(1,0,0).applyQuaternion(INPUT.ctrl.quat),INPUT.ctrl.trigger?[.65,.9,.9]:[.15,.12,.12]);return true;
  };
  HOOK.move=(fw,rt,grounded)=>{const r={x:0,z:0,jump:false};if(INPUT.context==='menu')return r;
    if(INPUT.has('jump'))r.jump=true;
    const tl=INPUT.has('turnL'),tr=INPUT.has('turnR');if(tl||tr){L.reset();const a=(SETTINGS.snap===2?45:30)*Math.PI/180*(tl?1:-1);if(VRP.vrActive())VRP.turn(a);else yw+=a}
    const pm=INPUT.padMove();if(pm&&SETTINGS.assist>0){const k=pm.r*SETTINGS.assist*(grounded?1:.6)*L.speedMul();r.z=-pm.y/Math.hypot(pm.x,pm.y)*k;r.x=pm.x/Math.hypot(pm.x,pm.y)*k}return r};
  HOOK.padFilter=g=>g&&INPUT.isVRPad(g)?null:g;
  HOOK.inputLocked=()=>INPUT.context==='menu';
  HOOK.respawned=()=>L.reset();
  return L})();
