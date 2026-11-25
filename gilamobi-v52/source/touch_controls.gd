extends Control
var game
var fingers := [-1, -1]
var look_finger := -1
var mic_button: Button
var menu_button: Button
var release_button: Button
var tear_buttons: Array[Button] = []
var death_reason := ""
var mic_warning: ConfirmationDialog
var joystick_finger := -1
var joystick_vector := Vector2.ZERO
var arm_stick_vector := [Vector2.ZERO,Vector2.ZERO]
var jump_button: Button
var grab_button: Button
var swing_button: Button
var monster_choice: Control
var chat_button:Button
var chat_popup:AcceptDialog
var chat_input:LineEdit
var chat_error:Label
var camera_pickup:Button
var camera_record:Button
var camera_retry:Button
var camera_notice:Label
var camera_tap_finger:=-1
var camera_tap_start:=Vector2.ZERO
var camera_tap_time:=0

func reset_inputs() -> void:
 fingers=[-1,-1];look_finger=-1;joystick_finger=-1;joystick_vector=Vector2.ZERO;arm_stick_vector=[Vector2.ZERO,Vector2.ZERO]
 camera_tap_finger=-1
 if game:game.move_input=Vector2.ZERO;game.simple_grab=false

func arm_stick_radius()->float:
 return minf(135.0,minf(size.y*.19,size.x*.105))

func arm_stick_center(i:int)->Vector2:
 var radius:=arm_stick_radius()
 var inset:=maxf(radius+40.0,size.x*.21)
 return Vector2(inset if i==0 else size.x-inset,size.y*.63)

func arm_stick_update(i:int,p:Vector2)->void:
 var radius:=arm_stick_radius()
 var offset:Vector2=(p-arm_stick_center(i)).limit_length(radius)
 arm_stick_vector[i]=offset/radius
 game.drag_hand(i,arm_stick_center(i)+offset*2.4)

func joystick_center() -> Vector2:
 return Vector2(155,size.y-150)

func joystick_update(p: Vector2) -> void:
 joystick_vector=((p-joystick_center())/105.0).limit_length(1.0)
 game.move_input=joystick_vector


func _ready() -> void:
 set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 mouse_filter = Control.MOUSE_FILTER_IGNORE
 mic_button = Button.new()
 mic_button.name = "Microfone"
 mic_button.text = tr("LIGAR MIC")
 mic_button.set_anchors_preset(Control.PRESET_TOP_RIGHT)
 mic_button.offset_left = -210
 mic_button.offset_right = -20
 mic_button.offset_top = 18
 mic_button.offset_bottom = 72
 mic_button.focus_mode = Control.FOCUS_NONE
 mic_button.add_theme_font_size_override("font_size", 20)
 mic_button.pressed.connect(_toggle_mic)
 add_child(mic_button)
 menu_button = Button.new()
 menu_button.name = "VoltarMenu"
 menu_button.text = tr("PAUSA")
 menu_button.set_anchors_preset(Control.PRESET_TOP_RIGHT)
 menu_button.offset_left = -330
 menu_button.offset_right = -220
 menu_button.offset_top = 18
 menu_button.offset_bottom = 72
 menu_button.focus_mode = Control.FOCUS_NONE
 menu_button.add_theme_font_size_override("font_size",20)
 menu_button.pressed.connect(func(): game.game_menu.pause_game())
 add_child(menu_button)
 camera_pickup=Button.new();camera_pickup.name="PegarCamera";camera_pickup.text=tr("PEGAR CÂMERA");camera_pickup.position=Vector2(20,115);camera_pickup.size=Vector2(205,48);camera_pickup.add_theme_font_size_override("font_size",17);camera_pickup.pressed.connect(func():game.sandbox_camera.pickup_or_drop());add_child(camera_pickup)
 camera_record=Button.new();camera_record.name="GravarCamera";camera_record.position=Vector2(235,115);camera_record.size=Vector2(205,48);camera_record.add_theme_font_size_override("font_size",17);camera_record.pressed.connect(func():game.sandbox_camera.toggle_record());add_child(camera_record)
 camera_retry=Button.new();camera_retry.name="SalvarGaleria";camera_retry.position=Vector2(450,115);camera_retry.size=Vector2(210,48);camera_retry.text=tr("SALVAR NA GALERIA");camera_retry.add_theme_font_size_override("font_size",16);camera_retry.pressed.connect(func():game.sandbox_camera.retry_gallery());add_child(camera_retry)
 camera_notice=Label.new();camera_notice.position=Vector2(20,168);camera_notice.add_theme_font_size_override("font_size",15);add_child(camera_notice)
 release_button = Button.new()
 release_button.name = "Soltar"
 release_button.text = tr("SOLTAR")
 release_button.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
 release_button.offset_left = -210; release_button.offset_right = -20
 release_button.offset_top = -88; release_button.offset_bottom = -20
 release_button.focus_mode = Control.FOCUS_NONE
 release_button.add_theme_font_size_override("font_size",22)
 release_button.visible = false
 release_button.pressed.connect(func(): game.release_grabs())
 add_child(release_button)
 for i in 2:
  var tear := Button.new();tear.name="ArrancarEsquerda" if i==0 else "ArrancarDireita"
  tear.text=tr("ARRANCAR E") if i==0 else tr("ARRANCAR D")
  tear.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
  tear.offset_left=20+i*205;tear.offset_right=215+i*205
  tear.offset_top=-88;tear.offset_bottom=-20
  tear.focus_mode=Control.FOCUS_NONE;tear.add_theme_font_size_override("font_size",18)
  var hand_index:=i
  tear.pressed.connect(func(): game.tear_held(hand_index))
  tear.visible=false;add_child(tear);tear_buttons.append(tear)
 jump_button=Button.new();jump_button.name="Pular";jump_button.text=tr("PULAR")
 jump_button.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
 jump_button.offset_left=-220;jump_button.offset_right=-20;jump_button.offset_top=-130;jump_button.offset_bottom=-20
 jump_button.focus_mode=Control.FOCUS_NONE;jump_button.add_theme_font_size_override("font_size",25)
 jump_button.pressed.connect(func():game.simple_jump());add_child(jump_button)
 grab_button=Button.new();grab_button.name="Agarrar";grab_button.text=tr("AGARRAR")
 grab_button.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
 grab_button.offset_left=-220;grab_button.offset_right=-20;grab_button.offset_top=-255;grab_button.offset_bottom=-145
 grab_button.focus_mode=Control.FOCUS_NONE;grab_button.add_theme_font_size_override("font_size",23)
 grab_button.button_down.connect(func():game.simple_grab=true)
 grab_button.button_up.connect(func():game.simple_grab=false)
 add_child(grab_button)
 swing_button=Button.new();swing_button.name="GolpearMotosserra";swing_button.text=tr("SERRAR")
 swing_button.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT);swing_button.offset_left=-225;swing_button.offset_right=-20;swing_button.offset_top=-255;swing_button.offset_bottom=-145
 swing_button.focus_mode=Control.FOCUS_NONE;swing_button.add_theme_font_size_override("font_size",22);swing_button.pressed.connect(func():game.swing_chainsaw());add_child(swing_button)
 monster_choice=ColorRect.new();monster_choice.name="EscolhaMonstro";monster_choice.color=Color(0,0,0,.78)
 monster_choice.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);monster_choice.mouse_filter=Control.MOUSE_FILTER_STOP;add_child(monster_choice)
 var group:=VBoxContainer.new();group.set_anchors_and_offsets_preset(Control.PRESET_CENTER);group.offset_left=-250;group.offset_right=250;group.offset_top=-125;group.offset_bottom=125
 group.add_theme_constant_override("separation",12);monster_choice.add_child(group)
 var title:=Label.new();title.text=tr("VOCÊ VIROU MONSTRO!\nESCOLHA SUA HABILIDADE");title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;title.add_theme_font_size_override("font_size",28);group.add_child(title)
 for item in [[tr("AGARRAR GILLAS"),"grab"],[tr("USAR MOTOSSERRA"),"chainsaw"]]:
  var btn:=Button.new();btn.name="Escolher_"+item[1];btn.text=item[0];btn.custom_minimum_size=Vector2(0,66);btn.add_theme_font_size_override("font_size",25)
  var mode:String=item[1];btn.pressed.connect(func():game.choose_monster_mode(mode));group.add_child(btn)
 monster_choice.visible=false
 chat_button=Button.new();chat_button.name="ChatTexto";chat_button.text=tr("CHAT")
 chat_button.set_anchors_preset(Control.PRESET_TOP_RIGHT)
 chat_button.offset_left=-455;chat_button.offset_right=-340;chat_button.offset_top=18;chat_button.offset_bottom=72
 chat_button.focus_mode=Control.FOCUS_NONE;chat_button.add_theme_font_size_override("font_size",20);add_child(chat_button)
 chat_popup=AcceptDialog.new();chat_popup.name="MensagemChat";chat_popup.title=tr("CHAT DE TEXTO")
 chat_popup.ok_button_text=tr("ENVIAR");chat_popup.min_size=Vector2i(560,190)
 var chat_box:=VBoxContainer.new();chat_box.add_theme_constant_override("separation",12);chat_popup.add_child(chat_box)
 var info:=Label.new();info.text=tr("Mensagem pública • aparece por 10 segundos");chat_box.add_child(info)
 chat_input=LineEdit.new();chat_input.max_length=96;chat_input.placeholder_text=tr("Escreva uma mensagem...");chat_box.add_child(chat_input)
 chat_error=Label.new();chat_box.add_child(chat_error)
 chat_popup.confirmed.connect(_send_chat)
 chat_input.text_submitted.connect(func(_text):_send_chat();chat_popup.hide())
 chat_button.pressed.connect(func():
  if game.network.state!="joined":return
  game.move_input=Vector2.ZERO;chat_input.text="";chat_error.text=""
  chat_popup.popup_centered(Vector2i(560,190));chat_input.grab_focus()
 )
 add_child(chat_popup)
 mic_warning = ConfirmationDialog.new()
 mic_warning.title = tr("Microfone online")
 mic_warning.dialog_text = tr("Sua voz será enviada a um servidor público de testes.\nOutras pessoas podem ouvir ou gravar.\nNão fale informações particulares. Quer ligar o microfone?")
 mic_warning.ok_button_text = tr("LIGAR MIC")
 mic_warning.cancel_button_text = tr("CANCELAR")
 mic_warning.confirmed.connect(func(): game.voice_face.toggle())
 add_child(mic_warning)

func update_monster_choice()->void:
 if is_instance_valid(monster_choice):monster_choice.visible=game.game_running and not game.vr_hand_enabled and game.active_character=="grinner" and game.monster_mode=="choose"

func hand_position(i: int) -> Vector2:
 return game.camera.unproject_position(game.targets[i].global_position)

func _input(event: InputEvent) -> void:
 if game == null or game.camera == null or not game.game_running or game.vr_hand_enabled: return
 if is_instance_valid(monster_choice) and monster_choice.visible:return
 if is_instance_valid(chat_popup) and chat_popup.visible:return
 if event is InputEventScreenTouch:
  if event.pressed:
   if (camera_pickup.visible and camera_pickup.get_global_rect().has_point(event.position)) or (camera_record.visible and camera_record.get_global_rect().has_point(event.position)) or (camera_retry.visible and camera_retry.get_global_rect().has_point(event.position)):return
   if chat_button.get_global_rect().has_point(event.position) or mic_button.get_global_rect().has_point(event.position) or menu_button.get_global_rect().has_point(event.position) or (release_button.visible and release_button.get_global_rect().has_point(event.position)): return
   if mic_warning.visible or game.is_dead or is_instance_valid(game.ragdoll): return
   for tear in tear_buttons:
    if tear.visible and tear.get_global_rect().has_point(event.position): return
   if (jump_button.visible and jump_button.get_global_rect().has_point(event.position)) or (grab_button.visible and grab_button.get_global_rect().has_point(event.position)) or (swing_button.visible and swing_button.get_global_rect().has_point(event.position)):return
   if game.try_sandbox_press(event.position):return
   if game.selected_map=="sandbox" and is_instance_valid(game.sandbox_camera) and not game.camera.is_position_behind(game.targets[1].global_position) and event.position.distance_to(hand_position(1))<65.0:
    camera_tap_finger=event.index;camera_tap_start=event.position;camera_tap_time=Time.get_ticks_msec()
   if game.control_mode=="simples":
    if jump_button.visible and jump_button.get_global_rect().has_point(event.position):return
    if grab_button.visible and grab_button.get_global_rect().has_point(event.position):return
    if swing_button.visible and swing_button.get_global_rect().has_point(event.position):return
    if joystick_finger==-1 and event.position.distance_to(joystick_center())<135:
     joystick_finger=event.index;joystick_update(event.position)
    elif look_finger==-1:look_finger=event.index
    return
   if game.control_mode=="dois_bracos":
    for i in 2:
     if fingers[i]==-1 and event.position.distance_to(arm_stick_center(i))<arm_stick_radius()+25 and not (game.active_character=="normal" and (game.missing_parts & (2 if i==0 else 4))!=0):
      fingers[i]=event.index;game.grab_hand(i,arm_stick_center(i));arm_stick_update(i,event.position);return
    if look_finger==-1:look_finger=event.index
    return
   # Pick the nearest available hand, in a forgiving area around the palm.
   var chosen := -1
   var nearest := minf(size.x, size.y) * 0.16
   for i in 2:
    if fingers[i] == -1 and not (game.active_character=="normal" and (game.missing_parts & (2 if i==0 else 4)) != 0) and not game.camera.is_position_behind(game.targets[i].global_position):
     var d: float = event.position.distance_to(hand_position(i))
     if d < nearest:
      nearest = d
      chosen = i
   if chosen >= 0:
    fingers[chosen] = event.index
    game.grab_hand(chosen, event.position)
   elif look_finger == -1:
    look_finger = event.index
  else:
   if camera_tap_finger==event.index:
    camera_tap_finger=-1
    if Time.get_ticks_msec()-camera_tap_time<350 and event.position.distance_to(camera_tap_start)<20 and is_instance_valid(game.sandbox_camera) and not game.sandbox_camera.held:
     game.sandbox_camera.pickup_or_drop()
   if joystick_finger==event.index:
    joystick_finger=-1;joystick_vector=Vector2.ZERO;game.move_input=Vector2.ZERO
   for i in 2:
    if fingers[i] == event.index:
     fingers[i] = -1
     arm_stick_vector[i]=Vector2.ZERO
     game.release_hand(i)
   if look_finger == event.index: look_finger = -1
 elif event is InputEventScreenDrag:
  if camera_tap_finger==event.index and event.position.distance_to(camera_tap_start)>=20:camera_tap_finger=-1
  if joystick_finger==event.index:joystick_update(event.position)
  for i in 2:
   if fingers[i] == event.index:
    if game.control_mode=="dois_bracos":arm_stick_update(i,event.position)
    else:game.drag_hand(i,event.position)
  if look_finger == event.index: game.look(event.relative)

func _send_chat()->void:
 if not is_instance_valid(game) or not is_instance_valid(game.network):return
 game.network.send_chat(chat_input.text)
 chat_input.text=""
 chat_input.release_focus()

func _toggle_mic() -> void:
 if not game.voice_face.active and not game.voice_face.waiting_permission and not game.voice_face.pending_start and game.network.state == "joined":
  mic_warning.popup_centered(Vector2i(560,230))
 else:
  game.voice_face.toggle()

func _process(_delta: float) -> void:
 if game and game.voice_face:
  var vf = game.voice_face
  mic_button.text = tr("DESLIGAR MIC") if vf.active or vf.waiting_permission or vf.pending_start else tr("LIGAR MIC")
 update_monster_choice()
 var cam=game.sandbox_camera
 var cam_available:bool=game.game_running and game.selected_map=="sandbox" and is_instance_valid(cam)
 camera_pickup.visible=cam_available
 camera_record.visible=cam_available and cam.spawned
 camera_retry.visible=cam_available and cam.gallery_retry
 camera_notice.visible=cam_available and cam.spawned
 if cam_available:
  camera_pickup.text=tr("DEIXAR NO AR") if cam.held else tr("CÂMERA NA MÃO")
  camera_record.text=tr("PARAR VÍDEO") if cam.recording else tr("GRAVAR VÍDEO")
  camera_notice.text=cam.status
 chat_button.visible=game.game_running and game.network.state=="joined"
 jump_button.visible = game.game_running and game.control_mode=="simples"
 grab_button.visible = game.game_running and game.control_mode=="simples" and game.active_character=="grinner" and game.monster_mode=="grab"
 swing_button.visible = game.game_running and game.control_mode=="simples" and game.active_character=="grinner" and game.monster_mode=="chainsaw"
 for i in 2:
  if game.control_mode=="dois_bracos":
   tear_buttons[i].set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
   tear_buttons[i].offset_left=-225+i*230;tear_buttons[i].offset_right=-5+i*230
   tear_buttons[i].offset_top=-90;tear_buttons[i].offset_bottom=-20
   tear_buttons[i].add_theme_font_size_override("font_size",18)
  elif game.control_mode=="simples":
   tear_buttons[i].set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
   tear_buttons[i].offset_left=300+i*225;tear_buttons[i].offset_right=510+i*225
   tear_buttons[i].offset_top=-120;tear_buttons[i].offset_bottom=-20
   tear_buttons[i].add_theme_font_size_override("font_size",22)
  else:
   tear_buttons[i].set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
   tear_buttons[i].offset_left=20+i*205;tear_buttons[i].offset_right=215+i*205
   tear_buttons[i].offset_top=-88;tear_buttons[i].offset_bottom=-20
   tear_buttons[i].add_theme_font_size_override("font_size",18)
 if game.control_mode=="dois_bracos":
  release_button.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
  release_button.offset_left=-110;release_button.offset_right=110;release_button.offset_top=-170;release_button.offset_bottom=-105
  release_button.add_theme_font_size_override("font_size",20)
 elif game.control_mode=="simples":
  release_button.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
  release_button.offset_left=-220;release_button.offset_right=-20
  release_button.offset_top=-380;release_button.offset_bottom=-270
  release_button.add_theme_font_size_override("font_size",24)
 else:
  release_button.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
  release_button.offset_left=-210;release_button.offset_right=-20
  release_button.offset_top=-88;release_button.offset_bottom=-20
  release_button.add_theme_font_size_override("font_size",22)
 release_button.visible = game.game_running and game.active_character == "grinner" and (not game.held[0].is_empty() or not game.held[1].is_empty())
 if release_button.visible:
  var held_players := {}
  for item in game.held:
   if not item.is_empty(): held_players[item.target] = true
  release_button.text = tr("SOLTAR (%d)") % held_players.size()
 for i in 2:
  tear_buttons[i].visible = game.game_running and game.active_character=="grinner" and not game.held[i].is_empty() and not bool(game.held[i].get("detached",false)) and str(game.held[i].get("part","body")) in ["head","arm_l","arm_r"]
 queue_redraw()

func _draw() -> void:
 if game == null or game.camera == null: return
 if game.control_mode=="simples":
  var center:=joystick_center()
  draw_circle(center,110,Color(0.04,0.06,0.08,.42))
  draw_arc(center,110,0,TAU,48,Color(1,1,1,.5),3.0,true)
  draw_circle(center+joystick_vector*82,40,Color(.9,.92,.95,.7))
 if game.control_mode=="dois_bracos":
  for i in 2:
   var center:=arm_stick_center(i)
   var radius:=arm_stick_radius()
   draw_circle(center,radius,Color(.04,.06,.08,.42))
   draw_arc(center,radius,0,TAU,48,Color(1,1,1,.60),4.0,true)
   draw_circle(center+arm_stick_vector[i]*radius*.72,radius*.36,Color(.9,.92,.95,.75))
 # Subtle targets sit over the actual 3D palms and help identify where to grab.
 var r := minf(size.x, size.y) * 0.045
 for i in 2:
  if game.control_mode in ["simples","dois_bracos"]:continue
  if game.active_character=="normal" and (game.missing_parts & (2 if i==0 else 4)) != 0: continue
  if not game.camera.is_position_behind(game.targets[i].global_position):
   var point := hand_position(i)
   var alpha := 0.8 if fingers[i] != -1 else 0.42
   draw_circle(point, r, Color(0.05, 0.08, 0.09, 0.18))
   draw_arc(point, r, 0, TAU, 48, Color(1, 1, 1, alpha), 2.0, true)
 var font := ThemeDB.fallback_font
 var fs := int(clampf(size.y * 0.031, 15, 26))
 draw_string(font, Vector2(24, 37), tr("GilaMobi  •  PROTÓTIPO"), HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(1,1,1,0.93))
 var hint:=tr("Joystick para andar • arraste a tela para olhar.") if game.control_mode=="simples" else tr("Segure e arraste as mãos. Arraste fora delas para olhar.")
 if game.control_mode=="dois_bracos":hint=tr("Um joystick para cada braço • arraste fora para olhar.")
 if game.active_character=="grinner" and game.monster_mode=="chainsaw":
  hint=tr("Toque em SERRAR para avançar a motosserra.") if game.control_mode=="simples" else (tr("Use o joystick direito para mover a motosserra.") if game.control_mode=="dois_bracos" else tr("Arraste a mão direita para cortar com a motosserra."))
 draw_string(font, Vector2(24, 37 + fs + 5), hint, HORIZONTAL_ALIGNMENT_LEFT, -1, int(fs * 0.75), Color(1,1,1,0.85))

 if game.voice_face:
  draw_string(font, Vector2(24, 37 + fs * 2 + 8), game.voice_face.status, HORIZONTAL_ALIGNMENT_LEFT, -1, int(fs * 0.65), Color(0.72,1.0,0.72,0.9))
 if game.network and game.network.state=="joined" and Time.get_ticks_msec()<game.round_notice_until and not game.round_notice.is_empty():
  draw_string(font,Vector2(size.x*.22,size.y*.18),tr(game.round_notice),HORIZONTAL_ALIGNMENT_CENTER,size.x*.56,int(fs*1.15),Color(1,.90,.55))
 if game.is_dead:
  draw_rect(Rect2(Vector2.ZERO,size),Color(0.25,0,0,.48))
  var message := tr("VOCÊ MORREU — ") + tr(death_reason) + (tr("\nVirando monstro em ") if game.network.state=="joined" else tr("\nVoltando em ")) + str(ceili(game.dead_timer))
  draw_multiline_string(font,Vector2(size.x*.25,size.y*.43),message,HORIZONTAL_ALIGNMENT_CENTER,size.x*.5,int(fs*1.4),-1,Color.WHITE)
 if is_instance_valid(game.ragdoll):
  draw_string(font,Vector2(size.x*.24,size.y*.27),tr("ATORDOADO • ")+str(ceili(game.ragdoll.time_left))+tr("s"),HORIZONTAL_ALIGNMENT_CENTER,size.x*.52,fs,Color(1,.83,.5))
 if game.network and game.network.state == "joined":
  draw_string(font, Vector2(24, 37 + fs * 3 + 14), tr("SALA: ") + game.network.room_code + tr("   •   TESTE PÚBLICO"), HORIZONTAL_ALIGNMENT_LEFT, -1, int(fs * 0.8), Color(1.0,0.85,0.60))
