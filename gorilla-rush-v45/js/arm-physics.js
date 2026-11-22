// Per-hand contact and reaction. Measured controller position when available;
// otherwise an explicitly estimated 3DoF arm. Body integration belongs to PICO.physics.
(function(root){'use strict';
 function create(THREE,solve){
  const v=()=>new THREE.Vector3(),up=v().set(0,1,0),direction=v(),filtered=v(),previous=v(),localOffset=v(),heading=v();
  const GAIN=2,OFFSET=.55,REACH=1.25,DROP=.58,RETURN=.38;let lastDown=null,downVel=0,lift=0,peak=0,trough=0,blocked=false;
  // Real hands shake (~1-2 deg at 8-12 Hz) and the G2 pose jitters, so contact is
  // decided by ANGLE hysteresis, never by instantaneous speed: the hand lets go only
  // after tilting back up RELEASE (arm rad, ~6 deg of controller) from the deepest
  // point of the stroke, and can grab again only after tilting down REARM from the
  // highest point since letting go (or after leaving the surface).
  const RELEASE=.16,REARM=.12,AWAY=.15;
  // Auto-neutral: how each person (and each browser/controller) reports a relaxed
  // hold differs by tens of degrees. The relaxed, still, free-air pitch is learned
  // slowly and treated as "level", so the floor is always ~13 deg of tilt away.
  let neutral=0,elev=0,learned=0;const NEUTRAL_MIN=-.6,NEUTRAL_MAX=.5,NEUTRAL_TAU=2.5;
  function downOf(dir){
   const flat=Math.hypot(dir.x,dir.z);
   if(flat>.2)heading.set(dir.x/flat,0,dir.z/flat);
   else if(heading.lengthSq()<.5)heading.set(0,0,-1);
   elev=Math.atan2(-dir.y,Math.max(flat,1e-6));
   return Math.max(-1,Math.min(2.3,OFFSET+GAIN*(elev-neutral)));
  }
  function reachOffset(down,scale,out){
   return out.copy(heading).multiplyScalar(REACH*Math.cos(down)*scale).addScaledVector(up,(RETURN*lift-DROP*Math.sin(down))*scale);
  }
  const A={target:v(),desired:v(),handVelocity:v(),reaction:v(),velocity:v(),plant:null,contact:false,normal:v(),ready:false,grace:0,last:null,held:false,travel:0,radius:0,cls:0,driving:false};
  A.reset=()=>{lastDown=null;downVel=0;lift=0;peak=0;trough=0;blocked=false;A.blocked=false;A.plant=null;A.last=null;A.lastBody=null;A.previousLocal=null;A.lifting=false;A.contact=false;A.held=false;A.ready=false;A.grace=.08;A.driving=false;A.handVelocity.set(0,0,0);A.reaction.set(0,0,0);A.velocity.set(0,0,0)};
  A.afterMove=function(shift){
   A.desired.add(shift);
   if(!A.contact){const r=solve(A.target,A.target.clone().add(shift),A.radius);A.target.copy(r.position);
    if(r.contact&&A.active&&!A.lifting&&!A.blocked&&A.grace===0){A.contact=true;A.plant=A.target.clone();A.normal.copy(r.normal);A.cls=r.cls}}
   A.last=A.target.clone();if(A.lastBody)A.lastBody.add(shift);
  };
  A.update=function(o){
   const {position,eye,quat,forward,right,scale}=o,dt=Math.max(.001,Math.min(.05,o.dt)),side=o.side==='left'?-1:1;
   A.radius=Math.max(.36,o.handRadius||0)*scale;A.active=o.present&&!o.paused;A.driving=false;A.reaction.set(0,0,0);
   direction.set(0,0,-1).applyQuaternion(o.armQuat||quat).normalize();
   if(A.ready&&(!o.handPosition&&direction.dot(previous)<Math.cos(1.2)||A.lastBody&&A.lastBody.distanceTo(position)>2*scale))A.reset();
   previous.copy(direction);
   if(!A.ready){filtered.copy(direction);A.ready=true}else filtered.lerp(direction,1-Math.exp(-dt*12)).normalize();
   A.grace=Math.max(0,A.grace-dt);
   const shoulder=eye.clone().addScaledVector(right,side*.22*scale).addScaledVector(up,-.25*scale);let down=0;
   if(o.handPosition)A.desired.copy(o.handPosition);
   else{
    // Preserve the calibrated, amplified 3DoF trajectory and anti-tremor release
    // from the newer main branch. Only true positional input bypasses estimation.
    down=downOf(filtered);downVel=lastDown==null?0:downVel+((down-lastDown)/dt-downVel)*(1-Math.exp(-dt*14));lastDown=down;
    if(A.active&&Math.abs(downVel)<.3&&(learned<3||!A.contact)){learned+=dt;neutral=Math.max(NEUTRAL_MIN,Math.min(NEUTRAL_MAX,neutral+(elev-neutral)*(1-Math.exp(-dt/(learned<3?.7:NEUTRAL_TAU)))))}
    A.elev=elev;A.neutral=neutral;
    if(!A.plant&&downVel<-.6)lift=Math.min(1,lift+dt*8);else lift=Math.max(0,lift-dt*(downVel>.3?10:1.5));
    if(A.plant)peak=Math.max(peak,down);
    A.desired.copy(shoulder).add(reachOffset(A.plant?peak:down,scale,localOffset)).addScaledVector(forward,.1*scale);
   }
   if(!A.active){const start=A.last||shoulder;A.reset();A.desired.copy(eye).addScaledVector(right,side*.22*scale).addScaledVector(up,-.32*scale).addScaledVector(forward,.6*scale);A.target.copy(solve(start,A.desired,A.radius).position);A.last=A.target.clone();return false}
   // Subtract body displacement: gravity/previous impulses cannot become a new hand stroke.
   const local=A.desired.clone().sub(position),delta=A.previousLocal?local.clone().sub(A.previousLocal):v();
   if(delta.length()>.65*scale){A.reset();delta.set(0,0,0)} // recenter/teleport, not a throw
   A.previousLocal=local;A.handVelocity.copy(delta).divideScalar(dt).clampLength(0,12*scale);
   A.lifting=(o.handPosition?A.handVelocity.dot(A.normal)>.04*scale:!!A.plant&&down<peak-RELEASE);
   const start=A.last||shoulder;
   if(A.plant){const away=A.desired.clone().sub(A.plant).dot(A.normal)>(o.handPosition?.04:AWAY)*scale,tooFar=A.desired.distanceTo(A.plant)>1.3*scale;
    if(!solve(A.plant,A.plant,A.radius).contact||tooFar||away||A.lifting){A.plant=null;A.held=false;if(!o.handPosition){blocked=true;trough=down}}}
   if(!o.handPosition&&blocked){trough=Math.min(trough,down);if(down>trough+REARM)blocked=false}
   if(!A.plant){const r=solve(start,A.desired,A.radius);if(!r.contact)blocked=false;A.contact=r.contact&&A.grace===0&&!A.lifting&&(o.handPosition||!blocked);A.target.copy(r.position);if(r.contact)A.normal.copy(r.normal);A.cls=r.cls;if(A.contact){A.plant=A.target.clone();peak=down}}
   else{A.contact=true;A.target.copy(A.plant)}
   // Trigger closes fingers / operates menus and ziplines, never glues a palm.
   A.held=!!(A.contact&&o.trigger&&!o.needsRelease);
   if(A.contact&&A.grace===0){
    A.reaction.copy(A.target).sub(A.desired).divideScalar(dt);
    // ZIP follower correction, not a gain on hand velocity. The body consumes
    // this displacement once; trigger never changes support/release.
    A.reaction.addScaledVector(A.normal,-Math.min(0,A.reaction.dot(A.normal)));
    A.reaction.clampLength(0,8*scale);A.driving=A.reaction.length()>.04*scale;
    if(!A.driving)A.reaction.set(0,0,0);
   }
   A.blocked=blocked;A.velocity.copy(A.reaction);
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
