# GilaMobi v52 — porte WebXR para avaliação no Pico G2

Este é o **projeto Godot enviado**, com seus mapas, personagens e sistemas, não uma troca de nome em outro jogo. A versão web fica separada de OpenSaber, Gorilla Rush e MonkeGo.

**Destino:** https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/gilamobi-v52/ (disponível somente depois do merge e da publicação Pages).

## Compatibilidade — leia antes

- Pico **G2 comum**, não G2 4K nem Quest. É um aparelho 3DoF: rotação da cabeça/controle não fornece posição física das mãos. A mão nesta versão usa um **braço estimado**; não é rastreamento 6DoF nem a física oficial do Gorilla Tag.
- É necessário um navegador com **WebXR `immersive-vr`, WebGL2, WebAssembly e `OVR_multiview2`**, numa página HTTPS. Android, OpenXR ou a marca Pico, isoladamente, não comprovam isso. A página verifica as APIs e não oferece “VR” quando faltam.
- O export usa **Godot 4.4.1 Compatibility, Web single-thread**. Não precisa de cabeçalhos COOP/COEP que o GitHub Pages não permite configurar livremente.
- Navegadores com WebXR básico mas sem Layers usam o `webxr-layers-polyfill` incluído. Ele compõe texturas reais por olho na camada nativa do navegador. **Não cria WebXR em navegador incompatível.**
- **Não testado num Pico G2 físico.** Firmware/navegador/versão do seu aparelho ainda não foram informados. Não há garantia de FPS, conforto, autonomia, instalação nem compatibilidade específica com o navegador instalado. Veja `docs/COMPATIBILITY.md`.
- **Não é APK VR nativo.** A rota escolhida foi WebXR/GitHub Pages; o projeto Android original dependia de template/plugin nativo não fornecido por completo.

## Como usar

1. Abra o link HTTPS no navegador do headset e aguarde “projeto v52 carregado”. A tela inicial comum é apenas uma prévia, não VR.
2. Aponte o controle à frente, numa posição confortável, e clique **INICIAR VR**. Aceite a solicitação do navegador.
3. Deixe **Braço estimado 3DoF** marcado no G2. Incline para baixo até encostar na superfície; continue o gesto para empurrar o corpo. Levante para soltar a mão. Não é preciso usar o touchpad ou manter o gatilho apertado para locomover.
4. Segure o gatilho por **1,1 segundo** para abrir/fechar o painel; aponte o raio e clique para selecionar. “Calibrar” redefine a posição neutra do gesto. “Recentrar” redefine a referência da cabeça.
5. No painel: mapas Base/Backrooms/Sandbox, personagem Normal/Grinner no Sandbox, agarrar/motosserra, soltar/dividir/arrancar, microfone, câmera e gravação. “Menu original” permite acessar os outros recursos recebidos. Campos de texto têm teclado no painel VR.
6. Somente fontes conectadas controlam braços. Esquerda/direita são respeitadas; controle sem indicação de mão (`tracker_2`) é atribuído uma vez à direita. Sem controle, não é inventada uma mão de apoio.
7. Em dispositivo **realmente 6DoF**, a opção de braço estimado pode ser desmarcada. Isso não transforma um controle G2 em 6DoF.

Comece sentado, em área desobstruída; interrompa se houver desconforto. Uma trajetória de braço estimada pode diferir bastante do movimento físico.

## Recursos mantidos e adaptações

Mantidos na fonte: os três mapas originais, personagens/rigs, IK, itens/cosméticos, Grinner, agarrar/desmembrar, motosserra, ragdolls, NPCs Sandbox, espelho, menu, rede e voz recebidos. A câmera continua produzindo vídeo sem áudio; na Web o resultado é **AVI/MJPEG baixado pelo navegador**, não MP4 na galeria Android.

O caminho imersivo antigo VRBox/giroscópio/touch foi substituído pelo compositor WebXR real. Câmera Android/MediaPipe não é usada para inventar tracking do G2. Fontes Java/C++ e binários recebidos continuam em `reference/`, fora do export Web.

Limitações importantes:

- Voz precisa de permissão e suporte à captura de áudio do navegador. Conversa de voz entre dois aparelhos e permissão no G2 **não validadas**.
- A rede original usa broker MQTT público, sem propriedade/autenticação confiável. Não envie dados privados. Disponibilidade, moderação, latência e sincronização entre aparelhos não foram certificadas.
- AVI depende de o navegador permitir downloads; gravação aumenta muito CPU/GPU/memória. Não há encoder Android/MediaStore na Web.
- Mecânicas de dois braços exigem dois controles/fontes reais; um G2 com um controle não pode executá-las simultaneamente como se tivesse dois.
- O painel/teclado, combate completo, interação online e desempenho de todos os cenários ainda precisam de ensaio físico. Os testes automatizados não substituem essa etapa.

## Compilar e testar

Requisitos: **Godot 4.4.1 oficial + templates de exportação 4.4.1**, Python 3, Bash/coreutils. No CI Linux, Xvfb/Mesa permite testar os buffers de morph no renderizador GL real (o renderizador Dummy não os implementa). Para testes de navegador: Node 22 e npm.

```bash
GODOT=/caminho/para/godot bash gilamobi-v52/tools/build.sh
python3 gilamobi-v52/tools/verify.py
npm ci --prefix gilamobi-v52/tests --ignore-scripts --no-audit --no-fund
python3 -m http.server 5192 --bind 0.0.0.0 --directory gilamobi-v52/web
# Em outro terminal:
PREVIEW_BASE_URL=http://127.0.0.1:5192/ node gilamobi-v52/tests/browser.mjs
```

No headset, use HTTPS; `localhost` no headset não é seu PC. O workflow **Build GilaMobi v52 WebXR** faz importação, regressões no motor com GL/Xvfb, export, verificação e regressões no navegador. O workflow Pages publica **apenas `web/`**, nunca fontes, emulador ou plugins Android.

`tools/preview_source.py` é somente um acelerador local de regressão: repacota scripts com assets já importados num diretório `.cache`. **Não é usado para gerar a versão publicada**, que deve passar pela exportação do motor.

## Evidências e arquivos

- `docs/BUILD.json`: hashes do export e das fontes utilizadas; motor e limites.
- `docs/ENGINE_TESTS.json`: testes do motor/colisões/rigs/mapas, gerado pela compilação.
- `docs/BROWSER_TESTS.json`: testes Chromium com runtime WebXR **emulado**; não mede FPS do headset.
- `docs/SUPPLIED_FILES.json`: inventário SHA-256 de todos os arquivos recebidos e identificação do ZIP.
- `source/`: projeto Godot completo modificado; `reference/`: componentes Android e ferramentas recebidas preservados.
- `docs/CHANGELOG.md`: modificações e diagnóstico de renderização.

## Créditos e autorização

Os créditos/licenças recebidos foram mantidos em `source/` e copiados para `web/licenses/`. Incluem o crédito **Grinner from gilla — reduxlethalape — CC BY 4.0** e as restrições da licença GILLA. O usuário declarou possuir **autorização específica**; não foi fornecido documento para verificação independente. Essa declaração **não altera as licenças** nem concede automaticamente direitos a terceiros. Não se afirma afiliação oficial ao jogo/modelo original.

Para voltar, use os links anteriores de Gorilla Rush ou OpenSaber: nenhum deles é substituído por este porte.
