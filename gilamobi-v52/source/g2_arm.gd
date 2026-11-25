extends RefCounted
# Explicitly estimated 3DoF arm; 6DoF sources bypass this trajectory.
var desired := Vector3.ZERO
var target := Vector3.ZERO
var normal := Vector3.UP
var contact := false
var valid := false
var ready := false
var plant := Vector3.ZERO
var last_local := Vector3.ZERO
var last_direction := Vector3.FORWARD
var peak := 0.0
var trough := 0.0
var blocked := false
var grace := 0.12
var neutral := 0.0
var calibrated := false
var orientation := Basis.IDENTITY
var radius := 0.16
var estimated := true
var discontinuity := false
var previous_body := Vector3.ZERO
const Motion = preload("res://g2_motion.gd")

func reset() -> void:
    contact = false
    ready = false
    grace = 0.12
    blocked = false
    discontinuity = false

func update(dt: float, origin: Transform3D, eye: Vector3, raw: Transform3D, is_estimated: bool, side: float, scale: float, space: PhysicsDirectSpaceState3D, exclusions: Array[RID]) -> void:
    discontinuity = false
    estimated = is_estimated
    orientation = origin.basis.orthonormalized() * raw.basis.orthonormalized()
    var direction := -orientation.z
    var elevation := atan2(-direction.y,maxf(Vector2(direction.x,direction.z).length(),0.001))
    if not calibrated:
        neutral = clampf(elevation,-0.6,0.5)
        calibrated = true
    var down := clampf(0.55 + 2.0*(elevation-neutral),-1.0,2.3)
    if estimated:
        var heading := Vector3(direction.x,0,direction.z).normalized()
        if heading.length_squared() < 0.1: heading = Vector3.FORWARD
        var right := Vector3(-heading.z,0,heading.x)
        var shoulder := eye + right * side * 0.22 * scale - Vector3.UP * 0.25 * scale
        desired = shoulder + heading * cos(down)*0.85*scale - Vector3.UP*sin(down)*1.30*scale
    else:
        desired = origin * raw.origin
    var local := desired - origin.origin
    if ready and (local.distance_to(last_local)>0.65*scale or direction.dot(last_direction)<cos(1.2) or previous_body.distance_to(origin.origin)>2.0*scale or dt>0.1):
        reset()
        discontinuity = true
    if not ready:
        target = desired
        last_local = local
        ready = true
    grace = maxf(0.0,grace-dt)
    var hand_delta := local-last_local
    var lifting := hand_delta.dot(normal)>0.005*scale if not estimated else down<peak-0.16
    if contact:
        peak = maxf(peak,down)
        var away := (desired-plant).dot(normal)>0.12*scale
        if lifting or away or desired.distance_to(plant)>1.3*scale:
            contact = false
            blocked = estimated
            trough = down
    if blocked:
        trough = minf(trough,down)
        if down>trough+0.12: blocked = false
    if not contact:
        var result := Motion.sweep(space,target,desired,radius*scale,exclusions)
        target = result.position
        normal = result.normal
        contact = result.contact and grace<=0.0 and not blocked and not lifting
        if contact:
            plant = target
            peak = down
    else:
        var result := Motion.sweep(space,plant,plant,radius*scale+0.006,exclusions)
        # The slightly expanded sphere verifies that the supporting surface still exists.
        if not result.contact: contact = false
        target = plant
    last_local = local
    last_direction = direction
    previous_body = origin.origin

func after_move(shift: Vector3, space: PhysicsDirectSpaceState3D, exclusions: Array[RID], scale: float) -> void:
    desired += shift
    previous_body += shift
    if not contact:
        target = Motion.sweep(space,target,target+shift,radius*scale,exclusions).position
