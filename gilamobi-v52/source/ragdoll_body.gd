extends RigidBody3D
var ragdoll:Node3D
var age:=0.0
func _integrate_forces(state:PhysicsDirectBodyState3D)->void:
 age+=state.step
 # Joint correction and spawning overlap can generate huge, non-physical
 # velocities. Damage uses the actual launch/gravity trajectory instead.
 state.linear_velocity=state.linear_velocity.limit_length(16.0)
 state.angular_velocity=state.angular_velocity.limit_length(8.0)
 if not is_instance_valid(ragdoll) or not ragdoll.authoritative:return
 var trajectory:Vector3=ragdoll.impact_velocity+Vector3(0,-9.8*state.step,0)
 for i in state.get_contact_count():
  var normal:Vector3=(state.transform.basis*state.get_contact_local_normal(i)).normalized()
  var impact:float=-trajectory.dot(normal)
  if age>.04 and impact>12.0:
   ragdoll.call_deferred("strong_impact")
  if impact>0:trajectory+=normal*impact
 ragdoll.impact_velocity=trajectory.limit_length(18.0)
