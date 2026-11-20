package com.picog2.discordvrg2;

import android.graphics.*;
import android.opengl.*;
import android.opengl.Matrix;
import android.view.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Native GLES2 scene. This is not a WebView and not a substitute for the PICO compositor. */
final class SpatialView extends GLSurfaceView implements GLSurfaceView.Renderer {
 final MainActivity app;
 volatile float yaw,pitch,cursorX=.5f,cursorY=.5f;
 final ArrayList<Surface> surfaces=new ArrayList<>();
 ArrayList<Hit> buttons=new ArrayList<>();
 int width=1280,height=720,program,panelTexture,keyTexture,whiteTexture;
 int posLoc,uvLoc,matrixLoc,colorLoc,scaleLoc,centerLoc;
 final float[] projection=new float[16],view=new float[16],vp=new float[16],centerVp=new float[16],inverse=new float[16];
 Surface highlighted;long pressedUntil;boolean stereo;long lastFrame;
 int sceneSize=1,sceneDistance=1,sceneCurve=2,sceneQuality=1,scenePanelCurve=1;
 float sceneKeyScale=1;boolean sceneKeyboard,sceneCinema;
 float downX,downY;boolean moved;
 static final String[][] ROWS={"Q W E R T Y U I O P".split(" "),"A S D F G H J K L".split(" "),"Z X C V B N M".split(" "),"0 1 2 3 4 5 6 7 8 9".split(" "),{"SHIFT","@",".","ESPAÇO","⌫","ENTER"},{"https://",":","/","-","_"}};
 static final class Hit {float x,y,w,h;String action;Hit(float x,float y,float w,float h,String a){this.x=x;this.y=y;this.w=w;this.h=h;action=a;}}
 static final class Surface {float[] data;FloatBuffer buffer;String key;boolean panel;float cx,cy,cz;Surface(float[] v,String key,boolean panel){data=v;this.key=key;this.panel=panel;buffer=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(v).position(0);for(int i=0;i<v.length;i+=5){cx+=v[i];cy+=v[i+1];cz+=v[i+2];}cx/=v.length/5;cy/=v.length/5;cz/=v.length/5;}}
 static final class Pick {Surface surface;float distance=Float.MAX_VALUE,u,v,x,y,z;}
 SpatialView(MainActivity app){super(app);this.app=app;setEGLContextClientVersion(2);setPreserveEGLContextOnPause(true);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);setFocusableInTouchMode(true);requestFocus();}
 void invalidateScene(){app.runOnUiThread(()->{
  if(app.isFinishing())return;
  final NativePanel.Frame frame=NativePanel.paint(app);
  final int sz=app.size,dist=app.distance,cur=app.curve,q=app.quality,pc=app.panelCurve;final float ks=app.keyboardScale;final boolean kb=app.keyboard,st=app.stereo,cin=app.cinema;
  queueEvent(()->{if(program==0){frame.bitmap.recycle();return;}sceneSize=sz;sceneDistance=dist;sceneCurve=cur;sceneQuality=q;scenePanelCurve=pc;sceneKeyScale=ks;sceneKeyboard=kb;stereo=st;sceneCinema=cin;buttons=frame.hits;GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,panelTexture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,frame.bitmap,0);frame.bitmap.recycle();rebuild();});requestRender();
 });}
 @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
  String vert="uniform mat4 uMatrix; uniform vec3 uCenter; uniform float uScale; attribute vec3 aPosition; attribute vec2 aUV; varying vec2 vUV; void main(){ vUV=aUV; gl_Position=uMatrix*vec4((aPosition-uCenter)*uScale+uCenter,1.0); }";
  String frag="precision mediump float; uniform sampler2D uTexture; uniform vec4 uColor; varying vec2 vUV; void main(){gl_FragColor=texture2D(uTexture,vUV)*uColor;}";
  program=GLES20.glCreateProgram();int vertex=shader(GLES20.GL_VERTEX_SHADER,vert),fragment=shader(GLES20.GL_FRAGMENT_SHADER,frag);GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
  posLoc=GLES20.glGetAttribLocation(program,"aPosition");uvLoc=GLES20.glGetAttribLocation(program,"aUV");matrixLoc=GLES20.glGetUniformLocation(program,"uMatrix");colorLoc=GLES20.glGetUniformLocation(program,"uColor");centerLoc=GLES20.glGetUniformLocation(program,"uCenter");scaleLoc=GLES20.glGetUniformLocation(program,"uScale");
  int[] textures=new int[3];GLES20.glGenTextures(3,textures,0);panelTexture=textures[0];keyTexture=textures[1];whiteTexture=textures[2];
  for(int texture:textures){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);}
  Bitmap white=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);white.eraseColor(Color.WHITE);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,white,0);white.recycle();
  Bitmap atlas=Bitmap.createBitmap(1024,640,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(atlas);Paint paint=new Paint(3);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);int i=0;
  for(String[] row:ROWS)for(String key:row){float x=(i%10)*102.4f,y=(i/10)*106.6667f;paint.setColor(Color.rgb(48,52,76));c.drawRoundRect(x+4,y+7,x+98,y+98,11,11,paint);paint.setColor(Color.rgb(225,213,252));paint.setTextSize(key.length()>2?17:30);c.drawText(key,x+51,y+62,paint);i++;}
  GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,keyTexture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,atlas,0);atlas.recycle();
  GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glEnable(GLES20.GL_DEPTH_TEST);invalidateScene();
 }
 int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] status=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
 @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;}
 void camera(float eye,float aspect,float[] result){Matrix.perspectiveM(projection,0,75,aspect,.05f,25);Matrix.setIdentityM(view,0);Matrix.translateM(view,0,-eye,0,0);Matrix.rotateM(view,0,-pitch,1,0,0);Matrix.rotateM(view,0,-yaw,0,1,0);Matrix.multiplyMM(result,0,projection,0,view,0);}
 @Override public void onDrawFrame(GL10 gl){
  GLES20.glClearColor(sceneCinema?.015f:.035f,sceneCinema?.018f:.045f,sceneCinema?.03f:.08f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);
  float aspect=(stereo?width*.5f:width)/(float)Math.max(1,height);camera(0,aspect,centerVp);Pick pick=pick(cursorX,cursorY);highlighted=pick==null?null:pick.surface;
  int eyes=stereo?2:1;
  for(int eye=0;eye<eyes;eye++){GLES20.glViewport(stereo?eye*width/2:0,0,stereo?width/2:width,height);camera(stereo?(eye==0?-.032f:.032f):0,aspect,vp);GLES20.glUniformMatrix4fv(matrixLoc,1,false,vp,0);
   for(Surface s:surfaces){float scale=s==highlighted&&System.currentTimeMillis()<pressedUntil?.94f:1;draw(s,s.panel?panelTexture:keyTexture,s==highlighted&&!s.panel?1.22f:1,scale,GLES20.GL_TRIANGLES);}
   if(pick!=null){float x=pick.x,y=pick.y,z=pick.z+.012f;Surface cursor=quad(x,y,z,.016f,.016f,0,0,0,1,1,"cursor");GLES20.glDisable(GLES20.GL_DEPTH_TEST);draw(cursor,whiteTexture,1,1,GLES20.GL_TRIANGLES);Surface line=new Surface(new float[]{.18f,-.18f,-.12f,0,0,x,y,z,1,1},"laser",false);draw(line,whiteTexture,.6f,1,GLES20.GL_LINES);GLES20.glEnable(GLES20.GL_DEPTH_TEST);}
  }
  if(System.currentTimeMillis()<pressedUntil)postDelayed(this::requestRender,20);
 }
 void draw(Surface s,int texture,float tint,float scale,int mode){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform4f(colorLoc,tint,tint,tint,1);GLES20.glUniform3f(centerLoc,s.cx,s.cy,s.cz);GLES20.glUniform1f(scaleLoc,scale);s.buffer.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,20,s.buffer);s.buffer.position(3);GLES20.glVertexAttribPointer(uvLoc,2,GLES20.GL_FLOAT,false,20,s.buffer);GLES20.glEnableVertexAttribArray(posLoc);GLES20.glEnableVertexAttribArray(uvLoc);GLES20.glDrawArrays(mode,0,s.data.length/5);}
 void rebuild(){
  surfaces.clear();highlighted=null;
  float w=3.4f*new float[]{.8f,1,1.25f,1.6f}[sceneSize],h=w*.625f,z=-new float[]{2.8f,3.4f,4.1f}[sceneDistance],cy=sceneKeyboard?.46f:.05f,bend=new float[]{0,.25f,.5f,.75f}[scenePanelCurve];
  int segments=24;float[] points=new float[segments*6*5];int at=0;float radius=bend==0?1000:w/bend;
  for(int i=0;i<segments;i++){float u1=i/(float)segments,u2=(i+1f)/segments,a1=(u1-.5f)*w/radius,a2=(u2-.5f)*w/radius;float x1=(float)Math.sin(a1)*radius,x2=(float)Math.sin(a2)*radius,z1=z+radius*(1-(float)Math.cos(a1)),z2=z+radius*(1-(float)Math.cos(a2));float[] vertices={x1,cy+h/2,z1,u1,0,x1,cy-h/2,z1,u1,1,x2,cy+h/2,z2,u2,0,x2,cy+h/2,z2,u2,0,x1,cy-h/2,z1,u1,1,x2,cy-h/2,z2,u2,1};System.arraycopy(vertices,0,points,at,30);at+=30;}
  surfaces.add(new Surface(points,"panel",true));
  if(!sceneKeyboard)return;int index=0;
  for(int row=0;row<ROWS.length;row++)for(int column=0;column<ROWS[row].length;column++){
   boolean wide=row>=4;float x=(column-(ROWS[row].length-1)/2f)*(wide?.25f:.157f)*sceneKeyScale,r=new float[]{100,4,2.2f,1.45f}[sceneCurve],angle=x/r;
   float xx=r*(float)Math.sin(angle),yy=-.43f-row*.113f*sceneKeyScale,zz=-1.8f+r*(1-(float)Math.cos(angle));
   float u=(index%10)/10f,v=(index/10)/6f;
   surfaces.add(quad(xx,yy,zz,(wide?.242f:.153f)*sceneKeyScale,.116f*sceneKeyScale,-angle,u,v,u+.1f,v+1/6f,ROWS[row][column]));index++;
  }
 }
 static Surface quad(float cx,float cy,float cz,float w,float h,float angle,float u1,float v1,float u2,float v2,String key){float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle),dx=w*.5f*cos,dz=-w*.5f*sin;return new Surface(new float[]{cx-dx,cy+h/2,cz-dz,u1,v1,cx-dx,cy-h/2,cz-dz,u1,v2,cx+dx,cy+h/2,cz+dz,u2,v1,cx+dx,cy+h/2,cz+dz,u2,v1,cx-dx,cy-h/2,cz-dz,u1,v2,cx+dx,cy-h/2,cz+dz,u2,v2},key,false);}
 Pick pick(float x,float y){
  if(!Matrix.invertM(inverse,0,centerVp,0))return null;float[] near={x*2-1,1-y*2,-1,1},far={x*2-1,1-y*2,1,1},a=new float[4],b=new float[4];Matrix.multiplyMV(a,0,inverse,0,near,0);Matrix.multiplyMV(b,0,inverse,0,far,0);for(int i=0;i<3;i++){a[i]/=a[3];b[i]/=b[3];}float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len;Pick result=new Pick();
  for(Surface surface:surfaces){float[] v=surface.data;for(int n=0;n<v.length;n+=15){float e1x=v[n+5]-v[n],e1y=v[n+6]-v[n+1],e1z=v[n+7]-v[n+2],e2x=v[n+10]-v[n],e2y=v[n+11]-v[n+1],e2z=v[n+12]-v[n+2];float px=dy*e2z-dz*e2y,py=dz*e2x-dx*e2z,pz=dx*e2y-dy*e2x,det=e1x*px+e1y*py+e1z*pz;if(Math.abs(det)<.00001f)continue;float inv=1/det,tx=a[0]-v[n],ty=a[1]-v[n+1],tz=a[2]-v[n+2],u=(tx*px+ty*py+tz*pz)*inv;if(u<0||u>1)continue;float qx=ty*e1z-tz*e1y,qy=tz*e1x-tx*e1z,qz=tx*e1y-ty*e1x,w=(dx*qx+dy*qy+dz*qz)*inv;if(w<0||u+w>1)continue;float t=(e2x*qx+e2y*qy+e2z*qz)*inv;if(t<0||t>=result.distance)continue;result.surface=surface;result.distance=t;result.u=v[n+3]*(1-u-w)+v[n+8]*u+v[n+13]*w;result.v=v[n+4]*(1-u-w)+v[n+9]*u+v[n+14]*w;result.x=a[0]+dx*t;result.y=a[1]+dy*t;result.z=a[2]+dz*t;}}
  return result.surface==null?null:result;
 }
 void moveCursor(float x,float y){cursorX=Math.max(.02f,Math.min(.98f,cursorX+x*app.cursorSpeed));cursorY=Math.max(.02f,Math.min(.98f,cursorY+y*app.cursorSpeed*(app.invert?-1:1)));requestRender();}
 void click(){queueEvent(()->{Pick p=pick(cursorX,cursorY);if(p==null)return;pressedUntil=System.currentTimeMillis()+140;String action=null;if(p.surface.panel){float x=p.u*1280,y=p.v*800;for(int i=buttons.size()-1;i>=0;i--){Hit h=buttons.get(i);if(x>=h.x&&x<=h.x+h.w&&y>=h.y&&y<=h.y+h.h){action=h.action;break;}}}else action="key:"+p.surface.key;final String selected=action;if(selected!=null)app.runOnUiThread(()->app.action(selected));});requestRender();}
 @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();if(stereo)x=x%(width/2);cursorX=x/(stereo?width/2f:width);cursorY=y/height;if(e.getAction()==MotionEvent.ACTION_DOWN){downX=x;downY=y;moved=false;}else if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(x-downX)+Math.abs(y-downY)>15)moved=true;if(e.getPointerCount()>1&&!app.motion){yaw+=(x-downX)*.08f*app.sensitivity;pitch=Math.max(-60,Math.min(60,pitch+(y-downY)*.08f*app.sensitivity*(app.invert?-1:1)));downX=x;downY=y;}}else if(e.getAction()==MotionEvent.ACTION_UP&&!moved)click();requestRender();return true;}
}
