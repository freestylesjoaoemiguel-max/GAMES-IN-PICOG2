// Reversible local hand visibility. Keep the supplied GLB and the original geometry.
// Source-specific left/right presence never changes remote avatars or source assets.
(function(root){'use strict';
 const variants=new WeakMap();
 function visible(model,left,right){let removed=0;const key=(left?'L':'')+(right?'R':'');
  model.traverse(mesh=>{if(!mesh.isSkinnedMesh)return;let cache=variants.get(mesh);if(!cache){cache={original:mesh.geometry,LR:mesh.geometry};variants.set(mesh,cache)}
   if(!cache[key]){const original=cache.original,joints=original.attributes.skinIndex,weights=original.attributes.skinWeight;if(!joints||!weights)return;
    const hidden=mesh.skeleton.bones.map(b=>!/^shoulder_/i.test(b.name)&&(!left&&/_L(?:_|$)/.test(b.name)||!right&&/_R(?:_|$)/.test(b.name))),cut=new Uint8Array(joints.count),get=['getX','getY','getZ','getW'];
    for(let v=0;v<cut.length;v++){let sum=0;for(const f of get)if(hidden[joints[f](v)])sum+=weights[f](v);cut[v]=sum>.05?1:0}
    const index=original.getIndex(),count=index?index.count:joints.count,at=i=>index?index.getX(i):i,groups=original.groups.length?original.groups:[{start:0,count,materialIndex:0}],kept=[],g=original.clone();g.clearGroups();
    for(const group of groups){const start=kept.length,end=Math.min(count,group.start+group.count);for(let i=group.start;i+2<end;i+=3){const a=at(i),b=at(i+1),c=at(i+2);if(cut[a]||cut[b]||cut[c])removed++;else kept.push(a,b,c)}g.addGroup(start,kept.length-start,group.materialIndex)}g.setIndex(kept);cache[key]=g;
   }
   mesh.geometry=cache[key];mesh.userData.rightArmOnly=!left&&right;mesh.userData.handMask=key;
  });return removed;
 }
 function strip(model){return visible(model,false,true)}
 // Collision radius is derived from hand/finger skin, not the wrist bone alone.
 function points(model,wrist){
  const bones=new Set(),out=[];wrist.traverse(b=>bones.add(b));model.updateMatrixWorld(true);
  model.traverse(mesh=>{if(!mesh.isSkinnedMesh)return;mesh.skeleton.update();const g=mesh.geometry,j=g.attributes.skinIndex,w=g.attributes.skinWeight,p=g.attributes.position;
   if(!j||!w)return;for(let i=0;i<p.count;i++){let weight=0;for(const get of ['getX','getY','getZ','getW'])if(bones.has(mesh.skeleton.bones[j[get](i)]))weight+=w[get](i);if(weight>.05){const v=wrist.position.clone().fromBufferAttribute(p,i);mesh.boneTransform(i,v);out.push(v.applyMatrix4(mesh.matrixWorld))}}
  });return out;
 }
 function radius(model,wrist){const center=wrist.getWorldPosition(wrist.position.clone());return points(model,wrist).reduce((r,p)=>Math.max(r,p.distanceTo(center)),0)}
 const api={strip:strip,visible:visible,points:points,radius:radius};root.RIGHT_ARM=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
