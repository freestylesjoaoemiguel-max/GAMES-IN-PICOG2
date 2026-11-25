extends Button
func _ready() -> void:
 name="ModoVRBox";tooltip_text="Modo VR Box (teste)";focus_mode=Control.FOCUS_NONE
 var style:=StyleBoxFlat.new();style.bg_color=Color("8e3335");style.border_color=Color("efb0aa");style.set_border_width_all(2);style.set_corner_radius_all(12)
 add_theme_stylebox_override("normal",style)
 var hover:=style.duplicate();hover.bg_color=Color("b34343");add_theme_stylebox_override("hover",hover)
 queue_redraw()
func _draw() -> void:
 var c:=size*.5
 draw_rect(Rect2(c+Vector2(-29,-17),Vector2(58,34)),Color(1,1,1,.95),false,3.0,true)
 draw_circle(c+Vector2(-14,0),9,Color(1,1,1,.9),false,2.5,true)
 draw_circle(c+Vector2(14,0),9,Color(1,1,1,.9),false,2.5,true)
 draw_line(c+Vector2(-37,-7),c+Vector2(-29,-7),Color.WHITE,3.0,true)
 draw_line(c+Vector2(29,-7),c+Vector2(37,-7),Color.WHITE,3.0,true)
