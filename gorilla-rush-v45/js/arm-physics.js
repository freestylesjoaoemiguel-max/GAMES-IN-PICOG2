// Per-hand contact and reaction. Measured controller position when available;
// otherwise an explicitly estimated 3DoF arm. Body integration belongs to PICO.physics.
(function(root){'use strict';
 function create(THREE,solve){
  const v=()=>new THREE.Vector3(),up=v().set(0,1,0),direction=v(),filtered=v(),previous=v();
  const A={target:v(),desired:v(),handVelocity:v(),reaction:v(),velocity:v(),plant:null,contact:false,normal:v(),ready:false,grace:0,last:null,held:false,travel:0,radius:0,cls:0,driving:false};
  A.reset=()=>{A.plant=null;A.last=null;A.lastBody=null;A.previousLocal=null;A.lifting=false;A.contact=false;A.held=false;A.ready=false;A.grace=.08;A.driving=false;A.handVelocity.set(0,0,0);A.reaction.set(0,0,0);A.velocity.set(0,0,0)};
  A.afterMove=function(shift){
   A.desired.add(shift);
   if(!A.contact){const r=solve(A.target,A.target.clone().add(shift),A.radius);A.target.copy(r.position);
    if(r.contact&&A.active&&!A.lifting&&A.grace===0){A.contact=true;A.plant=A.target.clone();A.normal.copy(r.normal);A.cls=r.cls}}
   A.last=A.target.clone();if(A.lastBody)A.lastBody.add(shift);
  };
  A.update=function(o){
   const {position,eye,quat,forward,right,scale}=o,dt=Math.max(.001,Math.min(.05,o.dt)),side=o.side==='left'?-1:1;
   A.radius=Math.max(.36,o.handRadius||0)*scale;A.active=o.present&&!o.paused;A.driving=false;A.reaction.set(0,0,0);
   direction.set(0,0,-1).applyQuaternion(o.armQuat||quat).normalize();
   if(A.ready&&(!o.handPosition&&direction.dot(previous)<Math.cos(1.2)||A.lastBody&&A.lastBody.distanceTo(position)>2*scale))A.reset();
   previous.copy(direction);
   if(!A.ready){filtered.copy(direction);A.ready=true}else filtered.lerp(direction,1-Math.exp(-dt*40)).normalize();
   A.grace=Math.max(0,A.grace-dt);
   const reach=Math.min(1.32*.96,.55+.85*Math.max(0,-filtered.y))*scale;
   const shoulder=eye.clone().addScaledVector(right,side*.22*scale).addScaledVector(up,-.25*scale);
   if(o.handPosition)A.desired.copy(o.handPosition);else A.desired.copy(shoulder).addScaledVector(filtered,reach).addScaledVector(forward,.1*scale);
   if(!A.active){const start=A.last||shoulder;A.reset();A.desired.copy(eye).addScaledVector(right,side*.22*scale).addScaledVector(up,-.32*scale).addScaledVector(forward,.6*scale);A.target.copy(solve(start,A.desired,A.radius).position);A.last=A.target.clone();return false}
   // Subtract body displacement: gravity/previous impulses cannot become a new hand stroke.
   const local=A.desired.clone().sub(position),delta=A.previousLocal?local.clone().sub(A.previousLocal):v();
   if(delta.length()>.65*scale){A.reset();delta.set(0,0,0)} // recenter/teleport, not a throw
   A.previousLocal=local;A.handVelocity.copy(delta).divideScalar(dt).clampLength(0,12*scale);
   if(A.held&&!o.trigger){A.held=false;A.plant=null}
   A.lifting=!A.held&&A.handVelocity.dot(A.normal)>.04*scale;
   const start=A.last||shoulder;
   if(A.plant){const away=A.desired.clone().sub(A.plant).dot(A.normal)>.04*scale,tooFar=A.desired.distanceTo(A.plant)>1.1*scale;
    if(!solve(A.plant,A.plant,A.radius).contact||tooFar||(!A.held&&away)||A.lifting){A.plant=null;A.held=false}}
   if(!A.plant){const r=solve(start,A.desired,A.radius);A.contact=r.contact&&A.grace===0&&!A.lifting;A.target.copy(r.position);if(r.contact)A.normal.copy(r.normal);A.cls=r.cls;if(A.contact)A.plant=A.target.clone()}
   else{A.contact=true;A.target.copy(A.plant)}
   if(A.contact&&o.trigger&&!o.needsRelease)A.held=true;
   if(A.contact&&A.grace===0){
    A.reaction.copy(A.handVelocity).negate();
    // Open palms only push into a surface or along it; lifting cannot suck the body back.
    if(!A.held)A.reaction.addScaledVector(A.normal,-Math.min(0,A.reaction.dot(A.normal)));
    else A.reaction.copy(A.target).sub(A.desired).divideScalar(dt);
    A.reaction.clampLength(0,8*scale);A.driving=A.reaction.length()>.04*scale;
    if(!A.driving)A.reaction.set(0,0,0);
   }
   A.velocity.copy(A.reaction);
   // Optional single-hand harness; runtime combines both reactions before moving the body once.
   if(o.move&&A.driving){const old=position.clone();o.move(A.reaction.clone().multiplyScalar(dt),dt);A.velocity.copy(position).sub(old).divideScalar(dt);A.travel+=position.distanceTo(old)/scale}
   A.last=A.target.clone();A.lastBody=position.clone();return A.contact;
  };
  A.reset();return A;
 }
 // Average active reactions, never double speed when two hands push together.
 function reaction(THREE,arms){const out=new THREE.Vector3();let count=0;for(const a of arms)if(a.active&&a.driving){out.add(a.reaction);count++}if(count)out.divideScalar(count);return {velocity:out,count}}
 root.ARM_PHYSICS={create,reaction};if(typeof module!=='undefined'&&module.exports)module.exports={create,reaction};
})(typeof window!=='undefined'?window:globalThis);
