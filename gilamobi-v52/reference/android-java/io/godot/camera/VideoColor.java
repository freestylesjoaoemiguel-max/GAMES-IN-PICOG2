package io.godot.camera;
final class VideoColor {
 static byte[] rgbaTo420(byte[] rgba,int w,int h,boolean semi) {
  int size=w*h;byte[] yuv=new byte[size+size/2];
  for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
   int pixel=(y*w+x)*4, r=rgba[pixel]&255,g=rgba[pixel+1]&255,b=rgba[pixel+2]&255;
   yuv[y*w+x]=(byte)clamp(((66*r+129*g+25*b+128)>>8)+16);
   if((x&1)==0&&(y&1)==0) {
    int sumR=0,sumG=0,sumB=0;
    for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){
     int j=((y+dy)*w+x+dx)*4;sumR+=rgba[j]&255;sumG+=rgba[j+1]&255;sumB+=rgba[j+2]&255;
    }
    int rr=sumR/4,gg=sumG/4,bb=sumB/4;
    byte u=(byte)clamp(((-38*rr-74*gg+112*bb+128)>>8)+128);
    byte v=(byte)clamp(((112*rr-94*gg-18*bb+128)>>8)+128);
    int chroma=(y/2)*(w/2)+x/2;
    if(semi){yuv[size+chroma*2]=u;yuv[size+chroma*2+1]=v;}
    else{yuv[size+chroma]=u;yuv[size+size/4+chroma]=v;}
   }
  }
  return yuv;
 }
 private static int clamp(int x){return x<0?0:(x>255?255:x);}
}
