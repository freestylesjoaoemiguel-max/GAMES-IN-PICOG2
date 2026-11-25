package io.godot.camera;
import java.nio.ByteBuffer;

/** YUV420 to upright RGBA and ARGB, obeying independent row/pixel strides. */
public final class FrameConverter {
 public static final class Frame {
  public final byte[] rgba; public final int[] argb; public final int width, height;
  Frame(byte[] p,int[] a,int w,int h){rgba=p;argb=a;width=w;height=h;}
 }
 private static int clamp(int x){return x<0?0:(x>255?255:x);}
 public static Frame convert(ByteBuffer y, int ys, int yp, ByteBuffer u, int us, int up, ByteBuffer v, int vs, int vp, int sw, int sh, int rotation){
  return convert(y,ys,yp,u,us,up,v,vs,vp,sw,sh,rotation,true);
 }
 public static Frame convert(ByteBuffer y, int ys, int yp, ByteBuffer u, int us, int up, ByteBuffer v, int vs, int vp, int sw, int sh, int rotation, boolean preview){
  if(sw<2 || sh<2)throw new IllegalArgumentException("Invalid image dimensions");
  float scale=Math.min(1f,Math.min(320f/sw,240f/sh));
  int w=Math.max(2,((int)(sw*scale))&~1), h=Math.max(2,((int)(sh*scale))&~1);
  int ow=(rotation==90 || rotation==270)?h:w, oh=(rotation==90 || rotation==270)?w:h;
  byte[] rgba=preview?new byte[ow*oh*4]:null;int[] argb=new int[ow*oh];
  int ybase=y.position(),ubase=u.position(),vbase=v.position();
  for(int dy=0;dy<h;dy++){
   int sy=dy*sh/h;int yrow=ybase+sy*ys, urow=ubase+(sy/2)*us, vrow=vbase+(sy/2)*vs;
   for(int dx=0;dx<w;dx++){
    int sx=dx*sw/w;
    int c=(y.get(yrow+sx*yp)&255)-16, d=(u.get(urow+(sx/2)*up)&255)-128, e=(v.get(vrow+(sx/2)*vp)&255)-128;
    int r=clamp((298*c+409*e+128)>>8), g=clamp((298*c-100*d-208*e+128)>>8), b=clamp((298*c+516*d+128)>>8);
    int ox=dx,oy=dy;
    if(rotation==90){ox=h-1-dy;oy=dx;} else if(rotation==180){ox=w-1-dx;oy=h-1-dy;} else if(rotation==270){ox=dy;oy=w-1-dx;}
    int index=oy*ow+ox, bi=index*4;
    argb[index]=0xff000000|(r<<16)|(g<<8)|b;
    if(preview){rgba[bi]=(byte)r;rgba[bi+1]=(byte)g;rgba[bi+2]=(byte)b;rgba[bi+3]=(byte)255;}
   }
  }
  return new Frame(rgba,argb,ow,oh);
 }
}
