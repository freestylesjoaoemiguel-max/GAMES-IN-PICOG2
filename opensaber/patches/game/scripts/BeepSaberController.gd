extends XRController3D
class_name BeepSaberController

# G2 has orientation, not measured hand translation. Never mirror a missing hand.
@export var is_left: bool = false
@export var hand_reach: float = 0.45
@export var hand_offset: float = 0.18
@export var follow_smoothing: float = 0.35
var ax := false
var ax_last_frame := false
var by := false
var by_last_frame := false
var trigger := false
var trigger_last_frame := false
var movement_aabb := AABB()
var is_3dof := true
var head: XRCamera3D
var _rumble_until := 0
var _was_tracked := false

func _ready() -> void:
    is_left = str(name).to_lower().contains("left")
    set_tracker("left_hand" if is_left else "right_hand")
    set_pose_name("default")
    process_physics_priority = -10
    head = get_parent().get_node_or_null("XRCamera3D") as XRCamera3D
    reset_movement_aabb()

func _physics_process(_delta: float) -> void:
    # The supplied desktop simulator owns keyboard input outside immersive VR.
    if not vr.inVR:
        visible = true
        return
    ax_last_frame = ax
    by_last_frame = by
    trigger_last_frame = trigger
    var target_name := "left_hand" if is_left else "right_hand"
    var tracked := XRServer.get_tracker(target_name) as XRPositionalTracker
    # WebXR sources without handedness use tracker_2. Assign only to right,
    # and prefer the real right-hand source as soon as it becomes available.
    if not is_left and (tracked == null or not tracked.has_pose("default") or not tracked.get_pose("default").has_tracking_data):
        var fallback := XRServer.get_tracker("tracker_2") as XRPositionalTracker
        if fallback != null and fallback.has_pose("default") and fallback.get_pose("default").has_tracking_data:
            target_name = "tracker_2"
            tracked = fallback
    if get_tracker() != StringName(target_name):
        set_tracker(target_name)
        _was_tracked = false
    var valid := tracked != null and tracked.has_pose("default") and tracked.get_pose("default").has_tracking_data
    visible = valid
    if not valid:
        if is_instance_valid(vr.webxr_initializer):
            vr.webxr_initializer.clear_source(get_tracker())
        ax = false
        by = false
        trigger = false
        _was_tracked = false
        return
    # Missing action names return null. bool(null) is not a valid Godot
    # constructor overload; explicit comparison prevents phantom button presses.
    ax = tracked.get_input("ax_button") == true
    by = tracked.get_input("by_button") == true or tracked.get_input("button_1") == true
    trigger = tracked.get_input("trigger_click") == true or tracked.get_input("button_0") == true or tracked.get_input("touchpad_click") == true
    if is_instance_valid(vr.webxr_initializer):
        trigger = trigger or vr.webxr_initializer.source_selected(get_tracker())
    var p: XRPose = tracked.get_pose("default")
    var raw: Transform3D = p.get_adjusted_transform()
    var browser = JavaScriptBridge.get_interface("opensaber") if OS.has_feature("web") else null
    is_3dof = browser == null or bool(browser.armModel)
    if is_3dof and head != null:
        # Raw orientation is already reference-space orientation: don't multiply
        # by head orientation again. Shoulder follows head yaw; arm follows ray.
        var world_basis: Basis = get_parent().global_transform.basis * raw.basis
        var yaw_basis := Basis(Vector3.UP, head.global_rotation.y)
        var side := -1.0 if is_left else 1.0
        var shoulder := head.global_position + yaw_basis * Vector3(hand_offset * side, -0.22, 0.0)
        global_transform = Transform3D(world_basis, shoulder - world_basis.z * hand_reach)
    else:
        transform = raw
    if not _was_tracked:
        ax_last_frame = ax
        by_last_frame = by
        trigger_last_frame = trigger
        reset_movement_aabb()
    movement_aabb = movement_aabb.expand(global_position)
    _was_tracked = true

func reset_movement_aabb() -> void:
    movement_aabb = AABB(global_position, Vector3.ZERO)

func simple_rumble(strength: float, duration: float) -> void:
    if vr.inVR and visible and strength > 0.0:
        trigger_haptic_pulse("haptic", 0.0, clampf(strength, 0.0, 1.0), duration, 0.0)
        _rumble_until = Time.get_ticks_msec() + int(duration * 1000.0)

func is_simple_rumbling() -> bool:
    return Time.get_ticks_msec() < _rumble_until

func ax_pressed() -> bool: return ax
func ax_just_pressed() -> bool: return ax and not ax_last_frame
func ax_just_released() -> bool: return ax_last_frame and not ax
func by_pressed() -> bool: return by
func by_just_pressed() -> bool: return by and not by_last_frame
func by_just_released() -> bool: return by_last_frame and not by
func trigger_pressed() -> bool: return trigger
func trigger_just_pressed() -> bool: return trigger and not trigger_last_frame
func trigger_just_released() -> bool: return trigger_last_frame and not trigger
