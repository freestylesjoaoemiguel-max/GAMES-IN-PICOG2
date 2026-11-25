extends "res://network.gd"
# Public, unauthenticated discovery. No permanent storage or trusted ownership.
signal directory_changed
var entries := {}
var refresh_timer := 0.0
var retry_timer := 0.0
const DIRECTORY_TOPIC := "gilamobi/teste/v19/servidores"

func start() -> void:
 if state != "idle": return
 uid = Crypto.new().generate_random_bytes(8).hex_encode()
 topic = DIRECTORY_TOPIC
 socket = WebSocketPeer.new()
 socket.supported_protocols = PackedStringArray(["mqtt"])
 if socket.connect_to_url(BROKER) != OK:
  socket = null;state = "idle";return
 state = "connecting"
 ping_timer = 0.0;refresh_timer = 0.0;subscribed = false

func _process(delta: float) -> void:
 if state == "idle" or socket == null: return
 socket.poll();ping_timer += delta;refresh_timer += delta
 var ready := socket.get_ready_state()
 if ready == WebSocketPeer.STATE_CLOSED:
  socket=null;state="idle";entries.clear();directory_changed.emit();return
 if ready == WebSocketPeer.STATE_OPEN and state == "connecting":
  _send_connect();state = "mqtt_connect"
 if ready != WebSocketPeer.STATE_OPEN: return
 while socket != null and socket.get_available_packet_count() > 0:
  _read_packet(socket.get_packet())
 if socket == null: return
 if ping_timer >= 15.0:
  socket.send(PackedByteArray([0xc0,0]),WebSocketPeer.WRITE_MODE_BINARY);ping_timer=0.0
 if refresh_timer >= 2.0 and subscribed:
  refresh_timer = 0.0
  var net = game.network
  if net.state == "joined" and net.mode == "create" and net.public_room:
   _publish({"kind":"public", "name":net.server_name, "code":net.room_code, "count":1+net.admitted.size(), "capacity":net.max_players,"map":net.room_map})
  var now := Time.get_ticks_msec()/1000.0
  var removed := false
  for key in entries.keys():
   if now - float(entries[key].last_seen) > 8.0: entries.erase(key);removed=true
  if removed: directory_changed.emit()

func _read_packet(p: PackedByteArray) -> void:
 if p.size() < 2:return
 var typ := p[0] >> 4;var idx := 1;var multiplier := 1;var length := 0
 while idx < p.size():
  var x := int(p[idx]);idx+=1;length+=(x&127)*multiplier;multiplier*=128
  if (x&128)==0:break
  if multiplier>2097152:return
 if idx+length>p.size():return
 if typ==2:
  if length<2 or p[idx+1]!=0:socket.close();socket=null;state="idle";return
  _subscribe();state="subscribing"
 elif typ==9:
  if length<3 or p[idx+2]==128:socket.close();socket=null;state="idle";return
  subscribed=true;state="ready";directory_changed.emit()
 elif typ==3:
  if length<2:return
  var tlen := (int(p[idx])<<8)|int(p[idx+1]);idx+=2
  if idx+tlen>p.size():return
  var t := p.slice(idx,idx+tlen).get_string_from_utf8();idx+=tlen
  if t!=topic:return
  if (p[0]&6)!=0:idx+=2
  if idx>p.size():return
  var data = JSON.parse_string(p.slice(idx).get_string_from_utf8())
  if not data is Dictionary:return
  if data.get("kind")=="offline":
   var id := str(data.get("id",""))
   if entries.has(id): entries.erase(id);directory_changed.emit()
   return
  if data.get("kind")!="public":return
  var code := str(data.get("code",""));var server_id := str(data.get("id",""));var name := str(data.get("name","")).strip_edges()
  var count := int(data.get("count",0));var capacity := int(data.get("capacity",0))
  if server_id.length()!=16 or code.length()!=6 or not code.is_valid_int() or name.is_empty() or name.length()>24 or capacity<2 or capacity>10 or count<1 or count>capacity:return
  var map_name:=str(data.get("map","base"))
  if map_name not in ["base","backrooms"]: return
  var changed := not entries.has(server_id)
  if not changed:
   var previous: Dictionary = entries[server_id]
   changed = previous.name != name or previous.code != code or int(previous.count) != count or int(previous.capacity) != capacity or str(previous.get("map","base")) != map_name
  entries[server_id] = {"name":name,"code":code,"count":count,"capacity":capacity,"map":map_name,"last_seen":Time.get_ticks_msec()/1000.0}
  if changed: directory_changed.emit()

func announce_stop(code: String) -> void:
 if subscribed and socket:
  _publish({"kind":"offline", "code":code})
 if entries.has(uid): entries.erase(uid);directory_changed.emit()

func is_gilabr_online() -> bool:
 for entry in entries.values():
  if str(entry.name).to_lower() == "gilabr": return true
 return false
