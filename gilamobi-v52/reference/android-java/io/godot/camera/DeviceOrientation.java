package io.godot.camera;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.WindowManager;
import android.util.Log;

/** OS-fused relative rotation, sampled independently of the game's render rate. */
final class DeviceOrientation implements SensorEventListener {
 private final Activity activity;
 private final SensorManager manager;
 private Sensor sensor;
 private HandlerThread thread;
 private volatile boolean active;
 private volatile float[] latest;
 private volatile long updated;
 DeviceOrientation(Activity activity){this.activity=activity;manager=(SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);}
 synchronized boolean start(){
  stop();
  try{
   if(manager==null)return false;
   sensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
   if(sensor==null)sensor=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
   if(sensor==null)return false;
   thread=new HandlerThread("GilaOrientation");thread.start();
   active=true;
   if(!manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME,new Handler(thread.getLooper()))){stop();return false;}
   return true;
  }catch(Exception e){Log.e("GilaOrientation","Cannot start",e);stop();return false;}
 }
 synchronized void stop(){
  active=false;latest=null;updated=0;
  if(manager!=null)manager.unregisterListener(this);
  if(thread!=null){thread.quitSafely();thread=null;}
  sensor=null;
 }
 @Override public void onSensorChanged(SensorEvent e){
  if(!active || e.values==null || e.values.length<3)return;
  float[] quat=new float[4];
  SensorManager.getQuaternionFromVector(quat,e.values); // w,x,y,z
  int display=((WindowManager)activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
  if(!active)return;
  latest=new float[]{quat[1],quat[2],quat[3],quat[0],(float)display};
  updated=SystemClock.elapsedRealtime();
 }
 @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
 float[] read(){float[] result=latest;return result!=null && SystemClock.elapsedRealtime()-updated<600?result:new float[0];}
}
