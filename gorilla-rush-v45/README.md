# Gorilla Rush v45 — adaptação de navegador para PICO G2 (física 1.0.8 — GMotion)

**Não é uma declaração de compatibilidade física certificada.** O usuário confirmou que a versão anterior acompanhava a cabeça/controle em Firefox Reality ou Wolvic, mas não permitia andar por gesto. Esta edição usa o **novo TXT v45**, em endereço e pacote separados. MonkeGo 1.3 permanece intacto.

- Jogo: https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/gorilla-rush-v45/?device=picog2&v=1.0.8-gmotion
- APK: `downloads/Gorilla-Rush-v45-PicoG2.apk` — **tela offline + abrir navegador externo**, não VR nativo.
- Projeto completo: `downloads/Gorilla-Rush-v45-projeto.zip`.
- Versão anterior: https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/monke-go/?device=picog2&v=1.3

## Usar no headset

1. No navegador VR que já acompanha seu controle, abra o endereço novo por HTTPS. Aguarde o carregamento dos mapas. Verifique o botão principal **INICIAR VR**.
2. Toque em **INICIAR VR**. Não precisa selecionar um modo no topo: o próprio clique solicita WebXR e inicia o jogo após a autorização do navegador. Em navegadores antigos sem WebXR, usa WebVR quando disponível. O botão só é habilitado se a API oferecer sessão WebXR imersiva ou VRDisplay WebVR com apresentação. Não há fallback disfarçado para VR Box.
3. O painel indica **E/D: posição medida** ou **3DoF estimado** por fonte. Usa esquerda, direita ou ambas se o navegador fornecer essas fontes; nunca duplica um controle para duas mãos. `gripSpace` tem preferência no WebXR; WebVR usa `pose.position` quando válida. A rotação do pulso continua livre em 360°. Não há tracking óptico dos dedos.
4. **Andar sem botão:** encoste a mão na superfície até indicar **CONTATO**, empurre para trás para avançar, levante e repita. Com posição medida, usa o deslocamento real. **No controlador original G2 3DoF, gire para baixo/trás:** translação física sem rotação não é medida pelo aparelho. A mão livre no ar não gera impulso. Não é possível obter duas posições 6DoF de um único controle 3DoF por software.
5. Segure o clique do touchpad por 0,6 s para o menu. Aponte o laser ou use cima/baixo do touchpad para escolher; gatilho ou clique curto confirma. Em **CALIBRAR**, aponte para a frente antes de confirmar; isso corrige o eixo do braço estimado, não inventa posição rastreada.
6. Gatilho fecha os dedos e aciona a tirolesa; não trava o corpo no chão/parede. Levante a mão para soltar, inclusive segurando o gatilho. Clique curto no centro do touchpad pula; bordas giram 30°. **Andar pelo touchpad** é opcional, identificado no menu e **desligado por padrão**; não é apresentada como locomoção física.
7. Se continuar sem andar, veja se o painel mostra **NO AR**, **CONTATO** ou **SEM POSE**. Use **SALVAR DIAGNÓSTICO** em **Configurações → Controles** na interface do lobby; envie esse JSON, navegador/versão e uma gravação curta. O diagnóstico contém apenas dados locais de execução, não credenciais.

A interface 2D do jogo original permite selecionar salas, acessórios e modos antes da sessão. A entrada rápida online, o modo tela e os diagnósticos ficam em **Configurações → Controles**, não em um painel flutuante no topo. O botão VR inicia Solo se você ainda não entrou em um modo. Para um modo de rede, entre pela interface original primeiro e então entre em VR. As telas de perfil/amigos/microfone do original continuam 2D; não foram todas reescritas como UI imersiva. Rede PeerJS, voz, câmera opcional e conforto de VR não foram validados em hardware.

## Recursos 3DoF da versão 1.0.5 preservados

Mantidos neutro automático (rápido nos primeiros segundos, lento depois), alcance amplificado 2×, trajetória baixa, retorno elevado da mão, histerese de ângulo contra tremor e touchpad de reserva opcional, agora desligado por padrão. Deixe o controle relaxado por alguns segundos ao entrar. Aproximadamente 15° abaixo do neutro aprendido encosta a mão no chão; o modo padrão já usa apenas as mãos para andar, sem deslizar o dedo no touchpad. A nova integração não usa a ajuda para mascarar testes de mãos: todas as passadas automatizadas desligam essa opção.

## Locomoção do ZIP — 1.0.8

Usa o **núcleo portado do `gmotion.js` enviado pelo usuário**, identificado no próprio ZIP como Lights Out: Bear Facility, não código oficial de Gorilla Tag. Fonte preservada em `reference/gorilla-movement`; detalhes de procedência, diferenças e testes em `docs/GMOTION_PORT.md` e `GMOTION_IMPORT.json`.

- Mão bloqueada na superfície versus posição desejada produz a correção do corpo, sem o ganho/interpolação da 1.0.7.
- Duas mãos em contato são promediadas, inclusive quando uma está parada. Um único controle nunca vira duas fontes.
- **Apoiar a mão sustenta o corpo**, como no ZIP. Levantar/recolher solta e a gravidade retorna; gatilho não muda essa regra.
- Impulso ao soltar: média de **8 deslocamentos efetivos do corpo**, limiar **0,4 m/s**, multiplicador **1,1**, limite **4 m/s**. Mão batendo numa parede sem mover o corpo não carrega um salto falso.
- Gravidade do ZIP: **5 m/s²**. Sem o antigo atrito de caminhada que freava o impulso. O corpo pode continuar deslizando após soltar; novo apoio permite frear. Tudo convertido pela escala PS.
- Assistência do touchpad permanece desligada por padrão e com zona morta/toque confirmado. Pulo por clique também funciona quando sustentado por mão no chão.

**Adaptações explícitas:** preservamos Three r128 e a colisão contínua existente, em vez de instalar outro motor Rapier que moveria o corpo duas vezes. O raio envolve a malha da mão, não a esfera de 3 cm da demonstração; há soltura por histerese 3DoF, proteção contra pose inválida/recentralização e limite de contato 8 m/s. A correção vertical passa pelo integrador com colisão, não pelo teleporte de até 0,25 m do ZIP. Não foram importados os efeitos de monstro/sprint do outro jogo.

43 testes Node incluem comparação numérica do núcleo portado com o `update()` do arquivo enviado em 30/60/72/90 Hz (backend de colisão simulado). Os 5 checks do exemplo original também passaram isoladamente com Three r160/Rapier 0.11.2. A suíte de navegador verifica os 13 cenários de locomoção, suporte/soltura em parede, salto pela média, gatilho, três tamanhos e quatro taxas de frame. Os testes executam o jogo e os triângulos reais com sessões/poses simuladas. **Não são testes físicos no headset.**

Menu, perda/troca de fonte, teleporte e mudança de escala limpam o histórico. Mapas/assets, modo VR, resolução/framebuffer, câmera, menus, sons e protocolo de rede foram mantidos. Gatilho na tirolesa e tag local usam o lado realmente conectado; multiplayer/voz continuam sem validação de rede no headset.

As mudanças históricas de 1.0.6/1.0.7 estão no CHANGELOG e diagnósticos anteriores. A regra antiga de gravidade permanente durante contato foi substituída intencionalmente pelo suporte do ZIP, sem manter dois motores concorrentes.

## Colisão de contato preservada — base 1.0.2

A mão agora usa **âncora em coordenadas do mapa**: ao tocar uma superfície, o ponto fica fixo enquanto o braço empurra o corpo. Levantar a mão solta a âncora visual, com ou sem gatilho. Na 1.0.8, o erro entre esse ponto e a posição desejada é consumido uma vez pelo deslocamento do corpo, como no ZIP. Levantar desfaz o apoio, sem trava extra de gatilho. Menu, perda de pose, teleporte e mudança de mapa/escala cancelam apoio antigo.

A colisão foi trocada de raios ao redor do pulso para uma esfera varrida contra **faces, arestas e vértices** dos triângulos. Seu raio envolve a mão/dedos do modelo (mínimo 0,36 × escala do jogador, ampliado pela malha), não só o pulso. É um volume conservador: pode existir folga visual ao girar a palma; não é colisão individual de cada dedo. A movimentação da gravidade também é varrida antes de posicionar a mão visível. O pulso é alinhado à posição corrigida após o IK, com ajuste visual de alcance do antebraço.

Os testes verificam vértices da mão acima do piso durante empurrão, soltura, queda e giro com âncora; cantos, rampas, bordas, escalas pequenas e frames de 50 ms; além de contatos nos dois mapas originais. Isso cobre regressões no código, **não confirma a correção no seu G2**. Se ainda atravessar, confira **FISICA 1.0.8** no painel e envie o diagnóstico e um vídeo do ponto do mapa.

## APK / instalação

O arquivo recebido é HTML/JS/Three.js, não Unity/Unreal. **O APK gerado não contém o compositor PICO ou integração nativa do controle.** Android WebView não adquire WebXR por trocar manifesto ou nome do dispositivo. O modo VR é o site no navegador externo já instalado.

- Pacote independente `com.picog2.gorillarushv45`, versão 1.0.8/code 9; Android mínimo API 23, alvo 28; Java/DEX, sem biblioteca JNI/ABI de engine.
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
- **Testes automatizados:** `docs/NODE_TESTS.txt`, `BROWSER_TESTS.json`, `ENTRY_TESTS.json` e `LOCOMOTION_TESTS.json`. Chromium executa o modelo, mapas, colisões e frame loop reais, mas as poses/sessões são simuladas. Inclui movimento horizontal em três ciclos com gravidade, sem botões, giro 360°, soltura, perda de pose e as duas projeções WebVR. Isso não prova o comportamento do navegador do G2.
- **NÃO testado no hardware físico PICO G2:** instalação Android, compositor real, firmware/browser do usuário, FPS, latência, temperatura, conforto, câmera, voz, multiplayer e transições completas entre todos os modos.
- Sem promessa de "perfeito/sem bugs", nem de física idêntica ao Gorilla Tag original: um controle 3DoF não fornece as posições independentes de duas mãos usadas por esse tipo de jogo. Braço articulado por IK, não ragdoll.

## Reversão sem perder a versão anterior

Não substituímos `/monke-go/` nem seu APK. Basta abrir o link anterior. Checkpoint do repositório anterior: `04f0d725a5e6b1e1a8a572b094950981784b2220`. Os pacotes Android são diferentes e podem coexistir. Se esta versão falhar, informe o diagnóstico; não é necessário apagar seus dados ou desfazer as outras aplicações.

[Alterações](CHANGELOG.md) · [Créditos/licenças](CREDITS.md)
