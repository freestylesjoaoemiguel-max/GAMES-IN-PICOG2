extends Node3D
var character := ""
var avatar: Node3D
var rig: Skeleton3D
var goals: Array[Node3D] = []
var desired_position := Vector3.ZERO
var desired_yaw := 0.0
var desired_left := Vector3.ZERO
var desired_right := Vector3.ZERO
var has_state := false
var grab_visual_active := false
var grab_visual_position := Vector3.ZERO
var grabs: Array = []
var face_level := 0.0
var face: MeshInstance3D
var voice_player: AudioStreamPlayer
var voice_playback: AudioStreamGeneratorPlayback
var voice_last_seq := -1
var voice_clock_offset := 0
var voice_clock_ready := false
var voice_clock_update := 0
var voice_last_received := 0
var voice_pending:Array[PackedByteArray]=[]
var voice_queue_started:=0
var voice_buffering:=true
var chat_bubble:Node3D
var missing_parts := 0
var is_dead := false
var life_number := 0
var detached_visuals := [null,null]
var detached_offsets := [Vector3.ZERO,Vector3.ZERO]
var cosmetic_color := -1
var cosmetic_accessory := -1
var nickname := "Jogador"
var name_label: Label3D
var vr_label: Label3D
var pico_vr:=false
var hand_rotations:=[Quaternion.IDENTITY,Quaternion.IDENTITY]
var rotation_valid:=[false,false]
var arm_modifiers:Array[SkeletonModifier3D]=[]
var monster_mode:="grab"
var split_life:=-1
var chainsaw_visual:Node3D
var ragdoll_visual:Node3D

func set_nickname(value:Variant) -> void:
 nickname=preload("res://profile_name.gd").clean(value)
 if is_instance_valid(name_label):name_label.text=nickname

func set_xr_state(packet:Dictionary) -> void:
 var flag=packet.get("vr_pico_g2",false)
 pico_vr=typeof(flag)==TYPE_BOOL and flag
 var mask=packet.get("xr_hands",0)
 if typeof(mask) not in [TYPE_INT,TYPE_FLOAT] or not is_finite(float(mask)):mask=0
 mask=clampi(int(mask),0,3)
 for i in 2:
  var q=preload("res://g2_presence.gd").decode_rotation(packet.get("left_rot" if i==0 else "right_rot"))
  rotation_valid[i]=pico_vr and (mask & (1<<i))!=0 and q!=null
  if rotation_valid[i]:hand_rotations[i]=q
  if i<arm_modifiers.size():
   if rotation_valid[i]:arm_modifiers[i].set_meta("g2_orientation",true)
   elif arm_modifiers[i].has_meta("g2_orientation"):arm_modifiers[i].remove_meta("g2_orientation")
 _refresh_identity()

func _refresh_identity() -> void:
 if is_instance_valid(name_label):
  name_label.modulate=Color("00ff00") if pico_vr else Color.WHITE
 if is_instance_valid(vr_label):
  vr_label.visible=pico_vr
  vr_label.position=Vector3(0,(2.45 if character=="grinner" else 2.15)+.29,0)

func set_cosmetics(color: int, accessory: int) -> void:
 if character!="normal": return
 color=clampi(color,0,9);accessory=clampi(accessory,0,preload("res://cosmetics.gd").NAMES.size()-1)
 if cosmetic_color != color:
  cosmetic_color=color;preload("res://cosmetics.gd").paint(face,color)
 if cosmetic_accessory != accessory:
  cosmetic_accessory=accessory;preload("res://cosmetics.gd").apply_accessory(self,accessory,1)


func play_voice(data: PackedByteArray, sequence:int = -1, sent_ms:int = -1) -> void:
 if data.size()!=800 or sequence<0 or sent_ms<0:return
 if sequence<=voice_last_seq:return
 var now:=Time.get_ticks_msec()
 # Compare relative clocks rather than wall clocks (phones need not be synchronized).
 var offset:=now-sent_ms
 if not voice_clock_ready:
  voice_clock_offset=offset;voice_clock_ready=true;voice_clock_update=now
 else:
  # Follow gradual clock drift / a sustained network delay, but not a burst of stale packets.
  var elapsed:=clampi(now-voice_clock_update,0,100)
  voice_clock_offset=mini(voice_clock_offset+elapsed/20,offset)
  voice_clock_update=now
 voice_last_seq=sequence
 if offset-voice_clock_offset>500:return
 voice_last_received=now
 # Only audible, non-silent remote speech arms the speaker echo guard.
 var owner_game:=get_parent()
 if is_instance_valid(owner_game) and is_instance_valid(owner_game.get("player")) and is_instance_valid(owner_game.get("voice_face")):
  var distance:float=global_position.distance_to(owner_game.player.global_position)
  var gain:=1.0-smoothstep(3.0,20.0,distance)
  if gain>.05:
   var energy:=0.0
   for i in range(0,data.size(),8):
    var sample:float=preload("res://voice_codec.gd").decode_sample(data[i])
    energy+=sample*sample
   if sqrt(energy/100.0)*gain>.01:owner_game.voice_face.note_incoming_voice()
 if voice_pending.is_empty():voice_queue_started=now
 voice_pending.append(data)
 # The newest audio wins. Never grow a backlog on slow devices.
 while voice_pending.size()>3:voice_pending.pop_front()

func _pump_voice()->void:
 if voice_pending.is_empty():
  if Time.get_ticks_msec()-voice_last_received>250:voice_buffering=true
  return
 if voice_buffering and voice_pending.size()<2 and Time.get_ticks_msec()-voice_queue_started<120:return
 voice_buffering=false
 if not is_instance_valid(voice_player):
  voice_player=AudioStreamPlayer.new()
  var stream:=AudioStreamGenerator.new()
  stream.mix_rate_mode=AudioStreamGenerator.MIX_RATE_CUSTOM;stream.mix_rate=16000.0;stream.buffer_length=.25
  voice_player.stream=stream;add_child(voice_player);voice_player.play()
  voice_playback=voice_player.get_stream_playback() as AudioStreamGeneratorPlayback
 if voice_playback==null:return
 # Apply volume based on real player positions, not camera zoom or screen position.
 var owner_game:=get_parent()
 if is_instance_valid(owner_game) and is_instance_valid(owner_game.get("player")):
  var distance:float=global_position.distance_to(owner_game.player.global_position)
  var gain:=1.0-smoothstep(3.0,20.0,distance)
  voice_player.volume_db=linear_to_db(maxf(.0001,gain))
 if Time.get_ticks_msec()-voice_last_received>500:
  voice_pending.clear();voice_buffering=true;return
 while not voice_pending.is_empty():
  # If playback is full, wait for the next frame instead of cutting a chunk.
  # The incoming queue itself remains capped at three packets.
  if voice_playback.get_frames_available()<800:break
  var data:PackedByteArray=voice_pending.pop_front()
  var frames:=PackedVector2Array();frames.resize(800)
  for i in 800:
   var v:=preload("res://voice_codec.gd").decode_sample(data[i])
   frames[i]=Vector2(v,v)
  voice_playback.push_buffer(frames)

func show_chat(message:String)->void:
 if not is_instance_valid(chat_bubble):
  chat_bubble=preload("res://chat_bubble.gd").new();add_child(chat_bubble)
 chat_bubble.position=Vector3(0,3.05 if character=="grinner" else 2.75,0)
 chat_bubble.say(message)

func configure(kind: String) -> void:
 if is_instance_valid(ragdoll_visual):ragdoll_visual.queue_free();ragdoll_visual=null
 if is_instance_valid(chainsaw_visual):chainsaw_visual.queue_free();chainsaw_visual=null
 monster_mode="grab"
 split_life=-1;missing_parts=0;is_dead=false;grab_visual_active=false
 character = kind
 if not is_instance_valid(name_label):
  name_label=Label3D.new();name_label.name="NomeDoJogador"
  name_label.billboard=BaseMaterial3D.BILLBOARD_ENABLED
  name_label.font_size=40;name_label.pixel_size=.005
  name_label.modulate=Color.WHITE;name_label.outline_modulate=Color(0,0,0,.94);name_label.outline_size=10
  name_label.text=nickname;add_child(name_label)
 name_label.position=Vector3(0,2.45 if kind=="grinner" else 2.15,0)
 if not is_instance_valid(vr_label):
  vr_label=Label3D.new();vr_label.name="EtiquetaPicoVR";vr_label.text="[VR PICO G2]"
  vr_label.billboard=BaseMaterial3D.BILLBOARD_ENABLED
  vr_label.font_size=38;vr_label.pixel_size=.005
  vr_label.modulate=Color("ff0000");vr_label.outline_modulate=Color(0,0,0,.94);vr_label.outline_size=10
  add_child(vr_label)
 _refresh_identity()
 arm_modifiers.clear()
 cosmetic_color=-1;cosmetic_accessory=-1
 var old_cosmetic=get_node_or_null("AcessorioCosmetico")
 if old_cosmetic:old_cosmetic.free()
 if is_instance_valid(avatar): avatar.queue_free()
 for g in goals:
  if is_instance_valid(g): g.queue_free()
 goals.clear()
 avatar = preload("res://monster_model.gd").instantiate() if kind=="grinner" else load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D
 avatar.rotation.y = PI
 avatar.scale = Vector3.ONE * (2.175 if kind == "grinner" else 1.45)
 add_child(avatar)
 rig = avatar.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 face = rig.get_node("Gilla") as MeshInstance3D
 face.layers = 1
 for side in ["L","R"]:
  var goal := Marker3D.new(); add_child(goal); goals.append(goal)
  var mod: SkeletonModifier3D = preload("res://arm_modifier.gd").new()
  mod.player = self; mod.target = goal; mod.suffix = side; mod.side = -1.0 if side == "L" else 1.0
  rig.add_child(mod)
  arm_modifiers.append(mod)
  if rotation_valid[arm_modifiers.size()-1]:mod.set_meta("g2_orientation",true)

func set_monster_mode(mode:String)->void:
 if character!="grinner" or mode not in ["choose","grab","chainsaw"]:return
 monster_mode=mode
 if mode=="chainsaw" and not is_instance_valid(chainsaw_visual) and goals.size()>1:
  chainsaw_visual=preload("res://chainsaw_visual.gd").new();goals[1].add_child(chainsaw_visual)
 elif mode!="chainsaw" and is_instance_valid(chainsaw_visual):chainsaw_visual.queue_free();chainsaw_visual=null

func set_ragdoll(packet:Array,cut_height:float = -1.0,severed:int = 0)->void:
 if character!="normal" or packet.size() not in [4,5]:
  if is_instance_valid(ragdoll_visual):ragdoll_visual.queue_free();ragdoll_visual=null
  if is_instance_valid(avatar):avatar.visible=(character!="normal" or split_life!=life_number)
  var accessory=get_node_or_null("AcessorioCosmetico")
  if accessory:accessory.visible=(missing_parts & 1)==0
  return
 if not is_instance_valid(ragdoll_visual):
  ragdoll_visual=preload("res://ragdoll.gd").new();get_parent().add_child(ragdoll_visual)
  ragdoll_visual.setup(global_position,rotation.y,missing_parts,maxi(0,cosmetic_color),Vector3.ZERO,false)
 if cut_height>=0.0 and packet.size()==5:ragdoll_visual.cut_torso(cut_height)
 ragdoll_visual.apply_severed(severed)
 if is_instance_valid(avatar):avatar.visible=false
 var accessory=get_node_or_null("AcessorioCosmetico")
 if accessory:accessory.visible=false
 ragdoll_visual.apply_positions(packet)

func set_injury(mask: int, dead: bool, life: int) -> void:
 if character != "normal":
  life_number=life;is_dead=dead
  if is_instance_valid(avatar):avatar.visible=true
  return
 if missing_parts != mask or life_number != life:
  missing_parts=mask;life_number=life
  if split_life!=life and is_instance_valid(avatar):avatar.visible=true
  var injury=preload("res://injury_visual.gd")
  injury.update_rig(rig,mask)
  injury.update_wounds(self,mask,1)
 is_dead=dead
 var accessory=get_node_or_null("AcessorioCosmetico")
 if accessory:accessory.visible=(mask & 1)==0 and not is_instance_valid(ragdoll_visual)

func set_state(kind: String, pos: Vector3, yaw: float, l: Vector3, r: Vector3) -> void:
 if character != kind: configure(kind)
 desired_position=pos; desired_yaw=yaw;desired_left=l;desired_right=r
 if not has_state:
  global_position=pos;rotation.y=yaw;has_state=true

func update_detached_visuals() -> void:
 var selected := [{},{}]
 if character == "grinner":
  for entry in grabs:
   if entry is Dictionary and bool(entry.get("detached",false)):
    var side := int(entry.get("hand",-1))
    if side >= 0 and side < 2: selected[side]=entry
 for i in 2:
  if selected[i].is_empty():
   if is_instance_valid(detached_visuals[i]):
    preload("res://injury_visual.gd").drop_piece(get_parent(),detached_visuals[i],detached_offsets[i],Vector3.ZERO)
    detached_visuals[i]=null
   continue
  var part := str(selected[i].get("part",""))
  if part not in ["head","arm_l","arm_r"]: continue
  if not is_instance_valid(detached_visuals[i]):
   detached_visuals[i]=preload("res://injury_visual.gd").make_piece(part,clampi(int(selected[i].get("color",0)),0,9))
   if detached_visuals[i] == null: continue
   get_parent().add_child(detached_visuals[i])
   var raw_goal = selected[i].get("piece_goal",[])
   var goal:=Vector3.ZERO
   if raw_goal is Array and raw_goal.size()==3:
    goal=Vector3(float(raw_goal[0]),float(raw_goal[1]),float(raw_goal[2]))
   preload("res://injury_visual.gd").pose_piece(detached_visuals[i],part,goal)
  var offset = selected[i].get("offset",[])
  if not offset is Array or offset.size()!=3: continue
  var yaw := float(selected[i].get("victim_yaw",0.0))
  if not is_finite(yaw): continue
  var off := Vector3(float(offset[0]),float(offset[1]),float(offset[2]))
  detached_offsets[i]=off
  detached_visuals[i].global_position=goals[i].global_position-Basis(Vector3.UP,yaw)*off
  detached_visuals[i].rotation.y=yaw

func _exit_tree() -> void:
 if is_instance_valid(ragdoll_visual):ragdoll_visual.queue_free()
 for obj in detached_visuals:
  if is_instance_valid(obj): obj.queue_free()

func _process(delta: float) -> void:
 _pump_voice()
 if not has_state: return
 if is_instance_valid(voice_player):
  var g:=get_parent()
  if is_instance_valid(g) and is_instance_valid(g.get("player")):
   var dist:float=global_position.distance_to(g.player.global_position)
   voice_player.volume_db=linear_to_db(maxf(.0001,1.0-smoothstep(3.0,20.0,dist)))
 var t := 1.0-exp(-delta*12.0)
 global_position=global_position.lerp(desired_position,t)
 rotation.y=lerp_angle(rotation.y,desired_yaw,t)
 if grab_visual_active:
  global_position = grab_visual_position
 # On the holder's screen translate both arm goals along with the predicted
 # body position. Otherwise IK chases stale network coordinates and the
 # grabbed arm visibly floats away from the hand.
 var correction := global_position - desired_position if grab_visual_active else Vector3.ZERO
 goals[0].global_position=goals[0].global_position.lerp(desired_left+correction,t)
 goals[1].global_position=goals[1].global_position.lerp(desired_right+correction,t)
 for i in 2:
  if rotation_valid[i]:
   var current:=goals[i].global_basis.orthonormalized().get_rotation_quaternion()
   goals[i].global_basis=Basis(current.slerp(hand_rotations[i],t).normalized())
 update_detached_visuals()
 if is_instance_valid(face):preload("res://facial_animation.gd").apply(face,face_level)
