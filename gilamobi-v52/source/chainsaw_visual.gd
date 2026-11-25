extends Node3D
# Low-poly motor body, moving chain and spatial engine sound.
var teeth:MultiMeshInstance3D
var chain_time:=0.0
var engine:AudioStreamPlayer3D
func material(color:String,metallic:=0.0,roughness:=.65)->StandardMaterial3D:
 var m:=StandardMaterial3D.new();m.albedo_color=Color(color);m.metallic=metallic;m.roughness=roughness;return m
func box(size:Vector3,pos:Vector3,mat:Material)->MeshInstance3D:
 var node:=MeshInstance3D.new();var mesh:=BoxMesh.new();mesh.size=size;node.mesh=mesh;node.position=pos;node.material_override=mat;add_child(node);return node
func _ready()->void:
 rotation.y=.60
 var orange=material("b94312",.18,.55);var dark=material("171b1b",.15,.6);var metal=material("879296",.78,.3);var black=material("333638",.35,.6)
 box(Vector3(.30,.31,.39),Vector3(0,-.01,-.15),orange)
 box(Vector3(.32,.09,.34),Vector3(0,.16,-.15),dark)
 box(Vector3(.20,.13,.23),Vector3(0,-.13,.12),dark)
 box(Vector3(.05,.30,.05),Vector3(-.20,.11,-.18),black)
 box(Vector3(.05,.30,.05),Vector3(.20,.11,-.18),black)
 box(Vector3(.44,.05,.06),Vector3(0,.27,-.18),black)
 for z in [-.27,-.22,-.17,-.12,-.07]:box(Vector3(.012,.13,.025),Vector3(.157,-.01,z),dark)
 box(Vector3(.07,.19,.80),Vector3(0,0,-.72),metal)
 box(Vector3(.09,.025,.82),Vector3(0,.105,-.72),black)
 box(Vector3(.09,.025,.82),Vector3(0,-.105,-.72),black)
 var blade_tip:=MeshInstance3D.new();var ball:=SphereMesh.new();ball.radius=.095;ball.height=.19;blade_tip.mesh=ball;blade_tip.position=Vector3(0,0,-1.12);blade_tip.scale=Vector3(.39,1,1);blade_tip.material_override=metal;add_child(blade_tip)
 teeth=MultiMeshInstance3D.new();var mesh:=BoxMesh.new();mesh.size=Vector3(.085,.026,.035);mesh.material=metal
 var mm:=MultiMesh.new();mm.transform_format=MultiMesh.TRANSFORM_3D;mm.mesh=mesh;mm.instance_count=28;teeth.multimesh=mm;add_child(teeth)
 engine=AudioStreamPlayer3D.new();engine.name="MotorMotosserra";var wav=load("res://sounds/chainsaw_loop.wav").duplicate() as AudioStreamWAV
 wav.loop_mode=AudioStreamWAV.LOOP_FORWARD;wav.loop_end=int(wav.data.size()/2);engine.stream=wav;engine.unit_size=3;engine.max_distance=16;engine.volume_db=-9;add_child(engine);engine.play()
func _process(delta:float)->void:
 chain_time+=delta*1.1
 for i in 28:
  var p:=fposmod(float(i)/28.0+chain_time,1.0)
  var along:float;var y:float
  if p<.5:along=p*2.0;y=.122
  else:along=(1.0-p)*2.0;y=-.122
  teeth.multimesh.set_instance_transform(i,Transform3D(Basis.IDENTITY,Vector3(0,y,lerpf(-.32,-1.12,along))))
func blade_position(t:float)->Vector3:return to_global(Vector3(0,0,lerpf(-.34,-1.12,clampf(t,0,1))))
