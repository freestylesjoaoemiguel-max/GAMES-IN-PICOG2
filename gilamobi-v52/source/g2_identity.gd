extends Node3D
# Local overhead identity, visible to mirror / external camera, not inside the
# player's own eyes. Remote avatars retain their network-driven identity.
var name_label:Label3D
var vr_label:Label3D

func _ready() -> void:
    name_label=_label("NomeDoJogador",40)
    vr_label=_label("EtiquetaPicoVR",38)
    vr_label.text="[VR PICO G2]"
    vr_label.modulate=Color("ff0000")
    vr_label.visible=false

func _label(node_name:String,size:int) -> Label3D:
    var label:=Label3D.new()
    label.name=node_name
    label.layers=4
    label.billboard=BaseMaterial3D.BILLBOARD_ENABLED
    label.font_size=size
    label.pixel_size=.005
    label.shaded=false
    label.outline_modulate=Color(0,0,0,.94)
    label.outline_size=10
    add_child(label)
    return label

func update_identity(nickname:String,active:bool,grinner:bool) -> void:
    name_label.text=preload("res://profile_name.gd").clean(nickname)
    name_label.modulate=Color("00ff00") if active else Color.WHITE
    name_label.position.y=2.45 if grinner else 2.15
    vr_label.position.y=name_label.position.y+.29
    vr_label.visible=active
