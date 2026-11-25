extends Node3D
# Two filtered pieces of the original normal avatar; effects are short-lived.
static var meshes:Dictionary={}
var pieces:Array[Node3D]=[]
var age:=0.0
var origin:=Vector3.ZERO
var lateral:=Vector3.RIGHT
var links:Array[Node3D]=[]
static func cut_mesh(src:ArrayMesh,side:int)->ArrayMesh:
 if meshes.has(side):return meshes[side]
 var out:=ArrayMesh.new()
 for surface in src.get_surface_count():
  var arrays:=src.surface_get_arrays(surface)
  var vertices:PackedVector3Array=arrays[Mesh.ARRAY_VERTEX]
  var ids:PackedInt32Array=arrays[Mesh.ARRAY_INDEX]
  if ids.is_empty():continue
  var kept:=PackedInt32Array()
  for i in range(0,ids.size(),3):
   # Divide at the center plane. Majority rule keeps each triangle once.
   var vote:=0
   for k in 3:
    if vertices[ids[i+k]].x*side>=0:vote+=1
   if vote>=2:
    kept.append(ids[i]);kept.append(ids[i+1]);kept.append(ids[i+2])
  if kept.is_empty():continue
  arrays[Mesh.ARRAY_INDEX]=kept
  var bones:PackedInt32Array=arrays[Mesh.ARRAY_BONES]
  var flags:=Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if bones.size()==vertices.size()*8 else 0
  out.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES,arrays,[],{},flags)
  out.surface_set_material(out.get_surface_count()-1,src.surface_get_material(surface))
 meshes[side]=out
 return out
static func spawn(scene:Node3D,where:Vector3,yaw:float,color:int)->void:
 if not scene.is_inside_tree():return
 var effect=load("res://split_visual.gd").new();scene.add_child(effect);effect.start(where,yaw,color)
func start(where:Vector3,yaw:float,color:int)->void:
 origin=where;lateral=Basis(Vector3.UP,yaw)*Vector3.RIGHT
 for side in [-1,1]:
  var half:=Node3D.new();add_child(half);pieces.append(half)
  var a=load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D
  a.rotation.y=PI;a.scale=Vector3.ONE*1.45;half.add_child(a)
  var sk=a.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
  var vis=sk.get_node("Gilla") as MeshInstance3D
  vis.mesh=cut_mesh(vis.mesh as ArrayMesh,side);vis.layers=1
  preload("res://cosmetics.gd").paint(vis,color)
  # Bloody inner surface makes the open seam clear from either side.
  var wound:=MeshInstance3D.new();var meat:=SphereMesh.new();meat.radius=.5;meat.height=1
  wound.mesh=meat;wound.position=Vector3(side*.025,.82,0);wound.scale=Vector3(.06,.75,.35)
  wound.material_override=preload("res://injury_visual.gd").flesh_material();half.add_child(wound)
  half.global_position=where;half.rotation.y=yaw
 for i in 18:
  var link:=MeshInstance3D.new();var cylinder:=CylinderMesh.new();cylinder.top_radius=.006+float(i%3)*.003;cylinder.bottom_radius=.01+float(i%2)*.004;cylinder.height=1;cylinder.radial_segments=5
  link.mesh=cylinder;var m:=StandardMaterial3D.new();m.albedo_color=Color("8b101d");m.roughness=.4;link.material_override=m;add_child(link);links.append(link)
 preload("res://injury_visual.gd").blood(get_parent(),where+Vector3.UP*.85)
func _process(delta:float)->void:
 age+=delta
 var separation:=.65*smoothstep(0.0,.85,age)
 for i in 2:
  var dir:float=1 if i==0 else -1
  pieces[i].global_position=origin+lateral*dir*separation+Vector3(0,-.18*clampf(age-1.0,0,1),0)
  pieces[i].rotation.z=dir*.22*clampf(age,0,1)
 for i in links.size():
  var strand:=int(i/2)
  var y:=.50+float(strand)*.075
  var z:=.10*sin(strand*2.9)
  var left:=origin-lateral*separation+Vector3(0,y,z)
  var right:=origin+lateral*separation+Vector3(0,y+.08*sin(strand*4.0),-z*.7)
  var midpoint:Vector3=(left+right)*.5+Vector3(0,-.05*sin(PI*clampf(age/.85,0,1))*(1+strand%3),.07*cos(strand*3.0))
  var a:=left if i%2==0 else midpoint
  var b:=midpoint if i%2==0 else right
  var d:=b-a;var link=links[i]
  link.visible=age<.85
  link.global_position=(a+b)*.5
  if d.length()>.001:link.global_basis=Basis(Quaternion(Vector3.UP,d.normalized())) * Basis.from_scale(Vector3(1,d.length(),1))
 if age>3.0:
  var fade:=1.0-clampf((age-3.0)/.8,0,1)
  for p in pieces:p.scale=Vector3.ONE*maxf(fade,.001)
 if age>3.8:queue_free()
