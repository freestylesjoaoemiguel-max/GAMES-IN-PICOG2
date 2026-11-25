extends CanvasLayer
var game: Node
var frame: Control
var screen: Control
var popup: Control
var panel_body: VBoxContainer
var status_label: Label
var plate: PanelContainer
var loading_layer: Control
var loading_bar: ProgressBar
var loading_text: Label
var pause_layer: Control
var initialized := false
var busy := false
var vr_enabled := false
var popup_tween: Tween
var public_list: VBoxContainer
var public_active := false
var server_stop: Button
var settings_from_pause := false
const ART_SIZE := Vector2(1280,720)

func _ready() -> void:
 var cfg:=ConfigFile.new()
 if cfg.load("user://modo_vr.cfg")==OK:vr_enabled=bool(cfg.get_value("vr","ativo",false))
 layer = 20
 screen = Control.new()
 screen.name = "TelaMenu"
 screen.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 screen.mouse_filter = Control.MOUSE_FILTER_STOP
 add_child(screen)
 var dark := ColorRect.new()
 dark.color = Color.BLACK
 dark.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 dark.mouse_filter = Control.MOUSE_FILTER_IGNORE
 screen.add_child(dark)
 frame = Control.new()
 frame.name = "ArteEControles"
 frame.size = ART_SIZE
 frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
 screen.add_child(frame)
 var art := TextureRect.new()
 art.name = "ImagemDeReferencia"
 art.texture = load("res://textures/menu_"+I18n.language+".jpg")
 art.position = Vector2.ZERO
 art.size = ART_SIZE
 art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
 art.stretch_mode = TextureRect.STRETCH_SCALE
 art.mouse_filter = Control.MOUSE_FILTER_IGNORE
 frame.add_child(art)
 # Hit areas map exactly to the five buttons in the supplied screenshot.
 make_hotspot("Jogar",Rect2(475,305,335,75),show_multiplayer)
 var vr_button:=preload("res://vr_icon.gd").new()
 vr_button.position=Vector2(833,306);vr_button.size=Vector2(80,72)
 vr_button.pressed.connect(show_vr_setup);frame.add_child(vr_button)
 make_hotspot("Configuracoes",Rect2(475,384,339,71),show_settings)
 make_hotspot("Cosmeticos",Rect2(475,457,342,66),show_cosmetics)
 make_hotspot("Creditos",Rect2(475,525,345,67),show_credits)
 make_hotspot("Sair",Rect2(475,595,350,74),exit_game)
 game.network.room_ready.connect(_room_ready)
 game.network.room_error.connect(_room_error)
 game.network.room_closed.connect(_room_closed)
 game.directory.directory_changed.connect(_refresh_public_list)
 var tag := Label.new()
 tag.name="AvisoPrototipo"
 tag.text = tr("GILAMOBI • PROTÓTIPO NÃO OFICIAL")
 tag.position = Vector2(915,686)
 tag.add_theme_font_size_override("font_size", 14)
 tag.add_theme_color_override("font_color",Color(1,1,1,.8))
 frame.add_child(tag)
 build_popup()
 build_loading()
 build_pause()
 screen.resized.connect(layout_screen)
 layout_screen()
 var intro=preload("res://intro.gd").new()
 intro.name="AberturaKinawJr"
 intro.opening_finished.connect(show_language_selection)
 add_child(intro)

func layout_screen() -> void:
 if not screen or not frame: return
 var s := minf(screen.size.x / ART_SIZE.x,screen.size.y / ART_SIZE.y)
 if s <= 0: return
 frame.scale = Vector2(s,s)
 frame.position = (screen.size - ART_SIZE*s)*.5

func make_hotspot(n: String, rect: Rect2, callback: Callable) -> void:
 var b := Button.new()
 b.name = n
 b.position = rect.position
 b.size = rect.size
 b.focus_mode = Control.FOCUS_NONE
 var blank := StyleBoxEmpty.new()
 b.add_theme_stylebox_override("normal",blank)
 b.add_theme_stylebox_override("focus",blank)
 var selected := StyleBoxFlat.new()
 selected.bg_color = Color(1,1,1,.23)
 selected.border_color = Color(1,1,1,.9)
 selected.set_border_width_all(3)
 selected.set_corner_radius_all(15)
 b.add_theme_stylebox_override("pressed",selected)
 var hover := StyleBoxFlat.new()
 hover.bg_color = Color(1,1,1,.07)
 hover.border_color = Color(1,1,1,.38)
 hover.set_border_width_all(2)
 hover.set_corner_radius_all(15)
 b.add_theme_stylebox_override("hover",hover)
 b.pivot_offset = b.size * .5
 b.button_down.connect(func(): animate_hotspot(b, true))
 b.button_up.connect(func(): animate_hotspot(b, false))
 b.pressed.connect(callback)
 frame.add_child(b)

func animate_hotspot(b: Button, pressed: bool) -> void:
 # The original screenshot stays underneath; the interactive highlight
 # contracts and brightens on touch, then springs back on release.
 var tw := create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
 tw.tween_property(b,"scale",Vector2.ONE * (0.965 if pressed else 1.0),0.13)

func build_popup() -> void:
 popup = Control.new()
 popup.name = "Painel"
 popup.size = ART_SIZE
 popup.visible = false
 popup.mouse_filter = Control.MOUSE_FILTER_STOP
 frame.add_child(popup)
 var shade := ColorRect.new()
 shade.color = Color(0,0,0,.70)
 shade.size = ART_SIZE
 popup.add_child(shade)
 build_plate()

func build_plate() -> void:
 plate = PanelContainer.new()
 plate.position = Vector2(330,125)
 plate.size = Vector2(620,465)
 var style := StyleBoxFlat.new()
 style.bg_color = Color("202024")
 style.border_color = Color("777179")
 style.set_border_width_all(3)
 style.set_corner_radius_all(18)
 style.set_content_margin_all(27)
 plate.add_theme_stylebox_override("panel",style)
 popup.add_child(plate)
 panel_body = VBoxContainer.new()
 panel_body.add_theme_constant_override("separation",20)
 plate.add_child(panel_body)

func reset_popup(title: String) -> void:
 public_active = false
 if popup_tween and popup_tween.is_running(): popup_tween.kill()
 if is_instance_valid(plate):
  plate.get_parent().remove_child(plate);plate.queue_free()
 build_plate()
 plate.position=Vector2(330,85);plate.size=Vector2(620,550)
 panel_body.add_theme_constant_override("separation",20)
 var heading := Label.new()
 heading.text = title
 heading.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
 heading.add_theme_font_size_override("font_size",32)
 panel_body.add_child(heading)
 popup.visible = true
 popup.modulate.a = 0.0
 plate.pivot_offset = plate.size * 0.5
 plate.scale = Vector2(0.92,0.92)
 if popup_tween and popup_tween.is_running(): popup_tween.kill()
 popup_tween = create_tween().set_parallel(true)
 popup_tween.tween_property(popup,"modulate:a",1.0,0.20)
 popup_tween.tween_property(plate,"scale",Vector2.ONE,0.26).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)

func text_line(s: String) -> void:
 var l := Label.new()
 l.text = s
 l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
 l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
 l.add_theme_font_size_override("font_size",23)
 panel_body.add_child(l)

func back_button() -> void:
 var b := Button.new()
 b.name = "Voltar"
 b.text = tr("VOLTAR")
 b.custom_minimum_size = Vector2(0,50)
 b.focus_mode = Control.FOCUS_NONE
 b.add_theme_font_size_override("font_size",24)
 b.pressed.connect(close_popup)
 panel_body.add_child(b)

func close_popup() -> void:
 if settings_from_pause:
  settings_from_pause = false
  if popup_tween and popup_tween.is_running(): popup_tween.kill()
  popup.visible = false
  screen.visible = false
  pause_layer.visible = true
  return
 if game.network.state != "joined": game.network.leave()
 if popup_tween and popup_tween.is_running(): popup_tween.kill()
 popup_tween = create_tween().set_parallel(true)
 popup_tween.tween_property(popup,"modulate:a",0.0,0.16)
 popup_tween.tween_property(plate,"scale",Vector2(.96,.96),0.16)
 popup_tween.chain().tween_callback(func(): popup.visible = false)

func show_settings() -> void:
 reset_popup(tr("CONFIGURAÇÕES"))
 panel_body.add_theme_constant_override("separation",6)
 var mode_label:=Label.new();mode_label.text=tr("Forma de jogo");mode_label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;mode_label.add_theme_font_size_override("font_size",20);panel_body.add_child(mode_label)
 var mode_row:=HBoxContainer.new();mode_row.add_theme_constant_override("separation",8);panel_body.add_child(mode_row)
 var mode_group:=ButtonGroup.new()
 for item in [[tr("MÃOS"),"principal"],[tr("SIMPLES"),"simples"],[tr("2 JOYSTICKS"),"dois_bracos"]]:
  var mode:=Button.new();mode.name="Controle_"+item[1];mode.text=item[0];mode.toggle_mode=true;mode.button_group=mode_group
  mode.button_pressed=game.control_mode==item[1];mode.size_flags_horizontal=Control.SIZE_EXPAND_FILL
  mode.custom_minimum_size=Vector2(0,45);mode.add_theme_font_size_override("font_size",17)
  var mode_selected:=StyleBoxFlat.new();mode_selected.bg_color=Color("8e3335");mode_selected.set_corner_radius_all(7);mode.add_theme_stylebox_override("pressed",mode_selected)
  var current:String=item[1];mode.pressed.connect(func():game.set_control_mode(current));mode_row.add_child(mode)
 text_line(tr("Sensibilidade da câmera"))
 var slider := HSlider.new()
 slider.name = "Sensibilidade"
 slider.min_value = 0.0015
 slider.max_value = 0.008
 slider.step = 0.0005
 slider.value = game.look_sensitivity
 slider.custom_minimum_size = Vector2(0,26)
 slider.value_changed.connect(func(value: float): game.look_sensitivity = value)
 panel_body.add_child(slider)
 text_line(tr("Qualidade dos gráficos"))
 var row := HBoxContainer.new()
 row.add_theme_constant_override("separation",8)
 panel_body.add_child(row)
 var group := ButtonGroup.new()
 for setting in [[tr("BAIXO"),"baixo"],[tr("MÉDIO"),"medio"],[tr("ALTO"),"alto"]]:
  var button := Button.new()
  button.name = "Graficos_" + setting[1]
  button.text = setting[0]
  button.toggle_mode = true
  button.button_group = group
  button.button_pressed = game.graphics_quality == setting[1]
  button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
  button.custom_minimum_size = Vector2(0,44)
  button.focus_mode = Control.FOCUS_NONE
  button.add_theme_font_size_override("font_size",21)
  var selected := StyleBoxFlat.new();selected.bg_color=Color("8e3335");selected.set_corner_radius_all(7)
  button.add_theme_stylebox_override("pressed",selected)
  var value: String = setting[1]
  button.pressed.connect(func(): game.set_graphics_quality(value))
  row.add_child(button)
 var info := Label.new()
 info.text = tr("Mãos: arraste os braços • Simples: andar e botões • 2 joysticks: um para cada braço.")
 info.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
 info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
 info.add_theme_font_size_override("font_size",16)
 panel_body.add_child(info)
 var echo:=CheckButton.new();echo.name="ProtecaoEco";echo.text=tr("Reduzir eco (alto-falante)")
 echo.button_pressed=game.echo_guard_enabled;echo.add_theme_font_size_override("font_size",17)
 echo.toggled.connect(func(v:bool):game.echo_guard_enabled=v;game.save_custom_settings());panel_body.add_child(echo)
 var languages:=compact_button(tr("IDIOMA / LANGUAGE"),"AlterarIdioma")
 languages.custom_minimum_size.y=38;languages.pressed.connect(func():show_language_selection.call_deferred(true));panel_body.add_child(languages)
 back_button()

func open_pause_settings() -> void:
 settings_from_pause = true
 pause_layer.visible = false
 screen.visible = true
 screen.modulate.a = 1.0
 show_settings()

func refresh_language_art()->void:
 var art=frame.get_node_or_null("ImagemDeReferencia") as TextureRect
 if art:art.texture=load("res://textures/menu_"+I18n.language+".jpg")
 var image=loading_layer.get_node_or_null("ImagemCarregamento") as TextureRect
 if image:image.texture=load("res://textures/loading_"+I18n.language+".jpg")
 var tag=frame.get_node_or_null("AvisoPrototipo") as Label
 if tag:tag.text=tr("GILAMOBI • PROTÓTIPO NÃO OFICIAL")
 # Pause controls were created before the language was chosen.
 if is_instance_valid(pause_layer):pause_layer.free()
 build_pause()

func select_language(locale:String,from_settings:bool)->void:
 I18n.set_language(locale);refresh_language_art()
 if is_instance_valid(game.voice_face) and not game.voice_face.active:game.voice_face.status=tr("MIC DESLIGADO")
 if from_settings:show_settings()
 else:show_mode_selection()

func show_language_selection(from_settings:=false)->void:
 reset_popup("IDIOMA • LANGUAGE • ЯЗЫК")
 plate.position=Vector2(55,47);plate.size=Vector2(1170,626)
 panel_body.add_theme_constant_override("separation",14)
 var subtitle:=Label.new();subtitle.text=tr("Escolha o idioma do jogo");subtitle.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
 subtitle.add_theme_font_size_override("font_size",19);panel_body.add_child(subtitle)
 var row:=HBoxContainer.new();row.name="Idiomas";row.add_theme_constant_override("separation",14);panel_body.add_child(row)
 for entry in [["pt","br","Brasil","SELECIONAR"],["en","us","United States","SELECT"],["ru","ru","Россия","ВЫБРАТЬ"]]:
  var card:=PanelContainer.new();card.name="Idioma_"+entry[0];card.size_flags_horizontal=Control.SIZE_EXPAND_FILL
  var style:=StyleBoxFlat.new();style.bg_color=Color("303038");style.border_color=Color("68636b");style.set_border_width_all(2);style.set_corner_radius_all(12);style.set_content_margin_all(12)
  card.add_theme_stylebox_override("panel",style);row.add_child(card)
  var column:=VBoxContainer.new();column.add_theme_constant_override("separation",12);card.add_child(column)
  var flag:=TextureRect.new();flag.name="Bandeira_"+entry[0];flag.texture=load("res://textures/flags/"+entry[1]+".png")
  flag.expand_mode=TextureRect.EXPAND_IGNORE_SIZE;flag.stretch_mode=TextureRect.STRETCH_KEEP_ASPECT_CENTERED;flag.custom_minimum_size=Vector2(0,190);column.add_child(flag)
  var label:=Label.new();label.text=entry[2];label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;label.add_theme_font_size_override("font_size",25);column.add_child(label)
  var choose:=Button.new();choose.name="SelecionarIdioma_"+entry[0];choose.text=entry[3];choose.custom_minimum_size=Vector2(0,56);choose.focus_mode=Control.FOCUS_NONE;choose.add_theme_font_size_override("font_size",21)
  var bstyle:=StyleBoxFlat.new();bstyle.bg_color=Color("a83b3e");bstyle.set_corner_radius_all(8);choose.add_theme_stylebox_override("normal",bstyle)
  var pressed:=bstyle.duplicate();pressed.bg_color=Color("75292d");choose.add_theme_stylebox_override("pressed",pressed)
  var code:String=entry[0];choose.pressed.connect(func():select_language.call_deferred(code,from_settings));column.add_child(choose)
 if from_settings:
  var back:=compact_button(tr("VOLTAR"),"VoltarIdioma");back.pressed.connect(func():show_settings.call_deferred());panel_body.add_child(back)

func show_mode_selection() -> void:
 reset_popup(tr("ESCOLHA SEU MODO DE JOGAR"))
 plate.position=Vector2(55,47);plate.size=Vector2(1170,626)
 panel_body.add_theme_constant_override("separation",12)
 var subtitle:=Label.new();subtitle.text=tr("Escolha como você quer controlar o personagem")
 subtitle.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;subtitle.add_theme_font_size_override("font_size",19)
 subtitle.add_theme_color_override("font_color",Color("c7c6c9"));panel_body.add_child(subtitle)
 var row:=HBoxContainer.new();row.name="ModosDeJogo";row.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 row.add_theme_constant_override("separation",14);panel_body.add_child(row)
 var entries=[
  ["principal",tr("MÃOS"),tr("Arraste as mãos para se mover.")],
  ["simples",tr("SIMPLES"),tr("Joystick para andar e botões.")],
  ["dois_bracos",tr("2 JOYSTICKS"),tr("Um joystick para cada braço.")]
 ]
 for entry in entries:
  var card:=PanelContainer.new();card.name="Modo_"+str(entry[0]);card.size_flags_horizontal=Control.SIZE_EXPAND_FILL
  var card_style:=StyleBoxFlat.new();card_style.bg_color=Color("303038");card_style.border_color=Color("68636b")
  card_style.set_border_width_all(2);card_style.set_corner_radius_all(12);card_style.set_content_margin_all(12)
  card.add_theme_stylebox_override("panel",card_style);row.add_child(card)
  var contents:=VBoxContainer.new();contents.add_theme_constant_override("separation",10);card.add_child(contents)
  var picture:=TextureRect.new();picture.name="Preview_"+str(entry[0]);picture.texture=load("res://textures/mode_previews/"+str(entry[0])+"_clean.png")
  picture.expand_mode=TextureRect.EXPAND_IGNORE_SIZE;picture.stretch_mode=TextureRect.STRETCH_KEEP_ASPECT_CENTERED
  picture.custom_minimum_size=Vector2(0,180);picture.size_flags_horizontal=Control.SIZE_EXPAND_FILL;contents.add_child(picture)
  var name_label:=Label.new();name_label.text=entry[1];name_label.name="NomeModo";name_label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
  name_label.add_theme_font_size_override("font_size",25);contents.add_child(name_label)
  var choose:=Button.new();choose.name="Selecionar_"+str(entry[0]);choose.text=tr("SELECIONAR");choose.custom_minimum_size=Vector2(0,54)
  choose.focus_mode=Control.FOCUS_NONE;choose.add_theme_font_size_override("font_size",21)
  var button_style:=StyleBoxFlat.new();button_style.bg_color=Color("a83b3e");button_style.set_corner_radius_all(8)
  choose.add_theme_stylebox_override("normal",button_style)
  var hover:=button_style.duplicate();hover.bg_color=Color("c24a4c");choose.add_theme_stylebox_override("hover",hover)
  var pressed:=button_style.duplicate();pressed.bg_color=Color("75292d");choose.add_theme_stylebox_override("pressed",pressed)
  var selected_mode:String=entry[0]
  choose.pressed.connect(func():game.set_control_mode(selected_mode);show_cosmetics.call_deferred(true))
  contents.add_child(choose)
  var description:=Label.new();description.text=entry[2];description.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
  description.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART;description.add_theme_font_size_override("font_size",16)
  description.add_theme_color_override("font_color",Color("cdcbce"));contents.add_child(description)
 var footer:=Label.new();footer.text=tr("Você pode mudar o modo depois em Configurações.")
 footer.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;footer.add_theme_font_size_override("font_size",17)
 footer.add_theme_color_override("font_color",Color("b7b3b9"));panel_body.add_child(footer)

func show_cosmetics(startup := false) -> void:
 reset_popup(tr("EDITAR PERFIL"))
 plate.position=Vector2(95,55);plate.size=Vector2(1090,610)
 panel_body.add_theme_constant_override("separation",7)
 var draft:={"color":game.cosmetic_color,"accessory":game.cosmetic_accessory}
 var nick_row:=HBoxContainer.new();nick_row.add_theme_constant_override("separation",14);panel_body.add_child(nick_row)
 var nick_title:=Label.new();nick_title.text=tr("NOME:");nick_title.add_theme_font_size_override("font_size",21);nick_row.add_child(nick_title)
 var nick:=LineEdit.new();nick.name="NomePerfil";nick.text=game.profile_name;nick.placeholder_text=tr("Digite seu nome");nick.max_length=20
 nick.custom_minimum_size=Vector2(0,40);nick.size_flags_horizontal=Control.SIZE_EXPAND_FILL;nick.add_theme_font_size_override("font_size",22);nick_row.add_child(nick)
 var tip:=Label.new();tip.text=tr("até 20 caracteres");tip.add_theme_font_size_override("font_size",15);nick_row.add_child(tip)
 var row:=HBoxContainer.new();row.add_theme_constant_override("separation",20);panel_body.add_child(row)
 var preview_column:=VBoxContainer.new();preview_column.custom_minimum_size=Vector2(370,0);row.add_child(preview_column)
 var preview_title:=Label.new();preview_title.text=tr("SEU GILLA");preview_title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;preview_title.add_theme_font_size_override("font_size",19);preview_column.add_child(preview_title)
 var container:=SubViewportContainer.new();container.name="Preview3D";container.custom_minimum_size=Vector2(370,338);container.stretch=true;preview_column.add_child(container)
 var viewport:=SubViewport.new();viewport.size=Vector2i(370,338);viewport.own_world_3d=true;viewport.render_target_update_mode=SubViewport.UPDATE_ALWAYS;container.add_child(viewport)
 var world:=Node3D.new();viewport.add_child(world)
 var environment:=WorldEnvironment.new();var env:=Environment.new();env.background_mode=Environment.BG_COLOR;env.background_color=Color("313c46");env.ambient_light_source=Environment.AMBIENT_SOURCE_COLOR;env.ambient_light_color=Color.WHITE;env.ambient_light_energy=1.0;environment.environment=env;world.add_child(environment)
 var light:=DirectionalLight3D.new();light.rotation_degrees=Vector3(-35,35,0);light.light_energy=.8;world.add_child(light)
 var model:=load("res://Gilla_PlayerModel.fbx").instantiate() as Node3D;model.name="GillaPreview";model.rotation.y=PI;model.scale=Vector3.ONE*1.45;world.add_child(model)
 var mesh:MeshInstance3D=model.get_node("Gilla_Rig/Skeleton3D/Gilla")
 var camera:=Camera3D.new();world.add_child(camera);camera.position=Vector3(0,1.28,-3.0);camera.look_at(Vector3(0,1.0,0),Vector3.UP);camera.fov=38;camera.current=true
 var options:=VBoxContainer.new();options.size_flags_horizontal=Control.SIZE_EXPAND_FILL;options.add_theme_constant_override("separation",5);row.add_child(options)
 var color_title:=Label.new();color_title.name="CorSelecionada";color_title.add_theme_font_size_override("font_size",19);options.add_child(color_title)
 var palette:=GridContainer.new();palette.columns=5;palette.add_theme_constant_override("h_separation",7);palette.add_theme_constant_override("v_separation",5);options.add_child(palette)
 var helper=preload("res://cosmetics.gd")
 var selection:=Label.new();selection.name="SelecaoAcessorio";selection.add_theme_font_size_override("font_size",16)
 var refresh:=func():
  color_title.text=tr("COR: ")+tr(helper.COLOR_NAMES[draft.color]).to_upper()
  selection.text=tr("Selecionado: ")+tr(helper.NAMES[draft.accessory])
  helper.paint(mesh,draft.color)
  helper.apply_accessory(world,draft.accessory,1)
 for i in 10:
  var swatch:=Button.new();swatch.name="Cor_"+str(i);swatch.text=tr(helper.COLOR_NAMES[i]);swatch.custom_minimum_size=Vector2(93,38);swatch.add_theme_font_size_override("font_size",12 if I18n.language=="ru" else 14)
  var box:=StyleBoxFlat.new();box.bg_color=helper.COLORS[i];box.set_corner_radius_all(7);swatch.add_theme_stylebox_override("normal",box)
  swatch.add_theme_color_override("font_color",Color.BLACK if i in [3,7,9] else Color.WHITE)
  var index:=i;swatch.pressed.connect(func():draft.color=index;refresh.call());palette.add_child(swatch)
 var accessory_title:=Label.new();accessory_title.name="AcessorioSelecionado";accessory_title.text=tr("ACESSÓRIOS • escolha 1");accessory_title.add_theme_font_size_override("font_size",19);options.add_child(accessory_title)
 options.add_child(selection)
 var scroll:=ScrollContainer.new();scroll.name="ListaAcessorios";scroll.custom_minimum_size=Vector2(0,152);scroll.horizontal_scroll_mode=ScrollContainer.SCROLL_MODE_DISABLED;scroll.size_flags_horizontal=Control.SIZE_EXPAND_FILL;options.add_child(scroll)
 var grid:=GridContainer.new();grid.columns=2;grid.size_flags_horizontal=Control.SIZE_EXPAND_FILL;grid.add_theme_constant_override("h_separation",6);grid.add_theme_constant_override("v_separation",5);scroll.add_child(grid)
 for i in helper.NAMES.size():
  var button:=Button.new();button.name="Acessorio_"+str(i);button.text=tr(helper.NAMES[i]);button.custom_minimum_size=Vector2(220,38);button.size_flags_horizontal=Control.SIZE_EXPAND_FILL;button.add_theme_font_size_override("font_size",16)
  var index:int=i;button.pressed.connect(func():draft.accessory=index;refresh.call());grid.add_child(button)
 refresh.call()
 var footer:=HBoxContainer.new();footer.add_theme_constant_override("separation",12);panel_body.add_child(footer)
 if not startup:
  var cancel:=Button.new();cancel.name="CancelarPerfil";cancel.text=tr("VOLTAR");cancel.custom_minimum_size=Vector2(0,47);cancel.size_flags_horizontal=Control.SIZE_EXPAND_FILL;cancel.add_theme_font_size_override("font_size",21);cancel.pressed.connect(close_popup);footer.add_child(cancel)
 var save:=Button.new();save.name="SalvarPerfil";save.text=tr("SALVAR E CONTINUAR") if startup else tr("SALVAR");save.custom_minimum_size=Vector2(0,47);save.size_flags_horizontal=Control.SIZE_EXPAND_FILL;save.add_theme_font_size_override("font_size",21)
 var save_style:=StyleBoxFlat.new();save_style.bg_color=Color("963b3c");save_style.set_corner_radius_all(8);save.add_theme_stylebox_override("normal",save_style)
 footer.add_child(save)
 save.pressed.connect(func():
  var old_name:String=game.profile_name;var old_color:int=game.cosmetic_color;var old_accessory:int=game.cosmetic_accessory
  game.profile_name=preload("res://profile_name.gd").clean(nick.text)
  var error:int=game.set_cosmetics(draft.color,draft.accessory)
  if error!=OK:
   game.profile_name=old_name;game.set_cosmetics(old_color,old_accessory)
   tip.text=tr("Não foi possível salvar. Tente novamente.");tip.add_theme_color_override("font_color",Color(1,.4,.4));return
  nick.release_focus()
  close_popup()
 )

func show_vr_setup() -> void:
 if OS.has_feature("web"):
  game.g2_xr.enter_vr()
  return
 reset_popup(tr("MODO VR"))
 var toggle:=compact_button(tr("DESATIVAR MODO VR") if vr_enabled else tr("ATIVAR MODO VR"), "AlternarModoVR")
 toggle.custom_minimum_size.y=65
 toggle.pressed.connect(func():
  vr_enabled=not vr_enabled
  var cfg:=ConfigFile.new();cfg.set_value("vr","ativo",vr_enabled);cfg.save("user://modo_vr.cfg")
  toggle.text=tr("DESATIVAR MODO VR") if vr_enabled else tr("ATIVAR MODO VR")
 )
 panel_body.add_child(toggle)
 back_button()

func stop_vr() -> void:
 var vr=game.get_node_or_null("VRBox")
 if is_instance_valid(vr) and not vr.is_queued_for_deletion():
  vr.ending=true
  vr.queue_free()

func show_credits() -> void:
 reset_popup(tr("CRÉDITOS"))
 panel_body.add_theme_constant_override("separation",14)
 var creator_row:=HBoxContainer.new()
 creator_row.name="CriadorFanGame"
 creator_row.alignment=BoxContainer.ALIGNMENT_CENTER
 creator_row.add_theme_constant_override("separation",18)
 panel_body.add_child(creator_row)
 var picture:=TextureRect.new()
 picture.name="FotoKinawJr"
 picture.texture=load("res://textures/kinawjr_avatar.png")
 picture.custom_minimum_size=Vector2(78,78)
 picture.expand_mode=TextureRect.EXPAND_IGNORE_SIZE
 picture.stretch_mode=TextureRect.STRETCH_KEEP_ASPECT_CENTERED
 creator_row.add_child(picture)
 var creator:=Label.new()
 creator.name="NomeCriador"
 creator.text=tr("Criador da fangame\nKinawJr")
 creator.vertical_alignment=VERTICAL_ALIGNMENT_CENTER
 creator.add_theme_font_size_override("font_size",23)
 creator.add_theme_font_override("font",load("res://fonts/LilitaOne-Regular.ttf"))
 creator_row.add_child(creator)
 var disclaimer:=Label.new();disclaimer.text=tr("GilaMobi é um protótipo não oficial feito por fãs, sem afiliação ao jogo original.");disclaimer.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART;disclaimer.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;disclaimer.add_theme_font_size_override("font_size",18);panel_body.add_child(disclaimer)
 var credit:=Label.new();credit.name="CreditosModeloMonstro";credit.text=tr("Modelo do monstro: Grinner from gilla, por reduxlethalape. Adaptado para o jogo. Licença CC BY 4.0.");credit.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART;credit.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;credit.add_theme_font_size_override("font_size",18);panel_body.add_child(credit)
 for item in [[tr("Fonte do modelo (Sketchfab)"),"https://sketchfab.com/3d-models/grinner-from-gilla-ab23e1c30c8447e49537deae7ccc6353"],[tr("Licença Creative Commons BY 4.0"),"https://creativecommons.org/licenses/by/4.0/"]]:
  var link:=LinkButton.new();link.text=item[0];link.uri=item[1];link.add_theme_font_size_override("font_size",17);link.size_flags_horizontal=Control.SIZE_SHRINK_CENTER;panel_body.add_child(link)
 back_button()

func compact_button(text: String, node_name: String) -> Button:
 var b := Button.new();b.name=node_name;b.text=text;b.focus_mode=Control.FOCUS_NONE
 b.custom_minimum_size=Vector2(0,48);b.add_theme_font_size_override("font_size",20)
 return b

func show_multiplayer() -> void:
 game.directory.start()
 reset_popup(tr("JOGAR"))
 panel_body.add_theme_constant_override("separation",6)
 var role_label:=Label.new();role_label.text=tr("Infecção: o monstro é sorteado na sala");role_label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;role_label.add_theme_font_size_override("font_size",17);panel_body.add_child(role_label)
 var solo:=compact_button(tr("JOGAR SOZINHO"), "JogarSozinho");solo.pressed.connect(show_solo_maps);panel_body.add_child(solo)
 var sandbox:=compact_button("SANDBOX","Sandbox");sandbox.pressed.connect(show_sandbox);panel_body.add_child(sandbox)
 var create := compact_button(tr("CRIAR SALA PRIVADA"), "CriarSala");panel_body.add_child(create)
 var row := HBoxContainer.new();row.add_theme_constant_override("separation",10);panel_body.add_child(row)
 var input := LineEdit.new();input.name="CodigoSala";input.placeholder_text=tr("CÓDIGO DE 6 DÍGITOS")
 input.max_length=6;input.virtual_keyboard_type=LineEdit.KEYBOARD_TYPE_NUMBER;input.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 input.custom_minimum_size=Vector2(0,48);input.add_theme_font_size_override("font_size",20);row.add_child(input)
 var join := compact_button(tr("ENTRAR"), "EntrarSala");join.custom_minimum_size.x=140;row.add_child(join)
 var row2 := HBoxContainer.new();row2.add_theme_constant_override("separation",10);panel_body.add_child(row2)
 var browse := compact_button(tr("SERVIDORES PÚBLICOS"), "VerServidores");browse.size_flags_horizontal=Control.SIZE_EXPAND_FILL;row2.add_child(browse)
 var create_public := compact_button(tr("CRIAR SERVIDOR"), "CriarServidor");create_public.size_flags_horizontal=Control.SIZE_EXPAND_FILL;row2.add_child(create_public)
 var info := Label.new();info.name="StatusSala";info.text=tr("Teste público: não envie informações pessoais.")
 info.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;info.add_theme_font_size_override("font_size",16);panel_body.add_child(info)
 create.pressed.connect(show_private_maps)
 join.pressed.connect(func():
  var code := input.text.strip_edges()
  if code.length()!=6 or not code.is_valid_int() or code.begins_with("-") or code.begins_with("+"):
   info.text=tr("Digite os 6 números da sala.");return
  info.text=tr("Procurando sala...");game.network.begin("join",code)
 )
 browse.pressed.connect(show_public_servers)
 create_public.pressed.connect(show_create_server)
 back_button()

func show_sandbox()->void:
 reset_popup("SANDBOX")
 var info:=Label.new();info.name="StatusSala";info.text=tr("Escolha como jogar no Sandbox.")
 info.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;info.add_theme_font_size_override("font_size",20);panel_body.add_child(info)
 var offline:=compact_button(tr("OFFLINE • COM MANEQUINS"),"SandboxOffline")
 offline.pressed.connect(func():game.network.leave();game.selected_map="sandbox";game.selected_character="normal";play());panel_body.add_child(offline)
 var online:=compact_button(tr("ONLINE • SALA PÚBLICA"),"SandboxOnline")
 online.pressed.connect(func():game.selected_map="sandbox";info.text=tr("Conectando...");game.network.begin("sandbox"));panel_body.add_child(online)
 var back:=compact_button(tr("VOLTAR"),"VoltarSandbox");back.pressed.connect(func():
  if game.network.mode=="sandbox" and game.network.state!="joined":game.network.leave()
  show_multiplayer());panel_body.add_child(back)

func map_picker() -> OptionButton:
 var row:=HBoxContainer.new();row.add_theme_constant_override("separation",12);panel_body.add_child(row)
 var title:=Label.new();title.text=tr("Mapa:");title.add_theme_font_size_override("font_size",21);row.add_child(title)
 var options:=OptionButton.new();options.name="EscolhaMapa";options.add_item(tr("A Base"));options.add_item("Backrooms")
 options.select(1 if game.selected_map=="backrooms" else 0)
 options.size_flags_horizontal=Control.SIZE_EXPAND_FILL;options.custom_minimum_size=Vector2(0,48);options.add_theme_font_size_override("font_size",20);row.add_child(options)
 return options

func chosen_map(options:OptionButton)->String:
 return "backrooms" if options.selected==1 else "base"

func show_solo_maps()->void:
 reset_popup(tr("JOGAR SOZINHO"))
 var maps:=map_picker()
 var go:=compact_button(tr("ENTRAR NO MAPA"),"ConfirmarSolo");panel_body.add_child(go)
 go.pressed.connect(func():game.selected_map=chosen_map(maps);play_solo())
 var back:=compact_button(tr("VOLTAR"),"VoltarMapas");back.pressed.connect(show_multiplayer);panel_body.add_child(back)

func show_private_maps()->void:
 reset_popup(tr("CRIAR SALA PRIVADA"))
 var maps:=map_picker()
 var info:=Label.new();info.name="StatusSala";info.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;info.text=tr("Escolha o mapa da sua sala.");panel_body.add_child(info)
 var go:=compact_button(tr("CRIAR E JOGAR"),"ConfirmarSalaPrivada");panel_body.add_child(go)
 go.pressed.connect(func():game.selected_map=chosen_map(maps);info.text=tr("Conectando...");game.network.begin("create"))
 var back:=compact_button(tr("VOLTAR"),"VoltarMapas");back.pressed.connect(show_multiplayer);panel_body.add_child(back)

func show_gilabr_maps()->void:
 reset_popup(tr("LIGAR GILABR"))
 var maps:=map_picker()
 var info:=Label.new();info.name="StatusSala";info.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;info.text=tr("Escolha o mapa do servidor.");panel_body.add_child(info)
 var go:=compact_button(tr("LIGAR E JOGAR"),"ConfirmarGilaBr");panel_body.add_child(go)
 go.pressed.connect(func():
  if game.directory.is_gilabr_online():info.text=tr("GilaBr já está online.");return
  game.selected_map=chosen_map(maps);info.text=tr("Conectando...");game.network.begin("create","","GilaBr",10,true)
 )
 var back:=compact_button(tr("VOLTAR"),"VoltarMapas");back.pressed.connect(show_public_servers);panel_body.add_child(back)

func show_create_server() -> void:
 game.directory.start()
 reset_popup(tr("CRIAR SERVIDOR PÚBLICO"))
 panel_body.add_theme_constant_override("separation",10)
 var label := Label.new();label.text=tr("Nome do servidor");label.add_theme_font_size_override("font_size",19);panel_body.add_child(label)
 var name_input := LineEdit.new();name_input.name="NomeServidor";name_input.placeholder_text=tr("Digite o nome");name_input.max_length=24
 name_input.custom_minimum_size=Vector2(0,48);name_input.add_theme_font_size_override("font_size",21);panel_body.add_child(name_input)
 var row := HBoxContainer.new();row.add_theme_constant_override("separation",20);panel_body.add_child(row)
 var players := Label.new();players.text=tr("Máximo de jogadores:");players.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 players.add_theme_font_size_override("font_size",20);row.add_child(players)
 var limit := SpinBox.new();limit.name="LimiteJogadores";limit.min_value=2;limit.max_value=10;limit.value=10;limit.step=1
 limit.custom_minimum_size=Vector2(150,48);row.add_child(limit)
 var maps:=map_picker()
 var status := Label.new();status.name="StatusSala";status.text=tr("Fica online enquanto seu jogo estiver aberto.")
 status.add_theme_font_size_override("font_size",16);status.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;panel_body.add_child(status)
 var create := compact_button(tr("CRIAR E JOGAR"),"ConfirmarServidor");panel_body.add_child(create)
 create.pressed.connect(func():
  var n := name_input.text.strip_edges().replace("\n"," ").replace("\r"," ")
  if n.is_empty(): status.text=tr("Digite um nome para o servidor.");return
  if n.to_lower()=="gilabr": status.text=tr("GilaBr é reservado na lista.");return
  game.selected_map=chosen_map(maps);status.text=tr("Conectando...");game.network.begin("create","",n,int(limit.value),true)
 )
 var back := compact_button(tr("VOLTAR"),"VoltarServidores");back.pressed.connect(show_multiplayer);panel_body.add_child(back)

func show_public_servers() -> void:
 game.directory.start()
 reset_popup(tr("SERVIDORES PÚBLICOS"))
 panel_body.add_theme_constant_override("separation",8)
 var info := Label.new();info.name="StatusSala";info.text=tr("A lista pode levar alguns segundos para atualizar.")
 info.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;info.add_theme_font_size_override("font_size",16);panel_body.add_child(info)
 var scroll := ScrollContainer.new();scroll.custom_minimum_size=Vector2(0,205);scroll.size_flags_vertical=Control.SIZE_EXPAND_FILL
 scroll.horizontal_scroll_mode=ScrollContainer.SCROLL_MODE_DISABLED;panel_body.add_child(scroll)
 public_list=VBoxContainer.new();public_list.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 public_list.add_theme_constant_override("separation",6);scroll.add_child(public_list)
 var row := HBoxContainer.new();row.add_theme_constant_override("separation",8);panel_body.add_child(row)
 var create := compact_button(tr("CRIAR SERVIDOR"),"CriarServidorLista");create.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 create.pressed.connect(show_create_server);row.add_child(create)
 var back := compact_button(tr("VOLTAR"),"VoltarLista");back.size_flags_horizontal=Control.SIZE_EXPAND_FILL;back.pressed.connect(show_multiplayer);row.add_child(back)
 public_active = true
 _refresh_public_list()

func _server_row(name: String, code: String, count: int, capacity: int, offline: bool, map_name: String = "base") -> void:
 var row := HBoxContainer.new();row.add_theme_constant_override("separation",8);public_list.add_child(row)
 var label := Label.new();label.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 label.text = name + (tr("   •   OFFLINE") if offline else "   •   %d/%d • %s" % [count,capacity,"Backrooms" if map_name=="backrooms" else "Base"])
 label.add_theme_font_size_override("font_size",18);row.add_child(label)
 var action := compact_button(tr("LIGAR") if offline else tr("ENTRAR"), "AcaoServidor");action.custom_minimum_size=Vector2(118,44);row.add_child(action)
 action.disabled = not offline and count >= capacity
 action.pressed.connect(func():
  var status: Label = panel_body.get_node("StatusSala")
  if offline:
   if game.directory.is_gilabr_online(): status.text=tr("GilaBr já está online. Atualizando...");return
   show_gilabr_maps()
  else:
   status.text=tr("Entrando em ") + name + "...";game.network.begin("join",code)
 )

func _refresh_public_list() -> void:
 if not public_active or not is_instance_valid(public_list) or not popup.visible: return
 for child in public_list.get_children(): public_list.remove_child(child);child.queue_free()
 var sandbox_row:=HBoxContainer.new();public_list.add_child(sandbox_row)
 var sandbox_label:=Label.new();sandbox_label.text=tr("SANDBOX • SALA PÚBLICA");sandbox_label.size_flags_horizontal=Control.SIZE_EXPAND_FILL;sandbox_label.add_theme_font_size_override("font_size",18);sandbox_row.add_child(sandbox_label)
 var sandbox_entry:=compact_button(tr("ENTRAR"),"EntrarSandboxPublico");sandbox_entry.custom_minimum_size=Vector2(118,44);sandbox_row.add_child(sandbox_entry)
 sandbox_entry.pressed.connect(func():game.selected_map="sandbox";panel_body.get_node("StatusSala").text=tr("Conectando...");game.network.begin("sandbox"))
 var gilabr = null
 var other := []
 for entry in game.directory.entries.values():
  if str(entry.name).to_lower()=="gilabr" and gilabr==null:gilabr=entry
  elif str(entry.name).to_lower()!="gilabr":other.append(entry)
 if gilabr==null: _server_row("GilaBr", "", 0, 10, true)
 else: _server_row("GilaBr",gilabr.code,int(gilabr.count),int(gilabr.capacity),false,str(gilabr.get("map","base")))
 other.sort_custom(func(a,b): return str(a.name).naturalnocasecmp_to(str(b.name))<0)
 for entry in other.slice(0,30): _server_row(str(entry.name),str(entry.code),int(entry.count),int(entry.capacity),false,str(entry.get("map","base")))

func _room_ready(_code: String) -> void:
 if game.network.mode == "join" and initialized and is_instance_valid(game.player):
  game.player.position = Vector3(2,0.04,2)
 play()

func _room_error(message: String) -> void:
 if popup.visible and panel_body.has_node("StatusSala"):
  panel_body.get_node("StatusSala").text = message

func _room_closed(message: String) -> void:
 if game.game_running:
  pause_game()
  return_to_menu()
 show_multiplayer()
 if panel_body.has_node("StatusSala"): panel_body.get_node("StatusSala").text = message

func play_solo() -> void:
 game.network.leave()
 game.selected_character="normal"
 play()

func build_loading() -> void:
 loading_layer = Control.new()
 loading_layer.name = "Carregamento"
 loading_layer.size = ART_SIZE
 loading_layer.visible = false
 loading_layer.mouse_filter = Control.MOUSE_FILTER_STOP
 frame.add_child(loading_layer)
 var bg := TextureRect.new()
 bg.name="ImagemCarregamento"
 bg.texture = load("res://textures/loading_"+I18n.language+".jpg")
 bg.size = ART_SIZE
 bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
 bg.stretch_mode = TextureRect.STRETCH_SCALE
 bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
 loading_layer.add_child(bg)
 # An unobtrusive dark strip improves legibility over the photograph.
 var strip := ColorRect.new()
 strip.color = Color(0.04,0.04,0.05,.73)
 strip.position = Vector2(0,600)
 strip.size = Vector2(1280,120)
 strip.mouse_filter = Control.MOUSE_FILTER_IGNORE
 loading_layer.add_child(strip)
 var title := Label.new()
 title.text = "GILAMOBI"
 title.position = Vector2(340,310)
 title.size = Vector2(600,58)
 title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
 title.add_theme_font_size_override("font_size",42)
 title.add_theme_color_override("font_color",Color.WHITE)
 title.add_theme_color_override("font_outline_color",Color(0,0,0,.8))
 title.add_theme_constant_override("outline_size",5)
 loading_layer.add_child(title)
 loading_text = Label.new()
 loading_text.position = Vector2(150,614)
 loading_text.size = Vector2(980,30)
 loading_text.text = tr("Preparando...")
 loading_text.add_theme_font_size_override("font_size",20)
 loading_layer.add_child(loading_text)
 loading_bar = ProgressBar.new()
 loading_bar.position = Vector2(150,655)
 loading_bar.size = Vector2(980,16)
 loading_bar.show_percentage = false
 loading_bar.min_value = 0; loading_bar.max_value = 100
 var back := StyleBoxFlat.new();back.bg_color=Color("333235");back.set_corner_radius_all(8)
 var fill := StyleBoxFlat.new();fill.bg_color=Color("ee3030");fill.set_corner_radius_all(8)
 loading_bar.add_theme_stylebox_override("background",back)
 loading_bar.add_theme_stylebox_override("fill",fill)
 loading_layer.add_child(loading_bar)

func set_loading_progress(value: float, label: String) -> void:
 loading_text.text = label
 loading_bar.value = clampf(value,0.0,1.0) * 100.0

func build_pause() -> void:
 pause_layer = Control.new()
 pause_layer.name = "Pausa"
 pause_layer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 pause_layer.visible = false
 pause_layer.mouse_filter = Control.MOUSE_FILTER_STOP
 add_child(pause_layer)
 var shade := ColorRect.new();shade.color=Color(0,0,0,.70)
 shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 pause_layer.add_child(shade)
 var center := CenterContainer.new()
 center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 pause_layer.add_child(center)
 var box := PanelContainer.new()
 box.name = "Cartao"
 box.custom_minimum_size = Vector2(420,340)
 var style := StyleBoxFlat.new();style.bg_color=Color("202025")
 style.border_color=Color("a63b3b");style.set_border_width_all(3)
 style.set_corner_radius_all(18);style.set_content_margin_all(25)
 box.add_theme_stylebox_override("panel",style);center.add_child(box)
 var column := VBoxContainer.new();column.add_theme_constant_override("separation",18);box.add_child(column)
 var header := Label.new();header.text=tr("JOGO PAUSADO");header.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
 header.add_theme_font_size_override("font_size",32);column.add_child(header)
 var line := Label.new();line.text=tr("Faça uma pausa e volte quando quiser.");line.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
 line.add_theme_font_size_override("font_size",18);column.add_child(line)
 var resume := Button.new();resume.name="Continuar";resume.text=tr("CONTINUAR");resume.custom_minimum_size=Vector2(0,60)
 resume.add_theme_font_size_override("font_size",23);resume.pressed.connect(resume_game);column.add_child(resume)
 server_stop = Button.new();server_stop.name="DesligarServidor";server_stop.text=tr("DESLIGAR SERVIDOR");server_stop.custom_minimum_size=Vector2(0,55)
 server_stop.add_theme_font_size_override("font_size",21);server_stop.pressed.connect(return_to_menu);column.add_child(server_stop)
 var settings := Button.new();settings.name="ConfiguracoesPausa";settings.text=tr("CONFIGURAÇÕES");settings.custom_minimum_size=Vector2(0,55)
 settings.add_theme_font_size_override("font_size",21);settings.pressed.connect(open_pause_settings);column.add_child(settings)
 var go_menu := Button.new();go_menu.name="MenuInicial";go_menu.text=tr("VOLTAR AO MENU");go_menu.custom_minimum_size=Vector2(0,60)
 go_menu.add_theme_font_size_override("font_size",23);go_menu.pressed.connect(return_to_menu);column.add_child(go_menu)

func play() -> void:
 if busy: return
 busy = true
 # Solo and the waiting room always start as Gilla; infection picks roles online.
 if game.network.state != "joined" or game.round_id < 0:game.selected_character="normal"
 popup.visible = false
 loading_layer.visible = true
 loading_layer.modulate.a = 0.0
 var fade := create_tween()
 fade.tween_property(loading_layer,"modulate:a",1.0,0.18)
 await fade.finished
 if not initialized:
  await game.initialize_game()
  initialized = true
 else:
  game.load_map(game.selected_map)
  if game.selected_character != game.active_character:
   set_loading_progress(0.5, tr("Trocando personagem..."))
   await get_tree().process_frame
   game.apply_character_choice()
  set_loading_progress(1.0, tr("Voltando para o jogo..."))
  await get_tree().create_timer(.25).timeout
 screen.visible = false
 loading_layer.visible = false
 if game.network.state == "joined" and game.network.mode == "join":
  game.player.position = Vector3(2,0.04,2)
 elif game.network.state=="joined" and game.network.mode=="sandbox":
  var slot:int=absi(int(game.network.uid.hash()))%12
  game.player.position=Vector3(float(slot%4)*2.2-3.3,.04,float(slot/4)*2.0+2.0)
 game.game_running = true
 if game.selected_map=="sandbox" and game.network.state!="joined":game.setup_sandbox_npcs()
 if OS.has_feature("web"):vr_enabled=false
 if vr_enabled:
  game.ui.reset_inputs();game.ui.visible=false
  var vr=preload("res://vr_box.gd").new();vr.name="VRBox";vr.game=game;game.add_child(vr)
 else:
  game.ui.visible = true
 busy = false
 game.apply_pending_round.call_deferred()

func pause_game() -> void:
 if not initialized or not game.game_running: return
 if is_instance_valid(game.sandbox_camera):game.sandbox_camera.stop_recording()
 game.game_running = false
 game.hand_dragging = [false,false]
 game.ui.reset_inputs()
 game.voice_face.stop_mic()
 game.ui.visible = false
 server_stop.visible = game.network.state == "joined" and game.network.mode == "create" and game.network.public_room
 pause_layer.modulate.a = 0.0
 pause_layer.visible = true
 var tw := create_tween()
 tw.tween_property(pause_layer,"modulate:a",1.0,.20)

func resume_game() -> void:
 pause_layer.visible = false
 game.game_running = true
 game.ui.visible = true
 game.apply_pending_round.call_deferred()

func return_to_menu() -> void:
 stop_vr()
 settings_from_pause = false
 if is_instance_valid(game.sandbox_camera):game.sandbox_camera.stop_recording()
 game.network.leave()
 game.game_running = false
 if initialized:
  game.reset_health()
  game.player.velocity = Vector3.ZERO
  game.hand_dragging = [false,false]
  game.ui.reset_inputs()
  game.voice_face.stop_mic()
  game.ui.visible = false
 pause_layer.visible = false
 popup.visible = false
 loading_layer.visible = false
 screen.visible = true
 screen.modulate.a = 0.0
 var tw := create_tween()
 tw.tween_property(screen,"modulate:a",1.0,.25)

func exit_game() -> void:
 get_tree().quit()
