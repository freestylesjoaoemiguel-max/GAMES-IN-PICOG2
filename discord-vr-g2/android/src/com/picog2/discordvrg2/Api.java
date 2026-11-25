package com.picog2.discordvrg2;

import org.json.JSONObject;
import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import android.os.Handler;
import android.os.Looper;

/** The APK never receives a Discord password, client secret, webhook token or user OAuth token. */
final class Api {
 interface Result { void done(JSONObject object, String error); }
 final ExecutorService network=Executors.newSingleThreadExecutor();
 final Handler main=new Handler(Looper.getMainLooper());
 volatile String origin="", session="";
 volatile boolean closed;
 static String validateOrigin(String value) throws Exception {
  URI uri=new URI(value.trim());
  if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/"))) throw new Exception("Use a origem HTTPS, sem caminho, senha ou parâmetros.");
  return "https://"+uri.getRawAuthority();
 }
 void request(String method,String path,JSONObject body,String bearer,Result callback){
  if(closed)return;
  final String requestOrigin=origin;
  network.execute(()->{
   JSONObject result=null;String error=null;HttpURLConnection connection=null;
   try {
    if(requestOrigin.isEmpty())throw new Exception("Configure o servidor OAuth2 HTTPS.");
    URL url=new URL(requestOrigin+path);connection=(HttpURLConnection)url.openConnection();
    connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
    connection.setRequestProperty("Accept","application/json");
    if(bearer!=null&&!bearer.isEmpty())connection.setRequestProperty("Authorization","Bearer "+bearer);
    if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
    int status=connection.getResponseCode();InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
    if(stream==null)throw new Exception("Servidor retornou HTTP "+status);
    ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] block=new byte[4096];int read;
    try(InputStream in=stream){while((read=in.read(block))!=-1){if(buffer.size()+read>1024*1024)throw new Exception("Resposta muito grande.");buffer.write(block,0,read);}}
    result=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
    if(status<200||status>=300){error=result.optString("error","HTTP "+status);result=null;}
   } catch(Exception e){error=e.getMessage()==null?"Falha de conexão HTTPS.":e.getMessage();}
   finally{if(connection!=null)connection.disconnect();}
   final JSONObject value=result;final String message=error;
   main.post(()->{if(!closed)callback.done(value,message);});
  });
 }
 void close(){closed=true;network.shutdownNow();main.removeCallbacksAndMessages(null);session="";}
}
