# Changelog — porte para Pico G2

## 1.1.0 — somente braço direito
- Desativação visual e física do braço esquerdo; remoção de troca automática/espelhamento.
- Migração de configurações antigas para braço direito fixo; menus sem opções de duas mãos.
- Pose suavizada com limite de velocidade e proteção contra impulsos ao reconectar/recentrar.
- Correção da integração duplicada de movimento ao escalar. Cancelar menu/pose não arremessa o jogador.
- APK versionCode 2/versionName 1.1.0, scripts web versionados.
- 24 testes Node e 17 verificações Chromium (WebVR simulado); headset físico não testado.

---

## Histórico que acompanhava o ZIP (estado anterior)

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
