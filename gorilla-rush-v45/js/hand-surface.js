// Original lightweight swept-hand contact solver. Not an exact port of Gorilla Tag's locomotion.
// cast(origin, unitDirection, distance) returns {point, n, distance, cls} or null.
(function(root){'use strict';
 function solve(from,to,radius,cast){
  const pos=from.clone(),goal=to.clone(),normal=from.clone().set(0,0,0);let contact=false,cls=0;
  // Large resets are seeded by locomotion, never swept through the entire map.
  if(![...from.toArray(),...to.toArray()].every(Number.isFinite)){const safe=from.clone();if(!from.toArray().every(Number.isFinite))safe.set(0,0,0);return {position:safe,contact:false,normal,cls};}
  if(from.distanceTo(to)>2)return {position:goal,contact:false,normal,cls};
  for(let pass=0;pass<2;pass++){
   const delta=goal.clone().sub(pos),len=delta.length();if(len<1e-6)break;const dir=delta.clone().divideScalar(len);
   let distance=len,found=null;
   const offsets=[[0,0,0],[radius,0,0],[-radius,0,0],[0,radius,0],[0,-radius,0],[0,0,radius],[0,0,-radius]];
   for(let i=0;i<offsets.length;i++){
    const offset=pos.clone().fromArray(offsets[i]),h=cast(pos.clone().add(offset),dir,len+(i===0?radius:0)+.003);
    // An offset ray can start slightly inside a triangle while the hand centre is
    // outside. Never let its back-face hit pull a departing hand under the floor.
    if(!h||pos.clone().sub(h.point).dot(h.n)<-1e-5)continue;
    const padding=i===0?radius/Math.max(.05,-h.n.dot(dir)):0,t=Math.max(0,h.distance-padding-.002);
    if(t<distance){distance=t;found=h;}
   }
   if(!found){pos.copy(goal);break}
   contact=true;cls=found.cls||0;normal.copy(found.n);pos.addScaledVector(dir,distance);
   // Slide tangentially, rather than gluing an open hand to the wall.
   const remain=goal.clone().sub(pos);remain.addScaledVector(normal,-Math.min(0,remain.dot(normal)));goal.copy(pos).add(remain);
  }
  // Real radius-only contact; no old half-metre magnet to the floor.
  for(const axis of [[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]]){
   const d=pos.clone().fromArray(axis),h=cast(pos,d,radius+.004);
   if(h&&h.distance<=radius+.003){contact=true;cls=h.cls||0;normal.copy(h.n);pos.addScaledVector(h.n,Math.max(0,radius-h.distance));}
  }
  return {position:pos,contact,normal,cls};
 }
 const api={solve};root.HAND_SURFACE=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
