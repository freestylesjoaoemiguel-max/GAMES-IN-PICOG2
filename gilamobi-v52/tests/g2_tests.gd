extends SceneTree
const Motion=preload("res://g2_motion.gd")
const Classic=preload("res://g2_classic_arm.gd")
const Presence=preload("res://g2_presence.gd")
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
    for hz in [10,30,60,72,90]:
        var dt:=1.0/float(hz)
        var impulse:=Classic.reaction(Vector3(0,-1,1)*dt,Vector3.UP,dt)
        check(impulse.is_equal_approx(Vector3(0,1.15,-1.15)),"Original v52 velocity impulse at %s Hz"%hz)
    check(Classic.reaction(Vector3(0,-.001,0),Vector3.UP,.02)==Vector3.ZERO,"Classic threshold ignores stationary/jitter contact")
    check(Classic.reaction(Vector3(0,1,0),Vector3.UP,.02)==Vector3.ZERO,"Lifting never launches body downward")
    check(Classic.reaction(Vector3(0,-10,10),Vector3.UP,.02).length()<=5.001,"Original v52 individual impulse capped at 5 m/s")
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
    var original_material: Material=face_before.get_active_material(0)
    rig.immersive=true
    rig._attach_player()
    check(face_before.mesh.get_blend_shape_count()==morph_count,"Skin partition retains original facial blend shapes")
    check(rig.arm_meshes[0].get_active_material(0)==original_material and rig.arm_meshes[1].get_active_material(0)==original_material,"Partitioned arms preserve imported material overrides and textures")
    check(rig.arm_meshes.size()==2 and rig.arm_meshes[0].mesh.get_surface_count()>0 and rig.arm_meshes[1].mesh.get_surface_count()>0,"Original skinned arms split for independent visibility")
    # Rotate only the measured HMD. Repeated attachment must not feed torso
    # rotation back into the XR reference or rotate controller goals twice.
    var original_target:Transform3D=game.targets[1].global_transform
    var body_position:Vector3=game.player.global_position
    var yaw_ok:=true
    for angle in [-2.8,-1.2,0.0,1.2,2.8]:
        rig.head.basis=Basis.from_euler(Vector3(.35,angle,.15))
        var expected:Vector3=-rig.head.global_basis.z
        expected.y=0;expected=expected.normalized()
        for frame in 40: rig._attach_player()
        yaw_ok=yaw_ok and (-game.player.global_basis.z).distance_to(expected)<.001
        yaw_ok=yaw_ok and game.targets[1].global_transform.is_equal_approx(original_target)
        yaw_ok=yaw_ok and game.player.global_position.is_equal_approx(body_position)
        yaw_ok=yaw_ok and rig.origin.global_basis.is_equal_approx(Basis.IDENTITY)
    check(yaw_ok,"Body follows HMD yaw left/right without reference feedback, translation or doubled controller rotation")
    var last_yaw:float=game.player.rotation.y
    rig.head.basis=Basis(Vector3.RIGHT,PI/2)
    rig._attach_player()
    check(is_equal_approx(last_yaw,game.player.rotation.y),"Looking vertically keeps stable torso yaw")
    rig.head.basis=Basis.IDENTITY;rig._attach_player()
    game.profile_name="Meu nome verde";rig._sync_identity()
    var identity=rig.local_identity
    check(identity.get_parent()==game.player and identity.vr_label.visible and identity.vr_label.modulate==Color("ff0000") and identity.name_label.modulate==Color("00ff00") and identity.name_label.text=="Meu nome verde" and identity.vr_label.position.y>identity.name_label.position.y,"Local avatar has red badge above saved green name")
    check(identity.vr_label.layers==4 and (rig.head.cull_mask & 4)==0,"Local overhead label visible to external cameras, not pasted inside headset")
    var mirror=game.get_node("Espelho")
    game.player.global_position=Vector3(-4*game.MAP_SCALE,0,-8.875*game.MAP_SCALE+3)
    mirror._process(.3)
    check(mirror.mirror_view.render_target_update_mode==SubViewport.UPDATE_WHEN_VISIBLE and (mirror.mirror_camera.cull_mask & 4)!=0,"Mirror stays live near player in immersive VR and includes local identity")
    game.player.global_position=body_position;rig._attach_player()
    # Real GL readback, not just Label3D properties: isolate the identity layer.
    var proof:=SubViewport.new();proof.size=Vector2i(512,256)
    proof.world_3d=game.get_viewport().find_world_3d()
    proof.render_target_update_mode=SubViewport.UPDATE_ALWAYS
    root.add_child(proof)
    var camera:=Camera3D.new();camera.cull_mask=4;camera.fov=45
    proof.add_child(camera)
    camera.global_position=game.player.global_position+Vector3(0,2.3,2.5)
    camera.look_at(game.player.global_position+Vector3(0,2.3,0));camera.make_current()
    for frame in 4: await process_frame
    await RenderingServer.frame_post_draw
    var image:=proof.get_texture().get_image()
    var reds:=0;var greens:=0
    for y in image.get_height():
        for x in image.get_width():
            var pixel:=image.get_pixel(x,y)
            if pixel.r>.6 and pixel.g<.2 and pixel.b<.2:reds+=1
            if pixel.g>.6 and pixel.r<.2 and pixel.b<.2:greens+=1
    check(reds>20 and greens>20,"Actual GL camera renders red VR badge pixels and green local name pixels")
    proof.queue_free()
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
    game.load_map("sandbox")
    game.selected_map="sandbox"
    game.select_sandbox_role("grinner")
    check(game.active_character=="grinner" and game.skeleton.get_bone_count()>10,"Credited Grinner model and original role switch instantiate")
    # Real outgoing JSON -> incoming state dispatcher -> actual remote rig.
    rig.immersive=true
    rig._attach_player()
    check(rig.local_identity.get_parent()==game.player and rig.local_identity.vr_label.visible and rig.local_identity.name_label.position.y==2.45,"Grinner rebuild recreates local identity at correct height")
    check((game.sandbox_camera.lens.cull_mask & 4)!=0,"Sandbox recording camera includes complete local avatar and identity")
    for height in [0.0,1.6,2.8]:
        rig.head.position.y=height
        rig._attach_player()
        check(is_equal_approx(rig.head.global_position.y-game.player.global_position.y,1.36*game.character_scale()),"3DoF avatar height independent of runtime floor offset %s"%height)
    rig.head.position.y=0
    rig._attach_player()
    rig.tracked=[true,false]
    game.profile_name="Nome verde"
    game.network.state="joined";game.network.mode="sandbox";game.network.uid="self_fixture"
    for axis in [Vector3.RIGHT,Vector3.UP,Vector3.FORWARD]:
        var ok:=true
        for degrees in range(0,361,45):
            var q:=Quaternion(axis,deg_to_rad(float(degrees)))
            game.targets[0].global_basis=Basis(q)
            var packet:Dictionary=game.network.player_state()
            packet["id"]="remote_fixture"
            game.network._on_message(JSON.parse_string(JSON.stringify(packet)))
            var remote=game.network.peers["remote_fixture"]
            remote._process(1.0)
            var received:Quaternion=remote.goals[0].global_basis.get_rotation_quaternion()
            remote.arm_modifiers[0]._process_modification()
            var hand_index:int=remote.rig.find_bone("hand_L")
            var hand_pose:Transform3D=remote.rig.get_bone_global_pose(hand_index)
            var visible_palm:Basis=remote.rig.global_basis.orthonormalized()*hand_pose.basis.orthonormalized()*remote.arm_modifiers[0].g2_palm_local
            ok=ok and absf(received.dot(q))>.9999 and absf(visible_palm.get_rotation_quaternion().dot(q))>.9999 and remote.rotation_valid[0] and not remote.rotation_valid[1]
        check(ok,"Remote full 360-degree rotation roundtrip axis %s"%axis)
    var peer=game.network.peers["remote_fixture"]
    check(peer.vr_label.visible and peer.vr_label.text=="[VR PICO G2]" and peer.vr_label.modulate==Color("ff0000"),"Remote VR badge exact text and red color")
    check(peer.name_label.text=="Nome verde" and peer.name_label.modulate==Color("00ff00") and peer.vr_label.position.y>peer.name_label.position.y,"Custom green name is below red VR badge")
    check(Presence.decode_rotation([0,0,0,0])==null and Presence.decode_rotation([NAN,0,0,1])==null and Presence.decode_rotation(["bad",0,0,1])==null,"Malformed remote orientations rejected")
    rig.immersive=false
    rig._sync_identity()
    check(not rig.local_identity.vr_label.visible and rig.local_identity.name_label.modulate==Color.WHITE,"Local identity clears VR colors on exit")
    var exit_packet:Dictionary=game.network.player_state();exit_packet["id"]="remote_fixture"
    game.network._on_message(exit_packet)
    check(not peer.vr_label.visible and peer.name_label.modulate==Color.WHITE and not peer.rotation_valid[0],"Leaving VR clears badge and returns legacy animation")
    peer.set_xr_state({})
    check(not peer.pico_vr,"Old clients without XR fields remain compatible")
    game.network.state="idle"
    var result={"passed":passed,"failed":failed,"physical_pico_g2_tested":false,"runtime":"Godot 4.4.1 Compatibility on Xvfb/Mesa; actual source, meshes and physics engine"}
    print("G2_TEST_RESULT "+JSON.stringify(result))
    var file:=FileAccess.open("res://../docs/ENGINE_TESTS.json",FileAccess.WRITE)
    if file: file.store_string(JSON.stringify(result,"  "))
    quit(0 if failed.is_empty() else 1)
