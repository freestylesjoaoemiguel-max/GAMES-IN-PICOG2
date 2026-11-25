package io.godot.camera;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.Range;
import android.view.Surface;
import android.view.WindowManager;
import java.util.Arrays;

/** Separate real-time camera preview using YUV_420_888, never camera JPEG. */
final class FastCamera {
 private final Activity activity;
 private final GodotHandTracking owner;
 private volatile boolean running=false;
 private volatile int generation=0;
 private HandlerThread thread;
 private Handler handler;
 private CameraDevice camera;
 private CameraCaptureSession session;
 private ImageReader reader;
 private int rotation;
 private Range<Integer> targetFps;
 private volatile FrameConverter.Frame frame;
 private volatile int sequence;
 private volatile String state="desligada";
 private long lastFrameMs, fpsStart;private int fpsCount;private volatile int fps;
 FastCamera(Activity activity,GodotHandTracking owner){this.activity=activity;this.owner=owner;}
 synchronized void stop(){
  running=false;generation++;frame=null;fps=0;
  if(session!=null){session.close();session=null;}
  if(camera!=null){camera.close();camera=null;}
  if(reader!=null){reader.setOnImageAvailableListener(null,null);reader.close();reader=null;}
  if(thread!=null){thread.quitSafely();thread=null;handler=null;}
  state="desligada";
 }
 @SuppressLint("MissingPermission") synchronized void start(String id){
  stop(); running=true;int gen=++generation;state="abrindo";fpsStart=SystemClock.elapsedRealtime();fpsCount=0;lastFrameMs=0;
  try{
   CameraManager manager=(CameraManager)activity.getSystemService(Activity.CAMERA_SERVICE);
   CameraCharacteristics chars=manager.getCameraCharacteristics(id);
   StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
   if(map==null)throw new IllegalStateException("Sem formatos de câmera");
   Size[] sizes=map.getOutputSizes(ImageFormat.YUV_420_888);
   if(sizes==null || sizes.length==0)throw new IllegalStateException("Formato YUV não disponível");
   Size best=null;double bestScore=Double.MAX_VALUE;
   for(Size s:sizes){int w=s.getWidth(),h=s.getHeight();if(w<160||h<120||w%2!=0||h%2!=0)continue;
    double score=Math.abs(Math.log((double)(w*h)/(640*480))) + Math.abs((double)w/h-4.0/3.0)*2;
    if(score<bestScore){bestScore=score;best=s;}}
   if(best==null)throw new IllegalStateException("Sem resolução compatível");
   targetFps=null; Range<Integer>[] ranges=chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
   if(ranges!=null){for(Range<Integer> range:ranges){
    if(range.getLower()<=15 && range.getUpper()>=15 && (targetFps==null || range.getUpper()<targetFps.getUpper() || (range.getUpper().equals(targetFps.getUpper()) && range.getLower()>targetFps.getLower())))targetFps=range;
   }}
   Integer sensor=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);int orient=sensor==null?0:sensor;
   int dr=((WindowManager)activity.getSystemService(Activity.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
   int device=dr==Surface.ROTATION_90?270:dr==Surface.ROTATION_180?180:dr==Surface.ROTATION_270?90:0;
   rotation=(orient+device+360)%360;
   thread=new HandlerThread("GilaCameraYUV");thread.start();handler=new Handler(thread.getLooper());
   reader=ImageReader.newInstance(best.getWidth(),best.getHeight(),ImageFormat.YUV_420_888,3);
   reader.setOnImageAvailableListener(r->onImage(r,gen),handler);
   manager.openCamera(id,new CameraDevice.StateCallback(){
    @Override public void onOpened(CameraDevice dev){
     synchronized(FastCamera.this){
      if(!running||gen!=generation){dev.close();return;}
      camera=dev;
      try{dev.createCaptureSession(Arrays.asList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
       @Override public void onConfigured(CameraCaptureSession cs){synchronized(FastCamera.this){
        if(!running||gen!=generation||camera==null||reader==null){cs.close();return;}
        session=cs;try{CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(reader.getSurface());if(targetFps!=null)b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,targetFps);cs.setRepeatingRequest(b.build(),null,handler);state="ativa";}
        catch(Exception e){fail("Prévia: "+e.getClass().getSimpleName());}
       }}
       @Override public void onConfigureFailed(CameraCaptureSession cs){cs.close();if(running && gen==generation)fail("Falha ao configurar câmera");}
      },handler);}catch(Exception e){fail("Sessão: "+e.getClass().getSimpleName());}
     }
    }
    @Override public void onDisconnected(CameraDevice dev){dev.close();if(running && gen==generation)fail("Câmera desconectada");}
    @Override public void onError(CameraDevice dev,int error){dev.close();if(running && gen==generation)fail("Câmera erro "+error);}
   },handler);
  }catch(Exception e){Log.e("GilaFastCam","Camera start failed",e);fail(e.getClass().getSimpleName()+": "+e.getMessage());}
 }
 private void fail(String message){state="erro: "+message;Log.e("GilaFastCam",message);}
 private void onImage(ImageReader r,int gen){
  if(!running||gen!=generation)return;
  Image image=null;boolean reserved=false;
  try{
   image=r.acquireLatestImage();if(image==null)return;
   long now=SystemClock.elapsedRealtime();
   // Preview and detection have separate schedules: a slow inference must
   // not freeze the camera image. Keep preview conversions bounded to 10 Hz.
   boolean doPreview=owner.previewEnabled() && now-lastFrameMs>=100;
   reserved=owner.reserveFastFrame();
   if(!reserved && !doPreview)return;
   Image.Plane[] p=image.getPlanes();
   FrameConverter.Frame f=FrameConverter.convert(p[0].getBuffer(),p[0].getRowStride(),p[0].getPixelStride(),p[1].getBuffer(),p[1].getRowStride(),p[1].getPixelStride(),p[2].getBuffer(),p[2].getRowStride(),p[2].getPixelStride(),image.getWidth(),image.getHeight(),rotation,doPreview);
   if(!running||gen!=generation)return;
   if(doPreview){
    frame=f;sequence++;lastFrameMs=now;
    fpsCount++;if(now-fpsStart>=1000){fps=(int)(fpsCount*1000L/Math.max(1,now-fpsStart));fpsStart=now;fpsCount=0;}
   }
   if(reserved){owner.submitReservedCameraArgb(f.argb,f.width,f.height);reserved=false;}
  }catch(Exception e){if(running){Log.e("GilaFastCam","frame",e);fail("Erro na imagem: "+e.getClass().getSimpleName());}}
  finally{if(reserved)owner.cancelFastFrame();if(image!=null)image.close();}
 }
 FrameConverter.Frame frame(){return frame;}
 int sequence(){return sequence;}
 int fps(){return fps;}
 String state(){return state;}
}
