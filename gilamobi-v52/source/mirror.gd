extends Node3D

var player: CharacterBody3D
var targets: Array[Node3D]
var avatar_path := "res://Gilla_PlayerModel.fbx"
var avatar_scale := 1.45
var is_grinner := false
var game: Node
var quality := "medio"
var distance_timer := 0.0
var mirror_view: SubViewport
var mirror_camera: Camera3D
var arm_modifiers:Array[SkeletonModifier3D]=[]

func mirror_size() -> Vector2i:
 if quality == "baixo": return Vector2i(192,256)
 if quality == "medio": return Vector2i(288,384)
 return Vector2i(384,512)

func set_graphics_quality(value: String) -> void:
 quality = value
 if is_instance_valid(mirror_view): mirror_view.size = mirror_size()

func _process(delta: float) -> void:
 distance_timer += delta
 if distance_timer < 0.20 or not is_instance_valid(mirror_view): return
 distance_timer = 0.0
 var playing: bool = is_instance_valid(game) and game.game_running
 var near := is_instance_valid(player) and player.global_position.distance_to(Vector3(-4*game.MAP_SCALE,1.55,-8.875*game.MAP_SCALE)) < 24.0
 var mode := SubViewport.UPDATE_WHEN_VISIBLE if playing and near and visible else SubViewport.UPDATE_DISABLED
 if mirror_view.render_target_update_mode != mode: mirror_view.render_target_update_mode = mode
 for modifier in arm_modifiers:
  if is_instance_valid(game.g2_xr) and game.g2_xr.immersive:modifier.set_meta("g2_orientation",true)
  elif modifier.has_meta("g2_orientation"):modifier.remove_meta("g2_orientation")

func _ready() -> void:
 # A second version of the same rig is visible only to the mirror camera.
 # Unlike the first-person rig it keeps its head, and uses the same hand goals.
 var avatar:Node3D = preload("res://monster_model.gd").instantiate() if is_grinner else load(avatar_path).instantiate() as Node3D
 avatar.name = "PersonagemNoEspelho"
 avatar.rotation.y = PI
 avatar.scale = Vector3.ONE * avatar_scale
 player.add_child(avatar)
 var rig := avatar.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 var avatar_mesh := rig.get_node("Gilla") as MeshInstance3D
 avatar_mesh.layers = 4
 if not is_grinner:
  preload("res://cosmetics.gd").paint(avatar_mesh,game.cosmetic_color)
  preload("res://cosmetics.gd").apply_accessory(player,game.cosmetic_accessory,4)
 for i in 2:
  var side := "L" if i == 0 else "R"
  var mod: SkeletonModifier3D = preload("res://arm_modifier.gd").new()
  mod.target = targets[i]
  mod.player = player
  mod.suffix = side
  mod.side = -1.0 if i == 0 else 1.0
  rig.add_child(mod)
  arm_modifiers.append(mod)

 mirror_view = SubViewport.new()
 mirror_view.name = "MirrorView"
 mirror_view.size = mirror_size()
 mirror_view.world_3d = get_viewport().find_world_3d()
 # The mirror does not need another render of the entire world when the
 # player is far away or the game is paused/in the menu.
 mirror_view.render_target_update_mode = SubViewport.UPDATE_DISABLED
 add_child(mirror_view)
 mirror_camera = Camera3D.new()
 mirror_camera.name = "CameraEspelho"
 mirror_camera.position = Vector3(-4*game.MAP_SCALE,1.35,-8.78*game.MAP_SCALE)
 mirror_camera.rotation.y = PI
 mirror_camera.fov = 75 if is_grinner else 65
 mirror_camera.near = 0.05
 mirror_camera.cull_mask = 1 | 4
 mirror_view.add_child(mirror_camera)
 mirror_camera.current = true

 var surface := MeshInstance3D.new()
 surface.name = "Vidro"
 var quad := QuadMesh.new()
 quad.size = Vector2(2.1,2.55)
 surface.mesh = quad
 surface.position = Vector3(-4*game.MAP_SCALE,1.55,-8.875*game.MAP_SCALE)
 surface.layers = 8
 var shader := Shader.new()
 shader.code = """shader_type spatial;
render_mode unshaded, cull_disabled;
uniform sampler2D mirror_image : source_color, filter_linear;
void fragment() { ALBEDO = texture(mirror_image, vec2(1.0-UV.x, UV.y)).rgb; }
"""
 var mat := ShaderMaterial.new()
 mat.shader = shader
 mat.set_shader_parameter("mirror_image", mirror_view.get_texture())
 surface.material_override = mat
 add_child(surface)
 var frame := StandardMaterial3D.new()
 frame.albedo_color = Color("50483b")
 frame.roughness = 0.8
 for spec in [
  [Vector3(-5.10,1.55,-8.83), Vector3(.10,2.75,.10)],
  [Vector3(-2.90,1.55,-8.83), Vector3(.10,2.75,.10)],
  [Vector3(-4,2.88,-8.83), Vector3(2.3,.10,.10)],
  [Vector3(-4,.22,-8.83), Vector3(2.3,.10,.10)]
 ]:
  var piece := MeshInstance3D.new()
  var mesh := BoxMesh.new()
  mesh.size = spec[1]
  piece.mesh = mesh
  piece.position = Vector3(spec[0].x-4.0*(game.MAP_SCALE-1.0),spec[0].y,spec[0].z-8.83*(game.MAP_SCALE-1.0))
  piece.material_override = frame
  add_child(piece)
