package com.picog2.gorillarushv45;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.util.HashMap;

/** Offline screen edition of the supplied web game. Immersive VR opens an external browser.
 * No claim of native PICO compositor/controller SDK support; no JavaScript native bridge. */
public class MainActivity extends Activity {
    private static final String ORIGIN = "https://gorillav45.invalid";
    private static final String VR_URL = "https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/gorilla-rush-v45/?device=picog2&v=1.0.7-free";
    private WebView web;
    private LinearLayout layout;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff101e18);
        TextView note = new TextView(this);
        note.setText("Gorilla Rush v45 • Tela offline | VR somente no navegador compatível");
        note.setTextColor(0xffc9efd6); note.setPadding(12, 6, 12, 6); layout.addView(note);
        LinearLayout bar = new LinearLayout(this);
        Button browser = new Button(this); browser.setText("ABRIR EM VR");
        browser.setOnClickListener(v -> openVR()); bar.addView(browser);
        Button menu = new Button(this); menu.setText("MENU");
        menu.setOnClickListener(v -> gameMenu()); bar.addView(menu);
        Button home = new Button(this); home.setText("LOBBY");
        home.setOnClickListener(v -> web.loadUrl(ORIGIN + "/index.html")); bar.addView(home);
        layout.addView(bar);
        web = new WebView(this);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " GorillaV45Android/1.0");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri uri = r.getUrl();
                if (!"https".equals(uri.getScheme()) || !"gorillav45.invalid".equals(uri.getHost())) return null;
                String path = uri.getPath(); if (path == null || path.equals("/")) path = "/index.html";
                if (!"GET".equals(r.getMethod()) || path.contains("..") || path.contains("\\") || path.contains("\0")) return missing();
                try {
                    String mime = "application/octet-stream";
                    if(path.endsWith(".html")) mime="text/html";
                    else if(path.endsWith(".js")) mime="application/javascript";
                    else if(path.endsWith(".css")) mime="text/css";
                    else if(path.endsWith(".json")) mime="application/json";
                    else if(path.endsWith(".png")) mime="image/png";
                    else if(path.endsWith(".jpg")) mime="image/jpeg";
                    else if(path.endsWith(".mp3")) mime="audio/mpeg";
                    else if(path.endsWith(".ogg")) mime="audio/ogg";
                    else if(path.endsWith(".otf")) mime="font/otf";
                    else if(path.endsWith(".glb")) mime="model/gltf-binary";
                    return new WebResourceResponse(mime, null, getAssets().open("game"+path));
                } catch(Exception e) { return missing(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {return navigate(r.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {return navigate(Uri.parse(url));}
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(layout);
        if(b != null) web.restoreState(b); else web.loadUrl(ORIGIN + "/index.html");
    }
    private WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", new HashMap<String,String>(), new ByteArrayInputStream(new byte[0]));
    }
    private boolean navigate(Uri uri) {
        if("https".equals(uri.getScheme()) && "gorillav45.invalid".equals(uri.getHost())) {
            if(uri.getPath() != null && uri.getPath().startsWith("/downloads/")) {openVR(); return true;}
            return false;
        }
        if("https".equals(uri.getScheme())) {try {startActivity(new Intent(Intent.ACTION_VIEW, uri));}catch(ActivityNotFoundException ignored){}}
        return true;
    }
    private void openVR() {
        new AlertDialog.Builder(this).setTitle("VR no navegador, não na WebView")
          .setMessage("O APK joga na tela e não inclui o SDK PICO. Para cabeça e controle 3DoF, abra o site em um navegador que ofereça WebXR ou WebVR. A compatibilidade do seu firmware G2 precisa ser testada. Internet necessária para abrir o site.")
          .setPositiveButton("ABRIR SITE", (d,w) -> {try {startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(VR_URL)));}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setMessage("Nenhum navegador instalado.").setPositiveButton("OK",null).show();}})
          .setNegativeButton("VOLTAR", null).show();
    }
    private void gameMenu(){web.evaluateJavascript("document.getElementById('pico-bar').scrollIntoView();",null);}
    @Override public void onBackPressed(){gameMenu();}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_MENU){gameMenu();return true;}return super.onKeyDown(key,e);}
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("if(typeof MG!=='undefined'){MG.pause();ML.forEach(a=>a.pause());WIND.pause();STORE.pause();}",null);web.onPause();web.pauseTimers();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null){web.resumeTimers();web.onResume();}}
    @Override protected void onSaveInstanceState(Bundle b){web.saveState(b);super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){if(web!=null){layout.removeView(web);web.destroy();web=null;}super.onDestroy();}
}
