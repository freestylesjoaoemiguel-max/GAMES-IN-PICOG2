extends RefCounted
const COLORS := [Color("5fae24"),Color("4797eb"),Color("e74c58"),Color("ebc64e"),Color("aa73e4"),Color("f286b5"),Color("e9903f"),Color("d2d7df"),Color("4f515c"),Color("5bd4bd")]
const COLOR_NAMES := ["Verde", "Azul", "Vermelho", "Amarelo", "Roxo", "Rosa", "Laranja", "Branco", "Cinza", "Turquesa"]
const NAMES := ["Nenhum", "Boné vermelho", "Boné azul", "Chapéu alto", "Coroa", "Gorro", "Capacete", "Cartola roxa", "Chifres", "Antenas", "Orelhas de coelho", "Óculos escuros", "Óculos azuis", "Viseira", "Máscara", "Fone de ouvido", "Laço", "Chapéu de festa", "Auréola", "Chapéu de pirata", "Bandana", "Balde na cabeça"]

static func paint(mesh: MeshInstance3D, color_index: int) -> void:
 if not is_instance_valid(mesh) or not mesh.mesh: return
 color_index=clampi(color_index,0,COLORS.size()-1)
 if color_index==0:
  mesh.set_surface_override_material(0,null)
  return
 var original = mesh.mesh.surface_get_material(0)
 if not original is StandardMaterial3D or original.albedo_texture == null: return
 var shader := Shader.new()
 shader.code="""shader_type spatial;
render_mode cull_back;
uniform sampler2D tex : source_color, filter_linear_mipmap;
uniform vec4 fur : source_color;
void fragment(){ vec4 p=texture(tex,UV); float mask=smoothstep(0.025,0.12,p.g-max(p.r,p.b)); float light=clamp(p.g*1.7,0.0,1.0); ALBEDO=mix(p.rgb,fur.rgb*light,mask); ROUGHNESS=1.0; ALPHA=p.a; }
"""
 var mat:=ShaderMaterial.new();mat.shader=shader;mat.set_shader_parameter("tex",original.albedo_texture);mat.set_shader_parameter("fur",COLORS[color_index]);mesh.set_surface_override_material(0,mat)

static func shape(root: Node3D, kind: String, pos: Vector3, size: Vector3, color: Color, layer: int) -> MeshInstance3D:
 var node:=MeshInstance3D.new()
 if kind=="ball":
  var s:=SphereMesh.new();s.radius=.5;s.height=1.0;node.mesh=s
 elif kind=="cyl":
  var c:=CylinderMesh.new();c.top_radius=.5;c.bottom_radius=.5;c.height=1.0;c.radial_segments=24;node.mesh=c
 elif kind=="cone":
  var c:=CylinderMesh.new();c.top_radius=0;c.bottom_radius=.5;c.height=1.0;c.radial_segments=24;node.mesh=c
 elif kind=="ring":
  var t:=TorusMesh.new();t.inner_radius=.34;t.outer_radius=.50;t.rings=20;t.ring_segments=12;node.mesh=t
 else:
  var b:=BoxMesh.new();b.size=Vector3.ONE;node.mesh=b
 node.position=pos;node.scale=size;node.layers=layer
 var mat:=StandardMaterial3D.new();mat.albedo_color=color;mat.roughness=.6;node.material_override=mat
 root.add_child(node);return node

static func apply_accessory(parent: Node3D, index: int, layer: int, size_factor := 1.0) -> void:
 var old=parent.get_node_or_null("AcessorioCosmetico")
 if old: old.free()
 index=clampi(index,0,NAMES.size()-1)
 if index==0:return
 var root:=Node3D.new();root.name="AcessorioCosmetico";root.scale=Vector3.ONE*size_factor;parent.add_child(root)
 # Headwear was visibly hovering above the scalp in the old preview.
 if index in [1,2,3,4,5,6,7,8,9,10,16,17,19]:root.position.y=-.075
 elif index==20:root.position.y=-.03
 var black:=Color("20222b");var gold:=Color("f3c653");var red:=Color("e94d52");var blue:=Color("487de0");var white:=Color("f1e7dd");var purple:=Color("8b52c4")
 if index in [1,2]:
  var col=red if index==1 else blue
  shape(root,"ball",Vector3(0,1.59,-.02),Vector3(.53,.24,.45),col,layer)
  shape(root,"box",Vector3(0,1.53,-.30),Vector3(.50,.035,.30),col,layer)
 elif index in [3,7]:
  var col=black if index==3 else purple
  shape(root,"cyl",Vector3(0,1.60,0),Vector3(.65,.04,.53),col,layer)
  shape(root,"cyl",Vector3(0,1.76,0),Vector3(.46,.30,.40),col,layer)
  shape(root,"cyl",Vector3(0,1.65,0),Vector3(.48,.06,.42),gold,layer)
 elif index==4:
  shape(root,"cyl",Vector3(0,1.61,0),Vector3(.50,.12,.43),gold,layer)
  for x in [-.18,0.0,.18]: shape(root,"cone",Vector3(x,1.73,0),Vector3(.15,.20,.15),gold,layer)
 elif index==5:
  shape(root,"ball",Vector3(0,1.63,0),Vector3(.53,.31,.47),red,layer)
  shape(root,"cyl",Vector3(0,1.54,0),Vector3(.54,.07,.48),white,layer)
  shape(root,"ball",Vector3(0,1.81,0),Vector3(.10,.10,.10),white,layer)
 elif index==6:
  shape(root,"ball",Vector3(0,1.60,0),Vector3(.57,.28,.49),gold,layer)
  shape(root,"box",Vector3(0,1.53,-.27),Vector3(.54,.04,.20),gold,layer)
 elif index==8:
  for x in [-.20,.20]:
   var horn=shape(root,"cone",Vector3(x,1.69,0),Vector3(.15,.29,.15),white,layer);horn.rotation.z=.25 if x<0 else -.25
 elif index==9:
  for x in [-.18,.18]:
   shape(root,"cyl",Vector3(x,1.72,0),Vector3(.025,.31,.025),black,layer)
   shape(root,"ball",Vector3(x,1.89,0),Vector3(.11,.11,.11),red,layer)
 elif index==10:
  for x in [-.17,.17]:
   shape(root,"ball",Vector3(x,1.75,0),Vector3(.16,.43,.12),white,layer)
   shape(root,"ball",Vector3(x,1.77,-.065),Vector3(.08,.29,.025),Color("f39fb4"),layer)
 elif index in [11,12]:
  var col=black if index==11 else blue
  for x in [-.13,.13]: shape(root,"ball",Vector3(x,1.39,-.29),Vector3(.22,.15,.06),col,layer)
  shape(root,"box",Vector3(0,1.39,-.30),Vector3(.10,.035,.04),black,layer)
 elif index==13:
  shape(root,"box",Vector3(0,1.39,-.30),Vector3(.49,.14,.07),blue,layer)
  shape(root,"box",Vector3(0,1.47,-.27),Vector3(.54,.04,.08),black,layer)
 elif index==14:
  shape(root,"box",Vector3(0,1.20,-.30),Vector3(.39,.20,.08),white,layer)
  shape(root,"box",Vector3(0,1.20,-.35),Vector3(.24,.04,.02),black,layer)
 elif index==15:
  for x in [-.28,.28]:shape(root,"ball",Vector3(x,1.40,0),Vector3(.15,.22,.18),blue,layer)
  var band=shape(root,"ring",Vector3(0,1.48,0),Vector3(.62,.10,.55),black,layer);band.rotation.x=PI/2
 elif index==16:
  for x in [-.12,.12]:
   var bow=shape(root,"ball",Vector3(x,1.67,-.05),Vector3(.26,.15,.12),red,layer);bow.rotation.z=.35 if x<0 else -.35
  shape(root,"ball",Vector3(0,1.67,-.12),Vector3(.10,.10,.10),gold,layer)
 elif index==17:
  shape(root,"cone",Vector3(0,1.78,0),Vector3(.46,.43,.42),purple,layer)
  shape(root,"ball",Vector3(0,2.0,0),Vector3(.09,.09,.09),gold,layer)
 elif index==18:
  shape(root,"ring",Vector3(0,1.84,0),Vector3(.52,.10,.52),gold,layer)
 elif index==19:
  shape(root,"ball",Vector3(0,1.64,0),Vector3(.70,.30,.38),black,layer)
  shape(root,"box",Vector3(0,1.58,-.18),Vector3(.58,.07,.12),black,layer)
  shape(root,"box",Vector3(0,1.67,-.22),Vector3(.15,.05,.03),white,layer)
 elif index==20:
  shape(root,"cyl",Vector3(0,1.50,0),Vector3(.55,.10,.48),red,layer)
  for x in [-.10,.10]:shape(root,"box",Vector3(x,1.45,.25),Vector3(.12,.22,.025),red,layer)

 elif index==21:
  # An upside-down tapered metal bucket, fitted over the scalp.
  var bucket:=MeshInstance3D.new();var mesh:=CylinderMesh.new()
  mesh.top_radius=.235;mesh.bottom_radius=.30;mesh.height=.37;mesh.radial_segments=32;bucket.mesh=mesh
  bucket.position=Vector3(0,1.615,0);bucket.layers=layer
  var metal:=StandardMaterial3D.new();metal.albedo_color=Color("8d9da2");metal.metallic=.72;metal.roughness=.4;bucket.material_override=metal;root.add_child(bucket)
  for h in [1.445,1.52,1.70,1.795]:
   var radius:=lerpf(.30,.235,clampf((h-1.43)/.37,0.0,1.0))
   var band=shape(root,"ring",Vector3(0,h,0),Vector3(radius*2.02,.075,radius*2.02),Color("c7d1cf"),layer)
   band.material_override.metallic=.75;band.material_override.roughness=.32
  for x in [-.29,.29]:
   var bolt=shape(root,"ball",Vector3(x,1.61,-.02),Vector3(.045,.045,.045),Color("dce2df"),layer)
   bolt.material_override.metallic=.8
  # Arched wire handle with rounded joints.
  var handle_color:=Color("c6d0ce")
  var points: Array[Vector3]=[]
  for k in 15:
   var a:=PI-float(k)*PI/14.0
   points.append(Vector3(cos(a)*.30,1.60+sin(a)*.37,-.02))
  for k in range(points.size()-1):
   var a:Vector3=points[k];var b:Vector3=points[k+1];var d:=b-a
   var wire=shape(root,"cyl",(a+b)*.5,Vector3(.023,d.length(),.023),handle_color,layer)
   wire.basis=Basis(Quaternion(Vector3.UP,d.normalized()))*Basis.from_scale(wire.scale)
   wire.material_override.metallic=.8
 # Small modeled details improve readability without using flat stickers.
 if index in [1,2]:
  var col=red if index==1 else blue
  shape(root,"cyl",Vector3(0,1.56,-.02),Vector3(.54,.035,.46),col.darkened(.28),layer)
  shape(root,"ball",Vector3(0,1.72,-.02),Vector3(.05,.035,.05),col.lightened(.25),layer)
  for x in [-.13,.13]:shape(root,"ball",Vector3(x,1.62,-.245),Vector3(.022,.022,.02),white,layer)
 elif index in [3,7]:
  shape(root,"ball",Vector3(0,1.69,-.235),Vector3(.10,.08,.03),gold.lightened(.15),layer)
  shape(root,"box",Vector3(0,1.69,-.254),Vector3(.047,.032,.02),black,layer)
 elif index==4:
  for x in [-.16,0.0,.16]:
   shape(root,"ball",Vector3(x,1.62,-.225),Vector3(.07,.07,.035),red if x==0 else blue,layer)
 elif index==5:
  for x in [-.18,-.09,0.0,.09,.18]:shape(root,"ball",Vector3(x,1.54,-.235),Vector3(.035,.035,.025),Color("c6bdb5"),layer)
 elif index==6:
  shape(root,"box",Vector3(0,1.73,-.11),Vector3(.10,.05,.30),gold.darkened(.3),layer)
  shape(root,"box",Vector3(0,1.56,-.35),Vector3(.16,.035,.025),black,layer)
 elif index==8:
  for x in [-.20,.20]:shape(root,"ball",Vector3(x,1.55,0),Vector3(.20,.08,.19),Color("b8a894"),layer)
 elif index==9:
  for x in [-.18,.18]:shape(root,"ball",Vector3(x,1.56,0),Vector3(.10,.06,.10),black,layer)
 elif index==10:
  for x in [-.17,.17]:shape(root,"ball",Vector3(x,1.54,0),Vector3(.14,.08,.14),white,layer)
 elif index in [11,12]:
  for x in [-.13,.13]:
   shape(root,"ball",Vector3(x,1.39,-.265),Vector3(.26,.19,.065),black,layer)
   shape(root,"box",Vector3(x-.055,1.42,-.327),Vector3(.07,.015,.012),white,layer)
  for x in [-.255,.255]:shape(root,"box",Vector3(x,1.39,-.12),Vector3(.035,.025,.30),black,layer)
 elif index==13:
  shape(root,"box",Vector3(0,1.41,-.344),Vector3(.39,.025,.012),Color("a4e1ef"),layer)
  for x in [-.25,.25]:shape(root,"box",Vector3(x,1.40,-.12),Vector3(.035,.04,.30),black,layer)
 elif index==14:
  for y in [1.14,1.18,1.22,1.26]:shape(root,"box",Vector3(0,y,-.345),Vector3(.29,.012,.015),Color("b4bec4"),layer)
  for x in [-.22,.22]:shape(root,"box",Vector3(x,1.20,-.10),Vector3(.025,.025,.39),white,layer)
 elif index==15:
  for x in [-.30,.30]:shape(root,"ball",Vector3(x,1.40,-.015),Vector3(.08,.14,.13),black,layer)
 elif index==16:
  for x in [-.12,.12]:shape(root,"ball",Vector3(x,1.67,-.119),Vector3(.10,.05,.012),white,layer)
 elif index==17:
  for y in [1.66,1.75,1.84]:shape(root,"ring",Vector3(0,y,0),Vector3(.32-(y-1.66),.045,.29-(y-1.66)),gold,layer)
 elif index==18:
  shape(root,"ring",Vector3(0,1.86,0),Vector3(.44,.06,.44),Color("fff1ae"),layer)
 elif index==19:
  shape(root,"ball",Vector3(0,1.68,-.215),Vector3(.12,.11,.025),white,layer)
  for x in [-.03,.03]:shape(root,"ball",Vector3(x,1.69,-.23),Vector3(.025,.025,.014),black,layer)
  shape(root,"box",Vector3(0,1.64,-.23),Vector3(.075,.018,.014),black,layer)
 elif index==20:
  for x in [-.18,0.0,.18]:shape(root,"ball",Vector3(x,1.50,-.244),Vector3(.035,.035,.02),white,layer)
