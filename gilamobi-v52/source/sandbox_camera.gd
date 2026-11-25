extends Node3D
# Personal camera: summon to the hand or leave floating while recording.
var game:Node3D
var held:=false
var spawned:=false
var recording:=false
var writer
var view:SubViewport
var lens:Camera3D
var status:=""
var saved_path:=""
var native_plugin
var gallery_retry:=false
var frame_accum:=0.0
var elapsed:=0.0
const WIDTH:=480
const HEIGHT:=272
const FPS:=10
const MAX_DURATION:=120.0
func mesh_box(parent:Node3D,size:Vector3,pos:Vector3,color:Color)->void:
 var mesh:=BoxMesh.new();mesh.size=size
 var item:=MeshInstance3D.new();item.mesh=mesh;item.position=pos
 var material:=StandardMaterial3D.new();material.albedo_color=color;material.roughness=.65;item.material_override=material;parent.add_child(item)
func _ready()->void:
 name="CameraSandbox";visible=false
 mesh_box(self,Vector3(.31,.19,.045),Vector3.ZERO,Color("222a32"))
 mesh_box(self,Vector3(.055,.055,.018),Vector3(-.105,.045,-.03),Color("93baca"))
 view=SubViewport.new();view.name="VisaoCamera";view.size=Vector2i(WIDTH,HEIGHT);view.world_3d=game.get_viewport().find_world_3d();view.render_target_update_mode=SubViewport.UPDATE_DISABLED;add_child(view)
 lens=Camera3D.new();lens.name="Lente";lens.fov=70;lens.near=.05;lens.far=150;lens.cull_mask=1;view.add_child(lens);lens.current=true
 var screen:=MeshInstance3D.new();var quad:=QuadMesh.new();quad.size=Vector2(.265,.15);screen.mesh=quad;screen.position=Vector3(0,0,.025)
 var display:=StandardMaterial3D.new();display.shading_mode=BaseMaterial3D.SHADING_MODE_UNSHADED;display.albedo_texture=view.get_texture();screen.material_override=display;add_child(screen)
 if OS.get_name()=="Android" and Engine.has_singleton("GodotHandTracking"):
  var p=Engine.get_singleton("GodotHandTracking")
  if p.has_method("startSandboxRecording") and p.has_method("sandboxRecordingState"):native_plugin=p
func in_reach()->bool:return true
func camera_hand()->int:
 if is_instance_valid(game.g2_xr) and game.g2_xr.immersive:
  if game.g2_xr.tracked[1]:return 1
  if game.g2_xr.tracked[0]:return 0
  return -1
 return 1
func pickup_or_drop()->void:
 var hand:=camera_hand()
 if hand<0 and not held:status="Conecte um controle para pegar a camera";return
 if held:
  held=false;status=tr("Câmera parada no ar")
 else:
  spawned=true;visible=true;held=true
  if is_instance_valid(game.targets[hand]):global_position=game.targets[hand].global_position
  global_basis=game.camera.global_basis.orthonormalized()
  view.render_target_update_mode=SubViewport.UPDATE_ALWAYS;status=tr("Câmera na mão")
func toggle_record()->void:
 if recording:stop_recording();return
 if not spawned:status=tr("Pegue a câmera primeiro");return
 var stamp:=Time.get_datetime_string_from_system().replace(":","-").replace("T","_")
 var name:="GilaMobi_"+stamp+"_"+str(Time.get_ticks_msec())
 if OS.get_name()=="Android":
  if native_plugin==null:status=tr("Gravação indisponível neste aparelho");return
  if native_plugin.sandboxAndroidSdk()<29:
   var granted:=false
   for permission in OS.get_granted_permissions():
    if permission.ends_with("WRITE_EXTERNAL_STORAGE"):granted=true
   if not granted:
    OS.request_permission("android.permission.WRITE_EXTERNAL_STORAGE");status=tr("Permita armazenamento e toque em gravar novamente");return
  saved_path="user://"+name+".mp4"
  if not native_plugin.startSandboxRecording(ProjectSettings.globalize_path(saved_path),WIDTH,HEIGHT,FPS):
   status=str(native_plugin.sandboxRecordingState());return
 else:
  saved_path="user://"+name+".avi"
  writer=preload("res://avi_writer.gd").new()
  if not writer.start(saved_path,WIDTH,HEIGHT,FPS):status=writer.error;return
 gallery_retry=false;recording=true;frame_accum=0;elapsed=0;status=tr("Gravando vídeo sem áudio")
func stop_recording()->void:
 if not recording:return
 recording=false
 if OS.get_name()=="Android":
  if native_plugin!=null:native_plugin.stopSandboxRecording();status=tr("Salvando vídeo...")
 elif writer!=null:
  if writer.finish():
   status=tr("Vídeo salvo")
   if OS.has_feature("web"):
    JavaScriptBridge.download_buffer(FileAccess.get_file_as_bytes(saved_path),saved_path.get_file(),"video/x-msvideo")
    status="Download AVI solicitado ao navegador (sem audio)"
  else:status=writer.error;DirAccess.remove_absolute(ProjectSettings.globalize_path(saved_path))
  writer=null
func retry_gallery()->void:
 if native_plugin!=null and native_plugin.has_method("retrySandboxGallery") and native_plugin.retrySandboxGallery():
  gallery_retry=false;status=tr("Salvando vídeo...")
func _process(delta:float)->void:
 if not is_instance_valid(game) or not is_instance_valid(game.player):return
 if not game.game_running:
  if recording:stop_recording()
  return
 var hand:=camera_hand()
 if held and hand<0:held=false;status="Controle desconectado; camera parada"
 if held and is_instance_valid(game.targets[hand]):
  global_position=game.targets[hand].global_position;global_basis=game.camera.global_basis.orthonormalized()
 if is_instance_valid(lens):lens.global_transform=global_transform.translated_local(Vector3(0,0,-.10))
 if native_plugin!=null:
  var native_status:String=native_plugin.sandboxRecordingState()
  if native_status.begins_with("erro"):
   status=native_status;recording=false
  elif not recording and status==tr("Salvando vídeo...") and native_status not in ["finalizando","salvando"]:
   status=native_status;gallery_retry=native_status.begins_with("Falha ao exportar")
 if recording:
  elapsed+=delta;frame_accum+=delta
  if frame_accum>=1.0/FPS:
   var count:=mini(20,int(frame_accum*FPS));frame_accum-=float(count)/FPS
   var image:=view.get_texture().get_image()
   if image and not image.is_empty():
    if OS.get_name()=="Android":
     image.convert(Image.FORMAT_RGBA8)
     native_plugin.sandboxVideoFrame(image.get_data(),int(elapsed*1000000))
    else:
     image.convert(Image.FORMAT_RGB8)
     var jpeg:=image.save_jpg_to_buffer(.75)
     for i in count:
      if not writer.append_jpeg(jpeg):status=writer.error;stop_recording();break
  if elapsed>=MAX_DURATION:stop_recording()
func _notification(what:int)->void:
 if what==NOTIFICATION_APPLICATION_PAUSED or what==NOTIFICATION_WM_CLOSE_REQUEST:stop_recording()
func _exit_tree()->void:stop_recording()
