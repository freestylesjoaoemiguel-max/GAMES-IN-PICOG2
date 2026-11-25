package io.godot.camera;
import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Android H.264 encoder. Bounded input queue; one worker owns codec after start.
final class SandboxVideoRecorder {
 private static final String MIME="video/avc";
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final AtomicBoolean framePending=new AtomicBoolean(false);
 private final SandboxVideoPublisher publisher;
 private volatile String state="parado";
 private volatile boolean accepting=false;
 private MediaCodec codec;
 private MediaMuxer muxer;
 private boolean muxerStarted=false, semi=false;
 private int track=-1,width,height;
 private long lastPts=-1;
 private int encodedSamples=0;
 private String path;
 SandboxVideoRecorder(SandboxVideoPublisher publisher){this.publisher=publisher;}
 synchronized boolean start(String outputPath,int w,int h,int fps){
  if(accepting || status().equals("finalizando") || status().equals("salvando"))return false;
  try {
   if(w<=0||h<=0||(w%16)!=0||(h%16)!=0)throw new IllegalArgumentException("tamanho incompatível");
   width=w;height=h;path=outputPath;lastPts=-1;track=-1;encodedSamples=0;muxerStarted=false;
   codec=chooseCodec(w,h,fps);
   muxer=new MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
   codec.start();accepting=true;state="gravando";return true;
  } catch(Throwable e){Log.e("GilaVideo","start",e);release();new File(outputPath).delete();state="erro: não foi possível iniciar vídeo";return false;}
 }
 private MediaCodec chooseCodec(int w,int h,int fps)throws Exception{
  for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
   if(!info.isEncoder())continue;
   boolean supports=false;for(String t:info.getSupportedTypes())if(MIME.equalsIgnoreCase(t))supports=true;
   if(!supports)continue;
   MediaCodecInfo.CodecCapabilities caps;
   try{caps=info.getCapabilitiesForType(MIME);}catch(Throwable e){continue;}
   for(int wanted:new int[]{MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar}){
    boolean found=false;for(int f:caps.colorFormats)if(f==wanted)found=true;
    if(!found)continue;
    MediaCodec c=null;
    try{
     MediaFormat fmt=MediaFormat.createVideoFormat(MIME,w,h);
     fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,wanted);fmt.setInteger(MediaFormat.KEY_BIT_RATE,1200000);
     fmt.setInteger(MediaFormat.KEY_FRAME_RATE,fps);fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
     c=MediaCodec.createByCodecName(info.getName());c.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
     semi=wanted==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;return c;
    }catch(Throwable e){if(c!=null)try{c.release();}catch(Throwable ignored){}}
   }
  }
  throw new IllegalStateException("codec AVC YUV não disponível");
 }
 void submit(byte[] rgba,long ptsUs){
  if(!accepting||rgba==null||rgba.length!=width*height*4||!framePending.compareAndSet(false,true))return;
  worker.execute(()->{
   try{
    int index=codec.dequeueInputBuffer(10000);
    if(index>=0){
     ByteBuffer b=codec.getInputBuffer(index);if(b==null)throw new IllegalStateException("buffer de entrada");
     byte[] yuv=VideoColor.rgbaTo420(rgba,width,height,semi);b.clear();b.put(yuv);
     long t=Math.max(lastPts+1,ptsUs);lastPts=t;codec.queueInputBuffer(index,0,yuv.length,t,0);
    }
    drain(false);
   }catch(Throwable e){Log.e("GilaVideo","encode",e);accepting=false;state="erro: falha ao gravar vídeo";release();}
   finally{framePending.set(false);}
  });
 }
 synchronized void stop(Activity activity){
  if(!accepting)return;
  accepting=false;state="finalizando";
  worker.execute(()->{
   try{
    int i=-1;long limit=System.currentTimeMillis()+5000;
    while(i<0&&System.currentTimeMillis()<limit){i=codec.dequeueInputBuffer(10000);if(i<0)drain(false);}
    if(i<0)throw new IllegalStateException("encoder sem entrada");
    codec.queueInputBuffer(i,0,0,Math.max(0,lastPts+1),MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    drain(true);
    if(encodedSamples<1||!muxerStarted)throw new IllegalStateException("sem quadros");
    muxer.stop();muxerStarted=false;release();publisher.publish(activity,path);state="salvando";
   }catch(Throwable e){Log.e("GilaVideo","stop",e);release();state="erro: vídeo não foi finalizado";}
  });
 }
 private void drain(boolean eos){
  MediaCodec.BufferInfo bi=new MediaCodec.BufferInfo();long limit=System.currentTimeMillis()+6000;
  while(true){
   int n=codec.dequeueOutputBuffer(bi,eos?10000:0);
   if(n==MediaCodec.INFO_TRY_AGAIN_LATER){if(!eos)return;if(System.currentTimeMillis()>limit)throw new IllegalStateException("encoder demorou para finalizar");continue;}
   if(n==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(muxerStarted)throw new IllegalStateException("formato duplicado");track=muxer.addTrack(codec.getOutputFormat());muxer.start();muxerStarted=true;continue;}
   if(n<0)continue;
   ByteBuffer data=codec.getOutputBuffer(n);
   if((bi.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)bi.size=0;
   if(bi.size>0){if(!muxerStarted||data==null)throw new IllegalStateException("sem formato de saída");data.position(bi.offset);data.limit(bi.offset+bi.size);muxer.writeSampleData(track,data,bi);encodedSamples++;}
   boolean finished=(bi.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
   codec.releaseOutputBuffer(n,false);
   if(finished)return;
  }
 }
 private void release(){
  if(codec!=null){try{codec.stop();}catch(Throwable ignored){}try{codec.release();}catch(Throwable ignored){}codec=null;}
  if(muxer!=null){if(muxerStarted)try{muxer.stop();}catch(Throwable ignored){}try{muxer.release();}catch(Throwable ignored){}muxer=null;}
  muxerStarted=false;
 }
 synchronized boolean retryPublish(Activity activity){
  if(accepting || status().equals("finalizando") || status().equals("salvando") || path==null || !new File(path).isFile())return false;
  publisher.publish(activity,path);state="salvando";return true;
 }
 String status(){
  if(state.equals("salvando")){
   String s=publisher.status();
   if(!s.equals("salvando")){state=s;return s;}
  }
  return state;
 }
}
