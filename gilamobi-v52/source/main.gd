extends Node3D

var g2_xr: Node3D
var player: CharacterBody3D
var local_chat_bubble:Node3D
var camera: Camera3D
var skeleton: Skeleton3D
var targets: Array[Node3D] = []
var sandbox_camera:Node3D
var hand_dragging := [false, false]
# Active while a VR match is running.
var vr_hand_enabled := false
var vr_hand_offsets := [Vector3(-.38,-.30,-.65),Vector3(.38,-.30,-.65)]
var vr_hand_tracked := [false,false]
var vr_hand_can_push := [false,false]
var vr_floor_strength := [0.0,0.0]
var vr_push_cooldown := [0.0,0.0]
# Prevent ground friction from shaving off a new hand impulse on its launch frame.
var hand_push_applied := false
# Each hand can hold a different normal player; the victim follows the hand.
var held := [{}, {}]
var release_cooldown := 0.0
var wait_for_separation := [false, false]
var previous_palm := [Vector3.ZERO, Vector3.ZERO]
var have_previous_palm := false
var missing_parts := 0
var life_number := 0
var is_dead := false
var dead_timer := 0.0
var held_age := 0.0
var held_last_destination:=Vector3.ZERO
var held_destination_valid:=false
var monster_mode:="grab"
var chainsaw:Node3D
var chainsaw_previous: Array[Vector3]=[]
var chainsaw_previous_valid:=false
var chainsaw_contacts:={}
var chainsaw_swing_time:=0.0
var ragdoll:Node3D
const MAP_SCALE:=1.12
var selected_map:="base"
var loaded_map:=""
var hand_velocity := [Vector3.ZERO,Vector3.ZERO]
var last_moving_velocity := [Vector3.ZERO,Vector3.ZERO]
var last_moving_usec := [0,0]
var last_hand_world := [Vector3.ZERO,Vector3.ZERO]
var hand_velocity_valid := false
var detached_visuals := [null,null]
var pending_throw := Vector3.ZERO
var throw_pending := false
var pull_pair := ""
var pull_start := [Vector2.ZERO,Vector2.ZERO]
var pull_start_vr := [Vector3.ZERO,Vector3.ZERO]
var hand_screen := [Vector2.ZERO, Vector2.ZERO]
var hand_start_screen := [Vector2.ZERO, Vector2.ZERO]
var finger_start := [Vector2.ZERO, Vector2.ZERO]
var ui: Control
var game_menu: CanvasLayer
var network: Node
var directory: Node
var game_running := false
# Round assignment is authoritative from the room creator.
var round_id := -1
var round_revision := -1
var round_phase := "waiting"
var pending_round := {}
var round_notice := ""
var round_notice_until := 0
var selected_character := "normal"
var control_mode := "principal"
var move_input := Vector2.ZERO
var simple_grab := false
# Speaker mode: suppress outgoing audio while another nearby voice plays.
var echo_guard_enabled := true
var cosmetic_color := 0
var cosmetic_accessory := 0
var profile_name := "Jogador"

func load_custom_settings() -> void:
 var cfg := ConfigFile.new()
 if cfg.load("user://customizacao.cfg") == OK:
  cosmetic_color=clampi(int(cfg.get_value("visual","cor",0)),0,9)
  cosmetic_accessory=clampi(int(cfg.get_value("visual","acessorio",0)),0,preload("res://cosmetics.gd").NAMES.size()-1)
  profile_name=preload("res://profile_name.gd").clean(cfg.get_value("perfil","nome","Jogador"))
  echo_guard_enabled=bool(cfg.get_value("audio","protecao_eco",true))
  var mode:=str(cfg.get_value("controles","modo","principal"))
  if mode in ["principal","simples","dois_bracos"]:control_mode=mode

func save_custom_settings() -> Error:
 var cfg:=ConfigFile.new()
 cfg.set_value("visual","cor",cosmetic_color);cfg.set_value("visual","acessorio",cosmetic_accessory)
 cfg.set_value("controles","modo",control_mode)
 cfg.set_value("audio","protecao_eco",echo_guard_enabled)
 cfg.set_value("perfil","nome",profile_name)
 return cfg.save("user://customizacao.cfg")

func set_control_mode(mode: String) -> void:
 if mode not in ["principal","simples","dois_bracos"]:return
 control_mode=mode;move_input=Vector2.ZERO;simple_grab=false
 hand_dragging=[false,false]
 if is_instance_valid(ui):ui.reset_inputs()
 save_custom_settings()

func set_cosmetics(color: int, accessory: int) -> Error:
 cosmetic_color=clampi(color,0,9);cosmetic_accessory=clampi(accessory,0,preload("res://cosmetics.gd").NAMES.size()-1)
 var saved:=save_custom_settings()
 if is_instance_valid(player) and active_character=="normal":
  preload("res://cosmetics.gd").paint(skeleton.get_node("Gilla"),cosmetic_color)
  var mirror_avatar=player.get_node_or_null("PersonagemNoEspelho")
  if mirror_avatar:
   preload("res://cosmetics.gd").paint(mirror_avatar.get_node("Gilla_Rig/Skeleton3D/Gilla"),cosmetic_color)
   preload("res://cosmetics.gd").apply_accessory(player,cosmetic_accessory,4)
 return saved

func simple_jump() -> void:
 if game_running and not is_dead and is_instance_valid(player) and player.is_on_floor():player.velocity.y=4.8

# Medium saves rendering work by default; 2D controls remain full-resolution.
var graphics_quality := "baixo"
var active_character := ""
const GRINNER_SIZE := 1.5

func character_scale() -> float:
 return GRINNER_SIZE if selected_character == "grinner" else 1.0

func character_path() -> String:
 return "res://grinner_from_gilla.glb" if selected_character == "grinner" else "res://Gilla_PlayerModel.fbx"

func select_character(which: String) -> void:
 if which not in ["normal", "grinner"]: return
 selected_character = which
 var config := ConfigFile.new()
 config.set_value("jogador", "personagem", selected_character)
 config.save("user://personagem.cfg")

func load_character_choice() -> void:
 var config := ConfigFile.new()
 if config.load("user://personagem.cfg") == OK:
  var which: String = str(config.get_value("jogador", "personagem", "normal"))
  if which in ["normal", "grinner"]: selected_character = which

var look_sensitivity := 0.0035
var voice_face: Node
var look_pitch := -0.18
var base_rotation := {}
var arm_iks: Array[SkeletonModifier3D] = []
var hand_local := [Vector3(-0.52, 0.72, -0.42), Vector3(0.52, 0.72, -0.42)]
var hand_visual := [Vector3(-0.52, 0.72, -0.42), Vector3(0.52, 0.72, -0.42)]
var contact := [false, false]
const HAND_RADIUS := 0.05
const HAND_SPEED := 9.0
const MAX_HORIZONTAL_SPEED := 7.0
const MAX_UP_SPEED := 4.8
# Braking only on the ground, never while moving through the air.
const GROUND_FRICTION := 35.0

func load_graphics_settings() -> void:
 var config := ConfigFile.new()
 if config.load("user://graficos.cfg") == OK:
  var saved := str(config.get_value("video","qualidade","medio"))
  if saved in ["baixo","medio","alto"]: graphics_quality = saved
 set_graphics_quality(graphics_quality, false)

func set_graphics_quality(quality: String, save := true) -> void:
 if quality not in ["baixo","medio","alto"]: return
 graphics_quality = quality
 # Only the 3D scene is downscaled; UI and touch coordinates are unchanged.
 var scale_3d := 0.60 if quality == "baixo" else (0.80 if quality == "medio" else 1.0)
 get_viewport().scaling_3d_scale = scale_3d
 var mirror = get_node_or_null("Espelho")
 if is_instance_valid(mirror): mirror.set_graphics_quality(quality)
 if save:
  var config := ConfigFile.new()
  config.set_value("video","qualidade",quality)
  config.save("user://graficos.cfg")

func _ready() -> void:
 selected_character="normal"
 load_graphics_settings()
 load_custom_settings()
 network = preload("res://network.gd").new()
 network.game = self
 add_child(network)
 directory = preload("res://public_directory.gd").new()
 directory.game = self
 add_child(directory)
 game_menu = preload("res://menu.gd").new()
 game_menu.game = self
 add_child(game_menu)
 g2_xr=preload("res://g2_xr.gd").new()
 g2_xr.name="G2WebXR"
 g2_xr.game=self
 add_child(g2_xr)

func initialize_game() -> void:
 # Each progress update corresponds to a real initialization step.
 game_menu.set_loading_progress(0.08, tr("Preparando a base..."))
 await get_tree().process_frame
 await get_tree().create_timer(0.12).timeout
 setup_world()
 game_menu.set_loading_progress(0.40, tr("Criando o personagem..."))
 await get_tree().process_frame
 setup_player()
 active_character = selected_character
 game_menu.set_loading_progress(0.68, tr("Preparando o espelho..."))
 await get_tree().process_frame
 setup_mirror()
 game_menu.set_loading_progress(0.83, tr("Preparando os controles..."))
 await get_tree().process_frame
 voice_face = preload("res://voice_face.gd").new()
 voice_face.game = self
 voice_face.face = player.get_node("PersonagemNoEspelho/Gilla_Rig/Skeleton3D/Gilla")
 add_child(voice_face)
 var layer = CanvasLayer.new()
 add_child(layer)
 ui = preload("res://touch_controls.gd").new()
 ui.game = self
 layer.add_child(ui)
 ui.visible = false
 game_menu.set_loading_progress(1.0, tr("Pronto!"))
 await get_tree().create_timer(0.20).timeout

func material(c: Color, rough := 1.0) -> StandardMaterial3D:
 var m := StandardMaterial3D.new()
 m.albedo_color = c
 m.roughness = rough
 return m

func make_box(pos: Vector3, size: Vector3, col: Color, collide := true) -> void:
 var body := StaticBody3D.new()
 body.position = pos
 add_child(body)
 var mesh := MeshInstance3D.new()
 var box := BoxMesh.new()
 box.size = size
 mesh.mesh = box
 mesh.material_override = material(col)
 body.add_child(mesh)
 if collide:
  var collision := CollisionShape3D.new()
  var shape := BoxShape3D.new()
  shape.size = size
  collision.shape = shape
  body.add_child(collision)

func setup_world() -> void:
 var env := WorldEnvironment.new()
 var world := Environment.new()
 world.background_mode = Environment.BG_COLOR
 world.background_color = Color("262c31")
 world.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
 world.ambient_light_color = Color("b3c2d0")
 world.ambient_light_energy = 0.58
 env.environment = world
 add_child(env)
 env.name="AmbienteMapa"
 load_map(selected_map)

func load_map(which:String) -> void:
 if which not in ["base","backrooms","sandbox"]: which="base"
 selected_map=which
 if loaded_map==which and is_instance_valid(get_node_or_null("Base")): return
 if is_instance_valid(sandbox_camera):sandbox_camera.queue_free();sandbox_camera=null
 var old=get_node_or_null("Base")
 if old: old.free()
 var world_node:Node3D
 if which=="backrooms":world_node=preload("res://backrooms_builder.gd").new()
 elif which=="sandbox":world_node=preload("res://sandbox_builder.gd").new()
 else:world_node=preload("res://base_builder.gd").new()
 world_node.name="Base"
 add_child(world_node)
 if which=="sandbox":
  sandbox_camera=preload("res://sandbox_camera.gd").new();sandbox_camera.game=self;add_child(sandbox_camera)
 loaded_map=which
 var env=get_node_or_null("AmbienteMapa")
 if env:
  env.environment.ambient_light_color=Color("fff2d8") if which=="backrooms" else Color("b3c2d0")
  env.environment.ambient_light_energy=.78 if which=="backrooms" else .58
 var mirror=get_node_or_null("Espelho")
 if mirror: mirror.visible=(which=="base")
 if is_instance_valid(player):
  end_ragdoll();clear_grabs();player.global_position=Vector3(0,.04,2);player.velocity=Vector3.ZERO


func setup_player() -> void:
 player = CharacterBody3D.new()
 player.name = "Jogador"
 player.position = Vector3(0,0.04,2)
 add_child(player)
 var col := CollisionShape3D.new()
 var capsule := CapsuleShape3D.new()
 capsule.radius = 0.28 * character_scale()
 capsule.height = 1.35 * character_scale()
 col.shape = capsule
 col.position.y = 0.70 * character_scale()
 player.add_child(col)
 var avatar:Node3D = preload("res://monster_model.gd").instantiate() if selected_character=="grinner" else load(character_path()).instantiate() as Node3D
 avatar.name = "Modelo"
 avatar.rotation.y = PI
 avatar.scale = Vector3.ONE * (1.45 * character_scale())
 player.add_child(avatar)
 skeleton = avatar.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 var first_person_mesh := skeleton.get_node("Gilla") as MeshInstance3D
 first_person_mesh.layers = 2
 if selected_character=="grinner":preload("res://monster_model.gd").first_person_arms(first_person_mesh,skeleton)
 if selected_character=="normal":preload("res://cosmetics.gd").paint(first_person_mesh,cosmetic_color)
 # The head is hidden from the inside; the rest remains visible when looking down.
 var head := skeleton.find_bone("head")
 skeleton.set_bone_pose_scale(head, Vector3.ONE * 0.001)
 for side in ["L", "R"]:
  var goal := Marker3D.new()
  goal.name = "Target" + side
  player.add_child(goal)
  targets.append(goal)
  var ik: SkeletonModifier3D = preload("res://arm_modifier.gd").new()
  ik.name = "ArmIK" + side
  ik.target = goal
  ik.player = player
  ik.suffix = side
  ik.side = -1.0 if side == "L" else 1.0
  skeleton.add_child(ik)
  arm_iks.append(ik)
  for finger in ["index1_", "index2_", "pinkie1_", "pinkie2_", "thumb1_"]:
   var idx := skeleton.find_bone(finger + side)
   if idx >= 0: base_rotation[idx] = skeleton.get_bone_pose_rotation(idx)
 camera = Camera3D.new()
 camera.position = Vector3(0, 1.36, -0.18) * character_scale()
 camera.rotation.x = look_pitch
 camera.near = 0.03
 camera.fov = 90
 camera.cull_mask = 1 | 2 | 8
 player.add_child(camera)
 camera.make_current()
 update_hands()

func setup_mirror() -> void:
 var mirror := preload("res://mirror.gd").new()
 mirror.name = "Espelho"
 mirror.player = player
 mirror.targets = targets
 mirror.avatar_path = character_path()
 mirror.avatar_scale = 1.45 * character_scale()
 mirror.is_grinner = selected_character == "grinner"
 mirror.game = self
 mirror.quality = graphics_quality
 mirror.visible=selected_map=="base"
 add_child(mirror)

func apply_character_choice(preserve_match := false) -> void:
 if selected_character == active_character: return
 end_ragdoll()
 # Rebuild the rig but keep the same room, mic and VR headset/tracking alive.
 var was_running:=game_running
 var old_position:Vector3=player.global_position if is_instance_valid(player) else Vector3(0,.04,2)
 var old_yaw:float=player.rotation.y if is_instance_valid(player) else 0.0
 var old_camera_rotation:Vector3=camera.rotation if is_instance_valid(camera) else Vector3.ZERO
 var vr=get_node_or_null("VRBox") if preserve_match else null
 if is_instance_valid(vr) and is_instance_valid(vr.marker_root):vr.marker_root.reparent(self)
 game_running = false
 if not preserve_match:voice_face.stop_mic()
 voice_face.face = null
 var old_mirror := get_node_or_null("Espelho")
 if old_mirror: old_mirror.free()
 if is_instance_valid(player): player.free()
 player = null;camera = null;skeleton = null
 targets.clear();arm_iks.clear();base_rotation.clear()
 hand_dragging = [false, false]
 clear_grabs();contact = [false, false];look_pitch = -0.18
 active_character=selected_character
 setup_player()
 if preserve_match:
  player.global_position=old_position;player.rotation.y=old_yaw;camera.rotation=old_camera_rotation
 setup_mirror()
 voice_face.face = player.get_node("PersonagemNoEspelho/Gilla_Rig/Skeleton3D/Gilla")
 reset_health()
 if is_instance_valid(vr):
  if is_instance_valid(vr.marker_root):vr.marker_root.reparent(player)
  vr.previous_mask=camera.cull_mask;camera.cull_mask=0
  vr.update_marker_visibility();vr.sync_cameras()
 if preserve_match:game_running=was_running

func setup_sandbox_npcs()->void:
 if selected_map!="sandbox" or network.state=="joined":return
 for i in 5:
  var id:="sandbox_npc_"+str(i)
  if network.peers.has(id):continue
  var npc=preload("res://sandbox_npc.gd").new();npc.name="Manequim_"+str(i);add_child(npc)
  npc.configure("normal");npc.set_nickname(tr("Manequim"))
  npc.spawn_at(Vector3(-16.0+float(i)*2.5,.04,8.0));network.peers[id]=npc

func sandbox_button_positions()->Array:
 if selected_map!="sandbox":return []
 var base=get_node_or_null("Base")
 if not is_instance_valid(base):return []
 var result:=[]
 for capsule in ["CapsulaGilla","CapsulaMonstro"]:
  var marker=base.get_node_or_null(capsule+"/BotaoSelecao")
  if is_instance_valid(marker):result.append(marker)
 return result

func try_sandbox_press(screen_pos:Vector2)->bool:
 if selected_map!="sandbox" or not is_instance_valid(camera):return false
 for marker in sandbox_button_positions():
  if marker.global_position.distance_to(player.global_position)>3.2:continue
  if camera.is_position_behind(marker.global_position):continue
  if camera.unproject_position(marker.global_position).distance_to(screen_pos)<90:
   select_sandbox_role.call_deferred(str(marker.get_meta("role")));return true
 return false

func select_sandbox_role(role:String)->void:
 if selected_map!="sandbox" or role not in ["normal","grinner"] or not is_instance_valid(player):return
 if role==active_character:return
 remove_chainsaw();selected_character=role;apply_character_choice(true)
 monster_mode="choose" if role=="grinner" else "grab"
 if is_instance_valid(ui):ui.update_monster_choice()
 var vr=get_node_or_null("VRBox")
 if role=="grinner" and is_instance_valid(vr):vr.begin_monster_choice()

func show_local_chat(message:String)->void:
 if not is_instance_valid(player):return
 if not is_instance_valid(local_chat_bubble):
  local_chat_bubble=preload("res://chat_bubble.gd").new();add_child(local_chat_bubble)
 local_chat_bubble.global_position=player.global_position+Vector3(0,3.05 if active_character=="grinner" else 2.75,0)
 local_chat_bubble.say(message)

func reset_round_state()->void:
 round_id=-1;round_revision=-1;round_phase="waiting";pending_round={};round_notice="";round_notice_until=0

func receive_round(id:int, revision:int, phase:String, role:String)->void:
 if id<round_id or (id==round_id and revision<round_revision):return
 if not pending_round.is_empty():
  var p_id:int=pending_round.id;var p_rev:int=pending_round.revision
  if id<p_id or (id==p_id and revision<p_rev):return
 pending_round={"id":id,"revision":revision,"phase":phase,"role":role}
 apply_pending_round.call_deferred()

func apply_pending_round()->void:
 if pending_round.is_empty() or not is_instance_valid(player) or not is_instance_valid(ui) or not game_running:return
 if network.state!="joined":pending_round={};return
 var id:int=pending_round.id;var rev:int=pending_round.revision
 var role:String=pending_round.role;var phase:String=pending_round.phase
 if id<round_id or (id==round_id and rev<round_revision):pending_round={};return
 var new_round:=id!=round_id
 # Show the death screen briefly before converting, unless a new round starts.
 if not new_round and is_dead and role=="grinner" and dead_timer>0:return
 var changed:=active_character!=role
 if new_round or changed:
  life_number+=1
  selected_character=role
  if changed:apply_character_choice(true)
  else:reset_health();clear_grabs()
  player.velocity=Vector3.ZERO
  if role=="grinner":
   remove_chainsaw()
   monster_mode="choose";chainsaw_previous_valid=false
   if is_instance_valid(ui):ui.update_monster_choice()
   var v=get_node_or_null("VRBox")
   if is_instance_valid(v):v.begin_monster_choice()
  else:
   monster_mode="grab";remove_chainsaw()
   if is_instance_valid(ui):ui.update_monster_choice()
  if new_round:
   # Spread up to 10 players to avoid a stack on the same spawn point.
   var slot:int=absi(int(network.uid.hash()))%8
   player.global_position=Vector3(float(slot%4)*1.2-1.8,.04,float(slot/4)*1.5+1.5)
  if is_instance_valid(ui):ui.reset_inputs()
  round_notice="VOCÊ É O MONSTRO!" if role=="grinner" else "VOCÊ É GILLA!"
  round_notice_until=Time.get_ticks_msec()+4500
 round_id=id;round_revision=rev;round_phase=phase;pending_round={}
 if phase=="reset":round_notice="TODOS INFECTADOS • NOVA RODADA";round_notice_until=Time.get_ticks_msec()+4000
 elif phase=="waiting":round_notice="AGUARDANDO OUTRO JOGADOR";round_notice_until=Time.get_ticks_msec()+4000

func update_hands(delta := 0.0) -> void:
 if is_instance_valid(g2_xr) and g2_xr.immersive:
  g2_xr.step_hands(delta)
  update_grab_gestures()
  return
 for i in 2:
  vr_push_cooldown[i]=maxf(0.0,vr_push_cooldown[i]-delta)
  previous_palm[i] = targets[i].global_position
  if active_character == "normal" and (missing_parts & (2 if i == 0 else 4)) != 0:
   continue
  var side := -1.0 if i == 0 else 1.0
  # The idle hands are comfortably in front of the camera. When touched,
  # cast through the finger position: vertical drags move up OR down.
  var camera_offset := Vector3(side * 0.38, -0.30, -0.65)
  if control_mode=="simples" and simple_grab and active_character=="grinner": camera_offset=Vector3(side*.18,-.12,-1.12)
  if hand_dragging[i]:
   var screen_pos: Vector2 = hand_screen[i]
   var down_distance: float = maxf(0.0, screen_pos.y - hand_start_screen[i].y)
   var view_height := maxf(get_viewport().get_visible_rect().size.y, 1.0)
   # When looking roughly ahead, a downward drag also sweeps the hand
   # backwards. This physically pushes the body forward on contact, rather
   # than injecting an unrelated bonus velocity. Looking up/down retains the
   # original reach behaviour and the free two-dimensional drag.
   var forward_look := 1.0 - smoothstep(0.40, 0.85, absf(look_pitch))
   var down_ratio := clampf(down_distance / (view_height * 0.38), 0.0, 1.0)
   var original_reach := 0.65 + minf(down_distance / view_height * 3.5, 1.25)
   var ray: Vector3 = camera.project_local_ray_normal(screen_pos)
   camera_offset = ray * (original_reach / maxf(-ray.z, 0.05))
   # Keep a straight downward swipe from drifting sideways.
   camera_offset.x = lerpf(camera_offset.x, ray.x * (0.65 / maxf(-ray.z, 0.05)), forward_look)
   camera_offset.z = lerpf(camera_offset.z, -0.65 + 0.55 * down_ratio, forward_look)
  if vr_hand_enabled and vr_hand_tracked[i]: camera_offset=vr_hand_offsets[i]
  if active_character=="grinner" and monster_mode=="chainsaw" and control_mode=="simples" and not vr_hand_enabled and i==1 and chainsaw_swing_time>0.0:
   var t:=1.0-chainsaw_swing_time/.55
   camera_offset=Vector3(.30,lerpf(.48,-.62,clampf(t*1.5,0,1)),-.68-0.55*sin(PI*clampf(t,0,1)))
  var desired: Vector3 = camera.position + camera.basis * (camera_offset * character_scale())
  var stick_floor:=0.0
  if not vr_hand_enabled and control_mode=="dois_bracos" and hand_dragging[i] and is_instance_valid(ui):
   stick_floor=smoothstep(.25,.85,float(ui.arm_stick_vector[i].y))
  if (vr_hand_enabled and vr_hand_tracked[i]) or stick_floor>0.0:
   # Reach a virtual floor relative to the player's feet, but still require
   # a real collision with the level to generate the existing physical impulse.
   var virtual_floor:float=-.16*character_scale()
   desired.y=lerpf(desired.y,minf(desired.y,virtual_floor),clampf(float(vr_floor_strength[i]) if vr_hand_enabled else stick_floor,0.0,1.0))
  if vr_hand_enabled and not vr_hand_tracked[i]: desired=hand_visual[i]
  # A gripping hand stays where it was when the finger is lifted, instead
  # of springing back to its idle pose and pulling the victim unexpectedly.
  if not held[i].is_empty() and not hand_dragging[i] and not (vr_hand_enabled and vr_hand_tracked[i]): desired = hand_visual[i]
  var before: Vector3 = hand_local[i]
  if delta > 0.0:
   hand_local[i] = before.move_toward(desired, (6.0 if vr_hand_enabled else HAND_SPEED) * character_scale() * delta)
  else:
   hand_local[i] = desired
  var actual: Vector3 = hand_local[i]
  contact[i] = false
  if delta > 0.0:
   var start_world: Vector3 = player.to_global(hand_visual[i])
   var end_world: Vector3 = player.to_global(hand_local[i])
   var movement: Vector3 = player.global_basis * (hand_local[i] - before)
   # A small downward offset approximates a spherical palm. The ray checks
   # actual level geometry, not just whether the player's feet are grounded.
   var ray_from: Vector3 = start_world + Vector3.UP * HAND_RADIUS * character_scale()
   var ray_to: Vector3 = end_world - Vector3.UP * HAND_RADIUS * character_scale()
   var query := PhysicsRayQueryParameters3D.create(ray_from, ray_to)
   query.exclude = [player.get_rid()]
   var hit := get_world_3d().direct_space_state.intersect_ray(query)
   if not hit.is_empty():
    var normal: Vector3 = hit.normal
    var into := movement.dot(normal)
    # Keep the palm just outside the surface. A moving hand transfers a
    # limited opposite impulse to the body, including sideways motion.
    var resolved: Vector3 = hit.position + normal * HAND_RADIUS * character_scale()
    actual = player.to_local(resolved)
    contact[i] = true
    if (hand_dragging[i] or (vr_hand_enabled and vr_hand_can_push[i])) and (into < -0.001 or (normal.dot(Vector3.UP) > 0.5 and end_world.y < hit.position.y + HAND_RADIUS * character_scale())):
     var reaction: Vector3 = -movement / delta * 0.19
     if vr_hand_enabled or (control_mode=="dois_bracos" and stick_floor>0.0):
      # Impact is based on the incoming hand velocity, not a jump button.
      # Threshold and per-hand cooldown avoid repeated launches from jitter.
      var incoming:float=-into/maxf(delta,.001)
      if incoming<.22 or vr_push_cooldown[i]>0.0:reaction=Vector3.ZERO
      else:
       reaction=(-movement/maxf(delta,.001)*1.15).limit_length(5.0)
       vr_push_cooldown[i]=.22
     # Pulling along a surface transfers sideways motion; it should not
     # pull the player down when the hand begins to lift away.
     if into >= 0.0: reaction -= normal * reaction.dot(normal)
     if reaction.length_squared() > .000001: hand_push_applied=true
     player.velocity += reaction
     var horizontal := Vector2(player.velocity.x, player.velocity.z).limit_length(MAX_HORIZONTAL_SPEED)
     player.velocity.x = horizontal.x
     player.velocity.z = horizontal.y
     player.velocity.y = minf(player.velocity.y, MAX_UP_SPEED)
  hand_visual[i] = actual
  targets[i].position = actual
  var suffix := "L" if i == 0 else "R"
  # The arm modifier stretches the rig toward this target.
  for finger in ["index1_", "index2_", "pinkie1_", "pinkie2_", "thumb1_"]:
   var idx := skeleton.find_bone(finger + suffix)
   if idx >= 0 and base_rotation.has(idx):
    skeleton.set_bone_pose_rotation(idx, base_rotation[idx] * Quaternion(Vector3.RIGHT, 0.20 if hand_dragging[i] or not held[i].is_empty() else 0.0))
 update_grab_gestures()

func update_grab_gestures() -> void:
 # Two hands deliberately pulled outwards split a player held on both sides.
 if not held[0].is_empty() and not held[1].is_empty() and held[0].target == held[1].target and not bool(held[0].get("detached",false)) and not bool(held[1].get("detached",false)):
  var pair: String = str(held[0].target) + ":" + str(held[0].get("life",0))
  if pull_pair != pair:
   pull_pair=pair;pull_start=[hand_screen[0],hand_screen[1]];pull_start_vr=[vr_hand_offsets[0],vr_hand_offsets[1]]
  var delta0: Vector2 = hand_screen[0]-pull_start[0] if hand_dragging[0] else Vector2.ZERO
  var delta1: Vector2 = hand_screen[1]-pull_start[1] if hand_dragging[1] else Vector2.ZERO
  var off0:Vector3=held[0].offset;var off1:Vector3=held[1].offset
  var opposite_sides:=off0.x*off1.x < -.005 and absf(off0.x-off1.x)>.18 and off0.y>.4 and off1.y>.4 and off0.y<1.35 and off1.y<1.35
  var deliberate:=false
  if vr_hand_enabled and vr_hand_tracked[0] and vr_hand_tracked[1]:
   var dl:float=vr_hand_offsets[0].x-pull_start_vr[0].x
   var dr:float=vr_hand_offsets[1].x-pull_start_vr[1].x
   deliberate=dl<-.12 and dr>.12 and dr-dl>.45
  elif hand_dragging[0] and hand_dragging[1]:
   var w:=get_viewport().get_visible_rect().size.x
   deliberate=delta0.x < -w*.07 and delta1.x > w*.07 and delta1.x-delta0.x>w*.24
  if opposite_sides and deliberate:
   split_held();pull_pair=""
  elif (not opposite_sides or absf(delta1.y-delta0.y)>absf(delta1.x-delta0.x)) and (delta1-delta0).length() > get_viewport().get_visible_rect().size.y * .32:
   var tear_hand := 1 if str(held[1].get("part","body")) != "body" else 0
   if str(held[tear_hand].get("part","body")) != "body": tear_held(tear_hand)
   pull_pair=""
 else: pull_pair=""
 # If both hands hold the same player, keep both contact points on the
 # same rigid body. The actively dragged hand drives the other hand.
 if not held[0].is_empty() and not held[1].is_empty() and held[0].target == held[1].target and not bool(held[0].get("detached",false)) and not bool(held[1].get("detached",false)) and network.peers.has(held[0].target):
  var driver := 1 if hand_dragging[1] and not hand_dragging[0] else 0
  var follower := 1 - driver
  var victim = network.peers[held[0].target]
  var basis := Basis(Vector3.UP,victim.rotation.y)
  var victim_origin: Vector3 = targets[driver].global_position - basis * held[driver].offset
  var constrained: Vector3 = victim_origin + basis * held[follower].offset
  hand_visual[follower] = player.to_local(constrained)
  hand_local[follower] = hand_visual[follower]
  targets[follower].global_position = constrained
 have_previous_palm = true

func grab_hand(i: int, finger_position: Vector2) -> void:
 if is_dead or (active_character == "normal" and (missing_parts & (2 if i==0 else 4)) != 0): return
 hand_dragging[i] = true
 finger_start[i] = finger_position
 hand_start_screen[i] = camera.unproject_position(targets[i].global_position)
 hand_screen[i] = hand_start_screen[i]
 if pull_pair != "": pull_start[i] = hand_screen[i]

func drag_hand(i: int, finger_position: Vector2) -> void:
 hand_screen[i] = hand_start_screen[i] + finger_position - finger_start[i]

func release_hand(i: int) -> void:
 hand_dragging[i] = false

func look(delta: Vector2) -> void:
 player.rotate_y(-delta.x * look_sensitivity)
 look_pitch = clampf(look_pitch - delta.y * look_sensitivity, -1.50, 1.50)
 camera.rotation.x = look_pitch

func clear_grabs() -> void:
 for i in 2:
  if not held[i].is_empty(): wait_for_separation[i] = true
 held = [{}, {}]
 for i in 2:
  if is_instance_valid(detached_visuals[i]): detached_visuals[i].queue_free();detached_visuals[i]=null
 release_cooldown = 0.25
 # Remove the local visual constraint immediately, not after a network tick.
 if is_instance_valid(network):
  for other in network.peers.values():
   if is_instance_valid(other): other.grab_visual_active = false

func serialize_grabs() -> Array:
 var output := []
 for i in 2:
  if held[i].is_empty(): continue
  var off: Vector3 = held[i].offset
  var goal: Vector3 = held[i].get("piece_goal",Vector3.ZERO)
  output.append({"hand":i,"target":held[i].target,"offset":[off.x,off.y,off.z],"part":held[i].get("part","body"),"detached":held[i].get("detached",false),"victim_yaw":held[i].get("victim_yaw",0.0),"color":held[i].get("color",0),"life":held[i].get("life",0),"piece_goal":[goal.x,goal.y,goal.z]})
 return output

func body_part_at(other: Node3D, point: Vector3) -> String:
 var local: Vector3 = other.to_local(point)
 if local.distance_to(Vector3(0,1.32,-.04)) < .28 and not (other.missing_parts & 1): return "head"
 for arm in 2:
  var bit := 2 if arm==0 else 4
  if (other.missing_parts & bit) != 0: continue
  var shoulder := Vector3(-.30 if arm==0 else .30,1.05,0)
  var finish: Vector3 = other.to_local(other.goals[arm].global_position)
  var line: Vector3 = finish-shoulder
  var t := clampf((local-shoulder).dot(line)/maxf(line.length_squared(),.0001),0,1)
  if local.distance_to(shoulder+line*t)<.23: return "arm_l" if arm==0 else "arm_r"
 return "body"

func touching_normal(other: Node3D, point: Vector3) -> bool:
 var local: Vector3 = other.to_local(point)
 # Approximate the normal avatar as head/torso plus its two arms.
 var nearest := Vector3(0,clampf(local.y,0.30,1.36),0)
 if local.distance_to(nearest) < 0.38: return true
 for arm in 2:
  if (other.missing_parts & (2 if arm==0 else 4)) != 0: continue
  var shoulder := Vector3(-0.30 if arm == 0 else 0.30, 1.05, 0.0)
  var arm_end: Vector3 = other.to_local(other.goals[arm].global_position)
  var line: Vector3 = arm_end - shoulder
  var u := clampf((local - shoulder).dot(line) / maxf(line.length_squared(),0.0001),0.0,1.0)
  if local.distance_to(shoulder + line*u) < 0.20: return true
 return false

# Returns the closest anatomical hit region for a point on the moving chain.
func chainsaw_contact(other:Node3D,point:Vector3)->Dictionary:
 var local:Vector3=other.to_local(point)
 var best:=1.0;var part:=""
 if is_instance_valid(other.ragdoll_visual) and other.ragdoll_visual.bodies.size()>=4:
  var parts=other.ragdoll_visual.bodies
  for i in 4:
   if i>0 and (other.missing_parts & (1 if i==1 else (2 if i==2 else 4)))!=0:continue
   var p:Vector3=parts[i].to_local(point)
   var half:=.22 if i==0 else (.05 if i==1 else .17)
   var radius:=.30 if i==0 else (.24 if i==1 else .16)
   var score:=p.distance_to(Vector3(0,clampf(p.y,-half,half),0))/radius
   if score<best:best=score;part=["body","head","arm_l","arm_r"][i]
  return {"part":part,"score":best}
 var torso:=local.distance_to(Vector3(0,clampf(local.y,.30,1.10),0))/.38
 if torso<best:best=torso;part="body"
 if (other.missing_parts & 1)==0:
  var head:=local.distance_to(Vector3(0,1.32,-.04))/.30
  if head<best:best=head;part="head"
 for arm in 2:
  if (other.missing_parts & (2 if arm==0 else 4))!=0:continue
  var shoulder:=Vector3(-.30 if arm==0 else .30,1.05,0)
  var end:Vector3=other.to_local(other.goals[arm].global_position)
  var line:=end-shoulder
  var u:=clampf((local-shoulder).dot(line)/maxf(line.length_squared(),.0001),0,1)
  var score:=local.distance_to(shoulder+line*u)/.23
  if score<best:best=score;part="arm_l" if arm==0 else "arm_r"
 return {"part":part,"score":best}

func check_grabs(delta: float) -> void:
 release_cooldown = maxf(0.0,release_cooldown-delta)
 if (network.state != "joined" and selected_map!="sandbox") or active_character != "grinner" or monster_mode!="grab":
  if not held[0].is_empty() or not held[1].is_empty(): clear_grabs()
  return
 for i in 2:
  if not held[i].is_empty():
   var id: String = held[i].target
   if not network.peers.has(id) or network.peers[id].character != "normal": held[i] = {}
   elif not bool(held[i].get("detached",false)) and (network.peers[id].is_dead or network.peers[id].life_number != int(held[i].get("life",0))): held[i] = {}
   continue
  if vr_hand_enabled and not vr_hand_tracked[i]:continue
  var palm: Vector3 = targets[i].global_position
  var overlap := false
  for other in network.peers.values():
   if other.character == "normal" and other.has_state and not other.is_dead and touching_normal(other,palm): overlap = true; break
  if wait_for_separation[i]:
   if not overlap: wait_for_separation[i] = false
   continue
  if release_cooldown > 0.0 or (control_mode=="simples" and not simple_grab and not vr_hand_enabled): continue
  # Sweep from the previous palm to prevent a fast hand crossing a player
  # between physics frames without detecting contact.
  var start: Vector3 = previous_palm[i] if have_previous_palm else palm
  var steps := clampi(ceili(start.distance_to(palm)/0.06),1,32)
  var found := false
  for step in range(steps+1):
   var point: Vector3 = start.lerp(palm,float(step)/steps)
   for id in network.peers:
    var other = network.peers[id]
    if other.character != "normal" or not other.has_state or other.is_dead: continue
    if touching_normal(other,point):
     # Keep the exact point of contact in the victim's local coordinates.
     var local: Vector3 = other.to_local(point)
     if local.length() > 2.0: continue
     held[i] = {"target":id,"offset":local,"part":body_part_at(other,point),"life":other.life_number,"victim_yaw":other.rotation.y,"detached":false}
     hand_visual[i] = player.to_local(point)
     hand_local[i] = hand_visual[i]
     targets[i].global_position = point
     found = true
     break
   if found: break
 update_held_visuals()
 update_detached_visuals()

func update_held_visuals() -> void:
 # Predict the held player's location on the monster's own screen immediately,
 # so network latency doesn't visually make the hand slide off the body.
 for id in network.peers:
  var other = network.peers[id]
  if not is_instance_valid(other): continue
  other.grab_visual_active = false
  if active_character != "grinner" or other.character != "normal": continue
  var positions: Array[Vector3] = []
  for i in 2:
   if not held[i].is_empty() and held[i].target == id and not bool(held[i].get("detached",false)):
    var off: Vector3 = held[i].offset
    positions.append(targets[i].global_position - Basis(Vector3.UP,other.rotation.y) * off)
  if not positions.is_empty():
   var target := Vector3.ZERO
   for pos in positions: target += pos
   other.grab_visual_position = target / positions.size()
   other.grab_visual_active = true

func follow_monster(delta: float) -> bool:
 if network.state != "joined" or active_character != "normal": return false
 var destinations: Array[Vector3] = []
 for id in network.peers:
  var monster = network.peers[id]
  if monster.character != "grinner" or not monster.has_state: continue
  for entry in monster.grabs:
   if not entry is Dictionary or str(entry.get("target","")) != network.uid or bool(entry.get("detached",false)) or int(entry.get("life",0)) != life_number: continue
   var hand = entry.get("hand",-1)
   var offset = network._vector(entry.get("offset"))
   if typeof(hand) not in [TYPE_INT,TYPE_FLOAT] or int(hand) < 0 or int(hand)>1 or offset == null: continue
   if offset.length()>2.0: continue
   var palm: Vector3 = monster.goals[int(hand)].global_position
   var destination: Vector3 = palm - Basis(Vector3.UP,player.rotation.y) * offset
   if destination.distance_to(player.global_position) > 30.0: continue
   destinations.append(destination)
  if not destinations.is_empty(): break
 if destinations.is_empty():
  held_age=0.0;held_destination_valid=false
  if throw_pending:
   player.velocity=pending_throw
   throw_pending=false
  return false
 held_age += delta
 var target := Vector3.ZERO
 for dest in destinations: target += dest
 target /= destinations.size()
 var target_delta:=target-held_last_destination if held_destination_valid else Vector3.ZERO
 held_last_destination=target;held_destination_valid=true
 # Only actual incoming hand movement counts, never accumulated penetration.
 var incoming_velocity:=target_delta/maxf(delta,.001) if target_delta.length()<.55 else Vector3.ZERO
 var movement := target - player.global_position
 player.velocity = Vector3.ZERO
 # Exact attachment to the latest interpolated hand. The body still respects
 # level collision, rather than passing through solid walls.
 var hit := player.move_and_collide(movement)
 if hit and held_age > .35 and -incoming_velocity.dot(hit.get_normal()) > 11.0:
  die("IMPACTO FORTE")
 return true

func _physics_process(delta: float) -> void:
 if is_instance_valid(local_chat_bubble) and is_instance_valid(player):
  local_chat_bubble.global_position=player.global_position+Vector3(0,3.05 if active_character=="grinner" else 2.75,0)
  if not game_running:local_chat_bubble.visible=false
 if player == null or not game_running: return
 if is_instance_valid(g2_xr) and g2_xr.immersive and g2_xr.menu_open: return
 if is_dead:
  if is_instance_valid(ragdoll) and ragdoll.bodies.size()>0:player.global_position=ragdoll.bodies[0].global_position-Vector3(0,.78,0)
  dead_timer -= delta
  if dead_timer <= 0.0:
   if network.state=="joined" and round_phase!="waiting":
    dead_timer=0.0
    apply_pending_round.call_deferred()
   else:respawn_player()
  return
 if is_instance_valid(ragdoll):
  ragdoll.time_left-=delta
  if ragdoll.bodies.size()>0:player.global_position=ragdoll.bodies[0].global_position-Vector3(0,.78,0)
  player.velocity=Vector3.ZERO
  if ragdoll.time_left<=0.0:end_ragdoll()
  return
 chainsaw_swing_time=maxf(0.0,chainsaw_swing_time-delta)
 hand_push_applied=false
 var body_before:=player.global_position
 update_hands(delta)
 update_chainsaw(delta)
 check_grabs(delta)
 if follow_monster(delta): return
 if not player.is_on_floor() and not (g2_xr.immersive and g2_xr.motion.supported): player.velocity.y -= (preload("res://g2_motion.gd").GRAVITY if g2_xr.immersive else 9.8) * delta
 if control_mode=="simples" and not vr_hand_enabled:
  var wish:Vector3=player.global_basis*Vector3(move_input.x,0,move_input.y)
  player.velocity.x=move_toward(player.velocity.x,wish.x*4.0,18.0*delta)
  player.velocity.z=move_toward(player.velocity.z,wish.z*4.0,18.0*delta)
 elif player.is_on_floor() and not hand_push_applied and player.velocity.y <= 0.0:
  # Apply directional friction after landing; leave fresh pushes and jumps intact.
  var horizontal:=Vector2(player.velocity.x,player.velocity.z).move_toward(Vector2.ZERO,GROUND_FRICTION*delta)
  player.velocity.x=horizontal.x
  player.velocity.z=horizontal.y
 player.move_and_slide()
 if g2_xr.immersive: g2_xr.after_body(player.global_position-body_before,delta)
 if active_character == "grinner":
  update_held_visuals()
  update_detached_visuals()
  for i in 2:
   var current: Vector3 = targets[i].global_position
   hand_velocity[i] = ((current-last_hand_world[i])/maxf(delta,.001)).limit_length(18.0) if hand_velocity_valid else Vector3.ZERO
   if hand_velocity[i].length() > 1.25:
    last_moving_velocity[i] = hand_velocity[i]
    last_moving_usec[i] = Time.get_ticks_usec()
   last_hand_world[i]=current
  hand_velocity_valid=true
 if player.position.y < -10:
  player.position = Vector3(0,0.04,2)
  player.velocity = Vector3.ZERO

func choose_monster_mode(mode:String)->void:
 if active_character!="grinner" or monster_mode!="choose" or mode not in ["grab","chainsaw"]:return
 monster_mode=mode;clear_grabs();remove_chainsaw()
 if mode=="chainsaw":
  chainsaw=preload("res://chainsaw_visual.gd").new();chainsaw.name="Motosserra";targets[1].add_child(chainsaw)
 else:remove_chainsaw()
 if is_instance_valid(ui):ui.update_monster_choice()
 round_notice="MOTOSSERRA • MOVA A MÃO DIREITA" if mode=="chainsaw" else "MÃOS • AGARRE OS GILLAS"
 round_notice_until=Time.get_ticks_msec()+4500
 var v=get_node_or_null("VRBox")
 if is_instance_valid(v):v.finish_monster_choice()

func remove_chainsaw()->void:
 if is_instance_valid(chainsaw):chainsaw.queue_free()
 chainsaw=null;chainsaw_previous_valid=false;chainsaw_swing_time=0.0;chainsaw_contacts.clear()

func swing_chainsaw()->void:
 if active_character=="grinner" and monster_mode=="chainsaw" and chainsaw_swing_time<=0.0:chainsaw_swing_time=.55

func update_chainsaw(delta:float)->void:
 if active_character!="grinner" or monster_mode!="chainsaw" or not is_instance_valid(chainsaw) or (network.state!="joined" and selected_map!="sandbox"):
  chainsaw_previous_valid=false;chainsaw_contacts.clear();return
 var now:Array[Vector3]=[]
 for k in 9:now.append(chainsaw.blade_position(float(k)/8.0))
 if not chainsaw_previous_valid:chainsaw_previous=now.duplicate();chainsaw_previous_valid=true
 var hit_ids:={}
 for id in network.peers:
  var victim=network.peers[id]
  if victim.character!="normal" or victim.is_dead or not victim.has_state:continue
  var point:=Vector3.ZERO;var found:=false;var best_score:=1.0;var hit_part:=""
  for k in 9:
   # Sweep the blade between frames, including stationary contact.
   var move:Vector3=now[k]-chainsaw_previous[k]
   if move.length()>2.0:continue
   var steps:=clampi(ceili(move.length()/.08),1,12)
   for step in range(steps+1):
    var sample:Vector3=chainsaw_previous[k].lerp(now[k],float(step)/steps)
    var contact_data:=chainsaw_contact(victim,sample)
    if str(contact_data.part)!="" and float(contact_data.score)<best_score:
     point=sample;found=true;best_score=float(contact_data.score);hit_part=str(contact_data.part)
  if not found:continue
  hit_ids[id]=true
  var part:=hit_part
  var e:Dictionary=chainsaw_contacts.get(id,{})
  if e.is_empty() or str(e.get("part",""))!=part or int(e.get("life",-1))!=victim.life_number:
   e={"part":part,"life":victim.life_number,"time":0.0,"send":0.0,"final":false}
  e.time=float(e.time)+maxf(delta,0.0);e.send=float(e.send)-maxf(delta,0.0)
  var threshold:=.38 if part in ["arm_l","arm_r","head"] else .60
  var final:bool=float(e.time)>=threshold
  if float(e.send)<=0.0:
   e.send=.18
   network.send_chainsaw_hit(str(id),part,point,victim.life_number,final)
   if victim.has_method("sandbox_chainsaw"):victim.sandbox_chainsaw(part,point,final)
   preload("res://injury_visual.gd").saw_blood(self,point)
   if final and not bool(e.final):e.final=true
   chainsaw_contacts[id]=e
  else:chainsaw_contacts[id]=e
 for id in chainsaw_contacts.keys():
  if not hit_ids.has(id):chainsaw_contacts.erase(id)
 chainsaw_previous=now

func receive_chainsaw_hit(part:String,point:Vector3,life:int,final:bool)->void:
 if active_character!="normal" or is_dead or life!=life_number:return
 if not is_instance_valid(ragdoll):
  var direction:Vector3=(player.global_position+Vector3(0,.8,0)-point).normalized()
  start_ragdoll(direction*.7+Vector3.UP*.3)
 if not final or not is_instance_valid(ragdoll):return
 if part in ["arm_l","arm_r","head"]:
  ragdoll.sever_part(part);receive_tear(part,life,true)
 else:
  var local:Vector3=ragdoll.bodies[0].to_local(point)+Vector3(0,.78,0)
  ragdoll.cut_torso(local.y)
  die("CORTADO PELA MOTOSSERRA",false,true)

func start_ragdoll(impulse:Vector3)->void:
 if is_dead or active_character!="normal":return
 if is_instance_valid(ragdoll):
  ragdoll.time_left=7.0
  if ragdoll.bodies.size()>0:ragdoll.bodies[0].linear_velocity+=impulse*.45
  return
 clear_grabs();hand_dragging=[false,false];player.velocity=Vector3.ZERO
 ragdoll=preload("res://ragdoll.gd").new();ragdoll.game=self;ragdoll.name="RagdollLocal";add_child(ragdoll)
 ragdoll.setup(player.global_position,player.rotation.y,missing_parts,cosmetic_color,impulse,true)
 var model=player.get_node_or_null("Modelo")
 if model:model.visible=false
 var reflection=player.get_node_or_null("PersonagemNoEspelho")
 if reflection:reflection.visible=false
 if is_instance_valid(ui):ui.reset_inputs()

func end_ragdoll()->void:
 if not is_instance_valid(ragdoll):ragdoll=null;return
 if is_instance_valid(player) and ragdoll.bodies.size()>0:
  var torso:Vector3=ragdoll.bodies[0].global_position
  var recovered:Vector3=torso-Vector3(0,.78,0)
  # A prone torso is lower than a standing capsule. Recover above the floor,
  # not inside it; preserve freefall when there is no nearby ground.
  if is_inside_tree():
   var query:=PhysicsRayQueryParameters3D.create(torso+Vector3.UP*.5,torso-Vector3.UP*2.0)
   query.exclude=[player.get_rid()]
   var hit:=get_world_3d().direct_space_state.intersect_ray(query)
   if not hit.is_empty() and hit.normal.y>.5:recovered.y=maxf(recovered.y,hit.position.y+.04)
  player.global_position=recovered
  player.velocity=Vector3.ZERO
 var model=player.get_node_or_null("Modelo") if is_instance_valid(player) else null
 if model:model.visible=true
 var reflection=player.get_node_or_null("PersonagemNoEspelho") if is_instance_valid(player) else null
 if reflection:reflection.visible=true
 ragdoll.queue_free();ragdoll=null

func release_grabs() -> void:
 var sent := {}
 for i in 2:
  if held[i].is_empty(): continue
  if bool(held[i].get("detached",false)):
   if is_instance_valid(detached_visuals[i]):
    var speed: Vector3 = hand_velocity[i]
    if speed.length()<1.25 and Time.get_ticks_usec()-int(last_moving_usec[i])<400000: speed=last_moving_velocity[i]
    preload("res://injury_visual.gd").drop_piece(self,detached_visuals[i],held[i].offset,speed)
    detached_visuals[i]=null
   continue
  var id: String = held[i].target
  if not sent.has(id):
   sent[id]=true
   var velocity: Vector3 = hand_velocity[i]
   if velocity.length() < 1.25 and Time.get_ticks_usec()-int(last_moving_usec[i]) < 400000:
    velocity=last_moving_velocity[i]
   network.send_throw(id,velocity,int(held[i].get("life",0)))
   if network.peers.has(id) and network.peers[id].has_method("sandbox_throw"):network.peers[id].sandbox_throw(velocity)
 clear_grabs()

func receive_throw(velocity: Vector3) -> void:
 if is_dead or active_character != "normal": return
 pending_throw=velocity.limit_length(18.0)
 throw_pending=true

func tear_held(i: int) -> void:
 if i<0 or i>1 or held[i].is_empty() or bool(held[i].get("detached",false)): return
 var part := str(held[i].get("part","body"))
 if part not in ["head","arm_l","arm_r"]: return
 var id: String = held[i].target
 if not network.peers.has(id): return
 var other = network.peers[id]
 var bit: int = preload("res://injury_visual.gd").mask_for(part)
 if (other.missing_parts & bit) != 0: return
 held[i].detached=true
 held[i].color=clampi(other.cosmetic_color,0,9)
 held[i].victim_yaw=other.rotation.y
 if part in ["arm_l","arm_r"]:
  var arm_index := 0 if part=="arm_l" else 1
  held[i].piece_goal=other.to_local(other.goals[arm_index].global_position)
 network.send_tear(id,part,int(held[i].get("life",0)))
 other.set_injury(other.missing_parts | bit,part=="head",other.life_number)
 var base:Vector3=other.global_position+Basis(Vector3.UP,other.rotation.y)*Vector3(0,1.2,0)
 var tip:Vector3=targets[i].global_position
 preload("res://injury_visual.gd").strands(self,base,tip)
 network.send_strands(base,tip)
 preload("res://injury_visual.gd").blood(self,tip)
 # A second hand holding the same severed part lets go of that part.
 var j:=1-i
 if not held[j].is_empty() and held[j].target==id and str(held[j].get("part","body"))==part:
  held[j]={};wait_for_separation[j]=true
 update_detached_visuals()

func split_held()->void:
 if monster_mode!="grab" or held[0].is_empty() or held[1].is_empty():return
 var id:=str(held[0].target)
 if id!=str(held[1].target) or not network.peers.has(id):return
 var other=network.peers[id]
 if other.character!="normal" or other.is_dead or other.life_number!=int(held[0].get("life",-1)):return
 var where:Vector3=other.global_position
 var yaw:float=other.rotation.y
 var color:int=clampi(other.cosmetic_color,0,9)
 var life:int=other.life_number
 preload("res://split_visual.gd").spawn(self,where,yaw,color)
 other.split_life=life;other.avatar.visible=false
 network.send_split(id,life,where,yaw,color)
 other.is_dead=true
 clear_grabs()

func receive_split(life:int)->void:
 if active_character!="normal" or is_dead or life!=life_number:return
 die("PARTIDO AO MEIO",false)

func inspect_grab_commands(commands: Array) -> void:
 if is_dead: return
 for entry in commands:
  if entry is Dictionary and str(entry.get("target",""))==network.uid and bool(entry.get("detached",false)):
   receive_tear(str(entry.get("part","")),int(entry.get("life",-1)))

func receive_tear(part: String, life: int, keep_physics := false) -> void:
 if active_character!="normal" or is_dead or life!=life_number: return
 var injury=preload("res://injury_visual.gd")
 var bit: int=injury.mask_for(part)
 if bit==0 or (missing_parts & bit)!=0: return
 missing_parts |= bit
 apply_injuries()
 var where: Vector3 = player.global_position + Vector3(0,1.2,0) if part=="head" else player.global_position + Vector3(-.23 if part=="arm_l" else .23,1.05,0)
 injury.blood(self,where)
 var end:Vector3=where+Basis(Vector3.UP,player.rotation.y)*Vector3(-.42 if part=="arm_l" else (.42 if part=="arm_r" else 0),.27 if part=="head" else .02,0)
 injury.strands(self,where,end);network.send_strands(where,end)
 network.send_blood(where)
 if part=="head": die("CABEÇA ARRANCADA",false,keep_physics)

func apply_injuries() -> void:
 if not is_instance_valid(player): return
 var injury=preload("res://injury_visual.gd")
 injury.update_rig(skeleton,missing_parts,true)
 var mirror_rig=player.get_node_or_null("PersonagemNoEspelho/Gilla_Rig/Skeleton3D")
 if mirror_rig: injury.update_rig(mirror_rig,missing_parts)
 injury.update_wounds(player,missing_parts,2|4)
 var accessory=player.get_node_or_null("AcessorioCosmetico")
 if accessory:accessory.visible=(missing_parts & 1)==0
 for i in 2:
  if (missing_parts & (2 if i==0 else 4)) != 0: hand_dragging[i]=false

func die(reason: String, blood_effect := true, keep_physics := false) -> void:
 if is_dead or active_character!="normal": return
 if not keep_physics:end_ragdoll()
 is_dead=true;dead_timer=2.0 if network.state=="joined" else 5.0;player.velocity=Vector3.ZERO
 hand_dragging=[false,false]
 if blood_effect:
  var point: Vector3=player.global_position+Vector3(0,1.0,0)
  preload("res://injury_visual.gd").blood(self,point)
  network.send_blood(point)
 if is_instance_valid(ui): ui.death_reason=reason

func reset_health() -> void:
 end_ragdoll()
 is_dead=false;dead_timer=0.0;missing_parts=0;held_age=0.0;held_destination_valid=false;throw_pending=false
 if is_instance_valid(player): apply_injuries()

func respawn_player() -> void:
 life_number+=1
 reset_health()
 player.global_position=Vector3(0,.04,2);player.velocity=Vector3.ZERO

func update_detached_visuals() -> void:
 for i in 2:
  if held[i].is_empty() or not bool(held[i].get("detached",false)):
   if is_instance_valid(detached_visuals[i]): detached_visuals[i].queue_free();detached_visuals[i]=null
   continue
  if not is_instance_valid(detached_visuals[i]):
   detached_visuals[i]=preload("res://injury_visual.gd").make_piece(str(held[i].part),int(held[i].get("color",0)))
   if detached_visuals[i]==null: continue
   add_child(detached_visuals[i])
   preload("res://injury_visual.gd").pose_piece(detached_visuals[i],str(held[i].part),held[i].get("piece_goal",Vector3.ZERO))
  var yaw: float=float(held[i].victim_yaw)
  var off: Vector3=held[i].offset
  detached_visuals[i].global_position=targets[i].global_position-Basis(Vector3.UP,yaw)*off
  detached_visuals[i].rotation.y=yaw

func _unhandled_input(event: InputEvent) -> void:
 if not game_running: return
 if event is InputEventKey and event.pressed:
  if event.keycode == KEY_R:
   player.position = Vector3(0,0.04,2)
   player.velocity = Vector3.ZERO
