#!/usr/bin/env python3
"""Servidor local para testar no Pico G2 sem publicar nada.
Uso:  python3 tools/serve.py [porta]      (padrão 8000)
Depois, com o headset por USB (depuração ativada):  adb reverse tcp:8000 tcp:8000
e abra  http://localhost:8000  no navegador do Pico  (localhost é "contexto seguro": WebVR/WebXR funcionam sem HTTPS).
Cabeçalhos: .bin/.gz servidos sem codificação surpresa; sem cache para facilitar iteração."""
import http.server, socketserver, sys, os
port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
class H(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, '.bin': 'application/octet-stream', '.glb': 'model/gltf-binary', '.mjs': 'text/javascript'}
    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache'); super().end_headers()
socketserver.ThreadingTCPServer.allow_reuse_address = True
with socketserver.ThreadingTCPServer(('0.0.0.0', port), H) as s:
    print(f'Gorilla Rush: http://localhost:{port}  (LAN: http://<IP-do-PC>:{port})'); s.serve_forever()
