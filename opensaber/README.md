# OpenSaber — G2 Web 1.0.0 (experimental)

**[Abrir OpenSaber WebXR](https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/opensaber/)**

Adaptação do **arquivo enviado pelo usuário**, separada dos demais jogos. Mantém o motor Godot, cenas, sabres, colisões, interface, pontuação e assets recebidos. Não é uma demonstração substituindo o OpenSaber.

## O que realmente veio no ZIP

`OpenSaber-WebXR-PicoG2-GitHub.zip` já continha um **export web Godot 4.3**, com `index.wasm`, `index.pck` e scripts compilados. Não continha o projeto-fonte `OpenSaber-G2/` anunciado no README recebido. A música Timelapse tinha somente a referência da capa: faltavam áudio, Info.dat e mapa.

Por isso, **não houve recompilação do motor nem exportação de um projeto completo**. O PCK foi verificado, scripts necessários foram recuperados/reescritos, e o pacote foi remontado. Os patches `.gd` são compilados/carregados pelo próprio Godot ao abrir a página. O WASM recebido permanece idêntico. Proveniência e hashes: [`docs/BUILD.json`](docs/BUILD.json). O README original é preservado em [`docs/SUPPLIED_README.md`](docs/SUPPLIED_README.md), **sem endossar suas afirmações**.

## Jogar no headset

1. Abra o link em **HTTPS**, em um navegador do headset que realmente ofereça **WebXR immersive-vr + WebGL 2 + WebAssembly**. WebView e uma aba comum não substituem VR imersivo. O primeiro carregamento transfere aproximadamente 47 MB (antes da compressão do servidor).
2. Deixe **Controle G2 / braço estimado 3DoF** marcado. Pressione **INICIAR VR** e autorize a sessão.
3. No menu original em VR, aponte para **Pico Pulse – Treino**, selecione a dificuldade **1 controle – DIREITA** ou **ESQUERDA** e pressione **Play**. Se o controle vier sem identificação de mão, ele é atribuído à direita.
4. Corte os blocos com o sabre. A faixa tem **52 segundos, 100 BPM e 37 notas**, sem bombas ou paredes. Seus blocos aceitam qualquer direção de corte. A música é síntese original incluída, não uma cópia de Timelapse.
5. **Gatilho ou clique do touchpad:** selecionar. **Segurar o gatilho por 1,2 s durante a música:** pausar. Use o laser para continuar ou voltar ao menu. B/Y também pausa quando disponível.

Não é preciso iniciar o treino antes de entrar em VR. Os botões de treino da página servem também para testar na tela. Para dois controles reais, escolha a dificuldade correspondente; para 6DoF, desmarque o braço estimado. **Um único controle nunca é duplicado para fabricar uma segunda mão.**

### Teste na tela

O simulador original foi mantido: botão direito do mouse gira a visão; **Alt + botão direito** move a mão direita; **Ctrl + botão direito**, a esquerda; clique esquerdo seleciona. WASD move a visão, e Alt/Ctrl + WASD movimenta/gira a mão selecionada. Isso não mede a experiência no headset.

## Correções deste pacote

- Página normal `index.html`, sem redirecionamento para `404.html`; arquivos relativos compatíveis com subpastas do GitHub Pages.
- Música/áudio/mapas de treino completos; diretórios sem Info.dat deixam de aparecer como música inválida.
- API correta de `XRPose.get_adjusted_transform()`, fontes esquerda/direita/sem mão, orientação sem duplicar o giro da cabeça, braço estimado explicitamente para G2 e preservação da pose em 6DoF.
- Estados de gatilho/soltura, mapeamentos `xr-standard`, botão genérico e eventos WebXR select; limpeza na perda de pose. Métodos de vibração/AABB usados pelos sabres restaurados.
- Mão desconectada não seleciona interface nem pontua com sabre invisível.
- Sessão `immersive-vr` com `local-floor` **opcional** e fallback `local`, compensando altura quando o piso não está disponível. Falha/saída de sessão não se passa por VR ativo.
- Servidor TCP de upload, servidor UDP de diagnóstico e extensão nativa OpenXR desativados no export web.
- Guarda para referência de nó vazia no painel BeatSage e arquivos iniciais de pontuação.
- Pool inicial de 16 cubos em vez de 100; espera pelo aquecimento antes de habilitar os botões da página; corrigida a devolução de cubos reutilizados ao pool.

## Testes e limites — leia antes de considerar compatível

- [`docs/BROWSER_TESTS.json`](docs/BROWSER_TESTS.json): motor real em Chromium, faixa completa até resultado, notas e colisão/pontos, pausa, retomada, três dificuldades e recarga com armazenamento persistente.
- [`docs/CONTROLLER_TESTS.json`](docs/CONTROLLER_TESTS.json): **fontes simuladas dentro do XRServer do Godot**, exercitando o script real. Não é teste de controle físico nem de compositor WebXR.
- Os scripts não apresentaram novos erros nos cenários aprovados. **Persistem avisos de `ViewportTexture` durante a inicialização das cenas binárias recebidas**; eles são registrados nos relatórios, não ocultados. Os painéis utilizados nos testes renderizaram.
- **PICO G2 físico, navegador/firmware específico, imersão real, alcance dos cortes, áudio ouvido, FPS, latência e temperatura ainda não foram validados.** Não há promessa de “zero bugs” ou “sem lag”. O renderizador por software do ambiente de testes não é um benchmark do G2.
- Um G2 cujo navegador só ofereça WebGL 1 ou WebVR antigo **não consegue executar este export Godot 4.3 em VR**. Esta adaptação não instala suporte WebXR no firmware.
- O braço 3DoF é uma estimativa pela orientação, não rastreamento posicional. Use uma área livre e movimentos moderados.
- Painéis online do app original podem depender de APIs/CORS/autenticação que não funcionam na web. **O caminho validado é a faixa local Pico Pulse.** O servidor de upload do APK não pode rodar dentro do navegador.
- Este pacote é **WebXR**, não um APK VR nativo PICO.

## Verificação / reconstrução

O `index.pck` pronto fica versionado (sem Git LFS, para não publicar ponteiros no Pages). O ZIP original fica fora do Git.

```sh
python3 opensaber/tools/verify.py
# Requer exatamente o ZIP recebido, validado por MD5 + hashes dos 388 arquivos:
python3 opensaber/tools/repack.py google_drive/OpenSaber-WebXR-PicoG2-GitHub.zip
# Opcional: regerar somente áudio e mapas originais (numpy + soundfile):
python3 opensaber/tools/generate_training.py
# Navegador usa as dependências já fixadas do projeto vizinho:
npm ci --prefix gorilla-rush-v45
python3 -m http.server 5190 --bind 0.0.0.0
# Em outro terminal:
node opensaber/tools/check_browser.mjs
python3 opensaber/tools/make_fixture.py
CONTROLLER_FIXTURE=1 PREVIEW_BASE_URL=http://127.0.0.1:5190/.cache/opensaber-fixture/ node opensaber/tools/check_browser.mjs
```

`make_fixture.py` cria um pacote separado e temporário em `.cache/`, com controles sintéticos. **Esse pacote nunca é publicado.** O workflow verifica e copia apenas os arquivos web necessários, em `/opensaber/`, sem modificar os outros jogos.

## Proveniência e licenças

Assets e código original: pacote fornecido pelo usuário, internamente identificado como Open Saber / BeepSaber / OQ Toolkit. Não atribuímos uma licença nova aos assets de terceiros nem afirmamos que o usuário enviou o projeto-fonte completo. O ZIP não trouxe um inventário completo de licenças; isso continua sendo uma limitação de proveniência. Godot é distribuído sob sua licença MIT, preservada em `docs/GODOT_LICENSE.txt`. A faixa Pico Pulse e seus mapas foram gerados especificamente para este treino; não usam amostras comerciais.
