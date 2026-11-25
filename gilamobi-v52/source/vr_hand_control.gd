extends Node
# Experimental single-camera mapping. No real metric depth: reach is estimated
# from palm size and clamped. Lost hands freeze, and cannot generate impulses.
var vr: Node
var tracker
var extension
var feed
var state := tr("Iniciando câmera...")
var poll := 0.0
var offsets := [Vector3(-.38,-.3,-.65),Vector3(.38,-.3,-.65)]
# Inference runs independently at a variable frame rate. Interpolate on every
# render frame, rather than moving the arm only when a sample arrives.
var target_offsets := [Vector3(-.38,-.3,-.65),Vector3(.38,-.3,-.65)]
var target_floor_strength := [0.0,0.0]
const TRACK_POLL_INTERVAL := 1.0/30.0
const TRACK_FOLLOW_RATE := 22.0
var valid := [false,false]
var since_seen := [99.0,99.0]
var last_pos := [Vector2(.25,.5),Vector2(.75,.5)]
var suppress := [0.0,0.0]
var floor_height := [.76,.76]
var floor_strength := [0.0,0.0]
var calibration_time:=0.0
var calibration_samples := [[],[]]
var calibration_notice := ""
var notice_time:=0.0

func _ready()->void:
 var config:=ConfigFile.new()
 if config.load("user://calibracao_maos_vr.cfg")==OK:
  floor_height[0]=clampf(float(config.get_value("maos","esquerda",.76)),.58,.94)
  floor_height[1]=clampf(float(config.get_value("maos","direita",.76)),.58,.94)
 if OS.get_name()!="Android":state=tr("Câmera disponível apenas no Android");return
 if not Engine.has_singleton("GodotHandTracking") or not ClassDB.class_exists("CameraServerExtension"):
  state=tr("Plugin de mãos não carregou");return
 tracker=Engine.get_singleton("GodotHandTracking")
 extension=ClassDB.instantiate("CameraServerExtension")
 extension.permission_result.connect(_permission_result)
 if not extension.permission_granted():
  state=tr("Permita a câmera no celular");extension.request_permission()
 else:open_camera()

func _permission_result(granted:bool)->void:
 if granted:call_deferred("open_camera")
 else:state=tr("Permissão da câmera negada")

func open_camera()->void:
 if not is_inside_tree() or tracker==null:return
 for item in CameraServer.feeds():
  if item.get_position()==CameraFeed.FEED_BACK:feed=item;break
 if feed==null:state=tr("Câmera traseira não encontrada");return
 feed.feed_is_active=false
 tracker.stopFastCamera();tracker.stopTracking()
 tracker.setCameraPreviewEnabled(false)
 tracker.startTracking();tracker.startFastCamera(feed.get_name())
 state=tr("Preparando mãos...")

func center(hand:Array)->Vector2:
 if hand.size()<10 or not hand[0] is Array or not hand[9] is Array:return Vector2(.5,.5)
 if hand[0].size()<2 or hand[9].size()<2:return Vector2(.5,.5)
 return (Vector2(float(hand[0][0]),float(hand[0][1]))+Vector2(float(hand[9][0]),float(hand[9][1])))*.5

func begin_hand_calibration()->void:
 calibration_time=5.0;calibration_samples=[[],[]]
 calibration_notice=tr("ABAIXE AS MÃOS CONFORTAVELMENTE");notice_time=0.0

func finish_hand_calibration()->void:
 var count:=0
 for i in 2:
  var samples:Array=calibration_samples[i]
  if samples.size()>=5:
   samples.sort()
   floor_height[i]=clampf(float(samples[int((samples.size()-1)*.8)]),.58,.94)
   count+=1
  # Avoid an abrupt push when calibration ends.
  suppress[i]=.4
 if count>0:
  var config:=ConfigFile.new()
  config.set_value("maos","esquerda",floor_height[0]);config.set_value("maos","direita",floor_height[1]);config.save("user://calibracao_maos_vr.cfg")
  calibration_notice=tr("%d MÃO(S) CALIBRADA(S)")%count
 else:calibration_notice=tr("NÃO VI AS MÃOS • TENTE NOVAMENTE")
 notice_time=5.0

func accept_hand(i:int, hand:Array)->void:
 if hand.size()<10 or not hand[0] is Array or not hand[9] is Array:return
 if hand[0].size()<2 or hand[9].size()<2:return
 var p:=center(hand)
 if not is_finite(p.x) or not is_finite(p.y):return
 if calibration_time>0.0:calibration_samples[i].append(clampf(p.y,0.0,1.0))
 if since_seen[i]>.45:suppress[i]=.25
 since_seen[i]=0.0;valid[i]=true;last_pos[i]=p
 var a:=Vector2(float(hand[0][0]),float(hand[0][1]))
 var b:=Vector2(float(hand[9][0]),float(hand[9][1]))
 var palm:=clampf(a.distance_to(b),.035,.3)
 # Distant hands have a smaller palm. This is only an approximation.
 var reach:=clampf(.65*(.11/palm),.40,1.15)
 # Lowering a hand reaches the floor more reliably. A downward stroke
 # also sweeps slightly back, allowing a forward reaction on contact.
 var sweep:=clampf((p.y-.50)/maxf(.08,float(floor_height[i])-.50),0.0,1.0)*.38
 target_floor_strength[i]=smoothstep(float(floor_height[i])-.10,float(floor_height[i]),p.y)
 var target:=Vector3(clampf((p.x-.5)*1.8,-.95,.95),clampf((.5-p.y)*3.6,-1.75,.8),-reach+sweep)
 target_offsets[i]=target

func _process(delta:float)->void:
 if calibration_time>0.0:
  calibration_time=maxf(0.0,calibration_time-delta)
  if calibration_time==0.0:finish_hand_calibration()
 elif notice_time>0.0:notice_time=maxf(0.0,notice_time-delta)
 for i in 2:
  since_seen[i]+=delta;suppress[i]=maxf(0.0,suppress[i]-delta)
  if since_seen[i]>.45:
   valid[i]=false;floor_strength[i]=0.0;target_floor_strength[i]=0.0
 poll+=delta
 if tracker!=null and poll>=TRACK_POLL_INTERVAL:
  poll=fmod(poll,TRACK_POLL_INTERVAL)
  var camera_state:String=tracker.getFastCameraState()
  var tracking_state:String=tracker.getTrackingState()
  if camera_state.begins_with("erro"):state=tr("Câmera: ")+I18n.native_status(camera_state)
  elif tracking_state.begins_with("erro"):state=tr("Mãos: ")+I18n.native_status(tracking_state)
  elif camera_state!="ativa" or tracking_state!="pronto":state=tr("Preparando câmera e mãos...")
  else:
   var decoded=JSON.parse_string(tracker.getLandmarks())
   var hands:Array=decoded if decoded is Array else []
   var items:Array=[]
   for hand in hands:
    if hand is Array and hand.size()>=10:items.append(hand)
   if items.size()>=2:
    if center(items[0]).x>center(items[1]).x:items.reverse()
    accept_hand(0,items[0]);accept_hand(1,items[1])
   elif items.size()==1:
    var p:Vector2=center(items[0]);var index:=0 if p.x<.5 else 1
    if since_seen[0]<.45 and since_seen[1]>=.45 and p.distance_to(last_pos[0])<.24:index=0
    elif since_seen[1]<.45 and since_seen[0]>=.45 and p.distance_to(last_pos[1])<.24:index=1
    accept_hand(index,items[0])
   state=tr("Mãos %d • %d fps")%[int(valid[0])+int(valid[1]),tracker.getTrackingFps()]
 # Smooth toward the most recent measurement continuously, without adding
 # another 10 Hz bottleneck or depending on a fixed render frame rate.
 var blend:=1.0-exp(-TRACK_FOLLOW_RATE*maxf(0.0,delta))
 for i in 2:
  if valid[i]:
   offsets[i]=offsets[i].lerp(target_offsets[i],blend)
   floor_strength[i]=lerpf(float(floor_strength[i]),float(target_floor_strength[i]),blend)
 if is_instance_valid(vr) and is_instance_valid(vr.game):
  vr.game.vr_hand_enabled=true
  vr.game.vr_hand_offsets=offsets.duplicate()
  vr.game.vr_hand_tracked=valid.duplicate()
  vr.game.vr_floor_strength=floor_strength.duplicate()
  var calibrated:bool=vr.calibrate_countdown<=0.0 and vr.bias_time<=0.0 and calibration_time<=0.0
  vr.game.vr_hand_can_push=[valid[0] and suppress[0]<=0.0 and calibrated,valid[1] and suppress[1]<=0.0 and calibrated]

func _exit_tree()->void:
 if tracker!=null:tracker.stopFastCamera();tracker.stopTracking();tracker.setCameraPreviewEnabled(true)
 if is_instance_valid(vr) and is_instance_valid(vr.game):
  vr.game.vr_hand_enabled=false;vr.game.vr_hand_tracked=[false,false];vr.game.vr_hand_can_push=[false,false];vr.game.vr_floor_strength=[0.0,0.0]
