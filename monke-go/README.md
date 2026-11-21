# MonkeGo para PICO G2 — Gorilla Rush

Adaptação do **projeto fornecido pelo usuário**, não um jogo substituto. Preserva os assets, mapa, modos SoloPlay/Lava Run e código de Gorilla Rush, creditado a **WelliDev** no ZIP.

- **Jogar:** https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/monke-go/?device=picog2
- **APK:** https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/monke-go/downloads/MonkeGo-PicoG2.apk
- **Pacote web:** https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/monke-go/downloads/MonkeGo-WebXR.zip

## Importante: o que o APK é

O arquivo de entrada é HTML/JavaScript/Three.js, **não Unity nem um projeto nativo PICO**.
O APK Android compilado neste repositório contém o jogo completo localmente para **modo tela, em WebView**, e um botão **ABRIR EM VR**, que abre o site HTTPS no navegador externo.

**O APK não é um porte VR nativo e não inclui o SDK/compositor PICO.** A WebView não fornece WebXR/WebVR imersivo. Não foi testada a instalação nem a execução no Android/G2 físico. O modo tela depende de Android System WebView com WebGL/JavaScript compatíveis. O pacote é `com.picog2.monkego`, versão 1.0.0, Android mínimo 6/API 23.

O modo VR do **site** utiliza WebXR, ou WebVR 1.1 quando exposto pelo navegador. Se o firmware/navegador do G2 não oferecer nenhuma dessas APIs, o site não pode fabricar acesso ao runtime. Não afirmamos compatibilidade de um navegador alternativo específico com o G2.

## Usar no PICO G2

1. Abra o link **Jogar** no navegador do headset (HTTPS).
2. Aguarde o modelo e mapa; o diagnóstico informa WebXR/WebVR disponíveis.
3. Se aparecer **ENTRAR EM VR (PICO G2)**, clique; o menu aparece dentro da cena.
4. Gatilho agarra; clique curto no touchpad pula; bordas giram; segurar o touchpad abre o menu. App também abre o menu se o sistema repassar esse botão.
5. Abra **Dispositivo** no menu para ver fonte, layout e índices de botões pressionados.
6. No layout legado, ajuste índices de gatilho/touchpad/App em **Ajustes**. Também é possível trocar eixos e inverter vertical. No layout `xr-standard`, o padrão WebXR tem precedência.
7. Para recentrar fisicamente, use a função de recentralização do próprio sistema PICO. Home pode ser reservado pelo sistema.

O G2 possui orientação **3DoF**, não posição medida da mão. A posição das mãos é estimada pelo modelo de braço do jogo; a segunda mão é simulada. Não é rastreamento 6DoF nem duas mãos físicas independentes. Gráficos **Baixos** são o padrão; isso não é promessa de FPS.

No computador: WASD/setas movem, arrastar olha, Espaço pula, M/Escape abre o menu. SoloPlay/Lava Run não exigem servidor; multiplayer continua PeerJS e precisa de internet, sinalização e eventualmente TURN. GitHub Pages não hospeda o servidor Node em `server/`.

## Correções desta adaptação

- Pose WebXR consultada pelo `XRInputSource.targetRaySpace` correto, no `XRFrame` atual e no mesmo espaço de referência. Não associa mais o índice da lista de fontes ao índice interno do Three.
- Eventos de seleção associados ao objeto da fonte (WeakMap), não a um índice que muda ao reconectar.
- Orientações inválidas/ausentes descartadas e quaternions normalizados; sem fallback para pose Gamepad obsoleta durante uma sessão XR.
- Perda/troca de fonte limpa âncora de escalada, filtro de braço e clique pendente: desconectar não produz um salto falso.
- `xr-standard`: separação entre pares de eixos touchpad/stick; zero não vira um eixo do outro dispositivo. Clique do stick não é tratado como App/Menu.
- Layout legado configurável também em fontes WebXR que não anunciam `xr-standard`; diagnósticos atualizados.
- Cabeça em WebXR lida da pose atual antes da física; inicialização espera a entrada em VR antes de iniciar o jogo.
- Android explicitamente em modo tela; não cai em Cardboard e não solicita câmera fingindo rastreamento PICO.

## Build e testes

Na raiz do repositório:

```sh
bash monke-go/tools/bootstrap_android.sh
cd monke-go
npm ci --ignore-scripts
npm test
npm run build
npm start
# Em outro terminal:
npm run test:browser
```

- `build_android.py`: Java → DEX → recursos Android → APK assinado v1/v2. Ferramentas com hashes verificados; mesma cadeia documentada em `../pulse-saber-g2/docs/TOOLCHAIN.md`. Nenhum template/runtime de terceiros é embutido pelo compilador.
- Verifica manifesto, assinatura, DEX, integridade ZIP e igualdade byte a byte dos assets/scripts empacotados.
- `build_web.mjs`: exporta site portátil para `build/webxr`, sem servidor, caches ou chaves.
- Workflow **Build MonkeGo APK and WebXR**: compila e testa em GitHub Actions, disponibiliza APK, checksum, pacote web e relatórios.
- Workflow **Deploy GitHub Pages**: recompila no GitHub e publica `/monke-go/`, preservando PicoVerso, Discord VR G2 e Pulse Saber G2.

**Assinatura de desenvolvimento:** cada runner sem uma chave configurada gera um certificado novo. Pode ser necessário desinstalar a versão anterior, perdendo dados locais. Para atualizações persistentes configure uma chave privada com `SIGNING_KEYSTORE`, `SIGNING_PASSWORD_FILE`, `SIGNING_ALIAS`; não coloque chaves/senhas no Git. O APK não é distribuição oficial PICO nem loja Android.

### Evidência e limites

14 testes Node cobrem mapeamento, pose, identidade da fonte, desconexão e integridade dos assets. 12 verificações Chromium cobrem carregamento real WebGL, Solo, Lava, menu, download APK e **WebVR simulado**. Relatórios em `docs/NODE_TESTS.txt`, `docs/BROWSER_TESTS.json`, `docs/ANDROID_BUILD.txt`. Os artefatos de cada execução CI contêm os relatórios e checksums correspondentes àquele build; o APK local pode ter outra assinatura.

**Não testados:** headset PICO G2 físico, instalação Android, navegador real do G2, sessão WebXR imersiva real, FPS, temperatura, conforto, multiplayer na internet. Testes de WebXR usam mocks de frame/espaço; não substituem teste físico. Os documentos originais relatam testes de outra execução: veja `docs/*_ORIGINAL.md`, não confunda com estes resultados.

## Origem/licenças

[Créditos e ressalvas](CREDITS.md). Hash do ZIP e assets em `docs/INPUT_PROVENANCE.json`. A licença MIT da raiz **não altera licenças dos assets recebidos**. Não foram adicionados assets proprietários novos, nem há comprovação de licença individual de todos os materiais recebidos.
