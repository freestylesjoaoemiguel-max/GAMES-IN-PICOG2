extends Node3D
# Four real rigid bodies joined at the neck/shoulders. Remote copies receive
# transforms over the room state instead of simulating competing physics.
var game:Node
var authoritative:=false
var bodies:Array[Node3D]=[]
var time_left:=7.0
var dead:=false
var impact_velocity:=Vector3.ZERO
var mask:=0
var color:=0
var cut_height:=-1.0
var severed_mask:=0
var joints:Dictionary={}
const PIVOTS:=[Vector3(0,.78,0),Vector3(0,1.34,0),Vector3(-.46,1.0,0),Vector3(.46,1.0,0)]
const PARTS:=["body","head","arm_l","arm_r"]

func make_visual(part:String)->Node3D:
 if part=="body":
  var root:=Node3D.new()
  var avatar=load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D
  avatar.rotation.y=PI;avatar.scale=Vector3.ONE*1.45;root.add_child(avatar)
  var rig=avatar.get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
  preload("res://cosmetics.gd").paint(rig.get_node("Gilla"),color)
  preload("res://injury_visual.gd").update_rig(rig,7)
  return root
 return preload("res://injury_visual.gd").make_piece(part,color)

func setup(origin:Vector3,yaw:float,injuries:int,skin:int,impulse:Vector3,simulate:bool)->void:
 authoritative=simulate;mask=injuries;color=skin;impact_velocity=impulse.limit_length(18.0)
 var basis:=Basis(Vector3.UP,yaw)
 for i in 4:
  var body:Node3D
  if simulate:
   var rb=preload("res://ragdoll_body.gd").new();rb.ragdoll=self;rb.mass=3.0 if i==0 else .45
   rb.collision_layer=0;rb.collision_mask=1;rb.contact_monitor=i==0;rb.max_contacts_reported=8 if i==0 else 0
   rb.linear_damp=.15;rb.angular_damp=.35;body=rb
   var cs:=CollisionShape3D.new();var shape:=CapsuleShape3D.new()
   shape.radius=.25 if i==0 else (.18 if i==1 else .12)
   shape.height=.75 if i==0 else (.36 if i==1 else .58)
   cs.shape=shape;body.add_child(cs)
  else:body=Node3D.new()
  body.name=PARTS[i];add_child(body);body.global_position=origin+basis*PIVOTS[i];body.rotation.y=yaw;bodies.append(body)
  if simulate and is_instance_valid(game) and is_instance_valid(game.player):
   # The invisible first-person capsule follows the torso. Without this
   # exception the ragdoll collides with its own capsule and explodes away.
   (body as RigidBody3D).add_collision_exception_with(game.player)
  var missing:=i>0 and (mask & (1 if i==1 else (2 if i==2 else 4)))!=0
  if not missing:
   var model:=make_visual(PARTS[i])
   if is_instance_valid(model):
    body.add_child(model);model.position=-PIVOTS[i]
    # The local camera is inside the head; hide only that head locally.
    if simulate and i==1:model.visible=false
 if simulate:
  for i in range(1,4):
   if i>1 and (mask & (2 if i==2 else 4))!=0:continue
   var joint:=PinJoint3D.new();add_child(joint)
   var pivot:Vector3=Vector3(0,1.18,0) if i==1 else Vector3(-.30 if i==2 else .30,1.03,0)
   joint.global_position=origin+basis*pivot
   joint.node_a=joint.get_path_to(bodies[0]);joint.node_b=joint.get_path_to(bodies[i]);joints[i]=joint
  for i in 4:
   var rb:=bodies[i] as RigidBody3D
   rb.linear_velocity=impulse
   rb.angular_velocity=Vector3(1.8 if i==0 else 2.4, .5, (-1 if i==2 else 1)*1.3)

func sever_part(part:String)->void:
 var index:=PARTS.find(part)
 if index<1 or index>=bodies.size():return
 var bit:=1 if index==1 else (2 if index==2 else 4)
 if (severed_mask & bit)!=0:return
 severed_mask|=bit;mask|=bit
 if joints.has(index):
  var joint=joints[index]
  if is_instance_valid(joint):joint.queue_free()
  joints.erase(index)
 # If an observer joined after the injury, recreate the detached part.
 var has_model:=false
 for c in bodies[index].get_children():
  if c is Node3D and not c is CollisionShape3D:has_model=true
 if not has_model:
  var model:=make_visual(part)
  if is_instance_valid(model):bodies[index].add_child(model);model.position=-PIVOTS[index]
 if authoritative and bodies[index] is RigidBody3D:
  var rb:=bodies[index] as RigidBody3D;rb.linear_velocity+=Vector3(-.7 if index==2 else .7,1.0,0)

func apply_severed(bits:int)->void:
 for item in [[1,"head"],[2,"arm_l"],[4,"arm_r"]]:
  if (bits & int(item[0]))!=0:sever_part(item[1])

func cut_torso(height:float)->void:
 if cut_height>=0.0 or bodies.size()<4:return
 cut_height=clampf(height,.3,1.15)
 var old_model:Node3D
 for c in bodies[0].get_children():
  if c is Node3D and not c is CollisionShape3D:old_model=c;break
 if is_instance_valid(old_model):old_model.queue_free()
 var upper:=make_visual("body")
 if not is_instance_valid(upper):return
 var upper_rig=upper.get_node("Gilla_PlayerModel/Gilla_Rig/Skeleton3D") if upper.has_node("Gilla_PlayerModel/Gilla_Rig/Skeleton3D") else upper.get_child(0).get_node("Gilla_Rig/Skeleton3D")
 var upper_mesh=upper_rig.get_node("Gilla") as MeshInstance3D
 upper_mesh.mesh=preload("res://saw_split_visual.gd").filtered(upper_mesh.mesh,upper_mesh.skin,upper_rig,1,cut_height)
 bodies[0].add_child(upper);upper.position=-PIVOTS[0]
 var lower_body:Node3D
 if authoritative:
  var rb=preload("res://ragdoll_body.gd").new();rb.ragdoll=self;rb.mass=1.5;rb.collision_layer=0;rb.collision_mask=1;rb.linear_damp=.15;rb.angular_damp=.35
  var cs:=CollisionShape3D.new();var shape:=SphereShape3D.new();shape.radius=.23;cs.shape=shape;rb.add_child(cs);lower_body=rb
 else:lower_body=Node3D.new()
 lower_body.name="parte_inferior";add_child(lower_body);lower_body.global_transform=bodies[0].global_transform;bodies.append(lower_body)
 if authoritative and is_instance_valid(game) and is_instance_valid(game.player):(lower_body as RigidBody3D).add_collision_exception_with(game.player)
 var lower:=make_visual("body");var rig=lower.get_child(0).get_node("Gilla_Rig/Skeleton3D") as Skeleton3D
 var mesh=rig.get_node("Gilla") as MeshInstance3D
 mesh.mesh=preload("res://saw_split_visual.gd").filtered(mesh.mesh,mesh.skin,rig,-1,cut_height)
 lower_body.add_child(lower);lower.position=-PIVOTS[0]
 for model in [upper,lower]:
  var wound:=MeshInstance3D.new();var flesh:=SphereMesh.new();flesh.radius=.5;flesh.height=1;wound.mesh=flesh;wound.position=Vector3(0,cut_height,0);wound.scale=Vector3(.42,.045,.31);wound.material_override=preload("res://injury_visual.gd").flesh_material();model.add_child(wound)
 if authoritative:
  (lower_body as RigidBody3D).linear_velocity=(bodies[0] as RigidBody3D).linear_velocity+Vector3(0,-.6,0)

func strong_impact()->void:
 if authoritative and not dead and time_left>0 and is_instance_valid(game) and not game.is_dead:
  dead=true;game.die("ESMAGADO PELO IMPACTO")

func positions()->Array:
 var output:=[]
 for b in bodies:
  var p:=b.global_position;var q:=b.global_basis.get_rotation_quaternion().normalized()
  output.append([p.x,p.y,p.z,q.x,q.y,q.z,q.w])
 return output

func apply_positions(packet:Array)->void:
 if authoritative or packet.size() not in [4,5] or packet.size()!=bodies.size():return
 for i in packet.size():
  var a=packet[i]
  if not a is Array or a.size()!=7:continue
  var valid:=true
  for value in a:
   if typeof(value) not in [TYPE_INT,TYPE_FLOAT] or not is_finite(float(value)) or absf(float(value))>1000:valid=false
  if not valid:continue
  var q:=Quaternion(float(a[3]),float(a[4]),float(a[5]),float(a[6]))
  if q.length_squared()<.5 or q.length_squared()>1.5:continue
  bodies[i].global_position=Vector3(float(a[0]),float(a[1]),float(a[2]));bodies[i].global_basis=Basis(q.normalized())
