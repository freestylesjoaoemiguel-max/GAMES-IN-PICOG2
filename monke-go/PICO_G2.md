# PICO G2 — guia atual

Consulte [README](README.md) para links, calibração, build e limitações.

- **Site:** WebXR ou WebVR 1.1 conforme API efetivamente oferecida pelo navegador.
- **APK:** jogo local em **tela/WebView** e botão para abrir o site no navegador externo. Não integra runtime PICO nativo.
- **Controle:** orientação real fornecida pela API; posição estimada por modelo de braço. Não cria 6DoF.
- **Testes:** Node + Chromium/WebVR simulado; nenhum teste físico G2 ou Android.
- **Sem API VR disponível:** continua em tela; não substitui o runtime por Cardboard no G2.

O texto que acompanhava o ZIP foi preservado em [PICO_G2_ORIGINAL](docs/PICO_G2_ORIGINAL.md). Ele descreve o estado e alegações de testes anteriores, não os resultados desta execução. Em particular, sua indicação genérica de navegador alternativo não comprova suporte ao hardware G2.
