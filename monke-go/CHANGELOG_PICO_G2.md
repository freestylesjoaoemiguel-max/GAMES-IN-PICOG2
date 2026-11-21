# Changelog — porte para Pico G2

## js/platform.js
- Novo `isPico` (UA contém "Pico", `?device=pico`, ou `displayName` do VRDisplay contém "pico").
- `eyeNative` = resolução real por olho lida do `VRDisplay` (padrão 1920×2160 = G2 4K).
- `standalone()`, `sixDoF()`, `threeDoFHeadset()`; `gyroLikely` não é mais verdadeiro em Pico (evita cair no Cardboard).
- Qualidade padrão agora `Auto` para todos (Auto = Baixa em headsets standalone).
- Novos ajustes persistentes: `padClick`, `padTrig`, `padApp` (índices dos botões do gamepad).
## js/vr.js
- `scaleFor()`: em Pico, escala = √(orçamento de pixels ÷ pixels nativos), limitado a 1.
- `startWebVR()`: `sizeWebVR()` antes de `requestPresent()`.
- `threeDoF()`, `HOOK.noCam`, `effQuality` e fallback usam as novas funções.
## js/input.js
- Botões do gamepad configuráveis; botão App abre o menu (Gear VR continua "voltar"); ids Pico/Goblin/Neo em `isVRPad`.
- `INPUT.diag()` para diagnóstico.
## UI / docs
- index.html, main.js, vrmenu.js: textos Pico G2; favicon vazio. README, `PICO_G2.md`, `CHANGELOG_PICO_G2.md`.
## tools/
- `serve.py`, `build.sh`, `pico_test.py`.
## Não alterado
- Jogo, física, mapa, modelos, sons, multiplayer, música, three.js/PeerJS e o suporte a Oculus Go/Cardboard/desktop.
