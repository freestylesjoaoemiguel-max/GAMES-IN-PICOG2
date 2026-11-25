extends RefCounted
# Visual-only helpers. These use the supplied player model, not external assets.
const HEAD := 1
const LEFT_ARM := 2
const RIGHT_ARM := 4
static var piece_meshes := {}
static var cached_flesh:StandardMaterial3D

static func mask_for(part: String) -> int:
 if part == "head": return HEAD
 if part == "arm_l": return LEFT_ARM
 if part == "arm_r": return RIGHT_ARM
 return 0

static func update_rig(rig: Skeleton3D, mask: int, hide_head := false) -> void:
 if not is_instance_valid(rig): return
 for spec in [["head",HEAD],["upperarm_L",LEFT_ARM],["upperarm_R",RIGHT_ARM]]:
  var bone := rig.find_bone(spec[0])
  if bone >= 0:
   var hidden: bool = (mask & int(spec[1])) != 0 or (hide_head and spec[0] == "head")
   rig.set_bone_pose_scale(bone, Vector3.ONE * (0.001 if hidden else 1.0))
 for child in rig.get_children():
  if child is SkeletonModifier3D and child.get("suffix") in ["L","R"]:
   var bit := LEFT_ARM if child.get("suffix") == "L" else RIGHT_ARM
   child.active = (mask & bit) == 0

static func update_wounds(parent: Node3D, mask: int, layers: int) -> void:
 if not is_instance_valid(parent): return
 var old := parent.get_node_or_null("Ferimentos")
 if old: old.free()
 if mask == 0: return
 var holder := Node3D.new();holder.name="Ferimentos";parent.add_child(holder)
 for item in [[HEAD, Vector3(0,1.23,-.04),Vector3(.19,.065,.18)], [LEFT_ARM,Vector3(-.23,1.02,.02),Vector3(.13,.15,.14)], [RIGHT_ARM,Vector3(.23,1.02,.02),Vector3(.13,.15,.14)]]:
  if (mask & int(item[0])) == 0: continue
  var wound := MeshInstance3D.new()
  var meat := SphereMesh.new();meat.radius=.5;meat.height=1.0
  wound.mesh=meat;wound.position=item[1];wound.scale=item[2];wound.layers=layers
  var red := StandardMaterial3D.new();red.albedo_color=Color("982c33");red.roughness=.9
  wound.material_override=red;holder.add_child(wound)

static func blood(scene: Node, position: Vector3) -> void:
 if not is_instance_valid(scene) or not scene.is_inside_tree(): return
 var spray := CPUParticles3D.new();spray.name="Sangue"
 spray.one_shot=true;spray.explosiveness=1.0;spray.amount=42;spray.lifetime=1.05
 spray.emission_shape=CPUParticles3D.EMISSION_SHAPE_SPHERE;spray.emission_sphere_radius=.04
 spray.direction=Vector3.UP;spray.spread=70.0;spray.gravity=Vector3(0,-8,0)
 spray.initial_velocity_min=1.2;spray.initial_velocity_max=4.5
 spray.scale_amount_min=.018;spray.scale_amount_max=.065
 var sphere:=SphereMesh.new();sphere.radius=.5;sphere.height=1.0
 var mat:=StandardMaterial3D.new();mat.albedo_color=Color("a01924");mat.roughness=.8
 sphere.material=mat;spray.mesh=sphere
 scene.add_child(spray);spray.global_position=position;spray.emitting=true
 scene.get_tree().create_timer(2.0).timeout.connect(func(): if is_instance_valid(spray): spray.queue_free())

static func drop_piece(scene: Node3D, piece: Node3D, contact: Vector3, velocity: Vector3) -> void:
 if not is_instance_valid(scene) or not is_instance_valid(piece): return
 var transform_before := piece.global_transform
 var body := RigidBody3D.new();body.name="ParteSolta"
 body.collision_layer=0;body.collision_mask=1;body.mass=.5
 scene.add_child(body);body.global_transform=transform_before
 var part := str(piece.get_meta("part", ""))
 var goal: Vector3 = piece.get_meta("piece_goal", Vector3.ZERO)
 var dropped := make_piece(part,int(piece.get_meta("color",0)))
 if dropped:
  body.add_child(dropped)
  pose_piece(dropped,part,goal)
 piece.queue_free()
 var collision := CollisionShape3D.new()
 var shape := SphereShape3D.new();shape.radius=.16
 collision.shape=shape;collision.position=contact
 body.add_child(collision);body.linear_velocity=velocity.limit_length(18.0)
 body.angular_velocity=Vector3(0,1.2,0)
 scene.get_tree().create_timer(12.0).timeout.connect(func(): if is_instance_valid(body): body.queue_free())

static func part_mesh(mesh: ArrayMesh, skin: Skin, rig: Skeleton3D, part: String) -> ArrayMesh:
 if piece_meshes.has(part): return piece_meshes[part]
 var parent_bone := rig.find_bone("head" if part=="head" else ("upperarm_L" if part=="arm_l" else "upperarm_R"))
 if parent_bone < 0: return null
 var selected := {}
 for bind in skin.get_bind_count():
  var bone := rig.find_bone(str(skin.get_bind_name(bind)))
  if bone < 0: bone=skin.get_bind_bone(bind)
  while bone >= 0:
   if bone == parent_bone: selected[bind]=true;break
   bone=rig.get_bone_parent(bone)
 var result:=ArrayMesh.new()
 for surface in mesh.get_surface_count():
  var arrays:=mesh.surface_get_arrays(surface)
  var vertices:PackedVector3Array=arrays[Mesh.ARRAY_VERTEX]
  var bones:PackedInt32Array=arrays[Mesh.ARRAY_BONES]
  var weights:PackedFloat32Array=arrays[Mesh.ARRAY_WEIGHTS]
  var indices:PackedInt32Array=arrays[Mesh.ARRAY_INDEX]
  if vertices.is_empty() or bones.is_empty() or weights.is_empty() or indices.is_empty(): continue
  var count:=int(bones.size()/vertices.size())
  var inside:=PackedByteArray();inside.resize(vertices.size())
  for v in vertices.size():
   var weight:=0.0
   for j in count:
    var at:=v*count+j
    if selected.has(bones[at]): weight+=weights[at]
   if weight>=.45: inside[v]=1
  var kept:=PackedInt32Array()
  for i in range(0,indices.size(),3):
   if int(inside[indices[i]])+int(inside[indices[i+1]])+int(inside[indices[i+2]]) >= 2:
    kept.append(indices[i]);kept.append(indices[i+1]);kept.append(indices[i+2])
  if kept.is_empty(): continue
  arrays[Mesh.ARRAY_INDEX]=kept
  var flags:=Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if count==8 else 0
  result.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, flags)
  result.surface_set_material(result.get_surface_count()-1, mesh.surface_get_material(surface))
 piece_meshes[part]=result
 return result

static func pose_piece(root: Node3D, part: String, target_local: Vector3) -> void:
 root.set_meta("piece_goal",target_local)
 update_wounds(root,mask_for(part),1)
 if part not in ["arm_l","arm_r"] or target_local==Vector3.ZERO: return
 var source := root.get_child(0) as Node3D
 var rig := source.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 var marker:=Marker3D.new();marker.position=target_local;root.add_child(marker)
 var modifier: SkeletonModifier3D = preload("res://arm_modifier.gd").new()
 modifier.player=root;modifier.target=marker;modifier.suffix="L" if part=="arm_l" else "R"
 modifier.side=-1.0 if part=="arm_l" else 1.0
 rig.add_child(modifier)

static func make_piece(part: String, color := 0) -> Node3D:
 var source:=load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D
 var rig:=source.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 var visual:=rig.get_node("Gilla") as MeshInstance3D
 var filtered:=part_mesh(visual.mesh as ArrayMesh, visual.skin, rig, part)
 if filtered == null or filtered.get_surface_count() == 0: source.free();return null
 visual.mesh=filtered;visual.layers=1
 preload("res://cosmetics.gd").paint(visual,color)
 source.rotation.y=PI;source.scale=Vector3.ONE*1.45
 var root:=Node3D.new();root.set_meta("part",part);root.set_meta("color",color);root.add_child(source)
 return root

static func strands(scene:Node3D,a:Vector3,b:Vector3)->void:
 if not is_instance_valid(scene) or not scene.is_inside_tree() or a.distance_to(b)>5.0:return
 var effect=preload("res://gore_effect.gd").new();scene.add_child(effect);effect.setup(a,b)

static func flesh_material()->StandardMaterial3D:
 if cached_flesh:return cached_flesh
 var image:=Image.create(96,96,false,Image.FORMAT_RGB8)
 var noise:=FastNoiseLite.new();noise.seed=518;noise.frequency=.095
 for y in 96:
  for x in 96:
   var n:float=(noise.get_noise_2d(x,y)+1)*.5
   var streak:=pow(absf(sin(x*.16+sin(y*.11)*2.0)),7.0)
   image.set_pixel(x,y,Color(.19+n*.32+streak*.08,.012+n*.048,.018+n*.035))
 cached_flesh=StandardMaterial3D.new();cached_flesh.albedo_texture=ImageTexture.create_from_image(image);cached_flesh.roughness=.33;cached_flesh.metallic=.05
 return cached_flesh

static func severed_piece(scene:Node3D,part:String,pos:Vector3,yaw:float,color:int)->void:
 if part not in ["arm_l","arm_r","head"]:return
 var piece:=make_piece(part,color)
 if not is_instance_valid(piece):return
 scene.add_child(piece);piece.global_position=pos;piece.rotation.y=yaw
 var contact:=Vector3(0,1.25,0) if part=="head" else Vector3(-.27 if part=="arm_l" else .27,1.0,0)
 var drift:=Basis(Vector3.UP,yaw)*Vector3(-1.1 if part=="arm_l" else 1.1,1.2,-.2)
 drop_piece(scene,piece,contact,drift)

static func saw_blood(scene:Node3D,position:Vector3)->void:
 blood(scene,position)
 if not is_instance_valid(scene) or not scene.is_inside_tree():return
 # Temporary glossy stains make the spray land on nearby floors.
 var query:=PhysicsRayQueryParameters3D.create(position+Vector3.UP*.05,position-Vector3.UP*2.4)
 if "player" in scene and is_instance_valid(scene.player):query.exclude=[scene.player.get_rid()]
 var hit:=scene.get_world_3d().direct_space_state.intersect_ray(query)
 if not hit.is_empty() and hit.normal.y>.8:
  var stains=scene.get_node_or_null("ManchasDeSangue")
  if not stains:stains=Node3D.new();stains.name="ManchasDeSangue";scene.add_child(stains)
  if stains.get_child_count()>=18:stains.get_child(0).queue_free()
  var stain:=MeshInstance3D.new();var blob:=SphereMesh.new();blob.radius=.5;blob.height=1;stain.mesh=blob;stain.material_override=flesh_material();stain.scale=Vector3(randf_range(.13,.30),.008,randf_range(.09,.22));stains.add_child(stain);stain.global_position=hit.position+Vector3.UP*.009
  scene.get_tree().create_timer(7).timeout.connect(func():if is_instance_valid(stain):stain.queue_free())
 var player:=AudioStreamPlayer3D.new();player.stream=load("res://sounds/saw_cut.wav");player.volume_db=-7;player.unit_size=3;player.max_distance=14
 scene.add_child(player);player.global_position=position;player.play()
 scene.get_tree().create_timer(.65).timeout.connect(func():if is_instance_valid(player):player.queue_free())
