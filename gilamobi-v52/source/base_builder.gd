extends Node3D
# Reuse identical, immutable procedural meshes and collision shapes. This
# reduces resources/memory without changing the appearance or level physics.
var box_mesh_cache := {}
var box_shape_cache := {}
var cylinder_mesh_cache := {}
var cylinder_shape_cache := {}
var wall_mat: StandardMaterial3D
var floor_mat: StandardMaterial3D
var ceiling_mat: StandardMaterial3D
var metal: StandardMaterial3D
var black: StandardMaterial3D
var tank_mat: StandardMaterial3D
var crate_mat: StandardMaterial3D
var screen: StandardMaterial3D
var red_lamp: StandardMaterial3D
var white_lamp: StandardMaterial3D
var yellow: StandardMaterial3D

func _ready() -> void:
 wall_mat = textured("res://textures/concrete_base.png", Vector3(3,2,1))
 floor_mat = textured("res://textures/floor_base.png", Vector3(6,6,1))
 ceiling_mat = textured("res://textures/ceiling_base.png", Vector3(3,3,1))
 metal = colored(Color("434a4b"))
 black = colored(Color("1c2225"))
 tank_mat = colored(Color("292f31"))
 crate_mat = colored(Color("66513b"))
 yellow = colored(Color("d7b72d"))
 screen = glow(Color("28e88c"))
 red_lamp = glow(Color("e74039"))
 white_lamp = glow(Color("e4efff"))
 build_base()
 resize_layout()


# Resize the architectural layout only; decorative groups retain their original dimensions.
func mark_object(begin: int, anchor: Vector3) -> void:
 for i in range(begin,get_child_count()):
  var n=get_child(i)
  if n is Node3D: n.set_meta("object_anchor",anchor)

func resize_layout() -> void:
 const S:=1.12
 for n in get_children():
  if not n is Node3D: continue
  var v:Vector3=n.position
  var a:Vector3=n.get_meta("object_anchor",v)
  n.position=Vector3(v.x+a.x*(S-1.0),v.y,v.z+a.z*(S-1.0))
  if n.get_meta("map_geometry",false): n.scale=Vector3(S,1,S)

func colored(c: Color) -> StandardMaterial3D:
 var m := StandardMaterial3D.new()
 m.albedo_color = c
 m.roughness = 0.92
 return m
func glow(c: Color) -> StandardMaterial3D:
 var m := colored(c)
 m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
 m.emission_enabled = true
 m.emission = c
 m.emission_energy_multiplier = 0.8
 return m
func textured(path: String, uv: Vector3) -> StandardMaterial3D:
 var m := colored(Color.WHITE)
 m.albedo_texture = load(path)
 m.uv1_scale = uv
 m.texture_repeat = true
 return m

func box(parent: Node3D, p: Vector3, dimensions: Vector3, mat: Material, solid := true) -> Node3D:
 var node := StaticBody3D.new() if solid else Node3D.new()
 node.position = p
 if parent == self and mat in [wall_mat,floor_mat,ceiling_mat]: node.set_meta("map_geometry",true)
 parent.add_child(node)
 var mi := MeshInstance3D.new()
 if not box_mesh_cache.has(dimensions):
  var bm := BoxMesh.new(); bm.size = dimensions
  box_mesh_cache[dimensions] = bm
 mi.mesh = box_mesh_cache[dimensions]; mi.material_override = mat
 node.add_child(mi)
 if solid:
  var cs := CollisionShape3D.new()
  if not box_shape_cache.has(dimensions):
   var shape := BoxShape3D.new(); shape.size = dimensions
   box_shape_cache[dimensions] = shape
  cs.shape = box_shape_cache[dimensions]; node.add_child(cs)
 return node
func b(p:Vector3, dimensions:Vector3, mat:Material, solid:=true) -> Node3D:
 return box(self,p,dimensions,mat,solid)
func wall(p: Vector3, dimensions: Vector3) -> void:
 b(p,dimensions,wall_mat)
 if dimensions.x > dimensions.z:
  b(Vector3(p.x,p.y-dimensions.y*0.5+0.06,p.z),Vector3(dimensions.x,0.12,dimensions.z+0.02),black,false)
 else:
  b(Vector3(p.x,p.y-dimensions.y*0.5+0.06,p.z),Vector3(dimensions.x+0.02,0.12,dimensions.z),black,false)
func cyl(p: Vector3, radius: float, height: float, mat: Material, collision := true, z_axis := false) -> void:
 var node := StaticBody3D.new() if collision else Node3D.new()
 node.position=p
 if z_axis: node.rotation.x=PI/2
 add_child(node)
 var key := Vector2(radius,height)
 if not cylinder_mesh_cache.has(key):
  var mesh := CylinderMesh.new();mesh.top_radius=radius;mesh.bottom_radius=radius;mesh.height=height;mesh.radial_segments=16
  cylinder_mesh_cache[key] = mesh
 var mi:=MeshInstance3D.new();mi.mesh=cylinder_mesh_cache[key];mi.material_override=mat;node.add_child(mi)
 if collision:
  if not cylinder_shape_cache.has(key):
   var sh:=CylinderShape3D.new();sh.radius=radius;sh.height=height
   cylinder_shape_cache[key] = sh
  var cs:=CollisionShape3D.new();cs.shape=cylinder_shape_cache[key];node.add_child(cs)
func light(p: Vector3, color: Color, energy: float, reach: float) -> void:
 var l:=OmniLight3D.new();l.position=p;l.light_color=color;l.light_energy=energy;l.omni_range=reach;l.shadow_enabled=false;add_child(l)
func fluorescent(p: Vector3, long:=false) -> void:
 var begin:=get_child_count()
 var width:=1.25 if long else 0.8
 b(p,Vector3(width+0.12,0.07,0.65),black,false)
 b(p+Vector3(0,-0.045,0),Vector3(width,0.035,0.51),white_lamp,false)
 mark_object(begin,p)

func cylinder_tank(p: Vector3) -> void:
 var begin:=get_child_count()
 cyl(p+Vector3(0,1.0,0),0.66,2.0,tank_mat)
 for h in [0.20,0.75,1.55,1.88]: cyl(p+Vector3(0,h,0),0.70,0.10,black,false)
 cyl(p+Vector3(0,2.08,0),0.14,0.16,black,false)
 mark_object(begin,p)

func pipe_z(x:float,y:float,z:float,length:float) -> void:
 var begin:=get_child_count()
 cyl(Vector3(x,y,z),0.15,length,tank_mat,false,true)
 for zp in [z-length*0.35,z,z+length*0.35]: cyl(Vector3(x,y,zp),0.18,0.10,black,false,true)
 mark_object(begin,Vector3(x,y,z))

func hazard_z(x: float, y:float, z:float, width:float) -> void:
 var begin:=get_child_count()
 # Frame and black-yellow industrial safety stripes across an open doorway.
 b(Vector3(x,y,z),Vector3(width,.30,.17),yellow,false)
 for i in range(int(width/0.32)+1):
  var px:=x-width/2.0+0.17+i*0.32
  if px<x+width/2:
   var stripe=b(Vector3(px,y,z+0.10),Vector3(.13,.32,.035),black,false)
   stripe.rotation.z=-0.46
 b(Vector3(x,y+0.52,z+0.02),Vector3(.48,.48,.09),black,false)
 b(Vector3(x,y+0.52,z+0.08),Vector3(.37,.37,.04),white_lamp,false)
 b(Vector3(x+width/2.0+0.23,1.45,z+0.10),Vector3(.18,.38,.09),black,false)
 b(Vector3(x+width/2.0+0.23,1.50,z+0.16),Vector3(.10,.12,.03),red_lamp,false)
 mark_object(begin,Vector3(x,y,z))

func hazard_x(x:float,y:float,z:float,width:float) -> void:
 var begin:=get_child_count()
 var node:=Node3D.new();node.position=Vector3(x,y,z);node.rotation.y=PI/2;add_child(node)
 box(node,Vector3.ZERO,Vector3(width,.30,.17),yellow,false)
 for i in range(int(width/0.32)+1):
  var px:float=-width/2.0+0.17+i*0.32
  if px<width/2:
   var stripe:=box(node,Vector3(px,0,.10),Vector3(.13,.32,.035),black,false);stripe.rotation.z=-0.46
 box(node,Vector3(0,.52,.02),Vector3(.48,.48,.09),black,false)
 box(node,Vector3(0,.52,.08),Vector3(.37,.37,.04),white_lamp,false)
 mark_object(begin,Vector3(x,y,z))

func recycler(p:Vector3) -> void:
 var begin:=get_child_count()
 # Decorative station inspired by the two green-screen recycling machines.
 b(p+Vector3(0,0.67,0),Vector3(1.15,1.34,.85),metal)
 b(p+Vector3(0,1.29,0),Vector3(1.23,.14,.93),black,false)
 b(p+Vector3(-.27,.96,.44),Vector3(.35,.35,.05),screen,false)
 b(p+Vector3(.20,.96,.44),Vector3(.30,.35,.05),black,false)
 for dy in [0,.09,.18]: b(p+Vector3(0,.31+dy,.44),Vector3(.73,.025,.04),black,false)
 mark_object(begin,p)

func crate(p:Vector3,size:=Vector3(.8,.8,.8)) -> void:
 var begin:=get_child_count()
 b(p+Vector3(0,size.y/2,0),size,crate_mat)
 # Frame strips read as reinforced wooden storage crates.
 for x in [-0.34,0.34]: b(p+Vector3(x,size.y/2,size.z/2+.01),Vector3(.06,size.y,.04),black,false)
 mark_object(begin,p)

func shelf(p:Vector3) -> void:
 var begin:=get_child_count()
 # Open shelves so the space between racks remains readable.
 for x in [-1.8,1.8]:
  for z in [-.36,.36]: b(p+Vector3(x,1.45,z),Vector3(.08,2.9,.08),metal,false)
 for y in [.18,1.2,2.35]:
  b(p+Vector3(0,y,0),Vector3(3.7,.10,.82),metal,false)
 for idx in [-1,0,1]:
  box(self,p+Vector3(idx*1.0,.58,0),Vector3(.65,.7,.64),crate_mat,false)
  box(self,p+Vector3(idx*1.0,1.58,0),Vector3(.65,.7,.64),crate_mat,false)
 # Single collision volume prevents walking through the rack.
 var body:=StaticBody3D.new();body.position=p+Vector3(0,1.45,0);add_child(body)
 var sh:=BoxShape3D.new();sh.size=Vector3(3.7,2.9,.82)
 var cs:=CollisionShape3D.new();cs.shape=sh;body.add_child(cs)

 mark_object(begin,p)

func build_base() -> void:
 # Main room: industrial gray walls, large floor, fluorescent panels and pipes.
 b(Vector3(0,-.15,0),Vector3(20,.3,18),floor_mat)
 b(Vector3(0,4.85,0),Vector3(20,.2,18),ceiling_mat)
 wall(Vector3(-10,2.4,0),Vector3(.18,4.8,18))
 wall(Vector3(0,2.4,9),Vector3(20,4.8,.18))
 # Doorway ahead, with room on the left for the mirror.
 wall(Vector3(-5.75,2.4,-9),Vector3(8.5,4.8,.18))
 wall(Vector3(5.75,2.4,-9),Vector3(8.5,4.8,.18))
 b(Vector3(0,3.9,-9),Vector3(3,1.8,.18),wall_mat)
 hazard_z(0,3.0,-8.88,3.0)
 # Doorway through the right wall to the adjacent office.
 wall(Vector3(10,2.4,-5.5),Vector3(.18,4.8,7))
 wall(Vector3(10,2.4,4.5),Vector3(.18,4.8,9))
 b(Vector3(10,3.9,-1),Vector3(.18,1.8,2),wall_mat)
 # A larger traversable 3m opening, made by shifting south wall to z=5.
 # Replace doorway crosspiece width only; southern segment begins at z=0.
 hazard_x(9.88,3,-1,2.0)
 for x in [-7,-2,3,8]:
  for z in [-6,-1,4]: fluorescent(Vector3(x,4.72,z),true)
 light(Vector3(-5,3.9,-3),Color("d5e4ff"),0.8,12)
 light(Vector3(4,3.9,0),Color("d5e4ff"),0.9,13)
 light(Vector3(2,3.4,-8),Color("ff4136"),1.0,7)
 b(Vector3(2.5,3.3,-8.88),Vector3(.55,.55,.08),red_lamp,false)
 for x in [-6,4]:
  cyl(Vector3(x,2.4,-3.4),.45,4.8,tank_mat)
  cyl(Vector3(x,0.15,-3.4),.51,.3,black,false)
  cyl(Vector3(x,4.65,-3.4),.50,.3,black,false)
 cylinder_tank(Vector3(-8,0,-6.4));cylinder_tank(Vector3(7.3,0,-6.2))
 pipe_z(-9.7,3.85,0,16)
 for z in [-6,0,6]: b(Vector3(0,4.6,z),Vector3(19.7,.20,.22),black,false)
 recycler(Vector3(-4,0,-1.7));recycler(Vector3(-2.7,0,-1.7))
 crate(Vector3(7,0,3));crate(Vector3(8,0,3.8));crate(Vector3(-8,0,6))
 # Main north corridor.
 b(Vector3(0,-.15,-12.5),Vector3(3,.3,7),floor_mat)
 b(Vector3(0,4.1,-12.5),Vector3(3,.2,7),ceiling_mat)
 wall(Vector3(-1.6,2.05,-12.5),Vector3(.18,4.1,7))
 wall(Vector3(1.6,2.05,-12.5),Vector3(.18,4.1,7))
 for z in [-10.5,-13,-15.5]: fluorescent(Vector3(0,3.98,z))
 light(Vector3(0,3.5,-12.6),Color("dae7ff"),.7,7)
 # Central machine room at the end of the corridor.
 b(Vector3(0,-.15,-23),Vector3(14,.3,14),floor_mat)
 b(Vector3(0,4.7,-23),Vector3(14,.2,14),ceiling_mat)
 wall(Vector3(-7,2.3,-23),Vector3(.18,4.6,14))
 wall(Vector3(-4.25,2.3,-16),Vector3(5.5,4.6,.18))
 wall(Vector3(4.25,2.3,-16),Vector3(5.5,4.6,.18))
 b(Vector3(0,3.8,-16),Vector3(3,1.6,.18),wall_mat)
 hazard_z(0,3,-15.9,3)
 wall(Vector3(-4.3,2.3,-30),Vector3(5.4,4.6,.18))
 wall(Vector3(4.2,2.3,-30),Vector3(5.6,4.6,.18))
 b(Vector3(-.1,3.8,-30),Vector3(3,1.6,.18),wall_mat)
 hazard_z(-.1,3,-29.9,3)
 # right-hand wall has an opening to storage.
 wall(Vector3(7,2.3,-18),Vector3(.18,4.6,4))
 wall(Vector3(7,2.3,-26),Vector3(.18,4.6,8))
 b(Vector3(7,3.8,-21),Vector3(.18,1.6,2),wall_mat)
 hazard_x(6.9,3,-21,2)
 for x in [-4,0,4]:
  for z in [-19,-23,-27]: fluorescent(Vector3(x,4.57,z),true)
 light(Vector3(-3,3.7,-21),Color("d0deff"),.8,10)
 light(Vector3(4,3.7,-27),Color("ff5545"),.7,8)
 cylinder_tank(Vector3(-5,0,-21));cylinder_tank(Vector3(5,0,-27))
 recycler(Vector3(2.4,0,-18));crate(Vector3(-3,0,-26));crate(Vector3(-2,0,-26))
 pipe_z(-6.7,3.9,-23,13)
 # Storage room east of the central machine room.
 b(Vector3(11,-.15,-22),Vector3(8,.3,8),floor_mat)
 b(Vector3(11,4.7,-22),Vector3(8,.2,8),ceiling_mat)
 wall(Vector3(15,2.3,-22),Vector3(.18,4.6,8))
 wall(Vector3(11,2.3,-18),Vector3(8,4.6,.18))
 wall(Vector3(11,2.3,-26),Vector3(8,4.6,.18))
 for x in [9,13]:
  for z in [-20,-24]: fluorescent(Vector3(x,4.57,z))
 light(Vector3(11,3.8,-22),Color("d5e5ff"),.8,8)
 shelf(Vector3(11.5,0,-24.4));shelf(Vector3(11.5,0,-19.6))
 crate(Vector3(13.4,0,-22));crate(Vector3(14.0,0,-22.4))
 # Office adjacent to starting room.
 b(Vector3(14,-.15,0),Vector3(8,.3,10),floor_mat)
 b(Vector3(14,4.7,0),Vector3(8,.2,10),ceiling_mat)
 wall(Vector3(18,2.3,-3.25),Vector3(.18,4.6,3.5))
 wall(Vector3(18,2.3,3.25),Vector3(.18,4.6,3.5))
 b(Vector3(18,3.8,0),Vector3(.18,1.6,3),wall_mat)
 hazard_x(17.9,3,0,3)
 wall(Vector3(14,2.3,-5),Vector3(8,4.6,.18))
 wall(Vector3(14,2.3,5),Vector3(8,4.6,.18))
 for x in [12,16]:
  for z in [-3,2]: fluorescent(Vector3(x,4.57,z))
 light(Vector3(14,3.8,0),Color("d4e4ff"),.8,8)
 # Two desks and static monitors; extra clear space for turning.
 for x in [12,15]:
  b(Vector3(x,.75,-3.7),Vector3(2,.10,.8),metal)
  for dx in [-.85,.85]: b(Vector3(x+dx,.37,-3.7),Vector3(.10,.75,.7),black,false)
  b(Vector3(x,1.19,-4),Vector3(.9,.65,.08),black,false)
  b(Vector3(x,1.19,-3.94),Vector3(.78,.53,.03),screen,false)
 crate(Vector3(16,0,2.8))
 # A short corridor beyond the last hazard frame, ending in a security door.
 b(Vector3(-.1,-.15,-33),Vector3(3,.3,6),floor_mat)
 b(Vector3(-.1,4,-33),Vector3(3,.2,6),ceiling_mat)
 wall(Vector3(-1.7,2,-33),Vector3(.18,4,6))
 wall(Vector3(1.5,2,-33),Vector3(.18,4,6))
 b(Vector3(-.1,3.55,-36),Vector3(3,.9,.18),wall_mat)
 hazard_z(-.1,3,-35.9,3)
 fluorescent(Vector3(0,3.88,-33));light(Vector3(0,3.3,-33),Color("d5e4ff"),.65,6)

 # Extended north sector: a large generator hall, open to the corridor.
 b(Vector3(0,-.15,-46),Vector3(24,.3,20),floor_mat)
 b(Vector3(0,5.2,-46),Vector3(24,.2,20),ceiling_mat)
 wall(Vector3(-6.8,2.6,-36),Vector3(10.4,5.2,.18))
 wall(Vector3(6.8,2.6,-36),Vector3(10.4,5.2,.18))
 b(Vector3(0,4,-36),Vector3(3.1,2.4,.18),wall_mat)
 # West and east gates lead to side sectors.
 wall(Vector3(-12,2.6,-39.5),Vector3(.18,5.2,7))
 wall(Vector3(-12,2.6,-52),Vector3(.18,5.2,8))
 b(Vector3(-12,4,-45.5),Vector3(.18,2.4,5),wall_mat)
 hazard_x(-11.9,3,-45.5,5)
 wall(Vector3(12,2.6,-39.5),Vector3(.18,5.2,7))
 wall(Vector3(12,2.6,-52),Vector3(.18,5.2,8))
 b(Vector3(12,4,-45.5),Vector3(.18,2.4,5),wall_mat)
 hazard_x(11.9,3,-45.5,5)
 # Two wide openings into the deep rear section.
 wall(Vector3(-9.5,2.6,-56),Vector3(5,5.2,.18))
 wall(Vector3(0,2.6,-56),Vector3(6,5.2,.18))
 wall(Vector3(9.5,2.6,-56),Vector3(5,5.2,.18))
 for x in [-5.75,5.75]:
  b(Vector3(x,4,-56),Vector3(5.5,2.4,.18),wall_mat)
  hazard_z(x,3,-55.9,5.5)
 for x in [-9,-4,1,6,10]:
  for z in [-39,-44,-49,-54]: fluorescent(Vector3(x,5.06,z),true)
 for x in [-8,0,8]:
  for z in [-40,-49]: light(Vector3(x,4.2,z),Color("cbdcf3"),.7,10)
 for x in [-8,8]:
  for z in [-39,-52]: cylinder_tank(Vector3(x,0,z))
 for x in [-6,6]:
  for z in [-42,-50]:
   cyl(Vector3(x,2.6,z),.42,5.2,tank_mat)
   cyl(Vector3(x,.15,z),.49,.3,black,false)
 pipe_z(-11.7,4.2,-46,19);pipe_z(11.7,4.2,-46,19)
 for z in [-40,-46,-52]: b(Vector3(0,5,z),Vector3(23.6,.18,.20),black,false)
 # Generator islands leave wide lanes through the room.
 for x in [-3,3]:
  b(Vector3(x,.8,-46),Vector3(2,1.6,2),metal)
  b(Vector3(x,1.72,-46),Vector3(2.15,.20,2.15),black,false)
  b(Vector3(x,1.05,-44.97),Vector3(.8,.6,.04),screen,false)
 # West storage sector: spacious aisles and shelving.
 b(Vector3(-21,-.15,-46),Vector3(18,.3,20),floor_mat)
 b(Vector3(-21,5.2,-46),Vector3(18,.2,20),ceiling_mat)
 wall(Vector3(-30,2.6,-46),Vector3(.18,5.2,20))
 wall(Vector3(-21,2.6,-36),Vector3(18,5.2,.18))
 wall(Vector3(-21,2.6,-56),Vector3(18,5.2,.18))
 for x in [-27,-22,-17]:
  for z in [-39,-45,-51]: fluorescent(Vector3(x,5.06,z),true)
 for x in [-26,-19]: light(Vector3(x,4,-46),Color("d1e0ef"),.7,11)
 for z in [-52,-48,-41,-38]:
  for x in [-25,-19,-15]: shelf(Vector3(x,0,z))
 crate(Vector3(-28,0,-44));crate(Vector3(-28,0,-45));crate(Vector3(-28,0,-46))
 # East lab sector with counters and machinery, accessible from main hall.
 b(Vector3(21,-.15,-46),Vector3(18,.3,20),floor_mat)
 b(Vector3(21,5.2,-46),Vector3(18,.2,20),ceiling_mat)
 wall(Vector3(30,2.6,-46),Vector3(.18,5.2,20))
 wall(Vector3(21,2.6,-36),Vector3(18,5.2,.18))
 wall(Vector3(21,2.6,-56),Vector3(18,5.2,.18))
 for x in [15,20,25,28]:
  for z in [-39,-45,-51]: fluorescent(Vector3(x,5.06,z),true)
 for x in [17,25]: light(Vector3(x,4,-46),Color("c8e1f2"),.8,11)
 for z in [-52,-40]:
  for x in [16,20,24,28]: recycler(Vector3(x,0,z))
 for z in [-49,-43]:
  b(Vector3(23,.75,z),Vector3(6,.12,1.3),metal)
  for x in [20.5,25.5]: b(Vector3(x,.37,z),Vector3(.12,.75,1),black,false)
  for x in [21,24]: b(Vector3(x,1.1,z),Vector3(.8,.6,.12),screen,false)
 # Far hall at the rear: two entrances and plenty of open floor.
 b(Vector3(0,-.15,-65),Vector3(24,.3,18),floor_mat)
 b(Vector3(0,5.2,-65),Vector3(24,.2,18),ceiling_mat)
 wall(Vector3(-12,2.6,-65),Vector3(.18,5.2,18))
 wall(Vector3(12,2.6,-65),Vector3(.18,5.2,18))
 # Central eight-metre opening into the new extension.
 wall(Vector3(-8,2.6,-74),Vector3(8,5.2,.18))
 wall(Vector3(8,2.6,-74),Vector3(8,5.2,.18))
 b(Vector3(0,4.15,-74),Vector3(8,2.1,.18),wall_mat)
 hazard_z(0,3.05,-73.9,8)
 for x in [-9,-3,3,9]:
  for z in [-59,-64,-70]: fluorescent(Vector3(x,5.06,z),true)
 for x in [-7,0,7]: light(Vector3(x,4,-65),Color("c9daee"),.7,11)
 for x in [-9,9]:
  for z in [-60,-69]: cylinder_tank(Vector3(x,0,z))
 for x in [-10,-8,8,10]: crate(Vector3(x,0,-71))
 pipe_z(-11.7,4.2,-65,17);pipe_z(11.7,4.2,-65,17)
 # East of the starting office, a separate hangar-sized work area.
 b(Vector3(27,-.15,0),Vector3(18,.3,16),floor_mat)
 b(Vector3(27,5.2,0),Vector3(18,.2,16),ceiling_mat)
 wall(Vector3(36,2.6,0),Vector3(.18,5.2,16))
 wall(Vector3(27,2.6,-8),Vector3(18,5.2,.18))
 wall(Vector3(27,2.6,8),Vector3(18,5.2,.18))
 wall(Vector3(18,2.6,-6.5),Vector3(.18,5.2,3))
 wall(Vector3(18,2.6,6.5),Vector3(.18,5.2,3))
 for x in [21,27,33]:
  for z in [-5,0,5]: fluorescent(Vector3(x,5.06,z),true)
 for x in [23,31]: light(Vector3(x,4,0),Color("cedef4"),.8,11)
 for x in [22,32]:
  for z in [-5,5]: cylinder_tank(Vector3(x,0,z))
 for z in [-3,3]: shelf(Vector3(30,0,z))

 # New northern complex: large open processing hall plus two side wings.
 b(Vector3(0,-.15,-89),Vector3(32,.3,30),floor_mat)
 b(Vector3(0,5.4,-89),Vector3(32,.2,30),ceiling_mat)
 # Front shoulders close the gap outside the old hall width.
 wall(Vector3(-14,2.7,-74),Vector3(4,5.4,.18))
 wall(Vector3(14,2.7,-74),Vector3(4,5.4,.18))
 for side in [-1,1]:
  var x: float = 16.0*side
  wall(Vector3(x,2.7,-78.5),Vector3(.18,5.4,9))
  wall(Vector3(x,2.7,-99.5),Vector3(.18,5.4,9))
  b(Vector3(x,4.2,-89),Vector3(.18,2.4,12),wall_mat)
  hazard_x(x-side*.1,3,-89,12)
 wall(Vector3(-10,2.7,-104),Vector3(12,5.4,.18))
 wall(Vector3(10,2.7,-104),Vector3(12,5.4,.18))
 b(Vector3(0,4.2,-104),Vector3(8,2.4,.18),wall_mat)
 hazard_z(0,3,-103.9,8)
 for x in [-12,-6,0,6,12]:
  for z in [-78,-85,-92,-99]: fluorescent(Vector3(x,5.24,z),true)
 for x in [-10,0,10]:
  for z in [-81,-95]: light(Vector3(x,4.3,z),Color("c8d9ed"),.7,14)
 for x in [-12,12]:
  for z in [-79,-98]: cylinder_tank(Vector3(x,0,z))
 for x in [-7,7]:
  for z in [-83,-94]:
   b(Vector3(x,.9,z),Vector3(2.5,1.8,2),metal)
   b(Vector3(x,1.9,z),Vector3(2.6,.18,2.1),black,false)
   b(Vector3(x,1.1,z+1.02),Vector3(.8,.55,.04),screen,false)
 # Side halls continue through both wide openings.
 for side in [-1,1]:
  var cx: float = 26.0*side
  b(Vector3(cx,-.15,-89),Vector3(20,.3,30),floor_mat)
  b(Vector3(cx,5.4,-89),Vector3(20,.2,30),ceiling_mat)
  wall(Vector3(36.0*side,2.7,-89),Vector3(.18,5.4,30))
  wall(Vector3(cx,2.7,-74),Vector3(20,5.4,.18))
  wall(Vector3(cx,2.7,-104),Vector3(20,5.4,.18))
  for xoff in [-6,0,6]:
   for z in [-79,-86,-93,-100]: fluorescent(Vector3(cx+xoff,5.24,z),true)
  for z in [-82,-96]: light(Vector3(cx,4.2,z),Color("d4deec"),.8,14)
  for z in [-99,-95,-82,-78]: shelf(Vector3(cx+5*side,0,z))
  for z in [-97,-81]: cylinder_tank(Vector3(cx-6*side,0,z))
 # One further room beyond the north gate.
 b(Vector3(0,-.15,-116),Vector3(32,.3,24),floor_mat)
 b(Vector3(0,5.4,-116),Vector3(32,.2,24),ceiling_mat)
 wall(Vector3(-16,2.7,-116),Vector3(.18,5.4,24))
 wall(Vector3(16,2.7,-116),Vector3(.18,5.4,24))
 wall(Vector3(0,2.7,-128),Vector3(32,5.4,.18))
 for x in [-12,-6,0,6,12]:
  for z in [-108,-115,-122]: fluorescent(Vector3(x,5.24,z),true)
 for x in [-10,0,10]: light(Vector3(x,4.3,-116),Color("cedcee"),.8,14)
 for x in [-12,12]:
  for z in [-109,-123]: cylinder_tank(Vector3(x,0,z))
 for x in [-6,-3,3,6]: crate(Vector3(x,0,-123))
