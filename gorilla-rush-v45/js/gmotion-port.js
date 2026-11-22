// Port of the contact/throw core in the user-supplied Gorilla Movement ZIP.
// Source: reference/gorilla-movement/gmotion.js (Lights Out: Bear Facility).
// Collision/body integration are injected by the existing v45 runtime, not Rapier.
// No claim of official Gorilla Tag code or of positional tracking on a 3DoF G2.
(function(root){'use strict';
 const HISTORY=8,THRESHOLD=.4,JUMP=1.1,MAX_JUMP=4,GRAVITY=5;
 function create(THREE){
  const history=Array.from({length:HISTORY},()=>new THREE.Vector3());
  const m={average:new THREE.Vector3(),moving:false,index:0,contacts:0,released:false,throwSpeed:0};
  m.reset=()=>{for(const v of history)v.set(0,0,0);m.average.set(0,0,0);m.index=0;m.moving=false;m.contacts=0;m.released=false;m.throwSpeed=0};
  // Like GMotion.update sections 9/10: average ALL contacting hands, including a
  // stationary one; never double force. Only supplied, valid input can contribute.
  m.apply=(arms,velocity,dt,scale)=>{
   const movement=new THREE.Vector3();let count=0;
   for(const a of arms)if(a.active&&a.contact&&a.grace===0){movement.add(a.target.clone().sub(a.desired));count++}
   m.contacts=count;m.released=false;m.throwSpeed=0;
   if(count){
    movement.divideScalar(count*Math.max(.001,dt));
    if(movement.lengthSq()<.02*scale*scale)movement.set(0,0,0);
    // Safety addition: source has no contact-speed cap; a bad pose must not fling.
    velocity.copy(movement).clampLength(0,8*scale);m.moving=true;
   }else if(m.moving){
    const speed=m.average.length();
    if(speed>THRESHOLD*scale)velocity.copy(m.average).multiplyScalar(JUMP).clampLength(0,MAX_JUMP*scale);
    else velocity.set(0,0,0);
    m.released=true;m.throwSpeed=velocity.length()/scale;m.moving=false;
   }
   return count;
  };
  // Source section 11: use actual collision-limited BODY displacement, not desired
  // controller motion. Blocked movement cannot charge a launch against a wall.
  m.record=(displacement,dt)=>{
   m.index=(m.index+1)%HISTORY;
   const sample=displacement.clone().divideScalar(Math.max(.001,dt));
   m.average.add(sample.clone().sub(history[m.index]).divideScalar(HISTORY));
   history[m.index].copy(sample);
  };
  m.reset();return m;
 }
 const api={create,HISTORY,THRESHOLD,JUMP,MAX_JUMP,GRAVITY};
 root.GMOTION_PORT=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
