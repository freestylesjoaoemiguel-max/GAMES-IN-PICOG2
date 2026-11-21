# Gorilla Rush v45 — adaptação de navegador para PICO G2 (gorila 1.0.4)

**Não é uma declaração de compatibilidade física certificada.** O usuário confirmou que a versão anterior acompanhava a cabeça/controle em Firefox Reality ou Wolvic, mas não permitia andar por gesto. Esta edição usa o **novo TXT v45**, em endereço e pacote separados. MonkeGo 1.3 permanece intacto.

- Jogo: https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/gorilla-rush-v45/?device=picog2&v=1.0.4-gorila
- APK: `downloads/Gorilla-Rush-v45-PicoG2.apk` — **tela offline + abrir navegador externo**, não VR nativo.
- Projeto completo: `downloads/Gorilla-Rush-v45-projeto.zip`.
- Versão anterior: https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/monke-go/?device=picog2&v=1.3

## Usar no headset

1. No navegador VR que já acompanha seu controle, abra o endereço novo por HTTPS. Aguarde o carregamento dos mapas. Verifique o botão principal **INICIAR VR**.
2. Toque em **INICIAR VR**. Não precisa selecionar um modo no topo: o próprio clique solicita WebXR e inicia o jogo após a autorização do navegador. Em navegadores antigos sem WebXR, usa WebVR quando disponível. O botão só é habilitado se a API oferecer sessão WebXR imersiva ou VRDisplay WebVR com apresentação. Não há fallback disfarçado para VR Box.
3. Apenas a mão direita é controlada. A posição do braço é **estimada**; o quaternion medido controla o pulso em 360°. Não há duas mãos 6DoF nem rastreamento óptico dos dedos.
4. **Andar como no Gorilla Tag (sem botão):** segure o controle quase reto: a mão fica logo acima do chão. Incline uns **10–15° para baixo** e ela encosta (painel: **NO CHÃO**). Num gesto rápido, incline mais **para baixo e para trás** (até ~45°): a mão fica presa no chão e o corpo vai para a frente. **Volte o controle para cima**: a mão solta na hora, volta pelo ar sem te puxar para trás, e o impulso continua (como soltar a mão no Gorilla Tag). Com a mão no chão, gire o controle para os lados para ir de lado; empurrar forte para baixo te levanta. A mão não gera força no ar. Como o G2 é 3DoF, **mover o controle sem girá-lo não é medido**: o movimento vem da inclinação do pulso.
5. Segure o clique do touchpad por 0,6 s para o menu. Aponte o laser ou use cima/baixo do touchpad para escolher; gatilho ou clique curto confirma. Em **CALIBRAR**, aponte para a frente antes de confirmar; isso corrige o eixo do braço estimado, não inventa posição rastreada.
6. Gatilho segura contato real em chão/parede. Clique curto no centro do touchpad pula; bordas giram 30°. **Ajuda no touchpad** é opcional, identificada no menu e desligada por padrão; não é apresentada como locomoção física.
7. Se continuar sem andar, veja se o painel mostra **NO AR**, **ANCORADA** ou **SEM POSE**. Use **SALVAR DIAGNÓSTICO** em **Configurações → Controles** na interface do lobby; envie esse JSON, navegador/versão e uma gravação curta. O diagnóstico contém apenas dados locais de execução, não credenciais.

A interface 2D do jogo original permite selecionar salas, acessórios e modos antes da sessão. A entrada rápida online, o modo tela e os diagnósticos ficam em **Configurações → Controles**, não em um painel flutuante no topo. O botão VR inicia Solo se você ainda não entrou em um modo. Para um modo de rede, entre pela interface original primeiro e então entre em VR. As telas de perfil/amigos/microfone do original continuam 2D; não foram todas reescritas como UI imersiva. Rede PeerJS, voz, câmera opcional e conforto de VR não foram validados em hardware.

## Correção de tremor — 1.0.4

No G2 real, a mão treme e a pose oscila; na 1.0.3 isso fazia o painel piscar NO AR/NO CHÃO e o corpo subir sozinho. Agora soltar e agarrar dependem do **ângulo** (é preciso inclinar ~5° de volta para soltar e voltar a inclinar para baixo para agarrar de novo), e com a mão no chão o corpo segue o ponto mais fundo do empurrão, então tremer ou voltar o controle não move você. Testado com tremor simulado de 3°: parado não pisca nem sobe.

## Locomoção estilo Gorilla — 1.0.3

Antes, a mão só tocava o chão com o controle apontado ~40° para baixo e o gesto de volta arrastava o corpo para trás, então quase não dava para andar. Agora o braço é longo e reto, pivotando no ombro, e a inclinação do pulso é amplificada (2×): um gesto natural de 10° a 45° varre o braço inteiro, rente ao chão. Inclinar o controle de volta para cima solta a mão imediatamente (detector sem atraso) e a levanta durante o retorno. Ao soltar, o corpo mantém 1,25× a velocidade do empurrão (limite 6,5 × escala/s). A colisão da 1.0.2 (esfera contínua, âncora fixa) continua igual.

Medido no jogo real em Chromium com sessão WebXR simulada e ruído de sensor: ~1,7–1,8 vezes o tamanho do jogador por empurrão (antes ~0,4); mão em repouso no chão sem deriva; braço inteiro (não só a mão) sempre acima do piso. **Não testado no G2 físico.**

## Correção de contato — 1.0.2

A mão agora usa **âncora em coordenadas do mapa**: ao tocar uma superfície, o ponto fica fixo enquanto o braço empurra o corpo. Levantar a mão aberta solta a âncora; o gatilho mantém a pegada enquanto houver apoio e alcance válidos. Menu, perda de pose, teleporte e mudança de mapa/escala cancelam apoio antigo.

A colisão foi trocada de raios ao redor do pulso para uma esfera varrida contra **faces, arestas e vértices** dos triângulos. Seu raio envolve a mão/dedos do modelo (mínimo 0,36 × escala do jogador, ampliado pela malha), não só o pulso. É um volume conservador: pode existir folga visual ao girar a palma; não é colisão individual de cada dedo. A movimentação da gravidade também é varrida antes de posicionar a mão visível. O pulso é alinhado à posição corrigida após o IK, com ajuste visual de alcance do antebraço.

Os testes verificam vértices da mão acima do piso durante empurrão, soltura, queda e giro com âncora; cantos, rampas, bordas, escalas pequenas e frames de 50 ms; além de contatos nos dois mapas originais. Isso cobre regressões no código, **não confirma a correção no seu G2**. Se ainda atravessar, confira **ANCORA 1.0.2** no painel e envie o diagnóstico e um vídeo do ponto do mapa.

## APK / instalação

O arquivo recebido é HTML/JS/Three.js, não Unity/Unreal. **O APK gerado não contém o compositor PICO ou integração nativa do controle.** Android WebView não adquire WebXR por trocar manifesto ou nome do dispositivo. O modo VR é o site no navegador externo já instalado.

- Pacote independente `com.picog2.gorillarushv45`, versão 1.0.2/code 3; Android mínimo API 23, alvo 28; Java/DEX, sem biblioteca JNI/ABI de engine.
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
- **Testes automatizados:** `docs/NODE_TESTS.txt`, `BROWSER_TESTS.json` e `ENTRY_TESTS.json`. Chromium executa o modelo, mapas, colisões e frame loop reais, mas as poses/sessões são simuladas. Inclui movimento horizontal em três ciclos com gravidade, sem botões, giro 360°, soltura, perda de pose e as duas projeções WebVR. Isso não prova o comportamento do navegador do G2.
- **NÃO testado no hardware físico PICO G2:** instalação Android, compositor real, firmware/browser do usuário, FPS, latência, temperatura, conforto, câmera, voz, multiplayer e transições completas entre todos os modos.
- Sem promessa de "perfeito/sem bugs", nem de física idêntica ao Gorilla Tag original: um controle 3DoF não fornece as posições independentes de duas mãos usadas por esse tipo de jogo. Braço articulado por IK, não ragdoll.

## Reversão sem perder a versão anterior

Não substituímos `/monke-go/` nem seu APK. Basta abrir o link anterior. Checkpoint do repositório anterior: `04f0d725a5e6b1e1a8a572b094950981784b2220`. Os pacotes Android são diferentes e podem coexistir. Se esta versão falhar, informe o diagnóstico; não é necessário apagar seus dados ou desfazer as outras aplicações.

[Alterações](CHANGELOG.md) · [Créditos/licenças](CREDITS.md)
