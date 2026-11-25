extends Node3D
# A short animated visual, never changes damage/collisions.
var start:=Vector3.ZERO
var finish:=Vector3.ZERO
var age:=0.0
var duration:=.52
var filaments:Array[MeshInstance3D]=[]
var red:StandardMaterial3D
func setup(a:Vector3,b:Vector3)->void:
 start=a;finish=b
 red=StandardMaterial3D.new();red.albedo_color=Color("790914");red.roughness=.3
 for i in 9:
  var node:=MeshInstance3D.new();var shape:=CylinderMesh.new();shape.top_radius=.008 if i%3 else .015;shape.bottom_radius=shape.top_radius;shape.height=1;shape.radial_segments=5;node.mesh=shape;node.material_override=red;add_child(node);filaments.append(node)
func _process(delta:float)->void:
 age+=delta
 var t:=clampf(age/duration,0,1)
 for i in filaments.size():
  var q:=float(i)/float(filaments.size())*TAU
  var offset:=Vector3(cos(q)*.045,sin(q)*.045,cos(q*2)*.035)
  var a:=start+offset
  var b:=start.lerp(finish,t)+offset*(1.0-t*.75)
  var d:=b-a
  var piece=filaments[i];piece.global_position=(a+b)*.5
  if d.length()>.001:
   piece.global_basis=Basis(Quaternion(Vector3.UP,d.normalized())) * Basis.from_scale(Vector3(1,d.length(),1))
 if age>=duration:queue_free()
