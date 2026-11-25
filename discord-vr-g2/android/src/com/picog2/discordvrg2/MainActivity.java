package com.picog2.discordvrg2;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.media.*;
import android.net.Uri;
import android.view.*;
import org.json.*;
import java.util.*;

public final class MainActivity extends Activity implements SensorEventListener {
 SpatialView view;
 final Api api=new Api();
 final Handler ui=new Handler(Looper.getMainLooper());
 final ArrayList<Message> localMessages=new ArrayList<>(),remoteMessages=new ArrayList<>();
 final ArrayList<String> guildNames=new ArrayList<>(),guildIds=new ArrayList<>();
 String page="chat",channel="geral",draft="",searchQuery="",serverInput="",user="Visitante",status="Modo local: mensagens não são enviadas ao Discord.";
 String postingChannel="",postingGuild="";
 boolean keyboard=false,shift=false,connected=false,remote=false,sending=false,stereo=false,motion=false,invert=false,cinema=false;
 int size=1,distance=1,curve=2,quality=1,panelCurve=1,selectedGuild=-1,guildOffset=0,scroll=0;
 float keyboardScale=1,sensitivity=1,cursorSpeed=1;
 volatile int microphoneLevel;
 volatile boolean microphone=false;
 volatile int microphoneGeneration;
 AudioRecord recorder;
 SensorManager sensors;Sensor rotationSensor;
 float baseYaw,basePitch,currentYaw,currentPitch;boolean centered=false;
 final float[] rotation=new float[16],remapped=new float[16],angles=new float[3];
 int pairGeneration;
 static final class Message {String author,text;Message(String a,String t){author=a;text=t;}}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
  readSettings();sensors=(SensorManager)getSystemService(SENSOR_SERVICE);rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);if(rotationSensor==null)rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
  localMessages.add(new Message("Guia do espaço","Bem-vindo ao Discord VR G2. Esta é uma interface nativa independente, sem WebView."));
  localMessages.add(new Message("Sistema local","Teclado 3D curvado pronto. As mensagens locais não são publicadas. Controle PICO com pose nativa ainda não está integrado."));
  try{JSONArray savedMessages=new JSONArray(getPreferences(0).getString("localMessages","[]"));for(int i=0;i<Math.min(savedMessages.length(),60);i++){JSONObject m=savedMessages.getJSONObject(i);localMessages.add(new Message("Você",m.getString("text")));}}catch(Exception ignored){}
  view=new SpatialView(this);setContentView(view);
 }
 void readSettings(){android.content.SharedPreferences p=getPreferences(0);size=clamp(p.getInt("size",1),0,3);distance=clamp(p.getInt("distance",1),0,2);curve=clamp(p.getInt("curve",2),0,3);quality=clamp(p.getInt("quality",1),0,2);panelCurve=clamp(p.getInt("panelCurve",1),0,3);keyboardScale=bounded(p.getFloat("keyboardScale",1));sensitivity=bounded(p.getFloat("sensitivity",1));cursorSpeed=bounded(p.getFloat("cursorSpeed",1));cinema=p.getBoolean("cinema",false);invert=p.getBoolean("invert",false);serverInput=p.getString("server","");try{api.origin=Api.validateOrigin(serverInput);}catch(Exception ignored){api.origin="";}}
 static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
 static float bounded(float n){return Float.isNaN(n)?1:Math.max(.6f,Math.min(1.6f,n));}
 void persist(){getPreferences(0).edit().putInt("size",size).putInt("distance",distance).putInt("curve",curve).putInt("quality",quality).putInt("panelCurve",panelCurve).putFloat("keyboardScale",keyboardScale).putFloat("sensitivity",sensitivity).putFloat("cursorSpeed",cursorSpeed).putBoolean("cinema",cinema).putBoolean("invert",invert).putString("server",serverInput).apply();}
 void changed(){if(view!=null)view.invalidateScene();}
 void notifyStatus(String value){status=value;changed();}
 void action(String action){
  if(action.startsWith("key:")){type(action.substring(4));return;}
  if(action.startsWith("channel:")){channel=action.substring(8);remote=false;scroll=0;page="chat";}
  else if(action.startsWith("guild:")){selectedGuild=Integer.parseInt(action.substring(6));remote=true;channel=postingGuild.equals(guildIds.get(selectedGuild))?"canal autorizado":"acesso limitado";page="chat";scroll=0;}
  else switch(action){
   case "local":remote=false;selectedGuild=-1;channel="geral";page="chat";scroll=0;break;
   case "settings":page="settings";keyboard=false;break;
   case "connect":page="connect";keyboard=false;break;
   case "draft":keyboard=true;break;
   case "keyboard":keyboard=!keyboard;break;
   case "back":back();return;
   case "send":send();return;
   case "login":connect(false);return;
   case "loginpost":connect(true);return;
   case "serverinput":keyboard=true;break;
   case "saveServer":try{changeServer();persist();keyboard=false;status="Servidor salvo. A autorização usa o navegador externo.";}catch(Exception e){status=e.getMessage();}break;
   case "logout":logout();return;
   case "mic":testMicrophone();return;
   case "friends":case "direct":case "voice":case "notifications":page="limits";keyboard=false;break;
   case "recenter":baseYaw=currentYaw;basePitch=currentPitch;centered=true;view.yaw=view.pitch=0;status="Vista recentrada. Pose do controle PICO não está disponível.";break;
   case "size":size=(size+1)%4;break;
   case "distance":distance=(distance+1)%3;break;
   case "curve":curve=(curve+1)%4;break;
   case "panelCurve":panelCurve=(panelCurve+1)%4;break;
   case "keyboardScale":keyboardScale=keyboardScale>=1.59f?.6f:keyboardScale+.2f;break;
   case "sensitivity":sensitivity=sensitivity>=1.59f?.6f:sensitivity+.2f;break;
   case "cursorSpeed":cursorSpeed=cursorSpeed>=1.59f?.6f:cursorSpeed+.2f;break;
   case "invert":invert=!invert;break;
   case "cinema":cinema=!cinema;break;
   case "quality":quality=(quality+1)%3;break;
   case "giant":size=3;break;
   case "motion":if(rotationSensor==null){status="Sensor de orientação indisponível.";}else{motion=!motion;if(motion)sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);else sensors.unregisterListener(this);centered=false;view.yaw=view.pitch=0;status=motion?"Orientação pelo sensor Android: experimental, não PICO SDK.":"Orientação por sensor desligada.";}break;
   case "stereo":if(stereo){stereo=false;status="Modo Android monoscópico.";}else{new AlertDialog.Builder(this).setTitle("Estéreo experimental — não calibrado").setMessage("Não usa compositor, correção de lentes, timewarp ou pose de controle PICO. Não equivale a VR PICO validado. Interrompa se houver desconforto. Deseja apenas testar duas vistas?").setNegativeButton("Cancelar",null).setPositiveButton("Testar",(d,w)->{stereo=true;notifyStatus("ESTÉREO EXPERIMENTAL · sem runtime/calibração PICO");}).show();}break;
   case "guildnext":guildOffset=Math.min(Math.max(0,guildNames.size()-3),guildOffset+3);break;
   case "guildprev":guildOffset=Math.max(0,guildOffset-3);break;
   case "up":scroll=Math.min(60,scroll+1);break;
   case "down":scroll=Math.max(0,scroll-1);break;
   case "emoji":type("✨");return;
   case "search":page="search";keyboard=true;break;
   case "clearsearch":searchQuery="";scroll=0;break;
   case "clear":localMessages.clear();getPreferences(0).edit().remove("localMessages").apply();status="Histórico local apagado.";break;
   default:status="Recurso não disponível nesta integração.";
  }
  persist();changed();
 }
 void back(){if(keyboard)keyboard=false;else if(!page.equals("chat"))page="chat";else page="settings";changed();}
 void type(String key){
  if(!page.equals("chat")&&!page.equals("connect")&&!page.equals("search"))return;
  if(key.equals("SHIFT")){shift=!shift;changed();return;}
  if(key.equals("ENTER")){if(page.equals("connect"))action("saveServer");else if(page.equals("search")){page="chat";keyboard=false;notifyStatus("Busca local: "+searchQuery);}else send();return;}
  String text=page.equals("connect")?serverInput:page.equals("search")?searchQuery:draft;
  if(key.equals("⌫")){if(!text.isEmpty())text=text.substring(0,text.offsetByCodePoints(text.length(),-1));}
  else if(key.equals("ESPAÇO"))text+=" ";else text+=shift?key:key.toLowerCase(java.util.Locale.ROOT);
  if(text.length()>1800){text=text.substring(0,1800);if(Character.isHighSurrogate(text.charAt(text.length()-1)))text=text.substring(0,text.length()-1);}
  if(page.equals("connect"))serverInput=text;else if(page.equals("search"))searchQuery=text;else draft=text;changed();
 }
 void changeServer() throws Exception {String next=Api.validateOrigin(serverInput);if(!api.origin.equals(next)){pairGeneration++;if(connected)logout();}api.origin=next;serverInput=next;}
 void connect(boolean post){
  try{changeServer();persist();}catch(Exception e){notifyStatus(e.getMessage());return;}
  status="Preparando autorização oficial…";changed();final int generation=++pairGeneration;
  JSONObject body=new JSONObject();try{body.put("post",post);}catch(Exception ignored){}
  api.request("POST","/api/pair",body,null,(result,error)->{
   if(generation!=pairGeneration)return;
   if(error!=null){notifyStatus(error);return;}
   String url=result.optString("url"),id=result.optString("id"),poll=result.optString("poll");
   if(!url.startsWith(api.origin+"/auth/start?pair=")||!id.matches("[A-Za-z0-9_-]{30,80}")||poll.length()<30){notifyStatus("Resposta de pareamento inválida.");return;}
   try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));status="Autorize no navegador e volte ao aplicativo.";pollPair(id,poll,generation,System.currentTimeMillis()+300000);changed();}catch(Exception e){notifyStatus("Nenhum navegador disponível. Autorize usando um dispositivo com navegador compatível.");}
  });
 }
 void pollPair(String id,String poll,int generation,long end){
  if(generation!=pairGeneration||isFinishing())return;
  if(System.currentTimeMillis()>end){notifyStatus("Login expirou. Tente novamente.");return;}
  ui.postDelayed(()->{if(generation!=pairGeneration)return;api.request("GET","/api/pair/"+id,null,poll,(data,error)->{
   if(generation!=pairGeneration)return;
   if(error!=null){notifyStatus(error);return;}
   if(data.optBoolean("pending")){pollPair(id,poll,generation,end);return;}
   api.session=data.optString("token");JSONObject profile=data.optJSONObject("user");if(api.session.isEmpty()||profile==null){notifyStatus("Autorização incompleta.");return;}
   user=profile.optString("name","Usuário");connected=true;guildOffset=0;selectedGuild=-1;remote=false;guildNames.clear();guildIds.clear();
   JSONArray guilds=data.optJSONArray("guilds");if(guilds!=null)for(int i=0;i<guilds.length();i++){JSONObject g=guilds.optJSONObject(i);if(g!=null){guildNames.add(g.optString("name"));guildIds.add(g.optString("id"));}}
   JSONObject posting=data.optJSONObject("posting");postingChannel=posting==null?"":posting.optString("channel_id");postingGuild=posting==null?"":posting.optString("guild_id");
   remoteMessages.clear();JSONArray history=data.optJSONArray("history");if(history!=null)for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m!=null)remoteMessages.add(new Message(m.optString("author"),m.optString("content")));}
   page="chat";keyboard=false;notifyStatus("Conectado via OAuth2. Mensagens reais somente no canal expressamente autorizado.");
  });},1500);
 }
 void send(){
  final String content=draft.trim();if(content.isEmpty()||sending)return;
  if(!remote){localMessages.add(new Message("Você",content));while(localMessages.size()>60)localMessages.remove(0);JSONArray saved=new JSONArray();for(Message m:localMessages)if(m.author.equals("Você")){JSONObject o=new JSONObject();try{o.put("text",m.text);saved.put(o);}catch(Exception ignored){}}getPreferences(0).edit().putString("localMessages",saved.toString()).apply();draft="";scroll=0;notifyStatus("Mensagem salva localmente. Não foi publicada no Discord.");return;}
  if(!connected||postingChannel.isEmpty()||selectedGuild<0||!postingGuild.equals(guildIds.get(selectedGuild))){notifyStatus("Este canal não possui webhook autorizado. Nenhuma mensagem foi enviada.");return;}
  sending=true;final int generation=pairGeneration;JSONObject body=new JSONObject();try{body.put("content",content);body.put("requestId",UUID.randomUUID().toString());body.put("channelId",postingChannel);}catch(Exception ignored){}
  notifyStatus("Enviando como Discord VR G2 · integração…");
  api.request("POST","/api/messages",body,api.session,(data,error)->{if(generation!=pairGeneration)return;sending=false;if(error!=null){notifyStatus(error);return;}remoteMessages.add(new Message(data.optString("author",user),data.optString("content",content)));while(remoteMessages.size()>80)remoteMessages.remove(0);if(draft.trim().equals(content))draft="";scroll=0;notifyStatus("Mensagem publicada pela integração autorizada, não como cliente oficial.");});
 }
 void logout(){pairGeneration++;sending=false;guildOffset=0;String token=api.session;api.session="";connected=false;remote=false;postingChannel=postingGuild="";guildNames.clear();guildIds.clear();remoteMessages.clear();user="Visitante";selectedGuild=-1;stopMicrophone();notifyStatus("Sessão local encerrada. Revogue a autorização no Discord se necessário.");if(!token.isEmpty())api.request("POST","/api/logout",new JSONObject(),token,(data,error)->notifyStatus(error==null?"Logout concluído. Webhook pode permanecer: remova em Integrações do servidor.":"Sessão local encerrada; falhou revogação remota. Revogue no Discord."));}
 void testMicrophone(){if(microphone){stopMicrophone();notifyStatus("Teste do microfone encerrado.");return;}new AlertDialog.Builder(this).setTitle("Teste local de microfone").setMessage("Mostra apenas o nível do microfone. Não grava, não transmite e não entra em canal de voz.").setNegativeButton("Cancelar",null).setPositiveButton("Testar",(d,w)->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);else startMicrophone();}).show();}
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==7&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startMicrophone();else notifyStatus("Microfone não autorizado.");}
 void startMicrophone(){
  if(microphone)return;int bytes=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(bytes<=0){notifyStatus("Microfone indisponível.");return;}
  try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(bytes,4096));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new Exception();recorder.startRecording();microphone=true;final int generation=++microphoneGeneration;final AudioRecord audio=recorder;new Thread(()->{short[] samples=new short[1024];long last=0;try{while(microphone&&generation==microphoneGeneration){int n=audio.read(samples,0,samples.length);if(n<=0||!microphone||generation!=microphoneGeneration)break;double sum=0;for(int i=0;i<n;i++)sum+=(double)samples[i]*samples[i];microphoneLevel=(int)Math.min(100,Math.sqrt(sum/n)/120);if(System.currentTimeMillis()-last>250){last=System.currentTimeMillis();ui.post(()->{if(microphone&&generation==microphoneGeneration)notifyStatus("Microfone LOCAL: "+microphoneLevel+"% · clique MIC para parar. Nenhuma transmissão.");});}}}catch(Exception ignored){}finally{try{audio.stop();}catch(Exception ignored){}audio.release();ui.post(()->{if(generation==microphoneGeneration){microphone=false;recorder=null;microphoneLevel=0;notifyStatus("Teste local do microfone encerrado.");}});}},"Local microphone meter").start();notifyStatus("Teste LOCAL do microfone ativo.");}catch(Exception e){if(recorder!=null)recorder.release();recorder=null;microphone=false;notifyStatus("Não foi possível iniciar o microfone.");}
 }
 void stopMicrophone(){microphone=false;microphoneGeneration++;microphoneLevel=0;recorder=null;}
 @Override protected void onResume(){super.onResume();if(view!=null)view.onResume();if(motion&&rotationSensor!=null)sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);}
 @Override protected void onPause(){stopMicrophone();sensors.unregisterListener(this);if(view!=null)view.onPause();super.onPause();}
 @Override protected void onDestroy(){pairGeneration++;ui.removeCallbacksAndMessages(null);stopMicrophone();api.close();super.onDestroy();}
 @Override public void onSensorChanged(SensorEvent e){if(!motion)return;SensorManager.getRotationMatrixFromVector(rotation,e.values);SensorManager.remapCoordinateSystem(rotation,SensorManager.AXIS_Y,SensorManager.AXIS_MINUS_X,remapped);SensorManager.getOrientation(remapped,angles);currentYaw=(float)Math.toDegrees(angles[0]);currentPitch=(float)Math.toDegrees(angles[1]);if(!centered){baseYaw=currentYaw;basePitch=currentPitch;centered=true;}float delta=currentYaw-baseYaw;while(delta>180)delta-=360;while(delta<-180)delta+=360;view.yaw=-delta;view.pitch=Math.max(-65,Math.min(65,currentPitch-basePitch));view.requestRender();}
 @Override public void onAccuracyChanged(Sensor s,int a){}
 @Override public boolean onKeyDown(int code,KeyEvent event){switch(code){case KeyEvent.KEYCODE_BACK:back();return true;case KeyEvent.KEYCODE_MENU:action("settings");return true;case KeyEvent.KEYCODE_DPAD_LEFT:view.moveCursor(-.06f,0);return true;case KeyEvent.KEYCODE_DPAD_RIGHT:view.moveCursor(.06f,0);return true;case KeyEvent.KEYCODE_DPAD_UP:view.moveCursor(0,-.06f);return true;case KeyEvent.KEYCODE_DPAD_DOWN:view.moveCursor(0,.06f);return true;case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_BUTTON_A:case KeyEvent.KEYCODE_BUTTON_R1:case KeyEvent.KEYCODE_BUTTON_R2:view.click();return true;case KeyEvent.KEYCODE_ENTER:type("ENTER");return true;case KeyEvent.KEYCODE_DEL:type("⌫");return true;default:int c=event.getUnicodeChar();if(c>0&&!Character.isISOControl(c)){type(new String(Character.toChars(c)));return true;}}return super.onKeyDown(code,event);}
 @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_SCROLL){action(event.getAxisValue(MotionEvent.AXIS_VSCROLL)>0?"up":"down");return true;}if((event.getSource()&InputDevice.SOURCE_CLASS_JOYSTICK)!=0){float x=event.getAxisValue(MotionEvent.AXIS_X)+event.getAxisValue(MotionEvent.AXIS_HAT_X),y=event.getAxisValue(MotionEvent.AXIS_Y)+event.getAxisValue(MotionEvent.AXIS_HAT_Y);if(Math.abs(x)>.2||Math.abs(y)>.2)view.moveCursor(x*.025f,y*.025f);return true;}return super.onGenericMotionEvent(event);}
}
