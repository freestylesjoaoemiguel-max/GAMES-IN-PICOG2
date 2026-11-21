// Original 3DoF arm/contact locomotion. Orientation measured, position estimated.
// One right arm; no claim to reproduce the exact Gorilla Tag physics or 6DoF.
(function(root){'use strict';
 function create(THREE,solve){
  const v=()=>new THREE.Vector3(),up=v().set(0,1,0),direction=v(),filtered=v(),previous=v();
  const A={target:v(),desired:v(),velocity:v(),plant:null,contact:false,normal:v(),ready:false,grace:0,last:null,held:false,travel:0};
  A.reset=()=>{A.plant=null;A.last=null;A.contact=false;A.held=false;A.ready=false;A.grace=.12;A.velocity.set(0,0,0)};
  A.update=function(o){
   const {position,eye,quat,forward,right,scale,cast,move}=o,dt=Math.max(.001,Math.min(.05,o.dt)),radius=.075*scale;
   const max=6*scale;
   direction.set(0,0,-1).applyQuaternion(o.armQuat||quat).normalize();
   // Filter only the pointing vector: roll cannot delay position or release support.
   if(A.ready&&(direction.dot(previous)<Math.cos(1.2)||A.lastBody&&A.lastBody.distanceTo(position)>2*scale))A.reset();
   previous.copy(direction);
   if(!A.ready){filtered.copy(direction);A.ready=true}else filtered.lerp(direction,1-Math.exp(-dt*25)).normalize();
   A.grace=Math.max(0,A.grace-dt);
   const down=Math.max(0,-filtered.y),reach=Math.min(1.32*.96,.55+.85*down)*scale;
   A.desired.copy(eye).addScaledVector(right,.22*scale).addScaledVector(up,-.25*scale).addScaledVector(filtered,reach).addScaledVector(forward,.1*scale);
   if(!o.present||o.paused){A.reset();A.target.copy(eye).addScaledVector(right,.22*scale).addScaledVector(up,-.32*scale).addScaledVector(forward,.6*scale);return false}
   const start=A.last||eye.clone().addScaledVector(right,.22*scale).addScaledVector(up,-.25*scale);
   const result=solve(start,A.desired,radius,cast);
   A.normal.copy(result.normal);const before=A.contact;A.contact=result.contact&&A.grace===0;
   if(A.held&&!o.trigger){A.held=false;A.plant=null}
   if(A.held&&o.trigger){A.contact=true;A.target.copy(A.plant)}else{
    if(A.contact&&result.normal.y>.55){
     if(A.plant&&(A.desired.clone().sub(A.plant).dot(A.normal)>.04*scale||result.position.distanceTo(A.plant)>1.1*scale))A.plant=null;
     if(!A.plant)A.plant=result.position.clone();A.target.copy(A.plant);
    }else{A.plant=null;A.target.copy(result.position)}
    if(A.contact&&o.trigger&&!o.needsRelease){A.held=true;A.plant=A.target.clone()}
   }
   if(A.contact){
    const correction=A.target.clone().sub(A.desired);
    if(correction.length()>1.1*scale){A.reset();A.target.copy(A.desired);return false}
    correction.clampLength(0,max*dt);const old=position.clone();move(correction,dt);
    const displacement=position.clone().sub(old);A.travel+=Math.hypot(displacement.x,displacement.z)/scale;
    const measured=displacement.divideScalar(dt);if(!before)A.velocity.copy(measured);else A.velocity.lerp(measured,1-Math.exp(-dt*22));A.velocity.clampLength(0,max);
   }
   A.last=A.target.clone();A.lastBody=position.clone();return A.contact;
  };
  A.reset();return A;
 }
 root.ARM_PHYSICS={create};if(typeof module!=='undefined'&&module.exports)module.exports={create};
})(typeof window!=='undefined'?window:globalThis);
