extends RefCounted
# Host-authoritative infection roster; snapshots are repeated to tolerate
# packet loss. This builds on the game's experimental public MQTT transport.
var net:Node
var round_id:=0
var revision:=0
var phase:="waiting"
var roles:={}
var reports:={}
var reset_timer:=0.0
var broadcast_timer:=0.0

func _init(n:Node)->void:net=n

func participants()->Array:
 var ids:=[net.uid]
 for id in net.peers:
  if net.admitted.has(id) and is_instance_valid(net.peers[id]):ids.append(id)
 ids.sort()
 return ids

func snapshot()->void:
 if net.mode!="create" or net.state!="joined":return
 if roles.has(net.uid):net.game.receive_round(round_id,revision,phase,str(roles[net.uid]))
 net._publish({"kind":"infection", "round":round_id, "rev":revision, "phase":phase, "roles":roles})
 broadcast_timer=0.0

func start_round(ids:Array)->void:
 round_id+=1;revision+=1;phase="active";reset_timer=0.0;reports.clear();roles.clear()
 for id in ids:roles[id]="normal"
 roles[ids[randi_range(0,ids.size()-1)]]="grinner"
 snapshot()

func wait_for_players(ids:Array)->void:
 if phase!="waiting":round_id+=1
 phase="waiting";reset_timer=0;reports.clear();roles.clear()
 for id in ids:roles[id]="normal"
 revision+=1;snapshot()

func observe_state(id:String,d:Dictionary)->void:
 if net.mode!="create" or not roles.has(id):return
 var rid=d.get("round",-1)
 if typeof(rid) not in [TYPE_INT,TYPE_FLOAT]:return
 reports[id]={"round":int(rid),"dead":bool(d.get("dead",false)),"character":str(d.get("character",""))}

func tick(delta:float)->void:
 if net.mode!="create" or net.state!="joined" or not is_instance_valid(net.game.player) or not is_instance_valid(net.game.ui) or net.game.targets.size()<2:return
 broadcast_timer+=delta
 var ids:=participants()
 var changed:=false
 if ids.size()<2:
  if phase!="waiting" or roles.size()!=ids.size() or not roles.has(net.uid) or str(roles.get(net.uid,""))!="normal":wait_for_players(ids)
 elif phase=="waiting":
  start_round(ids)
 else:
  for id in roles.keys():
   if id not in ids:roles.erase(id);reports.erase(id);changed=true
  for id in ids:
   if not roles.has(id):roles[id]="normal";changed=true
  if phase=="active":
   for id in roles.keys():
    if roles[id]!="normal":continue
    var dead:=false
    if id==net.uid:dead=net.game.round_id==round_id and net.game.is_dead and net.game.active_character=="normal"
    elif reports.has(id):
     var report:Dictionary=reports[id]
     dead=int(report.round)==round_id and bool(report.dead) and str(report.character)=="normal"
    if dead:roles[id]="grinner";changed=true
   var monsters:=0;var survivors:=0
   for id in roles:
    if roles[id]=="grinner":monsters+=1
    else:survivors+=1
   if monsters==0:
    start_round(ids);return
   if survivors==0:
    phase="reset";reset_timer=4.0;changed=true
  elif phase=="reset":
   reset_timer-=delta
   if reset_timer<=0.0:start_round(ids);return
  if changed:revision+=1;snapshot()
 if broadcast_timer>=.5:snapshot()
