# PICO G2 — guia atual (1.3)

**Tração horizontal no chão, somente mão direita, pulso livre em 360°.** Em Ajustes, use **PICO G2 · orientação 3DoF**. Aponte 45–55° para baixo até tocar o chão; gire mais para baixo/trás para avançar. Levante para soltar e repita, sem gatilho. O gatilho é opcional para agarrar. Não é a física exata de Gorilla Tag nem rastreamento posicional 6DoF.

Consulte [README](README.md) para links, calibração, build e limitações.

- **Site:** WebXR ou WebVR 1.1 conforme API efetivamente oferecida pelo navegador.
- **APK:** jogo local em **tela/WebView** e botão para abrir o site no navegador externo. Não integra runtime PICO nativo.
- **Controle:** somente o **braço direito**; braço esquerdo desativado no visual e na física. Orientação fornecida pela API, posição estimada pelo modelo de braço. Não cria 6DoF.
- **Testes:** Node + Chromium/WebVR simulado; nenhum teste físico G2 ou Android.
- **Sem API VR disponível:** continua em tela; não substitui o runtime por Cardboard no G2.

O texto que acompanhava o ZIP foi preservado em [PICO_G2_ORIGINAL](docs/PICO_G2_ORIGINAL.md). Ele descreve o estado e alegações de testes anteriores, não os resultados desta execução. Em particular, sua indicação genérica de navegador alternativo não comprova suporte ao hardware G2.
