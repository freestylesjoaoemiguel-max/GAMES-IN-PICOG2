extends Node3D
# Sandbox geometry. Character buttons are handled by the game interaction code.
var materials:={}
func mat(hex:String)->StandardMaterial3D:
 if not materials.has(hex):
  var m:=StandardMaterial3D.new();m.albedo_color=Color(hex);m.roughness=.82;materials[hex]=m
 return materials[hex]
func box(label:String,pos:Vector3,dimensions:Vector3,color:String,solid:=true,rot:=Vector3.ZERO)->Node3D:
 var n:Node3D=StaticBody3D.new() if solid else Node3D.new();n.name=label;n.position=pos;n.rotation=rot;add_child(n)
 var mesh:=BoxMesh.new();mesh.size=dimensions
 var view:=MeshInstance3D.new();view.mesh=mesh;view.material_override=mat(color);n.add_child(view)
 if solid:
  var shape:=BoxShape3D.new();shape.size=dimensions
  var collider:=CollisionShape3D.new();collider.shape=shape;n.add_child(collider)
 return n
func title(parent:Node3D,text:String,p:Vector3)->void:
 var label:=Label3D.new();label.text=text;label.position=p;label.billboard=BaseMaterial3D.BILLBOARD_ENABLED
 label.font_size=38;label.pixel_size=.005;label.outline_size=7;label.no_depth_test=true;parent.add_child(label)
func cylinder(parent:Node3D,r:float,height:float,pos:Vector3,material:Material)->void:
 var mesh:=CylinderMesh.new();mesh.top_radius=r;mesh.bottom_radius=r;mesh.height=height
 var view:=MeshInstance3D.new();view.mesh=mesh;view.material_override=material;view.position=pos;parent.add_child(view)
func capsule_display(kind:String,x:float)->void:
 var holder:=Node3D.new();holder.name="CapsulaGilla" if kind=="normal" else "CapsulaMonstro";holder.position=Vector3(x,0,-5);add_child(holder)
 cylinder(holder,1.15,.18,Vector3(0,.12,0),mat("44515d"))
 cylinder(holder,1.15,.18,Vector3(0,2.82,0),mat("44515d"))
 var glass:=StandardMaterial3D.new();glass.albedo_color=Color(.24,.75,.85,.15);glass.transparency=BaseMaterial3D.TRANSPARENCY_ALPHA;glass.cull_mode=BaseMaterial3D.CULL_DISABLED
 cylinder(holder,1.07,2.6,Vector3(0,1.5,0),glass)
 var model:Node3D=preload("res://monster_model.gd").instantiate() if kind=="grinner" else load("res://Gilla_PlayerModel.fbx").instantiate()
 model.name="ModeloDemonstracao";model.rotation.y=0.0;model.scale=Vector3.ONE*(2.175 if kind=="grinner" else 1.45);model.position=Vector3(0,.22,0);holder.add_child(model)
 title(holder,tr("MONSTRO") if kind=="grinner" else "GILLA",Vector3(0,3.45,0))
 # Interactive selection marker for touch and VR gaze.
 var marker:=Node3D.new();marker.name="BotaoSelecao";marker.set_meta("role",kind);marker.position=Vector3(0,.50,1.65);holder.add_child(marker)
 cylinder(marker,.32,.12,Vector3.ZERO,mat("e05045" if kind=="grinner" else "50cba1"))
 title(marker,tr("TOQUE PARA ESCOLHER"),Vector3(0,.35,0))
func _ready()->void:
 var sun:=DirectionalLight3D.new();sun.name="Sol";sun.rotation=Vector3(deg_to_rad(-48),deg_to_rad(-30),0);sun.light_energy=.9;sun.shadow_enabled=false;add_child(sun)
 box("Piso",Vector3(0,-.25,0),Vector3(120,.5,120),"647378")
 # Colored activity zones do not obstruct character physics.
 box("AreaEscada",Vector3(-10,.005,-14),Vector3(20,.01,9),"8b7758",false)
 box("AreaPilares",Vector3(21,.005,-15),Vector3(28,.01,9),"506d7b",false)
 box("AreaAlvos",Vector3(-11,.005,8),Vector3(18,.01,6),"746650",false)
 box("AreaRampa",Vector3(15,.005,11),Vector3(12,.01,20),"58756f",false)
 # Visible boundaries, geometry arranged around the central spawn area.
 for side in [-1,1]:
  box("LimiteX",Vector3(side*60,2,0),Vector3(1,4,120),"3a4a51")
  box("LimiteZ",Vector3(0,2,side*60),Vector3(120,4,1),"3a4a51")
 capsule_display("normal",-2.2);capsule_display("grinner",2.2)
 for i in 10:
  box("Degrau",Vector3(-16+i*1.5,.15+i*.25,-14),Vector3(1.5,.3+i*.5,4),"c59b55")
 for i in 8:
  box("Pilar",Vector3(10+i*3,1+(i%3)*.4,-15),Vector3(1.5,2+(i%3)*.8,1.5),"5b88a3")
 for i in 8:
  box("Caixa",Vector3(-22+(i%4)*2, .5+(i/4)*1.05,5),Vector3(1,1,1),"956e4d")
 box("Rampa",Vector3(15,1.3,8),Vector3(5,.3,10),"688ea0",true,Vector3(deg_to_rad(-14),0,0))
 box("Plataforma",Vector3(15,2.6,15),Vector3(8,.4,7),"506b7e")
 box("TunelL",Vector3(-5,1,19),Vector3(.4,2,12),"3a4a51")
 box("TunelR",Vector3(1,1,19),Vector3(.4,2,12),"3a4a51")
 box("TunelTopo",Vector3(-2,2,19),Vector3(6,.35,12),"53676d")
 for i in 5:
  box("Barreira",Vector3(29,1,i*8-16),Vector3(.5,2,4),"b68c48")
 # Additional areas: a climbing course, winding walls, a high tower and jumps.
 for i in 7:
  box("Salto",Vector3(-32+i*4,.3+float(i%3)*.35,-28),Vector3(2,.6+float(i%3)*.7,2),"bc9857")
 for i in 6:
  box("ParedeEscalada",Vector3(34,2.5,-28+i*8),Vector3(.5,5,5),"647e8e")
 for i in 8:
  box("Labirinto",Vector3(-30+i*5,1.5,28+(i%2)*6),Vector3(4,.3+3, .45),"6e7e70")
 for i in 6:
  box("TorreDegrau",Vector3(29+i*1.6,.2+i*.4,30),Vector3(1.6,.4+i*.8,4),"967b57")
 box("TorreTopo",Vector3(39,5,30),Vector3(6,.4,7),"4b6978")
 for i in 5:
  box("AlvoPlataforma",Vector3(-30+i*4,.5+i*.35,-3),Vector3(2,1+i*.7,2),"6e8676")
