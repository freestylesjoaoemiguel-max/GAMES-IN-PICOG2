extends Node3D
# No BBCode or interpretation of player-supplied content.
static var bubble_texture:Texture2D
var label:Label3D
var expires_at:=0
func _ready()->void:
 var backdrop:=Sprite3D.new();backdrop.billboard=BaseMaterial3D.BILLBOARD_ENABLED;backdrop.pixel_size=.004;backdrop.no_depth_test=false
 if bubble_texture==null:
  var image:=Image.create(550,220,false,Image.FORMAT_RGBA8)
  image.fill(Color(0,0,0,0))
  for y in 220:
   for x in 550:
    var dx:=maxi(24-x,0)+maxi(x-525,0)
    var dy:=maxi(24-y,0)+maxi(y-180,0)
    if (y<=204 and dx*dx+dy*dy<=24*24) or (y>195 and y<217 and absi(x-275)<int((217-y)*.7)):
     image.set_pixel(x,y,Color(.03,.045,.07,.90))
  bubble_texture=ImageTexture.create_from_image(image)
 backdrop.texture=bubble_texture;add_child(backdrop)
 label=Label3D.new();label.billboard=BaseMaterial3D.BILLBOARD_ENABLED;label.font_size=38;label.pixel_size=.004
 label.modulate=Color.WHITE;label.outline_size=6;label.outline_modulate=Color.BLACK;label.position.z=-.005
 label.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART;label.width=500;add_child(label)
 visible=false
func say(message:String)->void:
 if not is_instance_valid(label):return
 label.text=message;expires_at=Time.get_ticks_msec()+10000;visible=true
func _process(_delta:float)->void:
 if visible and Time.get_ticks_msec()>=expires_at:visible=false
