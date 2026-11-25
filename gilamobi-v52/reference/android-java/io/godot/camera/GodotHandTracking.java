package io.godot.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.UsedByGodot;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GodotHandTracking extends GodotPlugin {
 private static volatile GodotHandTracking instance;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final AtomicBoolean busy=new AtomicBoolean(false);
 private volatile boolean enabled=false;
 private volatile String state="desligado", json="[]";
 private volatile long resultAt=0, lastQueued=0;
 private long fpsStart=0;private int fpsCount=0;private volatile int trackingFps=0;
 private HandLandmarker detector;
 private FastCamera fast;
 private DeviceOrientation orientation;
 private volatile boolean orientationWanted;
 private volatile boolean previewEnabled=true;
 private volatile long inferenceMs=0;
 private final NativeVoiceCapture nativeVoice=new NativeVoiceCapture();
 private final SandboxVideoPublisher sandboxVideo=new SandboxVideoPublisher();
 private final SandboxVideoRecorder sandboxRecorder=new SandboxVideoRecorder(sandboxVideo);
 @UsedByGodot public int sandboxAndroidSdk(){return android.os.Build.VERSION.SDK_INT;}
 @UsedByGodot public boolean startSandboxRecording(String path,int w,int h,int fps){return sandboxRecorder.start(path,w,h,fps);}
 @UsedByGodot public void sandboxVideoFrame(byte[] frame,long timeUs){sandboxRecorder.submit(frame,timeUs);}
 @UsedByGodot public void stopSandboxRecording(){sandboxRecorder.stop(getActivity());}
 @UsedByGodot public String sandboxRecordingState(){return sandboxRecorder.status();}
 @UsedByGodot public boolean retrySandboxGallery(){return sandboxRecorder.retryPublish(getActivity());}
 @UsedByGodot public void publishSandboxVideo(String path){sandboxVideo.publish(getActivity(),path);}
 @UsedByGodot public String sandboxVideoStatus(){return sandboxVideo.status();}
 public GodotHandTracking(Godot godot){super(godot);instance=this;}
 @Override public String getPluginName(){return "GodotHandTracking";}
 @UsedByGodot public boolean startNativeVoice(){return nativeVoice.start();}
 @UsedByGodot public void stopNativeVoice(){nativeVoice.stop();}
 @UsedByGodot public byte[] getNativeVoice(){return nativeVoice.take();}
 @UsedByGodot public String getNativeVoiceState(){return nativeVoice.state();}
 @UsedByGodot public void startOrientation(){orientationWanted=true;if(orientation==null)orientation=new DeviceOrientation(getActivity());orientation.start();}
 @UsedByGodot public void stopOrientation(){orientationWanted=false;if(orientation!=null)orientation.stop();}
 @UsedByGodot public float[] getOrientation(){return orientation==null?new float[0]:orientation.read();}
 @UsedByGodot public void startTracking(){
  if(enabled)return;
  enabled=true; state="carregando"; json="[]";resultAt=0;lastQueued=0;trackingFps=0;fpsCount=0;fpsStart=SystemClock.elapsedRealtime();
  worker.execute(()->{
   try {
    if(detector==null){
     BaseOptions options=BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build();
     HandLandmarker.HandLandmarkerOptions settings=HandLandmarker.HandLandmarkerOptions.builder()
      .setBaseOptions(options).setRunningMode(RunningMode.VIDEO).setNumHands(2)
      .setMinHandDetectionConfidence(0.5f).setMinHandPresenceConfidence(0.5f).setMinTrackingConfidence(0.5f).build();
     detector=HandLandmarker.createFromOptions(getActivity(),settings);
    }
    if(enabled)state="pronto";
   }catch(Throwable e){Log.e("GilaHand", "Failed to initialize",e);state="erro: "+e.getClass().getSimpleName()+" "+String.valueOf(e.getMessage());enabled=false;}
  });
 }
 @UsedByGodot public void stopTracking(){enabled=false;state="desligado";json="[]";resultAt=0;trackingFps=0;}
 @UsedByGodot public String getTrackingState(){return state;}
 @UsedByGodot public String getLandmarks(){return enabled && SystemClock.elapsedRealtime()-resultAt<450 ? json : "[]";}
 @UsedByGodot public int getTrackingFps(){return trackingFps;}
 @UsedByGodot public void setCameraPreviewEnabled(boolean value){previewEnabled=value;}
 boolean previewEnabled(){return previewEnabled;}
 @UsedByGodot public int getInferenceMs(){return (int)inferenceMs;}
 @UsedByGodot public void startFastCamera(String id){if(fast==null)fast=new FastCamera(getActivity(),this);fast.start(id);}
 @UsedByGodot public void stopFastCamera(){if(fast!=null)fast.stop();}
 @UsedByGodot public String getFastCameraState(){return fast==null?"desligada":fast.state();}
 @UsedByGodot public int getFastCameraFps(){return fast==null?0:fast.fps();}
 @UsedByGodot public int getFastFrameSequence(){return fast==null?0:fast.sequence();}
 @UsedByGodot public int getFastFrameWidth(){FrameConverter.Frame f=fast==null?null:fast.frame();return f==null?0:f.width;}
 @UsedByGodot public int getFastFrameHeight(){FrameConverter.Frame f=fast==null?null:fast.frame();return f==null?0:f.height;}
 @UsedByGodot public byte[] getFastFrame(){FrameConverter.Frame f=fast==null?null:fast.frame();return f==null||f.rgba==null?new byte[0]:f.rgba;}
 private boolean reserve(){
  if(!enabled||!state.equals("pronto"))return false;
  long now=SystemClock.elapsedRealtime();
  // Keep one inference in flight, drop stale frames, and don't add extra idle
  // time proportional to the last inference (that imposed a 4 FPS ceiling).
  long minPeriod=67; // up to ~15 Hz, subject to inference speed
  if(android.os.Build.VERSION.SDK_INT>=29){
   android.os.PowerManager pm=(android.os.PowerManager)getActivity().getSystemService(android.content.Context.POWER_SERVICE);
   if(pm!=null){int t=pm.getCurrentThermalStatus();
    if(t>=android.os.PowerManager.THERMAL_STATUS_SEVERE)minPeriod=167;
    else if(t>=android.os.PowerManager.THERMAL_STATUS_MODERATE)minPeriod=100;
   }
  }
  if(now-lastQueued<minPeriod || !busy.compareAndSet(false,true))return false;
  lastQueued=now;return true;
 }
 boolean reserveFastFrame(){return reserve();}
 void cancelFastFrame(){busy.set(false);}
 void submitReservedCameraArgb(int[] argb,int width,int height){
  worker.execute(()->{
   Bitmap bitmap=null;
   try {if(!enabled||detector==null)return;bitmap=Bitmap.createBitmap(argb,width,height,Bitmap.Config.ARGB_8888);detect(bitmap);}
   catch(Throwable e){error(e);}
   finally{if(bitmap!=null)bitmap.recycle();busy.set(false);}
  });
 }
 // Compatibility with the old JPEG camera feed; unused by the fast mode.
 public static void submitCameraJpeg(byte[] jpeg,int rotation){
  GodotHandTracking h=instance;if(h==null||!h.reserve())return;
  h.worker.execute(()->{
   Bitmap source=null,oriented=null;
   try{
    if(!h.enabled||h.detector==null)return;
    source=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(source==null)return;
    if(rotation!=0){Matrix m=new Matrix();m.postRotate(rotation);oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);}else oriented=source;
    h.detect(oriented);
   }catch(Throwable e){h.error(e);}finally{if(oriented!=null&&oriented!=source)oriented.recycle();if(source!=null)source.recycle();h.busy.set(false);}
  });
 }
 private void detect(Bitmap bitmap){
  long started=SystemClock.elapsedRealtime();
  MPImage mp=new BitmapImageBuilder(bitmap).build();
  try {
   HandLandmarkerResult result=detector.detectForVideo(mp,SystemClock.elapsedRealtime());
   StringBuilder out=new StringBuilder("[");List<List<NormalizedLandmark>> hands=result.landmarks();
   for(int i=0;i<hands.size();i++){
    if(i>0)out.append(',');out.append('[');List<NormalizedLandmark> pts=hands.get(i);
    for(int j=0;j<pts.size();j++){if(j>0)out.append(',');NormalizedLandmark p=pts.get(j);out.append('[').append(p.x()).append(',').append(p.y()).append(']');}
    out.append(']');
   }
   out.append(']');long now=SystemClock.elapsedRealtime();
   if(enabled){json=out.toString();resultAt=now;fpsCount++;if(now-fpsStart>=1000){trackingFps=(int)(fpsCount*1000L/Math.max(1,now-fpsStart));fpsStart=now;fpsCount=0;}}
  }finally{inferenceMs=SystemClock.elapsedRealtime()-started;mp.close();}
 }
 private void error(Throwable e){Log.e("GilaHand","Inference error",e);state="erro: "+e.getClass().getSimpleName()+" "+String.valueOf(e.getMessage());enabled=false;json="[]";}
 @Override public void onMainPause(){nativeVoice.stop();if(fast!=null)fast.stop();if(orientation!=null)orientation.stop();}
 @Override public void onMainResume(){if(orientationWanted && orientation!=null)orientation.start();}
 @Override public void onMainDestroy(){nativeVoice.stop();orientationWanted=false;if(orientation!=null)orientation.stop();if(fast!=null)fast.stop();enabled=false;worker.execute(()->{if(detector!=null){detector.close();detector=null;}});worker.shutdown();if(instance==this)instance=null;}
}
