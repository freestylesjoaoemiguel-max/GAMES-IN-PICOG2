# Gorilla Rush v45 — adaptação de navegador para PICO G2 (teste 1)

**Não é uma declaração de compatibilidade física certificada.** O usuário confirmou que a versão anterior acompanhava a cabeça/controle em Firefox Reality ou Wolvic, mas não permitia andar por gesto. Esta edição usa o **novo TXT v45**, em endereço e pacote separados. MonkeGo 1.3 permanece intacto.

- Jogo: https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/gorilla-rush-v45/?device=picog2
- APK: `downloads/Gorilla-Rush-v45-PicoG2.apk` — **tela offline + abrir navegador externo**, não VR nativo.
- Projeto completo: `downloads/Gorilla-Rush-v45-projeto.zip`.
- Versão anterior: https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/monke-go/?device=picog2&v=1.3

## Usar no headset

1. No navegador VR que já acompanha seu controle, abra o endereço novo por HTTPS. Aguarde o carregamento dos mapas. Verifique **V45 · PICO G2 · TESTE 1**.
2. Toque em **ENTRAR EM VR**. O botão só é habilitado se a API oferecer sessão WebXR imersiva ou VRDisplay WebVR com apresentação. Não há fallback disfarçado para VR Box.
3. Apenas a mão direita é controlada. A posição do braço é **estimada**; o quaternion medido controla o pulso em 360°. Não há duas mãos 6DoF nem rastreamento óptico dos dedos.
4. **Andar sem botão:** aponte para baixo até o painel indicar **APOIADA**. Gire mais para baixo/trás para empurrar o chão. Levante a ponta para soltar e repita. A mão não gera força no ar. **Mover o controle para trás sem girá-lo não fornece translação ao jogo**, pois o G2 é 3DoF.
5. Segure o clique do touchpad por 0,6 s para o menu. Aponte o laser ou use cima/baixo do touchpad para escolher; gatilho ou clique curto confirma. Em **CALIBRAR**, aponte para a frente antes de confirmar; isso corrige o eixo do braço estimado, não inventa posição rastreada.
6. Gatilho segura contato real em chão/parede. Clique curto no centro do touchpad pula; bordas giram 30°. **Ajuda no touchpad** é opcional, identificada no menu e desligada por padrão; não é apresentada como locomoção física.
7. Se continuar sem andar, veja se o painel mostra **NO AR**, **APOIADA** ou **SEM POSE**. Saia do VR e use **SALVAR DIAGNÓSTICO**; envie esse JSON, navegador/versão e uma gravação curta. O diagnóstico contém apenas dados locais de execução, não credenciais.

A interface 2D do jogo original permite selecionar salas, acessórios e modos antes da sessão. O botão VR inicia Solo se você ainda não entrou em um modo. Para um modo de rede, entre pela interface original primeiro e então entre em VR. As telas de perfil/amigos/microfone do original continuam 2D; não foram todas reescritas como UI imersiva. Rede PeerJS, voz, câmera opcional e conforto de VR não foram validados em hardware.

## APK / instalação

O arquivo recebido é HTML/JS/Three.js, não Unity/Unreal. **O APK gerado não contém o compositor PICO ou integração nativa do controle.** Android WebView não adquire WebXR por trocar manifesto ou nome do dispositivo. O modo VR é o site no navegador externo já instalado.

- Pacote independente `com.picog2.gorillarushv45`, versão 1.0.0/code 1; Android mínimo API 23, alvo 28; Java/DEX, sem biblioteca JNI/ABI de engine.
- Abra o APK no instalador do G2 ou use `adb install Gorilla-Rush-v45-PicoG2.apk` com depuração USB autorizada. **Instalação não testada aqui.** Não desinstale o MonkeGo anterior.
- O botão **ABRIR EM VR** abre o site externo (internet necessária). No APK, o jogo fica em tela/WebView. Não confundir duas imagens no modo VR Box original com uma sessão imersiva.
- APK assinado para desenvolvimento. Configure `SIGNING_KEYSTORE`, `SIGNING_PASSWORD_FILE`, `SIGNING_ALIAS` para atualizações com certificado estável. Um certificado novo pode impedir atualização; não desinstale sem backup dos dados.

### Por que não há APK VR nativo nesta entrega?

Não há SDK legado licenciado ou projeto nativo no material fornecido. O suporte ao G2 pertence às integrações legadas, não se presume em SDKs recentes/OpenXR. Uma conversão nativa completa exigiria integrar esse runtime e portar o renderizador Three.js/JavaScript ou um navegador com backend legado, além de validar no headset. Isso **não foi feito**; o APK de tela não é substituto nem cumprimento de um porte VR nativo. A alternativa executada é a integração WebXR/WebVR do jogo no navegador que você informou já fornecer tracking. Veja `docs/COMPATIBILITY_AUDIT.md`.

## Compilar e testar

Requisitos: Linux, Python 3 com venv, Node 22/npm, tar. Na raiz deste diretório:

```sh
npm ci --ignore-scripts --no-audit --no-fund
npm test
bash tools/bootstrap_android.sh
npm run build
npm start
# Outro terminal: testes a partir da exportação, incluindo subdiretório
PREVIEW_BASE_URL=http://127.0.0.1:5180/build/webxr/ npm run test:browser
python3 tools/package_project.py
```

O bootstrap baixa ferramentas Java/Android fixadas por versão e verifica seus hashes antes de execução. Não baixa nem embute SDK PICO. Build real gera APK assinado, checksum, exportação web e projeto ZIP. O SDK Android de compilação não comprova compatibilidade do runtime VR.

`tools/import_source.py TXT DIRETORIO_VAZIO` reproduz a extração da entrada exata, verificando MD5. **Não execute sobre este projeto modificado:** produz o baseline sem integração. `docs/SOURCE_READABLE.txt` permite inspecionar o código original com os enormes payloads binários abreviados; os payloads reais e hashes estão em `assets/` e no manifesto. `baseline.html` preserva a estrutura original extraída para comparação; a entrada final é `index.html`.

## Verificação e limites

- **Compilação:** relatório real em `docs/ANDROID_BUILD.txt`; sintaxe JS verificada pelo build.
- **Código/análise estática:** inventário integral dos 68 payloads, hashes, dependências e pontos de integração; `docs/COMPATIBILITY_AUDIT.md`, `INPUT_PROVENANCE.json`, `SOURCE_STRUCTURE.json`.
- **Testes automatizados:** `docs/NODE_TESTS.txt` e `BROWSER_TESTS.json`. Chromium executa o modelo, mapas, colisões e frame loop reais, mas as poses/sessões são simuladas. Inclui movimento horizontal em três ciclos com gravidade, sem botões, giro 360°, soltura, perda de pose e as duas projeções WebVR. Isso não prova o comportamento do navegador do G2.
- **NÃO testado no hardware físico PICO G2:** instalação Android, compositor real, firmware/browser do usuário, FPS, latência, temperatura, conforto, câmera, voz, multiplayer e transições completas entre todos os modos.
- Sem promessa de "perfeito/sem bugs", nem de física idêntica ao Gorilla Tag original: um controle 3DoF não fornece as posições independentes de duas mãos usadas por esse tipo de jogo. Braço articulado por IK, não ragdoll.

## Reversão sem perder a versão anterior

Não substituímos `/monke-go/` nem seu APK. Basta abrir o link anterior. Checkpoint do repositório anterior: `04f0d725a5e6b1e1a8a572b094950981784b2220`. Os pacotes Android são diferentes e podem coexistir. Se esta versão falhar, informe o diagnóstico; não é necessário apagar seus dados ou desfazer as outras aplicações.

[Alterações](CHANGELOG.md) · [Créditos/licenças](CREDITS.md)
