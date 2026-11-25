extends Node
# Experimental 3DOF: Android fused rotation, with gyro-only fallback.
var game: Node3D
var layer: CanvasLayer
var views: Array[SubViewport] = []
var eyes: Array[Camera3D] = []
var head_orientation:=Quaternion.IDENTITY
var orientation_plugin
var sensor_reference:=Quaternion.IDENTITY
var sensor_reference_valid:=false
var sensor_display_rotation:=0
var body_sensor_yaw:=0.0
var orientation_status:=tr("giroscópio")
var gyro_bias:=Vector3.ZERO
var bias_accum:=Vector3.ZERO
var bias_samples:=0
var bias_time:=0.0
var calibrate_countdown:=10.0
var hint_time:=15.0
var marker_root:Node3D
var markers:Array[Node3D]=[]
var gaze_index:=-1
var gaze_time:=0.0
var gaze_locked:=-1
var mic_confirmation:=0.0
var menu_open:=false
var menu_remaining:=0.0
var menu_gesture_time:=0.0
var menu_needs_release:=false
const MENU_GESTURE_SECONDS:=2.0
const MENU_TIMEOUT:=12.0
var action_notice:=""
var action_notice_time:=0.0
var marker_labels:Array[Label3D]=[]
var previous_mask:=0
var previous_camera_rotation:=Vector3.ZERO
var ending:=false
var hand_control:Node
var sandbox_gaze_role:=""
var sandbox_gaze_time:=0.0
var sandbox_gaze_locked:=false
const IPD:=0.064
const SAMPLE_TIME:=1.2
const GAZE_SECONDS:=1.8
# Allow the head to look around freely; the torso gradually follows beyond this.
const BODY_FOLLOW_LIMIT:=deg_to_rad(20.0)
const BODY_FOLLOW_RATE:=3.2
const BODY_MAX_SPEED:=deg_to_rad(150.0)

func _ready()->void:
 previous_mask=game.camera.cull_mask
 previous_camera_rotation=game.camera.rotation
 game.camera.rotation=Vector3.ZERO
 game.player.rotation.y=0
 game.player.velocity=Vector3.ZERO
 game.game_running=true
 game.vr_hand_enabled=true;game.vr_hand_tracked=[false,false];game.vr_hand_can_push=[false,false];game.vr_push_cooldown=[0.0,0.0]
 game.ui.visible=false
 layer=CanvasLayer.new();layer.name="TelaVRBox";layer.layer=30;add_child(layer)
 var screen:=Control.new();screen.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);screen.mouse_filter=Control.MOUSE_FILTER_IGNORE;layer.add_child(screen)
 for i in 2:
  var view:=SubViewport.new();view.name="OlhoEsquerdo" if i==0 else "OlhoDireito"
  view.size=Vector2i(384,432)
  view.world_3d=game.get_viewport().find_world_3d()
  view.render_target_update_mode=SubViewport.UPDATE_ALWAYS;add_child(view);views.append(view)
  var eye:=Camera3D.new();eye.fov=80;eye.near=.05;eye.far=150;eye.cull_mask=previous_mask;view.add_child(eye);eye.current=true;eyes.append(eye)
  var rect:=TextureRect.new();rect.name="VisaoEsquerda" if i==0 else "VisaoDireita"
  rect.texture=view.get_texture();rect.expand_mode=TextureRect.EXPAND_IGNORE_SIZE;rect.stretch_mode=TextureRect.STRETCH_SCALE
  rect.anchor_left=0.0 if i==0 else .5;rect.anchor_right=.5 if i==0 else 1.0;rect.anchor_top=0;rect.anchor_bottom=1
  var lens:=ShaderMaterial.new();lens.shader=load("res://vr_lens.gdshader");rect.material=lens
  rect.mouse_filter=Control.MOUSE_FILTER_IGNORE;screen.add_child(rect)
 game.camera.cull_mask=0
 if OS.get_name()=="Android" and Engine.has_singleton("GodotHandTracking"):
  orientation_plugin=Engine.get_singleton("GodotHandTracking")
  orientation_plugin.startOrientation()
 var hud=preload("res://vr_hud.gd").new();hud.vr=self;screen.add_child(hud)
 make_markers()
 hand_control=preload("res://vr_hand_control.gd").new();hand_control.vr=self;add_child(hand_control)
 sync_cameras()

func make_markers()->void:
 marker_root=Node3D.new();marker_root.name="MenuPorOlhar";game.player.add_child(marker_root)
 # The normal room selection happens before putting the phone in the box.
 # In a match, gaze controls replace buttons unreachable inside the headset.
 var entries:=[
  [tr("RECENTRAR"),Vector3(-.95,-.55,-2.8),Color("254b50")],
  [tr("CALIBRAR MÃOS"),Vector3(0,-.55,-2.8),Color("365c79")],
  [tr("MENU"),Vector3(.95,-.55,-2.8),Color("743337")],
  [tr("SOLTAR"),Vector3(-1.32,-1.05,-2.8),Color("665132")],
  [tr("ARRANCAR E"),Vector3(-.44,-1.05,-2.8),Color("663b3b")],
  [tr("ARRANCAR D"),Vector3(.44,-1.05,-2.8),Color("663b3b")],
  ["MIC",Vector3(1.32,-1.05,-2.8),Color("3b5366")],
  [tr("AGARRAR"),Vector3(-.58,-.10,-2.8),Color("256550")],
  [tr("MOTOSSERRA"),Vector3(.58,-.10,-2.8),Color("815727")],
  [tr("PEGAR CÂMERA"),Vector3(-.50,-1.45,-2.8),Color("304e67")],
  [tr("GRAVAR VÍDEO"),Vector3(.50,-1.45,-2.8),Color("782e32")]]
 for entry in entries:
  var marker:=Node3D.new();marker_root.add_child(marker);markers.append(marker)
  marker.position=entry[1]
  var panel:=MeshInstance3D.new();var box:=BoxMesh.new();box.size=Vector3(.80,.29,.04);panel.mesh=box
  var mat:=StandardMaterial3D.new();mat.albedo_color=entry[2];mat.shading_mode=BaseMaterial3D.SHADING_MODE_UNSHADED;mat.no_depth_test=true
  panel.material_override=mat;marker.add_child(panel)
  var label:=Label3D.new();label.text=entry[0];label.font_size=28 if I18n.language=="ru" else 34;label.pixel_size=.0022;label.position.z=.03;label.no_depth_test=true;label.modulate=Color.WHITE;marker.add_child(label);marker_labels.append(label)
 marker_root.visible=false
 update_marker_visibility()

func update_marker_visibility()->void:
 if markers.size()<9:return
 var choosing:bool=game.active_character=="grinner" and game.monster_mode=="choose"
 var monster:bool=game.active_character=="grinner" and game.monster_mode=="grab"
 for i in [0,1,2,6]:markers[i].visible=not choosing
 for i in [3,4,5]:markers[i].visible=monster and not choosing
 for i in [7,8]:markers[i].visible=choosing
 var cam=game.sandbox_camera
 var available:bool=not choosing and game.selected_map=="sandbox" and is_instance_valid(cam)
 markers[9].visible=available
 markers[10].visible=available and cam.spawned
 if available:
  marker_labels[9].text=tr("DEIXAR NO AR") if cam.held else tr("CÂMERA NA MÃO")
  marker_labels[10].text=tr("PARAR VÍDEO") if cam.recording else tr("GRAVAR VÍDEO")
 if is_instance_valid(game.voice_face):
  if game.voice_face.active or game.voice_face.waiting_permission or game.voice_face.pending_start:marker_labels[6].text=tr("DESLIGAR MIC")
  elif mic_confirmation>0:marker_labels[6].text=tr("CONFIRMAR MIC")
  else:marker_labels[6].text=tr("LIGAR MIC")

func activate_marker(chosen:int)->void:
 if chosen==9 and is_instance_valid(game.sandbox_camera):
  game.sandbox_camera.pickup_or_drop();action_notice=game.sandbox_camera.status;action_notice_time=4
 elif chosen==10 and is_instance_valid(game.sandbox_camera):
  game.sandbox_camera.toggle_record();action_notice=game.sandbox_camera.status;action_notice_time=4
 elif chosen==7:game.choose_monster_mode("grab")
 elif chosen==8:game.choose_monster_mode("chainsaw")
 elif chosen==0:start_calibration()
 elif chosen==1 and is_instance_valid(hand_control):hand_control.begin_hand_calibration()
 elif chosen==2:exit_vr()
 elif chosen==3 and game.active_character=="grinner":game.release_grabs()
 elif chosen==4 and game.active_character=="grinner":game.tear_held(0)
 elif chosen==5 and game.active_character=="grinner":game.tear_held(1)
 elif chosen==6 and is_instance_valid(game.voice_face):
  if game.voice_face.active or game.voice_face.waiting_permission or game.voice_face.pending_start:
   game.voice_face.toggle();mic_confirmation=0.0
  elif game.network.state=="joined" and mic_confirmation<=0.0:
   mic_confirmation=12.0;action_notice=tr("VOZ PÚBLICA: DESVIE O OLHAR E CONFIRME MIC");action_notice_time=12.0
  else:
   game.voice_face.toggle();mic_confirmation=0.0;action_notice=game.voice_face.status;action_notice_time=3.0
 update_marker_visibility()

func integrate_gyro(sample:Vector3,dt:float)->void:
 var omega:=sample-gyro_bias
 if omega.length()<0.012:return
 var angle:=omega.length()*clampf(dt,0.0,.12)
 head_orientation=(head_orientation*Quaternion(omega.normalized(),angle)).normalized()

func follow_head_with_body(delta:float)->void:
 # Head orientation is relative to the body. Rotate the body only after the
 # head crosses a comfortable horizontal threshold. Counter-rotate the head
 # by exactly the same amount, so the user's view stays fixed in world space.
 # Project the head's RIGHT axis, not its forward axis. Forward becomes
 # vertical near the zenith and its yaw suddenly flips by 180 degrees.
 var right:Vector3=head_orientation * Vector3.RIGHT
 if Vector2(right.x,right.z).length()<.12:return
 var yaw:=atan2(-right.z,right.x)
 var excess:=absf(yaw)-BODY_FOLLOW_LIMIT
 if excess<=0.0:return
 var step:=signf(yaw)*minf(excess*BODY_FOLLOW_RATE,BODY_MAX_SPEED)*clampf(delta,0.0,.05)
 var before:Vector3=game.camera.global_position
 game.player.rotate_y(step)
 body_sensor_yaw+=step
 head_orientation=(Quaternion(Vector3.UP,-step)*head_orientation).normalized()
 # Pivot around the headset, not the body capsule, avoiding camera translation
 # when the body model follows the head.
 game.player.global_position+=before-game.camera.global_position

# The sensor quaternion maps portrait-phone coordinates into world coordinates.
# q0^-1*q1 gives relative movement; conjugation maps the phone's raw axes
# into the same display-oriented axes as Godot's Input.get_gyroscope().
func relative_sensor_orientation(reference:Quaternion, current:Quaternion, display:int)->Quaternion:
 var angle:=0.0
 if display==1:angle=PI*.5
 elif display==2:angle=PI
 elif display==3:angle=-PI*.5
 var conversion:=Quaternion(Vector3.BACK,angle)
 return (conversion*(reference.inverse()*current)*conversion.inverse()).normalized()

func read_fused()->PackedFloat32Array:
 if orientation_plugin==null:return PackedFloat32Array()
 var values=orientation_plugin.getOrientation()
 return values if values is PackedFloat32Array else PackedFloat32Array()

func apply_fused(values:PackedFloat32Array)->bool:
 if values.size()<5 or int(values[4])!=sensor_display_rotation:return false
 var q:=Quaternion(values[0],values[1],values[2],values[3])
 if q.length_squared()<.5:return false
 var relative:=relative_sensor_orientation(sensor_reference,q.normalized(),sensor_display_rotation)
 head_orientation=(Quaternion(Vector3.UP,-body_sensor_yaw)*relative).normalized()
 return true

func sync_cameras()->void:
 if not is_instance_valid(game.camera):return
 game.camera.quaternion=head_orientation
 var transform:Transform3D=game.camera.global_transform
 for i in 2:
  eyes[i].global_transform=transform.translated_local(Vector3(-IPD*.5 if i==0 else IPD*.5,0,0))

func start_calibration()->void:
 calibrate_countdown=3.0;bias_time=0;bias_accum=Vector3.ZERO;bias_samples=0;gaze_index=-1;gaze_time=0

func _process(delta:float)->void:
 if ending or not is_instance_valid(game.camera):return
 mic_confirmation=maxf(0.0,mic_confirmation-delta)
 action_notice_time=maxf(0.0,action_notice_time-delta)
 update_marker_visibility()
 var sample:=Input.get_gyroscope()
 if calibrate_countdown>0:
  calibrate_countdown=maxf(0,calibrate_countdown-delta)
  if calibrate_countdown==0:
   bias_time=SAMPLE_TIME;bias_samples=0;bias_accum=Vector3.ZERO
 elif bias_time>0:
  bias_accum+=sample;bias_samples+=1;bias_time=maxf(0,bias_time-delta)
  if bias_time==0:
   var measured_bias:Vector3=bias_accum/maxi(1,bias_samples)
   # Never mistake a moving head during calibration for a giant sensor bias.
   gyro_bias=measured_bias if measured_bias.length()<.035 else Vector3.ZERO
   head_orientation=Quaternion.IDENTITY;body_sensor_yaw=0.0;gaze_time=0;gaze_index=-1
   var values:=read_fused();sensor_reference_valid=false
   if values.size()>=5:
    var q:=Quaternion(values[0],values[1],values[2],values[3])
    if q.length_squared()>.5:
     sensor_reference=q.normalized();sensor_display_rotation=int(values[4]);sensor_reference_valid=true
   orientation_status=tr("sensores combinados") if sensor_reference_valid else tr("giroscópio")
 else:
  if sensor_reference_valid:
   if not apply_fused(read_fused()):orientation_status=tr("sensor parado: recentre")
   else:orientation_status=tr("sensores combinados")
  else:integrate_gyro(sample,delta)
  follow_head_with_body(delta)
  hint_time=maxf(0,hint_time-delta)
  gaze_check(delta)
  sandbox_gaze(delta)
 sync_cameras()

func sandbox_gaze(delta:float)->void:
 if game.selected_map!="sandbox" or menu_open:return
 var origin:Vector3=game.camera.global_position
 var forward:Vector3=-game.camera.global_basis.z.normalized()
 var role:=""
 for marker in game.sandbox_button_positions():
  var diff:Vector3=marker.global_position-origin
  if diff.length()<=3.2 and diff.length()>.01 and forward.dot(diff.normalized())>.985:
   role=str(marker.get_meta("role"));break
 if role.is_empty():sandbox_gaze_time=0;sandbox_gaze_role="";sandbox_gaze_locked=false;return
 if role!=sandbox_gaze_role:sandbox_gaze_role=role;sandbox_gaze_time=0;sandbox_gaze_locked=false
 if sandbox_gaze_locked:return
 sandbox_gaze_time+=delta
 if sandbox_gaze_time>=1.8:
  sandbox_gaze_locked=true;game.select_sandbox_role.call_deferred(role)

func open_gaze_menu()->void:
 if not is_instance_valid(marker_root):return
 # Recenter the panel in front of the body, not at the downward gesture.
 marker_root.global_transform=Transform3D(game.player.global_basis.orthonormalized(),game.camera.global_position)
 marker_root.visible=true;menu_open=true;menu_remaining=MENU_TIMEOUT
 gaze_index=-1;gaze_time=0.0;gaze_locked=-1;menu_gesture_time=0.0;menu_needs_release=true
 action_notice=tr("MENU ABERTO • OLHE PARA UMA AÇÃO");action_notice_time=3.0

func begin_monster_choice()->void:
 open_gaze_menu();update_marker_visibility()
 action_notice=tr("VOCÊ É MONSTRO • ESCOLHA AGARRAR OU MOTOSSERRA");action_notice_time=12.0

func finish_monster_choice()->void:
 update_marker_visibility();close_gaze_menu()

func close_gaze_menu()->void:
 menu_open=false;menu_remaining=0.0;gaze_index=-1;gaze_time=0.0;gaze_locked=-1
 if is_instance_valid(marker_root):marker_root.visible=false

func gaze_check(delta:float)->void:
 var origin:Vector3=game.camera.global_position
 var forward:Vector3=-game.camera.global_basis.z.normalized()
 if menu_needs_release and forward.y>-.45:menu_needs_release=false
 if not menu_open:
  # Looking down is only an unlock gesture: it cannot itself press a button.
  if not menu_needs_release and forward.y<-.80:
   menu_gesture_time+=maxf(delta,0.0)
   if menu_gesture_time>=MENU_GESTURE_SECONDS:open_gaze_menu()
  else:menu_gesture_time=0.0
  return
 var choosing:bool=game.active_character=="grinner" and game.monster_mode=="choose"
 if not choosing:menu_remaining-=maxf(delta,0.0)
 if not choosing and menu_remaining<=0.0:close_gaze_menu();return
 var chosen:=-1
 for i in markers.size():
  if not markers[i].visible:continue
  var direction:Vector3=(markers[i].global_position-origin).normalized()
  if forward.dot(direction)>.992:chosen=i;break
 if chosen<0:gaze_index=-1;gaze_time=0;gaze_locked=-1;return
 menu_remaining=MENU_TIMEOUT
 if chosen!=gaze_locked:gaze_locked=-1
 if gaze_locked==chosen:return
 if gaze_index!=chosen:gaze_index=chosen;gaze_time=0
 gaze_time+=maxf(delta,0.0)
 if gaze_time>=GAZE_SECONDS:
  gaze_time=0;gaze_index=-1;gaze_locked=chosen
  activate_marker(chosen)
  # The privacy warning needs a second deliberate gaze to enable voice.
  if chosen!=6 or mic_confirmation<=0.0:close_gaze_menu()

func exit_vr()->void:
 if ending:return
 ending=true
 game.game_running=false
 if is_instance_valid(hand_control):hand_control.queue_free()
 if is_instance_valid(marker_root):marker_root.queue_free()
 game.camera.cull_mask=previous_mask;game.camera.rotation=previous_camera_rotation
 game.game_menu.return_to_menu()
 queue_free()

func _exit_tree()->void:
 if orientation_plugin!=null:orientation_plugin.stopOrientation()
 if is_instance_valid(marker_root):marker_root.queue_free()
 if is_instance_valid(game):
  game.vr_hand_enabled=false;game.vr_hand_tracked=[false,false];game.vr_hand_can_push=[false,false];game.vr_floor_strength=[0.0,0.0]
  if is_instance_valid(game.camera):game.camera.cull_mask=previous_mask
