extends CanvasLayer
# Experimental on-device MediaPipe hand landmark preview.
var menu: Node
var camera_extension
var feed
var camera_texture
var last_sequence:=-1
var preview_time:=0.0
var views: Array[TextureRect]=[]
var overlays: Array[Control]=[]
var hand_tracker
var legacy_mode:=false
var mode_button:Button
var poll_time:=0.0
var status: Label
var retry: Button
var elapsed:=0.0
var closed:=false

func _ready()->void:
 layer=45
 var base:=Control.new();base.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);add_child(base)
 var background:=ColorRect.new();background.color=Color("10151b");background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);base.add_child(background)
 for i in 2:
  var view:=TextureRect.new();view.name="CameraOlho"+str(i);view.expand_mode=TextureRect.EXPAND_IGNORE_SIZE;view.stretch_mode=TextureRect.STRETCH_KEEP_ASPECT_CENTERED
  view.anchor_left=.5*i;view.anchor_right=.5*(i+1);view.anchor_top=.14;view.anchor_bottom=.82;view.mouse_filter=Control.MOUSE_FILTER_IGNORE;base.add_child(view);views.append(view)
  var overlay=preload("res://hand_overlay.gd").new();overlay.name="PontosDasMaos";overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);overlay.mouse_filter=Control.MOUSE_FILTER_IGNORE;view.add_child(overlay);overlays.append(overlay)
 var divider:=ColorRect.new();divider.color=Color(1,1,1,.18);divider.anchor_left=.5;divider.anchor_right=.5;divider.offset_left=-1;divider.offset_right=1;divider.anchor_top=.14;divider.anchor_bottom=.82;base.add_child(divider)
 status=Label.new();status.name="CameraStatus";status.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE);status.offset_top=10;status.offset_bottom=78
 status.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;status.vertical_alignment=VERTICAL_ALIGNMENT_CENTER;status.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART;status.add_theme_font_size_override("font_size",23);base.add_child(status)
 var buttons:=HBoxContainer.new();buttons.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE);buttons.offset_top=-88;buttons.offset_bottom=-14;buttons.offset_left=30;buttons.offset_right=-30;buttons.add_theme_constant_override("separation",18);base.add_child(buttons)
 retry=Button.new();retry.name="TentarNovamente";retry.text=tr("TENTAR NOVAMENTE");retry.size_flags_horizontal=Control.SIZE_EXPAND_FILL;retry.add_theme_font_size_override("font_size",21);retry.pressed.connect(start_test);buttons.add_child(retry)
 mode_button=Button.new();mode_button.name="AlternarModo";mode_button.text=tr("MODO ANTERIOR");mode_button.size_flags_horizontal=Control.SIZE_EXPAND_FILL;mode_button.add_theme_font_size_override("font_size",21);mode_button.pressed.connect(toggle_mode);buttons.add_child(mode_button)
 var exit:=Button.new();exit.name="SairTesteCamera";exit.text=tr("FECHAR CÂMERA");exit.size_flags_horizontal=Control.SIZE_EXPAND_FILL;exit.add_theme_font_size_override("font_size",21);exit.pressed.connect(close_test);buttons.add_child(exit)
 start_test()

func toggle_mode()->void:
 legacy_mode=not legacy_mode
 mode_button.text=tr("MODO RÁPIDO") if legacy_mode else tr("MODO ANTERIOR")
 start_test()

func start_test()->void:
 if closed:return
 if OS.get_name()!="Android":
  status.text=tr("Teste disponível no Android. A câmera não é aberta neste computador.");return
 if not Engine.has_singleton("GodotCameraServer") or not ClassDB.class_exists("CameraServerExtension"):
  status.text=tr("Plugin da câmera não carregou. Feche e conte o que aconteceu.");return
 if camera_extension==null:
  camera_extension=ClassDB.instantiate("CameraServerExtension")
  camera_extension.permission_result.connect(_permission_result)
 if not camera_extension.permission_granted():
  status.text=tr("Permita o uso da câmera para iniciar este teste.")
  camera_extension.request_permission();return
 open_rear_camera()

func _permission_result(granted:bool)->void:
 if closed:return
 if granted:call_deferred("open_rear_camera")
 else:status.text=tr("Permissão negada. Ative a câmera nas configurações do Android.")

func open_rear_camera()->void:
 if closed:return
 if feed!=null:
  feed.feed_is_active=false;feed=null
 var chosen=null
 for candidate in CameraServer.feeds():
  if candidate.get_position()==CameraFeed.FEED_BACK:
   chosen=candidate;break
 if chosen==null:
  status.text=tr("Nenhuma câmera traseira encontrada. Tente novamente.");return
 feed=chosen
 if not Engine.has_singleton("GodotHandTracking"):
  status.text=tr("Rastreamento não carregou. Feche e tente novamente.");return
 hand_tracker=Engine.get_singleton("GodotHandTracking")
 hand_tracker.stopFastCamera();hand_tracker.stopTracking()
 hand_tracker.setCameraPreviewEnabled(true)
 hand_tracker.startTracking()
 last_sequence=-1;preview_time=0.0;elapsed=0.0;camera_texture=null
 for view in views:view.texture=null
 for overlay in overlays:overlay.set_hands([],Vector2.ZERO)
 if legacy_mode:
  var formats:Array=feed.get_formats()
  var best:=-1;var best_score:=INF
  for i in formats.size():
   var f:Dictionary=formats[i]
   if f.get("format","")!="JPEG":continue
   var w:int=f.get("width",0);var h:int=f.get("height",0)
   if w<=0 or h<=0:continue
   var score:=absf(float(w*h)-640.0*480.0)/(640.0*480.0)+absf(float(w)/h-4.0/3.0)*2.0
   if score<best_score:best_score=score;best=i
  if best<0 or not feed.set_format(best,{}):
   status.text=tr("A câmera anterior não ofereceu um formato compatível.");return
  camera_texture=CameraTexture.new();camera_texture.camera_feed_id=feed.get_id()
  for view in views:view.texture=camera_texture
  feed.feed_is_active=true
  status.text=tr("Abrindo modo anterior...")
 else:
  hand_tracker.startFastCamera(feed.get_name())
  status.text=tr("Abrindo câmera rápida e preparando o rastreamento...")

func _process(delta:float)->void:
 if hand_tracker==null:return
 if legacy_mode:
  process_legacy(delta);return
 elapsed+=delta;preview_time+=delta;poll_time+=delta
 var camera_state:String=hand_tracker.getFastCameraState()
 if preview_time>=.10:
  preview_time=0.0
  var seq:int=hand_tracker.getFastFrameSequence()
  if seq!=last_sequence:
   var w:int=hand_tracker.getFastFrameWidth();var h:int=hand_tracker.getFastFrameHeight()
   var pixels:PackedByteArray=hand_tracker.getFastFrame()
   if w>0 and h>0 and pixels.size()==w*h*4:
    var img:=Image.create_from_data(w,h,false,Image.FORMAT_RGBA8,pixels)
    if camera_texture==null or camera_texture.get_width()!=w or camera_texture.get_height()!=h:
     camera_texture=ImageTexture.create_from_image(img)
     for view in views:view.texture=camera_texture
    else:camera_texture.update(img)
    last_sequence=seq
 if poll_time<.05:return
 poll_time=0.0
 var tracking_state:String=hand_tracker.getTrackingState()
 if camera_state.begins_with("erro"):
  status.text=tr("Falha na câmera rápida: ")+I18n.native_status(camera_state)
 elif camera_state=="desligada":
  status.text=tr("Câmera parada. Toque em TENTAR NOVAMENTE.")
 elif tracking_state.begins_with("erro"):
  status.text=tr("Falha no rastreamento: ")+I18n.native_status(tracking_state)
 elif camera_texture==null:
  status.text=tr("Abrindo câmera...") if elapsed<8 else tr("A imagem não chegou. Toque em TENTAR NOVAMENTE.")
 elif tracking_state!="pronto":
  status.text=tr("Preparando rastreamento das mãos…")
 else:
  var parsed=JSON.parse_string(hand_tracker.getLandmarks())
  var hands:Array=parsed if parsed is Array else []
  var dims:Vector2=camera_texture.get_size()
  for overlay in overlays:overlay.set_hands(hands,dims)
  status.text=tr("MÃOS: %d  •  IMAGEM: %d fps  •  TRACKING: %d fps  •  %d ms")%[hands.size(),hand_tracker.getFastCameraFps(),hand_tracker.getTrackingFps(),hand_tracker.getInferenceMs()]

func process_legacy(delta:float)->void:
 elapsed+=delta;poll_time+=delta
 if poll_time<.05:return
 poll_time=0.0
 var tracking_state:String=hand_tracker.getTrackingState()
 if tracking_state.begins_with("erro"):
  status.text=tr("Falha no rastreamento: ")+I18n.native_status(tracking_state)
 elif feed==null or feed.get_datatype()==CameraFeed.FEED_NOIMAGE:
  status.text=tr("Abrindo modo anterior...") if elapsed<8 else tr("A imagem não chegou. Toque em TENTAR NOVAMENTE.")
 elif tracking_state!="pronto":status.text=tr("Preparando rastreamento das mãos…")
 else:
  var parsed=JSON.parse_string(hand_tracker.getLandmarks())
  var hands:Array=parsed if parsed is Array else []
  for overlay in overlays:overlay.set_hands(hands,camera_texture.get_size())
  status.text=tr("MODO ANTERIOR  •  MÃOS: %d  •  MÃOS: %d fps")%[hands.size(),hand_tracker.getTrackingFps()]

func close_test()->void:
 if closed:return
 closed=true
 if hand_tracker!=null:hand_tracker.stopFastCamera();hand_tracker.stopTracking()
 if feed!=null:feed.feed_is_active=false
 for view in views:view.texture=null
 camera_texture=null;feed=null
 camera_extension=null
 menu.screen.visible=true
 queue_free()

func _exit_tree()->void:
 if hand_tracker!=null:hand_tracker.stopFastCamera();hand_tracker.stopTracking()
 if feed!=null:feed.feed_is_active=false
