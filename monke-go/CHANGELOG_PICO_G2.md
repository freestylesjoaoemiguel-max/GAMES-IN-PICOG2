# Changelog — porte para Pico G2

## 1.3.0 — tração no chão e pulso livre em 360°
- Corrige falta de movimento horizontal: apoio com atrito no chão transmite o gesto ao corpo sem gatilho. Levantar solta o apoio; paredes mantêm deslizamento e agarrar opcional.
- Alcance estimado para baixo ampliado dentro do comprimento do braço, sem fingir posição medida 6DoF.
- Giro do pulso não cancela apoio como se fosse recentralização; orientação por quaternion sem limite de Euler.
- Evita que raios deslocados prendam a mão na face inferior do chão durante a soltura.
- 37 testes Node, 17 verificações gerais Chromium e 12 de mão/física. Teste de três ciclos sem gatilho/joystick: aproximadamente 2,34 m de avanço; giro de 360° nos três eixos do esqueleto real. Poses simuladas, sem teste físico.
- APK 1.3.0/versionCode 4: continua modo tela + link VR, sem SDK nativo.

## 1.2.0 — mão visível, pose do controle e contato de superfície
- Menu não recolhe mais o braço; rotação do pulso (incluindo roll) aplicada à malha da mão. Laser sai do pulso.
- Neutro à frente do jogador; braço articulado via IK, sem trocar de mão.
- Perfil 3DoF explícito; correção da confusão generic-touchpad/Touch e suporte à indicação emulatedPosition.
- Varredura aproximada da esfera da mão, deslizamento e empurrão do corpo sem gatilho. Código próprio, não física exata de Gorilla Tag.
- Aviso de perda de pose e mão em repouso visível.
- 30 testes Node + 17 verificações gerais + 10 específicas de mão/física (poses XR simuladas; sem hardware físico).
- APK 1.2.0/versionCode 3 continua modo tela + link VR, sem SDK nativo.

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
