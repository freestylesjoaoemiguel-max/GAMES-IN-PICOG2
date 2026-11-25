package io.godot.camera;
import java.nio.ByteBuffer;
import java.util.Arrays;
public class TestNoPreview {
 public static void main(String[] args){
  int w=640,h=480;byte[] y=new byte[w*h],u=new byte[w*h/4],v=new byte[w*h/4];
  Arrays.fill(y,(byte)100);Arrays.fill(u,(byte)120);Arrays.fill(v,(byte)140);
  FrameConverter.Frame a=FrameConverter.convert(ByteBuffer.wrap(y),w,1,ByteBuffer.wrap(u),w/2,1,ByteBuffer.wrap(v),w/2,1,w,h,90,true);
  FrameConverter.Frame b=FrameConverter.convert(ByteBuffer.wrap(y),w,1,ByteBuffer.wrap(u),w/2,1,ByteBuffer.wrap(v),w/2,1,w,h,90,false);
  if(b.rgba!=null || !Arrays.equals(a.argb,b.argb))throw new AssertionError("preview off changed inference pixels");
  for(int i=0;i<500;i++){
   b=FrameConverter.convert(ByteBuffer.wrap(y),w,1,ByteBuffer.wrap(u),w/2,1,ByteBuffer.wrap(v),w/2,1,w,h,90,false);
   if(b.rgba!=null || b.argb.length!=a.argb.length)throw new AssertionError();
  }
  System.out.println("PASS preview disabled, pixels match; 500 conversions");
 }
}
