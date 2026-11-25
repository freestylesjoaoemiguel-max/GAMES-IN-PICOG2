extends RefCounted
# Motion JPEG in a standard RIFF AVI container. Video only; no microphone capture.
var file:FileAccess
var frames:Array[Dictionary]=[]
var width:=0
var height:=0
var fps:=10
var riff_size_offset:=0
var total_frames_offset:=0
var stream_frames_offset:=0
var movi_size_offset:=0
var movi_base:=0
var error:=""
func tag(s:String)->void:file.store_buffer(s.to_ascii_buffer())
func put16(n:int)->void:file.store_16(n)
func put32(n:int)->void:file.store_32(n)
func patch32(p:int,n:int)->void:
 var cursor:=file.get_position();file.seek(p);put32(n);file.seek(cursor)
func start(path:String,w:int,h:int,rate:int)->bool:
 width=w;height=h;fps=rate;frames.clear();error=""
 file=FileAccess.open(path,FileAccess.WRITE)
 if file==null:error="Não foi possível criar o vídeo";return false
 tag("RIFF");riff_size_offset=file.get_position();put32(0);tag("AVI ")
 tag("LIST");put32(192);tag("hdrl")
 tag("avih");put32(56);put32(1000000/fps);put32(width*height*4*fps);put32(0);put32(0x10)
 total_frames_offset=file.get_position();put32(0);put32(0);put32(1);put32(width*height*4);put32(width);put32(height)
 for i in 4:put32(0)
 tag("LIST");put32(116);tag("strl")
 tag("strh");put32(56);tag("vids");tag("MJPG");put32(0);put16(0);put16(0);put32(0);put32(1);put32(fps);put32(0)
 stream_frames_offset=file.get_position();put32(0);put32(width*height*4);put32(0xffffffff);put32(0)
 put16(0);put16(0);put16(width);put16(height)
 tag("strf");put32(40);put32(40);put32(width);put32(height);put16(1);put16(24);tag("MJPG");put32(width*height*3);put32(0);put32(0);put32(0);put32(0)
 tag("LIST");movi_size_offset=file.get_position();put32(0);movi_base=file.get_position();tag("movi")
 return true
func append_jpeg(jpeg:PackedByteArray)->bool:
 if file==null or jpeg.is_empty():return false
 if file.get_position()+jpeg.size()>1900000000:error="Vídeo atingiu o tamanho máximo";return false
 var offset:=file.get_position()-movi_base
 tag("00dc");put32(jpeg.size());file.store_buffer(jpeg)
 if jpeg.size()%2: file.store_8(0)
 frames.append({"offset":offset,"size":jpeg.size()})
 if file.get_error()!=OK:error="Falha ao gravar o vídeo";return false
 return true
func finish()->bool:
 if file==null:return false
 if frames.is_empty():error="Nenhum quadro gravado";file.close();file=null;return false
 var end_movi:=file.get_position();patch32(movi_size_offset,end_movi-movi_base)
 tag("idx1");put32(frames.size()*16)
 for entry in frames:
  tag("00dc");put32(0x10);put32(entry.offset);put32(entry.size)
 patch32(total_frames_offset,frames.size());patch32(stream_frames_offset,frames.size());patch32(riff_size_offset,file.get_position()-8)
 var ok:=file.get_error()==OK
 if not ok:error="Falha ao finalizar o vídeo"
 file.close();file=null;return ok
