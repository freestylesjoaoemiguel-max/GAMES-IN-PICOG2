package io.godot.camera;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

// Keep the source when publishing fails; only app-owned files can be copied.
final class SandboxVideoPublisher {
 private volatile String state="";
 synchronized void publish(Activity activity,String inputPath) {
  if ("salvando".equals(state)) return;
  state="salvando";
  new Thread(()->{
   Uri uri=null;
   try {
    File source=new File(inputPath).getCanonicalFile();
    String internal=activity.getFilesDir().getCanonicalPath()+File.separator;
    File externalRoot=activity.getExternalFilesDir(null);
    String external=externalRoot==null?"":externalRoot.getCanonicalPath()+File.separator;
    if (!source.getPath().startsWith(internal) && (external.isEmpty() || !source.getPath().startsWith(external))) throw new SecurityException("arquivo fora do aplicativo");
    if (!source.isFile() || source.length()==0) throw new IllegalStateException("arquivo vazio");
    if (Build.VERSION.SDK_INT>=29) {
     ContentResolver resolver=activity.getContentResolver();
     ContentValues values=new ContentValues();
     values.put(MediaStore.MediaColumns.DISPLAY_NAME,source.getName());
     values.put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4");
     values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/GilaMobi");
     values.put(MediaStore.MediaColumns.IS_PENDING,1);
     uri=resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
     if (uri==null) throw new IllegalStateException("galeria indisponível");
     try (InputStream in=new FileInputStream(source);OutputStream out=resolver.openOutputStream(uri)) {
      if(out==null) throw new IllegalStateException("não foi possível abrir destino");
      copy(in,out);
     }
     values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
     if(resolver.update(uri,values,null,null)!=1) throw new IllegalStateException("galeria não confirmou");
     source.delete();state="Vídeo salvo em Filmes/GilaMobi";
    } else {
     if(Build.VERSION.SDK_INT>=23 && activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("sem permissão de armazenamento");
     File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),"GilaMobi");
     if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("pasta indisponível");
     File output=new File(folder,source.getName());
     try (InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(output)){copy(in,out);}
     MediaScannerConnection.scanFile(activity,new String[]{output.getAbsolutePath()},new String[]{"video/mp4"},null);
     source.delete();state="Vídeo salvo em Filmes/GilaMobi";
    }
   } catch(Throwable ex) {
    if(uri!=null) try{activity.getContentResolver().delete(uri,null,null);}catch(Throwable ignored){}
    state="Falha ao exportar; vídeo guardado no aplicativo";
   }
  },"SandboxVideoSave").start();
 }
 String status(){return state;}
 private static void copy(InputStream in,OutputStream out)throws java.io.IOException{
  byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();
 }
}
