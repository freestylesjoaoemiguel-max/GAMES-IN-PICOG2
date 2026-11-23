extends Node
# Test-only Godot XRServer fixtures. Never added to the published PCK.
# Exercises actual patched controller code, not a JS reimplementation.
var failures: Array[String] = []
var passed: Array[String] = []
func check(condition: bool, label: String) -> void:
    if condition: passed.append(label)
    else: failures.append(label)

func source(id: String) -> XRControllerTracker:
    var t := XRControllerTracker.new()
    t.name = id
    t.type = XRServer.TRACKER_CONTROLLER
    t.set_pose("default", Transform3D.IDENTITY, Vector3.ZERO, Vector3.ZERO, XRPose.XR_TRACKING_CONFIDENCE_HIGH)
    XRServer.add_tracker(t)
    return t

func _ready() -> void:
    var game = GlobalReferences.main_game_scene
    var l = game.left_controller
    var r = game.right_controller
    var head: XRCamera3D = game.get_node("XROrigin3D/XRCamera3D")
    var browser = JavaScriptBridge.get_interface("opensaber")
    var old_head := head.transform
    vr.inVR = true
    l._physics_process(1.0/72.0)
    r._physics_process(1.0/72.0)
    check(not l.visible and not r.visible, "No source: both hands hidden")
    var unknown := source("tracker_2")
    r._physics_process(1.0/72.0)
    l._physics_process(1.0/72.0)
    check(r.visible and not l.visible and r.get_tracker() == &"tracker_2", "Single unhanded source: right only, no phantom left")
    var right := source("right_hand")
    r._physics_process(1.0/72.0)
    check(r.get_tracker() == &"right_hand", "Explicit right source takes priority over unhanded source")
    right.set_input("trigger_click", true)
    r._physics_process(1.0/72.0)
    check(r.trigger_just_pressed(), "xr-standard trigger rising edge")
    r._physics_process(1.0/72.0)
    check(r.trigger_pressed() and not r.trigger_just_pressed(), "Held trigger has no repeated rising edge")
    right.set_input("trigger_click", false)
    r._physics_process(1.0/72.0)
    check(r.trigger_just_released(), "Trigger release edge")
    right.set_input("button_0", true)
    r._physics_process(1.0/72.0)
    check(r.trigger_just_pressed(), "Generic button_0 mapping")
    right.set_input("button_0", false)
    right.set_input("touchpad_click", true)
    r._physics_process(1.0/72.0)
    check(r.trigger_pressed(), "xr-standard touchpad can select")
    right.set_input("touchpad_click", false)
    r._physics_process(1.0/72.0)
    vr.webxr_initializer._select_start(1)
    r._physics_process(1.0/72.0)
    check(r.trigger_just_pressed(), "WebXR selectstart works without a pressed Gamepad button")
    vr.webxr_initializer._select_end(1)
    r._physics_process(1.0/72.0)
    check(r.trigger_just_released(), "WebXR selectend releases the correct source")
    head.rotation.y = 0.8
    r._physics_process(1.0/72.0)
    check(r.transform.basis.is_equal_approx(Basis.IDENTITY), "3DoF orientation does not multiply head yaw twice")
    var first_pos: Vector3 = r.position
    var pose := Transform3D(Basis(Vector3.UP, 0.6), Vector3.ZERO)
    right.set_pose("default", pose, Vector3.ZERO, Vector3.ZERO, XRPose.XR_TRACKING_CONFIDENCE_HIGH)
    r._physics_process(1.0/72.0)
    check(r.position.distance_to(first_pos) > 0.1, "3DoF arm endpoint moves with controller rotation")
    browser.armModel = false
    pose.origin = Vector3(0.3, 1.1, -0.6)
    right.set_pose("default", pose, Vector3.ZERO, Vector3.ZERO, XRPose.XR_TRACKING_CONFIDENCE_HIGH)
    r._physics_process(1.0/72.0)
    check(r.transform.is_equal_approx(pose), "6DoF keeps measured transform without arm model")
    var left := source("left_hand")
    l._physics_process(1.0/72.0)
    check(l.visible and r.visible and l.get_tracker() != r.get_tracker(), "Two controllers use distinct trackers")
    XRServer.remove_tracker(unknown)
    right.invalidate_pose("default")
    r._physics_process(1.0/72.0)
    check(not r.visible and not r.trigger_pressed(), "Pose loss hides hand and clears trigger")
    XRServer.remove_tracker(right)
    l._physics_process(1.0/72.0)
    check(l.visible and not r.visible, "Left-only source stays left without duplication")
    XRServer.remove_tracker(left)
    vr.inVR = false
    browser.armModel = true
    head.transform = old_head
    l._physics_process(1.0/72.0)
    r._physics_process(1.0/72.0)
    var pool = game.cube_pool
    var initial_count: int = pool._free_list.size()
    for i in range(5):
        var cube = pool.acquire()
        cube.release()
    check(pool._free_list.size() == initial_count, "Cube reuse returns to pool on every release")
    browser.fixtureResult = JSON.stringify({"passed":passed,"failures":failures,"physical_device":false})
    print("[G2 TEST] " + str(browser.fixtureResult))
    queue_free()
