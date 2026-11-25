# v52-g2web-1.0.0

## Projeto/proveniência
- Importado o código e assets efetivamente recebidos, com inventário SHA-256, README/preset Android originais preservados.
- Diretório independente; sem sobrescrever OpenSaber, Gorilla Rush ou MonkeGo.
- Godot 4.4.1 Web single-thread; componentes exclusivos Android isolados em `reference/`.
- Licenças e atribuição originais preservadas; autorização específica declarada pelo usuário, não verificada documentalmente.

## VR/input
- WebXRInterface, XROrigin3D e XRCamera3D reais; sessão immersive-vr solicitada diretamente por gesto do botão.
- local-floor preferido, fallback local com altura de olho virtual; cabeça orientada pelo runtime.
- Mapeamento esquerda/direita e único controle sem handedness; perda de pose rejeita dados antigos, oculta braço e remove apoio.
- Braço 3DoF explicitamente estimado, opção de pose medida para hardware 6DoF.
- Rigs/modelos recebidos mantidos, braços particionados por pesos de osso; orientação da mão e IK preservados.
- Gatilho/select para UI; longo de 1,1 s abre painel; correção para não tratar a soltura de um gesto longo como clique curto.
- Menu original renderizado em SubViewport no mundo, raio de controle e teclado virtual; painel rápido para mapas, personagens, ferramentas, câmera e voz.

## Locomoção
- Núcleo baseado no GMotion fornecido: média dos contatos, histórico de oito velocidades corporais reais, limiar 0,4, ganho de soltura 1,1, limite 4 m/s, contato 8 m/s e gravidade 5 m/s²; escalados pelo personagem.
- Sweep de esfera, recuperação de sobreposição e deslizamento usando o mundo físico do mapa.
- Apoio da mão pode sustentar o corpo; levantar solta; perda/descontinuidade de tracking reinicia amostras para não lançar o jogador.
- Sem caminhada automática pelo touchpad, sem duplicar um controle em duas mãos, sem modo de voo permanente.

## Web/renderização
- Ponte HTML em português com verificação de WebXR, WebGL2 e multiview, e avisos honestos.
- Layers polyfill Apache-2.0 fixado em 1.1.0 para navegadores com XRWebGLLayer sem Layers; pequena correção de preservação de framebuffer documentada em `web/vendor/NOTICE.md`.
- Corrigida tela preta estéreo causada pelo post-process escalado do Godot 4.4: somente em XR mantém escala 1,0. Qualidade baixa da prévia/espelho não foi convertida silenciosamente em alta.
- Corrigido render target multiview residual na saída de VR, recriando target mono; testes de reentrada/reconexão.
- Gravação Web termina o AVI original e solicita download via JavaScriptBridge; não finge usar encoder/galeria Android.

## Build/testes
- Importação, testes do motor com GL/Xvfb e export via CI com versão fixa do motor.
- Testes de variação de frequência/escala, impulsos, paredes/piso finos, sobreposição, mapas/rigs, perda de pose e braços ausentes.
- Browser fixture usa WebXRInterface do próprio jogo, não bypass de locomotion em JS. Emulador restrito a testes.
- Verificação de hashes fonte/export e proteção para não publicar o runtime IWER de testes.
- Relatórios explicitam que Pico físico e performance não foram testados.

## Ainda não comprovado

Compatibilidade de uma versão específica do navegador G2, framerate, consumo, conforto, mic e downloads no headset, multiplayer/voz entre aparelhos, cobertura completa de combate/itens e teclado original. Não se declara “perfeito”, “sem bugs” ou física idêntica ao Gtag. Veja `COMPATIBILITY.md`.
