extends Node
# Microphone is opt-in. Online voice goes through the public broker, not private.
var game: Node
var active := false
var sample_phase := 0.0
var voice_packet := PackedByteArray()
var waiting_permission := false
var capture: AudioEffectCapture
var mic: AudioStreamPlayer
var face: MeshInstance3D
var facial_level := 0.0
var status := tr("MIC DESLIGADO")
var _last_samples_usec := 0
var permission_granted_usec := 0
var resume_after_permission := false
# On Android use AudioRecord directly; restarting Godot 4.4 microphone input
# can fail on some devices. Desktop keeps the standard Godot path.
var native_voice
var desired_active:=false
var pending_start:=false
var retry_due_usec:=0
var retry_count:=0
var empty_restarts:=0
var echo_hold_until_msec:=0
const RETRY_LIMIT:=12

func _ready() -> void:
 get_tree().on_request_permissions_result.connect(_on_permission_result)
 if OS.get_name()=="Android" and Engine.has_singleton("GodotHandTracking"):
  var plugin=Engine.get_singleton("GodotHandTracking")
  if plugin.has_method("startNativeVoice"):native_voice=plugin
 var bus := AudioServer.get_bus_index("MicFace")
 if bus < 0:
  AudioServer.add_bus()
  bus = AudioServer.bus_count - 1
  AudioServer.set_bus_name(bus, "MicFace")
  AudioServer.set_bus_send(bus, "Master")
 capture = AudioEffectCapture.new()
 capture.buffer_length = 0.2
 AudioServer.add_bus_effect(bus, capture)
 # Silence the signal AFTER capturing it, to prevent feedback/echo.
 var suppress := AudioEffectAmplify.new()
 suppress.volume_db = -80.0
 AudioServer.add_bus_effect(bus, suppress)
 mic = AudioStreamPlayer.new()
 mic.name = "MicrofoneLocal"
 mic.bus = "MicFace"
 mic.stream = AudioStreamMicrophone.new()
 add_child(mic)

func toggle() -> void:
 if active or waiting_permission or pending_start:
  waiting_permission = false
  stop_mic()
  return
 if OS.get_name() == "Android":
  var granted := false
  for permission in OS.get_granted_permissions():
   if permission.ends_with("RECORD_AUDIO"): granted = true
  if not granted:
   waiting_permission = true
   status = tr("PERMITA O MICROFONE")
   OS.request_permission("android.permission.RECORD_AUDIO")
   return
 start_mic()

func _on_permission_result(permission: String, granted: bool) -> void:
 if not permission.ends_with("RECORD_AUDIO") or not waiting_permission: return
 waiting_permission = false
 if granted:
  permission_granted_usec = Time.get_ticks_usec()
  start_mic()
 else: status = tr("MIC SEM PERMISSAO")

func start_mic() -> void:
 desired_active=true;retry_count=0;empty_restarts=0
 voice_packet.clear();sample_phase=0.0
 _attempt_start()

func _attempt_start()->void:
 if not desired_active:return
 pending_start=false
 if native_voice!=null:
  if not native_voice.startNativeVoice():
   active=false;retry_count+=1
   if retry_count<=RETRY_LIMIT:
    pending_start=true;retry_due_usec=Time.get_ticks_usec()+250000
    status=tr("MIC REINICIANDO...")
   else:
    desired_active=false;status=tr("MIC NÃO INICIOU — ")+I18n.native_status(str(native_voice.getNativeVoiceState()))
   return
 else:
  capture.clear_buffer();mic.play()
 active=true;retry_count=0
 status=tr("MIC LIGADO — VOZ PÚBLICA") if game.network.state=="joined" else tr("MIC LIGADO — SÓ VOCÊ")
 _last_samples_usec=Time.get_ticks_usec()

func stop_mic() -> void:
 # Explicit mute cancels pending retries before stopping capture.
 desired_active=false;pending_start=false;active=false;waiting_permission=false;resume_after_permission=false
 if native_voice!=null:native_voice.stopNativeVoice()
 elif mic:mic.stop()
 if capture:capture.clear_buffer()
 voice_packet.clear();sample_phase=0.0;echo_hold_until_msec=0;facial_level=0.0;set_face(0.0)
 status=tr("MIC DESLIGADO")

func native_level(data:PackedByteArray)->float:
 if data.is_empty():return 0.0
 var sum:=0.0
 var count:=data.size()/2
 if count==0:return 0.0
 for i in count:
  var x:=int(data[i*2]) | (int(data[i*2+1])<<8)
  if x>=32768:x-=65536
  var v:=float(x)/32768.0
  sum+=v*v
 return clampf((sqrt(sum/count)-.008)*13.0,0.0,1.0)

func note_incoming_voice()->void:
 # Half-duplex echo guard for speakers; this sacrifices simultaneous speech.
 if is_instance_valid(game) and game.echo_guard_enabled:
  echo_hold_until_msec=maxi(echo_hold_until_msec,Time.get_ticks_msec()+450)

func echo_blocked()->bool:
 return is_instance_valid(game) and game.echo_guard_enabled and Time.get_ticks_msec()<echo_hold_until_msec

func send_native_samples(data:PackedByteArray)->void:
 if not active or game.network.state!="joined" or echo_blocked():voice_packet.clear();return
 voice_packet.append_array(data)
 # Keep at most 150 ms of the newest capture after a long frame stall.
 if voice_packet.size()>6400:voice_packet=voice_packet.slice(voice_packet.size()-4800)
 var sent:=0
 while voice_packet.size()>=1600 and sent<3:
  game.network.send_voice(preload("res://voice_codec.gd").encode_pcm(voice_packet.slice(0,1600)))
  voice_packet=voice_packet.slice(1600);sent+=1

func _notification(what: int) -> void:
 # Android may pause the app while the permission dialog is visible.
 # Do not cancel the pending permission in that case.
 if what == NOTIFICATION_APPLICATION_PAUSED and (active or pending_start):
  var permission_dialog := permission_granted_usec > 0 and Time.get_ticks_usec() - permission_granted_usec < 2000000
  stop_mic()
  resume_after_permission = permission_dialog
 elif what == NOTIFICATION_APPLICATION_RESUMED and resume_after_permission:
  resume_after_permission = false
  call_deferred("start_mic")
 elif what == NOTIFICATION_WM_CLOSE_REQUEST:
  stop_mic()

func calculate_level(samples: PackedVector2Array) -> float:
 if samples.is_empty(): return 0.0
 var sum := 0.0
 for frame in samples:
  var mono := (frame.x + frame.y) * 0.5
  sum += mono * mono
 var rms := sqrt(sum / samples.size())
 # Noise gate followed by a gentle compression so ordinary speech moves
 # the mouth and louder speech makes the eyes bulge comically.
 return clampf((rms - 0.008) * 13.0, 0.0, 1.0)

func set_face(amount: float) -> void:
 if not is_instance_valid(face): return
 preload("res://facial_animation.gd").apply(face,amount)

func send_samples(samples: PackedVector2Array) -> void:
 if echo_blocked():voice_packet.clear();sample_phase=0.0;return
 # Desktop capture follows the same 16 kHz mu-law protocol.
 var step := float(AudioServer.get_mix_rate()) / 16000.0
 for frame in samples:
  sample_phase += 1.0
  if sample_phase >= step:
   sample_phase -= step
   var mono := clampf((frame.x + frame.y) * 0.5, -1.0, 1.0)
   var value:=clampi(roundi(mono*32767.0),-32768,32767)
   voice_packet.append(preload("res://voice_codec.gd").encode_sample(value))
 # Drain up to 150 ms per frame so low FPS does not discard half the voice.
 if voice_packet.size()>3200:voice_packet=voice_packet.slice(voice_packet.size()-2400)
 var sent:=0
 while voice_packet.size()>=800 and sent<3:
  game.network.send_voice(voice_packet.slice(0,800))
  voice_packet=voice_packet.slice(800);sent+=1

func _process(delta: float) -> void:
 if pending_start and desired_active and Time.get_ticks_usec()>=retry_due_usec:_attempt_start()
 var level := 0.0
 if active and native_voice!=null:
  var data:PackedByteArray=native_voice.getNativeVoice()
  var state:String=str(native_voice.getNativeVoiceState())
  if state!="gravando":
   active=false;voice_packet.clear()
   if desired_active:
    native_voice.stopNativeVoice();empty_restarts+=1
    if empty_restarts<=3:pending_start=true;retry_due_usec=Time.get_ticks_usec()+250000;status=tr("MIC REINICIANDO...")
    else:pending_start=false;desired_active=false;status=tr("MIC SEM ÁUDIO — ")+I18n.native_status(state)
  elif not data.is_empty():
   empty_restarts=0;level=native_level(data);send_native_samples(data)
   status=tr("MIC LIGADO — VOZ PÚBLICA") if game.network.state=="joined" else tr("MIC LIGADO — SÓ VOCÊ")
   _last_samples_usec=Time.get_ticks_usec()
  elif Time.get_ticks_usec()-_last_samples_usec<150000:level=facial_level
  elif Time.get_ticks_usec()-_last_samples_usec>3000000:
   native_voice.stopNativeVoice();active=false;empty_restarts+=1
   if empty_restarts<=3:pending_start=true;retry_due_usec=Time.get_ticks_usec()+250000;status=tr("MIC REINICIANDO...")
   else:desired_active=false;pending_start=false;status=tr("MIC SEM ÁUDIO — VERIFIQUE A PERMISSÃO")
 elif active and capture:
  var available := capture.get_frames_available()
  if available > 0:
   var samples := capture.get_buffer(available)
   status = tr("MIC LIGADO — VOZ PÚBLICA") if game.network.state == "joined" else tr("MIC LIGADO — SÓ VOCÊ")
   level = calculate_level(samples)
   if is_instance_valid(game) and game.network.state == "joined": send_samples(samples)
   elif is_instance_valid(game): voice_packet.clear()
   _last_samples_usec = Time.get_ticks_usec()
  elif Time.get_ticks_usec() - _last_samples_usec < 150000:
   level = facial_level
  elif Time.get_ticks_usec() - _last_samples_usec > 3000000:
   status = tr("MIC SEM ÁUDIO — VERIFIQUE A PERMISSÃO")
 facial_level = lerpf(facial_level, level, 1.0 - exp(-delta * (22.0 if level > facial_level else 11.0)))
 set_face(facial_level)
