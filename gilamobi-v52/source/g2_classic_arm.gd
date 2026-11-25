extends "res://g2_arm.gd"
# v1.0.1: restore the supplied v52 VR hand-velocity impulses (main.gd).
# Tracking/roll remain WebXR. Swept spheres replace the old palm ray for safety.
var stroke:=Vector3.ZERO
var cooldown:=0.0

func reset() -> void:
    super.reset()
    stroke=Vector3.ZERO
    cooldown=0.0

static func reaction(movement:Vector3,surface_normal:Vector3,dt:float) -> Vector3:
    if dt<=0.0 or -movement.dot(surface_normal)/dt<0.22: return Vector3.ZERO
    return (-movement/dt*1.15).limit_length(5.0)

func update(dt:float, frame:Transform3D, eye:Vector3, raw:Transform3D, is_estimated:bool, side:float, scale:float, space:PhysicsDirectSpaceState3D, exclusions:Array[RID]) -> void:
    discontinuity=false
    estimated=is_estimated
    orientation=frame.basis.orthonormalized()*raw.basis.orthonormalized()
    var direction:Vector3=-orientation.z
    var elevation:=atan2(-direction.y,maxf(Vector2(direction.x,direction.z).length(),0.001))
    if not calibrated:
        neutral=clampf(elevation,-0.6,0.5)
        calibrated=true
    var down:=clampf(.55+2.0*(elevation-neutral),-1.0,2.3)
    var requested:Vector3=frame*raw.origin
    if estimated:
        var heading:=Vector3(direction.x,0,direction.z).normalized()
        if heading.length_squared()<.1:heading=Vector3.FORWARD
        var right:=Vector3(-heading.z,0,heading.x)
        requested=eye+right*side*.22*scale-Vector3.UP*.25*scale+heading*cos(down)*.85*scale-Vector3.UP*sin(down)*1.30*scale
    var local:Vector3=requested-frame.origin
    if ready and (local.distance_to(last_local)>.65*scale or direction.dot(last_direction)<cos(1.2) or previous_body.distance_to(frame.origin)>2.0*scale or dt>.5):
        reset()
        discontinuity=true
    if not ready:
        last_local=local
        desired=requested
        target=requested
        ready=true
    var previous:Vector3=desired
    # The old VR path limited incoming hand speed to 6 m/s before applying force.
    desired=desired.move_toward(requested,6.0*scale*maxf(dt,0.0))
    stroke=desired-previous
    cooldown=maxf(0.0,cooldown-dt)
    grace=maxf(0.0,grace-dt)
    var hit:=Motion.sweep(space,target,desired,radius*scale,exclusions)
    target=hit.position
    normal=hit.normal
    contact=hit.contact and grace<=0.0
    last_local=local
    last_direction=direction
    previous_body=frame.origin

func impulse(dt:float) -> Vector3:
    if not valid or not contact or grace>0 or cooldown>0: return Vector3.ZERO
    var push:=reaction(stroke,normal,dt)
    if not push.is_zero_approx():cooldown=.22
    return push
