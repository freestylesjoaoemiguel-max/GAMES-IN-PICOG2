# 🏓 Ping-Pong VR — Pico G2 (WebXR)

Campeonato de ping-pong em VR: estádio com arquibancadas e torcida, adversário 3D animado, árbitro, física realista (arrasto, efeito/spin, quique na mesa, rede) e raquete rastreada pelo controle.

## Como jogar
* **Raquete:** mova o controle como uma raquete (a face acompanha a inclinação do controle; a mão/braço aparecem em 3D).
* **Andar para os lados:** deslize o **touch de trás** ← → (frente/trás dá um passo à frente/atrás).
* **Saque:** aperte o **gatilho** para lançar a bola e bata nela no ar. Ela deve quicar no seu lado e depois no do Pico.
* **Menu/pausa:** botão lateral. Escolha dificuldade (Fácil/Médio/Difícil), mão da raquete, altura e som.
* Regras: primeiro a 11 pontos (diferença de 2), saque alternado a cada 2 pontos, falta se a bola quicar duas vezes, bater antes de quicar, bola na rede/fora.

## Rodar
* **WebXR (recomendado):** abra `…/pingpong/` em HTTPS no navegador do Pico (GitHub Pages) → **Entrar em VR**.
* **APK:** Release `pingpong-latest` → `adb install PingPong-VR.apk` (WebView: modo giroscópio/tela cheia, sem WebXR).
* **PC (teste):** `python3 -m http.server` na pasta `pingpong` e abra `http://localhost:8000` → *Jogar na tela* (mouse = raquete, A/D andar, Espaço lança a bola).

## Arquivos
`index.html` · `js/physics.js` (física) · `js/pp.js` (jogo, personagens, estádio) · `lib/three.min.js` · `android/` (projeto do APK).
