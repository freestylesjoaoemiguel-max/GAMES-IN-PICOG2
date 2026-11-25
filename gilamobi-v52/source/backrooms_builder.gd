extends Node3D
# Backrooms: large connected halls, offices and maze; atrium with two routes up.
# Visual landmarks are reconstructed from reference screenshots. Unseen paths
# are original approximations, not an exact survey of the original game.
const CELL:=7.0
const COLS:=9
const ROWS:=9
const UPPER:=4.6
var wall:StandardMaterial3D
var carpet:StandardMaterial3D
var ceiling:StandardMaterial3D
var light_panel:StandardMaterial3D
var wood:StandardMaterial3D
var darkwood:StandardMaterial3D
var cloth:StandardMaterial3D
var cushion:StandardMaterial3D
var metal:StandardMaterial3D
var paper:StandardMaterial3D
var screen:StandardMaterial3D
var meshes:={}
var shapes:={}
var maze_edges:={}

func mat(c:Color)->StandardMaterial3D:
 var m:=StandardMaterial3D.new();m.albedo_color=c;m.roughness=1.0;return m
func grain(c:Color,amount:float,uv:Vector3)->StandardMaterial3D:
 var m:=mat(c);var img:=Image.create(128,128,false,Image.FORMAT_RGB8)
 var rng:=RandomNumberGenerator.new();rng.seed=68452
 for y in 128:
  for x in 128:
   var n:=rng.randf_range(-amount,amount)
   img.set_pixel(x,y,Color(clampf(1+n,0,1),clampf(1+n,0,1),clampf(1+n,0,1)))
 m.albedo_texture=ImageTexture.create_from_image(img);m.uv1_scale=uv;m.texture_repeat=true
 return m
func ceiling_material()->StandardMaterial3D:
 var m:=mat(Color("d4cdb0"));var img:=Image.create(128,128,false,Image.FORMAT_RGB8)
 for y in 128:
  for x in 128:
   var edge:=x<2 or y<2
   img.set_pixel(x,y,Color(.72,.70,.63) if edge else Color(1,1,1))
 m.albedo_texture=ImageTexture.create_from_image(img);m.uv1_scale=Vector3(28,28,1);m.texture_repeat=true
 return m
func _ready()->void:
 wall=grain(Color("c7bf8c"),.018,Vector3(2,2,1))
 carpet=grain(Color("d1c69a"),.035,Vector3(16,16,1))
 ceiling=ceiling_material()
 light_panel=mat(Color("f6fff0"));light_panel.shading_mode=BaseMaterial3D.SHADING_MODE_UNSHADED
 wood=mat(Color("8a6749"));darkwood=mat(Color("554638"));cloth=mat(Color("546e71"));cushion=mat(Color("8b8879"))
 metal=mat(Color("777a6e"));paper=mat(Color("ddd7bd"));screen=mat(Color("303735"))
 make_map()
func box(pos:Vector3,sz:Vector3,m:Material,solid:=true)->Node3D:
 var n:Node3D=StaticBody3D.new() if solid else Node3D.new();n.position=pos;add_child(n)
 if not meshes.has(sz):var mesh:=BoxMesh.new();mesh.size=sz;meshes[sz]=mesh
 var mi:=MeshInstance3D.new();mi.mesh=meshes[sz];mi.material_override=m;n.add_child(mi)
 if solid:
  if not shapes.has(sz):var shape:=BoxShape3D.new();shape.size=sz;shapes[sz]=shape
  var cs:=CollisionShape3D.new();cs.shape=shapes[sz];n.add_child(cs)
 return n
func glow(pos:Vector3)->void:
 box(pos,Vector3(1.1,.025,1.35),light_panel,false)
func light(pos:Vector3,r:float)->void:
 var l:=OmniLight3D.new();l.position=pos;l.light_color=Color("fff0bd");l.light_energy=.55;l.omni_range=r;l.shadow_enabled=false;add_child(l)
func chair(p:Vector3)->void:
 box(p+Vector3(0,.53,0),Vector3(.62,.10,.6),wood)
 for x in [-.25,.25]:
  for z in [-.24,.24]:box(p+Vector3(x,.26,z),Vector3(.055,.52,.055),darkwood,false)
 for x in [-.25,.25]:box(p+Vector3(x,.95,.27),Vector3(.055,.9,.055),darkwood,false)
 for y in [.78,1.0,1.2]:box(p+Vector3(0,y,.27),Vector3(.54,.07,.045),wood,false)
func cabinet(p:Vector3)->void:
 box(p+Vector3(0,.95,0),Vector3(1.0,1.9,.62),wood)
 for y in [.3,.75,1.2,1.65]:
  box(p+Vector3(0,y,.322),Vector3(.87,.035,.025),darkwood,false)
  box(p+Vector3(0,y+.14,.34),Vector3(.18,.035,.025),metal,false)
func desk(p:Vector3)->void:
 box(p+Vector3(0,.77,0),Vector3(2,.12,1),wood)
 for x in [-.85,.85]:
  for z in [-.38,.38]:box(p+Vector3(x,.38,z),Vector3(.09,.76,.09),darkwood,false)
 box(p+Vector3(.3,1.18,-.29),Vector3(.7,.52,.08),screen,false)
 box(p+Vector3(.3,.91,-.29),Vector3(.08,.20,.14),metal,false)
 box(p+Vector3(-.46,.85,.05),Vector3(.55,.035,.34),paper,false)
func couch(p:Vector3)->void:
 box(p+Vector3(0,.4,0),Vector3(1.7,.8,.85),cloth)
 box(p+Vector3(0,.9,.3),Vector3(1.7,.45,.25),cloth)
func wall_object(p:Vector3,front:float)->void:
 # Decorative shelf inset into the partition, with small items on it.
 box(p,Vector3(1.6,1.6,.15),darkwood,false)
 box(p+Vector3(0,0,front*.09),Vector3(1.35,1.35,.07),wood,false)
 for y in [-.43,0,.43]:box(p+Vector3(0,y,front*.15),Vector3(1.3,.05,.24),darkwood,false)
 for x in [-.4,0,.4]:box(p+Vector3(x,.18,front*.18),Vector3(.15,.30,.12),paper,false)
func key(a:int,b:int)->String:
 return str(mini(a,b))+":"+str(maxi(a,b))
func carve_maze()->void:
 # Deterministic depth-first maze: every room is connected, identical on all clients.
 var rng:=RandomNumberGenerator.new();rng.seed=104063
 var stack:Array=[4];var visited:={4:true}
 while not stack.is_empty():
  var cur:int=stack.back();var x:=cur%COLS;var z:=int(cur/COLS);var options:=[]
  if x>0 and not visited.has(cur-1):options.append(cur-1)
  if x<COLS-1 and not visited.has(cur+1):options.append(cur+1)
  if z>0 and not visited.has(cur-COLS):options.append(cur-COLS)
  if z<ROWS-1 and not visited.has(cur+COLS):options.append(cur+COLS)
  if options.is_empty():stack.pop_back();continue
  var next:int=options[rng.randi_range(0,options.size()-1)]
  maze_edges[key(cur,next)]=true;visited[next]=true;stack.append(next)
 # Main corridor runs uninterrupted down the middle; extra loops aid pursuit.
 for z in range(ROWS-1):maze_edges[key(z*COLS+4,(z+1)*COLS+4)]=true
 for v in [[11,12],[24,25],[38,39],[50,51],[65,66],[18,27],[35,44],[56,65]]:maze_edges[key(v[0],v[1])]=true
func cell_center(col:int,row:int)->Vector3:
 return Vector3((col-4)*CELL,0,-60+row*CELL)
func x_wall(x:float,z:float,open:bool)->void:
 if not open:box(Vector3(x,1.8,z),Vector3(.20,3.6,CELL),wall);return
 var length:=(CELL-3.4)*.5
 for side in [-1,1]:box(Vector3(x,1.8,z+side*(CELL-length)*.5),Vector3(.20,3.6,length),wall)
 box(Vector3(x,3.35,z),Vector3(.20,.5,3.4),wall)
func z_wall(x:float,z:float,open:bool,whole_open:=false)->void:
 if whole_open:return
 if not open:box(Vector3(x,1.8,z),Vector3(CELL,3.6,.20),wall);return
 var length:=(CELL-3.4)*.5
 for side in [-1,1]:box(Vector3(x+side*(CELL-length)*.5,1.8,z),Vector3(length,3.6,.20),wall)
 box(Vector3(x,3.35,z),Vector3(3.4,.5,.20),wall)
func z_niche(x:float,z:float)->void:
 # A real recess in the wall, with a back and returns: it is not a decal.
 for side in [-1,1]:box(Vector3(x+side*2.35,1.8,z),Vector3(2.3,3.6,.2),wall)
 box(Vector3(x,.23,z),Vector3(2.4,.46,.2),wall)
 box(Vector3(x,3.15,z),Vector3(2.4,.9,.2),wall)
 box(Vector3(x,1.55,z-.72),Vector3(2.4,2.2,.15),darkwood)
 for side in [-1,1]:box(Vector3(x+side*1.17,1.55,z-.37),Vector3(.08,2.2,.7),wood,false)
 for y in [.45,2.65]:box(Vector3(x,y,z-.35),Vector3(2.4,.08,.75),wood,false)
 for y in [.62,1.25,1.88,2.48]:box(Vector3(x,y,z-.35),Vector3(2.25,.06,.68),wood,false)
 for dx in [-.75,-.4,.1,.55]:box(Vector3(x+dx,1.48,z-.30),Vector3(.18,.40,.24),paper,false)

func build_maze()->void:
 carve_maze()
 # floor and ceiling, long outer walls; spawn at (0,2) is in the central hall.
 box(Vector3(0,-.15,-28),Vector3(63,.3,72),carpet)
 box(Vector3(0,3.68,-28),Vector3(63,.16,72),ceiling)
 for x in [-31.5,31.5]:box(Vector3(x,1.8,-28),Vector3(.20,3.6,72),wall)
 box(Vector3(0,1.8,-64),Vector3(63,3.6,.20),wall)
 for row in ROWS:
  for col in COLS:
   var c:=cell_center(col,row);var idx:=row*COLS+col
   if col<COLS-1:x_wall(c.x+CELL*.5,c.z,maze_edges.has(key(idx,idx+1)))
   if row<ROWS-1:
    var is_open:=maze_edges.has(key(idx,idx+COLS))
    if not is_open and idx%5==0:z_niche(c.x,c.z+CELL*.5)
    else:z_wall(c.x,c.z+CELL*.5,is_open,col==4)
 # Last row opens toward the starting lobby and atrium.
 for col in COLS:
  var c:=cell_center(col,ROWS-1)
  z_wall(c.x,-.5,col in [1,4,7],col==4)
 # A grid of fluorescent panels. Ambient light limits expensive lights on mobile.
 for x in [-28,-21,-14,-7,0,7,14,21,28]:
  for z in [-60,-53,-46,-39,-32,-25,-18,-11,-4,3]:glow(Vector3(x,3.58,z))
 for x in [-22,0,22]:
  for z in [-51,-27,-3]:light(Vector3(x,3.1,z),16)
 # Offices and scattered furnishings placed near walls, away from doorway centers.
 for cell in [[0,1],[2,3],[6,2],[8,4],[1,6],[7,7]]:
  var p:=cell_center(cell[0],cell[1])
  desk(p+Vector3(-.8,0,-1.75));chair(p+Vector3(-.8,0,-.55));cabinet(p+Vector3(2.55,0,-2.4))
 for cell in [[0,0],[3,2],[5,4],[8,6],[2,8],[6,8]]:
  var p:=cell_center(cell[0],cell[1]);wall_object(p+Vector3(0,1.55,-3.38),1.0)
 for cell in [[1,2],[3,5],[7,1],[8,8]]:
  var p:=cell_center(cell[0],cell[1]);couch(p+Vector3(1.8,0,-2.3))
 for cell in [[0,4],[2,1],[6,6],[8,1]]:
  var p:=cell_center(cell[0],cell[1]);box(p+Vector3(2.4,.42,2.2),Vector3(.85,.84,.85),wood)
func ramp(start:Vector3,end:Vector3,width:float)->void:
 var diff:=end-start;var flat:=Vector2(diff.x,diff.z).length();var length:=sqrt(flat*flat+diff.y*diff.y)
 var n:=StaticBody3D.new();n.position=(start+end)*.5
 # Local Z axis points from start to end, with its elevation.
 n.rotation.y=atan2(diff.x,diff.z);n.rotation.x=-atan2(diff.y,flat);add_child(n)
 var sh:=BoxShape3D.new();sh.size=Vector3(width,.12,length)
 var cs:=CollisionShape3D.new();cs.shape=sh;n.add_child(cs)
func build_atrium()->void:
 # Atrium is much larger and taller than the maze, with original-sized furniture.
 box(Vector3(0,-.15,33),Vector3(63,.3,50),carpet)
 box(Vector3(0,10.08,33),Vector3(63,.16,50),ceiling)
 box(Vector3(31.5,5,33),Vector3(.2,10,50),wall)
 box(Vector3(0,5,58),Vector3(63,10,.2),wall)
 # Wide central entrance from the maze at z=8.
 for x in [-18.25,18.25]:box(Vector3(x,5,8),Vector3(26.5,10,.2),wall)
 box(Vector3(0,6.85,8),Vector3(10,6.3,.2),wall)
 # Left wall has lower door to the enclosed route and upper door to the walkway.
 box(Vector3(-31.5,5,11.5),Vector3(.2,10,7),wall)
 box(Vector3(-31.5,5,36),Vector3(.2,10,34),wall)
 box(Vector3(-31.5,5,57),Vector3(.2,10,2),wall)
 box(Vector3(-31.5,6.8,17),Vector3(.2,6.4,4),wall)
 box(Vector3(-31.5,2.3,54.5),Vector3(.2,4.6,3),wall)
 box(Vector3(-31.5,9.25,54.5),Vector3(.2,1.5,3),wall)
 for x in [-27,-18,-9,0,9,18,27]:
  for z in [11,18,25,32,39,46,53]:glow(Vector3(x,9.98,z))
 for x in [-20,0,20]:
  for z in [18,39,53]:light(Vector3(x,9.0,z),20)
 # Upper balcony along east and rear, about five metres above the floor.
 box(Vector3(27,UPPER-.15,33),Vector3(9,.3,49.7),carpet)
 box(Vector3(0,UPPER-.15,55),Vector3(63,.3,5.8),carpet)
 box(Vector3(22.58,UPPER+.40,32),Vector3(.18,.8,47.7),wall)
 box(Vector3(22.58,UPPER+.82,32),Vector3(.22,.06,47.7),darkwood,false)
 box(Vector3(0,UPPER+.40,52.1),Vector3(38,.8,.18),wall)
 box(Vector3(0,UPPER+.82,52.1),Vector3(38,.06,.22),darkwood,false)
 # Long broad staircase. Invisible continuous collision makes it walkable.
 for i in 20:
  var h:float=(i+1)*UPPER/20.0
  box(Vector3(-25,h*.5,31.65+i*1.06),Vector3(5.5,h,1.07),carpet,false)
 ramp(Vector3(-25,-.35,29.5),Vector3(-25,UPPER,52.3),5.5)
 # Rows of chairs retain normal-sized proportions, with space around them.
 for x in [-8,-4,0,4,8]:
  for z in [23,27,31,35]:chair(Vector3(x,0,z))
 for p in [Vector3(-26,0,22),Vector3(18,0,47),Vector3(28,0,15)]:cabinet(p)
 for p in [Vector3(16,0,23),Vector3(-18,0,41)]:couch(p)
 for x in [-28,-27,-26]:box(Vector3(x,.15,43),Vector3(.9,.3,.65),cushion)
 desk(Vector3(-15,0,15));chair(Vector3(-15,0,16.5))
 # Pale lamp and an upright board echo details visible in references.
 box(Vector3(-14,.6,44),Vector3(.7,1.2,.7),wood)
 box(Vector3(-14,1.3,44),Vector3(.32,.25,.32),light_panel,false)
 box(Vector3(13,1.4,32),Vector3(.14,2.8,1.8),wood)
func build_enclosed_route()->void:
 # Lower doorway at x=-31.5,z=17 -> ascending enclosed corridor -> upper rear balcony.
 # Ramp rises west, then continues at upper level around a pair of right-angle turns.
 var start:=Vector3(-31.0,-.15,17);var end:=Vector3(-50.35,UPPER+.35,17)
 var slope:=atan2(UPPER+.50,19.35);var slope_len:=sqrt(19.35*19.35+(UPPER+.50)*(UPPER+.50))
 var floor:=box((start+end)*.5-Vector3(0,.09,0),Vector3(slope_len,.18,4),carpet)
 floor.rotation.z=-slope
 for z in [14.9,19.1]:
  var side:=box(Vector3(-40.675,(UPPER+.20)*.5+1.9,z),Vector3(slope_len,3.8,.2),wall);side.rotation.z=-slope
 var roof:=box(Vector3(-40.675,(UPPER+.20)*.5+3.8,17),Vector3(slope_len,.16,4.4),ceiling);roof.rotation.z=-slope
 for x in [-34,-39,-44,-49]:
  var y:float=maxf(0.0,(-31.0-x)*(UPPER+.50)/19.35-.15)
  glow(Vector3(x,y+3.68,17))
 # Upper north-south segment (x around -52) and eastward segment to the balcony.
 box(Vector3(-52,UPPER-.15,35),Vector3(4,.3,40),carpet)
 box(Vector3(-52,UPPER+3.8,35),Vector3(4.2,.16,40),ceiling)
 # Close the small seam between the inclined and level sections.
 box(Vector3(-51,UPPER+1.9,14.9),Vector3(3,3.8,.2),wall)
 box(Vector3(-51,UPPER+3.8,17),Vector3(3,.16,4.2),ceiling)
 box(Vector3(-54.1,UPPER+1.9,35),Vector3(.2,3.8,40),wall)
 box(Vector3(-49.9,UPPER+1.9,35),Vector3(.2,3.8,31),wall)
 box(Vector3(-41.75,UPPER-.15,54.5),Vector3(20.5,.3,3),carpet)
 box(Vector3(-41.75,UPPER+3.8,54.5),Vector3(20.5,.16,3),ceiling)
 box(Vector3(-41.75,UPPER+1.9,52.9),Vector3(20.5,3.8,.2),wall)
 box(Vector3(-41.75,UPPER+1.9,56.1),Vector3(20.5,3.8,.2),wall)
 box(Vector3(-52,UPPER+1.9,55.1),Vector3(4,3.8,.2),wall)
 for z in [23,30,37,44,51]:glow(Vector3(-52,UPPER+3.68,z))
 for x in [-47,-40,-34]:glow(Vector3(x,UPPER+3.68,54.5))
 light(Vector3(-45,UPPER+2.9,17),12);light(Vector3(-52,UPPER+3,36),15);light(Vector3(-41,UPPER+3,54.5),12)
func make_map()->void:
 build_maze();build_atrium();build_enclosed_route()
