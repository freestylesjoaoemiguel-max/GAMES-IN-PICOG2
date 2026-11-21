// Hide the LEFT arm in skinned geometry, not just bone.visible (which does not hide skin).
// The supplied GLB stays untouched. Cloned geometry is shared by clones of this right-only avatar.
(function(root){'use strict';
 function strip(model){let removed=0;
  model.traverse(function(mesh){
   if(!mesh.isSkinnedMesh||mesh.userData.rightArmOnly)return;
   const original=mesh.geometry,joints=original.getAttribute('skinIndex'),weights=original.getAttribute('skinWeight');
   if(!joints||!weights)return;
   const left=mesh.skeleton.bones.map(function(b){return /_L(?:_|$)/.test(b.name)&&!/^shoulder_/i.test(b.name)});
   const cut=new Uint8Array(joints.count),get=['getX','getY','getZ','getW'];
   for(let v=0;v<cut.length;v++){let sum=0;for(let j=0;j<4;j++)if(left[joints[get[j]](v)])sum+=weights[get[j]](v);cut[v]=sum>.05?1:0;}
   const index=original.getIndex(),count=index?index.count:joints.count,at=i=>index?index.getX(i):i;
   const groups=original.groups.length?original.groups:[{start:0,count:count,materialIndex:0}],kept=[],g=original.clone();g.clearGroups();
   for(const group of groups){const start=kept.length,end=Math.min(count,group.start+group.count);
    for(let i=group.start;i+2<end;i+=3){const a=at(i),b=at(i+1),c=at(i+2);if(cut[a]||cut[b]||cut[c])removed++;else kept.push(a,b,c)}
    g.addGroup(start,kept.length-start,group.materialIndex);
   }
   g.setIndex(kept);mesh.geometry=g;mesh.userData.rightArmOnly=true;
  });
  return removed;
 }
 const api={strip:strip};root.RIGHT_ARM=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
