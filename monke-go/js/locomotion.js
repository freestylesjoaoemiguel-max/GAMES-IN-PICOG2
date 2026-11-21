// One controller, ONE RIGHT ARM. No swapping, mirrored hand or delayed second arm.
// G2 provides orientation (3DoF); hand position remains an estimated arm model.
'use strict';
const LOCO=(function(){
  const L={anchor:null,climbVel:new THREE.Vector3(),desiredW:[new THREE.Vector3(),new THREE.Vector3()],ready:false,grace:.08,needsRelease:true};
  const _d=new THREE.Vector3(),_n=new THREE.Vector3(),_ax=new THREE.Vector3(),_q=new THREE.Quaternion(),_hq=new THREE.Quaternion(),_v=new THREE.Vector3(),_w=new THREE.Vector3(),UP=new THREE.Vector3(0,1,0);
  const fq=new THREE.Quaternion(),lastRaw=new THREE.Quaternion(),lastFw=new THREE.Vector3();let fqInit=false,L1b=0,L2b=0;
  L.ctrlHand=()=>1;
  HOOK.handEnabled=i=>i===1;
  L.speedMul=()=>({fast:1,medium:.7,slow:.45})[SETTINGS.speedMode]||1;
  L.applyTune=()=>{const k=L.speedMul();TUNE.imp=SETTINGS.impulse*k;TUNE.jmp=(1+(SETTINGS.impulse-1)*.32)*Math.sqrt(k);TUNE.grav=SETTINGS.gravity;TUNE.fric=SETTINGS.friction;TUNE.max=SETTINGS.maxSpeed*k;TUNE.spd=k;SHOW_NAMES=!!SETTINGS.names;window.MUSIC&&MUSIC.volume()};
  L.applyArms=()=>{if(!LM||!LM.uR)return;if(!L1b){L1b=L1;L2b=L2}const k=SETTINGS.arm;
    LM.uR.scale.setScalar(k);for(const id in RO){const M=RO[id];if(M.uR)M.uR.scale.setScalar(k)}
    L1=L1b*k;L2=L2b*k;ARM=(L1+L2)*.98;L.reset()};
  const active=()=>INPUT.ctrl.present&&VRP.threeDoF()&&state==='play'&&INPUT.context!=='menu';
  HOOK.bodyFw=(fw,d)=>{VRP.headWorldQuat(_hq);_d.set(0,0,-1).applyQuaternion(_hq);
    if(Math.abs(_d.y)>.97)_d.set(0,1,0).applyQuaternion(_hq).multiplyScalar(-Math.sign(_d.y));
    fw.set(_d.x,0,_d.z);if(fw.lengthSq()<1e-6)fw.set(0,0,-1);fw.normalize();d.set(0,0,-1).applyQuaternion(_hq)};
  // Reset does NOT throw the player. A held trigger must be released before grabbing again.
  L.reset=()=>{const climbing=!!L.anchor;L.anchor=null;L.climbVel.set(0,0,0);L.ready=false;L.grace=.08;L.needsRelease=true;fqInit=false;
    for(let i=0;i<2;i++){touch[i]=false;hv[i].set(0,0,0);slot[i].p=null;slot[i].t=0;}
    if(climbing)vel.set(0,0,0);
  };
  L.release=(throwPlayer=false)=>{if(!L.anchor)return;L.anchor=null;
    if(!throwPlayer){L.climbVel.set(0,0,0);vel.set(0,0,0);return}
    // Only a real trigger release keeps the last measured climbing speed; no extra multiplier.
    vel.copy(L.climbVel);vel.clampLength(0,TUNE.max);vel.y=Math.min(vel.y,5.2*TUNE.jmp);
  };
  HOOK.noPush=i=>i===0||INPUT.context==='menu'||(VRP.threeDoF()&&(!active()||!L.ready||L.grace>0||!!L.anchor));
  HOOK.hand=(i,fw,rt,dt)=>{
    if(i===0)return null; // Never let the disabled arm reset the right-arm filter.
    if(!active()){if(L.ready||L.anchor)L.reset();return null}
    dt=Math.max(.001,Math.min(.05,dt));
    const q=INPUT.ctrl.quat;
    // Runtime recenter/pose discontinuity is not a physical swing. Cancel grab and reseed.
    if(fqInit&&(2*Math.acos(Math.min(1,Math.abs(lastRaw.dot(q))))>1.2||lastFw.dot(fw)<.7))L.reset();
    if(!fqInit){fq.copy(q);fqInit=true}else{
      const angle=2*Math.acos(Math.min(1,Math.abs(fq.dot(q))));
      const alpha=1-Math.exp(-dt*(45-25*SETTINGS.smooth));
      fq.slerp(q,angle>1e-6?Math.min(alpha,8*dt/angle):1).normalize();
    }
    lastRaw.copy(q);lastFw.copy(fw);
    if(!INPUT.ctrl.trigger)L.needsRelease=false;
    _d.set(0,0,-1).applyQuaternion(fq);
    _n.copy(fw).multiplyScalar(.766).addScaledVector(UP,-.643).normalize();
    const ang=Math.acos(Math.max(-1,Math.min(1,_n.dot(_d))));_ax.crossVectors(_n,_d);
    if(_ax.lengthSq()>1e-8&&ang>1e-4){_ax.normalize();_q.setFromAxisAngle(_ax,Math.min(ang*SETTINGS.sens,2.9));_d.copy(_n).applyQuaternion(_q)}
    const reach=ARM*SETTINGS.reach,o=new THREE.Vector3(.19+_d.dot(rt)*reach,-.3+_d.y*reach,-.08+_d.dot(fw)*reach);
    L.desiredW[1].copy(P).addScaledVector(rt,o.x).addScaledVector(UP,o.y).addScaledVector(fw,o.z);
    if(L.anchor){_v.copy(L.anchor).sub(P);o.set(_v.dot(rt),_v.y,_v.dot(fw))}
    if(!L.ready){hl[1].copy(o);hv[1].set(0,0,0);touch[1]=false;L.ready=true;}
    L.grace=Math.max(0,L.grace-dt);return o;
  };
  function nearSurface(p){if(!p)return false;const r=.12+.25*SETTINGS.assist;if(hit(_w.set(p.x,p.y+.2,p.z),DOWN,.2+r))return true;
    for(let k=0;k<4;k++){_v.set(k&2?1:0,0,k&2?0:1);if(k&1)_v.negate();if(hit(p,_v,r))return true}return false}
  HOOK.climb=(dt,fw,rt)=>{
    if(!active()){if(L.anchor)L.reset();return false}
    if(!INPUT.ctrl.trigger){L.needsRelease=false;L.release(true);return false}
    if(!L.ready||L.grace>0||L.needsRelease)return false;
    if(!L.anchor){if(touch[1]||nearSurface(tgt[1])){L.anchor=tgt[1].clone();L.climbVel.set(0,0,0);vel.set(0,0,0);tapSound(1,lastCls,.6);return true}else return false}
    _v.copy(L.anchor).sub(L.desiredW[1]);
    const shoulder=_w.copy(P).addScaledVector(rt,.19);shoulder.y-=.3;
    if(L.anchor.distanceTo(shoulder)>ARM*1.7){L.reset();return false}
    const lim=TUNE.max*Math.max(.001,Math.min(.05,dt));if(_v.length()>lim)_v.setLength(lim);
    // Apply the positional constraint once. core.js must NOT integrate climb velocity again.
    const before=P.clone(),steps=Math.max(1,Math.ceil(_v.length()/.1));_v.divideScalar(steps);
    for(let i=0;i<steps;i++){const y=P.y;P.add(_v);wallFix();groundSweep(y)}
    _v.copy(P).sub(before).divideScalar(Math.max(dt,.001));
    L.climbVel.lerp(_v,1-Math.exp(-dt*18));L.climbVel.clampLength(0,TUNE.max);vel.copy(L.climbVel);return true;
  };
  HOOK.move=(fw,rt,grounded)=>{const r={x:0,z:0,jump:false};if(INPUT.context==='menu')return r;
    if(INPUT.has('jump'))r.jump=true;
    const tl=INPUT.has('turnL'),tr=INPUT.has('turnR');if(tl||tr){L.reset();const a=(SETTINGS.snap===2?45:30)*Math.PI/180*(tl?1:-1);if(VRP.vrActive())VRP.turn(a);else yw+=a}
    const pm=INPUT.padMove();if(pm&&SETTINGS.assist>0){const k=pm.r*SETTINGS.assist*(grounded?1:.6)*L.speedMul();r.z=-pm.y/Math.hypot(pm.x,pm.y)*k;r.x=pm.x/Math.hypot(pm.x,pm.y)*k}return r};
  HOOK.padFilter=g=>g&&INPUT.isVRPad(g)?null:g;
  HOOK.inputLocked=()=>INPUT.context==='menu';
  HOOK.respawned=()=>L.reset();
  return L})();
