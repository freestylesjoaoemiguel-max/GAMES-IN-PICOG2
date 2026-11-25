extends Node3D
# Real WebXR compositor integration. No Android/OpenXR device-name spoofing.
const Arm = preload("res://g2_arm.gd")
const Motion = preload("res://g2_motion.gd")
var game: Node3D
var xr: WebXRInterface
var origin: XROrigin3D
var head: XRCamera3D
var flat_camera: Camera3D
var immersive := false
var supported := false
var starting := false
var arms := [Arm.new(),Arm.new()]
var motion := Motion.new()
var arm_meshes: Array[MeshInstance3D] = []
var tracked := [false,false]
var pressed := [false,false]
var previous_pressed := [false,false]
var held_seconds := [0.0,0.0]
var consumed := [false,false]
var selected_sources := {}
var menu_open := false
var ui_view: SubViewport
var ui_quad: MeshInstance3D
var ui_root: CanvasLayer
var quick: Control
var status: Label
var last_player: Node3D
var status_timer := 0.0
var callback
var bridge
var rig_ready := false
var last_body := Vector3.ZERO
var ray_meshes: Array[MeshInstance3D] = []
var key_target: LineEdit
var keyboard: PanelContainer
var last_ui_pos := Vector2.ZERO
var ui_mouse_down := false

func _ready() -> void:
    origin=XROrigin3D.new()
    origin.name="WebXROrigin"
    add_child(origin)
    head=XRCamera3D.new()
    head.name="WebXRHead"
    head.near=0.04
    head.far=120.0
    head.cull_mask=1|2|8
    origin.add_child(head)
    head.current=false
    for i in 2:
        var beam:=MeshInstance3D.new()
        var box:=BoxMesh.new()
        box.size=Vector3(0.006,0.006,1.4)
        beam.mesh=box
        var mat:=StandardMaterial3D.new()
        mat.shading_mode=BaseMaterial3D.SHADING_MODE_UNSHADED
        mat.albedo_color=Color.CYAN if i==1 else Color.ORANGE
        beam.material_override=mat
        beam.visible=false
        add_child(beam)
        ray_meshes.append(beam)
    xr=XRServer.find_interface("WebXR") as WebXRInterface
    if xr:
        xr.session_supported.connect(func(mode,ok):
            if mode=="immersive-vr": supported=ok)
        xr.session_started.connect(_session_started)
        xr.session_ended.connect(_session_ended)
        xr.session_failed.connect(_session_failed)
        xr.selectstart.connect(func(id): selected_sources[id]=true)
        xr.selectend.connect(func(id): selected_sources.erase(id))
        xr.is_session_supported("immersive-vr")
    if OS.has_feature("web"):
        bridge=JavaScriptBridge.get_interface("gila")
        callback=JavaScriptBridge.create_callback(_command)
        if bridge!=null: bridge.command=callback
    _build_menu()
    if OS.has_feature("web"): call_deferred("_prepare_default")

func _prepare_default() -> void:
    # Import/build all original game systems through their normal initialization.
    var intro=game.game_menu.get_node_or_null("AberturaKinawJr")
    if intro: intro.queue_free()
    game.game_menu.vr_enabled=false
    game.selected_map="base"
    await game.game_menu.play()
    if bridge!=null: bridge.ready=true
    print("[G2] GilaMobi v52 WebXR 1.0.0 ready")

func _command(args: Array) -> void:
    if args.is_empty(): return
    command(str(args[0]))

func command(action: String) -> void:
    match action:
        "vr": enter_vr()
        "menu": toggle_menu()
        "resume": close_menu()
        "recenter": recenter()
        "base","backrooms","sandbox": _map(action)
        "normal","grinner":
            if game.selected_map=="sandbox":
                game.select_sandbox_role(action)
                reset_tracking()
        "release": game.release_grabs()
        "split": game.split_held()
        "tear_left": game.tear_held(0)
        "tear_right": game.tear_held(1)
        "chainsaw","grab":
            if game.active_character=="grinner": game.choose_monster_mode(action)
        "mic":
            if is_instance_valid(game.voice_face): game.voice_face.toggle()
        "camera":
            if is_instance_valid(game.sandbox_camera): game.sandbox_camera.pickup_or_drop()
        "record":
            if is_instance_valid(game.sandbox_camera): game.sandbox_camera.toggle_record()
        "original_menu":
            game.game_menu.return_to_menu()
            _show_menu(true)
        "calibrate":
            for arm in arms: arm.calibrated=false
            reset_tracking()

func _map(which: String) -> void:
    if game.game_menu.busy: return
    game.network.leave()
    game.selected_map=which
    await game.game_menu.play()
    reset_tracking()
    if immersive:
        _attach_player()
        _show_menu(false)

func enter_vr() -> void:
    if immersive or starting: return
    if not xr or not supported:
        _notice("Este navegador nao oferece WebXR immersive-vr. Tela comum nao e VR.")
        return
    if not is_instance_valid(game.player):
        _notice("Aguarde o carregamento do mapa.")
        return
    starting=true
    xr.session_mode="immersive-vr"
    xr.requested_reference_space_types="local-floor,local"
    xr.required_features=""
    xr.optional_features="local-floor"
    if not xr.initialize(): _session_failed("O navegador recusou iniciar a sessao.")

func _session_started() -> void:
    starting=false
    immersive=true
    get_viewport().use_xr=true
    # Godot 4.4 Compatibility's scaled tonemap pass is not stereo-safe here.
    # Keep the XR target at its runtime size (do not duplicate a mono frame).
    get_viewport().scaling_3d_scale=1.0
    flat_camera=game.camera
    _attach_player()
    game.camera=head
    head.make_current()
    game.game_menu.reparent(ui_view)
    game.game_menu.layout_screen()
    _show_menu(false)
    reset_tracking()
    if bridge!=null: bridge.immersive=true
    print("[G2] immersive-vr session started: "+xr.reference_space_type)

func _session_ended() -> void:
    starting=false
    immersive=false
    get_viewport().use_xr=false
    # 4.4 resets viewport view_count but can leave the render target stereo
    # when the dimensions coincide. Recreate its mono target before drawing.
    var size: Vector2i=get_viewport().get_visible_rect().size
    RenderingServer.viewport_set_size(get_viewport().get_viewport_rid(),size.x+1,size.y)
    RenderingServer.viewport_set_size(get_viewport().get_viewport_rid(),size.x,size.y)
    selected_sources.clear()
    if game.game_menu.get_parent()==ui_view: game.game_menu.reparent(game)
    if is_instance_valid(flat_camera):
        game.camera=flat_camera
        flat_camera.make_current()
    ui_quad.visible=false
    for ray in ray_meshes: ray.visible=false
    game.vr_hand_enabled=false
    game.vr_hand_tracked=[false,false]
    for mesh in arm_meshes:
        if is_instance_valid(mesh): mesh.visible=true
    reset_tracking()
    game.game_menu.pause_game()
    if bridge!=null: bridge.immersive=false
    game.set_graphics_quality(game.graphics_quality,false)
    print("[G2] immersive-vr session ended")

func _session_failed(message: String) -> void:
    starting=false
    _notice("Falha WebXR: "+message)

func _notice(text: String) -> void:
    if bridge!=null: bridge.notice=text
    if is_instance_valid(status): status.text=text
    print("[G2] "+text)

func _attach_player() -> void:
    if not is_instance_valid(game.player): return
    if last_player!=game.player:
        last_player=game.player
        if game.camera!=head: flat_camera=game.camera
        var sk: Skeleton3D=game.skeleton
        var mesh:=sk.get_node("Gilla") as MeshInstance3D
        var result:=preload("res://g2_skin.gd").split(mesh,sk)
        arm_meshes=result.arms
        for arm in arms: arm.radius=result.radius
        for modifier in game.arm_iks: modifier.set_meta("g2_orientation",true)
        rig_ready=true
        reset_tracking()
    origin.global_transform=game.player.global_transform
    origin.position.y += 1.36*game.character_scale() if xr and xr.reference_space_type=="local" else 0.0
    if immersive:
        game.camera=head
        head.make_current()
        game.ui.visible=false

func reset_tracking() -> void:
    motion.reset()
    for arm in arms: arm.reset()
    game.contact=[false,false]
    if is_instance_valid(game.player): game.player.velocity=Vector3.ZERO
    pressed=[false,false]
    previous_pressed=[false,false]
    held_seconds=[0.0,0.0]
    consumed=[false,false]
    ui_mouse_down=false

func recenter() -> void:
    if immersive and is_instance_valid(game.player):
        # Rotate the reference frame, not the measured headset pose.
        XRServer.center_on_hmd(XRServer.RESET_BUT_KEEP_TILT,false)
        reset_tracking()

func source_for(i: int) -> XRPositionalTracker:
    var t:=XRServer.get_tracker("left_hand" if i==0 else "right_hand") as XRPositionalTracker
    if valid_source(t): return t
    if i==1:
        var fallback:=XRServer.get_tracker("tracker_2") as XRPositionalTracker
        if valid_source(fallback): return fallback
    return null

func valid_source(t: XRPositionalTracker) -> bool:
    return t!=null and t.has_pose("default") and t.get_pose("default").has_tracking_data

func step_hands(dt: float) -> void:
    if not immersive: return
    _attach_player()
    game.vr_hand_enabled=true
    var corrections: Array[Vector3]=[]
    var space:=get_world_3d().direct_space_state
    var exclusions: Array[RID]=[game.player.get_rid()]
    var scale: float=game.character_scale()
    for i in 2:
        var t:=source_for(i)
        if game.active_character=="normal" and (game.missing_parts & (2 if i==0 else 4))!=0: t=null
        tracked[i]=t!=null
        game.previous_palm[i]=game.targets[i].global_position
        if is_instance_valid(arm_meshes[i]): arm_meshes[i].visible=tracked[i] and (game.missing_parts & (2 if i==0 else 4))==0
        if not tracked[i]:
            if arms[i].valid: motion.reset()
            arms[i].valid=false
            arms[i].reset()
            game.contact[i]=false
            pressed[i]=false
            game.held[i]={}
            continue
        var raw: Transform3D=t.get_pose("default").get_adjusted_transform()
        var force_arm: bool=true if bridge==null else bridge.force3dof==true
        arms[i].valid=true
        arms[i].update(dt,origin.global_transform,head.global_position,raw,force_arm,-1.0 if i==0 else 1.0,scale,space,exclusions)
        if arms[i].discontinuity:
            motion.reset()
            game.player.velocity=Vector3.ZERO
        var a=arms[i]
        game.hand_visual[i]=game.player.to_local(a.target)
        game.hand_local[i]=game.hand_visual[i]
        game.targets[i].global_transform=Transform3D(a.orientation,a.target)
        game.vr_hand_offsets[i]=head.global_transform.affine_inverse()*a.desired/scale
        game.contact[i]=a.contact and not menu_open
        if game.contact[i]: corrections.append(a.target-a.desired)
    game.vr_hand_tracked=tracked.duplicate()
    game.vr_hand_can_push=[tracked[0] and arms[0].grace<=0,tracked[1] and arms[1].grace<=0]
    game.have_previous_palm=true
    if menu_open:
        motion.reset()
        return
    game.player.velocity=motion.apply(corrections,game.player.velocity,dt,scale)
    game.hand_push_applied=not corrections.is_empty() or motion.released

func after_body(shift: Vector3,dt: float) -> void:
    if not immersive or menu_open: return
    var space:=get_world_3d().direct_space_state
    var exclusions: Array[RID]=[game.player.get_rid()]
    for i in 2:
        if not tracked[i]: continue
        arms[i].after_move(shift,space,exclusions,game.character_scale())
        game.targets[i].global_transform=Transform3D(arms[i].orientation,arms[i].target)
        game.hand_visual[i]=game.player.to_local(arms[i].target)
    motion.record(shift,dt)
    _attach_player()

func _physics_process(dt: float) -> void:
    if not immersive: return
    if not is_instance_valid(game.player): return
    _attach_player()
    for i in 2:
        var t:=source_for(i)
        previous_pressed[i]=pressed[i]
        if t==null:
            pressed[i]=false
            held_seconds[i]=0
            ray_meshes[i].visible=false
            if ui_mouse_down: _ui_click(false)
            continue
        var id:=0 if i==0 else 2 if t.name==&"tracker_2" else 1
        pressed[i]=t.get_input("trigger_click")==true or t.get_input("button_0")==true or t.get_input("touchpad_click")==true or selected_sources.get(id,false)==true
        held_seconds[i]=held_seconds[i]+dt if pressed[i] else 0.0
        if pressed[i] and not previous_pressed[i]: consumed[i]=false
        if held_seconds[i]>1.1 and not consumed[i]:
            consumed[i]=true
            toggle_menu()
        var raw: Transform3D=t.get_pose("default").get_adjusted_transform()
        var basis:=origin.global_basis*raw.basis
        var pos: Vector3=arms[i].target if arms[i].ready else head.global_position + Vector3(0.2 if i==1 else -0.2,-0.2,-0.4)
        ray_meshes[i].visible=menu_open
        ray_meshes[i].global_transform=Transform3D(basis,pos-basis.z*0.7)
        if menu_open:
            _point_ui(pos,-basis.z,pressed[i] and not previous_pressed[i],not pressed[i] and previous_pressed[i])
        elif not pressed[i] and previous_pressed[i] and not consumed[i]:
            game.release_grabs()
    if not game.game_running or menu_open:
        # Keep menu pointing / hand visuals alive while original gameplay is paused.
        step_hands(dt)
    status_timer+=dt
    if status_timer>0.25:
        status_timer=0
        var text: String="G2 Web 1.0.0 | "+("ESQ " if tracked[0] else "")+("DIR " if tracked[1] else "")
        text += " | BRACO ESTIMADO 3DoF" if bridge==null or bridge.force3dof==true else " | POSE 6DoF"
        status.text=text+"\nGatilho longo: menu. Gesto contra a superficie: impulso. Levante para soltar."
        if bridge!=null:
            bridge.tracked=int(tracked[0])+int(tracked[1])
            bridge.contacts=int(game.contact[0])+int(game.contact[1])
            bridge.map=game.selected_map
            bridge.position=JSON.stringify([game.player.position.x,game.player.position.y,game.player.position.z])
            bridge.fps=Engine.get_frames_per_second()
            bridge.diagnostics=JSON.stringify({"menu":menu_open,"tracked":tracked,"arm_visible":[arm_meshes[0].visible,arm_meshes[1].visible],"role":game.active_character,"head":[head.global_position.x,head.global_position.y,head.global_position.z],"head_forward":[-head.global_basis.z.x,-head.global_basis.z.y,-head.global_basis.z.z],"hands":[[arms[0].target.x,arms[0].target.y,arms[0].target.z],[arms[1].target.x,arms[1].target.y,arms[1].target.z]],"running":game.game_running,"scale":get_viewport().scaling_3d_scale})

func toggle_menu() -> void:
    _show_menu(not menu_open)

func close_menu() -> void:
    if game.game_menu.initialized: game.game_menu.resume_game()
    _show_menu(false)

func _show_menu(value: bool) -> void:
    menu_open=value
    ui_quad.visible=value and immersive
    quick.visible=value
    if value and immersive:
        var forward: Vector3=-head.global_basis.z
        forward.y=0
        forward=forward.normalized()
        if forward.length_squared()<0.1: forward=Vector3.FORWARD
        ui_quad.global_position=head.global_position+forward*1.7
        ui_quad.look_at(ui_quad.global_position+forward,Vector3.UP)
        game.player.velocity=Vector3.ZERO
        motion.reset()
    if is_instance_valid(game.ui) and immersive: game.ui.visible=false
    if not value:
        _ui_click(false)
        for ray in ray_meshes: ray.visible=false
        reset_tracking()

func _button(parent: Control,text: String,action: String) -> void:
    var b:=Button.new()
    b.text=text
    b.custom_minimum_size=Vector2(170,48)
    b.pressed.connect(command.bind(action))
    parent.add_child(b)

func _build_menu() -> void:
    ui_view=SubViewport.new()
    ui_view.name="ImmersiveUI"
    ui_view.size=Vector2i(1280,900)
    ui_view.transparent_bg=false
    ui_view.render_target_update_mode=SubViewport.UPDATE_WHEN_VISIBLE
    ui_view.handle_input_locally=true
    add_child(ui_view)
    ui_root=CanvasLayer.new()
    ui_root.layer=40
    ui_view.add_child(ui_root)
    quick=PanelContainer.new()
    quick.position=Vector2(0,660)
    quick.size=Vector2(1280,240)
    ui_root.add_child(quick)
    var column:=VBoxContainer.new()
    quick.add_child(column)
    var row:=HBoxContainer.new()
    column.add_child(row)
    for entry in [["Continuar","resume"],["Base","base"],["Backrooms","backrooms"],["Sandbox","sandbox"],["Menu original","original_menu"],["Calibrar","calibrate"],["Recentrar","recenter"]]: _button(row,entry[0],entry[1])
    var actions:=HBoxContainer.new()
    column.add_child(actions)
    for entry in [["Normal","normal"],["Grinner","grinner"],["Agarrar","grab"],["Motosserra","chainsaw"],["Soltar","release"],["Microfone","mic"],["Camera","camera"]]: _button(actions,entry[0],entry[1])
    var tools:=HBoxContainer.new()
    column.add_child(tools)
    for entry in [["Gravar/parar","record"],["Dividir","split"],["Arrancar ESQ","tear_left"],["Arrancar DIR","tear_right"]]: _button(tools,entry[0],entry[1])
    status=Label.new()
    status.add_theme_font_size_override("font_size",18)
    column.add_child(status)
    ui_quad=MeshInstance3D.new()
    var quad:=QuadMesh.new()
    quad.size=Vector2(2.1,1.4765625)
    ui_quad.mesh=quad
    var mat:=StandardMaterial3D.new()
    mat.shading_mode=BaseMaterial3D.SHADING_MODE_UNSHADED
    mat.albedo_texture=ui_view.get_texture()
    mat.cull_mode=BaseMaterial3D.CULL_DISABLED
    ui_quad.material_override=mat
    add_child(ui_quad)
    ui_quad.visible=false
    _build_keyboard()

func _point_ui(pos: Vector3,direction: Vector3,down: bool,up: bool) -> void:
    var inverse:=ui_quad.global_transform.affine_inverse()
    var p:=inverse*pos
    var d:=inverse.basis*direction
    if absf(d.z)<0.001: return
    var t: float=-p.z/d.z
    if t<0: return
    var point:=p+d*t
    if absf(point.x)>1.05 or absf(point.y)>0.7383:
        if up: _ui_click(false)
        return
    var pixel:=Vector2((point.x/2.1+0.5)*1280,(0.5-point.y/1.4765625)*900)
    var event:=InputEventMouseMotion.new()
    event.position=pixel
    event.global_position=pixel
    event.relative=pixel-last_ui_pos
    last_ui_pos=pixel
    ui_view.push_input(event,true)
    if down: _ui_click(true)
    if up: _ui_click(false)
    var focus=ui_view.gui_get_focus_owner()
    if focus is LineEdit:
        key_target=focus
        keyboard.visible=true

func _ui_click(down: bool) -> void:
    if not down and not ui_mouse_down: return
    var event:=InputEventMouseButton.new()
    event.button_index=MOUSE_BUTTON_LEFT
    event.pressed=down
    event.position=last_ui_pos
    event.global_position=last_ui_pos
    ui_view.push_input(event,true)
    ui_mouse_down=down

func _build_keyboard() -> void:
    keyboard=PanelContainer.new()
    keyboard.position=Vector2(170,420)
    keyboard.size=Vector2(940,280)
    ui_root.add_child(keyboard)
    var col:=VBoxContainer.new()
    keyboard.add_child(col)
    for text in ["1234567890","QWERTYUIOP","ASDFGHJKL","ZXCVBNM"]:
        var row:=HBoxContainer.new()
        col.add_child(row)
        for letter in text:
            var b:=Button.new()
            b.text=letter
            b.focus_mode=Control.FOCUS_NONE
            b.custom_minimum_size=Vector2(83,45)
            b.pressed.connect(func():
                if is_instance_valid(key_target): key_target.insert_text_at_caret(letter))
            row.add_child(b)
    var row:=HBoxContainer.new()
    col.add_child(row)
    for text in ["ESPACO","APAGAR","OK"]:
        var b:=Button.new()
        b.text=text
        b.focus_mode=Control.FOCUS_NONE
        b.custom_minimum_size=Vector2(280,45)
        b.pressed.connect(func():
            if not is_instance_valid(key_target): return
            if text=="ESPACO": key_target.insert_text_at_caret(" ")
            elif text=="APAGAR":
                var caret:=key_target.caret_column
                if caret>0: key_target.delete_text(caret-1,caret)
            else:
                key_target.text_submitted.emit(key_target.text)
                key_target.release_focus()
                keyboard.visible=false)
        row.add_child(b)
    keyboard.visible=false
