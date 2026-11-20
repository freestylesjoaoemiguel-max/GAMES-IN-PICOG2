package com.picoverso.cam;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Servidor HTTP mínimo (sem dependências): /stream (MJPEG), /sensor (SSE com giroscópio), /info, /zoom?r=, /snapshot.jpg. */
public class StreamServer {
    public interface Host {
        String infoJson();
        float setZoom(float ratio);
    }

    private final int port;
    private final Host host;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Object lock = new Object();
    private byte[] jpeg;
    private long seq = 0;
    private volatile float[] quat = {0, 0, 0, 1};
    private volatile int rot = 1;
    public final AtomicInteger clients = new AtomicInteger();
    private volatile boolean running;

    public StreamServer(int port, Host host) { this.port = port; this.host = host; }

    public void start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        running = true;
        pool.execute(() -> {
            while (running) {
                try {
                    final Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    pool.execute(() -> handle(s));
                } catch (IOException e) { if (running) continue; }
            }
        });
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    public void pushFrame(byte[] data) {
        synchronized (lock) { jpeg = data; seq++; lock.notifyAll(); }
    }

    public void setOrientation(float x, float y, float z, float w, int displayRotation) {
        quat = new float[]{x, y, z, w};
        rot = displayRotation;
    }

    private static final String CORS = "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\n";

    private void handle(Socket s) {
        try {
            s.setSoTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = in.readLine();
            if (line == null) { s.close(); return; }
            String l;
            while ((l = in.readLine()) != null && !l.isEmpty()) { /* ignora cabeçalhos */ }
            s.setSoTimeout(0);
            String[] p = line.split(" ");
            String method = p.length > 0 ? p[0] : "GET", target = p.length > 1 ? p[1] : "/";
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 65536);
            if (method.equals("OPTIONS")) { write(out, "HTTP/1.1 204 No Content\r\n" + CORS + "Connection: close\r\n\r\n"); out.flush(); s.close(); return; }
            switch (path) {
                case "/stream": stream(out); break;
                case "/sensor": sensor(out); break;
                case "/zoom": {
                    float r = 1f;
                    for (String kv : query.split("&")) if (kv.startsWith("r=")) { try { r = Float.parseFloat(kv.substring(2)); } catch (Exception ignored) {} }
                    float applied = host.setZoom(r);
                    json(out, String.format(Locale.US, "{\"zoom\":%.2f}", applied));
                    break;
                }
                case "/info": json(out, host.infoJson()); break;
                case "/snapshot.jpg": {
                    byte[] d; synchronized (lock) { d = jpeg; }
                    if (d == null) { write(out, "HTTP/1.1 503 Service Unavailable\r\n" + CORS + "Connection: close\r\n\r\n"); }
                    else { write(out, "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + d.length + "\r\n" + CORS + "Connection: close\r\n\r\n"); out.write(d); }
                    out.flush();
                    break;
                }
                default: {
                    String html = "<!doctype html><meta charset=utf-8><title>PicoVerso Camera</title><body style='background:#111;color:#eee;font-family:sans-serif'><h2>PicoVerso Câmera</h2><img src='/stream' style='max-width:100%'>";
                    byte[] b = html.getBytes(StandardCharsets.UTF_8);
                    write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\n" + CORS + "Connection: close\r\n\r\n");
                    out.write(b); out.flush();
                }
            }
            s.close();
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void json(OutputStream out, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        write(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nCache-Control: no-store\r\n" + CORS + "Connection: close\r\n\r\n");
        out.write(b); out.flush();
    }

    private void stream(OutputStream out) throws IOException {
        clients.incrementAndGet();
        try {
            write(out, "HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-cache, no-store\r\n" + CORS + "Connection: close\r\n\r\n");
            long last = -1;
            while (running) {
                byte[] d;
                synchronized (lock) {
                    while (seq == last && running) { try { lock.wait(1000); } catch (InterruptedException e) { return; } }
                    d = jpeg; last = seq;
                }
                if (d == null) continue;
                write(out, "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + d.length + "\r\n\r\n");
                out.write(d);
                write(out, "\r\n");
                out.flush();
            }
        } finally { clients.decrementAndGet(); }
    }

    private void sensor(OutputStream out) throws IOException {
        write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n" + CORS + "Connection: keep-alive\r\n\r\n");
        while (running) {
            float[] q = quat;
            write(out, String.format(Locale.US, "data: {\"q\":[%.5f,%.5f,%.5f,%.5f],\"r\":%d}\n\n", q[0], q[1], q[2], q[3], rot));
            out.flush();
            try { Thread.sleep(33); } catch (InterruptedException e) { return; }
        }
    }

    private static void write(OutputStream o, String s) throws IOException { o.write(s.getBytes(StandardCharsets.ISO_8859_1)); }
}
