# Matriz de aceitação — pendente de aparelho

O APK é real e assinado, mas **nenhuma linha desta matriz foi executada no G2**. Testes de navegador e assinatura não substituem instalação, conforto óptico ou rastreamento. O modo nativo atual é experimental; integração ao runtime PICO permanece ausente.

| Área | Verificação necessária | Estado |
|---|---|---|
| Instalação | `adb install`, abrir Activity, analisar logcat e permissões em firmware G2 | Não testado |
| Android antigo | Métodos API 23, shaders GLES2, fontes, TLS/CA e orientação landscape | Não testado |
| Compositor PICO | Runtime oficial, distorção óptica, sincronização, recenter e foco | Não integrado |
| Controle original | Pose 3DoF real, gatilho, APP, touchpad, menu e desconexão | Não integrado/testado |
| Entrada alternativa | Eventos Android genéricos, teclado USB/Bluetooth e touch | Implementada, não testada no Android |
| Teclado curvo | Todas as teclas, hover/click, escala, quatro curvaturas e raycast | Web desktop testado; headset pendente |
| Legibilidade | Pequena/Média/Grande/Gigante; três distâncias; clipping e contraste | Revisão visual desktop; headset pendente |
| Performance | FPS, frame time, memória, térmica, bateria durante 30 min | Não medido |
| Ciclo de vida | Pause/resume, bloqueio, perda de foco, mudança de áudio, 20 reinícios | Não testado |
| Microfone | Permissão negada/concedida/revogada, parar/reiniciar; sem transmissão | Código local apenas; Android não testado |
| OAuth2 | Aplicação própria, TLS, redirect, cancelamento, consentimento, expiração | Somente testes mockados |
| Envio autorizado | Canal correto, atribuição, 429, perda de rede, duplicação, logout | Somente testes mockados |
| Receber/DM/amigos/voz | SDK/API permitida e validação da integração | Não integrado |
| WebXR imersivo | Navegador compatível, HTTPS, entrada/saída, controle e recentragem | Não testado em runtime XR |

Não marque itens como aprovados só porque o APK instala. Para relatar falhas, informe modelo/firmware, ação, resultado esperado/observado e logcat sem tokens/dados pessoais. Interrompa o modo estéreo não calibrado se causar desconforto.
