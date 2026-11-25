extends Control
var vr: Node
func _ready()->void:
 mouse_filter=Control.MOUSE_FILTER_IGNORE
 set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
func _process(_delta:float)->void:queue_redraw()
func _draw()->void:
 if not is_instance_valid(vr):return
 var font:=ThemeDB.fallback_font
 for eye in 2:
  var c:=Vector2(size.x*(.25 if eye==0 else .75),size.y*.5)
  draw_circle(c,3,Color.WHITE)
  var progress:float=vr.gaze_time/vr.GAZE_SECONDS if vr.menu_open else vr.menu_gesture_time/vr.MENU_GESTURE_SECONDS
  if progress>0.0:draw_arc(c,14,-PI/2,-PI/2+TAU*clampf(progress,0,1),40,Color("72e9c1"),3.0,true)
  var text:String=""
  if vr.calibrate_countdown>0:
   text=tr("OLHE PARA A FRENTE  •  ")+str(ceili(vr.calibrate_countdown))
  elif vr.bias_time>0:
   text=tr("FIQUE PARADO...")
  elif vr.hint_time>0:
   text=tr("Para abrir o menu, olhe para baixo por 2 segundos")
  if vr.menu_gesture_time>0.0 and not vr.menu_open:text=tr("SEGURE PARA ABRIR O MENU")
  if is_instance_valid(vr.hand_control):
   if vr.hand_control.calibration_time>0.0:
    text=tr("ABAIXE AS MÃOS CONFORTAVELMENTE • ")+str(ceili(vr.hand_control.calibration_time))
   elif vr.hand_control.notice_time>0.0:text=vr.hand_control.calibration_notice
   var tracking:String=vr.hand_control.state
   draw_string(font,Vector2(c.x-size.x*.22,size.y*.90),tracking,HORIZONTAL_ALIGNMENT_CENTER,size.x*.44,14,Color.WHITE)
  draw_string(font,Vector2(c.x-size.x*.22,size.y*.95),tr("Visão: ")+vr.orientation_status,HORIZONTAL_ALIGNMENT_CENTER,size.x*.44,13,Color(.85,.9,.94))
  if vr.action_notice_time>0.0:text=vr.action_notice
  if vr.game.network.state=="joined" and Time.get_ticks_msec()<vr.game.round_notice_until:text=tr(vr.game.round_notice)
  if is_instance_valid(vr.game.ragdoll):text=tr("ATORDOADO • ")+str(ceili(vr.game.ragdoll.time_left))+tr("s")
  if vr.game.active_character=="grinner" and vr.game.monster_mode=="choose":text=tr("ESCOLHA: AGARRAR OU MOTOSSERRA")
  if vr.game.is_dead:text=(tr("VOCÊ MORREU • VIRANDO MONSTRO EM ") if vr.game.network.state=="joined" else tr("VOCÊ MORREU • VOLTANDO EM "))+str(ceili(vr.game.dead_timer))
  if not text.is_empty():
   draw_string(font,Vector2(c.x-size.x*.22,size.y*.17),text,HORIZONTAL_ALIGNMENT_CENTER,size.x*.44,16,Color.WHITE)
  if vr.menu_open and is_instance_valid(vr.game.voice_face):
   draw_string(font,Vector2(c.x-size.x*.22,size.y*.82),vr.game.voice_face.status,HORIZONTAL_ALIGNMENT_CENTER,size.x*.44,13,Color(.9,.95,1))
  if vr.game.network.state=="joined":
   draw_string(font,Vector2(c.x-size.x*.22,size.y*.09),tr("SALA: ")+vr.game.network.room_code,HORIZONTAL_ALIGNMENT_CENTER,size.x*.44,16,Color.WHITE)
