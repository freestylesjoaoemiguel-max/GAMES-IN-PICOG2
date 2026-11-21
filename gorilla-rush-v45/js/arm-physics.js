// Single estimated 3DoF arm. World-space anchor and continuous collision of the
// visible hand envelope; this does not turn orientation into measured 6DoF.
(function(root){'use strict';
 function create(THREE,solve){
  const v=()=>new THREE.Vector3(),up=v().set(0,1,0),direction=v(),filtered=v(),previous=v(),local=v(),heading=v();
  // Gorilla-style 3DoF arm: one long straight arm pivoting at the shoulder. The
  // wrist tilt is amplified (GAIN) so the small tilt a G2 controller makes during a
  // natural down-and-back stroke sweeps the whole arm: level = hand just above the
  // floor, ~13 deg down = palm on the floor, ~30 deg down = hand under the shoulder,
  // ~50 deg = hand behind. The path is flat near the floor so a stroke pushes you
  // forward, not up. Yaw comes from the controller, so side swings work too.
  // Tilting back up swings the hand forward through the air (RETURN lift), so the
  // recovery does not drag on the floor and push you backwards.
  const GAIN=2,OFFSET=.55,REACH=1.25,DROP=.58,RETURN=.38;let lastDown=null,downVel=0,lift=0,lastRaw=null,rawVel=0;
  function downOf(dir,keepHeading){
   const flat=Math.hypot(dir.x,dir.z);
   if(keepHeading);else if(flat>.2)heading.set(dir.x/flat,0,dir.z/flat);
   else if(heading.lengthSq()<.5)heading.set(0,0,-1);
   return Math.max(-1,Math.min(2.3,OFFSET+GAIN*Math.atan2(-dir.y,Math.max(flat,1e-6))));
  }
  function reachOffset(down,scale,out){
   return out.copy(heading).multiplyScalar(REACH*Math.cos(down)*scale).addScaledVector(up,(RETURN*lift-DROP*Math.sin(down))*scale);
  }
  const A={target:v(),desired:v(),velocity:v(),plant:null,contact:false,normal:v(),ready:false,grace:0,last:null,held:false,travel:0,radius:0,cls:0};
  A.reset=()=>{lastDown=null;downVel=0;lift=0;lastRaw=null;rawVel=0;A.plant=null;A.last=null;A.lastBody=null;A.rawLocal=null;A.lifting=false;A.contact=false;A.held=false;A.ready=false;A.grace=.12;A.velocity.set(0,0,0)};
  // Gravity/body collisions occur after hand locomotion. Sweep that movement too;
  // never translate a rendered hand through the floor after solving contact.
  A.afterMove=function(shift){
   if(A.contact)return;
   const result=solve(A.target,A.target.clone().add(shift),A.radius);
   A.target.copy(result.position);A.last=A.target.clone();
   if(result.contact&&A.active&&!A.lifting&&A.grace===0){A.contact=true;A.plant=A.target.clone();A.normal.copy(result.normal);A.cls=result.cls;A.velocity.set(0,0,0)}
  };
  A.update=function(o){
   const {position,eye,quat,forward,right,scale,move}=o,dt=Math.max(.001,Math.min(.05,o.dt)),max=6*scale;
   // 0.36 encloses the supplied open hand (measured vertices: ~0.335 from wrist).
   // Runtime may enlarge this using its skinned mesh; never use the old 0.075 wrist-only ball.
   A.radius=Math.max(.36,o.handRadius||0)*scale;A.active=o.present&&!o.paused;
   direction.set(0,0,-1).applyQuaternion(o.armQuat||quat).normalize();
   if(A.ready&&(direction.dot(previous)<Math.cos(1.2)||A.lastBody&&A.lastBody.distanceTo(position)>2*scale))A.reset();
   previous.copy(direction);
   if(!A.ready){filtered.copy(direction);A.ready=true}else filtered.lerp(direction,1-Math.exp(-dt*25)).normalize();
   A.grace=Math.max(0,A.grace-dt);
   const shoulder=eye.clone().addScaledVector(right,.22*scale).addScaledVector(up,-.25*scale);
   const down=downOf(filtered);
   downVel=lastDown==null?0:downVel+((down-lastDown)/dt-downVel)*(1-Math.exp(-dt*14));lastDown=down;
   // Unfiltered tilt rate: reacts within a frame or two when the stroke reverses.
   const raw=downOf(direction,true);rawVel=lastRaw==null?0:rawVel+((raw-lastRaw)/dt-rawVel)*(1-Math.exp(-dt*30));lastRaw=raw;
   if(downVel<-.8)lift=Math.min(1,lift+dt*8);else lift=Math.max(0,lift-dt*(downVel>.25?12:1));
   A.desired.copy(shoulder).add(reachOffset(down,scale,local)).addScaledVector(forward,.1*scale);
   if(!A.active){const start=A.last||shoulder;A.reset();A.desired.copy(eye).addScaledVector(right,.22*scale).addScaledVector(up,-.32*scale).addScaledVector(forward,.6*scale);A.target.copy(solve(start,A.desired,A.radius).position);A.last=A.target.clone();return false}
   const before=A.contact,start=A.last||shoulder;
   // Tilting the controller back up is the 3DoF version of lifting your hand off
   // the ground: release at once so the return swing never drags you backwards.
   // Sweeping the arm further back keeps pushing. Jitter is filtered by downVel.
   A.lifting=!A.held&&(downVel<-.5||rawVel<-1.5);
   if(A.held&&!o.trigger){A.held=false;A.plant=null}
   if(A.plant){
    const away=A.desired.clone().sub(A.plant).dot(A.normal)>.06*scale;
    const tooFar=A.desired.distanceTo(A.plant)>1.1*scale;
    // A removed/changed map cannot leave a floating anchor.
    const supported=solve(A.plant,A.plant,A.radius).contact;
    if(!supported||tooFar||(!A.held&&away)||A.lifting){A.plant=null;A.held=false}
   }
   if(!A.plant){const result=solve(start,A.desired,A.radius);A.contact=result.contact&&A.grace===0&&!A.lifting;A.target.copy(result.position);if(result.contact)A.normal.copy(result.normal);A.cls=result.cls;if(A.contact)A.plant=A.target.clone()}
   else{A.contact=true;A.target.copy(A.plant)}
   if(A.contact&&o.trigger&&!o.needsRelease)A.held=true;
   if(A.contact){
    const correction=A.target.clone().sub(A.desired),old=position.clone();
    // An open palm pushes; it must not pull the body back toward the floor when
    // the user lifts. Otherwise each frame cancels the release distance.
    if(!A.held)correction.addScaledVector(A.normal,-Math.min(0,correction.dot(A.normal)));
    correction.clampLength(0,max*dt);move(correction,dt);
    const displacement=position.clone().sub(old);A.travel+=Math.hypot(displacement.x,displacement.z)/scale;
    const measured=displacement.divideScalar(dt);if(!before)A.velocity.copy(measured);else A.velocity.lerp(measured,1-Math.exp(-dt*22));A.velocity.clampLength(0,max);
   }
   A.last=A.target.clone();A.lastBody=position.clone();return A.contact;
  };
  A.reset();return A;
 }
 root.ARM_PHYSICS={create};if(typeof module!=='undefined'&&module.exports)module.exports={create};
})(typeof window!=='undefined'?window:globalThis);
