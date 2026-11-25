package com.joaolimz.vrweb.platform;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.joaolimz.vrweb.game.web.WebHost;
import com.joaolimz.vrweb.render.VrRenderer;

/**
 * The embedded browser. A real WebView lives (attached, but behind the GL surface) at a size derived from the
 * texture size and zoom; ~15 times per second it is drawn into one of three bitmaps that the GL thread uploads
 * as a texture. Pointer events, scrolling and typing are injected into the WebView.
 *
 * Identification: the WebView's real user agent is kept (same Chromium version) and only the platform part is
 * switched to "desktop Linux". Pretending to be a newer Chrome than the engine really is breaks captchas.
 */
public final class WebPanel implements WebHost, VrRenderer.WebFrames {
    private static final int FRAME_MS = 66;
    private static final int REQ_MIC = 7001;

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FrameLayout holder;
    private WebView web;
    private final float density;
    private int texW = 1024, texH = 640;
    private float zoom = 1f;
    private boolean desktopUa = true;
    private String defaultUa;

    private final Object lock = new Object();
    private Bitmap[] buf = new Bitmap[3];
    private int ready = -1, reading = -1, drawingIdx = -1;
    private volatile String url = "", title = "";
    private volatile int progress = 100;
    private volatile boolean canBack, canFwd;
    private long downTime;
    private boolean running;
    private PermissionRequest pendingPermission;

    public WebPanel(Activity act, FrameLayout holder) {
        this.act = act;
        this.holder = holder;
        this.density = Math.max(1f, act.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ lifecycle
    public void create() {
        web = new WebView(act);
        web.setBackgroundColor(0xFF1E1F22);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setDatabaseEnabled(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        st.setUseWideViewPort(true);
        st.setLoadWithOverviewMode(false);
        st.setSupportZoom(false);
        st.setBuiltInZoomControls(false);
        st.setJavaScriptCanOpenWindowsAutomatically(true);
        st.setSupportMultipleWindows(false);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= 21) st.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        defaultUa = st.getUserAgentString();
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, true); // captchas live in third-party frames
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String s = r.getUrl().toString();
                if (s.startsWith("http://") || s.startsWith("https://")) return false;
                return true; // ignore intent:// and app links: stay inside the VR browser
            }

            @Override public void onPageFinished(WebView v, String u) { refreshState(); CookieManager.getInstance().flush(); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { progress = p; refreshState(); }

            @Override public void onReceivedTitle(WebView v, String t) { title = t == null ? "" : t; }

            @Override public void onPermissionRequest(final PermissionRequest request) {
                ui.post(new Runnable() { @Override public void run() { handlePermission(request); } });
            }
        });
        holder.addView(web, 0, new FrameLayout.LayoutParams(texW, texH));
    }

    public void start() {
        running = true;
        ui.removeCallbacks(capture);
        ui.post(capture);
        if (web != null) web.onResume();
    }

    public void stop() {
        running = false;
        ui.removeCallbacks(capture);
        if (web != null) { web.onPause(); CookieManager.getInstance().flush(); }
    }

    public void destroy() {
        running = false;
        if (web != null) { holder.removeView(web); web.destroy(); web = null; }
    }

    private final Runnable capture = new Runnable() {
        @Override public void run() {
            if (!running || web == null) return;
            try { captureFrame(); } catch (RuntimeException | OutOfMemoryError e) { /* skip this frame */ }
            ui.postDelayed(this, FRAME_MS);
        }
    };

    private void captureFrame() {
        int w = web.getWidth(), h = web.getHeight();
        if (w <= 0 || h <= 0) return;
        int idx;
        synchronized (lock) {
            idx = -1;
            for (int i = 0; i < 3; i++) if (i != ready && i != reading) { idx = i; break; }
            if (idx < 0) return;
            if (buf[idx] == null || buf[idx].getWidth() != texW || buf[idx].getHeight() != texH) {
                buf[idx] = Bitmap.createBitmap(texW, texH, Bitmap.Config.ARGB_8888);
            }
            drawingIdx = idx;
        }
        Bitmap b = buf[idx];
        Canvas c = new Canvas(b);
        c.drawColor(Color.rgb(30, 31, 34));
        float k = zoom / density;
        c.scale(k, k);
        web.draw(c);
        synchronized (lock) { ready = idx; drawingIdx = -1; }
    }

    // ------------------------------------------------------------------ frames for GL
    @Override public Bitmap acquire() {
        synchronized (lock) {
            if (ready < 0) return null;
            reading = ready;
            ready = -1;
            return buf[reading];
        }
    }

    @Override public void release() { synchronized (lock) { reading = -1; } }

    // ------------------------------------------------------------------ WebHost
    private void refreshState() {
        if (web == null) return;
        String u = web.getUrl();
        url = u == null ? "" : u;
        canBack = web.canGoBack();
        canFwd = web.canGoForward();
    }

    @Override public void configure(final int w, final int h, final float z, final boolean desktop) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (web == null) return;
                texW = w; texH = h; zoom = z;
                desktopUa = desktop;
                web.getSettings().setUserAgentString(desktop ? desktopUserAgent(defaultUa) : defaultUa);
                int px = Math.round(w / z * density), py = Math.round(h / z * density);
                ViewGroup.LayoutParams lp = web.getLayoutParams();
                lp.width = px; lp.height = py;
                web.setLayoutParams(lp);
            }
        });
    }

    /** Keeps the engine's real Chrome version, only changes the platform tokens to a desktop Linux machine. */
    static String desktopUserAgent(String ua) {
        if (ua == null) return null;
        String s = ua.replaceAll("\\(Linux; Android[^)]*\\)", "(X11; Linux x86_64)");
        s = s.replace(" Mobile Safari", " Safari").replace("; wv", "");
        s = s.replaceAll(" Version/[0-9.]+", "");
        return s;
    }

    @Override public String engineInfo() {
        String ua = defaultUa == null ? "" : defaultUa;
        int i = ua.indexOf("Chrome/");
        String chrome = i >= 0 ? ua.substring(i, Math.min(ua.length(), ua.indexOf(' ', i) > 0 ? ua.indexOf(' ', i) : ua.length())) : "?";
        return chrome + (Build.VERSION.SDK_INT >= 26 && WebView.getCurrentWebViewPackage() != null
                ? "  (" + WebView.getCurrentWebViewPackage().packageName + ")" : "") + "  Android " + Build.VERSION.RELEASE;
    }

    @Override public void loadUrl(final String u) { ui.post(new Runnable() { @Override public void run() { if (web != null) web.loadUrl(u); } }); }

    @Override public void reload() { ui.post(new Runnable() { @Override public void run() { if (web != null) web.reload(); } }); }

    @Override public void goBack() { ui.post(new Runnable() { @Override public void run() { if (web != null && web.canGoBack()) web.goBack(); } }); }

    @Override public void goForward() { ui.post(new Runnable() { @Override public void run() { if (web != null && web.canGoForward()) web.goForward(); } }); }

    @Override public void pointer(final int action, final float x, final float y) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (web == null) return;
                float k = density / zoom;
                long now = SystemClock.uptimeMillis();
                int a;
                if (action == DOWN) { downTime = now; a = MotionEvent.ACTION_DOWN; }
                else if (action == MOVE) a = MotionEvent.ACTION_MOVE;
                else a = MotionEvent.ACTION_UP;
                MotionEvent e = MotionEvent.obtain(downTime, now, a, x * k, y * k, 0);
                web.requestFocus();
                web.dispatchTouchEvent(e);
                e.recycle();
            }
        });
    }

    @Override public void scroll(final float x, final float y, final float dy) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (web == null) return;
                float cx = x / zoom, cy = y / zoom, d = dy / zoom;
                String js = "(function(x,y,d){var e=document.elementFromPoint(x,y);while(e&&e!==document.body){var s=getComputedStyle(e);"
                        + "var o=s.overflowY;if((o==='auto'||o==='scroll'||o==='overlay')&&e.scrollHeight>e.clientHeight){e.scrollTop+=d;return;}"
                        + "e=e.parentElement;}window.scrollBy(0,d);})(" + cx + "," + cy + "," + d + ");";
                web.evaluateJavascript(js, null);
            }
        });
    }

    @Override public void typeText(final String text) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (web == null) return;
                web.requestFocus();
                InputConnection ic = null;
                try { ic = web.onCreateInputConnection(new EditorInfo()); } catch (RuntimeException ignored) { }
                if (ic != null && ic.commitText(text, 1)) return;
                String esc = text.replace("\\", "\\\\").replace("'", "\\'");
                web.evaluateJavascript("document.execCommand('insertText',false,'" + esc + "');", null);
            }
        });
    }

    @Override public void key(final int code) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (web == null) return;
                web.requestFocus();
                long t = SystemClock.uptimeMillis();
                web.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                web.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
            }
        });
    }

    @Override public void clearData() {
        ui.post(new Runnable() {
            @Override public void run() {
                CookieManager cm = CookieManager.getInstance();
                if (Build.VERSION.SDK_INT >= 21) cm.removeAllCookies(null); else cm.removeAllCookie();
                cm.flush();
                WebStorage.getInstance().deleteAllData();
                if (web != null) { web.clearCache(true); web.clearHistory(); web.clearFormData(); }
            }
        });
    }

    @Override public String url() { return url; }
    @Override public String title() { return title; }
    @Override public int progress() { return progress; }
    @Override public boolean canGoBack() { return canBack; }
    @Override public boolean canGoForward() { return canFwd; }

    // ------------------------------------------------------------------ microphone
    @Override public boolean micGranted() {
        return Build.VERSION.SDK_INT < 23 || act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void requestMic() {
        ui.post(new Runnable() {
            @Override public void run() {
                if (Build.VERSION.SDK_INT >= 23 && !micGranted()) act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
        });
    }

    private void handlePermission(PermissionRequest r) {
        boolean wantsAudio = false;
        for (String res : r.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) wantsAudio = true;
        if (!wantsAudio) { r.deny(); return; }
        if (micGranted()) {
            r.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pendingPermission = r;
            requestMic();
        }
    }

    /** Called by the activity when the runtime permission dialog finishes. */
    public void onPermissionResult(boolean granted) {
        if (pendingPermission != null) {
            if (granted) pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}); else pendingPermission.deny();
            pendingPermission = null;
        }
        if (granted && web != null) web.reload();
    }

    public boolean handleBack() {
        if (web != null && web.canGoBack()) { web.goBack(); return true; }
        return false;
    }
}
