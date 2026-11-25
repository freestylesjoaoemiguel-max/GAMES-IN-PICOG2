package io.godot.camera;
import java.nio.ByteBuffer;
public class TestFrameConverter {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
 static int r(FrameConverter.Frame f,int x,int y){return f.rgba[(y*f.width+x)*4]&255;}
 public static void main(String[] args){
  ByteBuffer y=ByteBuffer.allocate(20), u=ByteBuffer.allocate(8), v=ByteBuffer.allocate(8);
  // 4x2 Y with padding. U/V have stride and non-unit pixel stride, like interleaved chroma planes.
  for(int i=0;i<8;i++){u.put(i,(byte)128);v.put(i,(byte)128);}
  y.put(1,(byte)16);y.put(3,(byte)50);y.put(5,(byte)100);y.put(7,(byte)235);
  y.put(11,(byte)235);y.put(13,(byte)100);y.put(15,(byte)50);y.put(17,(byte)16);
  y.position(1);u.position(1);v.position(1);
  FrameConverter.Frame a=FrameConverter.convert(y,10,2,u,8,2,v,8,2,4,2,0);
  check(a.width==4&&a.height==2,"dimensions");check(r(a,0,0)==0 && r(a,3,0)>=250,"stride and luminance");
  check(r(a,0,1)>=250&&r(a,3,1)==0,"row stride and buffer offsets");
  FrameConverter.Frame b=FrameConverter.convert(y,10,2,u,8,2,v,8,2,4,2,90);
  check(b.width==2&&b.height==4,"90 degree dimensions");
  for(int yy=0;yy<2;yy++)for(int x=0;x<4;x++)check(r(a,x,yy)==r(b,1-yy,x),"90 degree rotation");
  FrameConverter.Frame c=FrameConverter.convert(y,10,2,u,8,2,v,8,2,4,2,270);
  for(int yy=0;yy<2;yy++)for(int x=0;x<4;x++)check(r(a,x,yy)==r(c,yy,3-x),"270 degree rotation");
  for(int i=0;i<a.argb.length;i++)check(((a.argb[i]>>16)&255)==(a.rgba[i*4]&255),"ARGB matches preview");
  System.out.println("ALL CONVERTER TESTS PASSED");
 }
}
