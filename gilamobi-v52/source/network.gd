extends Node
# Experimental public test broker. Positions and voice are not private.
signal room_ready(code: String)
signal room_error(message: String)
signal room_closed(message: String)
var game: Node3D
var socket: WebSocketPeer
var uid := ""
var room_code := ""
var topic := ""
var mode := ""
var state := "idle"
var timer := 0.0
var heartbeat := 0.0
var send_timer := 0.0
var saw_fragment_events := {}
var ping_timer := 0.0
var subscribed := false
var peers := {}
var peer_seen := {}
var host_seen := 0.0
var host_id := ""
var admitted := {}
var max_players := 10
var public_room := false
var server_name := ""
var room_map := "base"
var infection
var voice_sequence := 0
var chat_last_received := {}
var chat_last_sent := 0
var chat_sequence:=0
var chat_last_sequence:={}
var pending_chat:Dictionary={}
var pending_chat_due:=0
var pending_chat_retries:=0
const BROKER := "wss://test.mosquitto.org:8081/mqtt"

func begin(which: String, code := "", room_name := "", capacity := 10, is_public := false) -> void:
 leave(false)
 max_players = clampi(capacity,2,10)
 public_room = is_public and which == "create"
 server_name = room_name.strip_edges().substr(0,24)
 room_map = "sandbox" if which=="sandbox" else (game.selected_map if which == "create" and game.selected_map in ["base","backrooms"] else "base")
 admitted.clear();host_seen = 0.0;host_id = ""
 if which == "join" and (code.length() != 6 or not code.is_valid_int()):
  room_error.emit(tr("Digite um código de 6 números.")); return
 uid = Crypto.new().generate_random_bytes(8).hex_encode()
 room_code = "SANDBOX" if which=="sandbox" else ("%06d" % randi_range(0,999999) if which == "create" else code)
 mode = which
 topic = "gilamobi/teste/v19/sandbox" if which=="sandbox" else "gilamobi/teste/v19/" + room_code
 infection=null if which=="sandbox" else preload("res://infection_round.gd").new(self)
 socket = WebSocketPeer.new()
 socket.supported_protocols = PackedStringArray(["mqtt"])
 var err := socket.connect_to_url(BROKER)
 if err != OK:
  leave(false);room_error.emit(tr("Não foi possível conectar ao servidor de teste."));return
 state = "connecting"
 timer = 0.0; heartbeat = 0.0; send_timer = 0.0; ping_timer = 0.0; subscribed = false

func leave(announce := true) -> void:
 if public_room and is_instance_valid(game) and is_instance_valid(game.directory):
  game.directory.announce_stop(room_code)
 if socket and socket.get_ready_state() == WebSocketPeer.STATE_OPEN:
  if announce and state == "joined": _publish({"kind":"host_left" if mode == "create" else "left"})
  socket.close()
 socket = null
 for p in peers.values():
  if is_instance_valid(p): p.queue_free()
 peers.clear();peer_seen.clear();admitted.clear();chat_last_received.clear();chat_last_sent=0;chat_last_sequence.clear();pending_chat.clear();pending_chat_retries=0
 if is_instance_valid(game): game.clear_grabs()
 infection=null
 if is_instance_valid(game) and game.has_method("reset_round_state"):game.reset_round_state()
 state = "idle";room_code = "";topic = "";mode = "";subscribed = false
 public_room = false;server_name = "";host_id = ""

func _process(delta: float) -> void:
 if state == "idle" or socket == null: return
 socket.poll()
 timer += delta;heartbeat += delta;send_timer += delta;ping_timer += delta
 var ready := socket.get_ready_state()
 if ready == WebSocketPeer.STATE_CLOSED:
  var was_joined := state == "joined"
  leave(false)
  if was_joined: room_closed.emit(tr("Conexão com o servidor de teste perdida."))
  else: room_error.emit(tr("Não foi possível conectar. Verifique a internet."))
  return
 if ready == WebSocketPeer.STATE_OPEN and state == "connecting":
  _send_connect()
  state = "mqtt_connect"
 if state != "joined" and timer > 12.0:
  var message := tr("Sala não encontrada ou servidor sem resposta.") if mode == "join" else tr("Servidor de teste sem resposta.")
  leave(false);room_error.emit(message);return
 if ready != WebSocketPeer.STATE_OPEN: return
 while socket != null and socket.get_available_packet_count() > 0:
  _read_packet(socket.get_packet())
 if socket == null or state == "idle": return
 if state=="joined" and not pending_chat.is_empty() and pending_chat_retries>0 and Time.get_ticks_msec()>=pending_chat_due:
  _publish(pending_chat.duplicate());pending_chat_retries-=1;pending_chat_due=Time.get_ticks_msec()+350
  if pending_chat_retries==0:pending_chat.clear()
 if ping_timer >= 15.0:
  socket.send(PackedByteArray([0xC0,0x00]),WebSocketPeer.WRITE_MODE_BINARY);ping_timer = 0.0
 if subscribed and state in ["waiting", "joined"] and heartbeat >= 1.0:
  heartbeat = 0.0
  if mode == "create": _publish({"kind":"host"})
  elif state == "waiting": _publish({"kind":"hello"})
 if state == "joined" and infection!=null:infection.tick(delta)
 if state == "joined" and send_timer >= .125 and is_instance_valid(game.player):
  send_timer = 0.0
  var p: Vector3 = game.player.global_position
  var l: Vector3 = game.targets[0].global_position
  var r: Vector3 = game.targets[1].global_position
  _publish({"kind":"state", "character":game.active_character, "monster_mode":game.monster_mode, "ragdoll":game.ragdoll.positions() if is_instance_valid(game.ragdoll) else [], "rag_cut":game.ragdoll.cut_height if is_instance_valid(game.ragdoll) else -1.0,"rag_sever":game.ragdoll.severed_mask if is_instance_valid(game.ragdoll) else 0, "round":game.round_id, "nick":game.profile_name, "color":game.cosmetic_color, "accessory":game.cosmetic_accessory, "position":[p.x,p.y,p.z], "yaw":game.player.rotation.y, "left":[l.x,l.y,l.z], "right":[r.x,r.y,r.z], "face": game.voice_face.facial_level if is_instance_valid(game.voice_face) else 0.0, "grabs": game.serialize_grabs(), "damage":game.missing_parts, "dead":game.is_dead, "life":game.life_number})
 if state == "joined":
  var now := Time.get_ticks_msec() / 1000.0
  for id in peer_seen.keys():
   if now - peer_seen[id] > 15.0: _remove_peer(id)
  if mode == "create":
   for id in admitted.keys():
    if now - admitted[id] > 15.0: admitted.erase(id)
  if mode == "join" and host_seen > 0.0 and now - host_seen > 15.0:
   leave(false);room_closed.emit(tr("O criador saiu ou a conexão foi perdida."))

func _remaining(n: int) -> PackedByteArray:
 var b := PackedByteArray()
 while true:
  var digit := n % 128
  n = n / 128
  if n > 0: digit |= 128
  b.append(digit)
  if n == 0: return b
 return b

func _str(s: String) -> PackedByteArray:
 var bytes := s.to_utf8_buffer()
 var result := PackedByteArray([(bytes.size() >> 8) & 255,bytes.size() & 255])
 result.append_array(bytes)
 return result

func _packet(header: int, body: PackedByteArray) -> void:
 var packet := PackedByteArray([header])
 packet.append_array(_remaining(body.size()));packet.append_array(body)
 socket.send(packet,WebSocketPeer.WRITE_MODE_BINARY)

func _send_connect() -> void:
 var p := _str("MQTT")
 p.append_array(PackedByteArray([4,2,0,30])) # MQTT 3.1.1, clean session, keepalive.
 p.append_array(_str("gm-"+uid))
 _packet(0x10,p)

func _subscribe() -> void:
 var p := PackedByteArray([0,1]);p.append_array(_str(topic));p.append(0)
 _packet(0x82,p)

func _publish(message: Dictionary) -> void:
 if not socket or socket.get_ready_state() != WebSocketPeer.STATE_OPEN: return
 message["id"] = uid
 var p := _str(topic);p.append_array(JSON.stringify(message).to_utf8_buffer())
 _packet(0x30,p)

func send_tear(target: String, part: String, life: int) -> void:
 if state == "joined": _publish({"kind":"tear", "target":target, "part":part, "life":life})

func send_strands(a:Vector3,b:Vector3)->void:
 if state=="joined":_publish({"kind":"strands","a":[a.x,a.y,a.z],"b":[b.x,b.y,b.z]})

func send_split(target:String,life:int,pos:Vector3,yaw:float,color:int)->void:
 if state=="joined":_publish({"kind":"split","target":target,"life":life,"pos":[pos.x,pos.y,pos.z],"yaw":yaw,"color":color,"round":game.round_id})

func send_throw(target: String, velocity: Vector3, life: int) -> void:
 if state == "joined": _publish({"kind":"throw", "target":target, "velocity":[velocity.x,velocity.y,velocity.z], "life":life})

func send_chainsaw_hit(target:String,part:String,point:Vector3,life:int,final:bool)->void:
 if state=="joined":_publish({"kind":"chainsaw", "target":target,"part":part,"point":[point.x,point.y,point.z],"life":life,"final":final,"round":game.round_id})

func send_blood(position: Vector3) -> void:
 if state == "joined": _publish({"kind":"blood", "position":[position.x,position.y,position.z]})

func send_voice(audio: PackedByteArray) -> void:
 if state != "joined" or audio.size()!=800:return
 # TCP is reliable but can queue stale speech; do not add to a congested socket.
 if socket==null or socket.get_ready_state()!=WebSocketPeer.STATE_OPEN:return
 if socket.get_current_outbound_buffered_amount()>8192:return
 voice_sequence+=1
 _publish({"kind":"voice", "seq":voice_sequence, "tick":Time.get_ticks_msec(), "audio":Marshalls.raw_to_base64(audio)})

func send_chat(message:String)->bool:
 var clean:=preload("res://chat_text.gd").clean(message)
 if state!="joined" or clean.is_empty():return false
 var now:=Time.get_ticks_msec()
 if chat_last_sent>0 and now-chat_last_sent<1200:return false
 chat_last_sent=now;chat_sequence+=1
 pending_chat={"kind":"chat","text":clean,"seq":chat_sequence}
 pending_chat_retries=2;pending_chat_due=now+350
 _publish(pending_chat.duplicate())
 if is_instance_valid(game) and game.has_method("show_local_chat"):game.show_local_chat(clean)
 return true

func _read_packet(p: PackedByteArray) -> void:
 if p.size() < 2: return
 var typ := p[0] >> 4
 var idx := 1; var multiplier := 1; var length := 0
 while idx < p.size():
  var x := int(p[idx]);idx += 1;length += (x & 127) * multiplier; multiplier *= 128
  if (x & 128) == 0: break
  if multiplier > 2097152: return
 if idx+length > p.size(): return
 if typ == 2:
  if length < 2 or p[idx+1] != 0: leave(false);room_error.emit(tr("Servidor de teste recusou a conexão."));return
  _subscribe();state="subscribing"
 elif typ == 9:
  if length < 3 or p[idx+2] == 128: leave(false);room_error.emit(tr("Não foi possível entrar no canal."));return
  subscribed=true;state="waiting";heartbeat=1.0
  if mode == "create":
   state="joined";_publish({"kind":"host"});room_ready.emit(room_code)
  elif mode=="sandbox":
   state="joined";room_ready.emit(room_code)
 elif typ == 3:
  if length < 2: return
  var topic_len := (int(p[idx])<<8) | int(p[idx+1]);idx += 2
  if idx + topic_len > p.size(): return
  var received_topic := p.slice(idx,idx+topic_len).get_string_from_utf8();idx += topic_len
  if received_topic != topic: return
  if (p[0] & 0x06) != 0: idx += 2
  if idx > p.size(): return
  var data = JSON.parse_string(p.slice(idx).get_string_from_utf8())
  if data is Dictionary: _on_message(data)

func _on_message(d: Dictionary) -> void:
 var id := str(d.get("id",""))
 if id == "" or id == uid or id.length()>32: return
 var kind := str(d.get("kind",""))
 var now := Time.get_ticks_msec()/1000.0
 if kind == "host":
  if mode == "join" and (host_id == "" or id == host_id):
   host_id=id;host_seen=now
   if state == "waiting": _publish({"kind":"hello"})
 elif kind == "hello":
  if mode == "create" and state == "joined":
   if admitted.has(id) or admitted.size() < max_players - 1:
    admitted[id]=now
    _publish({"kind":"welcome","target":id,"map":room_map})
   else:
    _publish({"kind":"full","target":id})
 elif kind == "welcome" and mode == "join" and state == "waiting":
  if str(d.get("target","")) == uid and (host_id == "" or id == host_id):
   var chosen:=str(d.get("map",""))
   if chosen not in ["base","backrooms"]:return
   room_map=chosen;game.selected_map=chosen
   host_id=id;host_seen=now;state="joined";room_ready.emit(room_code)
 elif kind == "full" and mode == "join" and state == "waiting":
  if str(d.get("target","")) == uid and (host_id == "" or id == host_id):
   leave(false);room_error.emit(tr("Servidor cheio. Tente outro."))
 elif kind == "host_left" and mode == "join" and id == host_id:
  leave(false);room_closed.emit(tr("O criador encerrou a sala."))
 elif kind == "left":
  admitted.erase(id);_remove_peer(id)
 elif kind == "chainsaw" and state=="joined":
  if not peers.has(id) or not is_instance_valid(game.player):return
  var attacker=peers[id]
  if attacker.character!="grinner" or attacker.monster_mode!="chainsaw" or not attacker.has_state:return
  if int(d.get("round",-1))!=game.round_id:return
  var target:=str(d.get("target",""));var part:=str(d.get("part",""));var point=_vector(d.get("point"));var life=int(d.get("life",-1));var final:=bool(d.get("final",false))
  if part not in ["head","arm_l","arm_r","body"] or point==null or point.distance_to(attacker.goals[1].global_position)>3.0:return
  var pos:=Vector3.ZERO;var yaw:=0.0;var color:=0;var victim
  if target==uid:
   if game.active_character!="normal" or game.is_dead or life!=game.life_number:return
   pos=game.player.global_position;yaw=game.player.rotation.y;color=game.cosmetic_color
  elif peers.has(target):
   victim=peers[target]
   if victim.character!="normal" or victim.life_number!=life:return
   pos=victim.global_position;yaw=victim.rotation.y;color=clampi(victim.cosmetic_color,0,9)
  else:return
  if point.distance_to(pos+Vector3(0,.8,0))>2.0:return
  preload("res://injury_visual.gd").saw_blood(game,point)
  if target==uid:game.receive_chainsaw_hit(part,point,life,final)
 elif kind == "tear" and state == "joined":
  if str(d.get("target","")) != uid or not peers.has(id) or peers[id].character != "grinner": return
  game.receive_tear(str(d.get("part","")),int(d.get("life",-1)))
 elif kind == "strands" and state=="joined":
  if not peers.has(id):return
  var a=_vector(d.get("a"));var b=_vector(d.get("b"))
  if a==null or b==null or a.distance_to(b)>5.0 or a.distance_to(peers[id].global_position)>5.0:return
  preload("res://injury_visual.gd").strands(game,a,b)
 elif kind == "split" and state=="joined":
  if not peers.has(id) or peers[id].character!="grinner" or peers[id].monster_mode!="grab" or int(d.get("round",-2))!=game.round_id:return
  var target:=str(d.get("target",""));var pos=_vector(d.get("pos"));var yaw=d.get("yaw");var life=int(d.get("life",-1));var color=int(d.get("color",0))
  if pos==null or typeof(yaw) not in [TYPE_INT,TYPE_FLOAT] or not is_finite(float(yaw)) or pos.distance_to(peers[id].global_position)>5.0:return
  if target==uid:
   if life!=game.life_number or game.active_character!="normal" or game.is_dead or pos.distance_to(game.player.global_position)>3.0:return
   preload("res://split_visual.gd").spawn(game,pos,float(yaw),clampi(color,0,9));game.receive_split(life)
  elif peers.has(target):
   var victim=peers[target]
   if victim.character!="normal" or victim.life_number!=life or victim.split_life==life or pos.distance_to(victim.global_position)>3.0:return
   preload("res://split_visual.gd").spawn(game,pos,float(yaw),clampi(color,0,9));victim.split_life=life;victim.avatar.visible=false
 elif kind == "throw" and state == "joined":
  if str(d.get("target","")) != uid or not peers.has(id) or peers[id].character != "grinner": return
  var velocity = _vector(d.get("velocity"))
  if velocity != null and int(d.get("life",-1)) == game.life_number: game.receive_throw(velocity.limit_length(18.0))
 elif kind == "blood" and state == "joined":
  if not peers.has(id): return
  var location = _vector(d.get("position"))
  if location != null and is_instance_valid(game.player) and location.distance_to(peers[id].global_position)<5.0:
   preload("res://injury_visual.gd").blood(game,location)
 elif kind == "voice" and state == "joined":
  if not peers.has(id) or not is_instance_valid(peers[id]): return
  if mode == "create" and not admitted.has(id): return
  var encoded = d.get("audio", "")
  if not encoded is String or encoded.length() > 1600: return
  var bytes := Marshalls.base64_to_raw(encoded)
  var seq=d.get("seq",-1);var tick=d.get("tick",-1)
  if typeof(seq) not in [TYPE_FLOAT,TYPE_INT] or typeof(tick) not in [TYPE_FLOAT,TYPE_INT]:return
  if not is_finite(float(seq)) or not is_finite(float(tick)):return
  if int(seq)<0 or int(tick)<0:return
  if bytes.size()==800: peers[id].play_voice(bytes,int(seq),int(tick))

 elif kind == "chat" and state=="joined":
  if not peers.has(id) or not is_instance_valid(peers[id]):return
  if mode=="create" and not admitted.has(id):return
  var seq=d.get("seq",-1)
  if typeof(seq) not in [TYPE_INT,TYPE_FLOAT] or not is_finite(float(seq)) or int(seq)<0:return
  if chat_last_sequence.has(id) and int(seq)<=int(chat_last_sequence[id]):return
  var raw=d.get("text","")
  if not raw is String or raw.length()>192:return
  var clean:=preload("res://chat_text.gd").clean(raw)
  if clean.is_empty():return
  var tm:=Time.get_ticks_msec()
  if chat_last_received.has(id) and tm-int(chat_last_received[id])<900:return
  chat_last_received[id]=tm;chat_last_sequence[id]=int(seq);peers[id].show_chat(clean)
 elif kind == "infection" and state == "joined" and mode == "join" and id == host_id:
  var rid=d.get("round",-1);var revision=d.get("rev",-1);var phase=str(d.get("phase",""));var roster=d.get("roles",{})
  if typeof(rid) not in [TYPE_INT,TYPE_FLOAT] or typeof(revision) not in [TYPE_INT,TYPE_FLOAT] or not roster is Dictionary:return
  if int(rid)<0 or int(rid)>1000000 or int(revision)<0 or int(revision)>10000000 or phase not in ["waiting","active","reset"] or roster.size()>10:return
  var my_role=str(roster.get(uid,""))
  if my_role not in ["normal","grinner"]:return
  game.receive_round(int(rid),int(revision),phase,my_role)
 elif kind == "state" and state == "joined":
  if mode == "create":
   if not admitted.has(id): return
   admitted[id]=now
  var character := str(d.get("character",""))
  if character not in ["normal","grinner"]: return
  var pos = _vector(d.get("position"));var l = _vector(d.get("left"));var r = _vector(d.get("right"))
  if pos == null or l == null or r == null: return
  var yaw = d.get("yaw")
  if typeof(yaw) not in [TYPE_FLOAT,TYPE_INT] or not is_finite(float(yaw)) or absf(float(yaw))>100000.0: return
  if not peers.has(id):
   if (mode!="sandbox" and peers.size() >= 9) or not is_instance_valid(game.player): return
   var remote := preload("res://remote_avatar.gd").new()
   remote.name="JogadorOnline_"+id
   game.add_child(remote)
   remote.configure(character)
   peers[id] = remote
  peer_seen[id] = now
  peers[id].set_state(character,pos,float(yaw),l,r)
  peers[id].set_nickname(d.get("nick","Jogador"))
  peers[id].set_monster_mode(str(d.get("monster_mode","grab")))
  if infection!=null and mode=="create":infection.observe_state(id,d)
  var color=d.get("color",0);var accessory=d.get("accessory",0)
  if typeof(color) in [TYPE_FLOAT,TYPE_INT] and typeof(accessory) in [TYPE_FLOAT,TYPE_INT]:
   peers[id].set_cosmetics(clampi(int(color),0,9),clampi(int(accessory),0,preload("res://cosmetics.gd").NAMES.size()-1))
  var level = d.get("face", 0.0)
  if typeof(level) in [TYPE_INT,TYPE_FLOAT] and is_finite(float(level)):
   peers[id].face_level = clampf(float(level),0.0,1.0)
  peers[id].grabs = d.get("grabs", []) if d.get("grabs", []) is Array else []
  var mask = d.get("damage",0)
  var life = d.get("life",0)
  if typeof(mask) in [TYPE_INT,TYPE_FLOAT] and typeof(life) in [TYPE_INT,TYPE_FLOAT]:
   peers[id].set_injury(clampi(int(mask),0,7),bool(d.get("dead",false)),clampi(int(life),0,1000000))
  var rag=d.get("ragdoll",[])
  var cut=d.get("rag_cut",-1);var sever=d.get("rag_sever",0)
  if typeof(cut) not in [TYPE_INT,TYPE_FLOAT] or not is_finite(float(cut)) or float(cut)>.0 and (float(cut)<.3 or float(cut)>1.15):cut=-1
  if typeof(sever) not in [TYPE_INT,TYPE_FLOAT]:sever=0
  if rag is Array:peers[id].set_ragdoll(rag,float(cut),clampi(int(sever),0,7))
  if character == "grinner": game.inspect_grab_commands(peers[id].grabs)

func _vector(v):
 if not v is Array or v.size()!=3: return null
 for n in v:
  if typeof(n) not in [TYPE_FLOAT,TYPE_INT] or not is_finite(float(n)) or absf(float(n))>1000.0: return null
 return Vector3(float(v[0]),float(v[1]),float(v[2]))

func _remove_peer(id: String) -> void:
 if peers.has(id):
  if is_instance_valid(peers[id]): peers[id].queue_free()
  peers.erase(id)
 peer_seen.erase(id)
