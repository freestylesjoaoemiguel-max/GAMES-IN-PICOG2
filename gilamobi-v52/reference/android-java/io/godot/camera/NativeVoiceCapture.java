package io.godot.camera;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.AudioEffect;
import android.util.Log;

/** 16 kHz PCM. Capture and its audio effects exist only while unmuted. */
final class NativeVoiceCapture {
 private static final int RATE=16000, CAPACITY=16000;
 private final Object lock=new Object();
 private final byte[] ring=new byte[CAPACITY];
 private int start=0,size=0;
 private volatile boolean running=false;
 private volatile String status="desligado";
 private AudioRecord recorder;
 private Thread worker;

 boolean start() {
  synchronized(lock) {
   if(running) return true;
   if(worker!=null && worker.isAlive()){status="aguarde o microfone";return false;}
   start=0;size=0;
   AudioRecord r=null;AudioEffect echo=null,noise=null;
   try {
    int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    if(min<=0) throw new IllegalStateException("formato indisponível");
    // Prefer the Android voice-processing route; some devices require the fallback.
    try { r=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,6400));
     if(r.getState()!=AudioRecord.STATE_INITIALIZED){r.release();r=null;}
    } catch(Throwable e){if(r!=null)try{r.release();}catch(Throwable ignored){} r=null;}
    if(r==null)r=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,6400));
    if(r.getState()!=AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("microfone indisponível");
    int session=r.getAudioSessionId();
    try{if(AcousticEchoCanceler.isAvailable()){echo=AcousticEchoCanceler.create(session);if(echo!=null)echo.setEnabled(true);}}catch(Throwable e){if(echo!=null)try{echo.release();}catch(Throwable ignored){} echo=null;}
    try{if(NoiseSuppressor.isAvailable()){noise=NoiseSuppressor.create(session);if(noise!=null)noise.setEnabled(true);}}catch(Throwable e){if(noise!=null)try{noise.release();}catch(Throwable ignored){} noise=null;}
    try {
     r.startRecording();
     if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("captura não iniciou");
    } catch(Throwable failedVoiceRoute) {
     if(echo!=null)try{echo.release();}catch(Throwable ignored){} echo=null;
     if(noise!=null)try{noise.release();}catch(Throwable ignored){} noise=null;
     try{r.release();}catch(Throwable ignored){} r=null;
     r=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,6400));
     if(r.getState()!=AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("microfone indisponível");
     session=r.getAudioSessionId();
     try{if(AcousticEchoCanceler.isAvailable()){echo=AcousticEchoCanceler.create(session);if(echo!=null)echo.setEnabled(true);}}catch(Throwable ignored){if(echo!=null)try{echo.release();}catch(Throwable ignored2){} echo=null;}
     try{if(NoiseSuppressor.isAvailable()){noise=NoiseSuppressor.create(session);if(noise!=null)noise.setEnabled(true);}}catch(Throwable ignored){if(noise!=null)try{noise.release();}catch(Throwable ignored2){} noise=null;}
     r.startRecording();
     if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("captura não iniciou");
    }
    recorder=r;running=true;status="gravando";
    final AudioRecord capture=r;final AudioEffect e=echo,n=noise;
    worker=new Thread(()->readLoop(capture,e,n),"GilaVoice");
    worker.start();return true;
   }catch(Throwable e){
    if(echo!=null)try{echo.release();}catch(Throwable ignored){}
    if(noise!=null)try{noise.release();}catch(Throwable ignored){}
    if(r!=null)try{r.release();}catch(Throwable ignored){}
    recorder=null;running=false;status="erro: "+e.getClass().getSimpleName();
    Log.e("GilaVoice","Cannot start microphone",e);return false;
   }
  }
 }
 private void readLoop(AudioRecord r,AudioEffect echo,AudioEffect noise){
  short[] samples=new short[640];
  try{
   while(running){
    int n=r.read(samples,0,samples.length);
    if(n<=0){if(running)status="erro de leitura";break;}
    synchronized(lock){
     if(!running)break;
     for(int i=0;i<n;i++){
      // Always evict whole 16-bit samples so byte alignment cannot be lost.
      if(size+2>CAPACITY){start=(start+2)%CAPACITY;size-=2;}
      int value=samples[i];ring[(start+size)%CAPACITY]=(byte)value;size++;
      ring[(start+size)%CAPACITY]=(byte)(value>>8);size++;
     }
    }
   }
  }catch(Throwable e){if(running){status="erro: "+e.getClass().getSimpleName();Log.e("GilaVoice","Microphone read failed",e);}}
  finally{
   synchronized(lock){running=false;if(recorder==r)recorder=null;}
   try{r.stop();}catch(Throwable ignored){}
   if(echo!=null)try{echo.release();}catch(Throwable ignored){}
   if(noise!=null)try{noise.release();}catch(Throwable ignored){}
   try{r.release();}catch(Throwable ignored){}
  }
 }
 void stop(){
  AudioRecord r;Thread t;
  synchronized(lock){running=false;status="desligado";start=0;size=0;r=recorder;t=worker;}
  if(r!=null)try{r.stop();}catch(Throwable ignored){}
  if(t!=null && t!=Thread.currentThread())try{t.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
 byte[] take(){
  synchronized(lock){
   if(!running){start=0;size=0;return new byte[0];}
   byte[] result=new byte[size];
   for(int i=0;i<size;i++)result[i]=ring[(start+i)%CAPACITY];
   start=0;size=0;return result;
  }
 }
 String state(){return status;}
}
