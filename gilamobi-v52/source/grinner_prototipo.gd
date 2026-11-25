extends Node3D
# Estudo visual não oficial: o personagem-base continua intacto no jogo.
const MOUTH_Y := 0.795
const MOUTH_Z := 0.255

func _ready() -> void:
 var avatar := load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D
 avatar.name = "ModeloBaseModificado"
 add_child(avatar)
 var sk := avatar.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 var face := sk.get_node("Gilla") as MeshInstance3D
 var body_mat := (face.mesh.surface_get_material(0) as StandardMaterial3D).duplicate() as StandardMaterial3D
 body_mat.albedo_texture = load("res://textures/grinner_fur.png")
 body_mat.albedo_color = Color(0.49, 0.45, 0.45)
 face.set_surface_override_material(0, body_mat)
 var eye_mat := _material(Color(0.035,0.004,0.006))
 face.set_surface_override_material(1, eye_mat)
 face.set_blend_shape_value(face.find_blend_shape_by_name("MouthOpen"), 1.0)
 face.set_blend_shape_value(face.find_blend_shape_by_name("Scream"), 0.65)
 var head := sk.find_bone("head")
 if head >= 0: sk.set_bone_pose_scale(head, Vector3(1.20,1.12,1.16))
 for side in ["L", "R"]:
  var eye := sk.find_bone("eye_"+side)
  if eye >= 0: sk.set_bone_pose_scale(eye, Vector3(0.72,0.72,0.72))
 for side in ["L", "R"]:
  var b := sk.find_bone("upperarm_"+side)
  if b >= 0: sk.set_bone_pose_scale(b, Vector3(1.0,1.18,1.0))
 _make_mouth()

func _material(c: Color) -> StandardMaterial3D:
 var m := StandardMaterial3D.new()
 m.albedo_color = c
 m.roughness = 0.95
 m.cull_mode = BaseMaterial3D.CULL_DISABLED
 return m

func _outline(t: float, scale_factor: float = 1.0) -> Vector3:
 # Irregular tear with an arched upper lip, broad lower jaw and tapered corners.
 var co := cos(t)
 var si := sin(t)
 var x := 0.137 * co * (1.0 + 0.025 * sin(7.0*t) + 0.012*cos(13.0*t))
 var y := 0.137 * si * (1.0 + 0.03*sin(5.0*t + 0.3))
 if si > 0.0: y *= 0.78
 if si < 0.0: y *= 1.15
 # Edges curve back toward the face; center of mouth remains recessed.
 return Vector3(x*scale_factor, MOUTH_Y + y*scale_factor, MOUTH_Z - 0.028*abs(co))

func _make_mouth() -> void:
 var skin := _material(Color(0.20,0.026,0.034))
 var gum := _material(Color(0.11,0.009,0.014))
 var dark := _material(Color(0.027,0.002,0.004))
 var red := _material(Color(0.065,0.008,0.012))
 var enamel := _material(Color(0.62,0.55,0.41))
 var n := 96
 # Layered irregular meshes rather than an oval disc.
 _ring(n,1.055,0.99,skin,0.0)
 _ring(n,1.0,0.85,gum,0.004)
 var surf := SurfaceTool.new()
 surf.begin(Mesh.PRIMITIVE_TRIANGLES)
 for i in n:
  var a := TAU * float(i)/n
  var b := TAU * float(i+1)/n
  var center := Vector3(0,MOUTH_Y, MOUTH_Z + 0.002)
  var p := _outline(a,0.88); p.z += 0.005
  var q := _outline(b,0.88); q.z += 0.005
  surf.add_vertex(center); surf.add_vertex(p); surf.add_vertex(q)
 surf.generate_normals()
 var cavity := MeshInstance3D.new()
 cavity.mesh = surf.commit()
 cavity.material_override = dark
 add_child(cavity)
 # Tongue low and recessed, rather than covering the full cavity.
 _ellipsoid(Vector3(0.005, MOUTH_Y-0.075, MOUTH_Z+0.009), Vector3(0.035,0.013,0.006), red)
 var rng := RandomNumberGenerator.new()
 rng.seed = 19517
 # Irregular, individually sized teeth following top and bottom jaw edges.
 for upper in [true, false]:
  for i in 21:
   var x := lerpf(-0.125,0.125,float(i)/20.0) + rng.randf_range(-0.002,0.002)
   var a := acos(clampf(x / 0.137,-0.99,0.99))
   if not upper: a = TAU - a
   var border := _outline(a,0.90)
   var length := rng.randf_range(0.012,0.032)
   if i in [3,6,14,17]: length *= 1.35
   var tooth := MeshInstance3D.new()
   var cone := CylinderMesh.new()
   cone.top_radius = 0.0
   cone.bottom_radius = rng.randf_range(0.0030,0.006)
   cone.height = length
   cone.radial_segments = 5
   tooth.mesh = cone
   tooth.material_override = enamel
   var inward := Vector2(-border.x, MOUTH_Y-border.y).normalized()
   tooth.position = border + Vector3(inward.x*length*0.43,inward.y*length*0.43,0.010+rng.randf_range(0.0,0.004))
   tooth.rotation.z = atan2(-inward.x,inward.y) + rng.randf_range(-0.13,0.13)
   tooth.rotation.x = rng.randf_range(-0.12,0.12)
   add_child(tooth)

func _ring(count: int, outside: float, inside: float, mat: Material, zoff: float) -> void:
 var s := SurfaceTool.new()
 s.begin(Mesh.PRIMITIVE_TRIANGLES)
 for i in count:
  var a := TAU * float(i)/count
  var b := TAU * float(i+1)/count
  var p := _outline(a,outside); p.z += zoff
  var q := _outline(b,outside); q.z += zoff
  var r := _outline(a,inside); r.z += zoff+0.003
  var u := _outline(b,inside); u.z += zoff+0.003
  s.add_vertex(p); s.add_vertex(q); s.add_vertex(r)
  s.add_vertex(q); s.add_vertex(u); s.add_vertex(r)
 s.generate_normals()
 var node := MeshInstance3D.new()
 node.mesh = s.commit()
 node.material_override = mat
 add_child(node)

func _ellipsoid(p: Vector3, radii: Vector3, mat: Material) -> void:
 var n := MeshInstance3D.new()
 var mesh := SphereMesh.new()
 mesh.radius = 1.0
 mesh.height = 2.0
 mesh.radial_segments = 24
 mesh.rings = 12
 n.mesh = mesh
 n.scale = radii
 n.position = p
 n.material_override = mat
 add_child(n)
