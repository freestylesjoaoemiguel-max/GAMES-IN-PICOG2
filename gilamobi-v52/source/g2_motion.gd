extends RefCounted
# Contact averaging / 8 actual body velocity samples / release impulse ported
# from the supplied GMotion reference, NOT official Gorilla Tag source.
const HISTORY := 8
const RELEASE_THRESHOLD := 0.4
const RELEASE_GAIN := 1.1
const MAX_RELEASE := 4.0
const MAX_CONTACT := 8.0
const GRAVITY := 5.0
var samples: Array[Vector3] = []
var cursor := 0
var average := Vector3.ZERO
var supported := false
var released := false

func _init() -> void: reset()
func reset() -> void:
    samples.assign([Vector3.ZERO,Vector3.ZERO,Vector3.ZERO,Vector3.ZERO,Vector3.ZERO,Vector3.ZERO,Vector3.ZERO,Vector3.ZERO])
    cursor = 0
    average = Vector3.ZERO
    supported = false
    released = false

func apply(corrections: Array[Vector3], velocity: Vector3, dt: float, scale: float) -> Vector3:
    released = false
    if not corrections.is_empty():
        var sum := Vector3.ZERO
        for correction in corrections: sum += correction
        supported = true
        return (sum / (corrections.size() * maxf(dt,0.001))).limit_length(MAX_CONTACT * scale)
    if supported:
        supported = false
        released = true
        return (average * RELEASE_GAIN).limit_length(MAX_RELEASE * scale) if average.length() > RELEASE_THRESHOLD * scale else Vector3.ZERO
    return velocity

func record(displacement: Vector3, dt: float) -> void:
    cursor = (cursor + 1) % HISTORY
    var sample := displacement / maxf(dt,0.001)
    average += (sample - samples[cursor]) / HISTORY
    samples[cursor] = sample

# Swept sphere, initial overlap recovery and sliding. Uses actual level bodies.
# No plane-only floor approximation, no triangle skipping by endpoint raycasts.
static func sweep(space: PhysicsDirectSpaceState3D, from: Vector3, to: Vector3, radius: float, exclude: Array[RID]) -> Dictionary:
    var shape := SphereShape3D.new()
    shape.radius = radius
    var q := PhysicsShapeQueryParameters3D.new()
    q.shape = shape
    q.exclude = exclude
    q.collision_mask = 1
    q.margin = 0.003
    var pos := from
    var normal := Vector3.UP
    var touched := false
    for j in range(8):
        q.transform = Transform3D(Basis.IDENTITY,pos)
        q.motion = Vector3.ZERO
        var overlap := space.get_rest_info(q)
        if overlap.is_empty(): break
        normal = overlap.normal
        var depth := radius + 0.004 - (pos - overlap.point).dot(normal)
        if depth <= 0.0: break
        pos += normal * depth
        touched = true
    var remaining := to - pos
    for j in range(4):
        if remaining.length_squared() < 0.00000001: break
        q.transform.origin = pos
        q.motion = remaining
        var fractions := space.cast_motion(q)
        if fractions.size() < 2 or fractions[0] >= 1.0:
            pos += remaining
            break
        var safe := clampf(fractions[0],0.0,1.0)
        var unsafe := clampf(fractions[1]+0.001,0.0,1.0)
        q.transform.origin = pos + remaining * unsafe
        q.motion = Vector3.ZERO
        var hit := space.get_rest_info(q)
        normal = hit.normal if not hit.is_empty() else -remaining.normalized()
        pos += remaining * safe + normal * 0.004
        remaining = (remaining * (1.0-safe)).slide(normal)
        touched = true
    return {"position":pos,"contact":touched,"normal":normal}
