extends SceneTree
const Motion=preload("res://g2_motion.gd")
var passed: Array[String]=[]
var failed: Array[String]=[]
func check(ok: bool,label: String) -> void:
    if ok: passed.append(label)
    else: failed.append(label)
func _initialize() -> void: call_deferred("run")
func run() -> void:
    var motion:=Motion.new()
    var one: Array[Vector3]=[Vector3(0,0,-0.02)]
    var two: Array[Vector3]=[Vector3(0,0,-0.02),Vector3(0,0,-0.02)]
    var none: Array[Vector3]=[]
    check(motion.apply(one,Vector3.ZERO,0.02,1).is_equal_approx(Vector3(0,0,-1)),"Contact displacement gives opposite body velocity")
    check(motion.apply(two,Vector3.ZERO,0.02,1).is_equal_approx(Vector3(0,0,-1)),"Two hands average; never double the speed")
    for i in 8: motion.record(Vector3(0,0,-0.02),0.02)
    check(motion.apply(none,Vector3.ZERO,0.02,1).is_equal_approx(Vector3(0,0,-1.1)),"Eight actual body samples produce release impulse")
    motion.reset()
    motion.apply(one,Vector3.ZERO,0.02,1)
    check(motion.apply(none,Vector3.ZERO,0.02,1)==Vector3.ZERO,"Tracking reset cannot throw")
    check(motion.apply([Vector3(10,10,10)],Vector3.ZERO,0.01,1).length()<=8.001,"Bad correction capped at 8 m/s")
    for hz in [30,60,72,90]:
        for scale in [0.7,1.0,1.4]:
            motion.reset()
            var dt:=1.0/float(hz)
            var correction: Array[Vector3]=[Vector3(0,0,-1)*dt*scale]
            var velocity:=motion.apply(correction,Vector3.ZERO,dt,scale)
            check(velocity.is_equal_approx(Vector3(0,0,-scale)),"Rate/scale invariant contact: %sHz x%s" % [hz,scale])
    motion.reset()
    motion.apply(one,Vector3.ZERO,0.02,1)
    for i in 8: motion.record(Vector3(0,0,-1),0.02)
    check(motion.apply(none,Vector3.ZERO,0.02,1).length()<=4.001,"Release speed is capped at 4 m/s")
    motion.reset()
    motion.apply(one,Vector3.ZERO,0.02,1)
    for i in 8: motion.record(Vector3(0,0,-.001),0.02)
    check(motion.apply(none,Vector3.ZERO,0.02,1)==Vector3.ZERO,"Subthreshold release cannot launch the player")
    var game=load("res://main.tscn").instantiate()
    root.add_child(game)
    await process_frame
    var intro=game.game_menu.get_node_or_null("AberturaKinawJr")
    if intro: intro.queue_free()
    await game.game_menu.play()
    game.set_physics_process(false)
    game.g2_xr.set_physics_process(false)
    check(is_instance_valid(game.player) and game.skeleton.get_bone_count()>10,"Original player model, skeleton, game and menu instantiate")
    for name in ["backrooms","sandbox","base"]:
        game.load_map(name)
        await physics_frame
        check(game.loaded_map==name and game.get_node("Base").get_child_count()>10,"Original map instantiated: "+name)
    var floor:=StaticBody3D.new()
    var collider:=CollisionShape3D.new()
    var box:=BoxShape3D.new()
    box.size=Vector3(20,1,20)
    collider.shape=box
    floor.position=Vector3(200,-0.5,0)
    floor.add_child(collider)
    root.add_child(floor)
    await physics_frame
    await physics_frame
    var space: PhysicsDirectSpaceState3D=game.get_world_3d().direct_space_state
    var exclusions: Array[RID]=[game.player.get_rid()]
    var swept:=Motion.sweep(space,Vector3(200,2,0),Vector3(200,-3,0),0.18,exclusions)
    check(swept.contact and swept.position.y>=0.179,"Swept sphere cannot tunnel through thin floor")
    var overlap:=Motion.sweep(space,Vector3(200,0.1,0),Vector3(200,0.1,0),0.18,exclusions)
    check(overlap.position.y>=0.179,"Initial overlap depenetrates above sphere radius")
    var wall:=StaticBody3D.new()
    var wc:=CollisionShape3D.new()
    var wb:=BoxShape3D.new();wb.size=Vector3(.1,8,8);wc.shape=wb
    wall.add_child(wc);wall.position=Vector3(205,3,0);root.add_child(wall)
    await physics_frame
    await physics_frame
    var wall_hit:=Motion.sweep(space,Vector3(203,1,0),Vector3(208,1,0),.18,exclusions)
    check(wall_hit.contact and wall_hit.position.x<204.78,"Swept hand cannot cross thin vertical wall")
    var slide:=Motion.sweep(space,Vector3(203,1,0),Vector3(208,1,2),.18,exclusions)
    check(slide.contact and slide.position.x<204.78 and slide.position.z>1.5,"Diagonal wall stroke slides tangentially without penetration")
    var air:=Motion.sweep(space,Vector3(200,3,0),Vector3(201,3,0),.18,exclusions)
    check(not air.contact and air.position.is_equal_approx(Vector3(201,3,0)),"No surface in air cannot create artificial support")
    var rig=game.g2_xr
    check(rig.source_for(0)==null and rig.source_for(1)==null,"No controller source is invented")
    var t:=XRControllerTracker.new()
    t.name="tracker_2"
    t.type=XRServer.TRACKER_CONTROLLER
    t.set_pose("default",Transform3D.IDENTITY,Vector3.ZERO,Vector3.ZERO,XRPose.XR_TRACKING_CONFIDENCE_HIGH)
    XRServer.add_tracker(t)
    check(rig.source_for(0)==null and rig.source_for(1)==t,"Unhanded controller mapped once to right")
    t.invalidate_pose("default")
    check(rig.source_for(1)==null,"Pose loss rejects stale tracker")
    XRServer.remove_tracker(t)
    var face_before: MeshInstance3D=game.skeleton.get_node("Gilla")
    var morph_count: int=face_before.mesh.get_blend_shape_count()
    rig.immersive=true
    rig._attach_player()
    check(face_before.mesh.get_blend_shape_count()==morph_count,"Skin partition retains original facial blend shapes")
    check(rig.arm_meshes.size()==2 and rig.arm_meshes[0].mesh.get_surface_count()>0 and rig.arm_meshes[1].mesh.get_surface_count()>0,"Original skinned arms split for independent visibility")
    rig.step_hands(1.0/72.0)
    check(not rig.arm_meshes[0].visible and not rig.arm_meshes[1].visible,"Missing hands hidden; no invisible support")
    t.name="right_hand"
    t.set_pose("default",Transform3D.IDENTITY,Vector3.ZERO,Vector3.ZERO,XRPose.XR_TRACKING_CONFIDENCE_HIGH)
    XRServer.add_tracker(t)
    game.missing_parts=4
    rig.step_hands(1.0/72.0)
    check(not rig.tracked[1] and not game.contact[1],"Severed original arm cannot provide invisible locomotion support")
    XRServer.remove_tracker(t)
    game.missing_parts=0
    rig.immersive=false
    game.camera=rig.flat_camera
    game.selected_map="sandbox"
    game.select_sandbox_role("grinner")
    check(game.active_character=="grinner" and game.skeleton.get_bone_count()>10,"Credited Grinner model and original role switch instantiate")
    var result={"passed":passed,"failed":failed,"physical_pico_g2_tested":false,"runtime":"Godot 4.4.1 headless; actual source and physics engine"}
    print("G2_TEST_RESULT "+JSON.stringify(result))
    var file:=FileAccess.open("res://../docs/ENGINE_TESTS.json",FileAccess.WRITE)
    if file: file.store_string(JSON.stringify(result,"  "))
    quit(0 if failed.is_empty() else 1)
