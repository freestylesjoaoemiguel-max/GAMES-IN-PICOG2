// Continuous sphere/triangle collision: faces, edges and vertices, not offset rays.
// query(from, to, radius, visit) visits nearby world triangles (a,b,c,materialClass).
(function(root){'use strict';
 function create(THREE,query){
  const V=()=>new THREE.Vector3(),tri=new THREE.Triangle(),near=V(),n=V(),edge=V(),rel=V(),vel=V();
  function sweep(from,to,radius){
   const delta=to.clone().sub(from),speed2=delta.lengthSq(),skin=Math.max(1e-6,radius*.002);
   let best=null,time=1;
   function accept(t,point,normal,cls,depth=0){
    if(t<0||t>time||normal.lengthSq()<.5)return;
    if(depth<=skin&&delta.dot(normal)>=-1e-10)return; // Lift away, including initial overlap.
    time=t;best={time:t,point:point.clone(),normal:normal.clone(),cls,depth};
   }
   function sphere(center,r,cls,segment){
    rel.copy(from).sub(center);vel.copy(delta);
    if(segment){const along=rel.dot(edge),travel=vel.dot(edge);rel.addScaledVector(edge,-along);vel.addScaledVector(edge,-travel)}
    const a=vel.lengthSq(),b=rel.dot(vel),c=rel.lengthSq()-r*r,disc=b*b-a*c;
    if(a<1e-14||disc<0)return;
    const t=(-b-Math.sqrt(disc))/a;if(t<0||t>time)return;
    const p=from.clone().addScaledVector(delta,t);let closest=center;
    if(segment){const u=p.clone().sub(center).dot(edge);if(u<0||u>segment)return;closest=center.clone().addScaledVector(edge,u)}
    accept(t,closest,p.sub(closest).normalize(),cls);
   }
   query(from,to,radius+skin,(a,b,c,cls)=>{
    tri.set(a,b,c);tri.getNormal(n);if(n.lengthSq()<.5)return;
    tri.closestPointToPoint(from,near);const dist=from.distanceTo(near);
    if(dist<radius+skin){const normal=from.clone().sub(near);if(dist>1e-9)normal.divideScalar(dist);else{normal.copy(n);if(normal.dot(delta)>0)normal.negate()}
     // A shallow overlap on departure must not glue the hand to the underside.
     if(delta.dot(normal)<0||dist<radius*.9)accept(0,near,normal,cls,Math.max(0,radius-dist));
    }
    if(speed2<1e-16)return;
    const signed=from.clone().sub(a).dot(n),rate=delta.dot(n);
    if(Math.abs(rate)>1e-12)for(const side of [-1,1]){
     const t=(side*radius-signed)/rate;if(t<0||t>time)continue;
     const normal=n.clone().multiplyScalar(side),point=from.clone().addScaledVector(delta,t).addScaledVector(normal,-radius);
     if(tri.containsPoint(point))accept(t,point,normal,cls);
    }
    for(const pair of [[a,b],[b,c],[c,a]]){edge.copy(pair[1]).sub(pair[0]);const len=edge.length();if(len>1e-10){edge.divideScalar(len);sphere(pair[0],radius,cls,len)}sphere(pair[0],radius,cls,0)}
   });
   return best;
  }
  function solve(from,to,radius){
   const pos=from.clone(),goal=to.clone(),normal=V(),skin=Math.max(1e-6,radius*.002);let contact=false,cls=0,point=null;
   if(![...from.toArray(),...to.toArray(),radius].every(Number.isFinite)||radius<=0)return {position:pos,contact:false,normal,cls,point};
   for(let pass=0;pass<5;pass++){
    const h=sweep(pos,goal,radius);if(!h){pos.copy(goal);break}
    contact=true;normal.copy(h.normal);cls=h.cls||0;point=h.point;
    const d=goal.clone().sub(pos);pos.addScaledVector(d,h.time).addScaledVector(normal,h.depth+skin);
    const remain=goal.clone().sub(pos);remain.addScaledVector(normal,-Math.min(0,remain.dot(normal)));goal.copy(pos).add(remain);
   }
   // Persistent contact tolerance, independent of frame displacement and scale.
   if(!contact)query(pos,pos,radius+skin*3,(a,b,c,material)=>{
    tri.set(a,b,c);tri.closestPointToPoint(pos,near);const distance=pos.distanceTo(near);
    if(distance<=radius+skin*3&&distance>1e-9){contact=true;normal.copy(pos).sub(near).divideScalar(distance);point=near.clone();cls=material||0}
   });
   return {position:pos,contact,normal,cls,point};
  }
  return {solve,sweep};
 }
 const api={create};root.HAND_SURFACE=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
