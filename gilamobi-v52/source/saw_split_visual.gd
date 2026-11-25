extends Node3D
# Approximate horizontal mesh split at the blade's contact height.
static var cache:Dictionary={}
var halves:Array[Node3D]=[]
var links:Array[MeshInstance3D]=[]
var origin:=Vector3.ZERO
var cut_height:=.8
var age:=0.0
static func filtered(source:ArrayMesh,skin:Skin,rig:Skeleton3D,side:int,level:float)->ArrayMesh:
 var bucket:=clampi(roundi(level*10),3,12)
 var key:=str(side)+":"+str(bucket)
 if cache.has(key):return cache[key]
 var result:=ArrayMesh.new();var threshold:=float(bucket)/10.0/145.0
 var arm_binds:={};var roots:=[rig.find_bone("upperarm_L"),rig.find_bone("upperarm_R")]
 for bind in skin.get_bind_count():
  var bone:=rig.find_bone(str(skin.get_bind_name(bind)))
  if bone<0:bone=skin.get_bind_bone(bind)
  while bone>=0:
   if bone in roots:arm_binds[bind]=true;break
   bone=rig.get_bone_parent(bone)
 for surf in source.get_surface_count():
  var data:=source.surface_get_arrays(surf)
  var v:PackedVector3Array=data[Mesh.ARRAY_VERTEX];var idx:PackedInt32Array=data[Mesh.ARRAY_INDEX];var keep:=PackedInt32Array()
  var bone_ids:PackedInt32Array=data[Mesh.ARRAY_BONES];var weights:PackedFloat32Array=data[Mesh.ARRAY_WEIGHTS]
  var arm:=PackedByteArray();arm.resize(v.size())
  if not v.is_empty() and not bone_ids.is_empty() and bone_ids.size()==weights.size():
   var count:=int(bone_ids.size()/v.size())
   for q in v.size():
    var weight:=0.0
    for j in count:
     if arm_binds.has(bone_ids[q*count+j]):weight+=weights[q*count+j]
    if weight>.55:arm[q]=1
  for i in range(0,idx.size(),3):
   var vote:=0
   for j in 3:
    var vertex:=idx[i+j]
    if (arm[vertex]==1 and side==1) or (arm[vertex]==0 and (v[vertex].z-threshold)*side>=0):vote+=1
   if vote>=2:
    keep.append(idx[i]);keep.append(idx[i+1]);keep.append(idx[i+2])
  if keep.is_empty():continue
  data[Mesh.ARRAY_INDEX]=keep
  var bone:PackedInt32Array=data[Mesh.ARRAY_BONES]
  var flags:=Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if bone.size()==v.size()*8 else 0
  result.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES,data,[],{},flags)
  result.surface_set_material(result.get_surface_count()-1,source.surface_get_material(surf))
 cache[key]=result
 return result
static func spawn(scene:Node3D,where:Vector3,yaw:float,color:int,contact:Vector3)->void:
 if not scene.is_inside_tree():return
 var n=load("res://saw_split_visual.gd").new();scene.add_child(n);n.start(where,yaw,color,contact)
func start(where:Vector3,yaw:float,color:int,contact:Vector3)->void:
 origin=where;cut_height=clampf(round((contact.y-where.y)*10)/10.0,.3,1.2)
 for side in [-1,1]:
  var half:=Node3D.new();half.rotation.y=yaw;add_child(half);halves.append(half)
  var model=load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D
  model.rotation.y=PI;model.scale=Vector3.ONE*1.45;half.add_child(model)
  var rig=model.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
  var mesh=rig.get_node("Gilla") as MeshInstance3D
  mesh.mesh=filtered(mesh.mesh as ArrayMesh,mesh.skin,rig,side,cut_height);mesh.layers=1
  preload("res://cosmetics.gd").paint(mesh,color)
  var flesh:=MeshInstance3D.new();var meat:=SphereMesh.new();meat.radius=.5;meat.height=1;flesh.mesh=meat
  flesh.position=Vector3(0,cut_height+side*.012,0);flesh.scale=Vector3(.46,.045,.31);flesh.material_override=preload("res://injury_visual.gd").flesh_material();half.add_child(flesh)
  half.global_position=where
 for i in 24:
  var piece:=MeshInstance3D.new();var shape:=CylinderMesh.new();shape.top_radius=.004+float(i%4)*.002;shape.bottom_radius=.006+float(i%3)*.002;shape.height=1;shape.radial_segments=5
  piece.mesh=shape;piece.material_override=preload("res://injury_visual.gd").flesh_material();add_child(piece);links.append(piece)
 preload("res://injury_visual.gd").blood(get_parent(),contact)
func _process(delta:float)->void:
 age+=delta
 var separation:=.28*smoothstep(0.0,.9,age)
 halves[0].global_position=origin+Vector3(0,-separation*.25,0)
 halves[1].global_position=origin+Vector3(0,separation,0)
 halves[1].rotation.z=.24*clampf(age,0,1)
 for i in links.size():
  var strand:=int(i/2)
  var angle:=strand*2.399;var radius:=.07+.012*(strand%8)
  var base:=origin+Vector3(cos(angle)*radius,cut_height,sin(angle)*radius*.65)
  var a:=base+Vector3.DOWN*separation*.25
  var b:=base+Vector3.UP*separation
  var mid:Vector3=(a+b)*.5+Vector3(.035*sin(strand*4),0,.04*cos(strand*3))
  var start:=a if i%2==0 else mid;var end:=mid if i%2==0 else b
  var d:=end-start;var l:=links[i];l.visible=age<1.0;l.global_position=(start+end)*.5
  if d.length()>.001:l.global_basis=Basis(Quaternion(Vector3.UP,d.normalized()))*Basis.from_scale(Vector3(1,d.length(),1))
 if age>3.0:
  for h in halves:h.scale=Vector3.ONE*maxf(.001,1.0-(age-3.0)/.8)
 if age>3.8:queue_free()
