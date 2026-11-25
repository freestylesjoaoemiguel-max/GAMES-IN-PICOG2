extends Control
# Opening runs only once, at application launch. All coordinates use a
# 1280x720 stage which is uniformly scaled for landscape phones.
signal opening_finished
const ART_SIZE := Vector2(1280,720)
var stage: Control
var avatar: TextureRect
var creator: Label
var title: Control
var title_top: Label
var title_bottom: Label
var burst: Control
var audio: AudioStreamPlayer
var pop_sound: AudioStream
var whoosh_sound: AudioStream
var impact_sound: AudioStream

func _ready() -> void:
 set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 mouse_filter=Control.MOUSE_FILTER_STOP
 var black:=ColorRect.new()
 black.color=Color.BLACK
 black.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 black.mouse_filter=Control.MOUSE_FILTER_IGNORE
 add_child(black)
 stage=Control.new()
 stage.name="PalcoAbertura"
 stage.size=ART_SIZE
 stage.mouse_filter=Control.MOUSE_FILTER_IGNORE
 add_child(stage)
 var fun_font=load("res://fonts/LilitaOne-Regular.ttf")
 avatar=TextureRect.new()
 avatar.name="FotoCriador"
 avatar.texture=load("res://textures/kinawjr_avatar.png")
 avatar.expand_mode=TextureRect.EXPAND_IGNORE_SIZE
 avatar.stretch_mode=TextureRect.STRETCH_KEEP_ASPECT_CENTERED
 avatar.position=Vector2(515,-270)
 avatar.size=Vector2(250,250)
 avatar.pivot_offset=Vector2(125,0)
 stage.add_child(avatar)
 creator=Label.new()
 creator.name="NomeCriador"
 creator.text="KinawJr"
 creator.position=Vector2(1330,438)
 creator.size=Vector2(440,75)
 creator.pivot_offset=Vector2(220,37.5)
 creator.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
 creator.vertical_alignment=VERTICAL_ALIGNMENT_CENTER
 creator.add_theme_font_override("font",fun_font)
 creator.add_theme_font_size_override("font_size",53)
 creator.add_theme_color_override("font_color",Color("fff5e2"))
 creator.add_theme_color_override("font_outline_color",Color("5f2024"))
 creator.add_theme_constant_override("outline_size",4)
 creator.add_theme_color_override("font_shadow_color",Color("9c3036"))
 creator.add_theme_constant_override("shadow_offset_y",5)
 stage.add_child(creator)
 # Two independently styled lines: Gilla above, Mob below and to the right.
 title=Control.new()
 title.name="NomeDoJogo"
 title.position=Vector2(340,-390)
 title.size=Vector2(600,250)
 title.pivot_offset=Vector2(300,250)
 title.mouse_filter=Control.MOUSE_FILTER_IGNORE
 stage.add_child(title)
 title_top=make_title_label("Gilla",fun_font,150,Color("fff4df"),Color("9b202f"))
 title_top.name="Gilla";title_top.position=Vector2(0,-15);title_top.size=Vector2(440,155);title.add_child(title_top)
 title_bottom=make_title_label("Mob",fun_font,112,Color("ffcb46"),Color("a82d2e"))
 title_bottom.name="Mob";title_bottom.position=Vector2(270,139);title_bottom.size=Vector2(330,130);title.add_child(title_bottom)
 # Draw comic impact lines that briefly flare at the exact point of contact.
 burst=Control.new();burst.name="Impacto";burst.size=ART_SIZE;burst.mouse_filter=Control.MOUSE_FILTER_IGNORE;burst.modulate.a=0;stage.add_child(burst)
 for entry in [Vector2(-1,-.25),Vector2(-.8,.38),Vector2(-.45,-.85),Vector2(1,-.25),Vector2(.8,.38),Vector2(.45,-.85)]:
  var ray:=Line2D.new();ray.width=7;ray.default_color=Color("ffd75e");ray.begin_cap_mode=Line2D.LINE_CAP_ROUND;ray.end_cap_mode=Line2D.LINE_CAP_ROUND
  var d:Vector2=entry.normalized();ray.add_point(Vector2(640,172)+d*150);ray.add_point(Vector2(640,172)+d*205);burst.add_child(ray)
 audio=AudioStreamPlayer.new();audio.name="SonsDaAbertura";audio.volume_db=-12.0;add_child(audio)
 pop_sound=load("res://sounds/intro_pop.wav");whoosh_sound=load("res://sounds/intro_whoosh.wav");impact_sound=load("res://sounds/intro_impact.wav")
 resized.connect(layout_stage)
 layout_stage()
 animate_opening()

func make_title_label(words:String,font:Font,font_size:int,fill:Color,outline:Color)->Label:
 var label:=Label.new();label.text=words
 label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;label.vertical_alignment=VERTICAL_ALIGNMENT_CENTER
 label.add_theme_font_override("font",font);label.add_theme_font_size_override("font_size",font_size)
 label.add_theme_color_override("font_color",fill);label.add_theme_color_override("font_outline_color",outline)
 label.add_theme_constant_override("outline_size",8)
 label.add_theme_color_override("font_shadow_color",Color(.24,.06,.07,.95))
 label.add_theme_constant_override("shadow_offset_x",6);label.add_theme_constant_override("shadow_offset_y",8)
 return label

func layout_stage()->void:
 var s:=minf(size.x/ART_SIZE.x,size.y/ART_SIZE.y)
 if s<=0:return
 stage.scale=Vector2(s,s)
 stage.position=(size-ART_SIZE*s)*.5

func play_sound(sound:AudioStream)->void:
 if sound!=null:audio.stream=sound;audio.play()

func hit()->void:
 play_sound(impact_sound)
 burst.modulate.a=1.0
 var flash:=create_tween()
 flash.tween_property(burst,"modulate:a",0.0,.42)

func animate_opening()->void:
 var tw:=create_tween()
 tw.tween_interval(.4)
 tw.tween_property(avatar,"position:y",165.0,1.05).set_trans(Tween.TRANS_BOUNCE).set_ease(Tween.EASE_OUT)
 tw.tween_callback(play_sound.bind(pop_sound))
 tw.tween_interval(.35)
 tw.tween_property(creator,"position:x",420.0,.95).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
 tw.tween_interval(1.55)
 tw.tween_callback(play_sound.bind(whoosh_sound))
 # Bottom edge of title reaches top of picture at y=165: real contact.
 tw.tween_property(title,"position:y",-85.0,1.05).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
 tw.tween_callback(hit)
 # Squash on impact, then travel together so the logo pushes the picture.
 tw.tween_property(title,"scale",Vector2(1.06,.94),.11)
 tw.parallel().tween_property(avatar,"scale",Vector2(1.06,.92),.11)
 tw.tween_property(title,"position:y",-43.0,.18).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
 tw.parallel().tween_property(avatar,"position:y",207.0,.18).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
 tw.parallel().tween_property(title,"scale",Vector2.ONE,.18)
 tw.parallel().tween_property(avatar,"scale",Vector2.ONE,.18)
 # Picture touches the name. All three now move the same distance together.
 tw.tween_property(title,"position:y",550.0,.85).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
 tw.parallel().tween_property(avatar,"position:y",800.0,.85).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
 tw.parallel().tween_property(creator,"position:y",1031.0,.85).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
 tw.parallel().tween_property(creator,"rotation",deg_to_rad(-8),.85)
 # Once they're gone, the game name springs back and settles in the middle.
 tw.tween_property(title,"position:y",235.0,.95).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
 tw.tween_interval(2.0)
 tw.tween_callback(func():opening_finished.emit())
 tw.tween_property(self,"modulate:a",0.0,1.0)
 tw.tween_callback(queue_free)
