extends RefCounted
# Adapts the credited Grinner model to the existing avatar/hand IK interface.
# Source model and materials remain unmodified in grinner_from_gilla.glb.
static var arm_mesh_cache: ArrayMesh

static func clean_bone_name(name:String)->String:
 var where:=name.rfind("_")
 if where>0 and name.substr(where+1).is_valid_int():return name.substr(0,where)
 return name

static func instantiate() -> Node3D:
 var source:=load("res://grinner_from_gilla.glb").instantiate() as Node3D
 # The downloaded scene has an arbitrary 82.9-degree turn; restore forward.
 var root_model:=source.find_child("Grinner",true,false) as Node3D
 if root_model:root_model.rotation.y=0.0
 var sk:=source.find_child("Skeleton3D",true,false) as Skeleton3D
 var mesh:=sk.find_child("Object_10",true,false) as MeshInstance3D
 # Bone and skin aliases let the existing IK and head hiding use this rig.
 var new_skin:=mesh.skin.duplicate(true) as Skin
 for i in new_skin.get_bind_count():new_skin.set_bind_name(i,clean_bone_name(str(new_skin.get_bind_name(i))))
 for i in sk.get_bone_count():sk.set_bone_name(i,clean_bone_name(sk.get_bone_name(i)))
 mesh.skin=new_skin;mesh.name="Gilla"
 # Fold intermediate import transforms into the skeleton, preserving mesh/skin.
 var t:=Transform3D.IDENTITY
 var n:Node=sk
 while n!=source and n!=null:
  if n is Node3D:t=n.transform*t
  n=n.get_parent()
 sk.owner=null
 mesh.owner=null
 sk.get_parent().remove_child(sk)
 var avatar:=Node3D.new()
 var wrapper:=Node3D.new();wrapper.name="Gilla_Rig";avatar.add_child(wrapper)
 wrapper.add_child(sk);sk.name="Skeleton3D"
 # Original scene is 1.7m tall; normalize before existing Grinner sizing.
 wrapper.scale=Vector3.ONE*.65
 sk.transform=t
 source.free()
 return avatar

# Keep only arm-weighted triangles for the local view. A single skinned mesh
# otherwise puts the camera inside this large, double-sided torso.
static func first_person_arms(mesh:MeshInstance3D,rig:Skeleton3D)->void:
 if arm_mesh_cache:
  mesh.mesh=arm_mesh_cache;return
 var src:=mesh.mesh as ArrayMesh
 var skin:=mesh.skin
 var roots:=[rig.find_bone("upperarm_L"),rig.find_bone("upperarm_R")]
 var selected:={}
 for bind in skin.get_bind_count():
  var bone:=rig.find_bone(str(skin.get_bind_name(bind)))
  while bone>=0:
   if bone in roots:selected[bind]=true;break
   bone=rig.get_bone_parent(bone)
 var result:=ArrayMesh.new()
 for surface in src.get_surface_count():
  var arrays:=src.surface_get_arrays(surface)
  var vertices:PackedVector3Array=arrays[Mesh.ARRAY_VERTEX]
  var bones:PackedInt32Array=arrays[Mesh.ARRAY_BONES]
  var weights:PackedFloat32Array=arrays[Mesh.ARRAY_WEIGHTS]
  var indices:PackedInt32Array=arrays[Mesh.ARRAY_INDEX]
  if vertices.is_empty() or bones.is_empty() or weights.is_empty() or indices.is_empty():continue
  var count:=int(bones.size()/vertices.size())
  var inside:=PackedByteArray();inside.resize(vertices.size())
  for v in vertices.size():
   var weight:=0.0
   for j in count:
    var at:=v*count+j
    if selected.has(bones[at]):weight+=weights[at]
   if weight>=.8:inside[v]=1
  var kept:=PackedInt32Array()
  for i in range(0,indices.size(),3):
   if int(inside[indices[i]])+int(inside[indices[i+1]])+int(inside[indices[i+2]])>=3:
    kept.append(indices[i]);kept.append(indices[i+1]);kept.append(indices[i+2])
  if kept.is_empty():continue
  arrays[Mesh.ARRAY_INDEX]=kept
  var flags:=Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if count==8 else 0
  result.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES,arrays,[],{},flags)
  result.surface_set_material(result.get_surface_count()-1,src.surface_get_material(surface))
 if result.get_surface_count()>0:arm_mesh_cache=result;mesh.mesh=result
