package com.joaolimz.vrweb.game.web;

/**
 * The embedded browser as seen by the (platform independent) VR logic. The Android layer implements it
 * with a WebView; tests use a fake. Coordinates are in texture pixels (top-left origin).
 */
public interface WebHost {
    int DOWN = 0, MOVE = 1, UP = 2;

    void loadUrl(String url);
    void reload();
    void goBack();
    void goForward();
    /** Synthetic touch on the page. */
    void pointer(int action, float x, float y);
    /** Scrolls the scrollable element under (x,y) by dy texture pixels (positive = content moves up). */
    void scroll(float x, float y, float dy);
    /** Inserts text into the focused field. */
    void typeText(String text);
    /** Sends an Android key code (KEYCODE_DEL, KEYCODE_ENTER...). */
    void key(int androidKeyCode);
    /** Texture size, page zoom (texture px per CSS px) and whether to identify as a desktop browser. */
    void configure(int texW, int texH, float zoom, boolean desktopUserAgent);
    /** Logs out: clears cookies, storage and cache. */
    void clearData();

    String url();
    String title();
    /** 0..100 */
    int progress();
    boolean canGoBack();
    boolean canGoForward();
    /** Short description of the embedded engine (WebView / Chromium version) for diagnostics. */
    String engineInfo();
    boolean micGranted();
    void requestMic();
}
