extends "res://remote_avatar.gd"
# A stationary practice target; it is local and never published to the broker.
var spawn_position:=Vector3.ZERO
var velocity:=Vector3.ZERO
var respawn_due:=0
func spawn_at(p:Vector3)->void:
 spawn_position=p
 set_state("normal",p,0,p+Vector3(-.35,.8,-.6),p+Vector3(.35,.8,-.6))
 global_position=p
 set_cosmetics(0,0)
func sandbox_throw(v:Vector3)->void:
 desired_position=global_position;velocity=v.limit_length(18.0)
func sandbox_chainsaw(part:String,point:Vector3,final:bool)->void:
 if not final or is_dead:return
 if part in ["arm_l","arm_r","head"]:
  var bit:int=preload("res://injury_visual.gd").mask_for(part)
  set_injury(missing_parts|bit,part=="head",life_number)
 else:
  preload("res://split_visual.gd").spawn(get_parent(),global_position,rotation.y,0)
  if is_instance_valid(avatar):avatar.visible=false
  is_dead=true;split_life=life_number
func _process(delta:float)->void:
 if is_dead:
  if respawn_due==0:respawn_due=Time.get_ticks_msec()+7000
  elif Time.get_ticks_msec()>=respawn_due:
   respawn_due=0;velocity=Vector3.ZERO;split_life=-1
   set_injury(0,false,life_number+1)
   if is_instance_valid(avatar):avatar.visible=true
   spawn_at(spawn_position)
 else:
  respawn_due=0
  if grab_visual_active:
   desired_position=global_position;velocity=Vector3.ZERO
  elif velocity.length_squared()>.0001:
   velocity.y-=9.8*delta
   var displacement:=velocity*delta
   desired_position+=displacement
   if desired_position.y<.04:desired_position.y=.04;velocity.y=0;velocity.x=move_toward(velocity.x,0.0,4*delta);velocity.z=move_toward(velocity.z,0.0,4*delta)
   desired_left+=displacement;desired_right+=displacement
 super._process(delta)
